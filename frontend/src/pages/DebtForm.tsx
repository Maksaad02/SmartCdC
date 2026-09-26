import React, { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
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
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Info } from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "../contexts/AuthContext";
import { apiFetch, errorMessage } from "@/lib/apiClient";
import { fetchAllPages } from "@/lib/pagedQueries";
import { localDateIso } from "@/lib/dates";
import { clientSchema, creanceSchema } from "@/schemas";
import { formatCurrency } from "../utils/formatters";

const DebtForm = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const isEditing = !!id;
  const { currentUser } = useAuth();

  const [formData, setFormData] = useState({
    numFacture: "",
    clientName: "",
    dateEmission: localDateIso(),
    echeance: "",
    montantFacture: "",
  });

  // Liste des clients pour la liste deroulante : toutes les pages (2000 clients maximum).
  const clientsQuery = useQuery({
    queryKey: ["/clients", "options"],
    queryFn: ({ signal }) => fetchAllPages("/clients", clientSchema, { maxItems: 2000, signal }),
  });
  const clients = clientsQuery.data?.items ?? [];
  useEffect(() => {
    if (clientsQuery.isError) toast.error("Impossible de charger les clients");
    if (clientsQuery.data?.truncated) {
      toast.warning("Plus de 2000 clients : la liste est tronquée. Contactez l'administrateur.");
    }
  }, [clientsQuery.isError, clientsQuery.data?.truncated]);

  // Creance a modifier
  const debtQuery = useQuery({
    queryKey: ["/creances", id],
    queryFn: ({ signal }) =>
      apiFetch(`/creances/${encodeURIComponent(id ?? "")}`, { schema: creanceSchema, signal }),
    enabled: isEditing,
  });
  const loading = isEditing && debtQuery.isLoading;

  useEffect(() => {
    const data = debtQuery.data;
    if (!data) return;
    setFormData({
      numFacture: data.numFacture,
      clientName: data.clientName || "",
      dateEmission: "",
      echeance: data.echeance ?? "",
      montantFacture: data.montantFacture?.toString() || "",
    });
  }, [debtQuery.data]);

  useEffect(() => {
    if (debtQuery.isError) {
      toast.error("Erreur lors du chargement de la créance");
      navigate("/debts");
    }
  }, [debtQuery.isError, navigate]);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const handleSelectChange = (name: string, value: string) => {
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const saveMutation = useMutation({
    mutationFn: (payload: object) =>
      apiFetch(isEditing ? `/creances/${encodeURIComponent(id ?? "")}` : "/creances", {
        method: isEditing ? "PUT" : "POST",
        body: payload,
      }),
    onSuccess: () => {
      toast.success(isEditing ? "Créance modifiée avec succès" : "Créance créée avec succès");
      queryClient.invalidateQueries({ queryKey: ["/creances"] });
      queryClient.invalidateQueries({ queryKey: ["/dashboard/stats"] });
      navigate("/debts");
    },
    onError: (err) =>
      toast.error(errorMessage(err, isEditing ? "Erreur lors de la modification de la créance" : "Erreur lors de la création de la créance")),
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    const { numFacture, clientName, dateEmission, echeance, montantFacture } = formData;
    if (!numFacture.trim() || !clientName || !echeance || !montantFacture) {
      toast.error("Veuillez remplir tous les champs obligatoires");
      return;
    }
    const montant = parseFloat(montantFacture);
    if (!Number.isFinite(montant) || montant <= 0) {
      toast.error("Le montant facturé doit être strictement positif");
      return;
    }

    // montantEncaisse et statut ne sont plus envoyes : le serveur les calcule a partir des
    // reglements et des penalites (ils etaient modifiables, donc falsifiables, avant).
    saveMutation.mutate({
      numFacture: numFacture.trim(),
      clientName,
      agentName: currentUser?.name || undefined,
      ...(dateEmission ? { dateEmission } : {}),
      echeance,
      montantFacture: montant,
    });
  };

  // Calculate days late for information
  const calculateDaysLate = () => {
    if (!formData.echeance) return 0;
    const today = new Date();
    const dueDate = new Date(formData.echeance);
    const diffTime = today.getTime() - dueDate.getTime();
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
    return diffDays > 0 ? diffDays : 0;
  };

  const daysLate = calculateDaysLate();
  const isOverdue = daysLate > 0;
  const isPenalized = daysLate >= 60;

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">{isEditing ? "Modifier la créance" : "Nouvelle créance"}</h1>

      <Card>
        <CardHeader>
          <CardTitle>Informations de la créance</CardTitle>
          <CardDescription>
            {isEditing
              ? "Modifiez les informations de la créance. Le numéro de facture ne peut pas être modifié."
              : "Remplissez les informations pour créer une nouvelle créance."}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="text-center py-8">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary mx-auto"></div>
              <p className="mt-2 text-sm text-muted-foreground">Chargement...</p>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-6">
              {/* Information Alert */}
              {isOverdue && (
                <Alert className={isPenalized ? "border-red-200 bg-red-50" : "border-orange-200 bg-orange-50"}>
                  <Info className="h-4 w-4" />
                  <AlertDescription>
                    {isPenalized
                      ? `Cette créance est en retard de ${daysLate} jours et sera automatiquement pénalisée (0.85% par mois après 60 jours).`
                      : `Cette créance est en retard de ${daysLate} jours.`
                    }
                  </AlertDescription>
                </Alert>
              )}

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
                <div>
                  <Label htmlFor="numFacture">N° Facture</Label>
                  <Input
                    id="numFacture"
                    name="numFacture"
                    value={formData.numFacture}
                    onChange={handleInputChange}
                    className={isEditing ? "bg-muted" : ""}
                    readOnly={isEditing}
                    required
                    maxLength={100}
                    placeholder="Ex. F2026-001"
                  />
                </div>

                <div>
                  <Label>Client</Label>
                  <Select
                    value={formData.clientName}
                    onValueChange={(value) => handleSelectChange("clientName", value)}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="Sélectionner un client" />
                    </SelectTrigger>
                    <SelectContent>
                      {clients.map((client) => (
                        <SelectItem key={client.id} value={client.raisonSociale}>
                          {client.raisonSociale}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div>
                  <Label>Date d'émission</Label>
                  <Input
                    type="date"
                    name="dateEmission"
                    value={formData.dateEmission}
                    onChange={handleInputChange}
                    required
                  />
                </div>

                <div>
                  <Label>Échéance</Label>
                  <Input
                    type="date"
                    name="echeance"
                    value={formData.echeance}
                    onChange={handleInputChange}
                    required
                  />
                </div>

                <div>
                  <Label>Montant facturé (MAD)</Label>
                  <Input
                    type="number"
                    step="0.01"
                    min="0"
                    name="montantFacture"
                    value={formData.montantFacture}
                    onChange={handleInputChange}
                    required
                  />
                </div>

                {isEditing && debtQuery.data && (
                  <div className="sm:col-span-2 rounded-md border bg-muted/40 p-4 text-sm">
                    <p className="font-medium">Calculés automatiquement</p>
                    <p className="mt-1 text-muted-foreground">
                      Montant encaissé : {formatCurrency(debtQuery.data.montantEncaisse)} MAD · Statut : {debtQuery.data.statut}
                      . Ces valeurs découlent des règlements enregistrés et ne se modifient pas ici.
                    </p>
                  </div>
                )}
              </div>

              <div className="flex justify-end gap-4">
                <Button type="button" variant="outline" onClick={() => navigate("/debts")}>
                  Annuler
                </Button>
                <Button type="submit" className="bg-debt-blue hover:bg-debt-lightBlue" disabled={saveMutation.isPending}>
                  {saveMutation.isPending ? "Enregistrement…" : `${isEditing ? "Modifier" : "Créer"} la créance`}
                </Button>
              </div>
            </form>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

export default DebtForm;
