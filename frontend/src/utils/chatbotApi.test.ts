import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { askChatbot, askChatbotStream } from "./chatbotApi";
import { setAccessToken } from "@/lib/authToken";

describe("askChatbot", () => {
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

  const hangingFetch = () =>
    fetchMock.mockImplementation((_url: string, init: RequestInit) =>
      new Promise((_resolve, reject) => {
        init.signal?.addEventListener("abort", () => reject(new DOMException("aborted", "AbortError")));
      }),
    );

  it("renvoie la reponse texte du chatbot", async () => {
    fetchMock.mockResolvedValue(new Response("Bonjour", { status: 200 }));

    await expect(askChatbot("Salut")).resolves.toBe("Bonjour");
    expect(fetchMock.mock.calls[0][0]).toMatch(/\/chat\/ask$/);
  });

  it("n'attend pas indefiniment un service bloque : delai depasse", async () => {
    vi.useFakeTimers();
    hangingFetch();

    const promise = askChatbot("Salut", { timeoutMs: 5000 });
    const assertion = expect(promise).rejects.toMatchObject({ kind: "timeout" });
    await vi.advanceTimersByTimeAsync(5001);

    await assertion;
  });

  it("peut etre annulee par l'utilisateur", async () => {
    hangingFetch();
    const controller = new AbortController();

    const promise = askChatbot("Salut", { signal: controller.signal });
    const assertion = expect(promise).rejects.toMatchObject({ kind: "cancelled" });
    controller.abort();

    await assertion;
  });

  it.each([
    [401, "auth"],
    [403, "forbidden"],
    [429, "rate-limit"],
    [503, "unavailable"],
    [500, "server"],
  ])("traduit le statut HTTP %i", async (status, kind) => {
    fetchMock.mockResolvedValue(new Response("", { status }));

    await expect(askChatbot("Salut")).rejects.toMatchObject({ kind });
  });

  it("refuse une question vide ou trop longue sans appeler le reseau", async () => {
    await expect(askChatbot("   ")).rejects.toMatchObject({ kind: "validation" });
    await expect(askChatbot("x".repeat(2001))).rejects.toMatchObject({ kind: "validation" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("exige une session", async () => {
    setAccessToken(null);

    await expect(askChatbot("Salut")).rejects.toMatchObject({ kind: "auth" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("jeton expire : renouvelle la session puis rejoue la question une fois", async () => {
    fetchMock
      .mockResolvedValueOnce(new Response("", { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: "nouveau-jeton" }), { status: 200 }))
      .mockResolvedValueOnce(new Response("Réponse", { status: 200 }));

    await expect(askChatbot("Salut")).resolves.toBe("Réponse");
    expect(fetchMock.mock.calls[2][1].headers.Authorization).toBe("Bearer nouveau-jeton");
  });
});

// ---------------------------------------------------------------- flux SSE

const sse = (...chunks: string[]) => {
  const encoder = new TextEncoder();
  return new Response(new ReadableStream({
    start(controller) {
      chunks.forEach((c) => controller.enqueue(encoder.encode(c)));
      controller.close();
    },
  }), { status: 200, headers: { "Content-Type": "text/event-stream" } });
};

describe("askChatbotStream", () => {
  const fetchMock2 = vi.fn();

  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock2);
    fetchMock2.mockReset();
    setAccessToken("jeton-test");
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    setAccessToken(null);
  });

  it("transmet les morceaux dans l'ordre puis s'arrete sur l'evenement done", async () => {
    fetchMock2.mockResolvedValue(sse(
      "event:message\ndata:Le délai \n\n",
      "event:message\ndata:est de 15 jours.\n\nevent:done\ndata:\n\n",
    ));
    const received: string[] = [];

    const full = await askChatbotStream("Délai ?", { onDelta: (d) => received.push(d) });

    expect(received).toEqual(["Le délai ", "est de 15 jours."]);
    expect(full).toBe("Le délai est de 15 jours.");
    expect(String(fetchMock2.mock.calls[0][0])).toMatch(/\/chat\/ask\/stream$/);
    expect(fetchMock2.mock.calls[0][1].headers.Accept).toBe("text/event-stream");
  });

  it("reconstitue un evenement coupe au milieu par le reseau et les accents (UTF-8)", async () => {
    // "é" = 2 octets : le decoupage tombe au milieu du caractere ET au milieu de l'evenement.
    const bytes = new TextEncoder().encode("event:message\ndata:Délai légal\n\nevent:done\ndata:\n\n");
    const enc = new TextEncoder();
    const cut = 20;
    const parts = [bytes.slice(0, cut), bytes.slice(cut)];
    fetchMock2.mockResolvedValue(new Response(new ReadableStream({
      start(c) { parts.forEach((p) => c.enqueue(p)); c.close(); },
    }), { status: 200 }));
    void enc;

    const full = await askChatbotStream("Q ?", { onDelta: () => {} });

    expect(full).toBe("Délai légal");
  });

  it("les retours a la ligne d'un morceau (plusieurs lignes data) sont restitues", async () => {
    fetchMock2.mockResolvedValue(sse("event:message\ndata:Ligne 1\ndata:Ligne 2\n\nevent:done\ndata:\n\n"));

    const full = await askChatbotStream("Q ?", { onDelta: () => {} });

    expect(full).toBe("Ligne 1\nLigne 2");
  });

  it("un evenement error signale l'echec en gardant ce qui a ete recu", async () => {
    fetchMock2.mockResolvedValue(sse(
      "event:message\ndata:Début \n\n",
      "event:error\ndata:Le service d'IA est momentanément indisponible.\n\nevent:done\ndata:\n\n",
    ));
    const received: string[] = [];

    await expect(askChatbotStream("Q ?", { onDelta: (d) => received.push(d) }))
      .rejects.toMatchObject({ kind: "unavailable" });
    expect(received).toEqual(["Début "]);
  });

  it("un flux fige (aucun octet) est abandonne apres le delai d'inactivite", async () => {
    vi.useFakeTimers();
    fetchMock2.mockImplementation((_url: string, init: RequestInit) =>
      new Promise((_resolve, reject) => {
        init.signal?.addEventListener("abort", () => reject(new DOMException("aborted", "AbortError")));
      }));

    const promise = askChatbotStream("Q ?", { onDelta: () => {}, idleTimeoutMs: 5000 });
    const assertion = expect(promise).rejects.toMatchObject({ kind: "timeout" });
    await vi.advanceTimersByTimeAsync(5001);

    await assertion;
  });

  it("peut etre annule par l'utilisateur", async () => {
    fetchMock2.mockImplementation((_url: string, init: RequestInit) =>
      new Promise((_resolve, reject) => {
        init.signal?.addEventListener("abort", () => reject(new DOMException("aborted", "AbortError")));
      }));
    const controller = new AbortController();

    const promise = askChatbotStream("Q ?", { onDelta: () => {}, signal: controller.signal });
    const assertion = expect(promise).rejects.toMatchObject({ kind: "cancelled" });
    controller.abort();

    await assertion;
  });

  it("traduit les statuts HTTP comme l'appel simple (429, 401 apres renouvellement)", async () => {
    fetchMock2.mockResolvedValueOnce(new Response("", { status: 429 }));
    await expect(askChatbotStream("Q ?", { onDelta: () => {} })).rejects.toMatchObject({ kind: "rate-limit" });

    fetchMock2.mockResolvedValueOnce(new Response("", { status: 401 }))            // jeton expire
      .mockResolvedValueOnce(new Response("", { status: 401 }));                    // renouvellement refuse
    await expect(askChatbotStream("Q ?", { onDelta: () => {} })).rejects.toMatchObject({ kind: "auth" });
  });

  it("apres un renouvellement reussi, rejoue le flux avec le nouveau jeton", async () => {
    fetchMock2
      .mockResolvedValueOnce(new Response("", { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: "nouveau-jeton" }), { status: 200 }))
      .mockResolvedValueOnce(sse("event:message\ndata:ok\n\nevent:done\ndata:\n\n"));

    await expect(askChatbotStream("Q ?", { onDelta: () => {} })).resolves.toBe("ok");
    expect(fetchMock2.mock.calls[2][1].headers.Authorization).toBe("Bearer nouveau-jeton");
  });
});
