import React from "react";
import { Button } from "@/components/ui/button";
import type { Page } from "@/schemas";

interface Props {
  data: Page<unknown> | undefined;
  onPageChange: (page: number) => void;
  isFetching?: boolean;
}

/** Barre "Precedent / Suivant" d'une liste paginee. */
const PaginationBar: React.FC<Props> = ({ data, onPageChange, isFetching }) => {
  if (!data || data.page.totalElements === 0) return null;
  const { number, totalPages, totalElements, size } = data.page;
  const first = number * size + 1;
  const last = Math.min((number + 1) * size, totalElements);

  return (
    <div className="mt-4 flex items-center justify-between text-sm text-muted-foreground">
      <span aria-live="polite">
        {first}–{last} sur {totalElements}
        {isFetching ? " · actualisation…" : ""}
      </span>
      <div className="flex items-center gap-2">
        <Button
          variant="outline"
          size="sm"
          disabled={number <= 0}
          onClick={() => onPageChange(number - 1)}
        >
          Précédent
        </Button>
        <span>
          Page {number + 1} / {Math.max(totalPages, 1)}
        </span>
        <Button
          variant="outline"
          size="sm"
          disabled={number + 1 >= totalPages}
          onClick={() => onPageChange(number + 1)}
        >
          Suivant
        </Button>
      </div>
    </div>
  );
};

export default PaginationBar;
