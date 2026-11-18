import { render, screen } from '@testing-library/react';
import { Stepper } from './Stepper';

describe('Stepper', () => {
  it('marks finished steps as completed and the current one as the step', () => {
    render(<Stepper steps={['Name', 'Files', 'Review']} current={1} />);

    expect(screen.getByText('Name').closest('li')).toHaveTextContent('(completed)');
    expect(screen.getByText('Files').closest('li')).toHaveAttribute('aria-current', 'step');
    expect(screen.getByRole('list', { name: 'Step 2 of 3' })).toBeInTheDocument();
  });
});
