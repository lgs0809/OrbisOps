import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Button, Input, Modal, Select, Space, Tag, Toast, Typography } from '@douyinfe/semi-ui';
import { IconPlus, IconRefresh, IconUser } from '@douyinfe/semi-icons';
import styled from 'styled-components';

import { OpsPageHeader, OpsPageShell, OpsSectionCard } from '../../../components/ops-layout';
import type { AdminUserResponseDTO } from '../../../services/admin-user-service';
import { theme } from '../../../styles/theme';
import { userFacingError } from '../../../utils/user-facing-error';
import { usePlatformUsersQuery, useSavePlatformUserMutation, useResetPlatformPasswordMutation } from '../api/platform-user-queries';
import { accountStatus, displayNameError, newPasswordError, normalizePhone, phoneInputError } from '../model/account-input';

const { Text } = Typography;
const { Option } = Select;
const Toolbar = styled.div`
  display: flex; align-items: center; justify-content: space-between;
  gap: ${theme.spacing.base}; padding: 0 ${theme.spacing.lg} ${theme.spacing.base}; flex-wrap: wrap;
`;
const UserGrid = styled.div`
  display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: ${theme.spacing.base}; padding: 0 ${theme.spacing.lg} ${theme.spacing.lg};
`;
const UserCard = styled.article`
  display: flex; flex-direction: column; gap: ${theme.spacing.sm}; min-width: 0;
  padding: ${theme.spacing.base}; border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base}; background: ${theme.colors.bg.primary};
`;
const UserHeading = styled.div`
  display: flex; align-items: center; justify-content: space-between; gap: ${theme.spacing.sm};
`;
const FieldGrid = styled.div`
  display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: ${theme.spacing.base};
  @media (max-width: ${theme.breakpoints.sm}) { grid-template-columns: 1fr; }
`;
const Field = styled.label`
  display: flex; flex-direction: column; gap: ${theme.spacing.xs}; color: ${theme.colors.text.secondary};
`;

const emptyForm = () => ({ username: '', phone: '', password: '', userRole: 'user', status: 1 });
type Editor = { kind: 'create' } | { kind: 'update'; user: AdminUserResponseDTO };

