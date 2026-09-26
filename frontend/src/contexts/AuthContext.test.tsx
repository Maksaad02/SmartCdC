import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider, useAuth } from "./AuthContext";
import { AUTH_EXPIRED_EVENT } from "@/lib/apiClient";
import { getAccessToken, setAccessToken } from "@/lib/authToken";

const toast = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn(), info: vi.fn() }));
vi.mock("sonner", () => ({ toast }));

const fetchMock = vi.fn();

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

const session = (over: object = {}) => ({ token: "jeton-d-acces", expiresIn: 900, ...over });
const me = (role: string) => ({ id: 1, nom: "Agent", email: "agent@x", role });

const Probe = () => {
  const { isAuthenticated, isLoading, currentUser, login, logout } = useAuth();
  return (
    <div>
      <p data-testid="state">{isLoading ? "loading" : isAuthenticated ? `in:${currentUser?.role}` : "out"}</p>
      <button onClick={() => login("agent@x", "pw")}>login</button>
      <button onClick={logout}>logout</button>
    </div>
  );
};

const renderProvider = () =>
  render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider><Probe /></AuthProvider>
    </QueryClientProvider>,
  );

const urls = () => fetchMock.mock.calls.map((c) => String(c[0]));

describe("AuthProvider (session par cookie de renouvellement)", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockReset();
    Object.values(toast).forEach((f) => f.mockReset());
    setAccessToken(null);
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    setAccessToken(null);
  });

  it("au chargement, reprend la session grace au cookie sans rien lire dans le navigateur", async () => {
    fetchMock
      .mockResolvedValueOnce(json(session()))       // /auth/refresh
      .mockResolvedValueOnce(json(me("AGENT")));    // /utilisateurs/me

    renderProvider();

    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("in:agent"));
    expect(urls()[0]).toMatch(/\/auth\/refresh$/);
    expect(getAccessToken()).toBe("jeton-d-acces");
    expect(localStorage.length).toBe(0);
  });

  it("sans cookie valide, l'utilisateur est deconnecte", async () => {
    fetchMock.mockResolvedValueOnce(json("Session expirée", 401));

    renderProvider();

    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("out"));
    expect(getAccessToken()).toBeNull();
  });

  it("efface les anciens jetons que les versions precedentes laissaient dans localStorage", async () => {
    localStorage.setItem("auth_token", "ancien-jeton-lisible-par-xss");
    localStorage.setItem("user", "{}");
    fetchMock.mockResolvedValueOnce(json({}, 401));

    renderProvider();

    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("out"));
    expect(localStorage.getItem("auth_token")).toBeNull();
    expect(localStorage.getItem("user")).toBeNull();
  });

  it.each([
    ["ADMIN", "admin"],
    ["MANAGER", "manager"],
    ["AGENT", "agent"],
    ["SUPER_ADMIN", "agent"], // role supprime : jamais elevé par erreur, le moins privilegie s'applique
  ])("le role serveur %s devient %s cote interface", async (serveur, attendu) => {
    fetchMock
      .mockResolvedValueOnce(json({}, 401)) // pas de session au chargement
      .mockResolvedValueOnce(json(session()))
      .mockResolvedValueOnce(json(me(serveur)));
    renderProvider();
    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("out"));

    await userEvent.click(screen.getByText("login"));

    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent(`in:${attendu}`));
    expect(localStorage.length).toBe(0);
  });

  it("un mot de passe incorrect affiche « Identifiants incorrects »", async () => {
    fetchMock
      .mockResolvedValueOnce(json({}, 401))
      .mockResolvedValueOnce(json("Identifiants incorrects", 401));
    renderProvider();
    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("out"));

    await userEvent.click(screen.getByText("login"));

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith("Identifiants incorrects"));
    expect(screen.getByTestId("state")).toHaveTextContent("out");
  });

  it("renouvelle le jeton avant son expiration, sans deconnecter", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    fetchMock
      .mockResolvedValueOnce(json(session({ expiresIn: 10 })))  // reprise
      .mockResolvedValueOnce(json(me("AGENT")))
      .mockResolvedValueOnce(json(session({ token: "jeton-renouvele", expiresIn: 10 }))); // renouvellement a 80 %
    renderProvider();
    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("in:agent"));

    await act(async () => { await vi.advanceTimersByTimeAsync(8500); });

    expect(getAccessToken()).toBe("jeton-renouvele");
    expect(screen.getByTestId("state")).toHaveTextContent("in:agent");
    expect(toast.info).not.toHaveBeenCalled();
  });

  it("si le renouvellement echoue (session terminee cote serveur), deconnecte avec un message clair", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    fetchMock
      .mockResolvedValueOnce(json(session({ expiresIn: 10 })))
      .mockResolvedValueOnce(json(me("AGENT")))
      .mockResolvedValueOnce(json("Session expirée", 401));
    renderProvider();
    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("in:agent"));

    await act(async () => { await vi.advanceTimersByTimeAsync(8500); });

    expect(screen.getByTestId("state")).toHaveTextContent("out");
    expect(toast.info).toHaveBeenCalledWith("Votre session a expiré. Veuillez vous reconnecter.");
  });

  it("un 401 definitif sur un appel quelconque deconnecte l'utilisateur", async () => {
    fetchMock.mockResolvedValueOnce(json(session())).mockResolvedValueOnce(json(me("AGENT")));
    renderProvider();
    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("in:agent"));

    act(() => { window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT)); });

    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("out"));
    expect(getAccessToken()).toBeNull();
    expect(toast.info).toHaveBeenCalledWith("Session expirée. Veuillez vous reconnecter.");
  });

  it("la deconnexion revoque la session cote serveur", async () => {
    fetchMock
      .mockResolvedValueOnce(json(session())).mockResolvedValueOnce(json(me("AGENT")))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    renderProvider();
    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("in:agent"));

    await userEvent.click(screen.getByText("logout"));

    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("out"));
    expect(urls().some((u) => u.endsWith("/auth/logout"))).toBe(true);
    expect(getAccessToken()).toBeNull();
  });
});
