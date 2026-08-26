/** Retain governed argument limits while reviewing a tool's effects and resource binding. */
export const policyArguments = (record: Record<string, unknown>): Record<string, unknown> => {
  const value = record.argumentPolicy ?? record.argument_policy ?? record.argumentPolicyJson ?? record.argument_policy_json;
  if (value === undefined || value === null || value === '') return {};
  let decoded: unknown = value;
  if (typeof value === 'string') {
    try { decoded = JSON.parse(value); }
    catch { throw new Error('原参数限制无法读取，请刷新后重试审核。'); }
  }
  if (!decoded || typeof decoded !== 'object' || Array.isArray(decoded)) {
    throw new Error('原参数限制无法读取，请刷新后重试审核。');
  }
  return { ...(decoded as Record<string, unknown>) };
};

export const policyResourceBinding = (argumentsPolicy: Record<string, unknown>) => {
  const value = argumentsPolicy.resourceBinding;
  if (value === undefined || value === null) return { targetEnvironment: '', resourceScope: '' };
  if (typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('原资源范围无法读取，请刷新后重试审核。');
  }
  const binding = value as Record<string, unknown>;
  return {
    targetEnvironment: String(binding.targetEnvironment ?? ''),
    resourceScope: String(binding.resourceScope ?? ''),
  };
};

export const reviewArguments = (argumentsPolicy: Record<string, unknown>, targetEnvironment: string, resourceScope: string) => {
  const environment = targetEnvironment.trim();
  const scope = resourceScope.trim();
  const previous = policyResourceBinding(argumentsPolicy);
  if (Boolean(environment) !== Boolean(scope)) throw new Error('请同时填写目标环境和资源范围。');
  if (!environment && (previous.targetEnvironment || previous.resourceScope)) {
    throw new Error('不能在审核时清空已有资源范围，请保留或明确填写新的范围。');
  }
  if (!environment) return { ...argumentsPolicy };
  return {
    ...argumentsPolicy,
    resourceBinding: {
      ...(argumentsPolicy.resourceBinding as Record<string, unknown> | undefined),
      targetEnvironment: environment,
      resourceScope: scope,
    },
  };
};
