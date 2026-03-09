import { beforeEach, describe, expect, it } from 'vitest';

import {
  PROJECT_CONTEXT_STORAGE_KEY,
  readProjectContextId,
  resolveProjectContextId,
  writeProjectContextId,
} from './project-context';

describe('project context', () => {
  beforeEach(() => localStorage.clear());

  it('normalizes persisted project ids and clears empty values', () => {
    writeProjectContextId('  project-a  ');
    expect(readProjectContextId()).toBe('project-a');
    expect(localStorage.getItem(PROJECT_CONTEXT_STORAGE_KEY)).toBe('project-a');

    writeProjectContextId('  ');
    expect(readProjectContextId()).toBe('');
  });

  it('prefers valid route context and falls back to an accessible project', () => {
    writeProjectContextId('project-b');
    expect(resolveProjectContextId('project-a', ['project-a', 'project-b'])).toBe('project-a');
    expect(resolveProjectContextId('forbidden', ['project-a', 'project-b'])).toBe('project-a');
    expect(resolveProjectContextId(undefined, ['project-a', 'project-b'])).toBe('project-b');
  });
});
