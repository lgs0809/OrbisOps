import React from 'react';
import { Button, Table, Tag, Typography } from '@douyinfe/semi-ui';
import styled from 'styled-components';
import type { OpsChangePackage } from '../../../services/ops-change-package-types';
import { toChangePackageSummary } from '../model/change-package-summary';

const { Text } = Typography;
const ListViewport = styled.div`
  min-width: 0;
  overflow-x: auto;
`;

interface ChangePackageListProps {
  packages: OpsChangePackage[];
  projects: ReadonlyArray<{ projectId: string; name?: string }>;
  onOpen: (record: OpsChangePackage) => void;
}

/** Displays the server lifecycle as received; governed actions stay in the detail/query layer. */
export const ChangePackageList: React.FC<ChangePackageListProps> = ({ packages, projects, onOpen }) => (
  <ListViewport>
    <Table
      dataSource={packages}
      rowKey="packageId"
      pagination={{ pageSize: 10 }}
      scroll={{ x: 1256 }}
      columns={[
        {
          title: '受控变更包',
          dataIndex: 'packageId',
          width: 260,
          render: (_: unknown, record: OpsChangePackage) => {
            const summary = toChangePackageSummary(record);
            return (
              <div>
                <Text strong style={{ display: 'block', maxWidth: 232 }} ellipsis={{ showTooltip: true }}>{summary.title}</Text>
                <Text type="tertiary" style={{ display: 'block', maxWidth: 232, fontSize: 12 }} ellipsis={{ showTooltip: true }}>
                  {summary.packageId} · v{summary.version}
                </Text>
              </div>
            );
          },
        },
        {
          title: '项目',
          dataIndex: 'projectId',
          width: 160,
          render: (value: string) => projects.find((project) => project.projectId === value)?.name || '当前项目',
        },
        {
          title: '状态',
          dataIndex: 'status',
          width: 130,
          render: (_: string, record: OpsChangePackage) => {
            const summary = toChangePackageSummary(record);
            return <Tag color={summary.statusColor as any}>{summary.statusLabel}</Tag>;
          },
        },
        {
          title: '风险',
          dataIndex: 'riskLevel',
          width: 120,
          render: (_: string, record: OpsChangePackage) => {
            const summary = toChangePackageSummary(record);
            return <Tag color={summary.riskColor as any}>{summary.riskLabel}</Tag>;
          },
        },
        {
          title: '环境',
          dataIndex: 'targetEnvironment',
          width: 140,
          render: (value: string) => value || '-',
        },
        {
          title: '当前状态',
          dataIndex: 'reasonCode',
          width: 190,
          render: (_: string, record: OpsChangePackage) => {
            const summary = toChangePackageSummary(record);
            return (
              <Text style={{ display: 'block', maxWidth: 164 }} ellipsis={{ showTooltip: true }}>
                {summary.reasonCode ? summary.reasonLabel : summary.statusHint}
              </Text>
            );
          },
        },
        {
          title: '更新时间',
          dataIndex: 'updateTime',
          width: 160,
          render: (value: string) => value || '-',
        },
        {
          title: '操作',
          key: 'actions',
          width: 96,
          fixed: 'right' as const,
          render: (_: unknown, record: OpsChangePackage) => (
            <Button size="small" onClick={() => onOpen(record)}>查看</Button>
          ),
        },
      ]}
    />
  </ListViewport>
);
