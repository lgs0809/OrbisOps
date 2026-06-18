import { describe, expect, it } from 'vitest';

import { activationRateLabel, productActivationMetrics } from './activation-metrics-model';

describe('product activation metrics', () => {
  it('normalizes the authoritative activation projection without inventing missing facts', () => {
    const metrics = productActivationMetrics({
      activation: {
        projectCount: 5,
        evidenceConnectedProjects: 4,
        queryProofVerifiedProjects: 3,
        defaultAgentReadyProjects: 4,
        activatedProjects: 2,
        diagnosisReadyProjects: 1,
        activationRate: 0.4,
        definition: 'required onboarding complete',
        source: 'ProjectWorkspace projection',
      },
    });

    expect(metrics).toEqual({
      projectCount: 5,
      evidenceConnectedProjects: 4,
      queryProofVerifiedProjects: 3,
      defaultAgentReadyProjects: 4,
      activatedProjects: 2,
      diagnosisReadyProjects: 1,
      activationRate: 0.4,
      definition: 'required onboarding complete',
      source: 'ProjectWorkspace projection',
    });
    expect(activationRateLabel(metrics!)).toBe('40%');
  });

  it('returns no activation surface when the backend projection is absent', () => {
    expect(productActivationMetrics(null)).toBeNull();
    expect(productActivationMetrics({})).toBeNull();
  });
});
