#!/usr/bin/env python3
"""Generate notes MCQs from the Combined Notes with the OpenAI API.

The prompt is the Group 2 prep app's MCQ prompt (tools/prompt/base_from_prep_app.txt, copied verbatim
from appsc-group2-prep-app/content-pipeline/mcq-prompt.txt) followed by the APPSC techniques
(tools/prompt/appsc_techniques.txt, from the Drive guide "APPSC MCQ Techniques - Tested on PYQs")
and the output format (tools/prompt/output_format.txt).

Work unit: one notes section (syllabus row), split into chunks of whole subsections (~5,000
characters each). Rows are processed in 90-day-plan order, so the first plan days get their
questions first and a partial run is already useful.

Design (same as the prep app's generate-mcqs.js):
  - Resumable: each finished chunk is written to tools/generated/b{book}/r{row}-c{chunk}.json;
    a chunk with a file is never paid for again. Failed chunks have no file and are retried.
  - Validated: four distinct options, a valid key, no "according to the notes" leaks, known type.
  - Unique: the model is shown the section's PYQs and the questions already written for the
    section, and every item is then checked for exact and near duplicates against ALL PYQs and
    ALL notes MCQs of the book (content-word overlap), so no fact is asked twice.
  - Option order: shuffled deterministically for plain questions (keeps "All / None of the
    above" last), numbers sorted ascending, coded options (statements, A-R, match, order) kept.

Usage:
  export OPENAI_API_KEY=sk-...           # never commit it; in GitHub use the repo secret
  export OPENAI_MODEL=gpt-4.1-mini       # or pass --model
  python3 tools/generate_notes_mcqs.py --dry-run --limit 1     # show the first prompt, no API call
  python3 tools/generate_notes_mcqs.py --limit 5               # pilot: 5 chunks
  python3 tools/generate_notes_mcqs.py --days 1-10             # rows of plan days 1-10
  python3 tools/generate_notes_mcqs.py --book 2                # one subject
  python3 tools/generate_notes_mcqs.py --time-budget 300       # stop cleanly after 300 minutes
  python3 tools/build_assets.py                                # then rebuild the app assets
"""
import argparse
import hashlib
import json
import os
import random
import re
import sys
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "source"
GEN = ROOT / "tools" / "generated"
PROMPT_DIR = ROOT / "tools" / "prompt"

sys.path.insert(0, str(Path(__file__).parent))
from mcq_text import DupIndex, content_tokens, norm  # noqa: E402

BOOK_NAMES = {1: "History & Culture (Indian and Andhra Pradesh)", 2: "Polity, Society & International Relations",
              3: "Economy (Indian and Andhra Pradesh)", 4: "Geography (World, India, Andhra Pradesh)",
              5: "Science, Technology & Environment", 6: "Current Affairs"}

# Part B/C of the techniques guide: the traps and option tricks proven in each subject.
SUBJECT_RULES = {
    1: "History & Culture: the strongest trap is a swapped ruler/founder/author ('during the reign of', 'founded by', 'built by', 'written by'). "
       "Use chronology questions (dynasties, battles, movements, Acts), matches (ruler–capital, author–work, dynasty–monument, movement–leader) "
       "and 'no exceptions' words and 'not' a little. For Andhra history (Satavahanas, Ikshvakus, Eastern Chalukyas, Kakatiyas, Reddis, Vijayanagara, "
       "Qutb Shahis, freedom struggle in Andhra) test names, places and years directly; number options with middle-value keys work here.",
    2: "Polity, Society & IR: use 'only' and 'no exceptions' words, a power given to the wrong authority (President/Governor/Speaker/CM), "
       "'member of / consists of', Article or Schedule swaps, time words, 'in India / the state' size words, comparisons (Lok Sabha vs Rajya Sabha, "
       "Union vs State), percentages, and long 'true start, false end' sentences. In Polity 'can / may' about a power is usually FALSE. "
       "Assertion–Reason and matches (Article–subject, body–type, list–entry) are common.",
    3: "Economy: use 'launched / developed / set up by' and 'Ministry of' swaps, world bodies (IMF, World Bank, ADB, WTO) swapped, trends "
       "('steadily', 'in the last decade'), 'no exceptions' words and 'not', long sentences, and TRUE 'aims to / helps in' statements for schemes. "
       "'All of the above' is strong for scheme objectives; 'Both A and B' options work here. Use formula-based numerical questions "
       "(deficits, growth rates) where the notes give the figures, with ascending number options.",
    4: "Geography: use 'only', 'no exceptions' words, 'not', 'between X and Y' ranges, 'is located in / lies in', size words ('largest in India'), "
       "comparisons and percentages. 'Some / one of the' and 'can' statements are usually TRUE here. Orders (north to south, source to mouth) "
       "and matches (river–origin, pass–state, soil–crop, port–state) are natural question types.",
    5: "Science, Technology & Environment: use 'only', 'no exceptions' words and 'not'; 'is used for / helps in / can' statements are usually TRUE; "
       "'All of the above' is strong (applications, pollutants, effects); swap world environment bodies (UNEP, IPCC, IUCN, UNFCCC) and conventions; "
       "missions and satellites with the wrong year or agency. Use 'break the word into parts' friendly terms (bio-, photo-, hydro-).",
    6: "Current Affairs: use 'launched by / Ministry of' swaps, 'located in / headquarters', world body swaps and the famous-word trap "
       "(a famous scheme, index or summit with the wrong year, host, rank or figure). Matches (index–publisher, summit–host city, "
       "scheme–ministry) with codes that share pairs work well; 'None of the above' is a real option.",
}

