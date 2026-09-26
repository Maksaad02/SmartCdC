/**
 * Client du service chatbot (RAG : Spring AI + OpenAI GPT-4o + pgvector).
 *
 * Un appel LLM peut durer plusieurs dizaines de secondes, voire ne jamais
 * aboutir si le service ou le fournisseur est bloque. Sans delai maximal ni
 * annulation, l'interface restait alors indefiniment en attente et la saisie
 * desactivee jusqu'au rechargement de la page.
 */
import { CHATBOT_URL } from "@/lib/config";
import { getAccessToken } from "@/lib/authToken";
import { refreshSession } from "@/lib/session";

const MAX_QUESTION_LENGTH = 2000;

/** Delai maximal d'attente d'une reponse. Au-dela, l'appel est abandonne. */
export const CHAT_TIMEOUT_MS = 90_000;

export type ChatbotErrorKind =
  | "timeout"
  | "cancelled"
  | "network"
  | "auth"
  | "forbidden"
  | "rate-limit"
  | "unavailable"
  | "server"
  | "validation";

export class ChatbotError extends Error {
  constructor(
    message: string,
    public readonly kind: ChatbotErrorKind,
  ) {
    super(message);
    this.name = "ChatbotError";
  }
}

interface AskOptions {
  /** Permet a l'appelant d'annuler la requete (bouton Annuler, demontage). */
  signal?: AbortSignal;
  timeoutMs?: number;
}

const messageForStatus = (status: number): ChatbotError => {
  switch (status) {
    case 401:
      return new ChatbotError("Session expirée. Veuillez vous reconnecter.", "auth");
    case 403:
      return new ChatbotError("Vous n'avez pas accès à cette information.", "forbidden");
    case 429:
      return new ChatbotError("Trop de requêtes. Veuillez patienter un instant.", "rate-limit");
    case 502:
    case 503:
    case 504:
      return new ChatbotError(
        "Le service chatbot est momentanément indisponible. Réessayez dans un instant.",
        "unavailable",
      );
    default:
      return status >= 500
        ? new ChatbotError("Erreur du serveur chatbot. Veuillez réessayer.", "server")
        : new ChatbotError(`Erreur chatbot (${status}).`, "server");
  }
};

/**
 * Envoie une question au chatbot.
 *
 * POST et non GET : la question porte des donnees de recouvrement et ne doit
 * pas finir dans les journaux d'acces, l'historique ou l'en-tete Referer.
 * Le jeton de l'agent est transmis pour que le chatbot herite de ses droits.
 */
