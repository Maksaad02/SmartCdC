/**
 * Lit la date d'expiration (claim `exp`, en secondes) d'un JWT, sans verifier sa
 * signature : c'est le serveur qui fait foi, ceci sert uniquement a prevenir
 * l'utilisateur et a nettoyer la session quand le jeton arrive a echeance.
 */
export const getTokenExpiryMs = (token: string): number | null => {
  try {
    const payload = token.split(".")[1];
    if (!payload) return null;
    const base64 = payload.replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64 + "=".repeat((4 - (base64.length % 4)) % 4);
    const claims: unknown = JSON.parse(atob(padded));
    if (claims && typeof claims === "object" && "exp" in claims) {
      const exp = (claims as { exp: unknown }).exp;
      if (typeof exp === "number") return exp * 1000;
    }
    return null;
  } catch {
    return null;
  }
};
