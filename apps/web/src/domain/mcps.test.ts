import { describe, expect, it } from 'vitest';
import { addressProblem, allowedTools, hostOf, pickAllowed, safeIcon, serverUsers, type McpServer, type McpTool } from './mcps';

const tool = (name: string, readOnly: boolean, allowed: boolean, users: string[] = []): McpTool => ({
  name, title: name, description: '', readOnly, allowed,
  usedBy: users.map((agentName) => ({ agentId: agentName, agentName, where: 'DRAFT', archived: false })),
});

const server: McpServer = {
  id: 's1', name: 'Acme Docs', address: 'https://mcp.acme.test/mcp', auth: 'NONE', icon: null, status: 'WORKS',
  tools: [tool('search', true, true, ['Zed', 'Analyst']), tool('get', true, false), tool('create', false, true, ['Zed'])],
  addedAt: '', checkedAt: '',
};

describe('MCP servers', () => {
  it('checks an address before asking the server', () => {
    expect(addressProblem('  ')).toBe('empty');
    expect(addressProblem('mcp.acme.test')).toBe('invalid');
    expect(addressProblem('ftp://mcp.acme.test')).toBe('invalid');
    expect(addressProblem('https://mcp.acme.test/mcp')).toBeNull();
    expect(hostOf('https://mcp.acme.test/mcp')).toBe('mcp.acme.test');
    expect(hostOf('not a url')).toBe('not a url');
  });

  it('shows a logo only when it is an inline image', () => {
    expect(safeIcon('data:image/png;base64,iVBORw0K')).toBe('data:image/png;base64,iVBORw0K');
    expect(safeIcon('https://evil.test/logo.png')).toBeNull();
    expect(safeIcon('data:text/html;base64,PHNjcmlwdD4=')).toBeNull();
    expect(safeIcon(null)).toBeNull();
  });

  it('counts only read tools that are allowed, and names each agent once', () => {
    expect(allowedTools(server).map((each) => each.name)).toEqual(['search']);
    expect(serverUsers(server)).toEqual(['Analyst', 'Zed']);
    expect(pickAllowed([server], { serverId: 's1', tool: 'search' })).toBe(true);
    expect(pickAllowed([server], { serverId: 's1', tool: 'get' })).toBe(false);
    expect(pickAllowed([server], { serverId: 'gone', tool: 'search' })).toBe(false);
  });
});
