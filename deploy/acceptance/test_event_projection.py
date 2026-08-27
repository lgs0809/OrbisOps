import json
import sqlite3
import tempfile
import threading
import unittest
from pathlib import Path
from event_projection import EventProjection


class EventProjectionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / "events.sqlite"
        with self.database() as db:
            db.execute("CREATE TABLE events(id TEXT PRIMARY KEY,event_json TEXT,mysql_done INT DEFAULT 0,es_done INT DEFAULT 0)")
            db.executemany("INSERT INTO events(id,event_json) VALUES(?,?)", [(str(i), json.dumps({"trace": i})) for i in range(75)])

    def tearDown(self):
        self.temp.cleanup()

    def database(self):
        db = sqlite3.connect(self.path)
        db.row_factory = sqlite3.Row
        return db

    def test_unavailable_sink_cannot_hide_later_mysql_events(self):
        def unavailable(row):
            raise OSError("offline")
        es = EventProjection(self.database, "es_done", unavailable)
        with self.assertRaises(OSError):
            es.drain()
        written = set()
        mysql = EventProjection(self.database, "mysql_done", lambda row: written.add(row["id"]))
        while mysql.drain():
            pass
        self.assertEqual(75, len(written))
        with self.database() as db:
            self.assertEqual((75, 0), tuple(db.execute("SELECT SUM(mysql_done),SUM(es_done) FROM events").fetchone()))
        EventProjection(self.database, "es_done", lambda row: None).drain()
        with self.database() as db:
            self.assertEqual(50, db.execute("SELECT SUM(es_done) FROM events").fetchone()[0])

    def test_response_loss_retains_event_and_retries_same_identity(self):
        committed = set()
        def lost_ack(row):
            committed.add(row["id"])
            raise TimeoutError("response lost after remote commit")
        with self.assertRaises(TimeoutError):
            EventProjection(self.database, "mysql_done", lost_ack).drain()
        with self.database() as db:
            self.assertEqual(0, db.execute("SELECT SUM(mysql_done) FROM events").fetchone()[0])
        projection = EventProjection(self.database, "mysql_done", lambda row: committed.add(row["id"]))
        while projection.drain():
            pass
        self.assertEqual(75, len(committed))
        self.assertFalse(projection.drain())

    def test_failure_is_retained_until_background_retry_is_stopped(self):
        stop = threading.Event()
        class ImmediateStop:
            delays = []
            def is_set(self):
                return stop.is_set()
            def wait(self, delay):
                self.delays.append(delay)
                if len(self.delays) == 9:
                    stop.set()
        def unavailable(row):
            raise OSError("offline")
        gate = ImmediateStop()
        EventProjection(self.database, "es_done", unavailable).run(gate)
        self.assertEqual(9, len(gate.delays))
        self.assertTrue(all(0 < delay <= 60 for delay in gate.delays))
        self.assertGreater(gate.delays[-1], gate.delays[0])
        with self.database() as db:
            self.assertEqual(0, db.execute("SELECT SUM(es_done) FROM events").fetchone()[0])

    def test_blocked_sink_does_not_block_independent_worker(self):
        entered, release, completed, stop = (threading.Event() for _ in range(4))
        def blocked(row):
            entered.set()
            release.wait(3)
            raise OSError("still unavailable")
        written = set()
        def healthy(row):
            written.add(row["id"])
            if len(written) == 75:
                completed.set()
        workers = [threading.Thread(target=EventProjection(self.database, "es_done", blocked).run, args=(stop,)),
                   threading.Thread(target=EventProjection(self.database, "mysql_done", healthy).run, args=(stop,))]
        for worker in workers:
            worker.start()
        try:
            self.assertTrue(entered.wait(2))
            self.assertTrue(completed.wait(2), "A blocked destination starved the independent durable destination")
        finally:
            stop.set()
            release.set()
            for worker in workers:
                worker.join(3)
        self.assertFalse(any(worker.is_alive() for worker in workers))

    def test_sql_flag_cannot_be_supplied_as_freeform_input(self):
        with self.assertRaises(ValueError):
            EventProjection(self.database, "mysql_done;DELETE FROM events", lambda row: None)


if __name__ == "__main__":
    unittest.main()
