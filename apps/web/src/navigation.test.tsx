import { act, renderHook, waitFor } from '@testing-library/react';
import { NOTICE_MS } from './components/useNotice';
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

describe('useArrivalNotice', () => {
  it('clears the notice after a while, so it does not stay on the page', () => {
    vi.useFakeTimers();
    try {
      goWithNotice('#/agents', 'Classifier was deleted.');
      const { result } = renderHook(() => useArrivalNotice());
      expect(result.current).toBe('Classifier was deleted.');

      act(() => { vi.advanceTimersByTime(NOTICE_MS); });

      expect(result.current).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });
});
