import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { OpsDashboardPage } from './ops-dashboard';

const query = vi.hoisted(() => ({ isLoading: false, isError: false, error: undefined as unknown,
  data: undefined, refetch: vi.fn() }));
vi.mock('../features/dashboard/api/dashboard-queries', () => ({ useDashboardOverviewQuery: () => query }));
vi.mock('../services/auth-session', () => ({ getStoredUserInfo: () => ({username:'操作员'}), isAdminUser: () => false }));
vi.mock('../components/ops-layout', () => ({ OpsPageShell: ({children}: {children: React.ReactNode}) => <main>{children}</main> }));
afterEach(() => cleanup());
describe('dashboard loading and failure presentation', () => {
  it('waits for real data instead of showing a zero backlog while loading', () => {
    Object.assign(query, {isLoading:true,isError:false,error:undefined});
    render(<MemoryRouter><OpsDashboardPage /></MemoryRouter>);
    expect(screen.getByRole('status')).toHaveTextContent('正在读取首页数据');
    expect(screen.queryByText('当前没有需要处理的事项')).not.toBeInTheDocument();
    expect(screen.queryByText('失败 / 阻塞')).not.toBeInTheDocument();
  });
  it('offers retry without presenting an unavailable response as healthy or empty', () => {
    Object.assign(query, {isLoading:false,isError:true,error:new Error('Network request failed')});
    render(<MemoryRouter><OpsDashboardPage /></MemoryRouter>);
    expect(screen.getByRole('button',{name:'refresh 重试'})).toBeInTheDocument();
    expect(screen.queryByText('当前没有阻塞事项')).not.toBeInTheDocument();
    expect(screen.queryByText('暂无最近对话')).not.toBeInTheDocument();
  });
});
