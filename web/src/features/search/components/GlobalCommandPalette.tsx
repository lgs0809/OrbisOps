import React, { useEffect, useMemo, useState } from 'react';
import { Button, Empty, Input, Modal, Tag, Typography } from '@douyinfe/semi-ui';
import { IconSearch } from '@douyinfe/semi-icons';
import { useNavigate } from 'react-router-dom';
import styled from 'styled-components';

import { APP_ROUTES, type AppRouteRole } from '../../../app/navigation/route-metadata';
import { useProjectScope } from '../../../hooks/use-project-scope';
import { currentUserRole } from '../../../services/auth-session';
import { theme } from '../../../styles/theme';
import {
  buildCommandPaletteItems,
  filterCommandPaletteItems,
  type CommandPaletteItem,
} from '../model/command-palette-model';

const { Text } = Typography;

const Trigger = styled(Button)`
  min-width: 190px;
  justify-content: space-between;
  border: 1px solid ${theme.colors.border.primary};
  color: ${theme.colors.text.secondary};
  background: ${theme.colors.bg.secondary};

  @media (max-width: ${theme.breakpoints.lg}) {
    min-width: 42px;
    width: 42px;
    padding: 0;

    .command-search-label,
    .command-search-shortcut {
      display: none;
    }
  }
`;

const Results = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 12px;
  max-height: min(58vh, 520px);
  overflow-y: auto;
`;

const ResultButton = styled.button<{ $active: boolean }>`
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  width: 100%;
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid ${({ $active }) => ($active ? theme.colors.primary : 'transparent')};
  border-radius: ${theme.borderRadius.base};
  background: ${({ $active }) => ($active ? theme.colors.bg.tertiary : 'transparent')};
  color: ${theme.colors.text.primary};
  text-align: left;
  cursor: pointer;

  &:hover {
    border-color: ${theme.colors.border.primary};
    background: ${theme.colors.bg.tertiary};
  }
`;

const ResultCopy = styled.div`
  min-width: 0;

  .label {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .description {
    display: block;
    margin-top: 3px;
    overflow: hidden;
    color: ${theme.colors.text.tertiary};
    font-size: ${theme.typography.fontSize.sm};
    text-overflow: ellipsis;
    white-space: nowrap;
  }
`;

const kindLabel: Record<CommandPaletteItem['kind'], string> = {
  action: 'Action',
  route: 'Page',
  project: 'Project',
};

const roleForPalette = (): AppRouteRole => currentUserRole() === 'admin' ? 'admin' : 'user';

const withProjectContext = (path: string, projectId: string): string => {
  if (!projectId || path.includes('projectId=')) return path;
  const separator = path.includes('?') ? '&' : '?';
  return `${path}${separator}projectId=${encodeURIComponent(projectId)}`;
};

export const GlobalCommandPalette: React.FC = () => {
  const navigate = useNavigate();
  const projectScope = useProjectScope();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [activeIndex, setActiveIndex] = useState(0);
  const role = roleForPalette();

  const items = useMemo(() => buildCommandPaletteItems(
    APP_ROUTES,
    role,
    projectScope.projects.map((project) => ({ projectId: project.projectId, name: project.name })),
    projectScope.projectId,
  ), [projectScope.projectId, projectScope.projects, role]);
  const filtered = useMemo(() => filterCommandPaletteItems(items, query), [items, query]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const key = event.key.toLowerCase();
      const macShortcut = event.metaKey && !event.ctrlKey && key === 'k';
      const nonMacShortcut = event.ctrlKey && event.shiftKey && key === 'k';
      if (macShortcut || nonMacShortcut) {
        event.preventDefault();
        setOpen((current) => !current);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

  useEffect(() => {
    setActiveIndex(0);
  }, [query, open]);

  const close = () => {
    setOpen(false);
    setQuery('');
    setActiveIndex(0);
  };

  const execute = (item?: CommandPaletteItem) => {
    if (!item) return;
    if (item.kind === 'project' && item.projectId) {
      projectScope.selectProject(item.projectId);
      close();
      return;
    }
    if (item.path) {
      navigate(item.kind === 'route' ? withProjectContext(item.path, projectScope.projectId) : item.path);
      close();
    }
  };

  return (
    <>
      <Trigger
        icon={<IconSearch />}
        theme="light"
        aria-label="Open global search"
        onClick={() => setOpen(true)}
      >
        <span className="command-search-label">Search</span>
        <Text className="command-search-shortcut" type="tertiary" size="small">⌘K / Ctrl⇧K</Text>
      </Trigger>
      <Modal
        title="Search OrbisOps"
        visible={open}
        footer={null}
        width={680}
        style={{ maxWidth: 'calc(100vw - 24px)' }}
        onCancel={close}
        closeOnEsc
      >
        <Input
          autoFocus
          prefix={<IconSearch />}
          value={query}
          aria-label="Search pages, actions, and projects"
          placeholder="Search pages, actions, and projects..."
          onChange={setQuery}
          onKeyDown={(event) => {
            if (event.key === 'ArrowDown') {
              event.preventDefault();
              setActiveIndex((index) => filtered.length ? (index + 1) % filtered.length : 0);
            } else if (event.key === 'ArrowUp') {
              event.preventDefault();
              setActiveIndex((index) => filtered.length ? (index - 1 + filtered.length) % filtered.length : 0);
            } else if (event.key === 'Enter') {
              event.preventDefault();
              execute(filtered[activeIndex]);
            }
          }}
        />
        <Results role="listbox" aria-label="Search results">
          {filtered.length === 0 ? (
            <Empty title="No matching commands" description="Try a page name, project name, MCP, incident, or diagnosis." />
          ) : filtered.map((item, index) => (
            <ResultButton
              key={item.id}
              type="button"
              role="option"
              aria-selected={index === activeIndex}
              $active={index === activeIndex}
              onMouseEnter={() => setActiveIndex(index)}
              onClick={() => execute(item)}
            >
              <ResultCopy>
                <Text strong className="label">{item.label}</Text>
                <span className="description">{item.description}</span>
              </ResultCopy>
              <Tag>{kindLabel[item.kind]}</Tag>
            </ResultButton>
          ))}
        </Results>
        <Text type="tertiary" size="small" style={{ display: 'block', marginTop: 12 }}>
          ↑↓ navigate · Enter open · Esc close
        </Text>
      </Modal>
    </>
  );
};