export const UsersAccessPage: React.FC = () => {
  const usersQuery = usePlatformUsersQuery();
  const saveMutation = useSavePlatformUserMutation();
  const resetPassword = useResetPlatformPasswordMutation();
  const [editor, setEditor] = useState<Editor | null>(null);
  const [form, setForm] = useState(emptyForm);
  const [formError, setFormError] = useState('');
  const [savingTicket, setSavingTicket] = useState<number | null>(null);
  const [busyUserId, setBusyUserId] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const editorTicket = useRef(0);
  const inFlightTickets = useRef(new Set<number>());
  const alive = useRef(true);
  useEffect(() => {
    alive.current = true;
    return () => { alive.current = false; editorTicket.current += 1; };
  }, []);

  const sortedUsers = useMemo(() => {
    const query = search.trim().toLowerCase();
    return [...(usersQuery.data || [])]
      .filter((user) => !query || user.username.toLowerCase().includes(query) || user.phone?.includes(query))
      .sort((left, right) => left.username.localeCompare(right.username));
  }, [usersQuery.data, search]);

  const closeEditor = () => {
    editorTicket.current += 1;
    setEditor(null); setForm(emptyForm()); setFormError(''); setSavingTicket(null);
  };
  const openEditor = (next: Editor) => {
    editorTicket.current += 1;
    setEditor(next); setFormError(''); setSavingTicket(null);
    setForm(next.kind === 'create' ? emptyForm() : {
      username: next.user.username, phone: next.user.phone || '', password: '',
      userRole: String(next.user.userRole || 'user').toLowerCase(), status: next.user.status ?? -1,
    });
  };
  const saveUser = async () => {
    if (!editor || inFlightTickets.current.has(editorTicket.current)) return;
    const error = displayNameError(form.username) || phoneInputError(form.phone)
      || (editor.kind === 'create' ? newPasswordError(form.password) : undefined);
    if (error) { setFormError(error); return; }
    if (editor.kind === 'update' && editor.user.id === undefined) {
      setFormError('成员信息不完整，请刷新列表后再试。'); return;
    }
    if (![0, 1, 2].includes(form.status)) {
      setFormError('请先确认成员的账号状态。'); return;
    }
    const ticket = editorTicket.current;
    inFlightTickets.current.add(ticket); setSavingTicket(ticket); setFormError('');
    try {
      const common = { username: form.username.trim(), phone: normalizePhone(form.phone), userRole: form.userRole, status: form.status };
      await saveMutation.mutateAsync(editor.kind === 'create'
        ? { kind: 'create', payload: { ...common, password: form.password } }
        : { kind: 'update', payload: { ...common, id: editor.user.id } });
      if (alive.current && editorTicket.current === ticket) {
        closeEditor(); Toast.success(editor.kind === 'create' ? '成员已添加，可使用手机号和初始密码登录。' : '成员信息已保存，原密码和项目权限保持不变。');
      }
    } catch (error) {
      if (alive.current && editorTicket.current === ticket) setFormError(userFacingError(error, '保存成员信息失败，请稍后重试。'));
    } finally {
      inFlightTickets.current.delete(ticket);
      if (alive.current && editorTicket.current === ticket) setSavingTicket(null);
    }
  };

  const setEnabled = async (user: AdminUserResponseDTO) => {
    if (busyUserId || !user.userId || user.id === undefined || !accountStatus(user.status).canToggle) return;
    const enabled = user.status === 0;
    setBusyUserId(user.userId);
    try {
      await saveMutation.mutateAsync({ kind: 'update', payload: {
        id: user.id, username: user.username, userRole: user.userRole, status: enabled ? 1 : 0,
      } });
      if (alive.current) Toast.success(enabled ? '成员已启用。' : '成员已停用。');
    } catch (error) {
      if (alive.current) Toast.error(userFacingError(error, '更新成员状态失败，请稍后重试。'));
    } finally {
      if (alive.current) setBusyUserId(null);
    }
  };
  const saving = savingTicket === editorTicket.current;

  return (
    <OpsPageShell selectedKey="settings">
      <OpsPageHeader title="成员与访问控制"
        description="由已有管理员添加成员，使用手机号和密码登录。加入后再按需要分配项目权限。"
        extra={<Space>
          <Button icon={<IconRefresh />} loading={usersQuery.isFetching} onClick={() => void usersQuery.refetch()}>刷新</Button>
          <Button theme="solid" type="primary" icon={<IconPlus aria-hidden />} onClick={() => openEditor({ kind: 'create' })}>管理员添加成员</Button>
        </Space>} />
      <OpsSectionCard title={`团队成员（${usersQuery.data?.length || 0}）`}>
        <Toolbar>
          <Text type="tertiary">平台角色与项目角色分别管理。添加账号不会自动授予项目或生产操作权限。</Text>
          <Input aria-label="搜索成员" placeholder="搜索姓名或手机号" value={search} showClear onChange={setSearch} style={{ width: 240 }} />
        </Toolbar>
        {usersQuery.isError && <div role="alert" style={{ padding: '0 24px 16px' }}><Text type="danger">
          {userFacingError(usersQuery.error, '加载成员失败，请点击刷新重试。')}
        </Text></div>}
        <UserGrid>
          {sortedUsers.map((user) => {
            const status = accountStatus(user.status);
            return <UserCard key={user.userId || user.id} aria-label={`成员 ${user.username}`}>
              <UserHeading><Space><IconUser /><Text strong>{user.username}</Text></Space>
                <Tag color={status.color}>{status.label}</Tag></UserHeading>
              <Text type={user.phone ? 'secondary' : 'warning'}>{user.phone || '未绑定手机号'}</Text>
              <Space wrap>
                <Tag>{String(user.userRole || 'user').toLowerCase() === 'admin' ? '管理员' : '普通用户'}</Tag>
                <Button size="small" disabled={user.id === undefined} onClick={() => openEditor({ kind: 'update', user })}>
                  {user.phone ? '管理成员' : '绑定手机号'}
                </Button>
                <Button size="small" disabled={!user.userId || resetPassword.isPending}
                  onClick={() => Modal.confirm({ title: `重置 ${user.username} 的初始密码？`,
                    content: '恢复创建账号时的初始密码。已有登录会话会失效，成员下次登录必须设置自己的密码。',
                    okText: '重置初始密码', cancelText: '取消',
                    okButtonProps: { 'aria-label': '重置初始密码' }, cancelButtonProps: { 'aria-label': '取消' },
                    onOk: async () => { try { await resetPassword.mutateAsync(user.userId!); Toast.success('已恢复初始密码，下次登录须修改。'); }
                      catch (error) { Toast.error(userFacingError(error, '重置未完成，请重试。')); throw error; } },
                  })}>重置初始密码</Button>
                {status.canToggle && <Button size="small" loading={busyUserId === user.userId} disabled={Boolean(busyUserId)}
                  onClick={() => void setEnabled(user)}>{user.status === 1 ? '停用' : '启用'}</Button>}
              </Space>
              {user.status === 2 && <Text type="tertiary" size="small">账号已锁定，管理员核对后可在成员管理中调整状态。</Text>}
            </UserCard>;
          })}
          {!usersQuery.isLoading && !usersQuery.isError && sortedUsers.length === 0 && <Text type="tertiary">
            {search ? '没有匹配的成员。' : '暂无成员，请由管理员添加。'}
          </Text>}
        </UserGrid>
      </OpsSectionCard>
      <Modal title={editor?.kind === 'create' ? '管理员添加成员' : '管理成员'} visible={Boolean(editor)}
        confirmLoading={saving} onOk={() => void saveUser()} onCancel={closeEditor}
        okText={editor?.kind === 'create' ? '添加成员' : '保存成员信息'} cancelText={saving ? '关闭窗口' : '取消'}
        okButtonProps={{ 'aria-label': editor?.kind === 'create' ? '添加成员' : '保存成员信息' }}
        cancelButtonProps={{ 'aria-label': saving ? '关闭窗口' : '取消' }}>
        <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
          <FieldGrid>
            <Field><Text strong>姓名</Text><Input aria-label="姓名" value={form.username} maxLength={50}
              disabled={saving || editor?.kind === 'update'} autoComplete="name" placeholder="成员姓名"
              onChange={(username) => setForm((current) => ({ ...current, username }))} /></Field>
            <Field><Text strong>手机号</Text><Input aria-label="手机号" type="tel" inputMode="tel" autoComplete="tel"
              value={form.phone} disabled={saving} placeholder="用于登录的手机号"
              onChange={(phone) => setForm((current) => ({ ...current, phone }))} /></Field>
            <Field><Text strong id="platform-member-role-label">平台角色</Text><Select aria-labelledby="platform-member-role-label" value={form.userRole} disabled={saving} style={{ width: '100%' }}
              onChange={(userRole) => setForm((current) => ({ ...current, userRole: String(userRole || 'user') }))}>
              <Option value="user">普通用户</Option><Option value="admin">管理员</Option>
            </Select></Field>
            <Field><Text strong id="platform-member-status-label">账号状态</Text><Select aria-labelledby="platform-member-status-label" value={form.status} disabled={saving} style={{ width: '100%' }}
              onChange={(status) => setForm((current) => ({ ...current, status: Number(status) }))}>
              <Option value={1}>已启用</Option><Option value={0}>已停用</Option><Option value={2}>已锁定</Option>
              {form.status === -1 && <Option value={-1} disabled>状态待确认</Option>}
            </Select></Field>
          </FieldGrid>
          {editor?.kind === 'create' && <Field style={{ width: '100%' }}><Text strong>初始密码</Text>
            <Input aria-label="初始密码" type="password" autoComplete="new-password" value={form.password} disabled={saving}
              placeholder="至少 8 位，同时包含字母和数字" onChange={(password) => setForm((current) => ({ ...current, password }))} />
          </Field>}
          {formError && <Text role="alert" type="danger">{formError}</Text>}
          <Text type="tertiary">{editor?.kind === 'create'
            ? '普通用户是默认角色。请通过安全渠道把初始密码交给成员，首次登录必须修改；再在「项目 → 成员」中分配权限。'
            : '绑定手机号不会重置原密码。原账号名称、用户身份和项目关系保持不变。'}</Text>
          {saving && <Text type="tertiary">请求已经提交，关闭窗口不会撤销变更；完成后成员列表会自动更新。</Text>}
        </Space>
      </Modal>
    </OpsPageShell>
  );
};
