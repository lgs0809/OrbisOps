import React from 'react';
import { Button, Typography } from '@douyinfe/semi-ui';

const { Text, Title } = Typography;

interface State {
  error?: Error;
}

export class AppErrorBoundary extends React.Component<React.PropsWithChildren, State> {
  state: State = {};

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error('Ops console render failure', error, info.componentStack);
  }

  private reload = () => window.location.reload();

  render() {
    if (!this.state.error) return this.props.children;
    return (
      <main role="alert" style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 24 }}>
        <div style={{ maxWidth: 560 }}>
          <Title heading={3}>当前页面无法显示</Title>
          <Text type="secondary">页面渲染时发生错误。审批或生产执行操作不会被自动重放；可以安全刷新后重新检查页面状态。</Text>
          <div style={{ marginTop: 16 }}>
            <Button type="primary" onClick={this.reload}>刷新页面</Button>
          </div>
        </div>
      </main>
    );
  }
}
