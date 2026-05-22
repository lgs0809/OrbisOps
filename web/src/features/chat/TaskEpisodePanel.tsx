import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button, SideSheet, Space, Tag, Typography } from '@douyinfe/semi-ui';
import { useNavigate } from 'react-router-dom';
import { getTaskEpisodes } from '../../services/task-episode-service';
import { ReadOnlyMarkdown } from '../../components/ReadOnlyMarkdown';
import { TaskAcceptancePanel } from './TaskAcceptancePanel';

const statuses: Record<string, string> = {
  WAITING_TURN: '等待本轮执行结束', WAITING_MODEL: '分段服务不可用 · 待归属', RUNNING: '处理中',
  PENDING: '等待整理', PENDING_RETRY: '等待重试', RETRY_EXHAUSTED: '重试已耗尽 · 保留待处理',
  SOURCE_BLOCKED: '上下文待处理', ASSIGNED: '已归属', SUCCEEDED: '整理已保存',
};

export const TaskEpisodePanel: React.FC<{ projectId: string; sessionId: string; visible: boolean; onClose: () => void }> =
  ({ projectId, sessionId, visible, onClose }) => {
    const navigate = useNavigate();
    const [acceptanceEpisode,setAcceptanceEpisode] = useState('');
    const query = useQuery({ queryKey: ['task-episodes', projectId, sessionId],
      enabled: visible && Boolean(projectId && sessionId), refetchInterval: visible ? 5000 : false,
      queryFn: () => getTaskEpisodes(projectId, sessionId) });
    const data = query.data;
    return <SideSheet title="会话任务分段" visible={visible} onCancel={onClose} width="min(600px, 100vw)">
      <Typography.Paragraph type="tertiary">同一任务的后续对话归入同一段；新目标另开一段。分段和整理在后台进行，对话与运行可继续。24 小时没有后续消息时，会分析当前任务的暂定结尾；后来继续对话会更新分析，不会因此自动认定任务成功。</Typography.Paragraph>
      {query.isLoading && <Typography.Text>正在读取任务分段…</Typography.Text>}
      {query.isError && <Typography.Text type="danger">任务分段暂时无法读取，请稍后重试。</Typography.Text>}
      {data && <>
        {data.episodes.length === 0 && <Typography.Paragraph>尚无已归属的任务段。</Typography.Paragraph>}
        {data.episodes.map(episode => <section key={episode.episodeId} style={{ marginBottom: 20 }}>
          <Space wrap><Typography.Text strong>{episode.goal}</Typography.Text><Tag>版本 {episode.revision}</Tag><Tag>{episode.outcome==='SUCCEEDED'?'验收通过':episode.outcome==='FAILED'?'验收未通过':'结果待验证'}</Tag>
            <Button onClick={()=>setAcceptanceEpisode(episode.episodeId)}>任务验收</Button></Space>
          {data.artifacts.filter(a => a.episodeId === episode.episodeId).map(artifact => <details key={artifact.revision}>
            <summary>进度摘要 · 版本 {artifact.revision}{artifact.revision < episode.revision ? '（历史版本）' : ''}</summary>
            <ReadOnlyMarkdown text={artifact.content} />
          </details>)}
        </section>)}
        <Typography.Title heading={5}>整轮归属记录</Typography.Title>
        {data.turns.length === 0 && <Typography.Paragraph>本轮消息持久化后会进入后台归属队列。</Typography.Paragraph>}
        {data.turns.map((turn, index) => <section key={turn.turnSeq} style={{ padding: '12px 0', borderBottom: '1px solid var(--semi-color-border)', overflowWrap: 'anywhere' }}>
          <Space wrap><Typography.Text strong>第 {index + 1} 轮</Typography.Text><Tag>{turn.blockedByPrevious ? '等待前序轮次归属' : statuses[turn.status] || turn.status}</Tag></Space>
          {turn.episodeId && <Typography.Paragraph>{data.episodes.find(e => e.episodeId === turn.episodeId)?.goal || turn.episodeId} · 版本 {turn.episodeRevision}</Typography.Paragraph>}
          <div><Button theme="borderless" onClick={() => navigate(`/workbench?projectId=${encodeURIComponent(projectId)}&runId=${encodeURIComponent(turn.sourceRunRef)}`)}>查看本轮运行</Button></div>
          <details><summary>追溯信息</summary>
            <Typography.Paragraph size="small">{turn.sourceRunRef}<br />消息序号 {turn.turnSeq}–{turn.endSeq || '待结束'}<br />
              {turn.contextFidelity === 'FROZEN_MAIN_CONTEXT' ? '原始上下文快照' : turn.contextFidelity === 'RECONSTRUCTED_RETAINED_CONTEXT' ? '历史上下文重建（非完整回放）' : '尚未冻结上下文'}<br />
              已尝试 {turn.attempts} 次{turn.lastError ? ` · ${turn.lastError}` : ''}<br />{turn.inputHash}</Typography.Paragraph>
          </details>
        </section>)}
        {data.jobs.length > 0 && <><Typography.Title heading={5}>进度整理记录</Typography.Title>
          {data.jobs.map(job => <Typography.Paragraph key={job.id}>版本 {job.revision} · {statuses[job.status] || job.status}</Typography.Paragraph>)}</>}
      </>}
      {acceptanceEpisode && <TaskAcceptancePanel key={acceptanceEpisode} projectId={projectId} episodeId={acceptanceEpisode} onClose={()=>setAcceptanceEpisode('')} />}
    </SideSheet>;
  };
