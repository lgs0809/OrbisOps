import React from 'react';
import {
  Button,
  Space,
  Table,
  Tag,
  Typography,
} from '@douyinfe/semi-ui';
import {
  IconEdit,
  IconEyeOpened,
} from '@douyinfe/semi-icons';

import { TableScroll } from '../../components/ops-layout';
import { OpsMcpTemplate } from '../../services/ops-mcp-template-service';
import { riskColor } from './helpers';

interface Props {
  templates: OpsMcpTemplate[];
  loading: boolean;
  onView: (template: OpsMcpTemplate) => void;
  onEdit: (template: OpsMcpTemplate) => void;
}

export const McpTemplateTable: React.FC<Props> = ({
  templates,
  loading,
  onView,
  onEdit,
}) => (
  <TableScroll>
    <Table
      dataSource={templates}
      loading={loading}
      rowKey="templateId"
      scroll={{ x: 1390 }}
      pagination={{ pageSize: 10, showSizeChanger: true }}
      empty={<Typography.Text type="tertiary">暂无 MCP 接入模板</Typography.Text>}
      columns={[
        { title: '系统编号', dataIndex: 'templateId', width: 190 },
        { title: '模板名称', dataIndex: 'templateName', width: 210 },
        {
          title: '资源类型',
          dataIndex: 'resourceType',
          width: 130,
          render: (type: string) => <Tag color="blue">{type}</Tag>,
        },
        {
          title: '支持动作',
          dataIndex: 'supportedActions',
          width: 240,
          render: (actions: string[]) => (
            <Space wrap>
              {(actions || []).slice(0, 3).map((action) => <Tag key={action}>{action}</Tag>)}
              {(actions || []).length > 3 && <Tag>+{actions.length - 3}</Tag>}
            </Space>
          ),
        },
        {
          title: '风险',
          dataIndex: 'riskLevel',
          width: 100,
          render: (risk: string, record: OpsMcpTemplate) => (
            <Space>
              <Tag color={riskColor(risk)}>{risk || '-'}</Tag>
              {!record.readOnly && <Tag color="orange">非只读</Tag>}
            </Space>
          ),
        },
        { title: '适用项目数', dataIndex: 'projectCount', width: 110, render: (count: number) => count ?? 0 },
        { title: '生成工具数', dataIndex: 'generatedToolCount', width: 110, render: (count: number) => count ?? 0 },
        {
          title: '状态',
          dataIndex: 'status',
          width: 90,
          render: (value: string) => (
            <Tag color={value === 'ENABLED' ? 'green' : 'grey'}>
              {value === 'ENABLED' ? '启用' : '停用'}
            </Tag>
          ),
        },
        {
          title: '操作',
          key: 'actions',
          width: 160,
          fixed: 'right' as const,
          render: (_: unknown, record: OpsMcpTemplate) => (
            <Space wrap>
              <Button theme="borderless" type="tertiary" icon={<IconEyeOpened />} size="small" onClick={() => onView(record)}>
                查看
              </Button>
              <Button theme="borderless" type="primary" icon={<IconEdit />} size="small" onClick={() => onEdit(record)}>
                编辑
              </Button>
            </Space>
          ),
        },
      ]}
    />
  </TableScroll>
);
