import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { IncidentWorkspaceTabs } from './IncidentWorkspaceTabs';

afterEach(() => cleanup());

describe('IncidentWorkspaceTabs', () => {
  it('exposes the five governed incident work surfaces', () => {
    render(<IncidentWorkspaceTabs value="overview" onChange={() => undefined} />);

    ['Overview', 'Investigation', 'Evidence', 'Change', 'Timeline'].forEach((label) => {
      expect(screen.getByRole('button', { name: label })).toBeInTheDocument();
    });
  });

  it('changes work surface without inventing a second incident state', () => {
    const onChange = vi.fn();
    render(<IncidentWorkspaceTabs value="overview" onChange={onChange} />);

    fireEvent.click(screen.getByRole('button', { name: 'Evidence' }));
    expect(onChange).toHaveBeenCalledWith('evidence');
  });
});
