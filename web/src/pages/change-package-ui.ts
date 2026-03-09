export const changePackageStatusLabel = (status?: string) => {
  switch (String(status || '').toUpperCase()) {
    case 'DRAFT':
      return '草稿';
    case 'VALIDATING':
      return '验证中';
    case 'VALIDATION_FAILED':
      return '验证失败';
    case 'REVISING':
      return '修订中';
    case 'READY_FOR_REVIEW':
      return '可提交审核';
    case 'REVIEWING':
      return '待审批';
    case 'REJECTED':
      return '已驳回';
    case 'APPROVED':
      return '已审批待落地';
    case 'LANDING':
    case 'LANDING_RUNNING':
      return '落地执行中';
    case 'LANDED':
      return '已落地';
    case 'LANDING_FAILED':
      return '落地失败';
    case 'NEEDS_REPLAN':
      return '需重新规划';
    case 'CLOSED':
      return '已关闭';
    case 'CANCELLED':
      return '已取消';
    default:
      return status || '-';
  }
};

export const changePackageStatusHint = (status?: string, reasonCode?: string) => {
  const reason = reasonCode ? `原因：${changePackageReasonLabel(reasonCode)}。` : '';
  switch (String(status || '').toUpperCase()) {
    case 'DRAFT':
      return '还没有完成审核前验证，不能提交审批。';
    case 'VALIDATION_FAILED':
      return `${reason || '审核前验证失败。'}需要回到对话或修订流程补证据、补测试或调整方案。`;
    case 'READY_FOR_REVIEW':
      return '验证通过，可以提交给审批人确认版本、风险和回滚边界。';
    case 'REVIEWING':
      return '正在等待审批。审批前请检查 packageHash、风险等级、验证证据、回滚方案和影响范围。';
    case 'APPROVED':
      return '已审批，只能由固定 LandingRuntime 按 approvedVersion 和 approvedPackageHash 执行。';
    case 'LANDING_FAILED':
      return `${reason || '落地失败。'}需要查看 operation run 和事件轨迹，判断重试、回滚或重新规划。`;
    case 'NEEDS_REPLAN':
      return `${reason || '后端判定当前审批快照不再适用。'}不能继续落地，需要重新生成或修订 ChangePackage 后再审批。`;
    case 'LANDED':
      return '已完成落地，可查看执行结果、post-check 和清理状态。';
    default:
      return reason || '当前状态下请按后端允许的操作继续。';
  }
};

export const changePackageReasonLabel = (reason?: string) => {
  switch (String(reason || '').toUpperCase()) {
    case 'PRECONDITION_DRIFT':
      return '目标当前状态与审批时不一致';
    case 'PRECONDITION_READ_FAILED':
      return '读取目标当前状态失败';
    case 'POST_CHECK_REQUIRED':
      return '缺少落地后验证';
    case 'POST_CHECK_FAILED':
    case 'LANDING_POST_CHECK_FAILED':
      return '落地后验证失败';
    case 'ROLLBACK_PLAN_REQUIRED':
      return '缺少回滚方案';
    case 'ROLLBACK_PRECONDITION_REQUIRED':
      return '缺少回滚前置条件';
    case 'MANUAL_FALLBACK_REQUIRED':
      return '需要人工接管方案';
    case 'LANDING_EXECUTOR_NOT_CONFIGURED':
      return '没有配置可执行的落地执行器';
    case 'OPERATION_HASH_MISMATCH':
      return '审批快照中的操作哈希不匹配';
    case 'SANDBOX_GATE_BLOCKED':
      return '旧版验证门禁不满足，请重新运行当前版本的审核前验证';
    case 'BOUNDARY_REJECTED':
      return '执行范围超出审批边界';
    case 'ADJUSTMENT_OUT_OF_BOUNDARY':
      return '小修超出已审批允许范围';
    case 'READY_FOR_REVIEW':
      return '验证通过，可提交审核';
    case 'VALIDATION_PASSED':
      return '审核前验证通过';
    case 'VALIDATION_FAILED':
      return '审核前验证失败';
    case 'LANDING_SUCCEEDED':
      return '落地执行成功';
    case 'LANDING_FAILED':
      return '落地执行失败';
    case 'MCP_TOOL_REQUIRES_CHANGE_PACKAGE':
      return '该工具必须通过 ChangePackage 审批后执行';
    case 'SANDBOX_UNAVAILABLE':
      return '旧版隔离验证环境不可用';
    case 'WAITING_SANDBOX':
      return '等待旧版隔离验证';
    case 'SANDBOX_FAILED':
      return '旧版隔离验证失败';
    default:
      return reason || '-';
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
    case 'LOW':
      return '低风险';
    case 'MEDIUM':
      return '中风险';
    case 'HIGH':
      return '高风险';
    case 'CRITICAL':
      return '关键风险';
    default:
      return risk || '-';
  }
};

export const changePackageRiskColor = (risk?: string) => {
  if (risk === 'CRITICAL') return 'red';
  if (risk === 'HIGH') return 'orange';
  if (risk === 'MEDIUM') return 'amber';
  return 'blue';
};
