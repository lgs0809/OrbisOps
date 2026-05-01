import React, { useMemo } from 'react';
import styled from 'styled-components';
import { Button, Empty, Spin, Table, Tag, Typography } from '@douyinfe/semi-ui';
import { IconBranch, IconHistory, IconShield } from '@douyinfe/semi-icons';
import { useNavigate } from 'react-router-dom';

import { OpsPageHeader, OpsPageShell, TableScroll } from '../components/ops-layout';
import { useMyAuditsQuery } from '../features/governance/api/user-audit-queries';
import type { UserAuditSummary } from '../services/ops-user-service';
import { theme } from '../styles/theme';

const { Text } = Typography;

const CardGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: ${theme.spacing.base};
  margin-bottom: ${theme.spacing.base};

  @media (max-width: ${theme.breakpoints.lg}) { grid-template-columns: 1fr; }
`;

const StatusCard = styled.section`
  min-width: 0;
  padding: ${theme.spacing.base};
  background: ${theme.colors.bg.primary};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.xl};
  box-shadow: ${theme.shadows.sm};

  .title {
    display: flex;
    align-items: center;
    gap: ${theme.spacing.sm};
    margin-bottom: ${theme.spacing.sm};
    color: ${theme.colors.text.primary};
    font-weight: ${theme.typography.fontWeight.semibold};
  }

  .hint {
    margin: 0;
    color: ${theme.colors.text.tertiary};
    font-size: ${theme.typography.fontSize.sm};
    line-height: ${theme.typography.lineHeight.normal};
  }
`;

const Panel = styled.section`
  padding: ${theme.spacing.lg};
  background: ${theme.colors.bg.primary};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.xl};
  box-shadow: ${theme.shadows.sm};
`;

export const UserAuditRecordsPage: React.FC = () => {
  const navigate = useNavigate();
  const auditsQuery = useMyAuditsQuery();
  const loading = auditsQuery.isLoading;
  const error = auditsQuery.isError
    ? (auditsQuery.error instanceof Error ? auditsQuery.error.message : '我的审计加载失败')
    : '';
  const audits = auditsQuery.data || [];

  const auditStats = useMemo(() => {
    const textOf = (audit: UserAuditSummary) => `${audit.moduleName || ''} ${audit.actionName || ''}`.toLowerCase();
    return {
      conversations: audits.filter((audit) => /chat|agent/.test(textOf(audit))).length,
      tools: audits.filter((audit) => /tool|mcp|rag|skill/.test(textOf(audit))).length,
      executions: audits.filter((audit) => /change|approve|reject|verification|execution/.test(textOf(audit))).length,
      exports: audits.filter((audit) => /export|导出/.test(textOf(audit))).length,
    };
  }, [audits]);

  return (
    <OpsPageShell selectedKey="my-audit">
          <OpsPageHeader
            title="我的审计"
            description="只展示我的对话、我的工具调用、我的执行申请、我的审批记录和我的导出记录。不会暴露平台内部密钥、执行节点地址或原始执行参数。"
            primaryAction={<Button icon={<IconBranch />} onClick={() => navigate('/chat')}>返回 AI 对话</Button>}
          />

          <CardGrid>
            <StatusCard><div className="title"><IconBranch />我的对话</div><p className="hint">相关审计 {auditStats.conversations} 条，包含我触发的 Agent 决策或对话事件。</p></StatusCard>
            <StatusCard><div className="title"><IconShield />我的工具调用</div><p className="hint">相关审计 {auditStats.tools} 条，只展示可读摘要，不展示内部工具参数。</p></StatusCard>
            <StatusCard><div className="title"><IconHistory />我的执行 / 导出</div><p className="hint">执行相关 {auditStats.executions} 条，导出相关 {auditStats.exports} 条。</p></StatusCard>
          </CardGrid>

          <Panel style={{ marginBottom: theme.spacing.base }}>
            <Text type="tertiary">
              这里的数据来自后端个人审计接口，只按当前登录用户过滤；如果没有可见记录，页面保持空状态，不硬编码演示数据。
            </Text>
          </Panel>

          <Panel>
            <Spin spinning={loading}>
              {error && <Text type="danger">我的审计暂时不可用：{error}</Text>}
              {!error && audits.length === 0 && (
                <Empty title="暂无与你相关的审计记录" description="当你发起诊断、确认提案或参与执行流程后，会在这里看到个人审计轨迹。" />
              )}
              {!error && audits.length > 0 && (
                <TableScroll>
                  <Table
                    dataSource={audits}
                    rowKey="id"
                    pagination={false}
                    columns={[
                      {
                        title: '记录',
                        dataIndex: 'summary',
                        render: (_: unknown, record: UserAuditSummary) => (
                          <div>
                            <strong>{record.summary || `${record.moduleName || '-'} / ${record.actionName || '-'}`}</strong>
                            <div style={{ color: theme.colors.text.tertiary, fontSize: theme.typography.fontSize.sm }}>
                              {record.projectId || '未关联项目'} · {record.targetId || '-'}
                            </div>
                          </div>
                        ),
                      },
                      {
                        title: '模块',
                        dataIndex: 'moduleName',
                        width: 160,
                        render: (moduleName: string) => <Tag>{moduleName || '-'}</Tag>,
                      },
                      {
                        title: '动作',
                        dataIndex: 'actionName',
                        width: 160,
                        render: (actionName: string) => actionName || '-',
                      },
                      {
                        title: '时间',
                        dataIndex: 'createdAt',
                        width: 180,
                        render: (createdAt: string) => createdAt || '-',
                      },
                    ]}
                  />
                </TableScroll>
              )}
            </Spin>
          </Panel>
    </OpsPageShell>
  );
};
