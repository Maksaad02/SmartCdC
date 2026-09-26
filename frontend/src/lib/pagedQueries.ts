/**
 * Acces aux listes paginees du backend (clients, creances, reglements, relances, utilisateurs).
 *
 * Les listes ne sont plus telechargees en entier puis filtrees dans le navigateur : la
 * recherche, le filtre et le tri sont appliques par le serveur, page par page (taille
 * bornee a 200 cote serveur). react-query apporte le cache, l'annulation des requetes
 * perimees et la nouvelle tentative sur erreur reseau.
 */
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { z } from "zod";
import { apiFetch } from "@/lib/apiClient";
import { pageSchema, type Page } from "@/schemas";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";

export const DEFAULT_PAGE_SIZE = 20;
const ALL_PAGES_SIZE = 200; // maximum accepte par le serveur

type Params = Record<string, string | number | undefined | null>;

export const buildQuery = (params: Params): string => {
  const qs = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== "") qs.set(key, String(value));
  }
  const s = qs.toString();
  return s ? `?${s}` : "";
};

interface UsePagedListOptions {
  /** Texte de recherche (le hook applique le delai de saisie). */
  q?: string;
  /** Filtres supplementaires : statut, numFacture... */
  params?: Params;
  size?: number;
  enabled?: boolean;
}

/** Liste paginee : { data, page, setPage, isLoading, isFetching, error, refetch }. */
export function usePagedList<S extends z.ZodTypeAny>(
  path: string,
  itemSchema: S,
  { q = "", params = {}, size = DEFAULT_PAGE_SIZE, enabled = true }: UsePagedListOptions = {},
) {
  const [page, setPage] = useState(0);
  const debouncedQ = useDebouncedValue(q.trim());
  const filterKey = JSON.stringify(params);

  // Un nouveau filtre ou une nouvelle recherche repart de la premiere page.
  useEffect(() => {
    setPage(0);
  }, [debouncedQ, filterKey]);

  const query = useQuery({
    queryKey: [path, { page, size, q: debouncedQ, params }],
    queryFn: ({ signal }) =>
      apiFetch<Page<z.infer<S>>>(`${path}${buildQuery({ ...params, q: debouncedQ, page, size })}`, {
        schema: pageSchema(itemSchema) as unknown as z.ZodType<Page<z.infer<S>>>,
        signal,
      }),
    placeholderData: keepPreviousData,
    enabled,
  });

  return {
    data: query.data,
    items: query.data?.content ?? [],
    page,
    setPage,
    isLoading: query.isLoading,
    isFetching: query.isFetching,
    error: query.error,
    refetch: query.refetch,
  };
}

/**
 * Charge TOUTES les pages (au plus maxItems elements). Sert aux listes deroulantes des
 * formulaires et aux exports : la ou l'on a besoin de l'ensemble, pas d'une page.
 * `truncated` indique que la limite a ete atteinte : l'appelant doit alors prevenir
 * l'utilisateur plutot que d'afficher silencieusement une liste incomplete.
 */
export async function fetchAllPages<S extends z.ZodTypeAny>(
  path: string,
  itemSchema: S,
  { params = {}, maxItems = 2000, signal }: { params?: Params; maxItems?: number; signal?: AbortSignal } = {},
): Promise<{ items: z.infer<S>[]; truncated: boolean }> {
  const items: z.infer<S>[] = [];
  let page = 0;
  for (;;) {
    const result = await apiFetch<Page<z.infer<S>>>(
      `${path}${buildQuery({ ...params, page, size: ALL_PAGES_SIZE })}`,
      { schema: pageSchema(itemSchema) as unknown as z.ZodType<Page<z.infer<S>>>, signal },
    );
    items.push(...result.content);
    const lastPage = page + 1 >= result.page.totalPages;
    if (lastPage) return { items, truncated: false };
    if (items.length >= maxItems) return { items: items.slice(0, maxItems), truncated: true };
    page += 1;
  }
}

/** Nombre total d'elements correspondant a des filtres, sans charger les elements (page de taille 1). */
export function useTotalCount<S extends z.ZodTypeAny>(path: string, itemSchema: S, params: Params = {}) {
  return useQuery({
    queryKey: [path, "count", params],
    queryFn: ({ signal }) =>
      apiFetch<Page<z.infer<S>>>(`${path}${buildQuery({ ...params, page: 0, size: 1 })}`, {
        schema: pageSchema(itemSchema) as unknown as z.ZodType<Page<z.infer<S>>>,
        signal,
      }),
    select: (result) => result.page.totalElements,
  });
}
