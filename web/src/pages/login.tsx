import React, { useEffect, useRef, useState } from 'react';
import { Button, Form, Toast, Typography } from '@douyinfe/semi-ui';
import { IconEyeClosed, IconEyeOpened, IconLock, IconSafe, IconUser } from '@douyinfe/semi-icons';
import { useNavigate } from 'react-router-dom';
import styled from 'styled-components';
import { theme } from '../styles/theme';
import { Card } from '../components/common';
import { AdminUserService } from '../services';
import { FirstTimeSetupService } from '../services/first-time-setup-service';
import { defaultRouteForCurrentUser, normalizeUserRole, saveAuthSession } from '../services/auth-session';
import { userFacingError } from '../utils/user-facing-error';
import { displayNameError, newPasswordError, normalizePhone, phoneInputError } from '../features/platform/model/account-input';
import type { AdminUserResponseDTO } from '../services/admin-user-service';

const { Title, Text } = Typography;

// 样式组件
const LoginContainer = styled.div`
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  position: relative;
  background: #f6f7f9;

  @media (max-width: ${theme.breakpoints.sm}) {
    padding: 18px;
  }
`;

const LoginWrapper = styled.div`
  width: 100%;
  max-width: 980px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 400px;
  gap: 72px;
  align-items: center;

  @media (max-width: ${theme.breakpoints.lg}) {
    grid-template-columns: 1fr;
    max-width: 420px;
    gap: 24px;
  }
`;

const BrandSection = styled.div`
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 18px;

  @media (max-width: ${theme.breakpoints.lg}) {
    display: none;
  }
`;

const BrandLogo = styled.div`
  width: 50px;
  height: 50px;
  display: grid;
  place-items: center;
  border-radius: 13px;
  background: #111827;
  color: #fff;
  box-shadow: ${theme.shadows.sm};

  .semi-icon {
    font-size: 28px;
    color: white;
  }
`;

const BrandTitle = styled(Title)`
  color: ${theme.colors.text.primary};
  margin: 0 !important;
  font-weight: ${theme.typography.fontWeight.bold};
`;

const BrandDescription = styled(Text)`
  max-width: 560px;
  color: ${theme.colors.text.secondary};
  font-size: 16px;
  line-height: 1.7;
`;

const CapabilityGrid = styled.div`
  display: grid;
  grid-template-columns: 1fr;
  gap: 0;
  width: 100%;
  max-width: 560px;
  margin-top: 6px;
`;

const CapabilityItem = styled.div`
  padding: 11px 0;
  border-bottom: 1px solid ${theme.colors.border.secondary};

  &:last-child {
    border-bottom: 0;
  }

  strong {
    display: block;
    margin-bottom: 3px;
    color: ${theme.colors.text.primary};
    font-size: 13px;
    font-weight: 600;
  }

  span {
    color: ${theme.colors.text.tertiary};
    font-size: 12px;
    line-height: 1.6;
  }
`;

const LoginCard = styled(Card)`
  padding: 30px !important;
  border: 1px solid ${theme.colors.border.secondary} !important;
  border-radius: 14px !important;
  background: #fff !important;
  box-shadow: 0 12px 34px rgb(15 23 42 / 7%) !important;
`;

const LoginHeader = styled.div`
  text-align: left;
  margin-bottom: 24px;
`;

const LoginTitle = styled(Title)`
  color: ${theme.colors.text.primary};
  margin-bottom: ${theme.spacing.sm} !important;
  font-weight: ${theme.typography.fontWeight.bold};
`;

const LoginSubtitle = styled(Text)`
  color: ${theme.colors.text.secondary};
  font-size: ${theme.typography.fontSize.base};
`;

const StyledForm = styled(Form)`
  .semi-form-field {
    margin-bottom: ${theme.spacing.lg};
  }
`;

const LoginButton = styled(Button)`
  width: 100%;
  height: 44px;
  border-radius: 10px;
  background: ${theme.colors.primary} !important;
  border: none !important;
  color: white !important;
  font-weight: 600;
  font-size: 14px;

  &:hover {
    background: ${theme.colors.primaryHover} !important;
  }
`;

const LoginFootnote = styled(Text)`
  display: block;
  margin-top: ${theme.spacing.base};
  color: ${theme.colors.text.tertiary};
  font-size: ${theme.typography.fontSize.sm};
  text-align: center;
`;

