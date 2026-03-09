import React, { useEffect, useMemo, useRef, useState } from 'react';
import styled from 'styled-components';
import { Button, Input, Select, Space, Tag, Toast, Typography } from '@douyinfe/semi-ui';
import { IconDelete, IconFile, IconPlus } from '@douyinfe/semi-icons';
import { OpsSkillArtifact } from '../../services/ops-admin-service';
import { theme } from '../../styles/theme';

const { Text } = Typography;

const EditorGrid = styled.div`
  display: grid;
  grid-template-columns: minmax(190px, 260px) minmax(0, 1fr);
  gap: ${theme.spacing.base};
  width: 100%;
  min-width: 0;

  @media (max-width: 760px) {
    grid-template-columns: minmax(0, 1fr);
  }
`;

const FilePanel = styled.div`
  min-width: 0;
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  overflow: hidden;
`;

const FileButton = styled.button<{ $selected: boolean }>`
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: ${theme.spacing.xs};
  padding: 9px 10px;
  border: 0;
  border-bottom: 1px solid ${theme.colors.border.secondary};
  background: ${(props) => (props.$selected ? '#eff6ff' : theme.colors.bg.primary)};
  color: ${theme.colors.text.primary};
  cursor: pointer;
  text-align: left;

  &:hover {
    background: #f5f9ff;
  }
`;

const FilePath = styled.span`
  min-width: 0;
  overflow-wrap: anywhere;
  word-break: break-word;
`;

const ContentEditor = styled.textarea`
  width: 100%;
  min-width: 0;
  min-height: 330px;
  padding: ${theme.spacing.base};
  resize: vertical;
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.primary};
  color: ${theme.colors.text.primary};
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
  font-size: 12px;
  line-height: 1.6;
  outline: none;
  white-space: pre;
  overflow: auto;

  &:focus {
    border-color: ${theme.colors.primary};
    box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.12);
  }

  &:read-only {
    background: ${theme.colors.bg.secondary};
  }
`;

const AddRow = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1fr) 120px auto;
  gap: ${theme.spacing.xs};
  padding: ${theme.spacing.sm};

  @media (max-width: 520px) {
    grid-template-columns: minmax(0, 1fr);
  }
`;

const ImportActions = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${theme.spacing.xs};
  padding: 0 ${theme.spacing.sm} ${theme.spacing.sm};
`;

const BinaryPreview = styled.div`
  width: 100%;
  min-height: 260px;
  padding: ${theme.spacing.lg};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.secondary};
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: ${theme.spacing.base};
  overflow: auto;

  img {
    max-width: 100%;
    max-height: 360px;
    object-fit: contain;
  }
`;

const ROLE_OPTIONS = [
  { value: 'RESOURCE', label: '资源' },
  { value: 'SCRIPT', label: '脚本' },
  { value: 'TEMPLATE', label: '模板' },
  { value: 'EVAL', label: '评测' },
  { value: 'REFERENCE', label: '参考资料' },
  { value: 'ASSET', label: '二进制资产' },
  { value: 'METADATA', label: '界面元数据' },
];

const TEXT_EXTENSIONS = new Set([
  'md', 'txt', 'json', 'yaml', 'yml', 'xml', 'sql', 'sh', 'py', 'js', 'ts', 'tsx', 'jsx',
  'java', 'kt', 'properties', 'toml', 'csv', 'css', 'scss', 'html', 'graphql', 'proto',
  'ini', 'conf', 'mustache', 'hbs', 'svg',
]);
const BINARY_EXTENSIONS = new Set([
  'png', 'jpg', 'jpeg', 'gif', 'webp', 'ico', 'pdf', 'docx', 'xlsx', 'pptx',
  'ttf', 'otf', 'woff', 'woff2',
]);

const extensionOf = (path: string) => path.split('.').pop()?.toLowerCase() || '';
const roleForPath = (path: string) => {
  if (path.startsWith('scripts/')) return 'SCRIPT';
  if (path.startsWith('references/')) return 'REFERENCE';
  if (path.startsWith('assets/')) return 'ASSET';
  if (path.startsWith('evals/')) return 'EVAL';
  if (path.startsWith('agents/')) return 'METADATA';
  if (path.startsWith('templates/')) return 'TEMPLATE';
  return BINARY_EXTENSIONS.has(extensionOf(path)) ? 'ASSET' : 'RESOURCE';
};

const roleLabel = (role?: string) => ROLE_OPTIONS.find((item) => item.value === role)?.label || role || '资源';

const normalizeFiles = (artifacts: OpsSkillArtifact[]) => artifacts
  .filter((item) => item.path && item.path !== 'SKILL.md')
  .map((item) => ({
    ...item,
    role: item.role || roleForPath(item.path),
    encoding: item.encoding || (BINARY_EXTENSIONS.has(extensionOf(item.path)) ? 'BASE64' : 'UTF8'),
    content: item.content || '',
  }));

