import { test, expect, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';
// Every API request in this suite reaches Spring Boot and disposable PostgreSQL.
const password = 'correct horse battery staple';
async function register(page: Page, email: string, name = 'Browser Owner') {
  await page.goto('/auth'); await page.getByRole('button',{name:'New here? Create an account'}).click();
  await page.getByLabel('Email',{exact:true}).fill(email); await page.getByLabel('Display name',{exact:true}).fill(name);
  await page.getByLabel('Password',{exact:true}).fill(password); await page.getByLabel('Reporting timezone',{exact:true}).fill('UTC'); await page.getByRole('checkbox').check();
  await page.getByRole('button',{name:'Create account',exact:true}).click(); await expect(page).toHaveURL('/app/dashboard');
}
test('registration, rotated session/CSRF, settings, deep-link reload, logout and login use PostgreSQL sessions', async ({page,context}, info) => {
  const email = `browser-${randomUUID()}@example.test`;
  await page.goto('/auth'); await expect(page.getByRole('heading',{name:'Welcome back.'})).toBeVisible();
  const anonymous = (await context.cookies()).find(cookie => cookie.name === 'BELSEN_SESSION'); expect(anonymous).toBeDefined();
  const csrfBefore = await (await page.request.get('/api/v1/auth/csrf')).json();
  await register(page,email);
  const session = (await context.cookies()).find(cookie => cookie.name === 'BELSEN_SESSION');
  expect(session?.value).not.toBe(anonymous?.value); expect(session?.httpOnly).toBe(true); expect(session?.sameSite).toBe('Lax'); expect(session?.secure).toBe(false);
  const stale = await page.request.patch('/api/v1/me',{data:{displayName:'Must not save'},headers:{[csrfBefore.headerName]:csrfBefore.token}}); expect(stale.status()).toBe(403);
  // The SPA's independently refreshed token must work after session rotation.
  await page.getByRole('link',{name:'Settings',exact:true}).click(); await expect(page.getByLabel('Email',{exact:true})).toHaveValue(email);
  await page.getByLabel('Display name',{exact:true}).fill('Updated Browser Owner'); await page.getByLabel('Reporting timezone',{exact:true}).fill('Asia/Almaty');
  await page.getByRole('button',{name:'Save settings'}).click(); await expect(page.getByRole('status')).toContainText('Settings saved');
  await page.goto('/app/settings'); await expect(page.getByLabel('Display name',{exact:true})).toHaveValue('Updated Browser Owner'); await expect(page.getByLabel('Reporting timezone',{exact:true})).toHaveValue('Asia/Almaty');
  await page.screenshot({path:info.outputPath('real-settings.png'),fullPage:true});
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.getByRole('button',{name:'Sign out',exact:true}).click(); await expect(page).toHaveURL('/auth'); expect((await page.request.get('/api/v1/me')).status()).toBe(401);
  await page.goto('/app/tasks'); await expect(page).toHaveURL('/auth'); await page.getByLabel('Email',{exact:true}).fill(email); await page.getByLabel('Password',{exact:true}).fill(password);
  await page.getByRole('button',{name:'Sign in',exact:true}).click(); await expect(page).toHaveURL('/app/tasks'); await page.reload(); await expect(page.getByRole('heading',{name:'Tasks',exact:true})).toBeVisible();
});
test('invalid credentials, client validation, keyboard and mobile form layout are safe', async ({page},info) => {
  await page.goto('/auth'); await expect(page.getByRole('heading',{name:'Welcome back.'})).toBeVisible();
  await page.getByLabel('Email',{exact:true}).fill(`missing-${randomUUID()}@example.test`); await page.getByLabel('Password',{exact:true}).fill(password); await page.getByRole('button',{name:'Sign in',exact:true}).click(); await expect(page.getByRole('alert')).toContainText('Check your credentials');
  await page.getByRole('button',{name:'New here? Create an account'}).click(); await page.getByLabel('Display name',{exact:true}).fill('Invalid'); await page.getByLabel('Password',{exact:true}).fill('😀'.repeat(19)); await page.getByRole('checkbox').check();
  await page.getByRole('button',{name:'Create account',exact:true}).click(); await expect(page.getByText(/Use at most 72/)).toBeVisible();
  await page.getByLabel('Password',{exact:true}).focus(); await page.keyboard.press('Tab'); await expect(page.getByRole('button',{name:'Show password'})).toBeFocused(); await page.keyboard.press('Enter'); await expect(page.getByLabel('Password',{exact:true})).toHaveAttribute('type','text');
  await expect(page.getByRole('link',{name:'Continue with Google'})).toHaveCount(0);
  for (const width of [320,375,768]) { await page.setViewportSize({width,height:900}); expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true); }
  await page.screenshot({path:info.outputPath('real-auth-validation.png'),fullPage:true});
});
test('two accounts in one browser never share settings and expired sessions stay gated', async ({page,context}) => {
  const first = `first-${randomUUID()}@example.test`; const second = `second-${randomUUID()}@example.test`;
  await register(page,first,'First Owner'); await page.getByRole('link',{name:'Settings',exact:true}).click(); await expect(page.getByLabel('Email',{exact:true})).toHaveValue(first); await page.getByRole('button',{name:'Sign out',exact:true}).click(); await expect(page).toHaveURL('/auth'); expect((await page.request.get('/api/v1/me')).status()).toBe(401);
  await register(page,second,'Second Owner'); await page.getByRole('link',{name:'Settings',exact:true}).click(); await expect(page.getByLabel('Email',{exact:true})).toHaveValue(second); await expect(page.getByLabel('Display name',{exact:true})).toHaveValue('Second Owner');
  await context.clearCookies(); await page.reload(); await expect(page).toHaveURL('/auth'); await expect(page.getByLabel('Email',{exact:true})).toBeEmpty(); await expect(page.getByText('Second Owner',{exact:true})).toHaveCount(0);
});
