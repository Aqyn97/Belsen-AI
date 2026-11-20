import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { reportFixture } from '../src/test/analyticsFixtures';
import { coachFixture } from '../src/test/coachFixtures';
import { stringifyExact } from '../src/shared/lib/numbers';
test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/me', route => route.fulfill({ json: { id: 'owner', email: 'test@example.test', displayName: 'Owner', timezone: 'UTC' } }));
  await page.route('**/api/v1/auth/csrf', route => route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'coach-fixture' } }));
  await page.route('**/api/v1/coach/status', route => route.fulfill({ json: { schemaVersion: 1, status: 'AVAILABLE', noticeVersion: 1, goalLimit: 20 } }));
  await page.route('**/api/v1/analytics/dashboard?*', route => route.fulfill({ contentType: 'application/json', body: stringifyExact(reportFixture()) }));
});
test('explicit analysis, exact evidence, replay, keyboard and accessible responsive cards', async ({ page }, info) => {
  const bodies: Record<string, string>[] = [];
  await page.route('**/api/v1/coach/analyses', async route => { const body = route.request().postDataJSON();bodies.push(body);expect(route.request().headers()['x-csrf-token']).toBe('coach-fixture');await route.fulfill({ json: coachFixture(body.submissionId) }); });
  await page.goto('/app/coach');await expect(page.getByText('Verified progress preview')).toBeVisible();expect(bodies).toHaveLength(0);
  await page.getByRole('checkbox').focus();await page.keyboard.press('Space');await page.getByRole('button', { name: 'Analyze my progress' }).click();
  await expect(page.getByText('Your focus, grounded in progress')).toBeVisible();await expect(page.getByRole('heading', { name: 'Review task commitments' })).toBeVisible();
  await page.getByRole('button', { name: 'Recover or replay same analysis' }).click();await expect.poll(() => bodies.length).toBe(2);expect(bodies[1]).toEqual(bodies[0]);
  expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([]);
  await page.screenshot({ path: info.outputPath('coach-results.png'), fullPage: true });
  for (const width of [320, 375, 768]) { await page.setViewportSize({ width, height: 900 });expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true); }
});
test('provider disabled keeps verified progress accessible and never dispatches', async ({ page }, info) => {
  let calls = 0;await page.route('**/api/v1/coach/analyses', route => { calls++;return route.fulfill({ status: 503, json: {} }); });
  await page.route('**/api/v1/coach/status', route => route.fulfill({ json: { schemaVersion: 1, status: 'NOT_CONFIGURED', noticeVersion: 1, goalLimit: 20 } }));
  await page.goto('/app/coach');await expect(page.getByText('Verified progress preview')).toBeVisible();await expect(page.getByText(/AI Coach is not configured/)).toBeVisible();await expect(page.getByRole('button', { name: 'Analyze my progress' })).toBeDisabled();expect(calls).toBe(0);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);await page.screenshot({ path: info.outputPath('coach-disabled.png'), fullPage: true });
});
test('uncertain outcome exposes safe same-key recovery without a retry loop', async ({ page }) => {
  const keys: string[] = [];await page.route('**/api/v1/coach/analyses', route => { keys.push(route.request().postDataJSON().submissionId);return route.fulfill({ status: 504, json: {} }); });
  await page.goto('/app/coach');await expect(page.getByText('Verified progress preview')).toBeVisible();await page.getByRole('checkbox').check();await page.getByRole('button', { name: 'Analyze my progress' }).click();await expect(page.getByRole('alert')).toContainText('timed out');expect(keys).toHaveLength(1);
  await page.getByRole('button', { name: 'Recover or replay same analysis' }).click();await expect.poll(() => keys.length).toBe(2);expect(keys[1]).toBe(keys[0]);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
});
