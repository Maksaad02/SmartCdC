import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import DepartementComparison from "./DepartementComparison";
import { setAccessToken } from "@/lib/authToken";

// recharts mesure le DOM (ResizeObserver, tailles) : inutile ici, le tableau porte les chiffres.
vi.mock("recharts", () => {
  const Vide = () => null;
  return { Bar: Vide, BarChart: Vide, CartesianGrid: Vide, ResponsiveContainer: Vide, Tooltip: Vide, XAxis: Vide, YAxis: Vide };
});

const fetchMock = vi.fn();

const ligne = (departementId: number | null, nom: string, nb: number, retard: number, facture: number, encaisse: number, taux: number) => ({
  departementId, nom, nbCreances: nb, nbEnRetard: retard, montantFacture: facture, montantPenalites: 0,
  montantEncaisse: encaisse, solde: facture - encaisse, tauxRecouvrement: taux,
});

describe("DepartementComparison", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockReset();
    setAccessToken("jeton");
  });
  afterEach(() => vi.unstubAllGlobals());

  const renderIt = () =>
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <DepartementComparison />
      </QueryClientProvider>,
    );

  it("affiche chaque departement puis la ligne globale, avec les taux", async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({
      global: ligne(null, "Global", 5, 2, 3000, 1500, 50),
      departements: [ligne(1, "Casablanca", 3, 1, 2000, 1500, 75), ligne(2, "Rabat", 2, 1, 1000, 0, 0)],
    }), { status: 200 }));

    renderIt();

    expect(await screen.findByText("Casablanca")).toBeInTheDocument();
    expect(screen.getByText("Rabat")).toBeInTheDocument();
    expect(screen.getByText("Global")).toBeInTheDocument();
    expect(screen.getByText("75.0 %")).toBeInTheDocument();
    expect(screen.getByText("0.0 %")).toBeInTheDocument();
    expect(String(fetchMock.mock.calls[0][0])).toContain("/dashboard/departements");
  });

  it("une erreur serveur est signalee, pas presentee comme des zeros", async () => {
    fetchMock.mockResolvedValue(new Response("{}", { status: 403 }));

    renderIt();

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.queryByText("Global")).not.toBeInTheDocument();
  });
});
