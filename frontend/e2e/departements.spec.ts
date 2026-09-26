import { expect, test } from "@playwright/test";
import { apiClient, apiDepartement, apiToken, apiUtilisateur, bearer, login, unique } from "./helpers";

/**
 * Cloisonnement par departement, de bout en bout : deux departements, un gestionnaire dans chacun.
 * Chaque test cree ses propres donnees (suffixe unique) et ne suppose rien de la base.
 */
test.describe("Departements : cloisonnement et vue globale", () => {
  test("un gestionnaire ne voit que son departement, jamais celui d'un autre, ni l'assistant IA", async ({ page, request }) => {
    const id = unique();
    const admin = await apiToken(request);
    const deptA = await apiDepartement(request, admin, `Casa ${id}`, `CA${id}`.toUpperCase().slice(0, 12));
    const deptB = await apiDepartement(request, admin, `Rabat ${id}`, `RB${id}`.toUpperCase().slice(0, 12));
    const managerA = await apiUtilisateur(request, admin, "MANAGER", deptA, "Manager A");
    const clientA = `Client Casa ${id}`;
    const clientB = `Client Rabat ${id}`;
    await apiClient(request, admin, clientA, deptA);
    const idB = await apiClient(request, admin, clientB, deptB);

    await login(page, managerA.email, managerA.password);

    // Interface : son client, pas celui de l'autre departement.
    await page.goto("/clients");
    await page.getByPlaceholder(/Rechercher/).fill(id);
    await expect(page.getByRole("cell", { name: clientA })).toBeVisible();
    await expect(page.getByRole("cell", { name: clientB })).toHaveCount(0);

    // Interface : ni assistant IA, ni administration.
    await expect(page.getByRole("button", { name: "Ouvrir le chatbot" })).toHaveCount(0);
    await expect(page.getByRole("link", { name: "Départements" })).toHaveCount(0);
    await expect(page.getByRole("link", { name: "Utilisateurs" })).toHaveCount(0);
    await page.goto("/dashboard");
    await expect(page.getByText("Performance par département")).toHaveCount(0);

    // API : le serveur refuse, l'interface masquee n'est pas la seule barriere.
    const token = await apiToken(request, managerA.email, managerA.password);
    expect((await request.get(`/api/clients/${idB}`, { headers: bearer(token) })).status()).toBe(404);
    expect((await request.get("/api/dashboard/departements", { headers: bearer(token) })).status()).toBe(403);
    expect((await request.get("/api/departements", { headers: bearer(token) })).status()).toBe(200);
    const visibles = await (await request.get("/api/departements", { headers: bearer(token) })).json();
    expect(visibles).toHaveLength(1);
    expect(visibles[0].id).toBe(deptA);
  });

  test("un gestionnaire ne peut pas creer un client dans un autre departement", async ({ request }) => {
    const id = unique();
    const admin = await apiToken(request);
    const deptA = await apiDepartement(request, admin, `Fes ${id}`, `FS${id}`.toUpperCase().slice(0, 12));
    const deptB = await apiDepartement(request, admin, `Agadir ${id}`, `AD${id}`.toUpperCase().slice(0, 12));
    const managerA = await apiUtilisateur(request, admin, "MANAGER", deptA, "Manager Fes");
    const token = await apiToken(request, managerA.email, managerA.password);

    const res = await request.post("/api/clients", {
      headers: bearer(token),
      data: { raisonSociale: `Intrus ${id}`, email: "i@e2e.test", telephone: "0522000000", adresse: "x", departementId: deptB },
    });

    expect(res.status()).toBe(403);
  });

  test("l'administrateur voit l'assistant IA et le comparatif consolide des departements", async ({ page, request }) => {
    const id = unique();
    const admin = await apiToken(request);
    const nomA = `Tanger ${id}`;
    const nomB = `Oujda ${id}`;
    await apiDepartement(request, admin, nomA, `TG${id}`.toUpperCase().slice(0, 12));
    await apiDepartement(request, admin, nomB, `OJ${id}`.toUpperCase().slice(0, 12));

    await login(page);

    await expect(page.getByRole("button", { name: "Ouvrir le chatbot" })).toBeVisible();
    await expect(page.getByText("Performance par département")).toBeVisible();
    await expect(page.getByRole("cell", { name: nomA })).toBeVisible();
    await expect(page.getByRole("cell", { name: nomB })).toBeVisible();
    await expect(page.getByRole("cell", { name: "Global" })).toBeVisible();
  });

  test("l'administrateur gere les departements depuis l'interface", async ({ page }) => {
    const id = unique();
    const nom = `Marrakech ${id}`;
    await login(page);

    await page.getByRole("link", { name: "Départements" }).click();
    await expect(page.getByRole("heading", { name: "Gestion des départements" })).toBeVisible();
    await page.getByRole("button", { name: "Nouveau département" }).click();
    await page.getByLabel("Nom").fill(nom);
    await page.getByLabel("Code").fill(`MK${id}`.toUpperCase().slice(0, 12));
    await page.getByRole("button", { name: "Enregistrer" }).click();

    await expect(page.getByRole("cell", { name: nom, exact: true })).toBeVisible();
  });
});
