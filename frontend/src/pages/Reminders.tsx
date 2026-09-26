import React, { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Link } from "react-router-dom";
import { formatDate } from "../utils/formatters";
import { Plus, Search, CalendarDays, Send } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";
import { toast } from "sonner";
import { relanceSchema } from "@/schemas";
import { usePagedList } from "@/lib/pagedQueries";
import { apiFetch, errorMessage } from "@/lib/apiClient";
import { localDateIso } from "@/lib/dates";
import { REMINDER_STATUS_LABELS, REMINDER_TYPE_LABELS } from "@/lib/labels";
import PaginationBar from "@/components/PaginationBar";
import QueryError from "@/components/QueryError";

const Reminders: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState("");
  const [todayFilter, setTodayFilter] = useState(false);
  const queryClient = useQueryClient();

  // Filtre "aujourd'hui" applique par le serveur. Le client et le retard de chaque relance
  // sont fournis par l'API : plus d'appel supplementaire par ligne (un par relance auparavant).
  const { items: reminders, data, setPage, isLoading, isFetching, error, refetch } =
    usePagedList("/relances", relanceSchema, {
      q: searchTerm,
      params: { dateRelance: todayFilter ? localDateIso() : undefined },
    });

  const sendMutation = useMutation({
    mutationFn: (reminderId: number) =>
      apiFetch(`/relances/${reminderId}/envoyer`, { method: "POST", timeoutMs: 30_000 }),
    onSuccess: () => {
      toast.success("Relance envoyée avec succès");
      queryClient.invalidateQueries({ queryKey: ["/relances"] });
    },
    onError: (err) => toast.error(errorMessage(err, "Erreur lors de l'envoi")),
  });

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
                placeholder="Rechercher par n° facture ou commentaire..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="pl-10"
              />
            </div>

            <div className="flex items-center space-x-2">
              <Checkbox
                id="today"
                checked={todayFilter}
                onCheckedChange={(checked) => setTodayFilter(checked === true)}
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

          <div className="mt-6 overflow-x-auto">
            {error && !data ? (
              <QueryError what="les relances" error={error} onRetry={() => refetch()} />
            ) : isLoading ? (
              <div className="flex justify-center py-8">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
              </div>
            ) : (
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
                  {reminders.map((reminder) => {
                    const type = reminder.typeRelance ? REMINDER_TYPE_LABELS[reminder.typeRelance] : undefined;
                    const status = reminder.statutRelance ? REMINDER_STATUS_LABELS[reminder.statutRelance] : undefined;
                    const canSendManually = reminder.statutRelance === "EN_ATTENTE";
                    const sending = sendMutation.isPending && sendMutation.variables === reminder.id;

                    return (
                      <tr key={reminder.id} className="hover:bg-muted/50">
                        <td className="px-4 py-3 text-sm">{reminder.clientName || "N/A"}</td>
                        <td className="px-4 py-3 text-sm">{reminder.numFacture}</td>
                        <td className="px-4 py-3 text-sm">
                          <Badge className={type?.className ?? "bg-gray-100 text-gray-800"}>
                            {type?.label ?? reminder.typeRelance ?? "-"}
                          </Badge>
                        </td>
                        <td className="px-4 py-3 text-sm">{formatDate(reminder.dateRelance ?? undefined)}</td>
                        <td className="px-4 py-3 text-sm">{reminder.joursRetard}</td>
                        <td className="px-4 py-3 text-sm">
                          <Badge className={status?.className ?? "bg-gray-100 text-gray-800"}>
                            {status?.label ?? reminder.statutRelance ?? "-"}
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
                                disabled={sending}
                                onClick={() => sendMutation.mutate(reminder.id)}
                                className="text-green-600 hover:text-green-700"
                              >
                                <Send className="h-4 w-4 mr-1" /> {sending ? "Envoi…" : "Envoyer"}
                              </Button>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}

                  {reminders.length === 0 && (
                    <tr>
                      <td colSpan={7} className="px-4 py-8 text-center text-muted-foreground">
                        Aucune relance trouvée
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

export default Reminders;
