import React from "react";
import { useParams, Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { formatDate, formatCurrency } from "@/utils/formatters";
import { ArrowLeft, CreditCard, Building, Calendar, Edit } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { apiFetch, errorMessage } from "@/lib/apiClient";
import { creanceSchema, reglementSchema } from "@/schemas";
import QueryError from "@/components/QueryError";

const METHODS: Record<string, { label: string; className: string }> = {
  VIREMENT: { label: "Virement", className: "bg-blue-100 text-blue-800" },
  CHEQUE: { label: "Chèque", className: "bg-purple-100 text-purple-800" },
  CARTE_BANCAIRE: { label: "Carte bancaire", className: "bg-green-100 text-green-800" },
  ESPECES: { label: "Espèces", className: "bg-yellow-100 text-yellow-800" },
  TRAITE: { label: "Traite", className: "bg-gray-100 text-gray-800" },
};

const PaymentDetails: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const queryClient = useQueryClient();

  const paymentQuery = useQuery({
    queryKey: ["/reglements", id],
    queryFn: ({ signal }) => apiFetch(`/reglements/${id}`, { schema: reglementSchema, signal }),
    enabled: !!id,
  });
  const payment = paymentQuery.data ?? null;

  // La creance concernee : l'API des reglements ne la joint pas, on la charge a part.
  const debtQuery = useQuery({
    queryKey: ["/creances", payment?.numFacture],
    queryFn: ({ signal }) =>
      apiFetch(`/creances/${encodeURIComponent(payment?.numFacture ?? "")}`, { schema: creanceSchema, signal }),
    enabled: !!payment?.numFacture,
  });
  const debt = debtQuery.data ?? null;

  // Le PATCH attend le statut brut en corps JSON ("EFFECTUE"), comme le declare le controller.
  const statusMutation = useMutation({
    mutationFn: (newStatus: "EFFECTUE" | "NON_EFFECTUE") =>
      apiFetch(`/reglements/${id}/status`, { method: "PATCH", body: newStatus, schema: reglementSchema }),
    onSuccess: (updated) => {
      queryClient.setQueryData(["/reglements", id], updated);
      // Le statut d'un reglement change le montant encaisse et le statut de la creance.
      queryClient.invalidateQueries({ queryKey: ["/creances"] });
      queryClient.invalidateQueries({ queryKey: ["/reglements"] });
      queryClient.invalidateQueries({ queryKey: ["/dashboard/stats"] });
      toast.success("Statut du paiement mis à jour avec succès");
    },
    onError: (err) => toast.error(errorMessage(err, "Impossible de mettre à jour le statut du paiement")),
  });

  if (paymentQuery.isLoading) {
    return <div className="flex items-center justify-center h-96">Chargement…</div>;
  }

  if (paymentQuery.isError) {
    return <QueryError what="le règlement" error={paymentQuery.error} onRetry={() => paymentQuery.refetch()} />;
  }

  if (!payment) {
    return <div className="text-center">Règlement introuvable</div>;
  }

  const method = payment.modePaiement ? METHODS[payment.modePaiement] : undefined;
  const newStatus = payment.statut === "EFFECTUE" ? "NON_EFFECTUE" : "EFFECTUE";

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Link to="/payments">
            <Button variant="outline">
              <ArrowLeft className="h-4 w-4 mr-2" />
              Retour
            </Button>
          </Link>
          <h1 className="text-2xl font-bold tracking-tight">Détails du règlement</h1>
        </div>
        <Link to={`/payments/${id}`}>
          <Button variant="outline" size="sm">
            <Edit className="h-4 w-4 mr-2" />
            Modifier
          </Button>
        </Link>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <CreditCard className="h-5 w-5" />
              Informations du règlement
            </CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="space-y-4">
              <div>
                <dt className="text-sm text-muted-foreground">Montant</dt>
                <dd className="text-2xl font-bold">{formatCurrency(payment.montant ?? 0)} MAD</dd>
              </div>
              <div>
                <dt className="text-sm text-muted-foreground">Mode de paiement</dt>
                <dd>
                  <Badge className={method?.className ?? "bg-gray-100 text-gray-800"}>
                    {method?.label ?? payment.modePaiement ?? "-"}
                  </Badge>
                </dd>
              </div>
              <div>
                <dt className="text-sm text-muted-foreground">Statut</dt>
                <dd className="flex items-center gap-2">
                  <Badge className={payment.statut === "EFFECTUE" ? "bg-green-100 text-green-800" : "bg-yellow-100 text-yellow-800"}>
                    {payment.statut === "EFFECTUE" ? "Effectué" : "Non effectué"}
                  </Badge>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={statusMutation.isPending}
                    onClick={() => statusMutation.mutate(newStatus)}
                    className="ml-2"
                  >
                    {payment.statut === "EFFECTUE" ? "Marquer comme non effectué" : "Marquer comme effectué"}
                  </Button>
                </dd>
              </div>
              <div>
                <dt className="text-sm text-muted-foreground">Date de règlement</dt>
                <dd className="flex items-center gap-2">
                  <Calendar className="h-4 w-4 text-muted-foreground" />
                  {formatDate(payment.dateReglement ?? undefined)}
                </dd>
              </div>
              {payment.reference && (
                <div>
                  <dt className="text-sm text-muted-foreground">Référence</dt>
                  <dd>{payment.reference}</dd>
                </div>
              )}
            </dl>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Building className="h-5 w-5" />
              Facture concernée
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
                  <dd className="text-primary">{debt.clientName || "Client inconnu"}</dd>
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <dt className="text-sm text-muted-foreground">Montant facturé</dt>
                    <dd>{formatCurrency(debt.montantFacture)} MAD</dd>
                  </div>
                  <div>
                    <dt className="text-sm text-muted-foreground">Solde restant</dt>
                    <dd>{formatCurrency(debt.solde)} MAD</dd>
                  </div>
                </div>
                <div>
                  <dt className="text-sm text-muted-foreground">Échéance</dt>
                  <dd>{formatDate(debt.echeance ?? undefined)}</dd>
                </div>
              </dl>
            ) : debtQuery.isLoading ? (
              <p className="text-muted-foreground">Chargement…</p>
            ) : (
              <p className="text-muted-foreground">Informations de facture indisponibles</p>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

export default PaymentDetails;
