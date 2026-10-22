"""Mutation check: plants one bug at a time in the library and runs the whole suite; every bug must fail it.

python3 scripts/mutants.py           # all
python3 scripts/mutants.py M3 M7     # some
"""

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = "src/main/java/dev/nailkarimov/library/"
MUTANTS = [
    ("M1", "Library.java", "LOAN_DAYS = 14;", "LOAN_DAYS = 15;", "loans last 15 days"),
    ("M2", "Library.java", "return fine.min(FINE_CAP)", "return fine", "no cap on fines"),
    ("M3", "Library.java", "ChronoUnit.DAYS.between(due, returned)", "ChronoUnit.DAYS.between(due, returned) + 1", "fine one day early"),
    ("M4", "Library.java", "compareTo(BLOCK_ABOVE) > 0", "compareTo(BLOCK_ABOVE) >= 0", "exactly 5.00 already blocks"),
    ("M5", "Library.java", "active >= LOAN_LIMIT", "active > LOAN_LIMIT", "four books allowed"),
    ("M6", "Library.java", "if (l.isbn.equals(isbn)) {", "if (false) {", "two copies of one title"),
    ("M7", "Library.java", "if (l.returned != null) {", "if (false) {", "a loan can be returned twice"),
    ("M8", "Library.java", 'text(in, "email", 3, 254).toLowerCase(Locale.ROOT)', 'text(in, "email", 3, 254)', "e-mails compared case-sensitively"),
    ("M9", "Isbn.java", "if (checkDigit(d.substring(0, 12)) != d.charAt(12) - '0') {", "if (false) {", "ISBN check digit not verified"),
    ("M10", "CatalogueClient.java", "attempt <= 2", "attempt <= 1", "no retry"),
    ("M11", "CatalogueClient.java", "res.statusCode() == 404", "res.statusCode() == 4040", "unknown ISBN reported as a catalogue failure"),
    ("M12", "CatalogueClient.java", ".timeout(timeout)", ".timeout(Duration.ofSeconds(5))", "no request timeout"),
    ("M13", "LibraryServer.java", 'type == null || !type.toLowerCase(Locale.ROOT).startsWith("application/json")', "false", "any content type accepted"),
    ("M14", "Json.java", "if (m.containsKey(k)) {", "if (false) {", "duplicate JSON keys accepted"),
]


def main(selected: set[str]) -> int:
    survived = []
    for mid, file, old, new, what in MUTANTS:
        if selected and mid not in selected:
            continue
        path = ROOT / SRC / file
        original = path.read_text()
        if original.count(old) != 1:
            raise SystemExit(f"{mid}: pattern must occur exactly once in {file}")
        path.write_text(original.replace(old, new))
        try:
            r = subprocess.run(["mvn", "-B", "-q", "-o", "test"], cwd=ROOT, capture_output=True, text=True, check=False)
        finally:
            path.write_text(original)
        caught = r.returncode != 0
        classes = sorted({ln.rsplit(".", 1)[-1] for ln in r.stdout.splitlines() if "<<< FAILURE! -- in " in ln})
        print(f"| {mid} | {what} | {'caught' if caught else '**SURVIVED**'} | {', '.join(classes)} |", flush=True)
        if not caught:
            survived.append(mid)
    print(f"\n{len(survived)} survived: {' '.join(survived)}" if survived else "\nAll mutants caught.")
    return 1 if survived else 0


if __name__ == "__main__":
    print("| # | Bug | Result | Failed test classes |\n| --- | --- | --- | --- |")
    sys.exit(main(set(sys.argv[1:])))
