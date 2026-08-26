/** Persisted audit timestamps without an offset use the server's UTC database session. */
export function serverEpoch(value?: string): number {
  if (!value) return Number.NaN;
  const normalized = value.trim().replace(' ', 'T');
  const utc = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?$/.test(normalized)
    ? `${normalized}Z` : normalized;
  return Date.parse(utc);
}

/** Business dates and query time ranges must keep their own timezone contracts. */
export function serverLocalTime(value?: string, timeZone?: string): string {
  const epoch = serverEpoch(value);
  return Number.isFinite(epoch)
    ? new Date(epoch).toLocaleString('zh-CN', { hour12: false, timeZone }) : '-';
}
