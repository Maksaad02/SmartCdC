import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import DebtForm from "./DebtForm";
import { setAccessToken } from "@/lib/authToken";

const useAuth = vi.fn();
vi.mock("../contexts/AuthContext", () => ({ useAuth: () => useAuth() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() } }));

const fetchMock = vi.fn();

const clients = {
  content: [{ id: 1, raisonSociale: "ACME SARL" }],
  page: { size: 200, number: 0, totalElements: 1, totalPages: 1 },
};
const extraction = {
  numFacture: "F-2026-0042",
  dateEmission: "2026-09-01",
  echeance: "2026-10-01",
  montantFacture: 12500,
  clientRaisonSociale: "ACME SARL",
  clientIce: "009988776655443",
  clientTrouve: "ACME SARL",
  source: "TEXTE",
  avertissements: ["Total HT et TTC ambigus"],
};

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
const appels = () => fetchMock.mock.calls.map((c) => ({ url: String(c[0]), init: c[1] as RequestInit }));

const repondre = (actif: boolean) => async (url: string, init?: RequestInit) => {
  if (url.includes("/factures/extraction/statut")) return json({ actif });
  if (url.includes("/factures/extraction")) return json(extraction);
  if (url.includes("/clients")) return json(clients);
  if (url.endsWith("/document")) return json({ nomFichier: "facture.pdf", taille: 4, dateAjout: "2026-10-06T10:00:00" });
  if (url.endsWith("/creances") && init?.method === "POST") return json({});
  return json({});
};

const pdf = () => new File(["%PDF-1.7"], "facture.pdf", { type: "application/pdf" });

const renderNew = () =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={["/debts/new"]}>
        <Routes>
          <Route path="/debts/new" element={<DebtForm />} />
          <Route path="/debts" element={<p>liste des creances</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );

describe("DebtForm — import d'une facture PDF", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockReset();
    setAccessToken("jeton");
    useAuth.mockReturnValue({ currentUser: { role: "agent", name: "Agent" } });
  });
  afterEach(() => vi.unstubAllGlobals());

  it("pre-remplit le formulaire, signale les points a verifier, puis joint le PDF a la creance creee", async () => {
    fetchMock.mockImplementation(repondre(true));
    const user = userEvent.setup();
    renderNew();

    const bouton = await screen.findByRole("button", { name: /Importer une facture/ });
    expect(bouton).toBeInTheDocument();
    await user.upload(screen.getByLabelText("Facture PDF à importer"), pdf());

    expect(await screen.findByDisplayValue("F-2026-0042")).toBeInTheDocument();
    expect(screen.getByLabelText("Échéance")).toHaveValue("2026-10-01");
    expect(screen.getByLabelText("Montant facturé")).toHaveValue(12500);
    expect(screen.getByText("Total HT et TTC ambigus")).toBeInTheDocument();
    expect(screen.getByText(/facture\.pdf sera jointe/)).toBeInTheDocument();

    const extractionCall = appels().find((a) => a.url.endsWith("/factures/extraction"));
    expect(extractionCall?.init.body).toBeInstanceOf(FormData);
    expect(new Headers(extractionCall?.init.headers).has("Content-Type")).toBe(false);

    await user.click(screen.getByRole("button", { name: "Créer la créance" }));

    expect(await screen.findByText("liste des creances")).toBeInTheDocument();
    const creation = appels().find((a) => a.url.endsWith("/creances") && a.init.method === "POST");
    expect(JSON.parse(String(creation?.init.body))).toMatchObject({
      numFacture: "F-2026-0042", clientName: "ACME SARL", echeance: "2026-10-01", montantFacture: 12500,
    });
    const jointe = appels().find((a) => a.url.endsWith("/creances/F-2026-0042/document"));
    expect(jointe?.init.method).toBe("POST");
    expect((jointe?.init.body as FormData).get("fichier")).toBeInstanceOf(File);
  });

  it("n'affiche pas l'import quand le serveur n'a pas de cle d'API", async () => {
    fetchMock.mockImplementation(repondre(false));
    renderNew();

    await waitFor(() => expect(appels().some((a) => a.url.includes("/factures/extraction/statut"))).toBe(true));
    expect(screen.queryByRole("button", { name: /Importer une facture/ })).not.toBeInTheDocument();
  });
});
