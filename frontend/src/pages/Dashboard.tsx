import React from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip, Legend } from "recharts";
import {
  ArrowUp,
  Calendar,
  AlertTriangle,
  PieChartIcon,
  DollarSign,
  Users
} from "lucide-react";
import { formatCurrency } from "../utils/formatters";
import { useAuth } from "../contexts/AuthContext";
import { apiFetch } from "@/lib/apiClient";
import { useTotalCount } from "@/lib/pagedQueries";
import { localDateIso } from "@/lib/dates";
import { relanceSchema } from "@/schemas";
import { z } from "zod";
import QueryError from "@/components/QueryError";
import DepartementComparison from "@/components/DepartementComparison";
import { useDepartements } from "@/lib/departements";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

/** Valeur de la liste deroulante pour la vue consolidee (aucun departement choisi). */
const VUE_GENERALE = "all";

// Agregats calcules par le serveur en base : le tableau de bord ne telecharge plus toutes
// les creances et toutes les relances pour les compter.
const statsSchema = z.object({
  totalCreances: z.number(),
  montantTotal: z.number(),
  montantEncaisse: z.number(),
  montantPenalites: z.number(),
  /** Pourcentage encaisse / (facture + penalites), calcule par le serveur (une seule definition). */
  tauxRecouvrement: z.number(),
  parStatut: z.record(z.number()),
});