export const askChatbot = async (
  question: string,
  { signal, timeoutMs = CHAT_TIMEOUT_MS }: AskOptions = {},
): Promise<string> => {
  if (!question.trim()) {
    throw new ChatbotError("La question ne peut pas être vide", "validation");
  }
  if (question.length > MAX_QUESTION_LENGTH) {
    throw new ChatbotError(
      `Question trop longue (max ${MAX_QUESTION_LENGTH} caractères)`,
      "validation",
    );
  }

  if (!getAccessToken()) {
    throw new ChatbotError("Session expirée. Veuillez vous reconnecter.", "auth");
  }

  // Un seul AbortController combine le delai maximal et l'annulation de l'appelant.
  const controller = new AbortController();
  let timedOut = false;
  const timer = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, timeoutMs);
  const onCallerAbort = () => controller.abort();
  if (signal) {
    if (signal.aborted) controller.abort();
    else signal.addEventListener("abort", onCallerAbort, { once: true });
  }

  try {
    const send = () =>
      fetch(`${CHATBOT_URL}/chat/ask`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${getAccessToken()}`,
        },
        body: JSON.stringify({ question }),
        signal: controller.signal,
      });

    let response = await send();
    // Jeton d'acces expire : un renouvellement, puis on rejoue la question une fois.
    if (response.status === 401 && (await refreshSession())) {
      response = await send();
    }

    if (!response.ok) {
      throw messageForStatus(response.status);
    }
    return await response.text();
  } catch (err) {
    if (err instanceof ChatbotError) throw err;
    if (timedOut) {
      throw new ChatbotError(
        "Le chatbot met trop de temps à répondre. Réessayez dans un instant.",
        "timeout",
      );
    }
    if (controller.signal.aborted) {
      throw new ChatbotError("Requête annulée.", "cancelled");
    }
    throw new ChatbotError("Impossible de contacter le service chatbot.", "network");
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener("abort", onCallerAbort);
  }
};

// ---------------------------------------------------------------- reponse en flux (SSE)

interface StreamOptions {
  /** Appele avec chaque morceau de texte recu, dans l'ordre. */
  onDelta: (text: string) => void;
  signal?: AbortSignal;
  /** Delai sans aucun octet recu avant d'abandonner (le flux peut durer longtemps tant qu'il avance). */
  idleTimeoutMs?: number;
}

/** Delai maximal ENTRE deux morceaux : un flux qui avance n'est pas coupe, un flux fige l'est. */
export const CHAT_IDLE_TIMEOUT_MS = 60_000;

interface SseEvent {
  event: string;
  data: string;
}

/** Decoupe un bloc "event: x\ndata: y" (specification SSE) ; plusieurs lignes data sont jointes par \n. */
const parseSseBlock = (block: string): SseEvent | null => {
  let event = "message";
  const data: string[] = [];
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith("event:")) event = line.slice(6).trim();
    else if (line.startsWith("data:")) data.push(line.slice(5).replace(/^ /, ""));
  }
  return data.length === 0 && event === "message" ? null : { event, data: data.join("\n") };
};

/**
 * Pose une question et recoit la reponse au fil de l'eau : les premiers mots s'affichent des qu'ils
 * sont generes, au lieu d'attendre la fin (parfois plus d'une minute). Renvoie le texte complet.
 *
 * POST + lecture du flux (et non EventSource, qui ne sait faire que des GET) : la question ne transite
 * jamais dans l'URL.
 */
export const askChatbotStream = async (
  question: string,
  { onDelta, signal, idleTimeoutMs = CHAT_IDLE_TIMEOUT_MS }: StreamOptions,
): Promise<string> => {
  if (!question.trim()) {
    throw new ChatbotError("La question ne peut pas être vide", "validation");
  }
  if (question.length > MAX_QUESTION_LENGTH) {
    throw new ChatbotError(`Question trop longue (max ${MAX_QUESTION_LENGTH} caractères)`, "validation");
  }
  if (!getAccessToken()) {
    throw new ChatbotError("Session expirée. Veuillez vous reconnecter.", "auth");
  }

  const controller = new AbortController();
  let timedOut = false;
  let idleTimer: ReturnType<typeof setTimeout> | undefined;
  const armIdleTimer = () => {
    clearTimeout(idleTimer);
    idleTimer = setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, idleTimeoutMs);
  };
  const onCallerAbort = () => controller.abort();
  if (signal) {
    if (signal.aborted) controller.abort();
    else signal.addEventListener("abort", onCallerAbort, { once: true });
  }

  try {
    armIdleTimer();
    const send = () =>
      fetch(`${CHATBOT_URL}/chat/ask/stream`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "text/event-stream",
          Authorization: `Bearer ${getAccessToken()}`,
        },
        body: JSON.stringify({ question }),
        signal: controller.signal,
      });

    let response = await send();
    if (response.status === 401 && (await refreshSession())) {
      response = await send();
    }
    if (!response.ok) {
      throw messageForStatus(response.status);
    }
    if (!response.body) {
      throw new ChatbotError("Réponse vide du service chatbot.", "server");
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder("utf-8");
    let buffer = "";
    let full = "";
    let finished = false;

    while (!finished) {
      const { done, value } = await reader.read();
      if (done) break;
      armIdleTimer(); // des octets arrivent : le flux est vivant
      // Fins de ligne normalisees : un evenement se termine toujours par une ligne vide ("\n\n").
      buffer = (buffer + decoder.decode(value, { stream: true })).replace(/\r\n/g, "\n");

      let end: number;
      while ((end = buffer.indexOf("\n\n")) >= 0) {
        const block = buffer.slice(0, end);
        buffer = buffer.slice(end + 2);
        const event = parseSseBlock(block);
        if (!event) continue;
        if (event.event === "error") {
          throw new ChatbotError(event.data || "Le service d'IA est momentanément indisponible.", "unavailable");
        }
        if (event.event === "done") {
          finished = true;
          break;
        }
        full += event.data;
        onDelta(event.data);
      }
    }

    if (!finished && full === "") {
      throw new ChatbotError("La réponse s'est interrompue avant de commencer.", "unavailable");
    }
    return full;
  } catch (err) {
    if (err instanceof ChatbotError) throw err;
    if (timedOut) {
      throw new ChatbotError("Le chatbot ne répond plus. Réessayez dans un instant.", "timeout");
    }
    if (controller.signal.aborted) {
      throw new ChatbotError("Requête annulée.", "cancelled");
    }
    throw new ChatbotError("Impossible de contacter le service chatbot.", "network");
  } finally {
    clearTimeout(idleTimer);
    signal?.removeEventListener("abort", onCallerAbort);
  }
};
