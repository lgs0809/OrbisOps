# Recorded change verification fixture

The four scenarios use Prometheus scrape values and MySQL statistics captured during isolated acceptance.
`change-verification-recorded.json` retains the complete before and after time series, histogram buckets and
independent sample, error-rate and p95 statistics. Identifiers were replaced and timestamps translated to a
common clock origin. Only fields read by the replay are included. This fixture exercises the evidence review
policy. Java Landing and approval checks have their own tests.