const Dashboard: React.FC = () => {
  const { currentUser } = useAuth();
  const isAdmin = currentUser?.role === "admin";

  // ADMIN : departement choisi dans la liste deroulante, garde dans l'URL (?departement=ID) pour
  // survivre a un rechargement. Un identifiant inconnu retombe sur la vue generale.
  const [searchParams, setSearchParams] = useSearchParams();
  const departementsQuery = useDepartements(isAdmin);
  const departements = departementsQuery.data ?? [];
  const demande = isAdmin ? Number(searchParams.get("departement")) || undefined : undefined;
  const departementChoisi = departements.find((d) => d.id === demande);
  const departementId = departementChoisi?.id;
  // Tant que la liste n'est pas chargee, on ne sait pas si le departement demande existe.
  const selectionResolue = demande === undefined || departementsQuery.data !== undefined;

  const choisirDepartement = (valeur: string) => {
    setSearchParams((params) => {
      if (valeur === VUE_GENERALE) params.delete("departement");
      else params.set("departement", valeur);
      return params;
    }, { replace: true });
  };

  const statsQuery = useQuery({
    queryKey: ["/dashboard/stats", departementId ?? VUE_GENERALE],
    queryFn: ({ signal }) =>
      apiFetch(departementId ? `/dashboard/stats?departementId=${departementId}` : "/dashboard/stats", { schema: statsSchema, signal }),
    enabled: selectionResolue,
    // Changer de departement garde les chiffres affiches jusqu'a l'arrivee des nouveaux (pas de clignotement).
    placeholderData: keepPreviousData,
  });
  const todayReminders = useTotalCount("/relances", relanceSchema, {
    dateRelance: localDateIso(),
    ...(departementId ? { departementId } : {}),
  });

  // Une erreur reseau n'est plus affichee comme des zeros ("0 creance") : elle est signalee.
  if (statsQuery.isError) {
    return <QueryError what="le tableau de bord" error={statsQuery.error} onRetry={() => statsQuery.refetch()} />;
  }
  if (statsQuery.isLoading || !statsQuery.data) {
    return <div className="flex justify-center items-center h-64 animate-spin rounded-full border-b-2 border-primary w-8 h-8"></div>;
  }

  const s = statsQuery.data;
  const par = (statut: string) => s.parStatut[statut] ?? 0;
  const recoveryRate = s.tauxRecouvrement;
  // Une creance penalisee (60 jours de retard ou plus) est aussi une creance en retard.
  const overdueDebts = par("EN_RETARD") + par("PENALISEE");

  const chartData = [
    { name: "Payées", value: par("PAYEE"), color: "#0a977c" },
    { name: "Impayées", value: par("IMPAYEE"), color: "#1e3799" },
    { name: "En retard", value: par("EN_RETARD"), color: "#e67e22" },
    { name: "Pénalisées", value: par("PENALISEE"), color: "#c23616" },
    { name: "Partiellement payées", value: par("PARTIELLEMENT_PAYEE"), color: "#8e44ad" },
  ];

  return (
    <div className="space-y-6">

      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex flex-wrap items-center gap-4">
          <h1 className="text-2xl font-bold tracking-tight">
            Tableau de bord{departementChoisi ? ` — ${departementChoisi.nom}` : ""}
          </h1>
          {isAdmin && (
            <Select value={departementId ? String(departementId) : VUE_GENERALE} onValueChange={choisirDepartement}>
              <SelectTrigger aria-label="Département affiché" className="w-60">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={VUE_GENERALE}>Vue générale</SelectItem>
                {departements.map((d) => (
                  <SelectItem key={d.id} value={String(d.id)}>
                    {d.nom}{d.actif ? "" : " (désactivé)"}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        </div>
        <span className="text-sm text-muted-foreground">
          Bonjour, {currentUser?.name}
          {!isAdmin && currentUser?.departementNom ? ` — ${currentUser.departementNom}` : ""}
        </span>
      </div>

      {/* Statistiques */}
      <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">

        <Card>
          <CardHeader className="flex justify-between items-center pb-2">
            <CardTitle className="text-sm font-medium">Total des créances</CardTitle>
            <Users className="h-5 w-5 text-blue-500" />
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold">{s.totalCreances}</div>
            <p className="text-xs text-muted-foreground mt-1">Nombre total de créances</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex justify-between items-center pb-2">
            <CardTitle className="text-sm font-medium">Montant total</CardTitle>
            <DollarSign className="h-5 w-5 text-green-500" />
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold">{formatCurrency(s.montantTotal)} MAD</div>
            <p className="text-xs text-muted-foreground mt-1">Montant total des créances</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex justify-between items-center pb-2">
            <CardTitle className="text-sm font-medium">Montant encaissé</CardTitle>
            <ArrowUp className="h-5 w-5 text-green-500" />
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold">{formatCurrency(s.montantEncaisse)} MAD</div>
            <p className="text-xs text-muted-foreground mt-1">
              <span className="inline-flex items-center text-green-500">
                <ArrowUp className="mr-1 h-3 w-3" />
                {recoveryRate.toFixed(1)}%
              </span>{" "}
              Montant total encaissé
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex justify-between items-center pb-2">
            <CardTitle className="text-sm font-medium">Taux de recouvrement</CardTitle>
            <PieChartIcon className="h-5 w-5 text-purple-500" />
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold">{recoveryRate.toFixed(1)} %</div>
            <p className="text-xs text-muted-foreground mt-1">Pourcentage des créances recouvrées</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex justify-between items-center pb-2">
            <CardTitle className="text-sm font-medium">Créances en retard</CardTitle>
            <AlertTriangle className="h-5 w-5 text-red-500" />
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold">{overdueDebts}</div>
            <p className="text-xs text-muted-foreground mt-1">Nombre de créances en retard</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex justify-between items-center pb-2">
            <CardTitle className="text-sm font-medium">Relances du jour</CardTitle>
            <Calendar className="h-5 w-5 text-orange-500" />
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold">{(todayReminders.data ?? 0)}</div>
            <p className="text-xs text-muted-foreground mt-1">Relances à effectuer aujourd'hui</p>
          </CardContent>
        </Card>
      </div>

      {/* Pie chart */}
      <Card>
        <CardHeader>
          <CardTitle>Répartition des créances par statut</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="h-80">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={chartData}
                  cx="50%"
                  cy="50%"
                  outerRadius={100}
                  dataKey="value"
                  label={({ name, percent }) => `${name}: ${(percent * 100).toFixed(0)}%`}
                >
                  {chartData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={entry.color} />
                  ))}
                </Pie>
                <Tooltip formatter={(value) => [`${value} créances`, "Nombre"]} />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          </div>
        </CardContent>
      </Card>

      {/* Comparatif des departements : ADMIN uniquement (le serveur refuse tout autre role), en vue generale. */}
      {isAdmin && !departementId && <DepartementComparison />}

    </div>
  );
};

export default Dashboard;
