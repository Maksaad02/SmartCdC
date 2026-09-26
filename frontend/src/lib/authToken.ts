/**
 * Jeton d'acces courant, conserve UNIQUEMENT en memoire (variable de module).
 *
 * Il etait stocke dans localStorage, lisible par tout script de la page : une faille XSS suffisait
 * a le voler. En memoire, il disparait avec l'onglet ; la session se reprend ensuite grace au cookie
 * de renouvellement httpOnly (illisible depuis JavaScript), voir lib/session.ts.
 */
let accessToken: string | null = null;

export const getAccessToken = (): string | null => accessToken;

export const setAccessToken = (token: string | null): void => {
  accessToken = token;
};
