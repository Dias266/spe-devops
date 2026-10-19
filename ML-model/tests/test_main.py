import os
import subprocess
import sys

import main

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def test_locate_existing_file():
    os.chdir(HERE)
    assert main.locate_project_file("main.py") == "main.py"


def test_locate_missing_file():
    os.chdir(HERE)
    assert main.locate_project_file("does-not-exist.xyz") is None


def test_prediction_runs_end_to_end():
    """Runs the Weka model through JPype on instance 8, as the CI did before."""
    result = subprocess.run(
        [sys.executable, "main.py"],
        input="8\n",
        cwd=HERE,
        capture_output=True,
        text=True,
        timeout=120,
    )
    assert result.returncode == 0, result.stderr
    assert "Final Filtered Decision" in result.stdout


class FakeConnection:
    """Stands in for a psycopg connection and records what was executed."""

    def __init__(self):
        self.executed = []

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False

    def execute(self, sql, params):
        self.executed.append((sql, params))


def test_save_to_database_skipped_without_url(monkeypatch):
    monkeypatch.setattr(main, "DATABASE_URL", None)
    assert main.save_to_database("8", "row", "1", 54.5, "decision") is False


def test_save_to_database_inserts_one_row(monkeypatch):
    import psycopg

    conn = FakeConnection()
    seen = {}

    def fake_connect(url, connect_timeout):
        seen["url"] = url
        return conn

    monkeypatch.setattr(main, "DATABASE_URL", "postgresql://fleet@db/fleet")
    monkeypatch.setattr(psycopg, "connect", fake_connect)

    assert main.save_to_database("8", "Van,...,?", "1", 54.54, "Maintenance 0") is True
    assert seen["url"] == "postgresql://fleet@db/fleet"
    sql, params = conn.executed[0]
    assert "INSERT INTO maintenance_prediction" in sql
    assert params == (8, "Van,...,?", "1", 54.5, "Maintenance 0")
