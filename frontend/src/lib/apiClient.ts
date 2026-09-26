/**
 * Point d'entree unique des appels a l'API backend.
 *
 * Il remplace une trentaine de `fetch` copies dans les pages, qui oubliaient
 * tour a tour de verifier `response.ok` (un 403 s'affichait comme un succes),
 * n'avaient aucun delai maximal et ne reagissaient pas a l'expiration de la
 * session. Ici : jeton ajoute, delai, erreurs typees, deconnexion sur 401 et
 * validation de la reponse par un schema zod.
 */
import { z } from "zod";
import { API_URL } from "@/lib/config";
import { getAccessToken } from "@/lib/authToken";
import { refreshSession } from "@/lib/session";

/** Evenement emis quand le serveur rejette le jeton (401) : AuthContext deconnecte. */
export const AUTH_EXPIRED_EVENT = "smartcdc:auth-expired";

const DEFAULT_TIMEOUT_MS = 30_000;

export type ApiErrorKind =
  | "http"
  | "network"
  | "timeout"
  | "cancelled"
  | "invalid-response";

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly kind: ApiErrorKind,
    public readonly status: number = 0,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

interface RequestOptions<T> {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  /** Corps JSON : sera serialise. */
  body?: unknown;
  /** Schema de validation de la reponse ; sans schema, la reponse n'est pas verifiee. */
  schema?: z.ZodType<T>;
  signal?: AbortSignal;
  timeoutMs?: number;
  /** false pour les appels publics (login) : pas de jeton, pas de deconnexion sur 401. */
  authenticated?: boolean;
  /** Interne : vrai apres un renouvellement de session, pour ne retenter qu'une fois. */
  retried?: boolean;
}

const messageForStatus = (status: number): string => {
  switch (status) {
    case 400:
      return "Requête invalide.";
    case 401:
      return "Session expirée. Veuillez vous reconnecter.";
    case 403:
      return "Vous n'avez pas les droits pour effectuer cette action.";
    case 404:
      return "Ressource introuvable.";
    case 409:
      return "Conflit avec l'état actuel des données.";
    case 429:
      return "Trop de requêtes. Veuillez patienter un instant.";
    default:
      return status >= 500
        ? "Erreur du serveur. Veuillez réessayer."
        : `Erreur inattendue (${status}).`;
  }
};

/** Extrait le message metier renvoye par le backend ({message}|{error}|texte). */
const readErrorMessage = async (response: Response): Promise<string> => {
  const fallback = messageForStatus(response.status);
  // 401/403 : le message du serveur est generique, le notre est plus utile.
  if (response.status === 401 || response.status === 403 || response.status >= 500) {
    return fallback;
  }
  try {
    const text = await response.text();
    if (!text) return fallback;
    try {
      const json: unknown = JSON.parse(text);
      if (json && typeof json === "object") {
        const { message, error, champs } = json as { message?: unknown; error?: unknown; champs?: unknown };
        // Erreurs de validation : le backend detaille les champs fautifs ({champs: {nom: "message"}}).
        if (champs && typeof champs === "object") {
          const details = Object.values(champs as Record<string, unknown>).filter((v) => typeof v === "string");
          if (details.length > 0) return details.join(" ");
        }
        if (typeof message === "string" && message) return message;
        if (typeof error === "string" && error) return error;
      }
      if (typeof json === "string" && json) return json;
    } catch {
      // Pas du JSON : texte brut renvoye par certains controllers.
      return text.length <= 300 ? text : fallback;
    }
  } catch {
    // corps illisible
  }
  return fallback;
};

export async function apiFetch<T = unknown>(
  path: string,
  options: RequestOptions<T> = {},
): Promise<T> {
  const {
    method = "GET",
    body,
    schema,
    signal,
    timeoutMs = DEFAULT_TIMEOUT_MS,
    authenticated = true,
    retried = false,
  } = options;

  const headers: Record<string, string> = { Accept: "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";

  const token = authenticated ? getAccessToken() : null;
  if (token) headers["Authorization"] = `Bearer ${token}`;

  // Un seul controleur pour le delai maximal ET l'annulation de l'appelant.
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

  let response: Response;
  try {
    response = await fetch(`${API_URL}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal: controller.signal,
    });
  } catch {
    if (timedOut) {
      throw new ApiError("Le serveur met trop de temps à répondre.", "timeout");
    }
    if (controller.signal.aborted) {
      throw new ApiError("Requête annulée.", "cancelled");
    }
    throw new ApiError("Impossible de contacter le serveur.", "network");
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener("abort", onCallerAbort);
  }

  if (!response.ok) {
    if (response.status === 401 && authenticated) {
      // Jeton d'acces expire : on tente UN renouvellement (cookie httpOnly), puis on rejoue l'appel.
      // Seul un echec du renouvellement termine la session.
      if (!retried && (await refreshSession())) {
        return apiFetch<T>(path, { ...options, retried: true });
      }
      window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT));
    }
    throw new ApiError(await readErrorMessage(response), "http", response.status);
  }

  // 204 ou corps vide (DELETE, PUT sans retour) : rien a valider.
  const text = await response.text();
  if (!text) return undefined as T;

  let json: unknown;
  try {
    json = JSON.parse(text);
  } catch {
    // Reponse texte volontaire (ex: "Relance envoyée avec succès").
    json = text;
  }

  if (!schema) return json as T;

  const parsed = schema.safeParse(json);
  if (!parsed.success) {
    console.error("Réponse API inattendue pour", path, parsed.error.issues);
    throw new ApiError(
      "Le serveur a renvoyé une réponse inattendue.",
      "invalid-response",
      response.status,
    );
  }
  return parsed.data;
}

/** Message affichable pour n'importe quelle erreur capturee dans un composant. */
export const errorMessage = (err: unknown, fallback = "Une erreur est survenue."): string =>
  err instanceof Error && err.message ? err.message : fallback;
