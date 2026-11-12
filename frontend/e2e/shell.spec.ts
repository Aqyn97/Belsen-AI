import { test, expect } from '@playwright/test';
test('public shell is honest, keyboard-accessible and fits the viewport', async ({ page }, info) => {
  await page.goto('/'); await expect(page.getByRole('heading',{level:1})).toContainText('Make room');
  await page.screenshot({ path: info.outputPath('public-shell.png'), fullPage: true });
  await page.keyboard.press('Tab'); await expect(page.getByRole('link',{name:'Skip to content'})).toBeFocused();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
});
test('anonymous routes stay gated against mocked API', async ({ page }) => {
  await page.route('**/api/v1/me', route => route.fulfill({ status:401, body:'{}', contentType:'application/problem+json' }));
  await page.goto('/app/dashboard'); await expect(page.getByRole('heading',{name:'Your next chapter'})).toBeVisible();
});
test('authenticated shell navigation uses mocked session without fake product data', async ({ page }) => {
  await page.route('**/api/v1/me', route => route.fulfill({ body:JSON.stringify({id:'test',email:'test@example.test',displayName:'Fixture',timezone:'UTC'}), contentType:'application/json' }));
  await page.goto('/app/dashboard'); await expect(page.getByRole('navigation',{name:'Workspace'})).toBeVisible();
  await page.getByRole('link',{name:'Habits',exact:true}).click(); await expect(page.getByRole('heading',{name:'Habits',exact:true})).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
});

test('public page fits common narrow and tablet widths', async ({ page }) => {
  for (const width of [320, 375, 768]) {
    await page.setViewportSize({ width, height: 900 });await page.goto('/');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  }
});
