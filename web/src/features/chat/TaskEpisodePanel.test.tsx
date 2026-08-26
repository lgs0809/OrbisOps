import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { expect, it, vi } from 'vitest';
import { TaskEpisodePanel } from './TaskEpisodePanel';
import { getTaskEpisodes } from '../../services/task-episode-service';
vi.mock('../../services/task-episode-service', () => ({ getTaskEpisodes: vi.fn() }));

it('shows persisted retry time and cumulative attempts without implying exhausted tasks succeeded', async () => {
  vi.mocked(getTaskEpisodes).mockResolvedValue({ model: 'gpt-5.6-luna', episodes: [], artifacts: [], jobs: [],
    turns: [{ turnSeq: 1, endSeq: 2, sourceRunRef: 'run', episodeId: '', episodeRevision: 0,
      status: 'PENDING_RETRY', attempts: 12, nextAttemptMs: Date.parse('2026-11-03T02:00:00Z'),
      contextFidelity: 'FROZEN_MAIN_CONTEXT', inputHash: 'hash', lastError: 'EPISODE_MODEL_FAILED', blockedByPrevious: false }] });
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <MemoryRouter><TaskEpisodePanel projectId="p" sessionId="s" visible readOnly onClose={() => {}} /></MemoryRouter>
  </QueryClientProvider>);
  expect(await screen.findByText('已暂存 · 自动重试')).toBeInTheDocument();
  expect(screen.getByText(/累计尝试 12 次（恢复不清零）/)).toBeInTheDocument();
  expect(screen.getByText(/下一次尝试不早于/).querySelector('time')).toHaveAttribute('datetime', '2026-11-03T02:00:00.000Z');
  expect(screen.queryByText(/重试已耗尽/)).not.toBeInTheDocument();
});
