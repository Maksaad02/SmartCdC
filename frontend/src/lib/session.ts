/**
 * Renouvellement de la session a partir du cookie httpOnly de renouvellement.
 *
 * Le navigateur joint le cookie de lui-meme (meme origine, chemin /api/auth) : ce code ne le voit
 * jamais. L'en-tete X-Requested-With est exige par le serveur (protection CSRF : un autre site ne
 * peut pas l'ajouter a une requete).
 */
import { API_URL } from "@/lib/config";
import { setAccessToken } from "@/lib/authToken";
import { jwtResponseSchema, type JwtResponse } from "@/schemas";

let inflight: Promise<JwtResponse | null> | null = null;

const doRefresh = async (): Promise<JwtResponse | null> => {
  try {
    const response = await fetch(`${API_URL}/auth/refresh`, {
      method: "POST",
      headers: { "X-Requested-With": "XMLHttpRequest", Accept: "application/json" },
      credentials: "same-origin",
    });
    if (!response.ok) {
      setAccessToken(null);
      return null;
    }
    const parsed = jwtResponseSchema.safeParse(await response.json());
    if (!parsed.success) {
      setAccessToken(null);
      return null;
    }
    setAccessToken(parsed.data.token);
    return parsed.data;
  } catch {
    // Reseau coupe : on ne sait pas si la session est valide, on ne la detruit pas.
    return null;
  }
};

/**
 * Renouvelle la session. Une seule requete a la fois : plusieurs appels simultanes (dix requetes qui
 * recoivent 401 ensemble) partagent le meme renouvellement au lieu de le declencher dix fois, ce qui
 * ferait tourner le jeton de renouvellement et invaliderait les suivants.
 */
export const refreshSession = (): Promise<JwtResponse | null> => {
  if (!inflight) {
    inflight = doRefresh().finally(() => {
      inflight = null;
    });
  }
  return inflight;
};

/** Deconnexion cote serveur : revoque la session et efface le cookie. Sans effet bloquant si le reseau est coupe. */
export const logoutOnServer = async (): Promise<void> => {
  try {
    await fetch(`${API_URL}/auth/logout`, {
      method: "POST",
      headers: { "X-Requested-With": "XMLHttpRequest" },
      credentials: "same-origin",
    });
  } catch {
    // Le jeton d'acces expire de lui-meme en quelques minutes.
  }
};
