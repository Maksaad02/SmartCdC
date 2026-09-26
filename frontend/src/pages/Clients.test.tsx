import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import Clients from "./Clients";
import { setAccessToken } from "@/lib/authToken";

const fetchMock = vi.fn();

const pageOf = (names: string[], totalElements = names.length) =>
  new Response(JSON.stringify({
    content: names.map((raisonSociale, i) => ({ id: i + 1, raisonSociale, email: "a@b.c", telephone: "0522" })),
    page: { size: 20, number: 0, totalElements, totalPages: Math.ceil(totalElements / 20) },
  }), { status: 200 });

const renderPage = () =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter><Clients /></MemoryRouter>
    </QueryClientProvider>,
  );

describe("page Clients", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockReset();
    setAccessToken("jeton");
  });
  afterEach(() => vi.unstubAllGlobals());

  it("affiche les clients d'une page et le total", async () => {
    fetchMock.mockResolvedValue(pageOf(["Acme SARL", "Beta SA"], 42));

    renderPage();

    expect(await screen.findByText("Acme SARL")).toBeInTheDocument();
    expect(screen.getByText(/sur 42/)).toBeInTheDocument();
    expect(String(fetchMock.mock.calls[0][0])).toContain("/clients?page=0&size=20");
  });

  /** Regression : une erreur reseau s'affichait comme « Aucun client trouve » (liste vide). */
  it("une erreur serveur n'est pas presentee comme une liste vide", async () => {
    fetchMock.mockResolvedValue(new Response("{}", { status: 500 }));

    renderPage();

    expect(await screen.findByRole("alert")).toHaveTextContent("Impossible de charger les clients");
    expect(screen.queryByText("Aucun client trouvé")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Réessayer" })).toBeInTheDocument();
  });

  it("la recherche est envoyee au serveur (et non filtree dans le navigateur)", async () => {
    fetchMock.mockImplementation(() => Promise.resolve(pageOf(["Acme SARL"])));
    renderPage();
    await screen.findByText("Acme SARL");

    await userEvent.type(screen.getByPlaceholderText(/Rechercher/), "acme");

    await waitFor(() => {
      const urls = fetchMock.mock.calls.map((c) => String(c[0]));
      expect(urls.some((u) => u.includes("q=acme"))).toBe(true);
    });
  });

  it("« Suivant » demande la page suivante au serveur", async () => {
    fetchMock.mockImplementation(() => Promise.resolve(pageOf(["Acme SARL"], 45)));
    renderPage();
    await screen.findByText("Acme SARL");

    await userEvent.click(screen.getByRole("button", { name: "Suivant" }));

    await waitFor(() => {
      const urls = fetchMock.mock.calls.map((c) => String(c[0]));
      expect(urls.some((u) => u.includes("page=1"))).toBe(true);
    });
  });
});
