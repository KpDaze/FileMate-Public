#!/usr/bin/env python3
"""Execute the real Kotlin schema SQL against host SQLite, without Android/emulators.

Checks additive migration from the known main (schema 6) and installed recovery
(schema 8) lineages, preserving every seeded old row. This deliberately does not
claim Android SQLiteOpenHelper or physical-phone verification.
"""
from pathlib import Path
import re
import sqlite3
import subprocess

ROOT = Path(__file__).resolve().parents[1]
KOTLIN = "app/src/main/java/app/filemate/"
BASELINES = {
    6: "743b9a6b1a6ea2f45b29bdad8edf58a13ddc6f4e",
    8: "cc9b8ec022299e806eb7db9f42d0ac05b10a5215",
}
LITERAL = r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"'
SQL = re.compile(r"db\.execSQL\(\s*(" + LITERAL + r")\s*\)")


def sources(ref=None):
    files = ("Store.kt", "GalleryStore.kt", "GalleryOrganisation.kt", "LearningStore.kt")
    if ref is None:
        return "\n".join((ROOT / KOTLIN / f).read_text() for f in files)
    return "\n".join(subprocess.check_output(
        ["git", "show", f"{ref}:{KOTLIN}{f}"], cwd=ROOT, text=True)
        for f in files if f != "LearningStore.kt")


def block(text, opening):
    # Replace literals/comments with spaces so braces inside them are not syntax.
    masked = re.sub(LITERAL + r"|//[^\n]*|/\*[\s\S]*?\*/",
                    lambda m: " " * len(m[0]), text)
    depth = 0
    for i in range(opening, len(text)):
        if masked[i] == "{":
            depth += 1
        elif masked[i] == "}":
            depth -= 1
            if depth == 0:
                return text[opening + 1:i], i + 1
    raise AssertionError("Unclosed Kotlin block")


def function(text, name):
    found = re.search(r"\bfun\s+" + name + r"\s*\([^)]*\)\s*\{", text)
    assert found, f"Missing schema function {name}"
    return block(text, found.end() - 1)[0]


def statements(body, source):
    events = [(m.start(), "sql", m[1]) for m in SQL.finditer(body)]
    for m in re.finditer(r"\b((?:create\w+|seedReviewedProjects))\(db\)", body):
        events.append((m.start(), "call", m[1]))
    for _, kind, value in sorted(events):
        if kind == "call":
            yield from statements(function(source, value), source)
        elif value.startswith('"""'):
            yield value[3:-3]
        else:
            # The schema currently uses no Kotlin interpolation or escaped SQL.
            assert "$" not in value and "\\" not in value, value
            yield value[1:-1]


def create(db, source):
    for sql in statements(function(source, "onCreate"), source):
        db.execute(sql)


def migrate(db, old, source):
    upgrade = function(source, "onUpgrade")
    for match in re.finditer(r"if\(old < (\d+)\)\s*", upgrade):
        if old >= int(match[1]):
            continue
        start = match.end()
        body = block(upgrade, start)[0] if upgrade[start] == "{" else upgrade[start:].splitlines()[0]
        for sql in statements(body, source):
            db.execute(sql)


