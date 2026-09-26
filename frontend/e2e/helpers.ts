import { expect, type APIRequestContext, type Page } from "@playwright/test";

export const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL ?? "admin@smartcdc.test";
export const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? "Adm1n-Test-Passw0rd!";

export async function login(page: Page, email = ADMIN_EMAIL, password = ADMIN_PASSWORD) {
  await page.goto("/login");
  await page.getByPlaceholder("Email").fill(email);
  await page.getByPlaceholder("Mot de passe").fill(password);
  await page.getByRole("button", { name: "Se connecter" }).click();
  await expect(page.getByRole("heading", { name: "Tableau de bord" })).toBeVisible();
}

/** Suffixe unique : les parcours partagent la base et ne se nettoient pas. */
export const unique = () => `${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`;

/** Jeton d'acces d'un compte, par l'API (les parcours preparent leurs donnees sans passer par l'interface). */
export async function apiToken(request: APIRequestContext, email = ADMIN_EMAIL, password = ADMIN_PASSWORD) {
  const res = await request.post("/api/login", { data: { username: email, password } });
  expect(res.status(), `connexion de ${email}`).toBe(200);
  return (await res.json()).token as string;
}

export const bearer = (token: string) => ({ Authorization: `Bearer ${token}` });

/** Cree un departement et renvoie son id. */
export async function apiDepartement(request: APIRequestContext, adminToken: string, nom: string, code: string) {
  const res = await request.post("/api/departements", { headers: bearer(adminToken), data: { nom, code } });
  expect(res.status()).toBe(201);
  return (await res.json()).id as number;
}

/** Cree un compte (ADMIN, MANAGER ou AGENT) et renvoie ses identifiants. */
export async function apiUtilisateur(
  request: APIRequestContext, adminToken: string,
  role: "ADMIN" | "MANAGER" | "AGENT", departementId: number | null, nom: string,
) {
  const roles = await (await request.get("/api/roles", { headers: bearer(adminToken) })).json();
  const roleId = roles.find((r: { nom: string }) => r.nom === role).id;
  const email = `${nom.toLowerCase().replace(/\W/g, ".")}.${unique()}@e2e.test`;
  const password = `Mdp-${unique()}-Solide-2026`;
  const res = await request.post("/api/utilisateurs", {
    headers: bearer(adminToken),
    data: { nom, email, motDePasse: password, roleId, ...(departementId ? { departementId } : {}) },
  });
  expect(res.status(), `creation de ${nom}`).toBe(201);
  return { email, password };
}

/** Cree un client dans un departement (ADMIN) et renvoie son id. */
export async function apiClient(request: APIRequestContext, token: string, raisonSociale: string, departementId?: number) {
  const res = await request.post("/api/clients", {
    headers: bearer(token),
    data: {
      raisonSociale, email: "contact@e2e.test", telephone: "0522000000", adresse: "1 rue des Tests",
      ...(departementId ? { departementId } : {}),
    },
  });
  expect(res.status(), `creation du client ${raisonSociale}`).toBe(200);
  return (await res.json()).id as number;
}
