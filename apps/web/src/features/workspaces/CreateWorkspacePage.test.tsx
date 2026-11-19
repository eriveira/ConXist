import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { accessibilityViolations } from '../../a11y';
import { stubApi } from '../../testing';
import { CreateWorkspacePage } from './CreateWorkspacePage';

const CREATED = { id: 'w1', name: 'Acme Product', description: '', sourceCount: 0, createdAt: '2026-11-19T12:00:00Z' };

describe('CreateWorkspacePage', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('asks for a name before creating, and keeps focus on the name', async () => {
    const calls = stubApi({});
    const { container } = render(<CreateWorkspacePage onCreated={vi.fn()} />);

    await userEvent.click(screen.getByRole('button', { name: 'Create' }));

    const name = screen.getByLabelText('Workspace name');
    expect(name).toHaveAccessibleDescription(/Give your workspace a name to continue\./);
    expect(name).toHaveAttribute('aria-invalid', 'true');
    expect(name).toHaveFocus();
    expect(calls).toHaveLength(0);
    expect(await accessibilityViolations(container)).toEqual([]);
  });

  it('counts the description as the person types, up to 500 characters', async () => {
    stubApi({});
    render(<CreateWorkspacePage onCreated={vi.fn()} />);
    const description = screen.getByLabelText(/Description/);

    await userEvent.type(description, 'Specs and meetings for Acme.');
    expect(screen.getByText('28 / 500')).toBeInTheDocument();

    fireEvent.change(description, { target: { value: 'x'.repeat(520) } });
    expect(description).toHaveValue('x'.repeat(500));
    expect(screen.getByText('500 / 500')).toHaveClass('counter', 'full');
  });

  it('creates the workspace with the trimmed name and description', async () => {
    const calls = stubApi({ 'POST /api/workspaces': () => ({ status: 201, body: CREATED }) });
    const onCreated = vi.fn();
    render(<CreateWorkspacePage onCreated={onCreated} />);

    await userEvent.type(screen.getByLabelText('Workspace name'), '  Acme Product ');
    await userEvent.type(screen.getByLabelText(/Description/), 'Specs ');
    await userEvent.click(screen.getByRole('button', { name: 'Create' }));

    expect(JSON.parse(String(calls[0].init?.body))).toEqual({ name: 'Acme Product', description: 'Specs' });
    expect(onCreated).toHaveBeenCalledWith(CREATED);
  });

  it('says so under the name when the name is taken', async () => {
    stubApi({ 'POST /api/workspaces': () => ({ status: 422, body: { code: 'workspace.name-taken', detail: 'taken' } }) });
    render(<CreateWorkspacePage onCreated={vi.fn()} />);

    await userEvent.type(screen.getByLabelText('Workspace name'), 'Acme');
    await userEvent.click(screen.getByRole('button', { name: 'Create' }));

    expect(await screen.findByText('A workspace with this name already exists. Choose another name.')).toBeInTheDocument();
    expect(screen.getByLabelText('Workspace name')).toHaveFocus();
  });
});
