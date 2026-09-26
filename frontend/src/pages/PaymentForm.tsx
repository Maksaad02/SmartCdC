import React, { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { formatCurrency } from "../utils/formatters";
import { toast } from "sonner";
import { useAuth } from "../contexts/AuthContext";
import { apiFetch, errorMessage } from "@/lib/apiClient";
import { fetchAllPages } from "@/lib/pagedQueries";
import { localDateIso } from "@/lib/dates";
import { creanceSchema, reglementSchema } from "@/schemas";

const PaymentForm = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const isEditing = !!id;
  const { currentUser } = useAuth();
  const [searchParams] = useSearchParams();
  const debtIdFromUrl = searchParams.get("debtId");

  const [formData, setFormData] = useState({
    debtId: debtIdFromUrl || "",
    montantEncaisse: "",
    dateReglement: localDateIso(),
    // Valeurs de l'enum backend (ModePaiement) : l'ancienne liste ("carte") produisait,
    // apres mise en majuscules, "CARTE" -- valeur inconnue du serveur.
    modePaiement: "VIREMENT",
    reference: "",
    statut: "NON_EFFECTUE"
  });

  // Creances a solder : toutes les pages (2000 maximum), puis filtre sur le solde restant.
  const debtsQuery = useQuery({
    queryKey: ["/creances", "options"],
    queryFn: ({ signal }) => fetchAllPages("/creances", creanceSchema, { maxItems: 2000, signal }),
  });
  useEffect(() => {
    if (debtsQuery.isError) toast.error("Impossible de charger les créances");
    if (debtsQuery.data?.truncated) {
      toast.warning("Plus de 2000 créances : la liste est tronquée. Filtrez depuis la page Créances.");
    }
  }, [debtsQuery.isError, debtsQuery.data?.truncated]);

  const debtsWithClients = useMemo(
    () =>
      (debtsQuery.data?.items ?? [])
        .map((debt) => ({
          ...debt,
          remaining: debt.solde,
          hasPenalties: debt.montantPenalites > 0,
          totalWithPenalties: debt.montantFacture + debt.montantPenalites,
        }))
        .filter((debt) => debt.remaining > 0),
    [debtsQuery.data],
  );

  // Creance pre-selectionnee par l'URL (?debtId=) : le montant par defaut (solde restant) doit etre
  // renseigne comme lors d'une selection manuelle, sinon le formulaire reste invalide.
  useEffect(() => {
    if (isEditing || !debtIdFromUrl) return;
    const debt = debtsWithClients.find((d) => d.numFacture === debtIdFromUrl);
    if (!debt) return;
    setFormData((prev) => (prev.montantEncaisse ? prev : { ...prev, montantEncaisse: debt.remaining.toString() }));
  }, [isEditing, debtIdFromUrl, debtsWithClients]);

  // Reglement a modifier : les champs renvoyes par l'API sont numFacture et montant
  // (le formulaire lisait debtId et montantEncaisse, inexistants : la modification ne
  // pre-remplissait rien).
  const paymentQuery = useQuery({
    queryKey: ["/reglements", id],
    queryFn: ({ signal }) => apiFetch(`/reglements/${id}`, { schema: reglementSchema, signal }),
    enabled: isEditing,
  });
  const loading = isEditing && paymentQuery.isLoading;

  useEffect(() => {
    const payment = paymentQuery.data;
    if (!payment) return;
    setFormData({
      debtId: payment.numFacture ?? "",
      montantEncaisse: payment.montant?.toString() ?? "",
      dateReglement: payment.dateReglement ?? localDateIso(),
      modePaiement: payment.modePaiement ?? "VIREMENT",
      reference: payment.reference || "",
      statut: payment.statut ?? "NON_EFFECTUE",
    });
  }, [paymentQuery.data]);

  useEffect(() => {
    if (paymentQuery.isError) {
      toast.error("Erreur lors du chargement du paiement");
      navigate("/payments");
    }
  }, [paymentQuery.isError, navigate]);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleSelectChange = (name: string, value: string) => {
    setFormData(prev => ({ ...prev, [name]: value }));

    // Creance choisie : le montant par defaut est le solde restant.
    if (name === "debtId") {
      const selectedDebt = debtsWithClients.find(d => d.numFacture === value);
      if (selectedDebt) {
        setFormData(prev => ({
          ...prev,
          debtId: value,
          montantEncaisse: selectedDebt.remaining.toString()
        }));
      }
    }
  };

  const saveMutation = useMutation({
    mutationFn: (payload: object) =>
      apiFetch(isEditing ? `/reglements/${id}` : "/reglements", {
        method: isEditing ? "PUT" : "POST",
        body: payload,
      }),
    onSuccess: () => {
      toast.success(isEditing ? "Règlement modifié avec succès" : "Règlement ajouté avec succès");
      // Un reglement change le montant encaisse, le statut et le solde de la creance.
      queryClient.invalidateQueries({ queryKey: ["/reglements"] });
      queryClient.invalidateQueries({ queryKey: ["/creances"] });
      queryClient.invalidateQueries({ queryKey: ["/dashboard/stats"] });
      navigate("/payments");
    },
    onError: (err) => toast.error(errorMessage(err, "Erreur lors de l'enregistrement du règlement")),
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    if (!formData.debtId || !formData.montantEncaisse || !formData.dateReglement || !formData.modePaiement) {
      toast.error("Veuillez remplir tous les champs obligatoires");
      return;
    }

    const selectedDebt = debtsWithClients.find(d => d.numFacture === formData.debtId);
    const paymentAmount = parseFloat(formData.montantEncaisse);

    // En modification, la creance peut etre deja soldee (donc absente de la liste) : le
    // serveur reste juge du montant.
    if (!selectedDebt && !isEditing) {
      toast.error("Veuillez sélectionner une créance valide");
      return;
    }

    if (!Number.isFinite(paymentAmount) || paymentAmount <= 0) {
      toast.error("Le montant du paiement doit être supérieur à 0");
      return;
    }

    if (selectedDebt && !isEditing && paymentAmount > selectedDebt.remaining) {
      let message = `Le montant du paiement ne peut pas dépasser le solde restant (${formatCurrency(selectedDebt.remaining)} MAD)`;
      if (selectedDebt.hasPenalties) {
        message += `\n\nDétail :\n- Montant facturé : ${formatCurrency(selectedDebt.montantFacture)} MAD\n- Pénalités : ${formatCurrency(selectedDebt.montantPenalites)} MAD\n- Total : ${formatCurrency(selectedDebt.totalWithPenalties)} MAD\n- Déjà payé : ${formatCurrency(selectedDebt.montantEncaisse)} MAD`;
      }
      toast.error(message);
      return;
    }

    saveMutation.mutate({
      numFacture: formData.debtId,
      montant: paymentAmount,
      dateReglement: formData.dateReglement,
      modePaiement: formData.modePaiement,
      // Reference vide : laissee vide plutot qu'un faux numero de virement genere au hasard.
      reference: formData.reference.trim() || undefined,
      agentName: currentUser?.name || undefined,
      statut: formData.statut,
    });
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight">
          {isEditing ? "Modifier le règlement" : "Nouveau règlement"}
        </h1>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Informations du règlement</CardTitle>
          <CardDescription>
            Remplissez les informations pour {isEditing ? "modifier" : "enregistrer"} un règlement
          </CardDescription>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="text-center py-8">Chargement...</div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-6">
              <div className="grid grid-cols-1 gap-6 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="debtId">Créance</Label>
                  <Select
                    value={formData.debtId}
                    onValueChange={(value) => handleSelectChange("debtId", value)}
                    disabled={isEditing}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="Sélectionner une créance" />
                    </SelectTrigger>
                    <SelectContent>
                      {debtsWithClients.map((debt) => (
                        <SelectItem key={debt.numFacture} value={debt.numFacture}>
                          {debt.numFacture} - {debt.clientName}
                          {debt.hasPenalties ? (
                            <span className="text-red-600">
                              {" "}({formatCurrency(debt.totalWithPenalties)} MAD incl. pénalités)
                            </span>
                          ) : (
                            <span>
                              {" "}({formatCurrency(debt.totalWithPenalties)} MAD)
                            </span>
                          )}
                          {" "}- Reste: {formatCurrency(debt.remaining)} MAD
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>

                  {/* Information sur la créance sélectionnée */}
                  {formData.debtId && (() => {
                    const selectedDebt = debtsWithClients.find(d => d.numFacture === formData.debtId);
                    if (selectedDebt) {
                      return (
                        <div className="mt-3 p-3 bg-gray-50 rounded-md text-sm">
                          <div className="font-medium mb-2">Détails de la créance :</div>
                          <div className="space-y-1 text-gray-600">
                            <div>Montant facturé : {formatCurrency(selectedDebt.montantFacture)} MAD</div>
                            {selectedDebt.hasPenalties && (
                              <div className="text-red-600">
                                Pénalités : +{formatCurrency(selectedDebt.montantPenalites)} MAD
                              </div>
                            )}
                            <div className="font-medium">
                              Total à payer : {formatCurrency(selectedDebt.totalWithPenalties)} MAD
                            </div>
                            <div className="text-blue-600">
                              Déjà payé : {formatCurrency(selectedDebt.montantEncaisse)} MAD
                            </div>
                            <div className="font-bold text-lg">
                              Reste à payer : {formatCurrency(selectedDebt.remaining)} MAD
                            </div>
                          </div>
                        </div>
                      );
                    }
                    return null;
                  })()}
                </div>

                <div className="space-y-2">
                  <Label htmlFor="montantEncaisse">Montant encaissé (MAD)</Label>
                  <Input
                    id="montantEncaisse"
                    name="montantEncaisse"
                    type="number"
                    min="0"
                    step="0.01"
                    value={formData.montantEncaisse}
                    onChange={handleInputChange}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="dateReglement">Date de règlement</Label>
                  <Input
                    id="dateReglement"
                    name="dateReglement"
                    type="date"
                    value={formData.dateReglement}
                    onChange={handleInputChange}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="modePaiement">Mode de paiement</Label>
                  <Select
                    value={formData.modePaiement}
                    onValueChange={(value) => handleSelectChange("modePaiement", value)}
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="VIREMENT">Virement</SelectItem>
                      <SelectItem value="CHEQUE">Chèque</SelectItem>
                      <SelectItem value="CARTE_BANCAIRE">Carte bancaire</SelectItem>
                      <SelectItem value="ESPECES">Espèces</SelectItem>
                      <SelectItem value="TRAITE">Traite</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="statut">Statut</Label>
                  <Select
                    value={formData.statut}
                    onValueChange={(value) => handleSelectChange("statut", value)}
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="NON_EFFECTUE">Non effectué</SelectItem>
                      <SelectItem value="EFFECTUE">Effectué</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="reference">Référence</Label>
                  <Input
                    id="reference"
                    name="reference"
                    placeholder="Référence du paiement (optionnel)"
                    value={formData.reference}
                    onChange={handleInputChange}
                  />
                </div>
              </div>

              <div className="flex justify-end space-x-4">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => navigate("/payments")}
                >
                  Annuler
                </Button>
                <Button type="submit" className="bg-debt-blue hover:bg-debt-lightBlue" disabled={saveMutation.isPending}>
                  {saveMutation.isPending ? "Enregistrement…" : `${isEditing ? "Modifier" : "Ajouter"} le règlement`}
                </Button>
              </div>
            </form>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

export default PaymentForm;
