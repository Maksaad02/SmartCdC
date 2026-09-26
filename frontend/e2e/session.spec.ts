import { expect, test } from "@playwright/test";
import { login } from "./helpers";

test.describe("Session", () => {
  test("la session survit au rafraichissement grace au cookie httpOnly, sans jeton dans le navigateur", async ({ page }) => {
    await login(page);

    // Aucun jeton lisible par JavaScript : ni localStorage ni sessionStorage.
    const stored = await page.evaluate(() => ({
      local: Object.keys(localStorage),
      session: Object.keys(sessionStorage),
      cookies: document.cookie,
    }));
    expect(stored.local).not.toContain("auth_token");
    expect(stored.session).toEqual([]);
    expect(stored.cookies).not.toContain("smartcdc_refresh"); // httpOnly : invisible depuis le script

    // Le cookie de renouvellement existe pourtant (visible par le navigateur, pas par la page).
    const cookies = await page.context().cookies();
    const refresh = cookies.find((c) => c.name === "smartcdc_refresh");
    expect(refresh?.httpOnly).toBe(true);
    expect(refresh?.sameSite).toBe("Strict");
    expect(refresh?.path).toBe("/api/auth");

    // F5 : le jeton d'acces (en memoire) est perdu, la session est reprise par le cookie.
    await page.reload();
    await expect(page.getByRole("heading", { name: "Tableau de bord" })).toBeVisible();
  });

  test("un lien direct et F5 sur une page interne fonctionnent (repli SPA de nginx)", async ({ page }) => {
    await login(page);

    await page.goto("/clients");
    await expect(page.getByRole("heading", { name: "Gestion des clients" })).toBeVisible();
    await page.reload();
    await expect(page.getByRole("heading", { name: "Gestion des clients" })).toBeVisible();
  });

  test("apres deconnexion, le cookie ne rouvre pas la session", async ({ page }) => {
    await login(page);

    await page.getByRole("button", { name: "Se déconnecter" }).click();
    await expect(page).toHaveURL(/\/login/);

    await page.reload();
    await expect(page).toHaveURL(/\/login/);
    await page.goto("/dashboard");
    await expect(page).toHaveURL(/\/login/);
  });

  test("un mauvais mot de passe est refuse sans ouvrir de session", async ({ page }) => {
    await page.goto("/login");
    await page.getByPlaceholder("Email").fill("inconnu@smartcdc.test");
    await page.getByPlaceholder("Mot de passe").fill("mauvais-mot-de-passe");
    await page.getByRole("button", { name: "Se connecter" }).click();

    await expect(page.getByText("Identifiants incorrects")).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
  });
});
