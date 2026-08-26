import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ApprovalCockpit } from './ApprovalCockpit';

describe('change state presentation', () => {
  it.each([
    ['LANDED', '已 Landing'], ['LANDING_RUNNING', 'Landing 中'], ['REVIEWING', '等待审批'],
  ])('shows %s without inferring actor authority or business verification', (status, label) => {
    render(<ApprovalCockpit record={{packageId:'cp-1',projectId:'p-1',packageType:'MCP_CHANGE',version:1,status}} />);
    expect(screen.getByText(label)).toBeVisible();
    expect(screen.queryByText('可审批', {exact:false})).toBeNull();
    expect(screen.queryByText('已验证', {exact:false})).toBeNull();
  });
});
