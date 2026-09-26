/**
 * Point unique de remontee des erreurs non gerees (rendu React, promesses
 * rejetees). Aujourd'hui : console.error, conservee en production (seuls
 * console.log/debug/info sont retires du build). C'est ici qu'on branchera un
 * service de suivi (Sentry) sans toucher aux composants.
 */
export const reportError = (error: unknown, context?: Record<string, unknown>): void => {
  console.error("Erreur non gérée :", error, context ?? "");
};
