import React, { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Link } from "react-router-dom";
import { formatDate } from "../utils/formatters";
import { Plus, Search, Filter, Check, CalendarDays } from "lucide-react";
import StatusBadge from "../components/StatusBadge";
import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";
import { toast } from "sonner";
import { useAuth } from "../contexts/AuthContext";
import { Debt } from "@/models/types";
// import { Reminder } from "@/models/types";

 interface Reminder {
   id: string;
  numFacture: string;
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
  dateRelance: Date;
  typeRelance: string;
  statutRelance: string;
  dateEcheance?: string;
  commentaire?: string;
  createdAt: Date;
}

const Reminders: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState("");
  const [reminders, setReminders] = useState<Reminder[]>([]);
  const [typeFilter, setTypeFilter] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<string | null>(null);
  const [todayFilter, setTodayFilter] = useState(false);
  const [loading, setLoading] = useState(true);
  const { authToken } = useAuth();

  const API_URL = "http://localhost:8080/api";
  
    useEffect(() => {
      const fetchReminders = async () => {
        try {
          setLoading(true);
          // First, fetch all reminders
          const response = await fetch(`${API_URL}/relances`, {
            method: "GET",
            headers: {
              "Authorization": `Bearer ${authToken}`,
              "Content-Type": "application/json",
            }
          });
          if (!response.ok) {
            throw new Error(`Erreur ${response.status}`);
          }
          const remindersData: Reminder[] = await response.json();
          
          // Then, fetch the associated debts for each reminder
          const remindersWithDebts = await Promise.all(
            remindersData.map(async (reminder) => {
              try {
                const debtResponse = await fetch(`${API_URL}/creances/${reminder.numFacture}`, {
                  headers: {
                    "Authorization": `Bearer ${authToken}`,
                    "Content-Type": "application/json",
                  }
                });
                if (debtResponse.ok) {
                  const debtData = await debtResponse.json();
                  return {
                    ...reminder,
                    creance: debtData
                  };
                }
                return reminder;
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
    switch (type) {
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
            
            {/* <div className="flex flex-wrap gap-2">
              <div>
                <Button
                  variant={!typeFilter ? "default" : "outline"}
                  className={!typeFilter ? "bg-debt-blue hover:bg-debt-lightBlue" : ""}
                  onClick={() => setTypeFilter(null)}
                  size="sm"
                >
                  Tous types
                </Button>
                <Button
                  variant={typeFilter === "email" ? "default" : "outline"}
                  className={typeFilter === "email" ? "bg-blue-500 hover:bg-blue-600" : ""}
                  onClick={() => setTypeFilter("email")}
                  size="sm"
                >
                  Email
                </Button>
                <Button
                  variant={typeFilter === "telephone" ? "default" : "outline"}
                  className={typeFilter === "telephone" ? "bg-orange-500 hover:bg-orange-600" : ""}
                  onClick={() => setTypeFilter("telephone")}
                  size="sm"
                >
                  Téléphone
                </Button>
                <Button
                  variant={typeFilter === "courrier" ? "default" : "outline"}
                  className={typeFilter === "courrier" ? "bg-purple-500 hover:bg-purple-600" : ""}
                  onClick={() => setTypeFilter("courrier")}
                  size="sm"
                >
                  Courrier
                </Button>
              </div>
              
              <div>
                <Button
                  variant={!statusFilter ? "default" : "outline"}
                  className={!statusFilter ? "bg-debt-blue hover:bg-debt-lightBlue" : ""}
                  onClick={() => setStatusFilter(null)}
                  size="sm"
                >
                  Tous statuts
                </Button>
                <Button
                  variant={statusFilter === "en_attente" ? "default" : "outline"}
                  className={statusFilter === "en_attente" ? "bg-gray-500 hover:bg-gray-600" : ""}
                  onClick={() => setStatusFilter("en_attente")}
                  size="sm"
                >
                  En attente
                </Button>
                <Button
                  variant={statusFilter === "envoyee" ? "default" : "outline"}
                  className={statusFilter === "envoyee" ? "bg-status-late hover:bg-status-late/90" : ""}
                  onClick={() => setStatusFilter("envoyee")}
                  size="sm"
                >
                  Envoyée
                </Button>
                <Button
                  variant={statusFilter === "repondue" ? "default" : "outline"}
                  className={statusFilter === "repondue" ? "bg-status-paid hover:bg-status-paid/90" : ""}
                  onClick={() => setStatusFilter("repondue")}
                  size="sm"
                >
                  Répondue
                </Button>
              </div>
            </div> */}
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
                  
                  return (
                    <tr key={reminder.id} className="hover:bg-muted/50">
                      <td className="px-4 py-3 text-sm">{clientName}</td>
                      <td className="px-4 py-3 text-sm">{reminder.numFacture}</td>
                      <td className="px-4 py-3 text-sm">
                        <Badge className={getTypeStyle(reminder.typeRelance)}>
                          {reminder.typeRelance === "email" ? "Email" : 
                           reminder.typeRelance === "telephone" ? "Téléphone" : 
                           reminder.typeRelance === "courrier" ? "Courrier" : reminder.typeRelance}
                        </Badge>
                      </td>
                      <td className="px-4 py-3 text-sm">{formatDate(reminder.dateRelance)}</td>
                      <td className="px-4 py-3 text-sm">{daysLate}</td>
                      <td className="px-4 py-3 text-sm">
                        <Badge className={getTypeStyle(reminder.statutRelance)}>
                          {reminder.statutRelance === "en_attente" ? "en_attente" : 
                           reminder.statutRelance === "envoyee" ? "envoyee" : 
                           reminder.statutRelance === "repondue" ? "repondue" : reminder.statutRelance}
                        </Badge>
                      </td>
                      <td className="px-4 py-3 text-sm">
                        <div className="flex space-x-2">
                          <Link to={`/reminders/${reminder.id}/details`}>
                            <Button variant="outline" size="sm">Détails</Button>
                          </Link>
                          {/* <Button variant="outline" size="sm" className="text-status-paid">
                            <Check className="h-4 w-4 mr-1" /> Marquer
                          </Button> */}
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