TYPES = {"fact", "statements", "assertion_reason", "match", "chronology", "negative", "odd_one_out",
         "confusable", "numerical", "application", "comparison"}
FIXED_ORDER_TYPES = {"statements", "assertion_reason", "match", "chronology"}

# Phrases the prep app bans (generate-mcqs.js SOURCE_LEAK), plus a few more seen in pilots.
SOURCE_LEAK = re.compile("|".join([
    r"according to the (notes|text|passage|material)",
    r"as per the (given|notes|text)",
    r"from the (passage|notes)",
    r"the notes (state|say|mention)",
    r"based on the given (information|text)",
    r"in the (above )?(notes|passage|text)\b",
    r"with reference to (topic|part|section) \d",
    r"\b(this|the) (subsection|block|fragment|source file|dump)\b",
    r"(leftover|unstructured) .{0,20}fragment",
    r"exam[- ]question[- ]style",
    r"as (mentioned|discussed|stated) (above|earlier)",
]), re.I)
LAST_OPTION = re.compile(r"^(all|none|both|neither)\b.*\b(above|these|of them|options)?", re.I)
NUMERIC = re.compile(r"^[₹$\s]*[-+]?\d[\d,]*(\.\d+)?\s*(%|per ?cent|years?|km|crores?|lakhs?|million|billion|days|months|times|tonnes?|mm|cm|m|kg|°\s*[CF]?|degrees?|seats|members|AD|BC|BCE|CE)?\.?$", re.I)
LETTERS = "abcd"

print_lock = threading.Lock()


def log(*a):
    with print_lock:
        print(*a, flush=True)


# ---------------------------------------------------------------- notes → text

def runs_text(x):
    if x is None:
        return ""
    if isinstance(x, str):
        return x
    return "".join(r[0] if isinstance(r, list) else str(r) for r in x)


def block_text(b):
    k = b["k"]
    if k == "t":
        lines = ["| " + " | ".join(runs_text(c) for c in b["h"]) + " |"]
        lines += ["| " + " | ".join(runs_text(c) for c in row) + " |" for row in b["r"]]
        return "📊 table\n" + "\n".join(lines)
    t = runs_text(b.get("x"))
    return {"b": "- ", "s": "  - ", "x": "🎯 Exam angle: ", "a": "See also: ", "n": "", "p": ""}.get(k, "") + t


def subsection_text(s):
    head = "■ " + s["t"] + (f"  [{', '.join(s['badges'])}]" if s.get("badges") else "")
    return head + "\n" + "\n".join(block_text(b) for b in s["b"])


def load_rows(book):
    """Notes rows in the notes app's order (row index counts across units)."""
    data = json.loads((SRC / "notes" / f"book{book}.json").read_text())
    rows = []
    for u in data["units"]:
        for r in u["rows"]:
            rows.append({"unit": u["title"], "title": r["title"], "codes": r.get("codes", []), "secs": r["secs"]})
    return rows


def chunks_of(row, max_chars):
    out, cur, size = [], [], 0
    for s in row["secs"]:
        t = subsection_text(s)
        if len(norm(t)) < 40:  # a bare heading with no body
            continue
        if cur and size + len(t) > max_chars:
            out.append(cur)
            cur, size = [], 0
        cur.append((s["t"], t))
        size += len(t)
    if cur:
        out.append(cur)
    return out


# ---------------------------------------------------------------- work list

