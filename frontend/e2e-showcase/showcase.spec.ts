import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
test('static showcase is accessible, responsive and independent of the application', async ({page}, info) => {
  const external: string[] = [];
  page.on('request', r => { if (!r.url().startsWith('http://127.0.0.1:4175/')) external.push(r.url()); });
  const response = await page.goto('/');
  await expect(page.getByRole('heading',{level:1})).toHaveText('A little direction.Every day.');
  await expect(page.getByText('Interactive app not deployed',{exact:true})).toBeVisible();
  await expect(page.getByRole('link',{name:/Launch application/})).toHaveCount(0);
  await expect(page.locator('a[href*="github.com"]')).toHaveCount(0);
  await expect(page.getByText('Source links are not enabled for this showcase release.',{exact:false})).toBeVisible();
  await expect(page.getByRole('heading',{name:'AI Coach, grounded in evidence'})).toBeVisible();
  for (const image of await page.getByRole('img').all()) {
    await image.scrollIntoViewIfNeeded(); await expect(image).toHaveJSProperty('naturalWidth',1280);
  }
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa','wcag22aa']).analyze()).violations).toEqual([]);
  const viewport = page.viewportSize();
  for (const width of [320,768,1440]) {
    await page.setViewportSize({width,height:900});
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  }
  if (viewport) await page.setViewportSize(viewport);
  expect(external).toEqual([]);
  expect(response?.headers()['content-security-policy']).toContain("connect-src 'none'");
  await page.screenshot({path:info.outputPath('showcase.png'),fullPage:true});
});
test('keyboard navigation reaches content and sections', async ({page}) => {
  await page.goto('/'); await page.keyboard.press('Tab');
  await expect(page.getByRole('link',{name:'Skip to content'})).toBeFocused();
  await page.keyboard.press('Enter');
  await page.getByRole('link',{name:'The engineering',exact:true}).click();
  await expect(page).toHaveURL(/#engineering$/);
  await expect(page.getByRole('heading',{name:'Built around data integrity.'})).toBeInViewport();
});
test('unknown routes and API paths remain explicit 404 pages', async ({request}) => {
  for (const route of ['/unknown','/api/v1/me','/.env','/app/goals']) {
    const response = await request.get(route); expect(response.status()).toBe(404);
    expect(await response.text()).toContain('That page');
  }
});

test('every public link resolves locally and restrictive headers are present', async ({page, request}) => {
  await page.goto('/');
  for (const href of await page.locator('a[href]').evaluateAll(links => links.map(link => link.getAttribute('href')!))) {
    if (href.startsWith('#')) {
      expect(await page.locator(href).count()).toBe(1);
    } else {
      expect(href.startsWith('/')).toBe(true);
      expect((await request.get(href)).status()).toBe(200);
    }
  }
  for (const route of ['/', '/styles.css', '/assets/dashboard.png', '/assets/analytics.png', '/unknown']) {
    const headers = (await request.get(route)).headers();
    expect(headers['content-security-policy']).toContain("script-src 'none'");
    expect(headers['content-security-policy']).toContain("frame-ancestors 'none'");
    expect(headers['x-content-type-options']).toBe('nosniff');
    expect(headers['referrer-policy']).toBe('no-referrer');
    expect(headers['x-frame-options']).toBe('DENY');
    expect(headers['strict-transport-security']).toContain('max-age=31536000');
  }
});
