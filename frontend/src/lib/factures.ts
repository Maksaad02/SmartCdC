import { useQuery } from "@tanstack/react-query";
import { z } from "zod";
import { apiFetch } from "./apiClient";
import { creanceDocumentInfoSchema, factureExtractionSchema } from "@/schemas";

/** Taille maximale acceptee par le serveur (10 Mo) : verifiee aussi ici pour un message immediat. */
export const TAILLE_MAX_FACTURE = 10 * 1024 * 1024;

const formulaire = (fichier: File) => {
  const data = new FormData();
  data.append("fichier", fichier);
  return data;
};

/** Message d'erreur local, ou null si le fichier peut etre envoye. */
export function verifierFichierFacture(fichier: File): string | null {
  if (fichier.type !== "application/pdf" && !fichier.name.toLowerCase().endsWith(".pdf")) {
    return "Choisissez un fichier PDF.";
  }
  if (fichier.size > TAILLE_MAX_FACTURE) return "Fichier trop volumineux (10 Mo maximum).";
  return null;
}

/** L'import automatique n'est propose que si le serveur a une cle d'API configuree. */
export function useExtractionActive(enabled = true) {
  return useQuery({
    queryKey: ["/factures/extraction/statut"],
    queryFn: ({ signal }) =>
      apiFetch("/factures/extraction/statut", { schema: z.object({ actif: z.boolean() }), signal }),
    enabled,
    staleTime: 5 * 60_000,
    select: (r) => r.actif,
  });
}

/** Lecture de la facture par le serveur (plusieurs secondes : appel au modele d'IA). */
export const extraireFacture = (fichier: File) =>
  apiFetch("/factures/extraction", {
    method: "POST",
    body: formulaire(fichier),
    schema: factureExtractionSchema,
    timeoutMs: 180_000,
  });

export const joindreFacture = (numFacture: string, fichier: File) =>
  apiFetch(`/creances/${encodeURIComponent(numFacture)}/document`, {
    method: "POST",
    body: formulaire(fichier),
    schema: creanceDocumentInfoSchema,
    timeoutMs: 120_000,
  });

export const telechargerFacture = (numFacture: string) =>
  apiFetch<Blob>(`/creances/${encodeURIComponent(numFacture)}/document`, { responseType: "blob", timeoutMs: 120_000 });
