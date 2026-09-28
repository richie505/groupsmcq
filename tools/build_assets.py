#!/usr/bin/env python3
"""Build the app's assets from source/ and the generated notes MCQs.

    python3 tools/build_assets.py            # writes app/src/main/assets/

Inputs
  source/index.json, source/plan.json   catalogue + 90-day plan (from the notes app, Group-app)
  source/pyq/pyq{1..6}.json             PYQ bank filed by notes row / unit (from the notes app)
  tools/generated/b{n}/*.json           notes MCQs written by tools/generate_notes_mcqs.py

Outputs (app/src/main/assets/)
  catalog.json, plan.json
  pyq{n}.json    {"rows": {row: [q]}, "units": {unit: [q]}}
  notes{n}.json  {"rows": {row: [q]}}
  stats.json     counts shown in the app and printed here

Uniqueness
  Every question gets an id from its normalised text (stem + options, options sorted), so the
  same PYQ filed under two rows or two books is ONE question: answering it once counts
  everywhere and it never comes back as "new". Flashcards (answer-only, no options) are
  dropped because this app is MCQ-only.
"""
import hashlib
import json
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "source"
GEN = ROOT / "tools" / "generated"
OUT = ROOT / "app" / "src" / "main" / "assets"

sys.path.insert(0, str(Path(__file__).parent))
from mcq_text import DupIndex, content_tokens, norm  # noqa: E402


def qid(prefix, stem, options):
    key = norm(stem) + "||" + "|".join(sorted(norm(o) for o in options))
    return prefix + hashlib.sha1(key.encode()).hexdigest()[:12]


def build_pyqs(book, seen_ids, stats):
    src = json.loads((SRC / "pyq" / f"pyq{book}.json").read_text())
    out = {"rows": {}, "units": {}}
    for sect in ("rows", "units"):
        for key, qs in src[sect].items():
            lst, local = [], set()
            for q in qs:
                if q.get("k") == "f" or len(q.get("o", [])) < 2:
                    stats["flashcards_dropped"] += 1
                    continue
                i = qid("p", q["s"], q["o"])
                if i in local:  # the same question twice in one row
                    stats["pyq_dupes_in_row"] += 1
                    continue
                local.add(i)
                if i in seen_ids:
                    stats["pyq_cross_filed"] += 1
                else:
                    seen_ids.add(i)
                    stats["pyq_unique"] += 1
                q2 = {k: v for k, v in q.items() if k in ("s", "o", "a", "src", "ap", "k", "t", "x", "n", "cx")}
                q2["id"] = i
                lst.append(q2)
            if lst:
                out[sect][key] = lst
    return out


def load_generated(book):
    """All accepted notes MCQs for a book, row -> list, in chunk order."""
    rows = {}
    d = GEN / f"b{book}"
    if not d.exists():
        return rows
    files = sorted(d.glob("r*-c*.json"), key=lambda p: tuple(int(x) for x in re.findall(r"\d+", p.stem)))
    for f in files:
        data = json.loads(f.read_text())
        rows.setdefault(str(data["row"]), []).extend(data.get("accepted", []))
    return rows


def build_notes(book, pyq_index, stats):
    """Generated notes MCQs, re-checked for duplicates against every PYQ and every kept notes MCQ of the book."""
    rows = load_generated(book)
    out = {"rows": {}}
    kept = DupIndex()
    seen = set()
    for key in sorted(rows, key=int):
        lst = []
        for m in rows[key]:
            opts = m["o"]
            i = qid(f"n{book}-", m["s"], opts)
            if i in seen:
                stats["notes_exact_dupes"] += 1
                continue
            toks = content_tokens(m["s"] + " " + opts[m["a"]])
            if pyq_index.has_dup(toks):
                stats["notes_dupe_of_pyq"] += 1
                continue
            if kept.has_dup(toks):
                stats["notes_near_dupes"] += 1
                continue
            seen.add(i)
            kept.add(toks)
            q = {"id": i, "s": m["s"], "o": opts, "a": m["a"], "x": m.get("x", "")}
            for k in ("ty", "tq", "sec"):
                if m.get(k):
                    q[k] = m[k]
            lst.append(q)
        if lst:
            out["rows"][key] = lst
            stats["notes_mcqs"] += len(lst)
    return out


def pyq_token_index(pyq):
    idx = DupIndex()
    for sect in ("rows", "units"):
        for qs in pyq[sect].values():
            for q in qs:
                table = " ".join(" ".join(r) for r in q.get("t", []))
                idx.add(content_tokens(q["s"] + " " + table + " " + (q["o"][q["a"]] if 0 <= q.get("a", -1) < len(q["o"]) else "")))
    return idx


def year_of(src):
    ys = re.findall(r"(?:19|20)\d\d", src or "")
    return int(ys[-1]) if ys else 0


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")))


