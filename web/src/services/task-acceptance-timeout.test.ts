import { afterEach, beforeEach, expect, it, vi } from 'vitest';

const originalFetch = window.fetch;
let native: ReturnType<typeof vi.fn>;
let resolveResponse: (response: Response) => void;
let requestSignal: AbortSignal;
beforeEach(async () => {
  vi.resetModules();
  vi.useFakeTimers();
  native = vi.fn((_url: RequestInfo | URL, init?: RequestInit) => new Promise<Response>((resolve, reject) => {
    resolveResponse = resolve;
    requestSignal = init!.signal!;
    requestSignal.addEventListener('abort', () => reject(requestSignal.reason), {once:true});
  }));
  window.fetch = native;
  const { installHttpInterceptor } = await import('./http-interceptor');
  installHttpInterceptor();
});
afterEach(() => { window.fetch = originalFetch; vi.useRealTimers(); });

it('waits for a real draft response beyond the normal management deadline without retrying the browser request', async () => {
  const { draftTaskAcceptance } = await import('./task-episode-service');
  const pending = draftTaskAcceptance('project', 'episode', {revision:1,instruction:'核对原定目标'});
  await vi.advanceTimersByTimeAsync(13000);
  expect(requestSignal.aborted).toBe(false);
  expect(native).toHaveBeenCalledTimes(1);
  expect(native.mock.calls[0][1]).not.toHaveProperty('requestTimeoutMs');
  resolveResponse(new Response(JSON.stringify({code:'0000',data:{status:'INSUFFICIENT_EVIDENCE',explanation:'缺少订单证据'}})));
  await expect(pending).resolves.toMatchObject({status:'INSUFFICIENT_EVIDENCE'});
});

it('keeps the draft wait bounded even if the backend never answers', async () => {
  const { draftTaskAcceptance } = await import('./task-episode-service');
  const pending = draftTaskAcceptance('project', 'episode', {revision:1,instruction:'核对原定目标'});
  const rejected = expect(pending).rejects.toThrow('请求超时');
  await vi.advanceTimersByTimeAsync(259999);
  expect(requestSignal.aborted).toBe(false);
  await vi.advanceTimersByTimeAsync(1);
  await rejected;
  expect(native).toHaveBeenCalledTimes(1);
});

it('preserves the short deadline for ordinary management reads', async () => {
  const { getTaskAcceptance } = await import('./task-episode-service');
  const pending = getTaskAcceptance('project', 'episode');
  const rejected = expect(pending).rejects.toThrow('请求超时');
  await vi.advanceTimersByTimeAsync(12000);
  await rejected;
});

it('still propagates caller cancellation during an extended wait', async () => {
  const { opsRequest } = await import('./ops-http-client');
  const controller = new AbortController();
  const pending = opsRequest('/api/example', {signal:controller.signal,requestTimeoutMs:260000});
  const rejected = expect(pending).rejects.toThrow('请求超时');
  controller.abort();
  await rejected;
  expect(requestSignal.aborted).toBe(true);
  expect(vi.getTimerCount()).toBe(0);
});
