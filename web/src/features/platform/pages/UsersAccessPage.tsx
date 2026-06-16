import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Input, Modal, Select, Space, Tag, Toast, Typography } from '@douyinfe/semi-ui';
import { IconPlus, IconRefresh, IconUser } from '@douyinfe/semi-icons';
import styled from 'styled-components';

import { OpsPageHeader, OpsPageShell, OpsSectionCard } from '../../../components/ops-layout';
import { PlatformUserService } from '../../../services/platform-user-service';
import type { AdminUserResponseDTO } from '../../../services/admin-user-service';
import { theme } from '../../../styles/theme';
import { userFacingError } from '../../../utils/user-facing-error';

const { Text } = Typography;
const { Option } = Select;

const Toolbar = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${theme.spacing.base};
  padding: 0 ${theme.spacing.lg} ${theme.spacing.base};
  flex-wrap: wrap;
`;

const UserGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: ${theme.spacing.base};
  padding: 0 ${theme.spacing.lg} ${theme.spacing.lg};
`;

const UserCard = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.sm};
  min-width: 0;
  padding: ${theme.spacing.base};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.primary};
`;

const UserHeading = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${theme.spacing.sm};
`;

const FieldGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: ${theme.spacing.base};

  @media (max-width: ${theme.breakpoints.sm}) {
    grid-template-columns: 1fr;
  }
`;

const Field = styled.label`
  display: flex;
  flex-direction: column;
  gap: ${theme.spacing.xs};
  color: ${theme.colors.text.secondary};
`;

const userIdFor = (username: string) => {
  const suffix = typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID().replace(/-/g, '').slice(0, 12)
    : `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`;
  // admin_user.user_id is a VARCHAR(64). Keep the generated identifier
  // bounded at the ingress instead of letting a long username reach SQL and
  // fail as an opaque truncation error. The random suffix still guarantees
  // uniqueness when two accounts share the same slug.
  const prefix = 'user-';
  const normalized = username.trim().toLowerCase().replace(/[^a-z0-9_-]+/g, '-').replace(/^-+|-+$/g, '');
  const maxSlugLength = Math.max(1, 64 - prefix.length - suffix.length - 1);
  const slug = (normalized || 'account').slice(0, maxSlugLength);
  return `${prefix}${slug}-${suffix}`;
};

export const UsersAccessPage: React.FC = () => {
  const [users, setUsers] = useState<AdminUserResponseDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState({ username: '', password: '', userRole: 'user' });

  const loadUsers = useCallback(async () => {
    setLoading(true);
    try {
      setUsers(await PlatformUserService.list());
    } catch (error) {
      Toast.error(userFacingError(error, '加载平台用户失败，请稍后重试。'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadUsers();
  }, [loadUsers]);

  const sortedUsers = useMemo(
    () => [...users].sort((left, right) => left.username.localeCompare(right.username)),
    [users],
  );

  const createUser = async () => {
    const username = form.username.trim();
    if (!username || form.password.length < 8) {
      Toast.warning('请输入用户名，并设置至少 8 个字符的初始密码。');
      return;
    }
    setSaving(true);
    try {
      await PlatformUserService.create({
        userId: userIdFor(username),
        username,
        password: form.password,
        userRole: form.userRole,
        status: 1,
      });
      setModalVisible(false);
      setForm({ username: '', password: '', userRole: 'user' });
      await loadUsers();
      Toast.success('平台用户已创建。');
    } catch (error) {
      Toast.error(userFacingError(error, '创建平台用户失败，请稍后重试。'));
    } finally {
      setSaving(false);
    }
  };

  const setEnabled = async (user: AdminUserResponseDTO, enabled: boolean) => {
    if (!user.userId) return;
    setSaving(true);
    try {
      await PlatformUserService.update({
        id: user.id,
        userId: user.userId,
        username: user.username,
        password: '',
        userRole: user.userRole,
        status: enabled ? 1 : 0,
      });
      await loadUsers();
      Toast.success(enabled ? '用户已启用。' : '用户已停用。');
    } catch (error) {
      Toast.error(userFacingError(error, '更新平台用户失败，请稍后重试。'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <OpsPageShell selectedKey="settings">
      <OpsPageHeader
        title="用户与访问控制"
        description="在这里创建和管理平台账号。Project 成员关系只能引用已经存在的平台用户。"
        extra={(
          <Space>
            <Button icon={<IconRefresh />} loading={loading} onClick={() => void loadUsers()}>刷新</Button>
            <Button theme="solid" type="primary" icon={<IconPlus />} onClick={() => setModalVisible(true)}>创建用户</Button>
          </Space>
        )}
      />

      <OpsSectionCard title={`平台用户（${sortedUsers.length}）`}>
        <Toolbar>
          <Text type="tertiary">平台角色控制账号级访问；Project 内的角色需要在「项目 → 成员」中单独分配。</Text>
        </Toolbar>
        <UserGrid>
          {sortedUsers.map((user) => {
            const enabled = user.status !== 0;
            return (
              <UserCard key={user.userId || user.username}>
                <UserHeading>
                  <Space>
                    <IconUser />
                    <Text strong>{user.username}</Text>
                  </Space>
                  <Tag color={enabled ? 'green' : 'grey'}>{enabled ? '已启用' : '已停用'}</Tag>
                </UserHeading>
                <Text type="tertiary">{user.userId || '暂无用户 ID'}</Text>
                <Space wrap>
                  <Tag>{String(user.userRole || 'user').toLowerCase() === 'admin' ? '管理员' : '普通用户'}</Tag>
                  <Button
                    size="small"
                    loading={saving}
                    onClick={() => void setEnabled(user, !enabled)}
                  >
                    {enabled ? '停用' : '启用'}
                  </Button>
                </Space>
              </UserCard>
            );
          })}
          {!loading && sortedUsers.length === 0 && <Text type="tertiary">暂无平台用户。</Text>}
        </UserGrid>
      </OpsSectionCard>

      <Modal
        title="创建平台用户"
        visible={modalVisible}
        confirmLoading={saving}
        onOk={() => void createUser()}
        onCancel={() => setModalVisible(false)}
        okText="创建用户"
        cancelText="取消"
      >
        <Space vertical align="start" spacing="medium" style={{ width: '100%' }}>
          <FieldGrid>
            <Field>
              <Text strong>用户名</Text>
              <Input value={form.username} placeholder="ops-user" onChange={(username) => setForm((current) => ({ ...current, username }))} />
            </Field>
            <Field>
              <Text strong>平台角色</Text>
              <Select value={form.userRole} style={{ width: '100%' }} onChange={(userRole) => setForm((current) => ({ ...current, userRole: String(userRole || 'user') }))}>
                <Option value="user">普通用户</Option>
                <Option value="admin">管理员</Option>
              </Select>
            </Field>
          </FieldGrid>
          <Field style={{ width: '100%' }}>
            <Text strong>初始密码</Text>
            <Input type="password" value={form.password} placeholder="至少 8 个字符" onChange={(password) => setForm((current) => ({ ...current, password }))} />
          </Field>
          <Text type="tertiary">只有先创建平台账号，之后才能把该用户加入 Project。</Text>
        </Space>
      </Modal>
    </OpsPageShell>
  );
};
