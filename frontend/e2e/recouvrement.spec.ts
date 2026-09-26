import { expect, test } from "@playwright/test";
import { apiDepartement, apiToken, apiUtilisateur, login, unique } from "./helpers";

test.describe("Parcours de recouvrement", () => {
  test("creer un client, une creance, enregistrer un reglement", async ({ page }) => {
    const id = unique();
    const client = `Client E2E ${id}`;
    const facture = `E2E-${id}`;
    await login(page);

    // 1. Client
    await page.goto("/clients/new");
    await page.getByLabel(/Nom \/ Raison sociale/).fill(client);
    await page.getByLabel(/^Email/).fill(`contact.${id}@e2e.test`);
    await page.getByLabel(/^Téléphone/).fill(`06${Math.floor(Math.random() * 1e8).toString().padStart(8, "0")}`);
    await page.getByLabel(/^Adresse/).fill("1 rue des Tests, Casablanca");
    // Un ADMIN choisit le departement du client (obligatoire) ; il est herite ensuite par creances et reglements.
    await page.getByRole("combobox", { name: "Département" }).click();
    await page.getByRole("option", { name: "Siège" }).click();
    await page.getByRole("button", { name: /Ajouter|Créer|Enregistrer/ }).click();
    await expect(page.getByRole("heading", { name: "Gestion des clients" })).toBeVisible();

    // Le client apparait dans la liste (recherche faite par le serveur)
    await page.getByPlaceholder(/Rechercher/).fill(client);
    await expect(page.getByRole("cell", { name: client })).toBeVisible();
    await expect(page.getByRole("cell", { name: "Siège" }).first()).toBeVisible();

    // 2. Creance
    await page.goto("/debts/new");
    await page.locator('input[name="numFacture"]').fill(facture);
    await page.getByRole("combobox").click();
    await page.getByRole("option", { name: client }).click();
    await page.locator('input[name="echeance"]').fill("2026-12-31");
    await page.locator('input[name="montantFacture"]').fill("5000");
    await page.getByRole("button", { name: /la créance/ }).click();
    await expect(page.getByRole("heading", { name: "Gestion des créances" })).toBeVisible();
    await page.getByPlaceholder(/Rechercher/).fill(facture);
    await expect(page.getByRole("cell", { name: facture })).toBeVisible();

    // 3. Reglement integral, marque effectue
    await page.goto(`/payments/new?debtId=${facture}`);
    await page.getByRole("combobox").nth(2).click(); // statut
    await page.getByRole("option", { name: "Effectué", exact: true }).click();
    await page.getByRole("button", { name: /le règlement/ }).click();
    await expect(page.getByRole("heading", { name: "Gestion des règlements" })).toBeVisible();

    // 4. La creance est soldee : montant encaisse et statut recalcules PAR LE SERVEUR
    await page.goto(`/debts/${facture}/details`);
    await expect(page.getByText("Payée").first()).toBeVisible();
  });

  test("un agent ne voit ni la gestion des utilisateurs, ni les departements, ni l'assistant IA", async ({ page, request }) => {
    // Compte agent cree via l'API dans un departement dedie (le test ne depend d'aucun compte pre-existant).
    const id = unique();
    const admin = await apiToken(request);
    const dept = await apiDepartement(request, admin, `Agence ${id}`, `AG${id}`.toUpperCase().slice(0, 12));
    const agent = await apiUtilisateur(request, admin, "AGENT", dept, "Agent E2E");

    await login(page, agent.email, agent.password);

    await expect(page.getByRole("link", { name: "Utilisateurs" })).toHaveCount(0);
    await expect(page.getByRole("link", { name: "Départements" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Ouvrir le chatbot" })).toHaveCount(0);
    await page.goto("/utilisateurs");
    await expect(page.getByRole("heading", { name: "Tableau de bord" })).toBeVisible(); // renvoye vers le tableau de bord
    await page.goto("/departements");
    await expect(page.getByRole("heading", { name: "Tableau de bord" })).toBeVisible();
  });
});
