import React, { useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  TextArea,
  Toast,
  Typography,
} from '@douyinfe/semi-ui';
import { IconCopy, IconEdit, IconPlus, IconRefresh } from '@douyinfe/semi-icons';
import styled from 'styled-components';

import { OpsPageHeader, OpsPageShell } from '../components/ops-layout';
import { SkillPackageEditor } from '../components/skill/SkillPackageEditor';
import {
  useCopyGlobalSkillMutation,
  useCreateGlobalSkillMutation,
  useReloadSkillsMutation,
  useRollbackGlobalSkillMutation,
  useSaveGlobalSkillMutation,
  useSkillCatalogQuery,
  useSkillContextQuery,
  useSkillCopyProjectsQuery,
  useSkillDetailQuery,
  useSkillUsageQuery,
  useSkillVersionsQuery,
  useUpdateGlobalSkillModeMutation,
  useUpdateGlobalSkillStatusMutation,
} from '../features/skills/api/skill-queries';
import type { OpsSkillArtifact, OpsSkillDetail, OpsSkillReference, OpsSkillSummary } from '../services/ops-admin-service';
import { theme } from '../styles/theme';
import { generatedId } from '../utils/generated-id';
import { userFacingError } from '../utils/user-facing-error';

const { Paragraph, Text, Title } = Typography;
const { Option } = Select;

const Stack = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.base};
  min-width: 0;
`;

const Grid = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 0.8fr);
  gap: ${theme.spacing.base};
  align-items: start;
  @media (max-width: 1080px) { grid-template-columns: 1fr; }
`;

const MetaGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: ${theme.spacing.sm};
  @media (max-width: 700px) { grid-template-columns: 1fr; }
`;

const MetaBox = styled.div`
  padding: ${theme.spacing.base};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.secondary};
  min-width: 0;
`;

const Code = styled.pre`
  margin: 0;
  padding: ${theme.spacing.base};
  border-radius: ${theme.borderRadius.base};
  background: #0f172a;
  color: #e2e8f0;
  max-height: 420px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.55;
