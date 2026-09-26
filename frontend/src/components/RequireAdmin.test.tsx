import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import RequireAdmin from "./RequireAdmin";

const useAuth = vi.fn();
vi.mock("../contexts/AuthContext", () => ({ useAuth: () => useAuth() }));

const renderAt = () =>
  render(
    <MemoryRouter initialEntries={["/utilisateurs"]}>
      <Routes>
        <Route path="/dashboard" element={<p>tableau de bord</p>} />
        <Route path="/utilisateurs" element={<RequireAdmin><p>gestion des utilisateurs</p></RequireAdmin>} />
      </Routes>
    </MemoryRouter>,
  );

describe("RequireAdmin", () => {
  it("laisse passer un administrateur", () => {
    useAuth.mockReturnValue({ currentUser: { role: "admin" }, isLoading: false });
    renderAt();
    expect(screen.getByText("gestion des utilisateurs")).toBeInTheDocument();
  });

  it("renvoie un agent vers le tableau de bord", () => {
    useAuth.mockReturnValue({ currentUser: { role: "manager" }, isLoading: false });
    renderAt();
    expect(screen.getByText("tableau de bord")).toBeInTheDocument();
  });

  it("n'affiche rien tant que la session est en cours de revalidation", () => {
    useAuth.mockReturnValue({ currentUser: null, isLoading: true });
    const { container } = renderAt();
    expect(container).toBeEmptyDOMElement();
  });
});
