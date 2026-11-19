import { characterCount, initialOf, limitCharacters, pickWorkspace, type Workspace } from './workspaces';

const workspace = (id: string): Workspace => ({ id, name: id, description: '', sourceCount: 0, createdAt: '2026-11-19T12:00:00Z' });

describe('workspaces', () => {
  it('counts and limits characters the way a person sees them', () => {
    expect(characterCount('Acme 😀')).toBe(6);
    expect(limitCharacters('😀😀😀', 2)).toBe('😀😀');
    expect(limitCharacters('short', 500)).toBe('short');
  });

  it('shows the first letter of the name on the badge', () => {
    expect(initialOf('  acme product')).toBe('A');
    expect(initialOf('')).toBe('?');
  });

  it('opens the workspace used last, or the oldest when that one is gone', () => {
    const list = [workspace('a'), workspace('b')];
    expect(pickWorkspace(list, 'b')?.id).toBe('b');
    expect(pickWorkspace(list, 'gone')?.id).toBe('a');
    expect(pickWorkspace([], null)).toBeNull();
  });
});
