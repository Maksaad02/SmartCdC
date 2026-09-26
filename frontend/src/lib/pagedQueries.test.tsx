import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { z } from "zod";
import { buildQuery, fetchAllPages } from "./pagedQueries";

const fetchMock = vi.fn();
const item = z.object({ id: z.number() });

const page = (ids: number[], number: number, totalPages: number, totalElements: number) =>
  new Response(JSON.stringify({
    content: ids.map((id) => ({ id })),
    page: { size: 200, number, totalElements, totalPages },
  }), { status: 200 });

describe("buildQuery", () => {
  it("ignore les valeurs vides et encode les autres", () => {
    expect(buildQuery({ q: "société & fils", statut: undefined, page: 0, size: 20, x: "" }))
      .toBe("?q=soci%C3%A9t%C3%A9+%26+fils&page=0&size=20");
    expect(buildQuery({})).toBe("");
  });
});

describe("fetchAllPages", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockReset();
  });
  afterEach(() => vi.unstubAllGlobals());

  it("parcourt toutes les pages", async () => {
    fetchMock
      .mockResolvedValueOnce(page([1, 2], 0, 2, 3))
      .mockResolvedValueOnce(page([3], 1, 2, 3));

    const result = await fetchAllPages("/clients", item);

    expect(result.items.map((i) => i.id)).toEqual([1, 2, 3]);
    expect(result.truncated).toBe(false);
    expect(String(fetchMock.mock.calls[1][0])).toContain("page=1");
  });

  it("signale qu'une liste est tronquee au lieu de la presenter comme complete", async () => {
    fetchMock
      .mockResolvedValueOnce(page([1, 2], 0, 5, 10))
      .mockResolvedValueOnce(page([3, 4], 1, 5, 10));

    const result = await fetchAllPages("/clients", item, { maxItems: 3 });

    expect(result.truncated).toBe(true);
    expect(result.items).toHaveLength(3);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("rejette une reponse qui n'est pas une page (contrat modifie)", async () => {
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify([{ id: 1 }]), { status: 200 }));

    await expect(fetchAllPages("/clients", item)).rejects.toMatchObject({ kind: "invalid-response" });
  });
});
