import { useQuery } from "@tanstack/react-query";
import { z } from "zod";
import { apiFetch } from "./apiClient";
import { departementSchema } from "@/schemas";

/**
 * Departements visibles de l'appelant : tous pour un ADMIN, uniquement le sien pour un MANAGER ou un
 * AGENT (le serveur decide). Sert aux listes deroulantes ; la cle ["/departements"] est invalidee par
 * la page de gestion des departements.
 */
export function useDepartements(enabled = true) {
  return useQuery({
    queryKey: ["/departements"],
    queryFn: ({ signal }) => apiFetch("/departements", { schema: z.array(departementSchema), signal }),
    enabled,
  });
}