def main():
    """Writes small per-section question files plus one id index, so the app never parses a whole bank."""
    if OUT.exists():
        for old in list(OUT.glob("pyq*.json")) + list(OUT.glob("notes*.json")):
            old.unlink()
        if (OUT / "q").exists():
            shutil.rmtree(OUT / "q")
    OUT.mkdir(parents=True, exist_ok=True)
    catalog = json.loads((SRC / "index.json").read_text())
    plan = json.loads((SRC / "plan.json").read_text())
    write_json(OUT / "catalog.json", catalog)
    (OUT / "plan.json").write_text((SRC / "plan.json").read_text())

    stats = {k: 0 for k in ("pyq_unique", "pyq_cross_filed", "pyq_dupes_in_row", "flashcards_dropped",
                            "notes_mcqs", "notes_exact_dupes", "notes_near_dupes", "notes_dupe_of_pyq")}
    per_book = {}
    seen_ids = set()
    index = {"rows": {}, "units": {}, "days": {}}
    meta = {}  # PYQ id -> (appsc, year) for ordering day pools
    for b in range(1, 7):
        pyq = build_pyqs(b, seen_ids, stats)
        notes = build_notes(b, pyq_token_index(pyq), stats)
        for key, qs in pyq["rows"].items():
            write_json(OUT / "q" / f"p{b}" / f"r{key}.json", qs)
            index["rows"].setdefault(f"{b}:{key}", [[], []])[0] = [q["id"] for q in qs]
            for q in qs:
                meta[q["id"]] = ("ap" in q, year_of(q.get("src")))
        for key, qs in pyq["units"].items():
            write_json(OUT / "q" / f"p{b}" / f"u{key}.json", qs)
            index["units"][f"{b}:{key}"] = [q["id"] for q in qs]
        for key, qs in notes["rows"].items():
            write_json(OUT / "q" / f"n{b}" / f"r{key}.json", qs)
            index["rows"].setdefault(f"{b}:{key}", [[], []])[1] = [q["id"] for q in qs]
        per_book[b] = {
            "pyq": len({q["id"] for s in ("rows", "units") for v in pyq[s].values() for q in v}),
            "notes": sum(len(v) for v in notes["rows"].values()),
        }

    # Each plan day's pools, in study order: PYQs APPSC first then newest year; notes MCQs in notes order.
    # Current affairs: the day's "Current affairs" block names one Book 6 section ("Book 6 pp 2-6, G-1 ...");
    # its pool is that section's notes MCQs then its PYQs, and "round" counts how often the plan has
    # returned to it, so each revision round's target is the next slice of the pool, not a repeat.
    def order_pyq(ids):
        return sorted(ids, key=lambda i: (0 if meta[i][0] else 1, -meta[i][1]))

    ca_code_row = {c: i for i, r in enumerate(catalog["books"][5]["rows"]) for c in r["codes"] if c.startswith("G-")}
    ca_rounds = {}
    for d in plan["days"]:
        pp, nn, sp, sn = [], [], set(), set()
        for r in d["rows"]:
            if "ref" not in r:
                continue
            ids = index["rows"].get(f"{r['ref'][0]}:{r['ref'][1]}", [[], []])
            pp += [i for i in ids[0] if not (i in sp or sp.add(i))]
            nn += [i for i in ids[1] if not (i in sn or sn.add(i))]
        day = {"p": order_pyq(pp), "n": nn}
        ca_text = " ".join(t["task"] for t in d.get("tasks", []) if "current affairs" in t["block"].lower())
        m = re.search(r"(G-\d+)\s+([^:]+)", ca_text)
        if m and m.group(1) in ca_code_row:
            row = ca_code_row[m.group(1)]
            ids = index["rows"].get(f"6:{row}", [[], []])
            day["c"] = ids[1] + order_pyq([i for i in ids[0] if i not in set(ids[1])])
            day["cr"] = ca_rounds.get(row, 0)
            day["ct"] = f"{m.group(1)} {m.group(2).strip()}"
            day["crow"] = row
            ca_rounds[row] = day["cr"] + 1
        index["days"][str(d["n"])] = day
    write_json(OUT / "index.json", index)

    stats["books"] = per_book
    (OUT / "stats.json").write_text(json.dumps(stats, indent=1))
    check(index)
    print(json.dumps(stats, indent=1))


def check(index):
    """Fail the build if the index and the question files disagree (a missing file would crash a quiz)."""
    ids_in_files = set()
    for f in (OUT / "q").rglob("*.json"):
        for q in json.loads(f.read_text()):
            assert q.get("id") and q.get("s") and len(q.get("o", [])) >= 2, f"bad question in {f}"
            assert -1 <= q.get("a", -1) < len(q["o"]), f"answer out of range in {f}: {q['id']}"
            ids_in_files.add(q["id"])
    listed = {i for v in index["rows"].values() for part in v for i in part} | {i for v in index["units"].values() for i in v}
    missing = listed - ids_in_files
    assert not missing, f"{len(missing)} indexed ids have no question file"
    for key, (p, n) in index["rows"].items():
        b, r = key.split(":")
        assert not p or (OUT / "q" / f"p{b}" / f"r{r}.json").exists(), key
        assert not n or (OUT / "q" / f"n{b}" / f"r{r}.json").exists(), key


if __name__ == "__main__":
    main()
