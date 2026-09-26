import React, { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Link } from "react-router-dom";
import { Plus, Search } from "lucide-react";
import { clientSchema } from "@/schemas";
import { usePagedList } from "@/lib/pagedQueries";
import PaginationBar from "@/components/PaginationBar";
import QueryError from "@/components/QueryError";

const Clients: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState("");
  // Recherche et pagination sont faites par le serveur : la liste n'est plus telechargee en entier.
  const { items: clients, data, setPage, isLoading, isFetching, error, refetch } =
    usePagedList("/clients", clientSchema, { q: searchTerm });

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
                placeholder="Rechercher par nom, email, téléphone ou ICE..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="pl-10"
              />
            </div>
          </div>

          <div className="mt-6 overflow-x-auto">
            {error && !data ? (
              <QueryError what="les clients" error={error} onRetry={() => refetch()} />
            ) : isLoading ? (
              <div className="flex justify-center py-8">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
              </div>
            ) : (
              <table className="w-full border-collapse">
                <thead className="bg-muted/50">
                  <tr>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Nom</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Département</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Email</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Téléphone</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">RC</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Identité Fiscale</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">ICE</th>
                    <th className="px-4 py-3 text-left text-sm font-medium text-muted-foreground">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {clients.map((client) => (
                    <tr key={client.id} className="hover:bg-muted/50">
                      <td className="px-4 py-3 text-sm">{client.raisonSociale}</td>
                      <td className="px-4 py-3 text-sm">{client.departementNom ?? "—"}</td>
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

                  {clients.length === 0 && (
                    <tr>
                      <td colSpan={8} className="px-4 py-8 text-center text-muted-foreground">
                        Aucun client trouvé
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

export default Clients;
