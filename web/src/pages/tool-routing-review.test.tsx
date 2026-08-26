import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ToolRoutingObservabilityPage } from './tool-routing-observability';

const state = vi.hoisted(() => ({ policy: {} as Record<string, unknown>, save: vi.fn() }));
vi.mock('../hooks/use-project-scope', () => ({ useProjectScope: () => ({ projectId: 'project-a', projects: [], selectedProject: null }) }));
vi.mock('../features/tool-routing/api/tool-routing-queries', () => ({
  useToolRoutingOverviewQuery: () => ({ data: { policies: [state.policy] }, isLoading: false }),
  useReviewToolPolicyMutation: () => ({ mutateAsync: state.save, isPending: false }),
  useRebuildToolCatalogMutation: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useSelectToolRouteMutation: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));
vi.mock('../components/ops-layout', async (importOriginal) => ({
  ...await importOriginal<typeof import('../components/ops-layout')>(),
  OpsPageShell: ({ children }: any) => <>{children}</>, OpsAdvancedPreview: () => null,
}));

const rangeRect = Object.getOwnPropertyDescriptor(Range.prototype, 'getBoundingClientRect');
const rangeRects = Object.getOwnPropertyDescriptor(Range.prototype, 'getClientRects');
const limits = { requiredKeys: ['service'], forbiddenKeys: ['secret'], futureConstraint: { enforce: true }, resourceBinding: {
  targetEnvironment: 'test', resourceScope: 'service://resource/test', immutableResourceId: 'resource-1',
} };
beforeEach(() => {
  vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} });
  Object.defineProperty(Range.prototype, 'getBoundingClientRect', { configurable: true, value: () => new DOMRect() });
  Object.defineProperty(Range.prototype, 'getClientRects', { configurable: true, value: () => [] });
  state.save.mockReset().mockResolvedValue({});
  state.policy = { policyId: 'policy-a', toolName: 'test_read_state', status: 'PENDING_REVIEW', argumentPolicy: limits };
});
afterEach(() => {
  cleanup(); vi.unstubAllGlobals();
  if (rangeRect) Object.defineProperty(Range.prototype, 'getBoundingClientRect', rangeRect);
  else Reflect.deleteProperty(Range.prototype, 'getBoundingClientRect');
  if (rangeRects) Object.defineProperty(Range.prototype, 'getClientRects', rangeRects);
  else Reflect.deleteProperty(Range.prototype, 'getClientRects');
});
const open = async () => {
  render(<MemoryRouter><ToolRoutingObservabilityPage /></MemoryRouter>);
  fireEvent.click(await screen.findByRole('button', { name: '审核并发布' }));
};

it('normal review submits all original argument restrictions instead of a maxLimit replacement', async () => {
  await open();
  fireEvent.click(await screen.findByText('确认发布', { exact: true }));
  await waitFor(() => expect(state.save).toHaveBeenCalledTimes(1));
  expect(state.save.mock.calls[0][0]).toMatchObject({ policyId: 'policy-a', action: 'approve', payload: { argumentPolicy: limits } });
  expect(state.save.mock.calls[0][0].payload.argumentPolicy).not.toHaveProperty('maxLimit');
});

it('resource fields prefill current native binding and merge only explicit changes', async () => {
  await open();
  expect(await screen.findByRole('textbox', { name: '目标环境' })).toHaveValue('test');
  expect(screen.getByRole('textbox', { name: '资源范围' })).toHaveValue('service://resource/test');
  fireEvent.change(screen.getByRole('textbox', { name: '资源范围' }), { target: { value: 'service://reviewed/test' } });
  fireEvent.click(screen.getByText('确认发布', { exact: true }));
  await waitFor(() => expect(state.save).toHaveBeenCalledTimes(1));
  expect(state.save.mock.calls[0][0].payload.argumentPolicy).toEqual({ ...limits, resourceBinding: {
    ...limits.resourceBinding, resourceScope: 'service://reviewed/test',
  } });
});

it('a partial resource binding cannot submit a broader policy', async () => {
  await open();
  fireEvent.change(await screen.findByRole('textbox', { name: '资源范围' }), { target: { value: '' } });
  fireEvent.click(screen.getByText('确认发布', { exact: true }));
  await screen.findByText('请同时填写目标环境和资源范围。');
  expect(state.save).not.toHaveBeenCalled();
});

it('clearing both fields cannot submit a broader policy', async () => {
  await open();
  fireEvent.change(await screen.findByRole('textbox', { name: '资源范围' }), { target: { value: '' } });
  fireEvent.change(screen.getByRole('textbox', { name: '目标环境' }), { target: { value: '' } });
  fireEvent.click(screen.getByText('确认发布', { exact: true }));
  await screen.findByText('不能在审核时清空已有资源范围，请保留或明确填写新的范围。');
  expect(state.save).not.toHaveBeenCalled();
});
