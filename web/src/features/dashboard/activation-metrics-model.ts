export interface ProductActivationMetrics {
  projectCount: number;
  evidenceConnectedProjects: number;
  queryProofVerifiedProjects: number;
  defaultAgentReadyProjects: number;
  activatedProjects: number;
  diagnosisReadyProjects: number;
  activationRate: number | null;
  definition: string;
  source: string;
}

const count = (value: unknown): number => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.max(0, Math.trunc(parsed)) : 0;
};

const rate = (value: unknown): number | null => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.max(0, Math.min(1, parsed)) : null;
};

export const productActivationMetrics = (productMetrics?: Record<string, any> | null): ProductActivationMetrics | null => {
  const activation = productMetrics?.activation;
  if (!activation || typeof activation !== 'object' || Array.isArray(activation)) return null;
  return {
    projectCount: count(activation.projectCount),
    evidenceConnectedProjects: count(activation.evidenceConnectedProjects),
    queryProofVerifiedProjects: count(activation.queryProofVerifiedProjects),
    defaultAgentReadyProjects: count(activation.defaultAgentReadyProjects),
    activatedProjects: count(activation.activatedProjects),
    diagnosisReadyProjects: count(activation.diagnosisReadyProjects),
    activationRate: rate(activation.activationRate),
    definition: String(activation.definition || ''),
    source: String(activation.source || ''),
  };
};

export const activationRateLabel = (metrics: ProductActivationMetrics): string => (
  metrics.activationRate === null ? '暂无样本' : `${Math.round(metrics.activationRate * 100)}%`
);
