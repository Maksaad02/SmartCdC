import React from "react";
import { useParams, Link, useNavigate } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { formatDate } from "@/utils/formatters";
import { ArrowLeft, Mail, Phone, FileText, Building, Trash2 } from "lucide-react";
import { useAuth } from "@/contexts/AuthContext";
import { toast } from "sonner";
import StatusBadge, { StatusType } from "@/components/StatusBadge";
import { Badge } from "@/components/ui/badge";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { apiFetch, errorMessage } from "@/lib/apiClient";
import { creanceSchema, relanceSchema } from "@/schemas";
import { REMINDER_STATUS_LABELS, REMINDER_TYPE_LABELS } from "@/lib/labels";
import QueryError from "@/components/QueryError";

const typeIcon = (type: string | null | undefined) => {
  switch (type) {
    case "TELEPHONE":
      return <Phone className="h-5 w-5" />;
    case "COURRIER":
      return <FileText className="h-5 w-5" />;
    default:
      return <Mail className="h-5 w-5" />;
  }
};

const ReminderDetails: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { currentUser } = useAuth();

  const reminderQuery = useQuery({
    queryKey: ["/relances", id],
    queryFn: ({ signal }) => apiFetch(`/relances/${id}`, { schema: relanceSchema, signal }),
    enabled: !!id,
  });
  const reminder = reminderQuery.data ?? null;

  const debtQuery = useQuery({
    queryKey: ["/creances", reminder?.numFacture],
    queryFn: ({ signal }) =>
      apiFetch(`/creances/${encodeURIComponent(reminder?.numFacture ?? "")}`, { schema: creanceSchema, signal }),
    enabled: !!reminder?.numFacture,
  });
  const debt = debtQuery.data ?? null;

  const deleteMutation = useMutation({
    mutationFn: () => apiFetch(`/relances/${id}`, { method: "DELETE" }),
    onSuccess: () => {
      toast.success("Relance supprimée avec succès");
      queryClient.invalidateQueries({ queryKey: ["/relances"] });
      navigate("/reminders");
    },
    onError: (err) => toast.error(errorMessage(err, "Impossible de supprimer la relance")),
  });

  if (reminderQuery.isLoading) {
    return <div className="flex items-center justify-center h-96">Chargement…</div>;
  }

  if (reminderQuery.isError) {
    return <QueryError what="la relance" error={reminderQuery.error} onRetry={() => reminderQuery.refetch()} />;
  }

  if (!reminder) {
    return <div className="text-center">Relance introuvable</div>;
  }

  const type = reminder.typeRelance ? REMINDER_TYPE_LABELS[reminder.typeRelance] : undefined;
  const status = reminder.statutRelance ? REMINDER_STATUS_LABELS[reminder.statutRelance] : undefined;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Link to="/reminders">
            <Button variant="outline">
              <ArrowLeft className="h-4 w-4 mr-2" />
              Retour
            </Button>
          </Link>
          <h1 className="text-2xl font-bold tracking-tight">Détails de la relance</h1>
        </div>
        <div className="flex items-center gap-2">
          <Badge className={status?.className ?? "bg-gray-100 text-gray-800"}>{status?.label ?? "-"}</Badge>
          {/* Masque pour les non-ADMIN : le serveur refuse la suppression (403) de toute facon. */}
          {currentUser?.role === "admin" && (
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button variant="destructive" size="sm">
                  <Trash2 className="h-4 w-4 mr-2" />
                  Supprimer
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>Êtes-vous sûr de vouloir supprimer cette relance ?</AlertDialogTitle>
                  <AlertDialogDescription>
                    Cette action est irréversible. La relance sera définitivement supprimée.
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>Annuler</AlertDialogCancel>
                  <AlertDialogAction onClick={() => deleteMutation.mutate()} className="bg-red-600 hover:bg-red-700">
                    Supprimer
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          )}
        </div>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              {typeIcon(reminder.typeRelance)}
              Informations de la relance
            </CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="space-y-4">
              <div>
                <dt className="text-sm text-muted-foreground">Type</dt>
                <dd>
                  <Badge className={type?.className ?? "bg-gray-100 text-gray-800"}>
                    {type?.label ?? reminder.typeRelance ?? "-"}
                  </Badge>
                </dd>
              </div>
              <div>
                <dt className="text-sm text-muted-foreground">Statut</dt>
                <dd>
                  <Badge className={status?.className ?? "bg-gray-100 text-gray-800"}>{status?.label ?? "-"}</Badge>
                </dd>
              </div>
              <div>
                <dt className="text-sm text-muted-foreground">Date de relance</dt>
                <dd>{formatDate(reminder.dateRelance ?? undefined)}</dd>
              </div>
              {reminder.commentaire && (
                <div>
                  <dt className="text-sm text-muted-foreground">Commentaire</dt>
                  <dd className="whitespace-pre-wrap">{reminder.commentaire}</dd>
                </div>
              )}
            </dl>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Building className="h-5 w-5" />
              Créance concernée
            </CardTitle>
          </CardHeader>
          <CardContent>
            {debt ? (
              <dl className="space-y-4">
                <div>
                  <dt className="text-sm text-muted-foreground">N° de facture</dt>
                  <dd className="text-lg font-medium">
                    <Link to={`/debts/${debt.numFacture}/details`} className="text-primary hover:underline">
                      {debt.numFacture}
                    </Link>
                  </dd>
                </div>
                <div>
                  <dt className="text-sm text-muted-foreground">Client</dt>
                  <dd>{debt.clientName || "Client inconnu"}</dd>
                </div>
                <div>
                  <dt className="text-sm text-muted-foreground">Échéance</dt>
                  <dd>{formatDate(debt.echeance ?? undefined)}</dd>
                </div>
                <div>
                  <dt className="text-sm text-muted-foreground">Statut de la créance</dt>
                  <dd>
                    <StatusBadge status={debt.statut as StatusType} />
                  </dd>
                </div>
              </dl>
            ) : debtQuery.isLoading ? (
              <p className="text-muted-foreground">Chargement…</p>
            ) : (
              <p className="text-muted-foreground">Informations de créance indisponibles</p>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

export default ReminderDetails;