def plan_order():
    """(book, row) in the order the 90-day plan first studies them, then any rows the plan never lists."""
    plan = json.loads((SRC / "plan.json").read_text())
    order, seen, day_of = [], set(), {}
    for d in plan["days"]:
        for r in d["rows"]:
            if "ref" in r:
                k = tuple(r["ref"])
                if k not in seen:
                    seen.add(k)
                    order.append(k)
                    day_of[k] = d["n"]
    return order, seen, day_of


def parse_days(spec):
    a, _, b = spec.partition("-")
    return int(a), int(b or a)


# ---------------------------------------------------------------- PYQs and existing MCQs (for uniqueness)

def load_pyqs(book):
    """row -> list of PYQ stems, and a DupIndex of every PYQ (stem + key) in the book."""
    data = json.loads((SRC / "pyq" / f"pyq{book}.json").read_text())
    by_row, idx = {}, DupIndex()
    for sect in ("rows", "units"):
        for key, qs in data[sect].items():
            for q in qs:
                opts = q.get("o", [])
                a = q.get("a", -1)
                ans = opts[a] if 0 <= a < len(opts) else q.get("at", "")
                table = " ".join(" ".join(r) for r in q.get("t", []))
                idx.add(content_tokens(q["s"] + " " + table + " " + ans))
                if sect == "rows":
                    by_row.setdefault(int(key), []).append(q["s"])
    return by_row, idx


def load_generated(book):
    """row -> list of generated stems, and a DupIndex over them, from chunk files already on disk."""
    by_row, idx = {}, DupIndex()
    d = GEN / f"b{book}"
    if d.exists():
        for f in d.glob("r*-c*.json"):
            data = json.loads(f.read_text())
            for m in data.get("accepted", []):
                by_row.setdefault(data["row"], []).append(m["s"])
                idx.add(content_tokens(m["s"] + " " + m["o"][m["a"]]))
    return by_row, idx


# ---------------------------------------------------------------- prompt

def system_prompt(book):
    base = (PROMPT_DIR / "base_from_prep_app.txt").read_text()
    tech = (PROMPT_DIR / "appsc_techniques.txt").read_text().replace("{SUBJECT_RULES}", SUBJECT_RULES[book])
    fmt = (PROMPT_DIR / "output_format.txt").read_text()
    return base.rstrip() + "\n" + tech + fmt


def user_prompt(book, row, chunk, bank):
    lines = [
        f"SUBJECT: {BOOK_NAMES[book]}",
        f"TOPIC: {row['unit']}",
        f"SECTION: {row['title']}" + (f"  (syllabus codes {', '.join(row['codes'])})" if row["codes"] else ""),
        "SUBSECTIONS IN THIS BLOCK: " + " | ".join(t for t, _ in chunk),
        "",
    ]
    if bank:
        lines.append(f"ALREADY IN THE BANK — do not repeat, rephrase or reverse these ({len(bank)}):")
        lines += ["- " + " ".join(s.split())[:170] for s in bank]
        lines.append("")
    lines.append("NOTES:")
    lines += [t for _, t in chunk]
    return "\n".join(lines)


# ---------------------------------------------------------------- OpenAI

API_URL = os.environ.get("OPENAI_BASE_URL", "https://api.openai.com/v1").rstrip("/") + "/chat/completions"


def call_model(model, key, system, user):
    body = json.dumps({
        "model": model,
        "response_format": {"type": "json_object"},
        "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}],
    }).encode()
    last = None
    for attempt in range(6):
        req = urllib.request.Request(API_URL, data=body, headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=600) as r:
                data = json.load(r)
            content = data["choices"][0]["message"]["content"]
            return content, data.get("usage", {})
        except urllib.error.HTTPError as e:
            msg = e.read().decode(errors="replace")[:300]
            if e.code in (400, 401, 403, 404):
                raise RuntimeError(f"OpenAI HTTP {e.code}: {msg}")
            last = f"HTTP {e.code}: {msg}"
            wait = min(90, 2 ** attempt * (5 if e.code == 429 else 2))
        except (urllib.error.URLError, TimeoutError, ConnectionError, KeyError, json.JSONDecodeError) as e:
            last = f"{type(e).__name__}: {e}"
            wait = min(90, 2 ** attempt * 2)
        log(f"    {last}; retrying in {wait}s")
        time.sleep(wait)
    raise RuntimeError(f"giving up after retries ({last})")


def parse_questions(text):
    t = text.strip()
    m = re.match(r"^```(?:json)?\s*([\s\S]*?)\s*```$", t)
    if m:
        t = m.group(1)
    data = json.loads(t)
    if isinstance(data, list):
        return data
    qs = data.get("questions")
    if not isinstance(qs, list):
        raise ValueError("no 'questions' array in the response")
    return qs


