/** This is a saved task experience, not a published Skill or an authorization. */
export function SavedExperiencePanel({ experience }: { experience: Record<string, any> }) {
  const method = experience.method || {};
  return <section aria-label="已保存的任务经验" style={{ overflowWrap: 'anywhere' }}>
    <p>{experience.currentSource === false
      ? '这是历史经验；任务已有修正或验收已失效，不能作为当前有效来源。'
      : '本次任务经验已保存。是否形成 Skill，仍按独立成功来源和内容检查规则判断。'}</p>
    <p><strong>目标：</strong>{String(method.goal || '未记录')}</p>
    {([
      ['conditions', '适用条件与边界'], ['steps', '有效步骤'],
      ['acceptance', '验收方法'], ['toolCategories', '工具类别'],
    ] as const).map(([key, title]) => <div key={key}>
      <h4>{title}</h4>
      {Array.isArray(method[key]) && method[key].length > 0
        ? <ol>{method[key].map((item: unknown, i: number) => <li key={i}>{String(item)}</li>)}</ol>
        : <p>未记录</p>}
    </div>)}
  </section>;
}
