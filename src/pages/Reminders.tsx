import React, { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Link } from "react-router-dom";
import { formatDate } from "../utils/formatters";
import { Plus, Search, Filter, Check, CalendarDays, Send } from "lucide-react";
import StatusBadge from "../components/StatusBadge";
import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";
import { toast } from "sonner";
import { useAuth } from "../contexts/AuthContext";
import { Debt } from "@/models/types";

interface Reminder {
  id: number; // Changed from string to number to match backend
  numFacture: string; // This field exists in the backend response
  agentName?: string;
  dateRelance: string; // Changed from Date to string to match backend
  typeRelance: string;
  statutRelance: string;
  dateCreation?: string;
  dateEnvoi?: string;
  dateProgrammee?: string;
  message?: string;
  commentaire?: string;
  agentEnvoi?: string;
  creance?: {
    id: string;
    numFacture: string;
    dateEmission: string;
    echeance: string;
    montantFacture: number;
    montantEncaisse: number;
    solde: number;
    statut: string;
    clientName: string;
  };
}

const Reminders: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState("");
  const [reminders, setReminders] = useState<Reminder[]>([]);
  const [typeFilter, setTypeFilter] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<string | null>(null);
  const [todayFilter, setTodayFilter] = useState(false);
  const [loading, setLoading] = useState(true);
  const { authToken } = useAuth();

  const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080/api";

  useEffect(() => {
    const fetchReminders = async () => {
      try {
        setLoading(true);
        console.log("Fetching reminders...");

        // First, fetch all reminders
        const response = await fetch(`${API_URL}/relances`, {
          method: "GET",
          headers: {
            "Authorization": `Bearer ${authToken}`,
            "Content-Type": "application/json",
          }
        });

        if (!response.ok) {
          throw new Error(`Erreur ${response.status}: ${response.statusText}`);
        }

        const remindersData: Reminder[] = await response.json();
        console.log("Reminders data received:", remindersData);

        // Then, fetch the associated debts for each reminder
        const remindersWithDebts = await Promise.all(
          remindersData.map(async (reminder) => {
            try {
              console.log(`Fetching debt for reminder ${reminder.id}, numFacture: ${reminder.numFacture}`);

              const debtResponse = await fetch(`${API_URL}/creances/${reminder.numFacture}`, {
                headers: {
                  "Authorization": `Bearer ${authToken}`,
                  "Content-Type": "application/json",
                }
              });

              if (debtResponse.ok) {
                const debtData = await debtResponse.json();
                console.log(`Debt data for ${reminder.numFacture}:`, debtData);
                return {
                  ...reminder,
                  creance: debtData
                };
              } else {
                console.warn(`Failed to fetch debt for ${reminder.numFacture}: ${debtResponse.status}`);
                return reminder;
              }
            } catch (error) {
              console.error(`Error fetching debt for reminder ${reminder.id}:`, error);
              return reminder;
            }
          })
        );

        setReminders(remindersWithDebts);
      } catch (error) {
        console.error("Erreur lors de la récupération des relances:", error);
        toast.error("Impossible de charger les relances.");
      } finally {
        setLoading(false);
      }
    };

    fetchReminders();
  }, [authToken]);

  // Function to manually send a reminder
  const handleManualSend = async (reminderId: number) => {
    try {
      const response = await fetch(`${API_URL}/relances/${reminderId}/envoyer`, {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${authToken}`,
          "Content-Type": "application/json",
        }
      });

      if (response.ok) {
        toast.success("Relance envoyée avec succès");
        // Refresh the reminders list
        window.location.reload();
      } else {
        const errorText = await response.text();
        toast.error(errorText || "Erreur lors de l'envoi");
      }
    } catch (error) {
      console.error("Error sending reminder:", error);
      toast.error("Erreur lors de l'envoi");
    }
  };

  const filteredReminders = reminders.filter(reminder => {
    const searchLower = searchTerm.toLowerCase();

    // Apply search filter
    const matchesSearch =
      reminder.numFacture.toLowerCase().includes(searchLower);

    // Apply type filter
    const matchesType = !typeFilter || reminder.typeRelance === typeFilter;

    // Apply status filter
    const matchesStatus = !statusFilter || reminder.statutRelance === statusFilter;

    // Apply today filter
    const today = new Date();
    today.setHours(0, 0, 0, 0); // Set to start of day

    const reminderDate = new Date(reminder.dateRelance);
    reminderDate.setHours(0, 0, 0, 0); // Set to start of day

    const matchesToday = !todayFilter || today.getTime() === reminderDate.getTime();

    return matchesSearch && matchesType && matchesStatus && matchesToday;
  });

  const getTypeStyle = (type: string) => {
    switch (type.toLowerCase()) {
      case "email":
        return "bg-blue-100 text-blue-800";
      case "telephone":
        return "bg-orange-100 text-orange-800";
      case "courrier":
        return "bg-purple-100 text-purple-800";
      default:
        return "bg-gray-100 text-gray-800";
    }
  };

  const getStatusStyle = (status: string) => {
    switch (status.toLowerCase()) {
      case "en_attente":
        return "bg-yellow-100 text-yellow-800";
      case "envoyee":
        return "bg-blue-100 text-blue-800";
      case "effectuee":
        return "bg-green-100 text-green-800";
      case "annulee":
        return "bg-red-100 text-red-800";
      case "echec":
        return "bg-red-100 text-red-800";
      case "reportee":
        return "bg-orange-100 text-orange-800";
      default:
        return "bg-gray-100 text-gray-800";
    }
  };

  const getStatusDisplayName = (status: string) => {
    switch (status.toLowerCase()) {
      case "en_attente":
        return "En attente";
      case "envoyee":
        return "Envoyée";
      case "effectuee":
        return "Effectuée";
      case "annulee":
        return "Annulée";
      case "echec":
        return "Échec";
      case "reportee":
        return "Reportée";
      default:
        return status;
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-lg">Chargement des relances...</div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight">Gestion des relances</h1>
        <Link to="/reminders/new">
          <Button className="bg-debt-blue hover:bg-debt-lightBlue">
            <Plus className="mr-2 h-4 w-4" /> Nouvelle relance
          </Button>
        </Link>
      </div>

      <div className="rounded-lg border bg-card text-card-foreground shadow-sm">
        <div className="p-6">
          <h2 className="text-lg font-semibold">Liste des relances</h2>

          <div className="flex flex-col sm:flex-row justify-between items-center mt-4 gap-4">
            <div className="relative w-full sm:w-96">
              <Search className="absolute left-2 top-3 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder="Rechercher par n° facture ou client..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="pl-10"
              />
            </div>

            <div className="flex flex-col sm:flex-row items-center gap-3">
              <div className="flex items-center space-x-2">
                <Checkbox
                  id="today"
                  checked={todayFilter}
                  onCheckedChange={(checked) => setTodayFilter(checked as boolean)}
                />
                <label
                  htmlFor="today"
                  className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70 flex items-center"
                >
                  <CalendarDays className="h-4 w-4 mr-1" />
                  Relances d'aujourd'hui
                </label>
              </div>
            </div>
          </div>

          <div className="mt-6 overflow-x-auto">
            <table className="w-full border-collapse">
              <thead className="bg-muted/50">
                <tr>
                  <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Client</th>
                  <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">N° Facture</th>
                  <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Type</th>
                  <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Date relance</th>
                  <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Retard (j)</th>
                  <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Statut</th>
                  <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {filteredReminders.map((reminder) => {
                  const today = new Date();
                  const dueDate = reminder.creance?.echeance ? new Date(reminder.creance.echeance) : null;
                  const daysLate = dueDate && today > dueDate ?
                    Math.floor((today.getTime() - dueDate.getTime()) / (1000 * 60 * 60 * 24)) : 0;
                  const clientName = reminder.creance?.clientName || 'N/A';
                  const canSendManually = reminder.statutRelance === "EN_ATTENTE";

                  return (
                    <tr key={reminder.id} className="hover:bg-muted/50">
                      <td className="px-4 py-3 text-sm">{clientName}</td>
                      <td className="px-4 py-3 text-sm">{reminder.numFacture}</td>
                      <td className="px-4 py-3 text-sm">
                        <Badge className={getTypeStyle(reminder.typeRelance)}>
                          {reminder.typeRelance === "EMAIL" ? "Email" :
                            reminder.typeRelance === "TELEPHONE" ? "Téléphone" :
                              reminder.typeRelance === "COURRIER" ? "Courrier" : reminder.typeRelance}
                        </Badge>
                      </td>
                      <td className="px-4 py-3 text-sm">{formatDate(reminder.dateRelance)}</td>
                      <td className="px-4 py-3 text-sm">{daysLate}</td>
                      <td className="px-4 py-3 text-sm">
                        <Badge className={getStatusStyle(reminder.statutRelance)}>
                          {getStatusDisplayName(reminder.statutRelance)}
                        </Badge>
                      </td>
                      <td className="px-4 py-3 text-sm">
                        <div className="flex space-x-2">
                          <Link to={`/reminders/${reminder.id}/details`}>
                            <Button variant="outline" size="sm">Détails</Button>
                          </Link>
                          {canSendManually && (
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleManualSend(reminder.id)}
                              className="text-green-600 hover:text-green-700"
                            >
                              <Send className="h-4 w-4 mr-1" /> Envoyer
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}

                {filteredReminders.length === 0 && (
                  <tr>
                    <td colSpan={7} className="px-4 py-8 text-center text-muted-foreground">
                      Aucune relance trouvée
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

export default Reminders;
