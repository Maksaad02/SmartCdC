import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import Layout from "./Layout";

const useAuth = vi.fn();
vi.mock("../contexts/AuthContext", () => ({ useAuth: () => useAuth() }));
vi.mock("./Navbar", () => ({ default: () => <nav>navbar</nav> }));
vi.mock("./Chatbot", () => ({ default: () => <div>assistant ia</div> }));

const renderLayout = () =>
  render(
    <MemoryRouter initialEntries={["/dashboard"]}>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/dashboard" element={<p>tableau de bord</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );

describe("Layout : visibilite de l'assistant IA", () => {
  beforeEach(() => useAuth.mockReset());

  it("l'assistant est visible pour un administrateur", () => {
    useAuth.mockReturnValue({ isAuthenticated: true, isLoading: false, currentUser: { role: "admin" } });
    renderLayout();
    expect(screen.getByText("assistant ia")).toBeInTheDocument();
  });

  it.each(["manager", "agent"])("l'assistant est totalement masque pour le role %s", (role) => {
    useAuth.mockReturnValue({ isAuthenticated: true, isLoading: false, currentUser: { role } });
    renderLayout();
    expect(screen.getByText("tableau de bord")).toBeInTheDocument();
    expect(screen.queryByText("assistant ia")).not.toBeInTheDocument();
  });
});
