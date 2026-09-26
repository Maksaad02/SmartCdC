import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import ErrorBoundary from "./ErrorBoundary";

let shouldThrow = true;
const Fragile = () => {
  if (shouldThrow) throw new Error("boom");
  return <p>contenu rétabli</p>;
};

describe("ErrorBoundary", () => {
  beforeEach(() => {
    shouldThrow = true;
    // React journalise l'erreur capturee : on evite de polluer la sortie.
    vi.spyOn(console, "error").mockImplementation(() => {});
  });
  afterEach(() => vi.restoreAllMocks());

  it("affiche un ecran d'erreur au lieu d'une page blanche", () => {
    render(<ErrorBoundary><Fragile /></ErrorBoundary>);

    expect(screen.getByRole("alert")).toHaveTextContent("Une erreur est survenue");
  });

  it("« Réessayer » remonte reellement le sous-arbre", async () => {
    render(<ErrorBoundary variant="page"><Fragile /></ErrorBoundary>);
    shouldThrow = false; // la cause a disparu (ex. donnees rechargees)

    await userEvent.click(screen.getByRole("button", { name: "Réessayer" }));

    expect(screen.getByText("contenu rétabli")).toBeInTheDocument();
  });

  it("efface l'erreur quand la cle de reinitialisation change (navigation)", () => {
    const { rerender } = render(<ErrorBoundary resetKey="/a"><Fragile /></ErrorBoundary>);
    expect(screen.getByRole("alert")).toBeInTheDocument();
    shouldThrow = false;

    rerender(<ErrorBoundary resetKey="/b"><Fragile /></ErrorBoundary>);

    expect(screen.getByText("contenu rétabli")).toBeInTheDocument();
  });
});
