import { renderHook, waitFor } from '@testing-library/react';
import { goWithNotice, useArrivalNotice } from './navigation';

describe('goWithNotice', () => {
  it('opens the page and has it show the notice once', async () => {
    goWithNotice('#/skills', 'Acme style was deleted.');
    expect(window.location.hash).toBe('#/skills');

    const first = renderHook(() => useArrivalNotice());
    await waitFor(() => expect(first.result.current).toBe('Acme style was deleted.'));
    first.unmount();

    const again = renderHook(() => useArrivalNotice());
    expect(again.result.current).toBeNull();
  });
});
