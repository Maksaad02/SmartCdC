import React, { useRef } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { FileDown, FileUp } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { ApiError, apiFetch, errorMessage } from "@/lib/apiClient";
import { joindreFacture, telechargerFacture, verifierFichierFacture } from "@/lib/factures";
import { creanceDocumentInfoSchema } from "@/schemas";

/**
 * Facture PDF d'origine d'une creance : telechargement si elle existe, sinon possibilite de la joindre.
 */
const FactureDocument: React.FC<{ numFacture: string }> = ({ numFacture }) => {
  const queryClient = useQueryClient();
  const input = useRef<HTMLInputElement>(null);
  const cle = ["/creances", numFacture, "document"];

  const infoQuery = useQuery({
    queryKey: cle,
    queryFn: async ({ signal }) => {
      try {
        return await apiFetch(`/creances/${encodeURIComponent(numFacture)}/document/info`, {
          schema: creanceDocumentInfoSchema,
          signal,
        });
      } catch (e) {
        // 404 : aucune facture jointe, ce n'est pas une erreur.
        if (e instanceof ApiError && e.status === 404) return null;
        throw e;
      }
    },
  });

  const telechargement = useMutation({
    mutationFn: () => telechargerFacture(numFacture),
    onSuccess: (blob) => {
      const url = URL.createObjectURL(blob);
      const lien = document.createElement("a");
      lien.href = url;
      lien.download = infoQuery.data?.nomFichier ?? `facture-${numFacture}.pdf`;
      lien.click();
      URL.revokeObjectURL(url);
    },
    onError: (err) => toast.error(errorMessage(err, "Téléchargement impossible")),
  });

  const envoi = useMutation({
    mutationFn: (fichier: File) => joindreFacture(numFacture, fichier),
    onSuccess: () => {
      toast.success("Facture PDF jointe");
      queryClient.invalidateQueries({ queryKey: cle });
    },
    onError: (err) => toast.error(errorMessage(err, "Impossible de joindre la facture")),
  });

  const choisir = (e: React.ChangeEvent<HTMLInputElement>) => {
    const fichier = e.target.files?.[0];
    e.target.value = "";
    if (!fichier) return;
    const erreur = verifierFichierFacture(fichier);
    if (erreur) toast.error(erreur);
    else envoi.mutate(fichier);
  };

  if (infoQuery.isLoading || infoQuery.isError) return null;

  return infoQuery.data ? (
    <Button variant="outline" onClick={() => telechargement.mutate()} disabled={telechargement.isPending}>
      <FileDown className="mr-2 h-4 w-4" /> Facture PDF
    </Button>
  ) : (
    <>
      <input ref={input} type="file" accept="application/pdf,.pdf" className="hidden" onChange={choisir}
             aria-label="Joindre la facture PDF" />
      <Button variant="outline" onClick={() => input.current?.click()} disabled={envoi.isPending}>
        <FileUp className="mr-2 h-4 w-4" /> {envoi.isPending ? "Envoi…" : "Joindre la facture PDF"}
      </Button>
    </>
  );
};

export default FactureDocument;
