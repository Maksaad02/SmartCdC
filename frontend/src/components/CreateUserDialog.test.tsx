import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import CreateUserDialog from "./CreateUserDialog";
import { setAccessToken } from "@/lib/authToken";

const fetchMock = vi.fn();

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });

const renderDialog = () =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <CreateUserDialog />
    </QueryClientProvider>,
  );

describe("CreateUserDialog", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockReset();
    setAccessToken("jeton");
    fetchMock.mockImplementation(async (url: string, init?: RequestInit) => {
      if (String(url).endsWith("/roles")) return json([{ id: 1, nom: "ADMIN" }, { id: 2, nom: "MANAGER" }, { id: 3, nom: "AGENT" }]);
      if (String(url).endsWith("/departements")) {
        return json([{ id: 10, nom: "Casablanca", code: "CASA", actif: true }, { id: 11, nom: "Ferme", code: "OLD", actif: false }]);
      }
      if (String(url).endsWith("/utilisateurs") && init?.method === "POST") return json({ id: 9 }, 201);
      return json({});
    });
  });
  afterEach(() => vi.unstubAllGlobals());

  const ouvrirEtRemplir = async (motDePasse: string) => {
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole("button", { name: /Nouvel utilisateur/ }));
    await user.type(await screen.findByLabelText("Nom"), "Sara Agent");
    await user.type(screen.getByLabelText("Email"), "sara@entreprise.test");
    await user.type(screen.getByLabelText("Mot de passe initial"), motDePasse);
    await waitFor(() => expect(fetchMock.mock.calls.some(([u]) => String(u).endsWith("/roles"))).toBe(true));
    return user;
  };

  const choisirDepartement = async (user: ReturnType<typeof userEvent.setup>) => {
    await user.click(screen.getByRole("combobox", { name: "Département" }));
    await user.click(await screen.findByRole("option", { name: "Casablanca" }));
  };

  it("envoie le roleId de AGENT et le departement choisi", async () => {
    const user = await ouvrirEtRemplir("Mot-de-passe-solide-1");
    await choisirDepartement(user);

    await user.click(screen.getByRole("button", { name: "Créer" }));

    await waitFor(() => {
      const post = fetchMock.mock.calls.find(([, init]) => init?.method === "POST");
      expect(post).toBeDefined();
      expect(JSON.parse(post![1].body)).toEqual({
        nom: "Sara Agent", email: "sara@entreprise.test", motDePasse: "Mot-de-passe-solide-1", roleId: 3, departementId: 10,
      });
    });
  });

  it("propose seulement les departements actifs", async () => {
    const user = await ouvrirEtRemplir("Mot-de-passe-solide-1");

    await user.click(screen.getByRole("combobox", { name: "Département" }));

    expect(await screen.findByRole("option", { name: "Casablanca" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Ferme" })).not.toBeInTheDocument();
  });

  it("refuse un manager ou un agent sans departement, sans appeler le serveur", async () => {
    const user = await ouvrirEtRemplir("Mot-de-passe-solide-1");

    await user.click(screen.getByRole("button", { name: "Créer" }));

    expect(fetchMock.mock.calls.some(([, init]) => init?.method === "POST")).toBe(false);
  });

  it("un administrateur n'a pas de departement : le champ disparait et rien n'est envoye", async () => {
    const user = await ouvrirEtRemplir("Mot-de-passe-solide-1");
    await user.click(screen.getByRole("combobox", { name: "Rôle" }));
    await user.click(await screen.findByRole("option", { name: /Administrateur d'entreprise/ }));

    expect(screen.queryByRole("combobox", { name: "Département" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Créer" }));

    await waitFor(() => {
      const post = fetchMock.mock.calls.find(([, init]) => init?.method === "POST");
      expect(post).toBeDefined();
      expect(JSON.parse(post![1].body)).toEqual({
        nom: "Sara Agent", email: "sara@entreprise.test", motDePasse: "Mot-de-passe-solide-1", roleId: 1,
      });
    });
  });

  it("refuse un mot de passe trop court sans appeler le serveur", async () => {
    const user = await ouvrirEtRemplir("court");
    await choisirDepartement(user);

    await user.click(screen.getByRole("button", { name: "Créer" }));

    expect(fetchMock.mock.calls.some(([, init]) => init?.method === "POST")).toBe(false);
  });
});
