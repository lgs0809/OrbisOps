import { useEffect, useRef, useState } from 'react';
import { Button, Form, Space, Typography } from '@douyinfe/semi-ui';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import styled from 'styled-components';
import { accountCredentialService } from '../services/account-credential-service';
import { saveAuthSession } from '../services/auth-session';
import { logoutAccount } from '../services/account-logout';
import { newPasswordError } from '../features/platform/model/account-input';
import { userFacingError } from '../utils/user-facing-error';

const Shell = styled.main`min-height:100vh;display:grid;place-items:center;background:#f6f7f9;padding:24px;`;
const Panel = styled.section`width:100%;max-width:440px;background:#fff;border:1px solid #e5e7eb;border-radius:16px;padding:32px;box-shadow:0 12px 34px rgb(15 23 42 / 7%);`;
export const AccountPasswordPage = () => {
  const navigate = useNavigate();
  const alive = useRef(true);
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  const profile = useQuery({ queryKey: ['my-account'], queryFn: accountCredentialService.profile, retry: false });
  const [error, setError] = useState('');
  const change = useMutation({
    mutationFn: async ({ currentPassword, newPassword }: { currentPassword: string; newPassword: string }) => {
      const previousToken = localStorage.getItem('token');
      return { account: await accountCredentialService.change(currentPassword, newPassword), previousToken };
    },
    onSuccess: ({ account, previousToken }) => {
      if (!alive.current || localStorage.getItem('token') !== previousToken) return;
      saveAuthSession(account); navigate('/chat', { replace: true });
    },
    onError: (problem) => { if (alive.current) setError(userFacingError(problem, '密码未修改，请重试。')); },
  });
  const submit = (values: Record<string, any>) => {
    if (change.isPending || profile.isPending || profile.isError) return;
    const problem = !values.currentPassword ? '请输入当前密码。' : newPasswordError(values.newPassword || '', '新密码')
      || (values.newPassword !== values.confirmPassword ? '两次输入的新密码不一致。' : '');
    setError(problem || '');
    if (!problem) change.mutate({ currentPassword: values.currentPassword, newPassword: values.newPassword });
  };
  const initial = profile.data?.passwordChangeRequired;
  return <Shell><Panel aria-label="账号密码设置">
    <Typography.Text strong>OrbisOps · {profile.data?.username || '我的账号'}</Typography.Text>
    <Typography.Title heading={3}>{initial ? '设置自己的登录密码' : '修改登录密码'}</Typography.Title>
    <Typography.Paragraph>{initial ? '你正在使用初始密码。请先设置自己的密码，再进入工作空间。' : '设置新密码后，之前的登录会话会失效。'}</Typography.Paragraph>
    {profile.isError && <Space vertical align="start"><Typography.Text type="danger" role="alert">读取账号状态失败，请重试。</Typography.Text><Button onClick={() => void profile.refetch()}>重新读取</Button></Space>}
    <Form onSubmit={submit} disabled={change.isPending || profile.isPending || profile.isError}>
      <Form.Input field="currentPassword" label={initial ? '初始密码' : '当前密码'} type="password" autoComplete="current-password" />
      <Form.Input field="newPassword" label="新密码" type="password" autoComplete="new-password" placeholder="至少 8 位，同时包含字母和数字" />
      <Form.Input field="confirmPassword" label="确认新密码" type="password" autoComplete="new-password" />
      {error && <Typography.Paragraph type="danger" role="alert">{error}</Typography.Paragraph>}
      <Button htmlType="submit" theme="solid" type="primary" block loading={change.isPending} disabled={change.isPending || profile.isPending || profile.isError}>保存并进入工作空间</Button>
    </Form>
    <Button theme="borderless" style={{marginTop:16}} onClick={() => void logoutAccount(() => navigate('/login', {replace:true}))}>退出登录</Button>
    {!initial && profile.data && <Button theme="borderless" onClick={() => navigate('/chat')}>返回工作空间</Button>}
  </Panel></Shell>;
};