`;

type SkillMeta = OpsSkillSummary & { skillId?: string; scope?: string; projectId?: string };

const skillIdOf = (skill?: OpsSkillSummary | OpsSkillDetail | null) => {
  const meta = skill as (SkillMeta & OpsSkillDetail) | null | undefined;
  return String(meta?.skillId || skill?.name || '').trim();
};

const managed = (skill?: OpsSkillSummary | OpsSkillDetail | null) => skill?.catalogManaged !== false;
const updateMode = (skill?: OpsSkillSummary | OpsSkillDetail | null) => String(skill?.updateMode || skill?.update_mode || 'AUTO').toUpperCase();
const origin = (skill?: OpsSkillSummary | OpsSkillDetail | null) => String(skill?.origin || skill?.sourceType || (managed(skill) ? 'MANUAL' : 'BUILT_IN')).toUpperCase();
const scopeOf = (skill: OpsSkillSummary) => String((skill as SkillMeta).scope || skill.frontMatter?.scope || '').toUpperCase();
const projectIdOf = (skill: OpsSkillSummary) => String((skill as SkillMeta).projectId || skill.frontMatter?.projectId || '');
const isProjectSkill = (skill: OpsSkillSummary) => scopeOf(skill) === 'PROJECT' || Boolean(projectIdOf(skill));

const statusLabel = (skill?: OpsSkillSummary | OpsSkillDetail | null) => {
  if (!managed(skill)) return '平台内置 · 只读';
  const value = String(skill?.status || 'ENABLED').toUpperCase();
  if (['ENABLED', 'ACTIVE'].includes(value)) return '已启用';
  if (value === 'FROZEN') return '已冻结';
  if (value === 'PAUSED') return '已暂停';
  if (value === 'DEPRECATED') return '已废弃';
  return '已停用';
};

const modeLabel = (skill?: OpsSkillSummary | OpsSkillDetail | null) => {
  const value = updateMode(skill);
  if (value === 'FROZEN') return '已冻结';
  if (value === 'MANUAL_ONLY') return '仅人工维护';
  return '允许自动更新';
};

const originLabel = (skill?: OpsSkillSummary | OpsSkillDetail | null) => {
  const value = origin(skill);
  if (value === 'BUILT_IN') return '平台内置';
  if (value === 'EVOLVED') return '自动沉淀';
  if (value === 'IMPORTED') return '导入';
  return '人工创建';
};

const mergeCatalog = (catalog: OpsSkillSummary[], builtIn: OpsSkillSummary[]) => {
  const current = catalog.map((skill) => ({ ...skill, catalogManaged: true, builtIn: false }));
  const names = new Set(current.map((skill) => String(skill.name || '').trim().toLowerCase()));
  return [
    ...current,
    ...builtIn
      .filter((skill) => !names.has(String(skill.name || '').trim().toLowerCase()))
      .map((skill) => ({ ...skill, catalogManaged: false, builtIn: true, origin: 'BUILT_IN', updateMode: 'MANUAL_ONLY' })),
  ];
};

const parseList = (value: string) => value.split(/[\n,，|]+/).map((item) => item.trim()).filter(Boolean);

export const SkillsPage: React.FC = () => {
  const catalogQuery = useSkillCatalogQuery(true);
  const reloadMutation = useReloadSkillsMutation();
  const saveMutation = useSaveGlobalSkillMutation();
  const createMutation = useCreateGlobalSkillMutation();
  const statusMutation = useUpdateGlobalSkillStatusMutation();
  const modeMutation = useUpdateGlobalSkillModeMutation();
  const rollbackMutation = useRollbackGlobalSkillMutation();
  const copyMutation = useCopyGlobalSkillMutation();

  const allSkills = useMemo(
    () => mergeCatalog(catalogQuery.data?.catalogSkills || [], catalogQuery.data?.builtInSkills || []),
    [catalogQuery.data],
  );
  const backendHasScope = useMemo(() => allSkills.some((skill) => Boolean(scopeOf(skill) || projectIdOf(skill))), [allSkills]);
  const globalSkills = useMemo(() => backendHasScope ? allSkills.filter((skill) => !isProjectSkill(skill)) : allSkills, [allSkills, backendHasScope]);

  const [keyword, setKeyword] = useState('');
  const [selectedId, setSelectedId] = useState('');
  const filtered = useMemo(() => {
    const query = keyword.trim().toLowerCase();
    if (!query) return globalSkills;
    return globalSkills.filter((skill) => [skillIdOf(skill), skill.name, skill.description, skill.basePath]
      .some((value) => String(value || '').toLowerCase().includes(query)));
  }, [globalSkills, keyword]);

  useEffect(() => {
    if (!selectedId && globalSkills[0]) setSelectedId(skillIdOf(globalSkills[0]));
    if (selectedId && !globalSkills.some((skill) => skillIdOf(skill) === selectedId)) setSelectedId(skillIdOf(globalSkills[0]) || '');
  }, [globalSkills, selectedId]);

  const summary = globalSkills.find((skill) => skillIdOf(skill) === selectedId) || null;
  const detailQuery = useSkillDetailQuery(summary ? {
    skillId: skillIdOf(summary),
    sourceName: summary.name || skillIdOf(summary),
    catalogManaged: managed(summary),
    summary,
  } : undefined);
  const detail = detailQuery.data || null;
  const contextQuery = useSkillContextQuery(detail?.name || '', Boolean(detail));

  const [editMode, setEditMode] = useState(false);
  const [draftContent, setDraftContent] = useState('');
  const [draftArtifacts, setDraftArtifacts] = useState<OpsSkillArtifact[]>([]);
  useEffect(() => {
    if (!detail) return;
    setDraftContent(detail.content || detail.markdown || '');
    setDraftArtifacts((detail.artifacts || []).filter((item) => item.path !== 'SKILL.md'));
    setEditMode(false);
  }, [detail]);

  const [createVisible, setCreateVisible] = useState(false);
  const [createForm, setCreateForm] = useState({
    skillId: '', name: '', description: '', category: 'GENERAL', whenToUse: '', whenNotToUse: '', keywords: '', content: '', artifacts: [] as OpsSkillArtifact[],
  });
  const resetCreate = () => setCreateForm({ skillId: '', name: '', description: '', category: 'GENERAL', whenToUse: '', whenNotToUse: '', keywords: '', content: '', artifacts: [] });

  const [usageVisible, setUsageVisible] = useState(false);
  const [versionsVisible, setVersionsVisible] = useState(false);
  const usageQuery = useSkillUsageQuery(skillIdOf(detail), usageVisible && Boolean(detail && managed(detail)));
  const versionsQuery = useSkillVersionsQuery(skillIdOf(detail), versionsVisible && Boolean(detail && managed(detail)));

  const [copyVisible, setCopyVisible] = useState(false);
  const projectsQuery = useSkillCopyProjectsQuery(copyVisible);
  const [copyForm, setCopyForm] = useState({ projectId: '', name: '', description: '' });
  useEffect(() => {
    if (copyVisible && !copyForm.projectId && projectsQuery.data?.[0]?.projectId) {
      setCopyForm((current) => ({ ...current, projectId: projectsQuery.data?.[0]?.projectId || '' }));
    }
  }, [copyVisible, copyForm.projectId, projectsQuery.data]);

  const reload = async () => {
    try { await reloadMutation.mutateAsync(); Toast.success('Skill 已刷新。'); }
    catch (error) { Toast.error(userFacingError(error, '刷新 Skill 失败，请稍后重试。')); }
  };

  const save = async () => {
    if (!detail || !managed(detail)) return;
    try {
      await saveMutation.mutateAsync({
        skillId: skillIdOf(detail),
        payload: {
          content: draftContent,
          name: detail.name,
          description: detail.description,
          status: detail.status || 'ENABLED',
          artifacts: draftArtifacts,
          evalSuites: draftArtifacts.filter((item) => item.role === 'EVAL').map((item) => item.path),
        },
      });
      setEditMode(false);
      Toast.success('Skill 已保存。');
    } catch (error) {
      Toast.error(userFacingError(error, '保存 Skill 失败，请稍后重试。'));
    }
  };

  const create = async () => {
    if (!createForm.name.trim()) { Toast.warning('请输入 Skill 名称。'); return; }
    if (!createForm.whenToUse.trim()) { Toast.warning('请输入适用场景。'); return; }
    if (!createForm.whenNotToUse.trim()) { Toast.warning('请输入不适用场景。'); return; }
    try {
      const result = await createMutation.mutateAsync({
        skillId: createForm.skillId.trim() || generatedId('skill', createForm.name),
        name: createForm.name.trim(),
        description: createForm.description.trim(),
        category: createForm.category,
        whenToUse: parseList(createForm.whenToUse),
        whenNotToUse: parseList(createForm.whenNotToUse),
        keywords: parseList(createForm.keywords),
        content: createForm.content,
        status: 'ENABLED',
        artifacts: createForm.artifacts,
        evalSuites: createForm.artifacts.filter((item) => item.role === 'EVAL').map((item) => item.path),
      });
      setCreateVisible(false);
      resetCreate();
      setSelectedId(skillIdOf(result));
      Toast.success('通用 Skill 已创建。');
    } catch (error) {
      Toast.error(userFacingError(error, '创建通用 Skill 失败，请稍后重试。'));
    }
  };

  const importBuiltIn = () => {
    if (!detail || managed(detail)) return;
    setCreateForm({
      skillId: generatedId('skill', detail.name),
      name: detail.name,
      description: detail.description || '',
      category: String(detail.frontMatter?.category || 'GENERAL'),
      whenToUse: Array.isArray(detail.frontMatter?.whenToUse) ? detail.frontMatter.whenToUse.join('\n') : '',
      whenNotToUse: Array.isArray(detail.frontMatter?.whenNotToUse) ? detail.frontMatter.whenNotToUse.join('\n') : '',
      keywords: Array.isArray(detail.frontMatter?.keywords) ? detail.frontMatter.keywords.join(', ') : '',
      content: detail.content || detail.markdown || '',
      artifacts: (detail.artifacts || []).filter((item) => item.path !== 'SKILL.md'),
    });
    setCreateVisible(true);
  };

  const toggleStatus = async () => {
    if (!detail || !managed(detail)) return;
    const next = String(detail.status || 'ENABLED').toUpperCase() === 'ENABLED' ? 'DISABLED' : 'ENABLED';
    try {
      await statusMutation.mutateAsync({ skillId: skillIdOf(detail), status: next });
      Toast.success(next === 'ENABLED' ? 'Skill 已启用。' : 'Skill 已停用。');
    } catch (error) {
      Toast.error(userFacingError(error, '更新 Skill 状态失败，请稍后重试。'));
    }
  };

  const setMode = async (value: 'AUTO' | 'MANUAL_ONLY' | 'FROZEN') => {
    if (!detail || !managed(detail)) return;
    try {
      await modeMutation.mutateAsync({
        skillId: skillIdOf(detail),
        payload: { updateMode: value, autoUpdateEnabled: value === 'AUTO', autoMergeEnabled: value === 'AUTO' },
      });
      Toast.success(value === 'AUTO' ? '已允许自动更新。' : value === 'FROZEN' ? 'Skill 已冻结。' : '已切换为仅人工维护。');
    } catch (error) {
      Toast.error(userFacingError(error, '更新 Skill 策略失败，请稍后重试。'));
    }
  };

  const rollback = async (version: number) => {
    if (!detail || !managed(detail)) return;
    try {
      await rollbackMutation.mutateAsync({ skillId: skillIdOf(detail), version });
      setVersionsVisible(false);
      Toast.success(`Skill 已回滚到 v${version}。`);
    } catch (error) {
      Toast.error(userFacingError(error, '回滚 Skill 失败，请稍后重试。'));
    }
  };

  const openCopy = () => {
    if (!detail || !managed(detail)) return;
    setCopyForm({ projectId: '', name: detail.name || skillIdOf(detail), description: detail.description || '' });
    setCopyVisible(true);
  };

  const copyToProject = async () => {
    if (!detail || !copyForm.projectId || !copyForm.name.trim()) { Toast.warning('请选择 Project 并填写 Skill 名称。'); return; }
    try {
      await copyMutation.mutateAsync({
        projectId: copyForm.projectId,
        payload: { globalSkillId: skillIdOf(detail), skillId: generatedId('skill', copyForm.name), name: copyForm.name.trim(), description: copyForm.description.trim() },
      });
      setCopyVisible(false);
      Toast.success('已生成 Project 专属 Skill。');
    } catch (error) {
      Toast.error(userFacingError(error, '复制 Skill 到 Project 失败，请稍后重试。'));
    }
  };

  const columns = [
    {
      title: 'Skill', width: 220,
      render: (_: unknown, skill: OpsSkillSummary) => (
        <div><Text strong>{skill.name || skillIdOf(skill)}</Text><Text type="tertiary" size="small" style={{ display: 'block' }}>{skillIdOf(skill)}</Text></div>
      ),
    },
    { title: '说明', dataIndex: 'description', render: (value: string) => value || '-' },
    { title: '来源', width: 110, render: (_: unknown, skill: OpsSkillSummary) => originLabel(skill) },
    { title: '更新策略', width: 130, render: (_: unknown, skill: OpsSkillSummary) => modeLabel(skill) },
    { title: '状态', width: 130, render: (_: unknown, skill: OpsSkillSummary) => <Tag color={managed(skill) && String(skill.status || 'ENABLED').toUpperCase() === 'DISABLED' ? 'grey' : 'green'}>{statusLabel(skill)}</Tag> },
    { title: '操作', width: 100, render: (_: unknown, skill: OpsSkillSummary) => <Button size="small" onClick={() => setSelectedId(skillIdOf(skill))}>查看</Button> },
  ];

  return (
    <OpsPageShell selectedKey="settings">
      <OpsPageHeader
        title="Skill"
        description="维护可复用的诊断经验、SOP 和报告方法。平台 Skill 接入后不会自动用于所有项目；具体项目只会使用自己已经启用的 Skill。"
        extra={<Space><Button icon={<IconRefresh />} loading={reloadMutation.isPending} onClick={() => void reload()}>刷新</Button><Button type="primary" icon={<IconPlus />} onClick={() => { resetCreate(); setCreateVisible(true); }}>新建 Skill</Button></Space>}
      />

      <Stack>
        <Card>
          <Paragraph type="tertiary" style={{ margin: 0 }}>
            Skill 是助手和工作流执行任务时按需加载的方法包，不需要把它作为独立流程节点拖进 Workflow。自动沉淀和人工维护都会进入版本体系，是否跟随新版本由每个 Skill 的更新策略决定。
          </Paragraph>
        </Card>
        <Input placeholder="搜索 Skill 名称或说明" value={keyword} onChange={setKeyword} style={{ maxWidth: 420 }} />
        <Grid>
          <Card title={`平台 Skill (${globalSkills.length})`}>
            <Table rowKey={(record?: OpsSkillSummary) => skillIdOf(record)} columns={columns} dataSource={filtered} loading={catalogQuery.isLoading} pagination={false} empty={<Text type="tertiary">暂无 Skill。</Text>} />
          </Card>

          <Card title={detail?.name || 'Skill 详情'}>
            {!summary ? <Text type="tertiary">请选择一个 Skill。</Text> : detailQuery.isLoading ? <Text type="tertiary">正在加载 Skill 详情...</Text> : detailQuery.isError ? <Text type="danger">加载 Skill 详情失败。</Text> : detail ? (
              <Stack>
                <Space wrap>
                  <Tag color={managed(detail) ? 'green' : 'blue'}>{statusLabel(detail)}</Tag>
                  <Tag>{originLabel(detail)}</Tag>
                  <Tag>{modeLabel(detail)}</Tag>
                </Space>
                <Paragraph type="tertiary" style={{ margin: 0 }}>{detail.description || '暂无说明。'}</Paragraph>
                <MetaGrid>
                  <MetaBox><Text strong>Skill ID</Text><div><Text type="tertiary">{skillIdOf(detail)}</Text></div></MetaBox>
                  <MetaBox><Text strong>显式引用</Text><div><Text type="tertiary">{detail.referencedBy?.length || 0} 处</Text></div></MetaBox>
                </MetaGrid>
                <Space wrap>
                  {!managed(detail) ? <Button type="primary" onClick={importBuiltIn}>加入平台 Skill 目录</Button> : editMode ? (
                    <><Button onClick={() => { setDraftContent(detail.content || detail.markdown || ''); setDraftArtifacts((detail.artifacts || []).filter((item) => item.path !== 'SKILL.md')); setEditMode(false); }}>取消</Button><Button type="primary" loading={saveMutation.isPending} onClick={() => void save()}>保存修改</Button></>
                  ) : <Button icon={<IconEdit />} onClick={() => setEditMode(true)}>编辑内容</Button>}
                  {managed(detail) && <>
                    <Button onClick={() => void toggleStatus()}>{String(detail.status || 'ENABLED').toUpperCase() === 'ENABLED' ? '停用' : '启用'}</Button>
                    <Button onClick={() => void setMode('AUTO')}>允许自动更新</Button>
                    <Button onClick={() => void setMode('MANUAL_ONLY')}>仅人工维护</Button>
                    <Button type="danger" theme="borderless" onClick={() => void setMode('FROZEN')}>冻结</Button>
                    <Button onClick={() => setVersionsVisible(true)}>版本</Button>
                    <Button onClick={() => setUsageVisible(true)}>使用位置</Button>
                    <Button icon={<IconCopy />} onClick={openCopy}>复制到 Project</Button>
                  </>}
                </Space>
                <SkillPackageEditor
                  entrypointContent={editMode ? draftContent : (detail.content || detail.markdown || '')}
                  artifacts={editMode ? draftArtifacts : (detail.artifacts || []).filter((item) => item.path !== 'SKILL.md')}
                  readOnly={!editMode || !managed(detail)}
                  disabled={saveMutation.isPending}
                  onEntrypointChange={setDraftContent}
                  onArtifactsChange={setDraftArtifacts}
                />
                <div><Title heading={6}>Agent 实际读取的上下文</Title><Code>{contextQuery.isLoading ? '加载中...' : contextQuery.isError ? '上下文预览加载失败。' : contextQuery.data || '暂无内容。'}</Code></div>
              </Stack>
            ) : null}
          </Card>
        </Grid>
      </Stack>

      <Modal title="新建通用 Skill" visible={createVisible} width={900} confirmLoading={createMutation.isPending} okText="创建" cancelText="取消" onOk={() => void create()} onCancel={() => { setCreateVisible(false); resetCreate(); }}>
        <Stack>
          <Input value={createForm.name} placeholder="Skill 名称" onChange={(name) => setCreateForm((current) => ({ ...current, name }))} />
          <Input value={createForm.description} placeholder="说明" onChange={(description) => setCreateForm((current) => ({ ...current, description }))} />
          <Select value={createForm.category} onChange={(category) => setCreateForm((current) => ({ ...current, category: String(category) }))}>
            <Option value="GENERAL">通用</Option><Option value="OPERATIONS">运维与发布</Option><Option value="OBSERVABILITY">日志、指标与链路</Option><Option value="DEVELOPMENT">研发与代码</Option><Option value="DATA">数据与数据库</Option><Option value="KNOWLEDGE">知识库与检索</Option>
          </Select>
          <TextArea value={createForm.whenToUse} placeholder="适用场景（必填），每行一个" autosize={{ minRows: 2, maxRows: 5 }} onChange={(whenToUse) => setCreateForm((current) => ({ ...current, whenToUse }))} />
          <TextArea value={createForm.whenNotToUse} placeholder="不适用场景（必填），每行一个" autosize={{ minRows: 2, maxRows: 5 }} onChange={(whenNotToUse) => setCreateForm((current) => ({ ...current, whenNotToUse }))} />
          <Input value={createForm.keywords} placeholder="检索关键词，用逗号分隔" onChange={(keywords) => setCreateForm((current) => ({ ...current, keywords }))} />
          <SkillPackageEditor entrypointContent={createForm.content} artifacts={createForm.artifacts} disabled={createMutation.isPending} onEntrypointChange={(content) => setCreateForm((current) => ({ ...current, content }))} onArtifactsChange={(artifacts) => setCreateForm((current) => ({ ...current, artifacts }))} />
        </Stack>
      </Modal>

      <Modal title="复制到 Project" visible={copyVisible} width={620} confirmLoading={copyMutation.isPending} okText="生成 Project Skill" cancelText="取消" onOk={() => void copyToProject()} onCancel={() => setCopyVisible(false)}>
        <Stack>
          <Text type="tertiary">复制后会形成独立的 Project 专属 Skill，后续修改不会影响平台 Skill。</Text>
          <Select value={copyForm.projectId || undefined} placeholder="选择目标 Project" loading={projectsQuery.isLoading} onChange={(projectId) => setCopyForm((current) => ({ ...current, projectId: String(projectId || '') }))}>
            {(projectsQuery.data || []).map((project) => <Option key={project.projectId} value={project.projectId}>{project.name}（{project.projectId}）</Option>)}
          </Select>
          <Input value={copyForm.name} placeholder="Project Skill 名称" onChange={(name) => setCopyForm((current) => ({ ...current, name }))} />
          <TextArea value={copyForm.description} placeholder="Project 内适用范围说明" autosize={{ minRows: 3, maxRows: 6 }} onChange={(description) => setCopyForm((current) => ({ ...current, description }))} />
        </Stack>
      </Modal>

      <Modal title="Skill 使用位置" visible={usageVisible} width={760} footer={null} onCancel={() => setUsageVisible(false)}>
        <Table
          rowKey={(record?: OpsSkillReference) => record ? `${record.projectId || ''}-${record.agentId || ''}-${record.targetId || record.type}` : ''}
          loading={usageQuery.isLoading}
          dataSource={usageQuery.data || []}
          pagination={false}
          empty={<Text type="tertiary">当前没有 Agent 或节点显式引用该 Skill。</Text>}
          columns={[
            { title: 'Project', dataIndex: 'projectId', width: 160, render: (value: string) => value || '-' },
            { title: 'Agent', dataIndex: 'agentName', render: (value: string, record: OpsSkillReference) => value || record.agentId || '-' },
            { title: '绑定位置', width: 180, render: (_: unknown, record: OpsSkillReference) => record.type === 'node' ? `节点：${record.targetId || '-'}` : 'Agent 默认能力' },
          ]}
        />
      </Modal>

      <Modal title="Skill 版本" visible={versionsVisible} width={860} footer={null} onCancel={() => setVersionsVisible(false)}>
        <Table
          rowKey={(record?: Record<string, any>) => `${record?.skillId || record?.skill_id || ''}-${record?.version || ''}`}
          loading={versionsQuery.isLoading}
          dataSource={versionsQuery.data || []}
          pagination={{ pageSize: 6 }}
          empty={<Text type="tertiary">暂无版本记录。</Text>}
          columns={[
            { title: '版本', dataIndex: 'version', width: 90 },
            { title: '来源', render: (_: unknown, record: Record<string, any>) => record.sourceType || record.source_type || '-' },
            { title: '变更说明', render: (_: unknown, record: Record<string, any>) => record.changeSummary || record.change_summary || '-' },
            { title: '创建时间', width: 180, render: (_: unknown, record: Record<string, any>) => record.createTime || record.create_time || '-' },
            { title: '操作', width: 100, render: (_: unknown, record: Record<string, any>) => <Button size="small" type="danger" theme="borderless" loading={rollbackMutation.isPending} onClick={() => void rollback(Number(record.version))}>回滚</Button> },
          ]}
        />
      </Modal>
    </OpsPageShell>
  );
};
