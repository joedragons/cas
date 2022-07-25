const puppeteer = require('puppeteer');
const path = require('path');
const cas = require('../../cas.js');

(async () => {
    const browser = await puppeteer.launch(cas.browserOptions());
    const page = await cas.newPage(browser);

    await cas.goto(page, "https://localhost:8443/cas/login");
    await page.waitForTimeout(1000);
    await cas.loginWith(page, "casuser", "Mellon");
    
    await cas.goto(page, "http://localhost:9443/simplesaml/module.php/core/authenticate.php?as=default-sp");
    await page.waitForTimeout(2000);
    await cas.removeDirectory(path.join(__dirname, '/saml-md'));
    await cas.assertVisibility(page, '#username');
    await cas.assertVisibility(page, '#password');

    await browser.close();
})();