export interface SkillPackageEditorProps {
  entrypointContent: string;
  artifacts: OpsSkillArtifact[];
  readOnly?: boolean;
  disabled?: boolean;
  onEntrypointChange?: (content: string) => void;
  onArtifactsChange?: (artifacts: OpsSkillArtifact[]) => void;
}

export const SkillPackageEditor: React.FC<SkillPackageEditorProps> = ({
  entrypointContent,
  artifacts,
  readOnly = false,
  disabled = false,
  onEntrypointChange,
  onArtifactsChange,
}) => {
  const files = useMemo(() => normalizeFiles(artifacts), [artifacts]);
  const [selectedPath, setSelectedPath] = useState('SKILL.md');
  const [newPath, setNewPath] = useState('');
  const [newRole, setNewRole] = useState('RESOURCE');
  const fileInputRef = useRef<HTMLInputElement>(null);
  const folderInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    folderInputRef.current?.setAttribute('webkitdirectory', '');
    folderInputRef.current?.setAttribute('directory', '');
  }, []);

  useEffect(() => {
    if (selectedPath !== 'SKILL.md' && !files.some((item) => item.path === selectedPath)) {
      setSelectedPath('SKILL.md');
    }
  }, [files, selectedPath]);

  const selected = files.find((item) => item.path === selectedPath);
  const selectedContent = selectedPath === 'SKILL.md' ? entrypointContent : selected?.content || '';

  const updateSelected = (content: string) => {
    if (selectedPath === 'SKILL.md') {
      onEntrypointChange?.(content);
      return;
    }
    onArtifactsChange?.(files.map((item) => item.path === selectedPath ? { ...item, content } : item));
  };

  const addFile = () => {
    const path = newPath.trim().replace(/\\/g, '/');
    if (!path || path === 'SKILL.md') {
      Toast.warning('请填写资源、脚本、模板或评测文件路径，SKILL.md 已由系统管理');
      return;
    }
    if (path.startsWith('/') || path.split('/').some((segment) => !segment || segment === '.' || segment === '..')) {
      Toast.warning('文件路径必须是 Skill 包内的相对路径，不能包含空目录或 ..');
      return;
    }
    if (files.some((item) => item.path === path)) {
      Toast.warning('该文件已存在');
      return;
    }
    const extension = extensionOf(path);
    if (BINARY_EXTENSIONS.has(extension)) {
      Toast.warning('二进制资产请使用“导入文件”或“导入 Skill 目录”，不能在文本编辑器中创建');
      return;
    }
    if (!TEXT_EXTENSIONS.has(extension)) {
      Toast.warning('该文件类型不在 Skill 安全白名单中');
      return;
    }
    const inferredRole = roleForPath(path);
    onArtifactsChange?.([...files, {
      path,
      role: inferredRole === 'RESOURCE' ? newRole : inferredRole,
      encoding: 'UTF8',
      content: '',
    }]);
    setSelectedPath(path);
    setNewPath('');
  };

  const readAsBase64 = (file: File) => new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(reader.error || new Error('读取文件失败'));
    reader.onload = () => resolve(String(reader.result || '').split(',', 2)[1] || '');
    reader.readAsDataURL(file);
  });

  const importFiles = async (fileList: FileList | null, fromFolder: boolean) => {
    if (!fileList?.length) return;
    const selectedFiles = Array.from(fileList);
    const roots = selectedFiles.map((file) => file.webkitRelativePath.split('/')[0]).filter(Boolean);
    const sharedRoot = fromFolder && roots.length === selectedFiles.length && roots.every((root) => root === roots[0])
      ? roots[0] : '';
    const imported: OpsSkillArtifact[] = [];
    let importedEntrypoint: string | undefined;
    const rejected: string[] = [];
    for (const file of selectedFiles) {
      const relative = (file.webkitRelativePath || file.name).replace(/\\/g, '/');
      const path = sharedRoot && relative.startsWith(`${sharedRoot}/`)
        ? relative.slice(sharedRoot.length + 1) : relative;
      const extension = extensionOf(path);
      if ((!TEXT_EXTENSIONS.has(extension) && !BINARY_EXTENSIONS.has(extension)) || file.size > 256 * 1024) {
        rejected.push(path);
        continue;
      }
      if (path === 'SKILL.md') {
        importedEntrypoint = await file.text();
        continue;
      }
      const binary = BINARY_EXTENSIONS.has(extension);
      imported.push({
        path,
        role: roleForPath(path),
        mediaType: file.type || undefined,
        encoding: binary ? 'BASE64' : 'UTF8',
        content: binary ? await readAsBase64(file) : await file.text(),
        sizeBytes: file.size,
      });
    }
    if (importedEntrypoint !== undefined) onEntrypointChange?.(importedEntrypoint);
    const merged = new Map<string, OpsSkillArtifact>(files.map((item) => [item.path, item]));
    imported.forEach((item) => merged.set(item.path, item));
    onArtifactsChange?.(Array.from(merged.values()));
    if (rejected.length) Toast.warning(`已跳过 ${rejected.length} 个不支持或超过 256KB 的文件`);
    if (imported.length || importedEntrypoint !== undefined) {
      Toast.success(`已导入 ${imported.length + (importedEntrypoint !== undefined ? 1 : 0)} 个 Skill 文件`);
    }
  };

  const binarySelected = selected?.encoding === 'BASE64';
  const binaryDataUrl = binarySelected && selected?.content
    ? `data:${selected.mediaType || 'application/octet-stream'};base64,${selected.content}` : '';

  const downloadSelected = () => {
    if (!selected || !binaryDataUrl) return;
    const anchor = document.createElement('a');
    anchor.href = binaryDataUrl;
    anchor.download = selected.path.split('/').pop() || 'skill-asset';
    anchor.click();
  };

  const removeSelected = () => {
    if (selectedPath === 'SKILL.md') return;
    onArtifactsChange?.(files.filter((item) => item.path !== selectedPath));
    setSelectedPath('SKILL.md');
  };

  return (
    <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
      <Space wrap>
        <Text strong>Skill 文件包</Text>
        <Tag color="blue">{files.length + 1} 个文件</Tag>
        <Text type="tertiary" size="small">
          SKILL.md 是入口；资源、脚本、模板、评测和资产会随版本一起保存。脚本不会自动获得执行权限。
        </Text>
      </Space>
      <EditorGrid>
        <FilePanel>
          <FileButton type="button" $selected={selectedPath === 'SKILL.md'} onClick={() => setSelectedPath('SKILL.md')}>
            <FilePath><IconFile /> SKILL.md</FilePath>
            <Tag size="small">入口</Tag>
          </FileButton>
          {files.map((file) => (
            <FileButton key={file.path} type="button" $selected={selectedPath === file.path} onClick={() => setSelectedPath(file.path)}>
              <FilePath>{file.path}</FilePath>
              <Tag size="small" color={file.role === 'SCRIPT' ? 'orange' : file.role === 'EVAL' ? 'purple' : 'grey'}>
                {roleLabel(file.role)}
              </Tag>
            </FileButton>
          ))}
          {!readOnly && (
            <>
              <AddRow>
                <Input value={newPath} placeholder="resources/schema.json" onChange={setNewPath} disabled={disabled} />
                <Select value={newRole} onChange={(value) => setNewRole(String(value || 'RESOURCE'))} disabled={disabled}>
                  {ROLE_OPTIONS.map((option) => <Select.Option key={option.value} value={option.value}>{option.label}</Select.Option>)}
                </Select>
                <Button icon={<IconPlus />} onClick={addFile} disabled={disabled} aria-label="添加 Skill 文本文件" />
              </AddRow>
              <ImportActions>
                <Button onClick={() => fileInputRef.current?.click()} disabled={disabled}>导入文件</Button>
                <Button onClick={() => folderInputRef.current?.click()} disabled={disabled}>导入 Skill 目录</Button>
                <input ref={fileInputRef} type="file" multiple hidden onChange={(event) => {
                  void importFiles(event.target.files, false);
                  event.target.value = '';
                }} />
                <input ref={folderInputRef} type="file" multiple hidden onChange={(event) => {
                  void importFiles(event.target.files, true);
                  event.target.value = '';
                }} />
              </ImportActions>
            </>
          )}
        </FilePanel>
        <Space vertical align="start" spacing="tight" style={{ width: '100%', minWidth: 0 }}>
          <Space wrap style={{ width: '100%', justifyContent: 'space-between' }}>
            <Text strong>{selectedPath}</Text>
            {!readOnly && selectedPath !== 'SKILL.md' && (
              <Button icon={<IconDelete />} type="danger" theme="borderless" onClick={removeSelected} disabled={disabled}>
                删除文件
              </Button>
            )}
          </Space>
          {binarySelected ? (
            <BinaryPreview>
              <Text strong>二进制资产</Text>
              <Text type="tertiary">{selected?.mediaType || 'application/octet-stream'} · {selected?.sizeBytes || 0} bytes</Text>
              {selected?.mediaType?.startsWith('image/') && binaryDataUrl && <img src={binaryDataUrl} alt={selected.path} />}
              <Button onClick={downloadSelected}>导出原始文件</Button>
              {!readOnly && <Text type="tertiary">需要替换时重新导入同一路径文件。</Text>}
            </BinaryPreview>
          ) : (
            <ContentEditor
              value={selectedContent}
              onChange={(event) => updateSelected(event.target.value)}
              readOnly={readOnly}
              disabled={disabled}
              spellCheck={false}
            />
          )}
        </Space>
      </EditorGrid>
    </Space>
  );
};
