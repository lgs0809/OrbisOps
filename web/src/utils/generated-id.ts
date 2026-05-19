export const generatedId = (prefix: string, name?: string) => {
  const source = String(name || '').trim();
  const slug = source
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 48);
  if (slug) {
    return `${prefix}-${slug}`;
  }
  let hash = 2166136261;
  for (let i = 0; i < source.length; i += 1) {
    hash ^= source.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }
  const suffix = Math.abs(hash >>> 0).toString(36).slice(0, 8) || Date.now().toString(36);
  return `${prefix}-${suffix}`;
};
