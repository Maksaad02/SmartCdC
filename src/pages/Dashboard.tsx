import React, { useState, useEffect } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip, Legend } from "recharts";
import { ArrowUp, ArrowDown, BarChart, Calendar, AlertTriangle, PieChartIcon } from "lucide-react";
import { formatCurrency, formatDate } from "../utils/formatters";
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

  const API_URL = "http://34.226.195.59:8080/api";

  useEffect(() => {
    const fetchDashboardData = async () => {
      try {
        setLoading(true);
        
        // Fetch all debts first
        const debtsResponse = await fetch(`${API_URL}/creances`, {
          headers: {
            "Authorization": `Bearer ${authToken}`,
            "Content-Type": "application/json",
          },
        });

        if (!debtsResponse.ok) {
          throw new Error(`Error ${debtsResponse.status}: ${debtsResponse.statusText}`);
        }

        const debts: Debt[] = await debtsResponse.json();
        console.log("Debts received:", debts);

        // Calculate dashboard statistics
        const totalDebts = debts.length;
        const totalAmount = debts.reduce((sum, debt) => sum + (debt.montantFacture || 0), 0);
        const paidAmount = debts.reduce((sum, debt) => sum + (debt.montantEncaisse || 0), 0);
        const recoveryRate = totalAmount > 0 ? (paidAmount / totalAmount) * 100 : 0;
        const overdueDebts = debts.filter(debt => debt.statut === "EN_RETARD").length;

        // Calculate debts by status
        const debtsByStatus = {
          PAYEE: debts.filter(debt => debt.statut === "PAYEE").length,
          IMPAYEE: debts.filter(debt => debt.statut === "IMPAYEE").length,
          EN_RETARD: debts.filter(debt => debt.statut === "EN_RETARD").length,
          PARTIELLEMENT_PAYEE: debts.filter(debt => debt.statut === "PARTIELLEMENT_PAYEE").length,
        };

        // Create a map of debts for quick lookup
        const debtsMap = new Map(debts.map(debt => [debt.numFacture, debt]));

        // Fetch today's reminders
        const today = new Date().toISOString().split('T')[0];
        console.log("Fetching reminders for date:", today);

        const remindersResponse = await fetch(`${API_URL}/relances`, {
          headers: {
            "Authorization": `Bearer ${authToken}`,
            "Content-Type": "application/json",
          },
        });

        if (!remindersResponse.ok) {
          throw new Error(`Error ${remindersResponse.status}: ${remindersResponse.statusText}`);
        }

        const remindersData: Reminder[] = await remindersResponse.json();
        console.log("All reminders received:", remindersData);

        // Filter reminders for today and enrich with debt data
        const todayRemindersData = remindersData
          .filter(reminder => {
            const reminderDate = new Date(reminder.dateRelance).toISOString().split('T')[0];
            return reminderDate === today;
          })
          .map(reminder => {
            const debt = debtsMap.get(reminder.numFacture) as Debt | undefined;
            return {
              ...reminder,
              debt: debt ? {
                numFacture: debt.numFacture,
                clientName: debt.clientName,
                montantFacture: debt.montantFacture
              } : undefined
            };
          });

        console.log("Today's processed reminders:", todayRemindersData);

        // Set all the data
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
        console.error("Error fetching dashboard data:", error);
        // Set default values if the API call fails
        setStats({
          totalDebts: 0,
          totalAmount: 0,
          paidAmount: 0,
          recoveryRate: 0,
          overdueDebts: 0,
          todayReminders: 0,
          debtsByStatus: {
            PAYEE: 0,
            IMPAYEE: 0,
            EN_RETARD: 0,
            PARTIELLEMENT_PAYEE: 0
          }
        });
        setTodayReminders([]);
      } finally {
        setLoading(false);
      }
    };

    if (authToken) {
      fetchDashboardData();
    } else {
      setLoading(false);
    }
  }, [authToken]);

  // Add a check for authToken
  if (!authToken) {
    return (
      <div className="flex justify-center items-center h-64">
        <p className="text-muted-foreground">Please log in to view the dashboard</p>
      </div>
    );
  }

  if (loading || !stats) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
      </div>
    );
  }

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
        <div className="flex items-center space-x-2">
          <span className="text-sm text-muted-foreground">
            Bonjour, {currentUser?.name}
          </span>
        </div>
      </div>

      <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Total des créances</CardTitle>
            <BarChart className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold">{stats.totalDebts}</div>
            <p className="text-xs text-muted-foreground mt-1">
              Nombre total de créances
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Montant total</CardTitle>
            <div className="rounded-full bg-primary/10 p-1 text-primary">
              <BarChart className="h-4 w-4" />
            </div>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold">{formatCurrency(stats.totalAmount)} MAD</div>
            <p className="text-xs text-muted-foreground mt-1">
              Montant total des créances
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Montant encaissé</CardTitle>
            <div className="rounded-full bg-primary/10 p-1 text-primary">
              <BarChart className="h-4 w-4" />
            </div>
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
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Taux de recouvrement</CardTitle>
            <div className="rounded-full bg-primary/10 p-1 text-primary">
              <PieChartIcon className="h-4 w-4" />
            </div>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold">{stats.recoveryRate.toFixed(1)} %</div>
            <p className="text-xs text-muted-foreground mt-1">
              Pourcentage des créances recouvrées
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Créances en retard</CardTitle>
            <div className="rounded-full bg-primary/10 p-1 text-primary">
              <AlertTriangle className="h-4 w-4 text-amber-500" />
            </div>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold">{stats.overdueDebts}</div>
            <p className="text-xs text-muted-foreground mt-1">
              Nombre de créances en retard
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Relances du jour</CardTitle>
            <div className="rounded-full bg-primary/10 p-1 text-primary">
              <Calendar className="h-4 w-4" />
            </div>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold">{stats.todayReminders}</div>
            <p className="text-xs text-muted-foreground mt-1">
              Nombre de relances à effectuer aujourd'hui
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Table for today's reminders */}
      <div className="mt-6">
        <Card>
          <CardHeader>
            <CardTitle>Relances du jour</CardTitle>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Client</TableHead>
                  <TableHead>N° Facture</TableHead>
                  <TableHead>Montant</TableHead>
                  <TableHead>Type de relance</TableHead>
                  <TableHead>Statut</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {todayReminders.length > 0 ? (
                  todayReminders.map((reminder) => (
                    <TableRow key={reminder.id}>
                      <TableCell className="font-medium">
                        {reminder.debt?.clientName || "Client inconnu"}
                      </TableCell>
                      <TableCell>{reminder.debt?.numFacture || "N/A"}</TableCell>
                      <TableCell>{reminder.debt ? formatCurrency(reminder.debt.montantFacture) + " MAD" : "N/A"}</TableCell>
                      <TableCell>
                        <span className="capitalize">{reminder.typeRelance.replace("_", " ")}</span>
                      </TableCell>
                      <TableCell>
                        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${
                          reminder.statutRelance === "envoyee" ? "bg-green-100 text-green-800" : 
                          reminder.statutRelance === "en_attente" ? "bg-yellow-100 text-yellow-800" :
                          "bg-blue-100 text-blue-800"
                        }`}>
                          {reminder.statutRelance === "en_attente" ? "En attente" : 
                           reminder.statutRelance === "envoyee" ? "Envoyée" : "Répondue"}
                        </span>
                      </TableCell>
                    </TableRow>
                  ))
                ) : (
                  <TableRow>
                    <TableCell colSpan={5} className="text-center py-6 text-muted-foreground">
                      Aucune relance prévue pour aujourd'hui
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </div>

      <div className="mt-6">
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
                    labelLine={false}
                    outerRadius={100}
                    fill="#8884d8"
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
    </div>
  );
};

export default Dashboard;
