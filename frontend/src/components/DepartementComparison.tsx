import React from "react";
import { useQuery } from "@tanstack/react-query";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Building2 } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { apiFetch } from "@/lib/apiClient";
import { dashboardDepartementsSchema } from "@/schemas";
import { formatCurrency } from "../utils/formatters";
import QueryError from "@/components/QueryError";

/**
 * Comparatif des performances des departements (ADMIN) : taux de recouvrement, encours et retards.
 * Une seule requete agregee cote serveur ; la ligne « Global » est la somme des departements.
 */
const DepartementComparison: React.FC = () => {
  const query = useQuery({
    queryKey: ["/dashboard/departements"],
    queryFn: ({ signal }) => apiFetch("/dashboard/departements", { schema: dashboardDepartementsSchema, signal }),
  });

  if (query.isError) {
    return <QueryError what="le comparatif des départements" error={query.error} onRetry={() => query.refetch()} />;
  }
  if (query.isLoading || !query.data) {
    return <div className="flex justify-center py-8"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" /></div>;
  }

  const { global, departements } = query.data;
  const lignes = [...departements, global];
  const graphique = departements.map((d) => ({ nom: d.nom, taux: Number(d.tauxRecouvrement.toFixed(1)) }));

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Building2 className="h-5 w-5" /> Performance par département
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-6">
        <div className="text-sm text-muted-foreground">
          Taux de recouvrement global : <span className="font-semibold text-foreground">{global.tauxRecouvrement.toFixed(1)} %</span>
        </div>

        {graphique.length > 0 && (
          <div className="h-64" aria-label="Taux de recouvrement par département">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={graphique}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="nom" />
                <YAxis unit=" %" domain={[0, 100]} />
                <Tooltip formatter={(value: number) => [`${value} %`, "Taux de recouvrement"]} />
                <Bar dataKey="taux" fill="#1e3799" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}

        <div className="overflow-x-auto rounded-md border">
          <table className="w-full border-collapse text-sm">
            <thead className="bg-muted/50">
              <tr>
                <th className="px-4 py-2 text-left font-medium text-muted-foreground">Département</th>
                <th className="px-4 py-2 text-right font-medium text-muted-foreground">Créances</th>
                <th className="px-4 py-2 text-right font-medium text-muted-foreground">En retard</th>
                <th className="px-4 py-2 text-right font-medium text-muted-foreground">Facturé</th>
                <th className="px-4 py-2 text-right font-medium text-muted-foreground">Encaissé</th>
                <th className="px-4 py-2 text-right font-medium text-muted-foreground">Solde</th>
                <th className="px-4 py-2 text-right font-medium text-muted-foreground">Taux</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {lignes.map((d, i) => (
                <tr key={d.departementId ?? "global"} className={i === lignes.length - 1 ? "bg-muted/30 font-semibold" : ""}>
                  <td className="px-4 py-2">{d.nom}</td>
                  <td className="px-4 py-2 text-right">{d.nbCreances}</td>
                  <td className="px-4 py-2 text-right">{d.nbEnRetard}</td>
                  <td className="px-4 py-2 text-right">{formatCurrency(d.montantFacture)} MAD</td>
                  <td className="px-4 py-2 text-right">{formatCurrency(d.montantEncaisse)} MAD</td>
                  <td className="px-4 py-2 text-right">{formatCurrency(d.solde)} MAD</td>
                  <td className="px-4 py-2 text-right">{d.tauxRecouvrement.toFixed(1)} %</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </CardContent>
    </Card>
  );
};

export default DepartementComparison;
