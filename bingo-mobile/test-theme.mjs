import puppeteer from 'puppeteer-core';
const browser = await puppeteer.launch({ headless: 'new', executablePath: '/usr/bin/google-chrome', args: ['--no-sandbox'] });
const page = await browser.newPage();
await page.goto('http://localhost:8082', { waitUntil: 'networkidle0', timeout: 60000 });
await new Promise(r => setTimeout(r, 3000));
const before = await page.evaluate(() => {
  const cs = getComputedStyle(document.documentElement);
  return { htmlDark: document.documentElement.classList.contains('dark'), bg: cs.getPropertyValue('--bp-background').trim() };
});
console.log('BEFORE:', JSON.stringify(before));
const clicked = await page.evaluate(() => {
  const all = Array.from(document.querySelectorAll('button'));
  for (const b of all) {
    if (b.querySelector('svg')) { b.click(); return 'clicked'; }
  }
  return 'no-svg-button';
});
await new Promise(r => setTimeout(r, 2000));
const after = await page.evaluate(() => {
  const cs = getComputedStyle(document.documentElement);
  return { htmlDark: document.documentElement.classList.contains('dark'), bg: cs.getPropertyValue('--bp-background').trim() };
});
console.log('CLICKED:', clicked, 'AFTER:', JSON.stringify(after));
await browser.close();