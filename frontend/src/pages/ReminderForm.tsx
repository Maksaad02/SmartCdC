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
import { Textarea } from "@/components/ui/textarea";
import { formatCurrency } from "../utils/formatters";
import { toast } from "sonner";
import { useAuth } from "../contexts/AuthContext";
import { apiFetch, errorMessage } from "@/lib/apiClient";
import { fetchAllPages } from "@/lib/pagedQueries";
import { localDateIso } from "@/lib/dates";
import { creanceSchema, relanceSchema } from "@/schemas";

const MESSAGE_MAX = 1000; // relance.message : VARCHAR(1000)

const ReminderForm = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const isEditing = !!id;
  const { currentUser } = useAuth();
  const [searchParams] = useSearchParams();
  const debtIdFromUrl = searchParams.get("debtId");

  // Valeurs de l'enum backend (TypeRelance, StatutRelance).
  const [formData, setFormData] = useState({
    numFacture: debtIdFromUrl || "",
    dateRelance: localDateIso(),
    typeRelance: "EMAIL",
    statutRelance: "EN_ATTENTE",
    message: ""
  });

  // Creances non soldees pour la liste deroulante : toutes les pages (2000 maximum).
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
  const unpaidDebts = useMemo(
    () => (debtsQuery.data?.items ?? []).filter((debt) => debt.statut !== "PAYEE"),
    [debtsQuery.data],
  );

  const reminderQuery = useQuery({
    queryKey: ["/relances", id],
    queryFn: ({ signal }) => apiFetch(`/relances/${id}`, { schema: relanceSchema, signal }),
    enabled: isEditing,
  });
  const loading = isEditing && reminderQuery.isLoading;

  useEffect(() => {
    const reminder = reminderQuery.data;
    if (!reminder) return;
    setFormData({
      numFacture: reminder.numFacture ?? "",
      dateRelance: reminder.dateRelance ?? localDateIso(),
      typeRelance: reminder.typeRelance ?? "EMAIL",
      statutRelance: reminder.statutRelance ?? "EN_ATTENTE",
      message: reminder.message ?? reminder.commentaire ?? "",
    });
  }, [reminderQuery.data]);

  useEffect(() => {
    if (reminderQuery.isError) {
      toast.error("Erreur lors du chargement de la relance");
      navigate("/reminders");
    }
  }, [reminderQuery.isError, navigate]);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleSelectChange = (name: string, value: string) => {
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const saveMutation = useMutation({
    mutationFn: async (payload: object) => {
      const saved = await apiFetch(isEditing ? `/relances/${id}` : "/relances", {
        method: isEditing ? "PUT" : "POST",
        body: payload,
        schema: relanceSchema,
      });

      // Nouvelle relance par e-mail : on la fait partir. Le destinataire est determine par le
      // SERVEUR a partir de la creance : l'ancien appel a /sendMail, ou le navigateur fournissait
      // destinataire, sujet et corps, a ete supprime (c'etait un relais de messagerie ouvert).
      if (!isEditing && payload && (payload as { typeRelance: string }).typeRelance === "EMAIL"
        && (payload as { statutRelance: string }).statutRelance === "EN_ATTENTE") {
        try {
          await apiFetch(`/relances/${saved.id}/envoyer`, { method: "POST", timeoutMs: 30_000 });
          return { emailSent: true as const };
        } catch (err) {
          return { emailSent: false as const, emailError: errorMessage(err) };
        }
      }
      return { emailSent: null };
    },
    onSuccess: (result) => {
      if (result.emailSent === true) toast.success("Relance créée et e-mail envoyé avec succès");
      else if (result.emailSent === false) toast.error(`La relance a été créée mais l'envoi de l'e-mail a échoué : ${result.emailError}`);
      else toast.success(isEditing ? "Relance modifiée avec succès" : "Relance ajoutée avec succès");
      queryClient.invalidateQueries({ queryKey: ["/relances"] });
      navigate("/reminders");
    },
    onError: (err) => toast.error(errorMessage(err, "Erreur lors de l'enregistrement de la relance")),
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    if (!formData.numFacture || !formData.dateRelance || !formData.typeRelance || !formData.statutRelance) {
      toast.error("Veuillez remplir tous les champs obligatoires");
      return;
    }
    if (formData.message.length > MESSAGE_MAX) {
      toast.error(`Le message ne peut pas dépasser ${MESSAGE_MAX} caractères`);
      return;
    }

    saveMutation.mutate({
      numFacture: formData.numFacture,
      dateRelance: formData.dateRelance,
      typeRelance: formData.typeRelance,
      statutRelance: formData.statutRelance,
      message: formData.message.trim() || undefined,
      agentName: currentUser?.name || undefined,
    });
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight">
          {isEditing ? "Modifier la relance" : "Nouvelle relance"}
        </h1>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Informations de la relance</CardTitle>
          <CardDescription>
            Remplissez les informations pour {isEditing ? "modifier" : "créer"} une relance
          </CardDescription>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="text-center py-8">Chargement...</div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-6">
              <div className="grid grid-cols-1 gap-6 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="numFacture">Créance</Label>
                  <Select
                    value={formData.numFacture}
                    onValueChange={(value) => handleSelectChange("numFacture", value)}
                    disabled={isEditing || !!debtIdFromUrl}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="Sélectionner une créance" />
                    </SelectTrigger>
                    <SelectContent>
                      {unpaidDebts.map((debt) => (
                        <SelectItem key={debt.numFacture} value={debt.numFacture}>
                          {debt.numFacture} - {debt.clientName} ({formatCurrency(debt.solde)} MAD)
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="typeRelance">Type de relance</Label>
                  <Select
                    value={formData.typeRelance}
                    onValueChange={(value) => handleSelectChange("typeRelance", value)}
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="EMAIL">Email</SelectItem>
                      <SelectItem value="TELEPHONE">Téléphone</SelectItem>
                      <SelectItem value="COURRIER">Courrier</SelectItem>
                      <SelectItem value="VISITE">Visite</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="dateRelance">Date de relance</Label>
                  <Input
                    id="dateRelance"
                    name="dateRelance"
                    type="date"
                    value={formData.dateRelance}
                    onChange={handleInputChange}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="statutRelance">Statut</Label>
                  <Select
                    value={formData.statutRelance}
                    onValueChange={(value) => handleSelectChange("statutRelance", value)}
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="EN_ATTENTE">En attente</SelectItem>
                      <SelectItem value="ENVOYEE">Envoyée</SelectItem>
                      <SelectItem value="EFFECTUEE">Effectuée</SelectItem>
                      <SelectItem value="REPORTEE">Reportée</SelectItem>
                      <SelectItem value="ANNULEE">Annulée</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-2 sm:col-span-2">
                  <Label htmlFor="message">Contenu du message</Label>
                  <Textarea
                    id="message"
                    name="message"
                    placeholder="Contenu de l'e-mail ou note sur la relance (un rappel standard est envoyé si vide)..."
                    value={formData.message}
                    onChange={handleInputChange}
                    rows={4}
                    maxLength={MESSAGE_MAX}
                    className="resize-none"
                  />
                  <p className="text-xs text-muted-foreground text-right">{formData.message.length}/{MESSAGE_MAX}</p>
                </div>
              </div>

              <div className="flex justify-end space-x-4">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => navigate("/reminders")}
                >
                  Annuler
                </Button>
                <Button type="submit" className="bg-debt-blue hover:bg-debt-lightBlue" disabled={saveMutation.isPending}>
                  {saveMutation.isPending ? "Enregistrement…" : `${isEditing ? "Modifier" : "Ajouter"} la relance`}
                </Button>
              </div>
            </form>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

export default ReminderForm;
