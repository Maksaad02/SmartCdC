import React, { useState, useEffect } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip, Legend } from "recharts";
import {
  ArrowUp,
  ArrowDown,
  BarChart,
  Calendar,
  AlertTriangle,
  PieChartIcon,
  DollarSign,
  Users
} from "lucide-react";
import { formatCurrency } from "../utils/formatters";
import { useAuth } from "../contexts/AuthContext";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

interface DashboardStats {
  totalDebts: number;
  totalAmount: number;
  paidAmount: number;
  recoveryRate: number;
  overdueDebts: number;
  todayReminders: number;
  debtsByStatus: {
    PAYEE: number;
    IMPAYEE: number;
    EN_RETARD: number;
    PARTIELLEMENT_PAYEE: number;
  };
}

interface Debt {
  numFacture: string;
  clientName: string;
  montantFacture: number;
  montantEncaisse: number;
  solde: number;
  statut: string;
  dateEmission: string;
  echeance: string;
}

interface Reminder {
  id: string;
  numFacture: string;
  dateRelance: string;
  typeRelance: string;
  statutRelance: string;
  debt?: {
    numFacture: string;
    clientName: string;
    montantFacture: number;
  };
}

const Dashboard: React.FC = () => {
  const { authToken, currentUser } = useAuth();
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [todayReminders, setTodayReminders] = useState<Reminder[]>([]);
  const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080/api";

  useEffect(() => {
    const fetchDashboardData = async () => {
      try {
        setLoading(true);

        const debtsResponse = await fetch(`${API_URL}/creances`, {
          headers: {
            "Authorization": `Bearer ${authToken}`,
            "Content-Type": "application/json",
          },
        });
        if (!debtsResponse.ok) throw new Error("Erreur fetch créances");
        const debts: Debt[] = await debtsResponse.json();

        const totalDebts = debts.length;
        const totalAmount = debts.reduce((sum, d) => sum + (d.montantFacture || 0), 0);
        const paidAmount = debts.reduce((sum, d) => sum + (d.montantEncaisse || 0), 0);
        const recoveryRate = totalAmount > 0 ? (paidAmount / totalAmount) * 100 : 0;
        const overdueDebts = debts.filter(d => d.statut === "EN_RETARD").length;

        const debtsByStatus = {
          PAYEE: debts.filter(d => d.statut === "PAYEE").length,
          IMPAYEE: debts.filter(d => d.statut === "IMPAYEE").length,
          EN_RETARD: debts.filter(d => d.statut === "EN_RETARD").length,
          PARTIELLEMENT_PAYEE: debts.filter(d => d.statut === "PARTIELLEMENT_PAYEE").length,
        };

        const debtsMap = new Map(debts.map(d => [d.numFacture, d]));
        const today = new Date().toISOString().split("T")[0];

        const remindersResponse = await fetch(`${API_URL}/relances`, {
          headers: {
            "Authorization": `Bearer ${authToken}`,
            "Content-Type": "application/json",
          },
        });
        if (!remindersResponse.ok) throw new Error("Erreur fetch relances");
        const remindersData: Reminder[] = await remindersResponse.json();

        const todayRemindersData = remindersData
          .filter(r => new Date(r.dateRelance).toISOString().split("T")[0] === today)
          .map(r => ({
            ...r,
            debt: debtsMap.get(r.numFacture) ? {
              numFacture: debtsMap.get(r.numFacture)!.numFacture,
              clientName: debtsMap.get(r.numFacture)!.clientName,
              montantFacture: debtsMap.get(r.numFacture)!.montantFacture
            } : undefined
          }));

        setStats({
          totalDebts,
          totalAmount,
          paidAmount,
          recoveryRate,
          overdueDebts,
          todayReminders: todayRemindersData.length,
          debtsByStatus,
        });

        setTodayReminders(todayRemindersData);
      } catch (error) {
        console.error(error);
        setStats({
          totalDebts: 0,
          totalAmount: 0,
          paidAmount: 0,
          recoveryRate: 0,
          overdueDebts: 0,
          todayReminders: 0,
          debtsByStatus: { PAYEE: 0, IMPAYEE: 0, EN_RETARD: 0, PARTIELLEMENT_PAYEE: 0 },
        });
        setTodayReminders([]);
      } finally {
        setLoading(false);
      }
    };

    if (authToken) fetchDashboardData();
    else setLoading(false);
  }, [authToken]);

  if (!authToken) return <p>Veuillez vous connecter pour accéder au tableau de bord</p>;
  if (loading || !stats) return <div className="flex justify-center items-center h-64 animate-spin rounded-full border-b-2 border-primary w-8 h-8"></div>;

  const chartData = [
    { name: "Payées", value: stats.debtsByStatus.PAYEE, color: "#0a977c" },
    { name: "Impayées", value: stats.debtsByStatus.IMPAYEE, color: "#1e3799" },
    { name: "En retard", value: stats.debtsByStatus.EN_RETARD, color: "#e67e22" },
    { name: "Partiellement payées", value: stats.debtsByStatus.PARTIELLEMENT_PAYEE, color: "#c23616" },
  ];

  return (
    <div className="space-y-6">

      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight">Tableau de bord</h1>
        <span className="text-sm text-muted-foreground">Bonjour, {currentUser?.name}</span>
      </div>

      {/* Statistiques */}
      <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">

        <Card>
          <CardHeader className="flex justify-between items-center pb-2">
            <CardTitle className="text-sm font-medium">Total des créances</CardTitle>
            <Users className="h-5 w-5 text-blue-500" />
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold">{stats.totalDebts}</div>
            <p className="text-xs text-muted-foreground mt-1">Nombre total de créances</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex justify-between items-center pb-2">
            <CardTitle className="text-sm font-medium">Montant total</CardTitle>
            <DollarSign className="h-5 w-5 text-green-500" />
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold">{formatCurrency(stats.totalAmount)} MAD</div>
            <p className="text-xs text-muted-foreground mt-1">Montant total des créances</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex justify-between items-center pb-2">
            <CardTitle className="text-sm font-medium">Montant encaissé</CardTitle>
            <ArrowUp className="h-5 w-5 text-green-500" />
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold">{formatCurrency(stats.paidAmount)} MAD</div>
            <p className="text-xs text-muted-foreground mt-1">
              <span className="inline-flex items-center text-green-500">
                <ArrowUp className="mr-1 h-3 w-3" />
                {((stats.paidAmount / stats.totalAmount) * 100).toFixed(1)}%
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
            <div className="text-3xl font-bold">{stats.recoveryRate.toFixed(1)} %</div>
            <p className="text-xs text-muted-foreground mt-1">Pourcentage des créances recouvrées</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex justify-between items-center pb-2">
            <CardTitle className="text-sm font-medium">Créances en retard</CardTitle>
            <AlertTriangle className="h-5 w-5 text-red-500" />
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold">{stats.overdueDebts}</div>
            <p className="text-xs text-muted-foreground mt-1">Nombre de créances en retard</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex justify-between items-center pb-2">
            <CardTitle className="text-sm font-medium">Relances du jour</CardTitle>
            <Calendar className="h-5 w-5 text-orange-500" />
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold">{stats.todayReminders}</div>
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

    </div>
  );
};

export default Dashboard;
