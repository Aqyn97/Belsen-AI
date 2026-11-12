import { render, screen } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import userEvent from '@testing-library/user-event';
import { Button, Field, Status } from './components';
it('labels inputs and associates errors', () => { render(<Field label="Quantity" error="Use four decimal places"/>); expect(screen.getByRole('textbox',{name:'Quantity'})).toHaveAccessibleDescription('Use four decimal places'); expect(screen.getByRole('textbox')).toHaveAttribute('aria-invalid','true'); });
it('supports keyboard buttons and disabled controls', async () => { const click = vi.fn(); render(<><Button onClick={click}>Continue</Button><Button disabled>Unavailable</Button></>); await userEvent.tab(); await userEvent.keyboard('{Enter}'); expect(click).toHaveBeenCalledOnce(); expect(screen.getByRole('button',{name:'Unavailable'})).toBeDisabled(); });
it('announces errors as alerts', () => { render(<Status error>Request failed</Status>); expect(screen.getByRole('alert')).toHaveTextContent('Request failed'); });

it('does not submit forms by default and preserves help descriptions', () => { render(<><Button>Action</Button><span id="help">Use decimal text</span><Field label="Amount" aria-describedby="help" error="Required"/></>);expect(screen.getByRole('button')).toHaveAttribute('type','button');expect(screen.getByRole('textbox')).toHaveAccessibleDescription('Use decimal text Required'); });
