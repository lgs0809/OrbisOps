import React from 'react';
import styled from 'styled-components';
import { ReadOnlyMarkdown } from '../../components/ReadOnlyMarkdown';
import { structuredAnswerData, toolAnswerRows, workflowToolAnswer } from './workflow-tool-answer';

const Receipt = styled.section`
  min-width: 0;
  overflow-wrap: anywhere;
  h3 { font-size: 15px; margin: 0 0 8px; }
  p { margin: 8px 0; color: #526074; font-size: 13px; line-height: 1.65; }
  dl { display: grid; grid-template-columns: minmax(65px, 1fr) minmax(0, 3fr); margin: 14px 0; }
  dt, dd { padding: 8px; border-bottom: 1px solid #e5e9ef; margin: 0; white-space: pre-wrap; }
  dt { color: #526074; }
  dd { color: #172b4d; }
  details { margin-top: 12px; max-width: 100%; }
  summary { cursor: pointer; font-size: 13px; color: #526074; }
  pre { max-width: 100%; max-height: 320px; overflow: auto; white-space: pre-wrap; font-size: 12px; }
`;

export function AssistantAnswer({ text }: { text: string }) {
  const answer = React.useMemo(() => workflowToolAnswer(text), [text]);
  const structured = React.useMemo(() => answer ? undefined : structuredAnswerData(text), [answer, text]);
  if (!answer && !structured) return <ReadOnlyMarkdown text={text} />;
  const { rows, omitted } = toolAnswerRows(answer ? answer.data : structured!.data);
  return <Receipt aria-label={answer ? '工具返回数据' : '结构化返回结果'}>
    <h3>{answer ? (answer.isError ? '工具返回错误' : '已收到工具回执') : '结构化返回结果'}{answer?.toolName ? ` · ${answer.toolName}` : ''}</h3>
    <p>{answer ? (answer.isError ? '请根据错误回执检查本次执行。' : '以下为工具实际返回的数据。是否达到业务目标，仍需结合任务的验收标准判断。') : '以下展示返回内容，结果含义以任务说明和运行记录为准。'}</p>
    <dl>{rows.map((row, index) => <React.Fragment key={index}><dt>{row.field}</dt><dd>{row.value}</dd></React.Fragment>)}</dl>
    {(omitted || answer?.truncated) && <p>当前展示为摘要，更多内容请查看原始回执或运行详情。</p>}
    <details><summary>{answer ? '查看原始回执' : '查看完整返回内容'}</summary>{answer?.evidenceRef && <p>回执引用：{answer.evidenceRef}</p>}<pre>{text}</pre></details>
  </Receipt>;
}
