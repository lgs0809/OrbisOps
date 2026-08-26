import { Typography } from '@douyinfe/semi-ui';
import { ReadOnlyMarkdown } from '../../components/ReadOnlyMarkdown';

const { Text } = Typography;
const sections = [
  ['whenToUse', '适用条件'], ['when', '适用条件'], ['whenNotToUse', '不适用条件'],
  ['steps', '诊断步骤'], ['stopConditions', '完成与停止条件'],
  ['required', '必须具备的证据'], ['mustHaveEvidence', '必须具备的证据'],
  ['interpretationRules', '证据解释要求'], ['preferredSources', '优先证据'],
  ['rules', '规则'], ['mustNot', '禁止事项'],
] as const;

/** Read both current method fields and older proposals without dropping their stopping or safety rules. */
export function SkillMethodChange({ value, section }: { value: unknown; section?: string }) {
  const fields = value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown> : {};
  const lists = sections.map(([key, title]) => ({ key, title: key === 'rules' && section === 'negativeRules' ? '禁止事项' : title, items: Array.isArray(fields[key])
    ? (fields[key] as unknown[]).filter((item): item is string => typeof item === 'string') : [] }))
    .filter(section => section.items.length);
  const procedure = typeof fields.procedure === 'string' ? fields.procedure : '';
  return <div style={{ minWidth: 0, width: '100%', overflowWrap: 'anywhere' }}>
    {procedure && <ReadOnlyMarkdown text={procedure} />}
    {lists.map(section => <section key={section.key} aria-label={section.title}>
      <h4 style={{ margin: '12px 0 6px', fontSize: 13 }}>{section.title}</h4>
      <ol style={{ margin: 0, paddingLeft: 22 }}>{section.items.map((item, index) => <li key={index} style={{ margin: '5px 0', lineHeight: 1.7 }}>{item}</li>)}</ol>
    </section>)}
    {!procedure && !lists.length && <Text type="tertiary">此项没有可展示的方法正文，可展开审计记录核对原始内容。</Text>}
  </div>;
}
