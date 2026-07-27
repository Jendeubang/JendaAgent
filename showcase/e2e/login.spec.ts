import { expect, test } from "@playwright/test";

const captcha = { captchaId: "e2e-captcha", imageDataUrl: "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='120' height='40'%3E%3C/svg%3E" };

test("login creates a browser session and opens the agent workspace", async ({ page }) => {
  await page.route("**/api/v1/auth/captcha", async (route) => route.fulfill({
    contentType: "application/json", body: JSON.stringify(captcha),
  }));
  await page.route("**/api/v1/auth/login", async (route) => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify({ accessToken: "e2e-access-token", tokenType: "Bearer", expiresInSeconds: 900, refreshExpiresInSeconds: 2_592_000, userId: "e2e-user", username: "e2e-user" }),
  }));

  await page.goto("/login");
  await page.locator("input").nth(0).fill("e2e-user");
  await page.locator("input").nth(1).fill("test-password");
  await page.locator("input").nth(2).fill("ABCD");
  await page.locator("button[type='submit']").click();

  await expect(page.locator(".ant-message")).toContainText("登录成功");
  await expect.poll(async () => page.evaluate(() => localStorage.getItem("jenda-agent-auth-session"))).toContain("e2e-user");

  await page.goto("/zh/agent");
  await expect(page).toHaveURL(/\/zh\/agent$/);
});