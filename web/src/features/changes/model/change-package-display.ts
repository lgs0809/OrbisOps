export const changePackageStatusLabel = (status?: string) => {
  switch (String(status || '').toUpperCase()) {
    case 'DRAFT': return '草稿';
    case 'VALIDATING': return '校验中';
    case 'VALIDATION_FAILED': return '校验失败';
    case 'REVISING': return '修订中';
    case 'READY_FOR_REVIEW': return '待提交审批';
    case 'REVIEWING': return '等待审批';
    case 'REJECTED': return '已驳回';
    case 'APPROVED': return '已批准 · 可执行 Landing';
    case 'LANDING':
    case 'LANDING_RUNNING': return 'Landing 中';
    case 'LANDED': return '已 Landing';
    case 'LANDING_FAILED': return 'Landing 失败';
    case 'NEEDS_REPLAN': return '需要重规划';
    case 'CLOSED': return '已关闭';
    case 'CANCELLED': return '已取消';
    default: return status || '-';
  }
};

export const changePackageReasonLabel = (reason?: string) => {
  switch (String(reason || '').toUpperCase()) {
    case 'PRECONDITION_DRIFT': return '批准后目标状态发生变化';
    case 'PRECONDITION_READ_FAILED': return '无法读取当前目标状态';
    case 'POST_CHECK_REQUIRED': return '缺少 Landing 后的 Verification';
    case 'POST_CHECK_FAILED':
    case 'LANDING_POST_CHECK_FAILED': return 'Landing 后的 Verification 失败';
    case 'ROLLBACK_PLAN_REQUIRED': return '缺少回滚方案';
    case 'ROLLBACK_PRECONDITION_REQUIRED': return '缺少回滚前置条件';
    case 'MANUAL_FALLBACK_REQUIRED': return '需要人工接管方案';
    case 'LANDING_EXECUTOR_NOT_CONFIGURED': return '尚未配置可执行的 Landing 目标';
    case 'OPERATION_HASH_MISMATCH': return '操作 hash 与已批准快照不一致';
    case 'SANDBOX_GATE_BLOCKED': return '旧版校验门禁未满足；请重新执行当前审批前校验';
    case 'BOUNDARY_REJECTED': return '执行超出已批准边界';
    case 'ADJUSTMENT_OUT_OF_BOUNDARY': return '调整超出已批准边界';
    case 'READY_FOR_REVIEW': return '校验通过，可以提交审批';
    case 'VALIDATION_PASSED': return '审批前校验通过';
    case 'VALIDATION_FAILED': return '审批前校验失败';
    case 'LANDING_SUCCEEDED': return 'Landing 成功';
    case 'LANDING_FAILED': return 'Landing 失败';
    case 'MCP_TOOL_REQUIRES_CHANGE_PACKAGE': return '该工具必须通过已批准的 ChangePackage 执行';
    case 'SANDBOX_UNAVAILABLE': return '旧版隔离校验环境不可用';
    case 'WAITING_SANDBOX': return '等待旧版隔离校验';
    case 'SANDBOX_FAILED': return '旧版隔离校验失败';
    default: return reason || '-';
  }
};

export const changePackageStatusHint = (status?: string, reasonCode?: string) => {
  const reason = reasonCode ? `原因：${changePackageReasonLabel(reasonCode)}。` : '';
  switch (String(status || '').toUpperCase()) {
    case 'DRAFT': return '审批前校验尚未完成，因此当前 ChangePackage 还不能提交审批。';
    case 'VALIDATION_FAILED': return `${reason || '审批前校验失败。'}请返回对话或修订流程，补充证据、重新测试或调整方案。`;
    case 'READY_FOR_REVIEW': return '校验已通过。请提交审批，由审批人核对版本、风险、证据、回滚方案和影响边界。';
    case 'REVIEWING': return '等待审批。批准前请核对 packageHash、风险、校验证据、回滚方案和影响边界。';
    case 'APPROVED': return '已批准。只有受治理的 LandingRuntime 可以执行 approvedVersion 与 approvedPackageHash。';
    case 'LANDING_RUNNING': return '已批准的方案正在执行，请查看实时操作记录。';
    case 'REJECTED': return '方案已驳回；修订后需要重新校验并提交审批。';
    case 'LANDING_FAILED': return `${reason || 'Landing 失败。'}请检查操作记录和事件轨迹，再决定重试、回滚或重新规划。`;
    case 'NEEDS_REPLAN': return `${reason || '已批准快照不再适用。'}在新的或修订后的 ChangePackage 获得批准前，Landing 不能继续。`;
    case 'LANDED': return 'Landing 已完成。请检查执行结果、Verification 和清理状态。';
    default: return reason || '只能继续执行当前后端状态允许的操作。';
  }
};

export const changePackageStatusColor = (status?: string) => {
  const normalized = String(status || '').toUpperCase();
  if (['APPROVED', 'LANDED', 'CLOSED', 'READY_FOR_REVIEW'].includes(normalized)) return 'green';
  if (['NEEDS_REPLAN', 'LANDING_FAILED', 'REJECTED', 'VALIDATION_FAILED', 'CANCELLED'].includes(normalized)) return 'red';
  if (['LANDING', 'LANDING_RUNNING', 'REVIEWING', 'VALIDATING', 'REVISING'].includes(normalized)) return 'orange';
  return 'blue';
};

export const changePackageRiskLabel = (risk?: string) => {
  switch (String(risk || '').toUpperCase()) {
    case 'LOW': return '低';
    case 'MEDIUM': return '中';
    case 'HIGH': return '高';
    case 'CRITICAL': return '严重';
    default: return risk || '-';
  }
};

export const changePackageRiskColor = (risk?: string) => {
  const normalized = String(risk || '').toUpperCase();
  if (normalized === 'CRITICAL') return 'red';
  if (normalized === 'HIGH') return 'orange';
  if (normalized === 'MEDIUM') return 'amber';
  return 'blue';
};