# ---------------------------------------------------------------- validation and clean-up

def strip_label(o):
    return re.sub(r"^\s*(\(?[a-dA-D1-4]\)|[a-dA-D1-4][.)])\s+", "", str(o)).strip()


def validate(item):
    if not isinstance(item, dict):
        return "not an object"
    for f in ("question", "option_a", "option_b", "option_c", "option_d", "correct_option"):
        if str(item.get(f, "")).strip() == "":
            return f"missing {f}"
    if str(item["correct_option"]).strip().lower() not in LETTERS:
        return "bad correct_option"
    q = str(item["question"])
    if SOURCE_LEAK.search(q) or SOURCE_LEAK.search(str(item.get("explanation", ""))):
        return "references the source material"
    if len(q) < 15:
        return "question too short"
    opts = [norm(strip_label(item[f"option_{c}"])) for c in LETTERS]
    if len(set(opts)) != 4 or any(not o for o in opts):
        return "duplicate or empty options"
    ty = str(item.get("type", "fact")).strip().lower()
    if ty == "assertion_reason" and not re.search(r"assertion", q, re.I):
        return "assertion_reason without an assertion"
    return None


def seeded(text):
    return random.Random(int(hashlib.sha1(text.encode()).hexdigest()[:12], 16))


def arrange_options(q):
    """Return (options, answer) with APPSC-like ordering (see module docstring)."""
    opts = [strip_label(q[f"option_{c}"]) for c in LETTERS]
    key = opts[LETTERS.index(str(q["correct_option"]).strip().lower())]
    ty = str(q.get("type", "fact")).strip().lower()
    if ty in FIXED_ORDER_TYPES:
        return opts, opts.index(key)
    if all(NUMERIC.match(o) for o in opts):
        def val(o):
            m = re.search(r"\d[\d,]*(\.\d+)?", o)
            v = float(m.group().replace(",", ""))
            return -v if re.search(r"\b(BC|BCE)\b", o, re.I) else v
        opts.sort(key=val)
        return opts, opts.index(key)
    last = [o for o in opts if LAST_OPTION.match(o) and re.search(r"\b(above|these|of them|both|neither)\b", o, re.I)]
    free = [o for o in opts if o not in last]
    seeded(q["question"]).shuffle(free)
    opts = free + last
    return opts, opts.index(key)


def clean(item, pyq_idx, gen_idx, lock):
    """Validated, deduplicated app question, or (None, reason)."""
    err = validate(item)
    if err:
        return None, "invalid"
    opts, ans = arrange_options(item)
    stem = str(item["question"]).strip()
    toks = content_tokens(stem + " " + opts[ans])
    with lock:
        if pyq_idx.has_dup(toks):
            return None, "dupe_of_pyq"
        if gen_idx.has_dup(toks):
            return None, "dupe"
        gen_idx.add(toks)
    ty = str(item.get("type", "fact")).strip().lower()
    q = {"s": stem, "o": opts, "a": ans, "x": str(item.get("explanation", "")).strip(),
         "ty": ty if ty in TYPES else "fact"}
    if str(item.get("technique", "")).strip():
        q["tq"] = str(item["technique"]).strip()
    if str(item.get("subsection", "")).strip():
        q["sec"] = str(item["subsection"]).strip()
    return q, None


# ---------------------------------------------------------------- main

