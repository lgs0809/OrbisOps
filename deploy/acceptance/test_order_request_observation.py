"""Real loopback HTTP counterexamples for completed order-observation receipts."""
import json
import socket
import threading
import time
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from order_request_observation import observe_order_requests, summarize_requests


class OrderRequestObservationTest(unittest.TestCase):
    def run_server(self, mode, count=20, timeout=.1):
        received = []

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_):
                pass

            def do_GET(self):
                index = len(received)
                status = 500 if mode == "500" and index == 4 else 403 if mode == "403" else 200
                trace = "same" if mode == "duplicate" else "" if mode == "missing" else str(index)
                received.append({"httpStatus": status, "traceId": trace, "version": "v1"})
                if mode == "interrupted" and index == 4:
                    self.connection.shutdown(socket.SHUT_RDWR)
                    self.connection.close()
                    return
                if mode == "timeout" and index == 4:
                    time.sleep(.3)
                payload = json.dumps({"order": {"version": "v1"}}).encode()
                self.send_response(status)
                self.send_header("X-Trace-Id", trace)
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                try:
                    self.wfile.write(payload)
                except (BrokenPipeError, ConnectionResetError):
                    pass

        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            result = observe_order_requests(f"http://127.0.0.1:{server.server_port}/orders", count, timeout)
            self.assertEqual(received, result["requests"])
            self.assertEqual(len(received), result["requestCount"])
            return result
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

    def test_twenty_actual_successes(self):
        result = self.run_server("healthy")
        self.assertTrue(result["allSuccessful"])
        self.assertEqual(0, result["errorCount"])

    def test_http500_cannot_pass(self):
        result = self.run_server("500")
        self.assertFalse(result["allSuccessful"])
        self.assertEqual(1, result["errorCount"])

    def test_http403_cannot_pass_even_with_zero_server_errors(self):
        result = self.run_server("403")
        self.assertFalse(result["allSuccessful"])
        self.assertEqual(0, result["errorCount"])

    def test_empty_sampling_cannot_pass(self):
        self.assertFalse(self.run_server("healthy", count=0)["allSuccessful"])
        self.assertFalse(summarize_requests([], 20)["allSuccessful"])

    def test_missing_or_duplicate_trace_cannot_pass(self):
        for mode in ("missing", "duplicate"):
            with self.subTest(mode=mode):
                self.assertFalse(self.run_server(mode)["allSuccessful"])

    def test_actual_timeout_produces_no_positive_receipt(self):
        with self.assertRaises((TimeoutError, OSError)):
            self.run_server("timeout")

    def test_actual_mid_loop_disconnect_produces_no_positive_receipt(self):
        with self.assertRaises((OSError, __import__("http.client").client.HTTPException)):
            self.run_server("interrupted")


if __name__ == "__main__":
    unittest.main()
