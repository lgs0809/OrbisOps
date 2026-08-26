import { describe, expect, it } from 'vitest';
import { projectProductViewFor } from './project-product-navigation';
describe('Project view navigation', () => {
  it('opens the runtime section from the registered advanced route', () => {
    expect(projectProductViewFor(null, '/projects/advanced')).toBe('runtime');
    expect(projectProductViewFor(null, '/projects')).toBe('overview');
  });
  it('keeps valid explicit tabs and falls back safely for unknown values', () => {
    expect(projectProductViewFor('members', '/projects/advanced')).toBe('members');
    expect(projectProductViewFor('unregistered', '/projects')).toBe('overview');
  });
});
