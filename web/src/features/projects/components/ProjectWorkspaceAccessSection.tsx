import React from 'react';
import styled from 'styled-components';
import { Button, Card, Space, Tag, Typography } from '@douyinfe/semi-ui';
import { IconEdit } from '@douyinfe/semi-icons';

import { OpsProjectMember } from '../../../services/ops-project-service';
import { theme } from '../../../styles/theme';

const { Text } = Typography;

const Section = styled(Card)`
  margin-bottom: ${theme.spacing.lg};

  .semi-card-body {
    padding: ${theme.spacing.lg};
  }
`;

const ResourceTitle = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${theme.spacing.base};
  flex-wrap: wrap;
`;

const ResourceGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(280px, 100%), 1fr));
  gap: ${theme.spacing.base};
  margin-top: ${theme.spacing.base};
`;

type Props = {
  members: OpsProjectMember[];
  onManageMembers: () => void;
};

const memberKeyOf = (member: OpsProjectMember) => String(
  member.memberKey
  || member.member_key
  || member.userId
  || member.user_id
  || member.username
  || '',
);

export const ProjectWorkspaceAccessSection: React.FC<Props> = ({ members, onManageMembers }) => (
  <Section title="项目成员与使用权限">
    <ResourceTitle>
      <div>
        <Text strong>谁可以使用当前项目的 Agent</Text>
        <div>
          <Text type="tertiary" size="small">
            项目负责人默认可访问；这里的显式成员可以在普通用户工作台看到项目，并使用项目下已发布 Agent。
          </Text>
        </div>
      </div>
      <Button icon={<IconEdit />} onClick={onManageMembers}>管理成员</Button>
    </ResourceTitle>
    <ResourceGrid>
      {members.map((member) => {
        const memberKey = memberKeyOf(member);
        return (
          <Card key={memberKey}>
            <Space vertical align="start">
              <Text strong>{member.username || member.userId || member.user_id || memberKey}</Text>
              <Text type="tertiary" size="small">用户 ID：{member.userId || member.user_id || '-'}</Text>
              <Tag color="blue">{member.memberRole || member.member_role || 'MEMBER'}</Tag>
            </Space>
          </Card>
        );
      })}
      {!members.length && (
        <Card>
          <Space vertical align="start">
            <Text strong>暂无显式项目成员</Text>
            <Text type="tertiary" size="small">
              当前只有项目负责人可访问。普通用户需要被加入项目后才能看到项目和 Agent。
            </Text>
          </Space>
        </Card>
      )}
    </ResourceGrid>
  </Section>
);
