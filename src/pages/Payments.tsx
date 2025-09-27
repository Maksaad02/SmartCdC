import React, { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Link } from "react-router-dom";
import { formatDate, formatCurrency } from "../utils/formatters";
import { Client } from "@/models/types";
import { Plus, Search } from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "../contexts/AuthContext";

interface Payment {
  id: number;
  montant: number;
  dateReglement: string;
  modePaiement: string;
  reference?: string;
  numFacture?: string;
  clientName?: string;
  agentRecouv?: {
    nom?: string;
  };
  statut: "EFFECTUE" | "NON_EFFECTUE";
}

const Payments: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState("");
  const [payments, setPayments] = useState<Payment[]>([]);
  const [clients, setClients] = useState<Client[]>([]);
  const [loading, setLoading] = useState(true);
  const { authToken } = useAuth();
  
  const API_URL = "http://34.226.195.59:8080/api";

  useEffect(() => {
    const fetchPayments = async () => {
      try {
        setLoading(true);
        
        if (!authToken) {
          throw new Error("Authentication token missing");
        }

        const response = await fetch(`${API_URL}/reglements`, {
          method: "GET",
          headers: {
            "Authorization": `Bearer ${authToken}`,
            "Content-Type": "application/json",
          }
        });

        if (!response.ok) {
          if (response.status === 403) {
            throw new Error("Access denied. Check your permissions.");
          }
          throw new Error(`Error ${response.status}: ${response.statusText}`);
        }

        const data = await response.json();
        setPayments(data);
      } catch (error) {
        console.error("Error fetching payments:", error);
        toast.error(
          error instanceof Error 
            ? error.message 
            : "Failed to load payments"
        );
      } finally {
        setLoading(false);
      }
    };

    fetchPayments();
  }, [authToken]);

  const filteredPayments = payments.filter(payment => {
    const searchLower = searchTerm.toLowerCase();
    return (
      (payment.numFacture?.toLowerCase().includes(searchLower) ||
      (payment.clientName?.toLowerCase().includes(searchLower)) ||
      (payment.reference?.toLowerCase().includes(searchLower)) ||
      payment.modePaiement.toLowerCase().includes(searchLower)
    ));
  });

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
      </div>
    );
  }

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
                placeholder="Rechercher par n° facture ou client..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="pl-10"
              />
            </div>
          </div>
          
          <div className="mt-6 overflow-x-auto">
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
                {filteredPayments.map((payment) => {
                  // Safe access with fallbacks
                  const numFacture = payment.numFacture || "-";
                  const raisonSociale = payment.clientName || "-";
                  
                  return (
                    <tr key={payment.id} className="hover:bg-muted/50">
                      <td className="px-4 py-3 text-sm">{numFacture}</td>
                      <td className="px-4 py-3 text-sm">{raisonSociale}</td>
                      <td className="px-4 py-3 text-sm">{formatDate(payment.dateReglement)}</td>
                      <td className="px-4 py-3 text-sm">{formatCurrency(payment.montant)} €</td>
                      <td className="px-4 py-3 text-sm">
                        <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${
                          payment.modePaiement === "virement"
                            ? "bg-green-100 text-green-800"
                            : payment.modePaiement === "cheque"
                            ? "bg-blue-100 text-blue-800"
                            : payment.modePaiement === "carte"
                            ? "bg-orange-100 text-orange-800"
                            : "bg-gray-100 text-gray-800"
                        }`}>
                          {payment.modePaiement === "virement"
                            ? "Virement"
                            : payment.modePaiement === "cheque"
                            ? "Chèque"
                            : payment.modePaiement === "carte"
                            ? "Carte"
                            : payment.modePaiement === "especes"
                            ? "Espèces"
                            : payment.modePaiement}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-sm">
                        <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${
                          payment.statut === "EFFECTUE"
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
                
                {filteredPayments.length === 0 && !loading && (
                  <tr>
                    <td colSpan={8} className="px-4 py-8 text-center text-muted-foreground">
                      {searchTerm ? "Aucun résultat trouvé" : "Aucun règlement disponible"}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Payments;