import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import Dashboard from "./Dashboard";
import { setAccessToken } from "@/lib/authToken";

const useAuth = vi.fn();
vi.mock("../contexts/AuthContext", () => ({ useAuth: () => useAuth() }));
// Le comparatif a ses propres tests : ici on ne verifie que sa presence.
vi.mock("@/components/DepartementComparison", () => ({ default: () => <p>comparatif</p> }));
// recharts mesure le DOM (ResizeObserver, tailles) : inutile ici.
vi.mock("recharts", () => {
  const Vide = () => null;
  return { Cell: Vide, Legend: Vide, Pie: Vide, PieChart: Vide, ResponsiveContainer: Vide, Tooltip: Vide };
});

const fetchMock = vi.fn();

const stats = (total: number) => ({
  totalCreances: total, montantTotal: 1000, montantEncaisse: 500, montantPenalites: 0, tauxRecouvrement: 50, parStatut: {},
});
const departements = [
  { id: 1, nom: "Casablanca", code: "CA", actif: true },
  { id: 2, nom: "Rabat", code: "RB", actif: false },
];
const page = { content: [], page: { size: 1, number: 0, totalElements: 0, totalPages: 0 } };

const repondre = (url: string) => {
  if (url.includes("/dashboard/stats?departementId=2")) return stats(7);
  if (url.includes("/dashboard/stats")) return stats(42);
  if (url.includes("/departements")) return departements;
  return page;
};

const urls = () => fetchMock.mock.calls.map((c) => String(c[0]));

describe("Dashboard", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockReset();
    fetchMock.mockImplementation(async (url: string) => new Response(JSON.stringify(repondre(String(url))), { status: 200 }));
    setAccessToken("jeton");
  });
  afterEach(() => vi.unstubAllGlobals());

  const renderAt = (path: string) =>
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter initialEntries={[path]}>
          <Dashboard />
        </MemoryRouter>
      </QueryClientProvider>,
    );

  it("un administrateur voit la vue generale avec la liste des departements et le comparatif", async () => {
    useAuth.mockReturnValue({ currentUser: { role: "admin", name: "Admin" } });
    renderAt("/dashboard");

    expect(await screen.findByText("42")).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Département affiché" })).toHaveTextContent("Vue générale");
    expect(screen.getByText("comparatif")).toBeInTheDocument();
    expect(urls().some((u) => u.includes("departementId"))).toBe(false);
  });

  it("un departement choisi restreint les chiffres et masque le comparatif", async () => {
    useAuth.mockReturnValue({ currentUser: { role: "admin", name: "Admin" } });
    renderAt("/dashboard?departement=2");

    expect(await screen.findByText("7")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /Rabat/ })).toBeInTheDocument();
    expect(screen.queryByText("comparatif")).not.toBeInTheDocument();
    expect(urls()).toContainEqual(expect.stringContaining("/dashboard/stats?departementId=2"));
    await waitFor(() => expect(urls().some((u) => u.includes("/relances") && u.includes("departementId=2"))).toBe(true));
  });

  it("un departement inconnu retombe sur la vue generale", async () => {
    useAuth.mockReturnValue({ currentUser: { role: "admin", name: "Admin" } });
    renderAt("/dashboard?departement=99");

    expect(await screen.findByText("42")).toBeInTheDocument();
    expect(screen.getByText("comparatif")).toBeInTheDocument();
    expect(urls().some((u) => u.includes("departementId=99"))).toBe(false);
  });

  it("un gestionnaire n'a pas de liste deroulante, meme avec un departement dans l'URL", async () => {
    useAuth.mockReturnValue({ currentUser: { role: "manager", name: "Gestionnaire", departementNom: "Casablanca" } });
    renderAt("/dashboard?departement=2");

    expect(await screen.findByText("42")).toBeInTheDocument();
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
    expect(screen.queryByText("comparatif")).not.toBeInTheDocument();
    expect(urls().some((u) => u.includes("/departements") || u.includes("departementId"))).toBe(false);
  });
});
