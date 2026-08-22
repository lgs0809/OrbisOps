import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { AppErrorBoundary } from './AppErrorBoundary';

const Broken: React.FC = () => {
  throw new Error('render failed');
};

describe('AppErrorBoundary', () => {
  it('keeps rendering normal children when the page is healthy', () => {
    render(<AppErrorBoundary><div>healthy page</div></AppErrorBoundary>);
    expect(screen.getByText('healthy page')).toBeInTheDocument();
  });

  it('shows a safe recovery message instead of replaying page actions', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    render(<AppErrorBoundary><Broken /></AppErrorBoundary>);

    expect(screen.getByRole('alert')).toHaveTextContent('当前页面无法显示');
    expect(screen.getByText(/审批或生产执行操作不会被自动重放/)).toBeInTheDocument();
    consoleSpy.mockRestore();
  });
});
