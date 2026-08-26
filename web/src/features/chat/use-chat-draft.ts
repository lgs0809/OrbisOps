import { useCallback, useLayoutEffect, useRef, useState } from 'react';

/** Keep unsent text per account/project/conversation, only for this mounted page. */
export function useChatDraft(scope: string) {
  const drafts = useRef(new Map<string, string>());
  const activeScope = useRef(scope);
  const [input, updateInput] = useState('');

  useLayoutEffect(() => {
    activeScope.current = scope;
    updateInput(drafts.current.get(scope) || '');
  }, [scope]);

  const setInput = useCallback((value: string) => {
    if (value) drafts.current.set(activeScope.current, value);
    else drafts.current.delete(activeScope.current);
    updateInput(value);
  }, []);
  const discardDraft = useCallback((key: string) => { drafts.current.delete(key); }, []);
  return { input, setInput, discardDraft };
}
