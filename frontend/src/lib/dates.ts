/**
 * Date locale au format AAAA-MM-JJ, telle qu'attendue par l'API.
 *
 * new Date().toISOString().split("T")[0] donne la date en UTC : entre minuit et 1 h
 * (Maroc, UTC+1) elle renvoie la veille, et les "relances d'aujourd'hui" etaient fausses.
 */
export const localDateIso = (date: Date = new Date()): string => {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
};
