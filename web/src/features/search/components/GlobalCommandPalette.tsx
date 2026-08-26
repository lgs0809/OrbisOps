import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { Button, Empty, Input, Modal, Tag, Typography } from '@douyinfe/semi-ui';
import { IconSearch } from '@douyinfe/semi-icons';
import { useNavigate } from 'react-router-dom';
import styled from 'styled-components';

import { APP_ROUTES, type AppRouteRole } from '../../../app/navigation/route-metadata';
import { useProjectScope } from '../../../hooks/use-project-scope';
import { currentUserRole, isAuthenticated } from '../../../services/auth-session';
import { theme } from '../../../styles/theme';
import { writeProjectContextId } from '../../../utils/project-context';
import {
  buildCommandPaletteItems,
  commandPaletteDestination,
  filterCommandPaletteItems,
  type CommandPaletteItem,
} from '../model/command-palette-model';

const { Text } = Typography;

const Trigger = styled(Button)<{ $compact?: boolean }>`
  min-width: ${(props) => (props.$compact ? '64px' : '190px')};
  width: ${(props) => (props.$compact ? '64px' : 'auto')};
  height: ${(props) => (props.$compact ? '48px' : 'auto')};
  padding: ${(props) => (props.$compact ? '4px 2px' : undefined)};
  flex-direction: ${(props) => (props.$compact ? 'column' : 'row')};
  gap: ${(props) => (props.$compact ? '3px' : '8px')};
  justify-content: ${(props) => (props.$compact ? 'center' : 'space-between')};
  border: 1px solid ${theme.colors.border.primary};
  color: ${theme.colors.text.secondary};
  background: ${(props) => (props.$compact ? 'transparent' : theme.colors.bg.secondary)};
  border-radius: ${(props) => (props.$compact ? '11px' : undefined)};

  .command-search-label {
    display: ${(props) => (props.$compact ? 'block' : 'inline')};
    font-size: ${(props) => (props.$compact ? '10px' : undefined)};
    line-height: ${(props) => (props.$compact ? '1' : undefined)};
  }

  .command-search-shortcut {
    display: ${(props) => (props.$compact ? 'none' : 'inline')};
  }

  &:hover {
    background: ${(props) => (props.$compact ? '#ecefed' : theme.colors.bg.tertiary)};
  }

  @media (max-width: ${theme.breakpoints.lg}) {
    min-width: ${(props) => (props.$compact ? '64px' : '38px')};
    width: ${(props) => (props.$compact ? '64px' : '38px')};
    padding: ${(props) => (props.$compact ? '4px 2px' : '0')};

    .command-search-shortcut {
      display: none;
    }
  }

  @media (max-width: ${theme.breakpoints.md}) {
    min-width: ${(props) => (props.$compact ? '0' : '38px')};
    width: ${(props) => (props.$compact ? '100%' : '38px')};
    height: ${(props) => (props.$compact ? '40px' : '38px')};
    flex-direction: row;
    gap: ${(props) => (props.$compact ? '10px' : '8px')};
    justify-content: ${(props) => (props.$compact ? 'flex-start' : 'center')};
    padding: ${(props) => (props.$compact ? '0 11px' : '0')};

    .command-search-label {
      font-size: ${(props) => (props.$compact ? '12px' : undefined)};
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
  action: '操作',
  route: '页面',
  project: 'Project',
};

const roleForPalette = (): AppRouteRole => currentUserRole() === 'admin' ? 'admin' : 'user';

const CommandPaletteContext = createContext<(() => void) | null>(null);

// The controller belongs above the routed page's Suspense boundary. A trigger
// clicked during lazy navigation must not disappear with the outgoing Sidebar.
export const GlobalCommandPaletteProvider: React.FC<React.PropsWithChildren> = ({ children }) => {
  const navigate = useNavigate();
  const projectScope = useProjectScope();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [activeIndex, setActiveIndex] = useState(0);
  const role = roleForPalette();
  const authenticated = isAuthenticated();

  const items = useMemo(() => buildCommandPaletteItems(
    APP_ROUTES,
    role,
    projectScope.projects.map((project) => ({ projectId: project.projectId, name: project.name })),
    projectScope.projectId,
  ), [projectScope.projectId, projectScope.projects, role]);
  const filtered = useMemo(() => filterCommandPaletteItems(items, query), [items, query]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (!authenticated) return;
      // KeyboardEvent.key is normally a string, but browser automation and
      // IME composition can emit a partially populated event. Normalize at
      // this boundary so the global command palette never crashes the page
      // while handling an otherwise unrelated key event.
      const key = String(event.key || '').toLowerCase();
      const macShortcut = event.metaKey && !event.ctrlKey && key === 'k';
      const nonMacShortcut = event.ctrlKey && event.shiftKey && key === 'k';
      if (macShortcut || nonMacShortcut) {
        event.preventDefault();
        setOpen((current) => !current);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [authenticated]);

  useEffect(() => {
    setActiveIndex(0);
  }, [query, open]);

  const close = () => {
    setOpen(false);
    setQuery('');
    setActiveIndex(0);
  };

  const execute = (item?: CommandPaletteItem) => {
    // BrowserRouter commits history before all React/Modal callbacks settle.
    // Rebind every command to one activation-time URL plus the authorized catalog.
    const target = commandPaletteDestination(item, window.location, projectScope.projectId, projectScope.projects);
    if (!item || !target) return;
    if (item.kind === 'project') writeProjectContextId(item.projectId);
    navigate(target, { replace: item.kind === 'project' });
    close();
  };

  return (
    <CommandPaletteContext.Provider value={() => { if (authenticated) setOpen(true); }}>
      {children}
      <Modal
        title="快速查找 OrbisOps"
        visible={authenticated && open}
        // Dispatching navigation must remove the old mask immediately so the
        // next page's search trigger cannot be intercepted by its exit animation.
        motion={false}
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
          aria-label="搜索页面、操作和 Project"
          placeholder="搜索页面、操作或 Project"
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
        <Results role="listbox" aria-label="搜索结果">
          {filtered.length === 0 ? (
            <Empty title="没有匹配结果" description="可以尝试页面名称、Project 名称、MCP、事件或排障关键词。" />
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
        <Text type="tertiary" size="small" style={{ display: 'block', marginTop: 10 }}>
          用来跳转页面、切换 Project 和打开常用操作；不会搜索历史对话。
        </Text>
        <Text type="tertiary" size="small" style={{ display: 'block', marginTop: 12 }}>
          ↑↓ 选择 · Enter 打开 · Esc 关闭
        </Text>
      </Modal>
    </CommandPaletteContext.Provider>
  );
};

export const GlobalCommandPalette: React.FC<{ compact?: boolean }> = ({ compact = false }) => {
  const open = useContext(CommandPaletteContext);
  if (!open) throw new Error('GlobalCommandPalette must be used inside GlobalCommandPaletteProvider');
  return (
    <Trigger
      $compact={compact}
      icon={<IconSearch />}
      theme="light"
      aria-label="打开全局搜索"
      aria-keyshortcuts="Meta+K Control+Shift+K"
      title="快速查找（⌘K / Ctrl⇧K）"
      onClick={open}
    >
      <span className="command-search-label">快速查找</span>
      <Text className="command-search-shortcut" type="tertiary" size="small">⌘K / Ctrl⇧K</Text>
    </Trigger>
  );
};
