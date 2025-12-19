import React, { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Link } from "react-router-dom";
import { Plus, Search, Download } from "lucide-react";
import StatusBadge, { StatusType } from "../components/StatusBadge";
import { formatDate, formatCurrency } from "../utils/formatters";
import { toast } from "sonner";
import { useAuth } from "../contexts/AuthContext";
import * as XLSX from "xlsx";
import { saveAs } from "file-saver";


interface Debt {
  numFacture: string;
  dateEmission?: string;
  echeance: string;
  montantFacture: number;
  montantEncaisse: number;
  montantPenalites: number;
  solde: number;
  joursRetard: number;
  statut: string;
  clientName?: string;
}

const Debts: React.FC = () => {
  const [debts, setDebts] = useState<Debt[]>([]);
  const [searchTerm, setSearchTerm] = useState("");
  const [statusFilter, setStatusFilter] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const { authToken } = useAuth();


  const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080/api";

  useEffect(() => {
    const fetchDebts = async () => {
      try {
        setLoading(true);
        const response = await fetch(`${API_URL}/creances`, {
          method: "GET",
          headers: {
            "Authorization": `Bearer ${authToken}`,
            "Content-Type": "application/json",
          }
        });
        if (!response.ok) {
          throw new Error(`Erreur ${response.status}`);
        }
        const data: Debt[] = await response.json();
        setDebts(data);
      } catch (error) {
        console.error("Erreur lors de la récupération des créances :", error);
        toast.error("Impossible de charger les créances.");
      } finally {
        setLoading(false);
      }
    };

    fetchDebts();
  }, [authToken]);

  const filteredDebts = debts.filter((debt) => {
    const searchLower = searchTerm.toLowerCase();
    const matchesSearch =
      debt.numFacture.toLowerCase().includes(searchLower) ||
      debt.clientName?.toLowerCase().includes(searchLower) ||
      formatCurrency(debt.montantFacture).includes(searchTerm);

    const matchesStatus = !statusFilter || debt.statut.toLowerCase() === statusFilter;
    return matchesSearch && matchesStatus;
  });

  const handleExportExcel = () => {
    const dataToExport = filteredDebts.map(({ numFacture, echeance, montantFacture, montantEncaisse, montantPenalites, solde, statut, clientName, joursRetard }) => ({
      "N° Facture": numFacture,
      "Échéance": echeance,
      "Client": clientName,
      "Montant Facturé (MAD)": montantFacture,
      "Montant Payé (MAD)": montantEncaisse,
      "Pénalités (MAD)": montantPenalites,
      "Solde (MAD)": solde,
      "Jours de retard": joursRetard,
      "Statut": statut,
    }));

    const worksheet = XLSX.utils.json_to_sheet(dataToExport);
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, worksheet, "Créances");
    const excelBuffer = XLSX.write(workbook, { bookType: "xlsx", type: "array" });
    const file = new Blob([excelBuffer], { type: "application/octet-stream" });
    saveAs(file, "creances.xlsx");
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight">Gestion des créances</h1>
        <Link to="/debts/new">
          <Button className="bg-debt-blue hover:bg-debt-lightBlue">
            <Plus className="mr-2 h-4 w-4" /> Nouvelle créance
          </Button>
        </Link>
      </div>

      <div className="rounded-lg border bg-card text-card-foreground shadow-sm">
        <div className="p-6">
          <h2 className="text-lg font-semibold">Liste des créances</h2>

          <div className="flex flex-col sm:flex-row justify-between items-center mt-4 space-y-4 sm:space-y-0">
            <div className="relative w-full sm:w-96">
              <Search className="absolute left-2 top-3 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder="Rechercher par n° facture ou client..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="pl-10"
              />
            </div>

            <div className="flex space-x-2">
              {/* Export Excel Button */}
              <Button
                className="bg-debt-blue hover:bg-debt-lightBlue text-white"
                onClick={handleExportExcel}
              >
                <Download className="w-4 h-4 mr-2" /> Export Excel
              </Button>

              <Button
                variant={!statusFilter ? "default" : "outline"}
                className={!statusFilter ? "bg-debt-blue hover:bg-debt-lightBlue" : ""}
                onClick={() => setStatusFilter(null)}
                size="sm"
              >
                Tous
              </Button>
              <Button
                variant={statusFilter === "payee" ? "default" : "outline"}
                className={statusFilter === "payee" ? "bg-status-paid hover:bg-status-paid/90" : ""}
                onClick={() => setStatusFilter("payee")}
                size="sm"
              >
                Payées
              </Button>
              <Button
                variant={statusFilter === "en_retard" ? "default" : "outline"}
                className={statusFilter === "en_retard" ? "bg-status-late hover:bg-status-late/90" : ""}
                onClick={() => setStatusFilter("en_retard")}
                size="sm"
              >
                En retard
              </Button>
              <Button
                variant={statusFilter === "penalisee" ? "default" : "outline"}
                className={statusFilter === "penalisee" ? "bg-red-600 hover:bg-red-700 text-white" : ""}
                onClick={() => setStatusFilter("penalisee")}
                size="sm"
              >
                Pénalisées
              </Button>
            </div>
          </div>

          <div className="mt-6 overflow-x-auto">
            {loading ? (
              <div className="flex justify-center py-8">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
              </div>
            ) : (
              <table className="w-full border-collapse">
                <thead className="bg-muted/50">
                  <tr>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">N° Facture</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Client</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Échéance</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Montant facturé</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Montant payé</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Pénalités</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Solde</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Retard (j)</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Statut</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {filteredDebts.map((debt) => {
                    const today = new Date();
                    const dueDate = new Date(debt.echeance);
                    const daysLate = today > dueDate
                      ? Math.floor((today.getTime() - dueDate.getTime()) / (1000 * 60 * 60 * 24))
                      : 0;

                    return (
                      <tr key={debt.numFacture} className="hover:bg-muted/50">
                        <td className="px-4 py-3 text-sm font-medium">{debt.numFacture}</td>
                        <td className="px-4 py-3 text-sm">{debt.clientName ?? "—"}</td>
                        <td className="px-4 py-3 text-sm">{formatDate(debt.echeance)}</td>
                        <td className="px-4 py-3 text-sm">{formatCurrency(debt.montantFacture)} MAD</td>
                        <td className="px-4 py-3 text-sm">{formatCurrency(debt.montantEncaisse)} MAD</td>
                        <td className="px-4 py-3 text-sm">
                          {debt.montantPenalites > 0 ? (
                            <span className="text-red-600 font-medium">
                              {formatCurrency(debt.montantPenalites)} MAD
                            </span>
                          ) : (
                            <span className="text-muted-foreground">—</span>
                          )}
                        </td>
                        <td className="px-4 py-3 text-sm font-medium">
                          {formatCurrency(debt.solde)} MAD
                        </td>
                        <td className="px-4 py-3 text-sm">
                          {debt.joursRetard > 0 ? (
                            <span className={debt.joursRetard >= 60 ? "text-red-600 font-medium" : "text-orange-600"}>
                              {debt.joursRetard}
                            </span>
                          ) : (
                            <span className="text-muted-foreground">—</span>
                          )}
                        </td>
                        <td className="px-4 py-3 text-sm">
                          <StatusBadge status={debt.statut as StatusType} />
                        </td>
                        <td className="px-4 py-3 text-sm">
                          <div className="flex space-x-2">
                            <Link to={`/debts/${debt.numFacture}/details`}>
                              <Button variant="outline" size="sm">Détails</Button>
                            </Link>
                            <Link to={`/reminders/new?debtId=${debt.numFacture}`}>
                              <Button variant="outline" size="sm">Relancer</Button>
                            </Link>
                          </div>
                        </td>
                      </tr>
                    );
                  })}

                  {filteredDebts.length === 0 && (
                    <tr>
                      <td colSpan={10} className="px-4 py-8 text-center text-muted-foreground">
                        Aucune créance trouvée
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default Debts;
