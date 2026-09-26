/**
 * Configuration d'execution du front, lue au BUILD par Vite.
 *
 * En production le front est servi par nginx qui relaie /api vers le backend
 * et /chat vers le service chatbot : les chemins relatifs suffisent et rien ne
 * pointe vers localhost. Les URLs absolues ne servent qu'au developpement
 * local (`npm run dev`), jamais dans un build de production.
 */

const isDev = import.meta.env.DEV;

/** Base de l'API backend (sans slash final). */
export const API_URL: string =
  import.meta.env.VITE_API_URL ?? (isDev ? "http://localhost:8080/api" : "/api");

/**
 * Base du service chatbot (sans slash final). Chaine vide = meme origine : les
 * appels partent vers /chat/..., relayes par nginx.
 */
export const CHATBOT_URL: string =
  import.meta.env.VITE_CHATBOT_API_URL ?? (isDev ? "http://localhost:8082" : "");
