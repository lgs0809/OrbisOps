import { describe, expect, it } from 'vitest';
import { policyArguments, policyResourceBinding, reviewArguments } from './policy-review-arguments';

describe('tool policy review retains governed limits', () => {
  it('keeps all known and future restrictions when only effects are reviewed', () => {
    const original = { requiredKeys: ['projectId'], forbiddenKeys: ['secret'], maxBytes: 20, futureConstraint: { denied: true } };
    const input = policyArguments({ argumentPolicy: original });
    expect(reviewArguments(input, '', '')).toEqual(original);
    expect(input).not.toBe(original);
  });

  it('preserves native JSON constraints and changes only the explicitly edited binding', () => {
    const original = { requiredKeys: ['service'], maxLimit: 17, resourceBinding: {
      resourceScope: 'service://before/test', targetEnvironment: 'test', immutableResourceId: 'target-1',
    } };
    const input = policyArguments({ argument_policy_json: JSON.stringify(original) });
    expect(policyResourceBinding(input)).toEqual({ targetEnvironment: 'test', resourceScope: 'service://before/test' });
    expect(reviewArguments(input, ' test ', ' service://after/test ')).toEqual({ ...original, resourceBinding: {
      ...original.resourceBinding, targetEnvironment: 'test', resourceScope: 'service://after/test',
    } });
    expect(original.resourceBinding.resourceScope).toBe('service://before/test');
  });

  it('adds an explicit binding to a fresh suggestion without erasing safety limits', () => {
    const input = policyArguments({ argumentPolicy: { forbiddenKeys: ['secret'], requiredKeys: [] } });
    expect(reviewArguments(input, 'test', 'service://resource/test')).toEqual({
      forbiddenKeys: ['secret'], requiredKeys: [], resourceBinding: { targetEnvironment: 'test', resourceScope: 'service://resource/test' },
    });
  });

  it('does not infer a resource scope or synthesize an unrelated maxLimit', () => {
    expect(reviewArguments(policyArguments({}), '', '')).toEqual({});
  });

  it.each(['bad JSON', '[]', 'null', '3'])('rejects unreadable native policy instead of replacing it: %s', (value) => {
    expect(() => policyArguments({ argumentPolicyJson: value })).toThrow('原参数限制无法读取');
  });

  it('rejects malformed bindings and partial or implicit removal', () => {
    expect(() => policyResourceBinding({ resourceBinding: 'bad' })).toThrow('原资源范围无法读取');
    expect(() => reviewArguments({}, 'test', '')).toThrow('同时填写');
    expect(() => reviewArguments({}, '', 'service://resource/test')).toThrow('同时填写');
    expect(() => reviewArguments({ resourceBinding: { targetEnvironment: 'test', resourceScope: 'service://resource/test' } }, '', '')).toThrow('不能在审核时清空');
  });
});
