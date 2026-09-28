# APPSC MCQ 90 (Android)

MCQ-only practice app for APPSC Group-I + Group-II. It follows the same 90-day plan as the notes app
([Group-app](https://github.com/richie505/Group-app)) and has two question sources:

| Source | What it is | Where it comes from |
|---|---|---|
| **PYQs** | 29,061 unique previous-year MCQs (APPSC, UPSC, other states), filed under the notes sections | the notes app's PYQ bank (`source/pyq/`) |
| **Notes MCQs** | APPSC-style MCQs written from the Combined Notes (6 books) | generated with your OpenAI key by `tools/generate_notes_mcqs.py` |

## In the app

- **Today** — Day N of 90, days to exam, and two daily targets for the day's sections:
  **PYQ practice** (default 100, APPSC papers first, newest years first) and **Notes MCQ practice**
  (default 60, in notes order). Each run covers every remaining question of the set, with
  Previous / Skip / Next, like the notes app's PYQs; "Retry wrong" on every card.
  Revision/Sunday days add a *Retry mistakes* card; mock days (Phase 3) give a timed mock paper.
- **Plan** — all 90 days with each day's target progress.
- **Subjects** — the 6 books → topics → sections, each with its PYQs and its notes MCQs.
- **Review** — *Saved for revision*: every question you starred (☆ on any question), listed with its answer and
  explanation to read over (no quiz); and *Mistakes*: every wrong answer until you get it right.
- **Progress** — accuracy by subject and source, 14-day activity, mock history, and settings
  (daily PYQ target, notes MCQ target, mock size).
- **Mock test** — N questions (default 150) over all six subjects from the sections studied so far,
  one minute per question, no hints, answers editable until you submit, 1/3 negative marking.

Every question: "Stuck? Show a hint" (before answering) and "Why it went wrong" (after a wrong answer) from
the APPSC MCQ Techniques guide (`data/Techniques.kt`, same as the notes app). Notes MCQs also show
**the technique the question trains** and the notes subsection it comes from.

**No repeats:** each question gets an id from its normalised text, so a PYQ filed under two sections (10,159
such filings) is one question — answer it once and it counts everywhere. Notes MCQs are rejected when they
duplicate a PYQ or another notes MCQ (exact or near-duplicate on content words).

## Generating the notes MCQs (OpenAI)

The prompt is built from three files in `tools/prompt/`:

1. `base_from_prep_app.txt` — the MCQ prompt of the Group 2 prep app
   (`appsc-group2-prep-app/content-pipeline/mcq-prompt.txt`), unchanged: paper-setter role, scan for
   every fact, completeness, the 11 question patterns, no "according to the notes".
2. `appsc_techniques.txt` — from the Drive guide *APPSC MCQ Techniques – Tested on PYQs*: APPSC's real
   question-type mix, how APPSC writes false statements (only / reign-of / wrong authority / ministry
   swap / world body / comparison / location…), the measured answer-key balance (65% of statements true,
   A–R key mostly (a) then (b), "All of the above" 40%, middle-number keys, match codes that share
   pairs…), per-subject traps, and the uniqueness rules.
3. `output_format.txt` — JSON schema (adds `type`, `technique`, `subsection`) and the self-check.

Each call also lists the section's PYQs and already-written questions under "ALREADY IN THE BANK", so the
model writes new facts instead of repeats.

### Run it on GitHub (recommended)

1. Add your key: repository **Settings → Secrets and variables → Actions → New repository secret**,
   name `OPENAI_API_KEY`.
2. **Actions → Generate notes MCQs (OpenAI) → Run workflow**. For a pilot set `limit` to 3 and read the
   files it commits under `tools/generated/`. Then run again with `limit` 0 (all) — or `days` = `1-10`
   to do the first plan days first.
3. It commits the questions and the rebuilt assets, then builds the APK (artifact `appsc-mcq-apk`).
   A run stops cleanly after `time_budget` minutes; running it again continues where it stopped.

### Run it locally

```
export OPENAI_API_KEY=sk-...          # never commit it
python3 tools/generate_notes_mcqs.py --dry-run --limit 1   # prints the full prompt, no API call
python3 tools/generate_notes_mcqs.py --limit 3             # pilot
python3 tools/generate_notes_mcqs.py --days 1-10           # plan days 1-10
python3 tools/generate_notes_mcqs.py                       # everything (≈8.3M characters of notes)
python3 tools/build_assets.py
```

## Building

```
python3 tools/build_assets.py        # source/ + tools/generated/ -> app/src/main/assets/
./gradlew testDebugUnitTest          # every question loads; every screen renders; quiz + mock click-through
./gradlew assembleRelease            # app/build/outputs/apk/release/app-release.apk
```

Assets: `index.json` holds the question ids of every section and plan day (all counts and targets come
from it), and `q/{p|n}{book}/r{row}.json` holds one section's questions, so the app only ever reads the
section files a quiz needs. `build_assets.py` fails the build if the index and the files disagree.

Every push also builds the APK in GitHub Actions (**Build APK**). Builds are signed with
`keystore/appsc-mcq.jks`, so a new APK installs over the old one and keeps your progress. The app id is
`com.appsc.mcq`, so it installs next to the notes app.

## Updating the source data

`source/` is copied from the notes app's `app/src/main/assets/` (`book*.json` → `source/notes/`,
`mcq*.json` → `source/pyq/pyq*.json`, `plan.json`, `index.json`). Copy them again after the notes app
changes, then rebuild the assets.
