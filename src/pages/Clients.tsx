import React, { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Link } from "react-router-dom";
import { Plus, Search } from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "../contexts/AuthContext";

interface Client {
  id: number;
  raisonSociale: string;
  email: string;
  telephone: string;
  adresse: string;
  rc?: string;
  identiteFiscale?: string;
  ice?: string;
  agentName?: string;
}

const Clients: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState("");
  const [clients, setClients] = useState<Client[]>([]);
  const [loading, setLoading] = useState(true);
  const { authToken } = useAuth();

  // Add token verification
  if (!authToken) {
    throw new Error("Authentication token missing");
  }

  const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080/api";

  useEffect(() => {
    const fetchClients = async () => {
      try {
        setLoading(true);
        const response = await fetch(`${API_URL}/clients`, {
          method: "GET",
          headers: {
            "Authorization": `Bearer ${authToken}`,
            "Content-Type": "application/json",
          }
        });
        if (!response.ok) {
          throw new Error(`Erreur ${response.status}: Impossible de récupérer les clients`);
        }
        const data: Client[] = await response.json();
        setClients(data);
      } catch (error) {
        console.error("Erreur lors de la récupération des clients :", error);
        toast.error("Erreur lors du chargement des clients");
      } finally {
        setLoading(false);
      }
    };

    fetchClients();
  }, []);

  const filteredClients = clients.filter(client => {
    const searchLower = searchTerm.toLowerCase();
    return (
      client.raisonSociale?.toLowerCase().includes(searchLower) ||
      client.email?.toLowerCase().includes(searchLower) ||
      client.telephone?.includes(searchTerm) ||
      client.adresse?.toLowerCase().includes(searchLower) ||
      client.identiteFiscale?.toLowerCase().includes(searchLower) ||
      client.ice?.toLowerCase().includes(searchLower)
    );
  });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight">Gestion des clients</h1>
        <Link to="/clients/new">
          <Button className="bg-debt-blue hover:bg-debt-lightBlue">
            <Plus className="mr-2 h-4 w-4" /> Nouveau client
          </Button>
        </Link>
      </div>

      <div className="rounded-lg border bg-card text-card-foreground shadow-sm">
        <div className="p-6">
          <h2 className="text-lg font-semibold">Liste des clients</h2>

          <div className="flex justify-between items-center mt-4">
            <div className="relative w-96">
              <Search className="absolute left-2 top-3 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder="Rechercher par nom, email, téléphone, adresse, identité fiscale ou ICE..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="pl-10"
              />
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
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Nom</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Email</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Téléphone</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">RC</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Identité Fiscale</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">ICE</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {filteredClients.map((client) => (
                    <tr key={client.id} className="hover:bg-muted/50">
                      <td className="px-4 py-3 text-sm">{client.raisonSociale}</td>
                      <td className="px-4 py-3 text-sm">{client.email}</td>
                      <td className="px-4 py-3 text-sm">{client.telephone}</td>
                      <td className="px-4 py-3 text-sm">{client.rc || "N/A"}</td>
                      <td className="px-4 py-3 text-sm">{client.identiteFiscale || "N/A"}</td>
                      <td className="px-4 py-3 text-sm">{client.ice || "N/A"}</td>
                      <td className="px-4 py-3 text-sm">
                        <div className="flex space-x-2">
                          <Link to={`/clients/${client.id}`}>
                            <Button variant="outline" size="sm">Détails</Button>
                          </Link>
                        </div>
                      </td>
                    </tr>
                  ))}

                  {filteredClients.length === 0 && !loading && (
                    <tr>
                      <td colSpan={7} className="px-4 py-8 text-center text-muted-foreground">
                        Aucun client trouvé
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

export default Clients;
