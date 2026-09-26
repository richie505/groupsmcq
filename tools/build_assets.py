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


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    index = json.loads((SRC / "index.json").read_text())
    (OUT / "catalog.json").write_text(json.dumps(index, ensure_ascii=False, separators=(",", ":")))
    (OUT / "plan.json").write_text((SRC / "plan.json").read_text())

    stats = {k: 0 for k in ("pyq_unique", "pyq_cross_filed", "pyq_dupes_in_row", "flashcards_dropped",
                            "notes_mcqs", "notes_exact_dupes", "notes_near_dupes", "notes_dupe_of_pyq")}
    per_book = {}
    seen_ids = set()
    for b in range(1, 7):
        pyq = build_pyqs(b, seen_ids, stats)
        (OUT / f"pyq{b}.json").write_text(json.dumps(pyq, ensure_ascii=False, separators=(",", ":")))
        before = stats["notes_mcqs"]
        notes = build_notes(b, pyq_token_index(pyq), stats)
        (OUT / f"notes{b}.json").write_text(json.dumps(notes, ensure_ascii=False, separators=(",", ":")))
        per_book[b] = {
            "pyq": len({q["id"] for s in ("rows", "units") for v in pyq[s].values() for q in v}),
            "notes": stats["notes_mcqs"] - before,
        }
    stats["books"] = per_book
    (OUT / "stats.json").write_text(json.dumps(stats, indent=1))
    print(json.dumps(stats, indent=1))


if __name__ == "__main__":
    main()
