import { unified } from 'unified';
import remarkParse from 'remark-parse';
import { structuredAnswerData, workflowToolAnswer } from './workflow-tool-answer';
import { taskTitle } from './task-title';

interface PreviewNode { type: string; value?: string; alt?: string; children?: PreviewNode[] }
const markdown = unified().use(remarkParse);

/** Read Markdown as plain text; previews never execute HTML or fetch linked resources. */
export function chatPreview(content?: string) {
  const tool = workflowToolAnswer(content || '');
  if (tool) return `${tool.toolName ? `${tool.toolName} · ` : ''}${tool.isError ? '工具返回错误' : '已收到工具回执'}`;
  if (structuredAnswerData(content || '')) return '结构化结果（打开对话查看）';
  // Session summaries are server-capped and may end inside a receipt. This neutral
  // label makes no success/authentication claim; full rendering still requires its complete envelope.
  if (content?.trimStart().startsWith('{')) {
    try { JSON.parse(content); } catch {
      const prefix = content.slice(0, 1024);
      if (/"providerType"\s*:\s*"MCP"/.test(prefix)
        || /"mcpEnvelope"\s*:\s*\{\s*"content"\s*:/.test(prefix)
        // Evidence references may put their envelope beyond the server's summary cap.
        // Require the compound receipt identity; providerId alone is ordinary business data.
        || (/"providerId"\s*:\s*"[^"\\]+"/.test(prefix)
          && /"resultId"\s*:\s*"tool-result-[a-z0-9-]+"/.test(prefix)
          && /"providerOutputHash"\s*:\s*"[a-f0-9]{64}"/.test(prefix))) return '工具回执（打开对话查看）';
    }
  }
  // The summary can stop inside nested previews before its typed receipt fields.
  // Identify only the structured shape here; never infer a tool, permission or successful outcome.
  if (/^\s*(?:\{\s*"[^"\\]+"\s*:|\[\s*\{)/.test(content || '')) return '结构化结果（打开对话查看）';
  const collect = (node: PreviewNode): string => {
    if (node.type === 'html' || node.type === 'code') return '';
    if (node.type === 'image') return node.alt || '';
    if (node.value) return node.value;
    return (node.children || []).map(collect).join(node.type === 'root' ? ' ' : '');
  };
  return collect(markdown.parse(content || '') as PreviewNode).replace(/\s+/g, ' ').trim();
}

export function chatSessionTitle(title?: string, lastMessage?: string) {
  if (title?.trim() && title.trim() !== '新对话') return taskTitle(title);
  const preview = chatPreview(lastMessage);
  return preview ? preview.slice(0, 42) : title || '新对话';
}

export function shouldSendChatKey(event: { key: string; shiftKey: boolean; isComposing?: boolean; keyCode?: number }) {
  return event.key === 'Enter' && !event.shiftKey && !event.isComposing && event.keyCode !== 229;
}
