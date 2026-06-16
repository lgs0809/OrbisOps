import React from 'react';
import styled from 'styled-components';

import { theme } from '../../styles/theme';

const BoundaryNote = styled.div`
  padding: ${theme.spacing.base};
  border: 1px solid #bfdbfe;
  border-radius: ${theme.borderRadius.base};
  background: #eff6ff;
  color: #1d4ed8;
  line-height: 1.6;
`;

export const McpTemplateBoundaryNote: React.FC = () => (
  <BoundaryNote>
    MCP 接入模板只描述资源类型、传输方式和默认能力边界；Agent 只能绑定当前项目已经生成或启用的项目工具。模板默认传输配置会在项目生成工具时被项目数据连接、密钥和权限策略覆盖。
  </BoundaryNote>
);
