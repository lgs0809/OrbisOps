import React from 'react';
import { Fragment, jsx, jsxs } from 'react/jsx-runtime';
import { unified } from 'unified';
import remarkParse from 'remark-parse';
import remarkGfm from 'remark-gfm';
import remarkRehype from 'remark-rehype';
import { toJsxRuntime } from 'hast-util-to-jsx-runtime';
import styled from 'styled-components';

const Document = styled.div`
  min-width: 0;
  white-space: normal;
  overflow-wrap: anywhere;
  font-size: 14px;
  line-height: 1.75;

  table { display: block; max-width: 100%; overflow-x: auto; border-collapse: collapse; }
  th, td { padding: 7px 12px; border: 1px solid #dfe3e9; text-align: left; }
  th { background: #f5f7fa; }
  pre { max-width: 100%; overflow-x: auto; }
`;

// Reports contain tool/model text. Use Markdown, never MDX expressions or raw HTML.
// Images remain explicit text so reading a report does not fetch an external URL.
const components = {
  a: ({ href, children }: React.AnchorHTMLAttributes<HTMLAnchorElement>) => {
    const safe = href && (/^https?:\/\//i.test(href) || /^\/(?!\/)/.test(href) || href.startsWith('#'));
    return safe ? <a href={href} target="_blank" rel="noopener noreferrer">{children}</a> : <span>{children}</span>;
  },
  img: ({ alt }: React.ImgHTMLAttributes<HTMLImageElement>) => <span>{alt || '图片引用'}</span>,
};

// Build a syntax tree and React elements. Unlike an MDX evaluator this works with
// the deployed script-src 'self' policy without enabling dynamic code execution.
const markdown = unified().use(remarkParse).use(remarkGfm).use(remarkRehype);

export function renderReadOnlyMarkdown(text: string) {
  return toJsxRuntime(markdown.runSync(markdown.parse(text)), { Fragment, jsx, jsxs, components });
}

export function ReadOnlyMarkdown({ text }: { text: string }) {
  const content = React.useMemo(() => renderReadOnlyMarkdown(text), [text]);
  return <Document>{content}</Document>;
}