def tables(db):
    return [row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")]


def insert(db, table, **values):
    for _, name, typ, required, default, pk in db.execute(f'PRAGMA table_info("{table}")'):
        if name not in values and required and default is None:
            values[name] = 11 if typ == "INTEGER" else f"preserve-{name}"
    names = ",".join(f'"{name}"' for name in values)
    db.execute(f'INSERT INTO "{table}" ({names}) VALUES ({",".join("?" for _ in values)})', tuple(values.values()))


def seed(db):
    project = 17
    identity = "external:images:77"
    moved = "/storage/emulated/0/Documents/FileMate/Life Map/old.png"
    reviewed = "/storage/emulated/0/Download/Mountain_Assets_v01.png"
    camera = "/storage/emulated/0/DCIM/Camera/personal.jpg"
    insert(db, "hub", package="com.openai.chatgpt", label="ChatGPT")
    insert(db, "projects", id=project, name="Life Map")
    insert(db, "files", path=moved, project_id=project, project_confidence="Confirmed")
    insert(db, "files", path=reviewed, project_id=project, project_confidence="Confirmed")
    insert(db, "files", path=camera, project_id=project, project_confidence="Confirmed")
    insert(db, "observations", path=reviewed, name="Mountain_Assets_v01.png", source="Qwen", confidence="Medium")
    insert(db, "history", title="Preserve Activity", detail="Previous user operation")
    insert(db, "state", key="naming_preference", value="TIDY_WHEN_REVIEWED")
    insert(db, "state", key="external_storage_uri", value="content://fixture.provider/tree/persisted-folder")
    insert(db, "cleanup_scans", id=5)
    insert(db, "cleanup_entries", path=reviewed, name="Mountain_Assets_v01.png", scan_id=5)
    insert(db, "selected_folders", uri="content://fixture.provider/tree/manual", name="Manual folder")
    for status in ("applied", "pending", "undo_pending", "review", "undone"):
        insert(db, "file_actions", source_path="/storage/emulated/0/Download/old.png", target_path=moved,
               project_id=project, previous_project_id=project, status=status, hash="a" * 64)
    insert(db, "media", identity=identity, current_path=camera, original_path=camera,
           name="personal.jpg", camera=1, available=1, project_id=project)
    insert(db, "media_favourites", identity=identity)
    insert(db, "media_albums", id=3, name="Personal album")
    insert(db, "media_album_items", album_id=3, identity=identity)
    if "project_learning" in tables(db):
        insert(db, "project_learning", token="mountain", project_id=project, hits=9)
        insert(db, "project_storage_rules", project_id=project, rule="PHONE_AND_DRIVE")
    return reviewed, moved, camera


def snapshot(db):
    result = {}
    for table in tables(db):
        cols = [row[1] for row in db.execute(f'PRAGMA table_info("{table}")')]
        selected = ",".join(f'"{name}"' for name in cols)
        result[table] = cols, sorted(db.execute(f'SELECT {selected} FROM "{table}"').fetchall(), key=repr)
    return result


def main():
    current = sources()
    version = int(re.search(r"SQLiteOpenHelper\(context, databaseName, null, (\d+)\)", current)[1])
    assert version >= 9, "Schema must advance past installed recovery v8"
    fresh = sqlite3.connect(":memory:")
    fresh.execute("PRAGMA foreign_keys=ON")
    create(fresh, current)
    for old, ref in BASELINES.items():
        db = sqlite3.connect(":memory:")
        db.execute("PRAGMA foreign_keys=ON")
        create(db, sources(ref))
        reviewed, moved, camera = seed(db)
        before = snapshot(db)
        migrate(db, old, current)
        for table, (cols, expected) in before.items():
            selected = ",".join(f'"{name}"' for name in cols)
            actual = sorted(db.execute(f'SELECT {selected} FROM "{table}"').fetchall(), key=repr)
            assert actual == expected, f"v{old}: existing {table} rows changed"
        assert not db.execute("PRAGMA foreign_key_check").fetchall()
        assert db.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
        assert tables(db) == tables(fresh), f"v{old}: migration differs from fresh schema"
        for table in tables(fresh):
            assert db.execute(f'PRAGMA table_info("{table}")').fetchall() == fresh.execute(f'PRAGMA table_info("{table}")').fetchall(), table
        assert db.execute("SELECT COUNT(*) FROM file_actions WHERE automatic != 0").fetchone()[0] == 0
        learned = dict(db.execute("SELECT path,project_id FROM learning_examples"))
        assert reviewed in learned, "Reviewed historical assignment was not seeded"
        assert moved not in learned, "Old move journal must not self-train"
        assert camera not in learned, "Camera media must not seed automatic learning"
        print(f"PASS host SQLite {sqlite3.sqlite_version}: schema {old} → {version}; {len(before)} seeded tables preserved; fresh schema matches; safe review-only seed")

    # Exercise the production veto SQL for the live/catch-up race. New-at-first-
    # event proof is kept separately in memory by MonitorService, not inferred here.
    eligibility = re.search(r'fun Store\.eligibleAfterLiveEvent[\s\S]*?rawQuery\("""([\s\S]*?)"""', current)
    assert eligibility, "Missing live-event eligibility query"
    sql = eligibility[1]
    path = "/storage/emulated/0/Download/Qwen_Life_Map_New.png"
    allowed = lambda: fresh.execute(sql, (path, path, path, path)).fetchone() is None
    assert allowed()
    insert(fresh, "projects", id=17, name="Life Map")
    insert(fresh, "files", path=path, project_id=None)
    assert allowed(), "Catch-up indexing alone must not discard first-live-event proof"
    fresh.execute("UPDATE files SET project_id=17 WHERE path=?", (path,))
    assert not allowed(), "Manual assignment during stability must veto automation"
    fresh.execute("UPDATE files SET project_id=NULL WHERE path=?", (path,))
    insert(fresh, "learning_examples", path=path, name="Reviewed source", project_id=None)
    assert not allowed(), "A source/project correction or clearing must veto automation"
    fresh.execute("DELETE FROM learning_examples WHERE path=?", (path,))
    for status in ("applied", "pending", "undo_pending", "review", "undone", "failed"):
        insert(fresh, "file_actions", source_path=path, target_path="/Documents/previous.png", status=status)
        assert not allowed(), f"Prior journal status {status} must veto automation"
        fresh.execute("DELETE FROM file_actions")
    print("PASS host SQLite: catch-up indexing retains live eligibility; assignment, correction, and every prior journal status veto it")


if __name__ == "__main__":
    main()
