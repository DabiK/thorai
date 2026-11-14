#!/usr/bin/env python3
"""Search publishable text with rg; report locations, never matched credentials.

Default scope: tracked files and new non-ignored files in this worktree, not Git
history, dependencies or local credentials. Explicit paths support isolated tests.
This is a heuristic check, not a guarantee that arbitrary secrets are absent.
"""
import json
import pathlib
import subprocess
import sys

RULES = {
    "credential": r"sk-[A-Za-z0-9_-]{16,}|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----",
    "literal-token": r'''(?i)(["']?(token|api[_-]?key|access[_-]?token)["']?\s*[:=]\s*["'][A-Za-z0-9_./+=-]{24,}["']|Bearer\s+[A-Za-z0-9_./+=-]{24,})''',
    "private-ip": r"\b(192\.168\.[0-9]+\.[0-9]+|10\.[0-9]+\.[0-9]+\.[0-9]+|172\.(1[6-9]|2[0-9]|3[01])\.[0-9]+\.[0-9]+)\b",
    "email": r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}",
    "personal-path": r"/" + r"Users/[^/\s\"\\`'<>]+",
}


def allowed(kind, value):
    if kind == "email":
        domain = value.rsplit("@", 1)[1]
        # Reserved domains, Kotlin labels, public attribution and GitHub's SSH user.
        return (domain.endswith(".invalid") or domain in {"example.com", "example.org", "example.net"}
                or value in {"this@Fixture.sessions", "this@until.collect",
                             "this@CountingSpeechToText.request", "noreply@anthropic.com",
                             "git@github.com"})
    if kind == "literal-token":
        return value == "API_" + "KEY: 'regie-contract-no-inference'"
    return kind == "personal-path" and value == "/home/USER"


def main():
    paths = sys.argv[1:]
    if not paths:
        root = subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True).strip()
        paths = subprocess.check_output(
            ["git", "-C", root, "ls-files", "--cached", "--others", "--exclude-standard", "-z"]
        ).decode().split("\0")
        paths = [str(pathlib.Path(root, p)) for p in dict.fromkeys(paths) if p and pathlib.Path(root, p).is_file()]
    # Binary assets are checked separately (visual review for published screenshots).
    try:
        paths = [p for p in paths if b"\0" not in pathlib.Path(p).read_bytes()]
    except OSError:
        print("Audit impossible : fichier absent ou illisible.", file=sys.stderr)
        return 2
    if not paths:
        print("Audit rg : aucun fichier texte.")
        return 0
    findings = set()
    for kind, pattern in RULES.items():
        result = subprocess.run(["rg", "--json", "--no-ignore", "--hidden", "-e", pattern, "--", *paths],
                                capture_output=True, text=True)
        if result.returncode not in (0, 1):
            # Do not echo rg diagnostics: supplied file paths may also be sensitive.
            print("Audit impossible : vérifier les fichiers et la disponibilité de rg.", file=sys.stderr)
            return 2
        for line in result.stdout.splitlines():
            event = json.loads(line)
            if event["type"] != "match":
                continue
            data = event["data"]
            if any(not allowed(kind, match["match"]["text"]) for match in data["submatches"]):
                path = pathlib.Path(data["path"]["text"])
                try:
                    path = path.relative_to(pathlib.Path.cwd())
                except ValueError:
                    path = pathlib.Path(path.name)
                findings.add((str(path), data["line_number"], kind))
    for path, line, kind in sorted(findings):
        print(f"{path}:{line}: {kind}")
    print(f"Audit rg : {len(paths)} fichiers, {len(findings)} signalements (valeurs masquées).")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
