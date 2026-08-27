"""Aggregate a completed, bounded set of actual order HTTP observations."""
import json
import urllib.error
import urllib.request


def summarize_requests(requests, expected_count):
    """A complete successful receipt requires every actual response and unique trace."""
    trace_ids = [row.get("traceId", "") for row in requests]
    return {
        "status": "AVAILABLE",
        "requests": requests,
        "requestCount": len(requests),
        "errorCount": sum(row["httpStatus"] >= 500 for row in requests),
        "allSuccessful": (
            expected_count > 0 and len(requests) == expected_count
            and all(row["httpStatus"] == 200 for row in requests)
            and all(trace_ids) and len(set(trace_ids)) == expected_count
        ),
    }


def observe_order_requests(url, count=20, timeout=5):
    """Transport failures propagate; a partial loop never emits an AVAILABLE receipt."""
    requests = []
    for _ in range(count):
        try:
            response = urllib.request.urlopen(url, timeout=timeout)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            data = json.load(response)
            requests.append({
                "httpStatus": response.code,
                "traceId": response.headers.get("X-Trace-Id", ""),
                "version": data.get("order", {}).get("version", data.get("version", "")),
            })
    return summarize_requests(requests, count)
