import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { z } from "zod";
import { ApiError, apiFetch, AUTH_EXPIRED_EVENT } from "./apiClient";
import { getAccessToken, setAccessToken } from "./authToken";

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

describe("apiFetch", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockReset();
    setAccessToken("jeton-test");
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    setAccessToken(null);
  });

  it("ajoute le jeton et renvoie la reponse validee", async () => {
    fetchMock.mockResolvedValue(json({ id: 1, nom: "Ali" }));

    const result = await apiFetch("/x", { schema: z.object({ id: z.number(), nom: z.string() }) });

    expect(result).toEqual({ id: 1, nom: "Ali" });
    const headers = fetchMock.mock.calls[0][1].headers as Record<string, string>;
    expect(headers.Authorization).toBe("Bearer jeton-test");
  });

  it("ne traite jamais une erreur HTTP comme un succes (403)", async () => {
    fetchMock.mockResolvedValue(json({ message: "interdit" }, 403));

    await expect(apiFetch("/x")).rejects.toMatchObject({ kind: "http", status: 403 });
  });

  it("emet l'evenement d'expiration sur 401 quand le renouvellement echoue aussi", async () => {
    // 1er appel : 401 ; renouvellement (cookie) : 401 -> session terminee.
    fetchMock.mockResolvedValue(json({}, 401));
    const listener = vi.fn();
    window.addEventListener(AUTH_EXPIRED_EVENT, listener);

    await expect(apiFetch("/x")).rejects.toMatchObject({ status: 401 });

    expect(listener).toHaveBeenCalledTimes(1);
    window.removeEventListener(AUTH_EXPIRED_EVENT, listener);
  });

  it("n'emet pas l'evenement pour un appel public (login) en 401", async () => {
    fetchMock.mockResolvedValue(json({}, 401));
    const listener = vi.fn();
    window.addEventListener(AUTH_EXPIRED_EVENT, listener);

    await expect(apiFetch("/login", { method: "POST", authenticated: false })).rejects.toBeInstanceOf(ApiError);

    expect(listener).not.toHaveBeenCalled();
    window.removeEventListener(AUTH_EXPIRED_EVENT, listener);
  });

  it("rejette une reponse qui ne respecte pas le schema", async () => {
    // Le cas reel : une liste attendue, un objet d'erreur recu.
    fetchMock.mockResolvedValue(json({ error: "boom" }));

    await expect(apiFetch("/x", { schema: z.array(z.object({ id: z.number() })) }))
      .rejects.toMatchObject({ kind: "invalid-response" });
  });

  it("reprend le message metier renvoye par le backend sur une 400", async () => {
    fetchMock.mockResolvedValue(json({ message: "Email déjà utilisé" }, 400));

    await expect(apiFetch("/x")).rejects.toThrow("Email déjà utilisé");
  });

  it("renvoie undefined pour un corps vide (DELETE)", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await expect(apiFetch("/x", { method: "DELETE" })).resolves.toBeUndefined();
  });

  it("abandonne apres le delai maximal", async () => {
    vi.useFakeTimers();
    fetchMock.mockImplementation((_url: string, init: RequestInit) =>
      new Promise((_resolve, reject) => {
        init.signal?.addEventListener("abort", () => reject(new DOMException("aborted", "AbortError")));
      }),
    );

    const promise = apiFetch("/x", { timeoutMs: 1000 });
    const assertion = expect(promise).rejects.toMatchObject({ kind: "timeout" });
    await vi.advanceTimersByTimeAsync(1001);

    await assertion;
  });

  it("distingue une coupure reseau d'un delai depasse", async () => {
    fetchMock.mockRejectedValue(new TypeError("Failed to fetch"));

    await expect(apiFetch("/x")).rejects.toMatchObject({ kind: "network" });
  });

  it("respecte l'annulation demandee par l'appelant", async () => {
    fetchMock.mockImplementation((_url: string, init: RequestInit) =>
      new Promise((_resolve, reject) => {
        init.signal?.addEventListener("abort", () => reject(new DOMException("aborted", "AbortError")));
      }),
    );
    const controller = new AbortController();

    const promise = apiFetch("/x", { signal: controller.signal });
    const assertion = expect(promise).rejects.toMatchObject({ kind: "cancelled" });
    controller.abort();

    await assertion;
  });

  // ------------------------------------------------------------ renouvellement transparent

  it("un 401 declenche UN renouvellement puis rejoue l'appel avec le nouveau jeton", async () => {
    fetchMock
      .mockResolvedValueOnce(json({}, 401))                                        // appel : jeton expire
      .mockResolvedValueOnce(json({ token: "nouveau-jeton", expiresIn: 900 }))    // renouvellement
      .mockResolvedValueOnce(json({ ok: true }));                                  // appel rejoue

    const result = await apiFetch("/x", { schema: z.object({ ok: z.boolean() }) });

    expect(result).toEqual({ ok: true });
    expect(getAccessToken()).toBe("nouveau-jeton");
    const refreshCall = fetchMock.mock.calls[1];
    expect(String(refreshCall[0])).toMatch(/\/auth\/refresh$/);
    expect(refreshCall[1].headers["X-Requested-With"]).toBe("XMLHttpRequest");
    expect(fetchMock.mock.calls[2][1].headers.Authorization).toBe("Bearer nouveau-jeton");
  });

  it("ne rejoue qu'une seule fois : pas de boucle si le serveur refuse encore", async () => {
    fetchMock
      .mockResolvedValueOnce(json({}, 401))
      .mockResolvedValueOnce(json({ token: "nouveau-jeton" }))
      .mockResolvedValueOnce(json({}, 401));

    await expect(apiFetch("/x")).rejects.toMatchObject({ status: 401 });
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it("plusieurs appels qui expirent ensemble partagent UN seul renouvellement", async () => {
    let refreshes = 0;
    fetchMock.mockImplementation(async (url: string, init: RequestInit) => {
      if (String(url).endsWith("/auth/refresh")) {
        refreshes++;
        await new Promise((r) => setTimeout(r, 20));
        return json({ token: "nouveau-jeton" });
      }
      const auth = (init.headers as Record<string, string>).Authorization;
      return auth === "Bearer nouveau-jeton" ? json({ ok: 1 }) : json({}, 401);
    });

    await Promise.all([apiFetch("/a"), apiFetch("/b"), apiFetch("/c")]);

    expect(refreshes).toBe(1);
  });

  it("le jeton d'acces n'est jamais ecrit dans le stockage du navigateur", async () => {
    fetchMock.mockResolvedValueOnce(json({}, 401)).mockResolvedValueOnce(json({ token: "nouveau-jeton" }))
      .mockResolvedValueOnce(json({}));

    await apiFetch("/x");

    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });
});
