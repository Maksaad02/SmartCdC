import React from "react";
import { Button } from "@/components/ui/button";
import { errorMessage } from "@/lib/apiClient";

interface Props {
  error: unknown;
  onRetry?: () => void;
  /** Ce qu'on essayait de charger, ex. "les clients". */
  what: string;
}

/**
 * Erreur de chargement visible avec un bouton "Reessayer". Remplace les catch qui
 * affichaient une liste vide ("Aucun client") ou des zeros : une erreur reseau ne doit
 * pas ressembler a une base vide.
 */
const QueryError: React.FC<Props> = ({ error, onRetry, what }) => (
  <div role="alert" className="rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-800">
    <p className="font-medium">Impossible de charger {what}.</p>
    <p className="mt-1">{errorMessage(error)}</p>
    {onRetry && (
      <Button variant="outline" size="sm" className="mt-3" onClick={onRetry}>
        Réessayer
      </Button>
    )}
  </div>
);

export default QueryError;