def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model", default=os.environ.get("OPENAI_MODEL") or "gpt-4.1-mini")
    ap.add_argument("--book", type=int, action="append", help="only this book (repeatable)")
    ap.add_argument("--days", help="only rows of these plan days, e.g. 1-10")
    ap.add_argument("--limit", type=int, default=0, help="stop after this many chunks")
    ap.add_argument("--chunk-chars", type=int, default=5000)
    ap.add_argument("--concurrency", type=int, default=4)
    ap.add_argument("--time-budget", type=float, default=0, help="minutes; start no new chunk after this")
    ap.add_argument("--dry-run", action="store_true", help="print the first prompt and the work count; no API call")
    args = ap.parse_args()

    key = os.environ.get("OPENAI_API_KEY", "")
    if not key and not args.dry_run:
        sys.exit("OPENAI_API_KEY is not set (export it, or add it as the repository secret for the GitHub workflow).")

    order, listed, day_of = plan_order()
    books = args.book or list(range(1, 7))
    rows_by_book = {b: load_rows(b) for b in books}
    keys = [k for k in order if k[0] in books] + [(b, i) for b in books for i in range(len(rows_by_book[b])) if (b, i) not in listed]
    if args.days:
        lo, hi = parse_days(args.days)
        keys = [k for k in keys if lo <= day_of.get(k, 10 ** 6) <= hi]

    jobs = []
    for b, ri in keys:
        if ri >= len(rows_by_book[b]):
            continue
        row = rows_by_book[b][ri]
        for ci, ch in enumerate(chunks_of(row, args.chunk_chars)):
            if not (GEN / f"b{b}" / f"r{ri}-c{ci}.json").exists():
                jobs.append((b, ri, ci, row, ch))
    total_chars = sum(len(t) for *_, ch in jobs for _, t in ch)
    if args.limit:
        jobs = jobs[: args.limit]
    log(f"model {args.model} · {len(jobs)} chunks to do · {total_chars:,} characters of notes pending")

    pyqs = {b: load_pyqs(b) for b in books}
    gens = {b: load_generated(b) for b in books}
    systems = {b: system_prompt(b) for b in books}
    lock = threading.Lock()

    if args.dry_run:
        if jobs:
            b, ri, ci, row, ch = jobs[0]
            bank = pyqs[b][0].get(ri, []) + gens[b][0].get(ri, [])
            print("=" * 30, "SYSTEM", "=" * 30)
            print(systems[b])
            print("=" * 30, "USER", "=" * 30)
            print(user_prompt(b, row, ch, bank))
        return

    started = time.time()
    totals = {"chunks": 0, "generated": 0, "accepted": 0, "invalid": 0, "dupe": 0, "dupe_of_pyq": 0,
              "failed": 0, "in": 0, "out": 0}

    def run(job):
        b, ri, ci, row, ch = job
        if args.time_budget and time.time() - started > args.time_budget * 60:
            return "skipped"
        with lock:
            bank = pyqs[b][0].get(ri, []) + gens[b][0].get(ri, [])
        user = user_prompt(b, row, ch, bank[-150:])
        items = None
        for attempt in range(2):
            content, usage = call_model(args.model, key, systems[b], user)
            with lock:
                totals["in"] += usage.get("prompt_tokens", 0)
                totals["out"] += usage.get("completion_tokens", 0)
            try:
                items = parse_questions(content)
                break
            except (ValueError, json.JSONDecodeError) as e:
                if attempt:
                    raise RuntimeError(f"malformed JSON twice: {e}")
                log(f"    malformed JSON ({e}); asking once more")
        accepted, rej = [], {"invalid": 0, "dupe": 0, "dupe_of_pyq": 0}
        for it in items:
            q, why = clean(it, pyqs[b][1], gens[b][1], lock)
            if q:
                accepted.append(q)
            else:
                rej[why] += 1
        out = GEN / f"b{b}" / f"r{ri}-c{ci}.json"
        out.parent.mkdir(parents=True, exist_ok=True)
        tmp = out.with_suffix(".tmp")
        tmp.write_text(json.dumps({
            "book": b, "row": ri, "chunk": ci, "section": row["title"], "subsections": [t for t, _ in ch],
            "model": args.model, "generated": len(items), "rejected": rej, "accepted": accepted,
        }, ensure_ascii=False, indent=1))
        tmp.replace(out)
        with lock:
            gens[b][0].setdefault(ri, []).extend(q["s"] for q in accepted)
            totals["chunks"] += 1
            totals["generated"] += len(items)
            totals["accepted"] += len(accepted)
            for k, v in rej.items():
                totals[k] += v
        return f"b{b} r{ri} c{ci} {row['title'][:50]!r}: {len(items)} generated, {len(accepted)} kept, {rej}"

    with ThreadPoolExecutor(max_workers=max(1, args.concurrency)) as pool:
        futs = {pool.submit(run, j): j for j in jobs}
        for n, f in enumerate(as_completed(futs), 1):
            b, ri, ci, *_ = futs[f]
            try:
                msg = f.result()
                if msg != "skipped":
                    log(f"[{n}/{len(jobs)}] {msg}")
            except Exception as e:  # noqa: BLE001 - one chunk failing must not stop the run
                totals["failed"] += 1
                log(f"[{n}/{len(jobs)}] b{b} r{ri} c{ci} FAILED: {e}")
                if "HTTP 401" in str(e) or "HTTP 403" in str(e):
                    log("The API key was refused; stopping.")
                    pool.shutdown(cancel_futures=True)
                    break

    log("\n--- run summary ---")
    log(json.dumps(totals, indent=1))
    if totals["failed"] and not totals["chunks"]:
        sys.exit(1)


if __name__ == "__main__":
    main()