const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [setupRequired, setSetupRequired] = useState<boolean | null>(null);
  const [setupError, setSetupError] = useState('');
  const [setupAttempt, setSetupAttempt] = useState(0);
  const [legacyLogin, setLegacyLogin] = useState(false);
  const [formError, setFormError] = useState('');
  const alive = useRef(true);
  const requestInFlight = useRef(false);

  useEffect(() => {
    let active = true;
    alive.current = true;
    setSetupRequired(null);
    setSetupError('');
    FirstTimeSetupService.isRequired()
      .then((required) => {
        if (active) setSetupRequired(required);
      })
      .catch((error) => {
        if (active) setSetupError(userFacingError(error, '暂时无法确认平台状态，请重试。'));
      });
    return () => {
      active = false;
      alive.current = false;
    };
  }, [setupAttempt]);

  const persistSession = (loginUser: AdminUserResponseDTO, fallbackUsername: string) => {
    if (!loginUser.token) return false;
    const userInfo = {
      userId: loginUser.userId,
      username: loginUser.username || fallbackUsername,
      phone: loginUser.phone,
      userRole: normalizeUserRole(loginUser.userRole),
      loginTime: new Date().toISOString(),
      token: loginUser.token,
      passwordChangeRequired: Boolean(loginUser.passwordChangeRequired),
    };
    saveAuthSession(userInfo);
    return true;
  };

  const handleLogin = async (values: Record<string, any>) => {
    if (requestInFlight.current || setupRequired === null) return;
    setFormError('');
    const reject = (message: string) => setFormError(message);
    const phone = normalizePhone(String(values.phone || ''));
    const username = String(values.username || '').trim();
    const password = String(values.password || '');
    if ((!legacyLogin || setupRequired) && phoneInputError(phone)) return reject(phoneInputError(phone)!);
    if ((setupRequired || legacyLogin) && displayNameError(username)) return reject(setupRequired ? displayNameError(username)! : '请输入原账号。');
    if (!password) return reject('请输入密码。');
    if (setupRequired && newPasswordError(password)) return reject(newPasswordError(password)!);
    if (setupRequired && password !== values.confirmPassword) return reject('两次输入的密码不一致。');
    requestInFlight.current = true;
    setLoading(true);
    try {
      if (setupRequired) {
        const firstAdministrator = await FirstTimeSetupService.createFirstAdministrator({
          username, phone, password,
        });
        if (!alive.current) return;
        if (!persistSession(firstAdministrator, username)) {
          throw new Error('初始化已完成，但未返回有效登录会话。');
        }
        Toast.success('首位管理员已创建，欢迎使用 OrbisOps。');
        navigate(firstAdministrator.passwordChangeRequired ? '/account/password' : '/home');
        return;
      }

      const loginUser = await AdminUserService.loginAdminUser({
        ...(legacyLogin ? { username } : { phone }), password,
      });
      if (!alive.current) return;
      if (loginUser?.token && persistSession(loginUser, username)) {
        Toast.success('登录成功。');
        navigate(defaultRouteForCurrentUser());
      } else {
        reject(legacyLogin ? '原账号或密码错误。' : '手机号或密码错误。');
      }
    } catch (error) {
      if (alive.current) reject(userFacingError(error, '登录失败，请稍后重试。'));
    } finally {
      requestInFlight.current = false;
      if (alive.current) setLoading(false);
    }
  };

  return (
    <LoginContainer>
      <LoginWrapper>
        <BrandSection>
          <BrandLogo>
            <IconSafe />
          </BrandLogo>
          <BrandTitle heading={1}>OrbisOps</BrandTitle>
          <BrandDescription>
            用自然对话完成排查与分析，用可复用工作流标准化重复任务，并让每次执行与生产变更都可追踪、可审批、可验证。
          </BrandDescription>
          <CapabilityGrid>
            <CapabilityItem>
              <strong>智能排查</strong>
              <span>直接描述问题，OrbisOps 会结合已授权的工具、数据和知识完成分析。</span>
            </CapabilityItem>
            <CapabilityItem>
              <strong>标准流程</strong>
              <span>将稳定的处理方法固化为工作流，支持手动运行和自动触发。</span>
            </CapabilityItem>
            <CapabilityItem>
              <strong>证据可追溯</strong>
              <span>关键查询、判断和执行结果会随任务保存，便于复核与审计。</span>
            </CapabilityItem>
            <CapabilityItem>
              <strong>受控变更</strong>
              <span>涉及生产环境的操作需要明确审批，并保留执行与验证记录。</span>
            </CapabilityItem>
          </CapabilityGrid>
        </BrandSection>

        <LoginCard>
          <LoginHeader>
            <LoginTitle heading={3}>{setupRequired ? '初始化 OrbisOps' : '登录'}</LoginTitle>
            <LoginSubtitle>
              {setupRequired
                ? '创建平台首位管理员，完成后将自动登录。'
                : '使用手机号和密码，进入团队运维工作台。'}
            </LoginSubtitle>
          </LoginHeader>

          {setupError && <div role="alert" style={{ marginBottom: 16 }}>
            <Text type="danger">{setupError}</Text>
            <Button theme="borderless" onClick={() => setSetupAttempt((attempt) => attempt + 1)}>重试读取平台状态</Button>
          </div>}
          <StyledForm key={`${setupRequired}-${legacyLogin}`} onSubmit={handleLogin}>
            {(setupRequired || !legacyLogin) && <Form.Input
              field="phone" label="手机号" placeholder="请输入手机号" type="tel" inputMode="tel"
              autoComplete={setupRequired ? 'tel' : 'username'} disabled={loading}
              prefix={<IconUser />} size="large"
              rules={[{ required: true, message: '请输入手机号。' },
                { validator: (_rule, value) => !phoneInputError(String(value || '')), message: '请输入有效的中国大陆手机号。' }]}
            />}
            {(setupRequired || legacyLogin) && <Form.Input
              field="username" label={setupRequired ? '姓名' : '原账号'}
              placeholder={setupRequired ? '管理员姓名' : '原用户名'} autoComplete={setupRequired ? 'name' : 'username'}
              maxLength={50} prefix={<IconUser />} size="large" disabled={loading}
              rules={[{ required: true, message: setupRequired ? '请输入姓名。' : '请输入原账号。' }]}
            />}
            <Form.Input
              field="password" label={setupRequired ? '管理员密码' : '密码'}
              type={showPassword ? 'text' : 'password'}
              autoComplete={setupRequired ? 'new-password' : 'current-password'} disabled={loading}
              placeholder={setupRequired ? '管理员密码' : '密码'}
              prefix={<IconLock />}
              size="large"
              suffix={
                <Button
                  theme="borderless"
                  htmlType="button"
                  aria-label={showPassword ? '隐藏密码' : '显示密码'}
                  icon={showPassword ? <IconEyeClosed /> : <IconEyeOpened />}
                  onClick={() => setShowPassword(!showPassword)}
                  style={{ padding: '4px' }}
                />
              }
              rules={[
                { required: true, message: '请输入密码。' },
                ...(setupRequired ? [{ min: 8, message: '管理员密码至少 8 个字符。' }] : []),
              ]}
            />
            {setupRequired && (
              <Form.Input
                field="confirmPassword" label="确认密码" autoComplete="new-password" disabled={loading}
                type={showPassword ? 'text' : 'password'}
                placeholder="确认管理员密码"
                prefix={<IconLock />}
                size="large"
                rules={[{ required: true, message: '请再次输入管理员密码。' }]}
              />
            )}

            {formError && <p role="alert"><Text type="danger">{formError}</Text></p>}
            <LoginButton
              type="primary"
              htmlType="submit"
              loading={loading || (setupRequired === null && !setupError)}
              disabled={setupRequired === null || loading}
            >
              {setupRequired ? '创建管理员并进入系统' : '登录'}
            </LoginButton>
          </StyledForm>

          {setupRequired ? (
            <LoginFootnote>
              仅当平台还没有任何用户时才会显示首次初始化入口。管理员密码至少 8 位，并同时包含字母和数字。
            </LoginFootnote>
          ) : setupRequired === false && <>
            <LoginFootnote>需要账号或忘记密码？请联系已有管理员添加成员或处理账号。</LoginFootnote>
            <details style={{ marginTop: 16 }}>
              <summary style={{ cursor: 'pointer', color: theme.colors.text.tertiary, fontSize: 12 }}>旧账号尚未绑定手机号？</summary>
              <Text type="tertiary" size="small">迁移期间可使用原账号登录，再联系管理员绑定手机号。</Text>
              <Button theme="borderless" disabled={loading} onClick={() => { setLegacyLogin(!legacyLogin); setFormError(''); }}>
                {legacyLogin ? '返回手机号登录' : '使用原账号登录'}
              </Button>
            </details>
          </>}
        </LoginCard>
      </LoginWrapper>
    </LoginContainer>
  );
};
export default LoginPage;
