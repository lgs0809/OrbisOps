import React, { useState } from 'react';
import { Button, Typography } from '@douyinfe/semi-ui';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { opsChangePackageService, type OpsExecutionScope } from '../../../services/ops-change-package-service';
import { changePackageQueryKeys } from '../api/change-package-queries';
import { userFacingError } from '../../../utils/user-facing-error';

/** Re-observes frozen postconditions; this action never starts a Landing agent. */
export const LandingPostcheckVerifier: React.FC<{ packageId: string; scope: OpsExecutionScope }> = ({ packageId, scope }) => {
  const [result, setResult] = useState('');
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: async () => {
      const response = await opsChangePackageService.verifyLanding(packageId, scope);
      if (response.code !== '0000') throw new Error(response.info || '无法完成复核');
      return response.data;
    },
    onSuccess: async (proof) => {
      setResult(proof?.passed === true ? '目标已通过全部已批准的后置检查，证据已保存。' : '后置检查未通过，失败证据已保存。');
      await queryClient.invalidateQueries({ queryKey: changePackageQueryKeys.detail(packageId, scope) });
    },
    onError: (error) => setResult(userFacingError(error, '复核暂时不可用，请稍后重试。')),
  });
  return <>
    <Button loading={mutation.isPending} disabled={mutation.isPending} onClick={() => { setResult(''); mutation.mutate(); }}>复核落地结果</Button>
    {result && <Typography.Text role="status">{result}</Typography.Text>}
  </>;
};
