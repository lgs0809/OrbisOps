import React, { useState } from 'react';
import { Button, Form, Toast, Typography } from '@douyinfe/semi-ui';
import { IconEyeClosed, IconEyeOpened, IconLock, IconSafe, IconUser } from '@douyinfe/semi-icons';
import { useNavigate } from 'react-router-dom';
import styled from 'styled-components';
import { theme } from '../styles/theme';
import { Card } from '../components/common';
import { AdminUserService } from '../services';
import { defaultRouteForCurrentUser, normalizeUserRole } from '../services/auth-session';

const { Title, Text } = Typography;

// 样式组件
const LoginContainer = styled.div`
  min-height: 100vh;
  background:
    linear-gradient(120deg, rgba(37, 99, 235, 0.08), rgba(15, 118, 110, 0.1)),
    ${theme.colors.bg.secondary};
  display: flex;
  align-items: center;
  justify-content: center;
  padding: ${theme.spacing.lg};
  position: relative;
`;

const LoginWrapper = styled.div`
  width: 100%;
  max-width: 1120px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 420px;
  gap: ${theme.spacing['2xl']};
  align-items: center;

  @media (max-width: ${theme.breakpoints.lg}) {
    grid-template-columns: 1fr;
    max-width: 440px;
    gap: ${theme.spacing.xl};
  }
`;

const BrandSection = styled.div`
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: ${theme.spacing.lg};

  @media (max-width: ${theme.breakpoints.lg}) {
    display: none;
  }
`;

const BrandLogo = styled.div`
  width: 72px;
  height: 72px;
  background: ${theme.colors.gradient.primary};
  border-radius: ${theme.borderRadius['2xl']};
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: ${theme.shadows.lg};

  .semi-icon {
    font-size: 48px;
    color: white;
  }
`;

const BrandTitle = styled(Title)`
  color: ${theme.colors.text.primary};
  margin: 0 !important;
  font-weight: ${theme.typography.fontWeight.bold};
`;

const BrandDescription = styled(Text)`
  color: ${theme.colors.text.secondary};
  font-size: ${theme.typography.fontSize.lg};
  line-height: ${theme.typography.lineHeight.relaxed};
  max-width: 620px;
`;

const CapabilityGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: ${theme.spacing.base};
  width: 100%;
  max-width: 680px;
`;

const CapabilityItem = styled.div`
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: rgba(255, 255, 255, 0.76);
  padding: ${theme.spacing.base};

  strong {
    display: block;
    color: ${theme.colors.text.primary};
    font-size: ${theme.typography.fontSize.base};
    margin-bottom: ${theme.spacing.xs};
  }

  span {
    color: ${theme.colors.text.tertiary};
    font-size: ${theme.typography.fontSize.sm};
    line-height: 1.6;
  }
`;

const LoginCard = styled(Card)`
  padding: ${theme.spacing['2xl']} !important;
  box-shadow: ${theme.shadows.lg} !important;
  border: 1px solid ${theme.colors.border.secondary} !important;
`;

const LoginHeader = styled.div`
  text-align: center;
  margin-bottom: ${theme.spacing.xl};
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
  height: 48px;
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.gradient.primary} !important;
  border: none !important;
  color: white !important;
  font-weight: ${theme.typography.fontWeight.medium};
  font-size: ${theme.typography.fontSize.base};

  &:hover {
    opacity: 0.9;
    transform: translateY(-1px);
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

  const handleLogin = async (values: Record<string, any>) => {
    setLoading(true);
    try {
      if (!values.username || !values.password) {
        Toast.error('请输入账号和密码');
        return;
      }

      const loginUser = await AdminUserService.loginAdminUser({
        username: values.username,
        password: values.password
      });

      if (loginUser?.token) {
        const userInfo = {
          userId: loginUser.userId,
          username: loginUser.username || values.username,
          userRole: normalizeUserRole(loginUser.userRole),
          loginTime: new Date().toISOString(),
          token: loginUser.token
        };
        localStorage.setItem('token', userInfo.token);
        localStorage.setItem('userInfo', JSON.stringify(userInfo));
        localStorage.setItem('isLoggedIn', 'true');
        Toast.success('登录成功！');
        navigate(defaultRouteForCurrentUser());
      } else {
        Toast.error('账号或密码错误，请重试');
      }
    } catch (error) {
      console.error('登录失败:', error);
      Toast.error('登录失败，请检查网络连接或稍后重试');
    } finally {
      setLoading(false);
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
            面向生产运维的受控 Agent 平台，把 ReAct 调查、可编排 Workflow、MCP 工具、知识与变更治理放进同一条可信执行链。
          </BrandDescription>
          <CapabilityGrid>
            <CapabilityItem>
              <strong>ReAct + Workflow</strong>
              <span>未知任务交给默认 ReAct；成熟 SOP 使用可发布、可恢复的 Workflow 固定宏观流程。</span>
            </CapabilityItem>
            <CapabilityItem>
              <strong>Evidence First</strong>
              <span>MCP、RAG、指标、日志和数据库查询形成可追踪证据，不把模型文字冒充执行事实。</span>
            </CapabilityItem>
            <CapabilityItem>
              <strong>Governed Changes</strong>
              <span>调查与准备变更和生产落地分权；ChangePackage 审批后才允许独立 Landing Runtime 执行。</span>
            </CapabilityItem>
            <CapabilityItem>
              <strong>Durable Runtime</strong>
              <span>运行、节点、工具调用与 checkpoint 可持久化，进程异常后可安全恢复而不是盲目重跑。</span>
            </CapabilityItem>
          </CapabilityGrid>
        </BrandSection>

        <LoginCard>
          <LoginHeader>
            <LoginTitle heading={3}>
              欢迎登录
            </LoginTitle>
            <LoginSubtitle>
              请输入您的账号和密码
            </LoginSubtitle>
          </LoginHeader>

          <StyledForm onSubmit={handleLogin}>
            <Form.Input
              field="username"
              placeholder="请输入账号"
              prefix={<IconUser />}
              size="large"
              rules={[
                { required: true, message: '请输入账号' },
                { min: 3, message: '账号至少3个字符' }
              ]}
            />
            <Form.Input
              field="password"
              type={showPassword ? 'text' : 'password'}
              placeholder="请输入密码"
              prefix={<IconLock />}
              size="large"
              suffix={
                <Button
                  theme="borderless"
                  htmlType="button"
                  icon={showPassword ? <IconEyeClosed /> : <IconEyeOpened />}
                  onClick={() => setShowPassword(!showPassword)}
                  style={{ padding: '4px' }}
                />
              }
              rules={[
                { required: true, message: '请输入密码' },
                { min: 6, message: '密码至少6个字符' }
              ]}
            />

            <LoginButton
              type="primary"
              htmlType="submit"
              loading={loading}
            >
              立即登录
            </LoginButton>
          </StyledForm>

          <LoginFootnote>后台接口使用登录令牌访问，页面不会内置演示账号。</LoginFootnote>
        </LoginCard>
      </LoginWrapper>
    </LoginContainer>
  );
};
export default LoginPage;
