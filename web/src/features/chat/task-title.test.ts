import { expect, it } from 'vitest';
import { taskTitle } from './task-title';

it('keeps normal language and bounds internal object or partial JSON titles without inferring success', () => {
  expect(taskTitle('请检查订单延迟')).toBe('请检查订单延迟');
  expect(taskTitle('{"serviceId":"checkout","status":"SUCCEEDED","priorEvidence":{"large":"record"}}')).toBe('结构化任务 · checkout');
  expect(taskTitle('{"result":"HEALTHY","metrics":{')).toBe('结构化输入任务');
  expect(taskTitle('[{"allowed":false}]')).toBe('结构化输入任务');
  expect(taskTitle('x'.repeat(200))).toHaveLength(120);
});

it('treats embedded descriptions literally and leaves encoded or non-object text neutral', () => {
  expect(taskTitle('{"alertContent":"<script>window.evil=1</script>"}')).toBe('<script>window.evil=1</script>');
  expect(taskTitle('{"query":"{\\\"status\\\":\\\"HEALTHY\\\"}"}')).toBe('结构化输入任务');
  expect(taskTitle('{"serviceId":"<img onerror=x>"}')).toBe('结构化输入任务');
  expect(taskTitle('', '运行详情')).toBe('运行详情');
});
