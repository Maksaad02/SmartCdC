import React, { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Link } from "react-router-dom";
import { formatDate, formatCurrency } from "../utils/formatters";
import { Plus, Search } from "lucide-react";
import { reglementSchema } from "@/schemas";
import { usePagedList } from "@/lib/pagedQueries";
import PaginationBar from "@/components/PaginationBar";
import QueryError from "@/components/QueryError";

// L'API renvoie les modes de paiement en MAJUSCULES : l'ancienne comparaison avec
// "virement", "cheque"... ne correspondait jamais et affichait la valeur brute.
const MODE_LABELS: Record<string, { label: string; className: string }> = {
  VIREMENT: { label: "Virement", className: "bg-green-100 text-green-800" },
  CHEQUE: { label: "Chèque", className: "bg-blue-100 text-blue-800" },
  CARTE_BANCAIRE: { label: "Carte bancaire", className: "bg-orange-100 text-orange-800" },
  ESPECES: { label: "Espèces", className: "bg-gray-100 text-gray-800" },
  TRAITE: { label: "Traite", className: "bg-purple-100 text-purple-800" },
};

const Payments: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState("");
  const { items: payments, data, setPage, isLoading, isFetching, error, refetch } =
    usePagedList("/reglements", reglementSchema, { q: searchTerm });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight">Gestion des règlements</h1>
        <Link to="/payments/new">
          <Button className="bg-debt-blue hover:bg-debt-lightBlue">
            <Plus className="mr-2 h-4 w-4" /> Nouveau règlement
          </Button>
        </Link>
      </div>

      <div className="rounded-lg border bg-card text-card-foreground shadow-sm">
        <div className="p-6">
          <h2 className="text-lg font-semibold">Liste des règlements</h2>

          <div className="flex justify-between items-center mt-4">
            <div className="relative w-96">
              <Search className="absolute left-2 top-3 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder="Rechercher par n° facture, client ou référence..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="pl-10"
              />
            </div>
          </div>

          <div className="mt-6 overflow-x-auto">
            {error && !data ? (
              <QueryError what="les règlements" error={error} onRetry={() => refetch()} />
            ) : isLoading ? (
              <div className="flex justify-center py-8">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
              </div>
            ) : (
              <table className="w-full border-collapse">
                <thead className="bg-muted/50">
                  <tr>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">N° Facture</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Client</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Date</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Montant</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Mode</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Statut</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Référence</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {payments.map((payment) => {
                    const mode = payment.modePaiement ? MODE_LABELS[payment.modePaiement] : undefined;
                    return (
                      <tr key={payment.id} className="hover:bg-muted/50">
                        <td className="px-4 py-3 text-sm">{payment.numFacture || "-"}</td>
                        <td className="px-4 py-3 text-sm">{payment.clientName || "-"}</td>
                        <td className="px-4 py-3 text-sm">{formatDate(payment.dateReglement ?? undefined)}</td>
                        <td className="px-4 py-3 text-sm">{formatCurrency(payment.montant ?? 0)} MAD</td>
                        <td className="px-4 py-3 text-sm">
                          <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${mode?.className ?? "bg-gray-100 text-gray-800"}`}>
                            {mode?.label ?? payment.modePaiement ?? "-"}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-sm">
                          <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${payment.statut === "EFFECTUE"
                              ? "bg-green-100 text-green-800"
                              : "bg-yellow-100 text-yellow-800"
                            }`}>
                            {payment.statut === "EFFECTUE" ? "Effectué" : "Non effectué"}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-sm">{payment.reference || "-"}</td>
                        <td className="px-4 py-3 text-sm">
                          <Link to={`/payments/${payment.id}/details`}>
                            <Button variant="outline" size="sm">Détails</Button>
                          </Link>
                        </td>
                      </tr>
                    );
                  })}

                  {payments.length === 0 && (
                    <tr>
                      <td colSpan={8} className="px-4 py-8 text-center text-muted-foreground">
                        {searchTerm ? "Aucun résultat trouvé" : "Aucun règlement disponible"}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            )}
          </div>

          <PaginationBar data={data} onPageChange={setPage} isFetching={isFetching} />
        </div>
      </div>
    </div>
  );
};

export default Payments;
