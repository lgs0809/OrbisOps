"""Independent durable destinations: an unavailable sink never starves another sink."""
from contextlib import closing
import logging
import random
import sqlite3
import subprocess
import threading


class EventProjection:
    def __init__(self, database, flag, publish):
        if flag not in {"mysql_done", "es_done"}:
            raise ValueError("Unknown durable projection destination")
        self.database, self.flag, self.publish = database, flag, publish

    def drain(self):
        with closing(self.database()) as connection:
            pending = connection.execute(
                f"SELECT id,event_json FROM events WHERE {self.flag}=0 ORDER BY rowid LIMIT 50").fetchall()
        for row in pending:
            # Destinations must be idempotent by event ID. A crash after publish
            # and before this acknowledgement safely repeats the original event.
            self.publish(row)
            with closing(self.database()) as connection:
                with connection:
                    connection.execute(f"UPDATE events SET {self.flag}=1 WHERE id=?", (row["id"],))
        return bool(pending)

    def run(self, stop=None):
        stop = stop if stop is not None else threading.Event()
        failures = 0
        while not stop.is_set():
            try:
                pending = self.drain()
                failures = 0
                stop.wait(0.05 if pending else 1)
            except (OSError, RuntimeError, ValueError, sqlite3.Error, subprocess.TimeoutExpired) as error:
                failures = min(failures + 1, 7)
                delay = min(60, 2 ** (failures - 1) * random.uniform(0.8, 1.2))
                logging.warning("%s projection deferred (%s); retry in %.1fs", self.flag, type(error).__name__, delay)
                stop.wait(delay)
