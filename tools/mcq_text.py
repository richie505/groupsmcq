"""Text helpers shared by the generator and the asset builder: normalising and near-duplicate checks."""
import re

STOP = set("""
a an the of in on at to for from by with and or as is are was were be been being which who whom whose what
when where why how this that these those it its their there following given above below statements statement
correct incorrect consider select code codes using answer options option only both none all not true false
following one two three four five match list arrange order about into than such also has have had did does do
under per during among upon its his her they them known called named given regarding reference respect most
select answer code using lists iii vii viii english telugu versions common pair pairs matched correctly
""".split())

# words setters use interchangeably for the same fact
SAME = {w: "establish" for w in ("established", "establish", "set", "setup", "founded", "formed", "constituted",
                                  "created", "started", "instituted", "launched", "introduced")}


def _stem(w):
    w = SAME.get(w, w)
    for suf in ("ing", "ed", "es", "s"):
        if len(w) > 4 + len(suf) - 1 and w.endswith(suf):
            return w[: -len(suf)]
    return w


def norm(s):
    return re.sub(r"[^a-z0-9]+", " ", str(s).lower()).strip()


def content_tokens(text):
    """Content words of a question (stem + correct answer): what fact it actually tests."""
    return frozenset(_stem(w) for w in norm(text).split() if (len(w) > 2 or w.isdigit()) and w not in STOP)


def near_duplicate(a, b):
    """True when two token sets test the same fact: high overlap, or one almost inside the other."""
    if not a or not b:
        return False
    inter = len(a & b)
    if inter == 0:
        return False
    if inter / len(a | b) >= 0.7:
        return True
    small = min(len(a), len(b))
    return small >= 5 and inter / small >= 0.88


class DupIndex:
    """Near-duplicate lookup over many token sets via an inverted index on each set's rarest tokens."""

    def __init__(self):
        self.sets = []
        self.post = {}

    def add(self, toks):
        i = len(self.sets)
        self.sets.append(toks)
        for t in toks:
            self.post.setdefault(t, []).append(i)

    def has_dup(self, toks, probe=4):
        if not toks:
            return False
        # rarest tokens that the index has seen (unseen tokens can't lead to a candidate)
        rare = sorted((t for t in toks if t in self.post), key=lambda t: len(self.post[t]))[:probe]
        cand = set()
        for t in rare:
            cand.update(self.post.get(t, ()))
        return any(near_duplicate(toks, self.sets[i]) for i in cand)
