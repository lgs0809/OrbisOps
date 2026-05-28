/** Incident SQL timestamps use the server's UTC database session. */
export function incidentEpoch(value?: string): number {
  if (!value) return Number.NaN;
  const normalized = value.trim().replace(' ', 'T');
  const utc = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?$/.test(normalized)
    ? `${normalized}Z` : normalized;
  return Date.parse(utc);
}

export function incidentLocalTime(value?: string): string {
  const epoch = incidentEpoch(value);
  return Number.isFinite(epoch)
    ? new Date(epoch).toLocaleString('zh-CN', { hour12: false }) : '-';
}
