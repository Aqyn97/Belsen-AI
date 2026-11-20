import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { randomUUID } from 'node:crypto';
// Real HTTPS, Java services and PostgreSQL. Only ranking is a test-class bean, never Groq.
test('explicit fixture ranking, persisted replay, CSRF and account isolation', async ({ page, browser }, info) => {
  let posts = 0;page.on('request', r => { if (r.method() === 'POST' && r.url().endsWith('/coach/analyses')) posts++; });
  await page.goto('/auth');await page.getByRole('button', {name:'New here? Create an account'}).click();
  await page.getByLabel('Email', {exact:true}).fill(`coach-real-${randomUUID()}@example.test`);
  await page.getByLabel('Display name', {exact:true}).fill('Fixture Owner');
  await page.getByLabel('Password', {exact:true}).fill('deterministic browser password');
  await page.getByLabel('Reporting timezone', {exact:true}).fill('UTC');
  await page.getByRole('checkbox').check();await page.getByRole('button', {name:'Create account',exact:true}).click();
  await expect(page).toHaveURL('/app/dashboard');
  const csrf = await (await page.request.get('/api/v1/auth/csrf')).json();
  const goal = await page.request.post('/api/v1/goals', {headers:{[csrf.headerName]:csrf.token},data:{title:'Private fixture goal',target:10,unit:'pages'}});expect(goal.status()).toBe(201);
  await page.goto('/app/coach');await expect(page.getByText('Verified progress preview')).toBeVisible();expect(posts).toBe(0);
  await page.getByRole('checkbox').focus();await page.keyboard.press('Space');
  const responsePromise = page.waitForResponse(r => r.url().endsWith('/coach/analyses') && r.request().method() === 'POST');
  await page.getByRole('button', {name:'Analyze my progress'}).click();const response = await responsePromise;expect(response.status()).toBe(200);const result = await response.json();
  expect(result.source).toBe('AI_PRIORITIZED');expect(result.selectedIds).toEqual(['c1']);
  await expect(page.getByRole('heading', {name:'Make goal progress measurable',exact:true})).toBeVisible();
  expect(JSON.stringify(result)).not.toContain('Private fixture goal');
  const replayPromise = page.waitForResponse(r => r.url().endsWith('/coach/analyses') && r.request().method() === 'POST');
  await page.getByRole('button', {name:'Recover or replay same analysis'}).click();expect(await (await replayPromise).json()).toEqual(result);expect(posts).toBe(2);
  const input = response.request().postDataJSON();
  expect((await page.request.post('/api/v1/coach/analyses',{data:input})).status()).toBe(403);
  const other = await browser.newContext({baseURL:'https://localhost:18443',ignoreHTTPSErrors:true});
  try {expect((await other.request.post('/api/v1/coach/analyses',{data:input})).status()).toBe(403);expect((await other.request.get('/api/v1/coach/status')).status()).toBe(401);} finally {await other.close();}
  expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa']).analyze()).violations).toEqual([]);
  await page.screenshot({path:info.outputPath('coach-deterministic-fixture.png'),fullPage:true});
  for (const width of [320,375,768]) {await page.setViewportSize({width,height:900});expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);}
});

async function fixtureAccount(page: import('@playwright/test').Page, goalCount: number) {
  const c = await (await page.request.get('/api/v1/auth/csrf')).json();
  expect((await page.request.post('/api/v1/auth/register', {headers:{[c.headerName]:c.token},data:{email:`fixture-${randomUUID()}@example.test`,password:'deterministic browser password',displayName:'Fixture Owner',timezone:'UTC'}})).status()).toBe(201);
  const token = await (await page.request.get('/api/v1/auth/csrf')).json();
  for (let i=0;i<goalCount;i++) expect((await page.request.post('/api/v1/goals', {headers:{[token.headerName]:token.token},data:{title:`Fixture ${i}`,target:10,unit:'pages'}})).status()).toBe(201);
  await page.goto('/app/coach');await expect(page.getByText('Verified progress preview')).toBeVisible();
  await page.getByRole('checkbox').check();
}
test('empty real account receives a rules-only result without fabricated inference', async ({ page }) => {
  await fixtureAccount(page,0);await page.getByRole('button',{name:'Analyze my progress'}).click();
  await expect(page.getByText(/This is a rules-only result/)).toBeVisible();
  await expect(page.getByRole('heading',{name:'No eligible recommendations in this period'})).toBeVisible();
  expect((await new AxeBuilder({page}).analyze()).violations).toEqual([]);
});
test('real uncertain ledger replays safely without another dispatch or fresh-key escape', async ({ page }) => {
  await fixtureAccount(page,2);const response = page.waitForResponse(r=>r.url().endsWith('/coach/analyses')&&r.request().method()==='POST');
  await page.getByRole('button',{name:'Analyze my progress'}).click();expect((await response).status()).toBe(504);
  await expect(page.getByRole('alert')).toContainText('timed out');
  await expect(page.getByRole('button',{name:'Analyze my progress'})).toBeDisabled();
  await expect(page.getByRole('button',{name:'Prepare a new analysis'})).toHaveCount(0);
  const recovery = page.waitForResponse(r=>r.url().endsWith('/coach/analyses')&&r.request().method()==='POST');
  await page.getByRole('button',{name:'Recover or replay same analysis'}).click();expect((await recovery).status()).toBe(409);
  await expect(page.getByRole('alert')).toContainText('uncertain provider outcome');
  expect((await new AxeBuilder({page}).analyze()).violations).toEqual([]);
});
