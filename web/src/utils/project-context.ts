export const PROJECT_CONTEXT_STORAGE_KEY = 'ops:selected-project-id';

export const readProjectContextId = (): string => {
  if (typeof localStorage === 'undefined') return '';
  return localStorage.getItem(PROJECT_CONTEXT_STORAGE_KEY)?.trim() || '';
};

export const writeProjectContextId = (projectId?: string): void => {
  if (typeof localStorage === 'undefined') return;
  const normalized = projectId?.trim() || '';
  if (normalized) {
    localStorage.setItem(PROJECT_CONTEXT_STORAGE_KEY, normalized);
  } else {
    localStorage.removeItem(PROJECT_CONTEXT_STORAGE_KEY);
  }
};

export const resolveProjectContextId = (
  routeProjectId?: string | null,
  availableProjectIds: string[] = [],
): string => {
  const requested = routeProjectId?.trim() || readProjectContextId();
  if (!availableProjectIds.length) return requested;
  if (requested && availableProjectIds.includes(requested)) return requested;
  return availableProjectIds[0] || '';
};
