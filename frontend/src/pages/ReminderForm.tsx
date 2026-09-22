import React, { useState, useEffect } from "react";
import { useNavigate, useParams } from "react-router-dom";
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

const ReminderForm = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const isEditing = !!id;
  const { authToken, currentUser } = useAuth();
  const [loading, setLoading] = useState(false);

  // Get debt ID from URL query params if it exists
  const urlParams = new URLSearchParams(window.location.search);
  const debtIdFromUrl = urlParams.get('debtId');

  const [formData, setFormData] = useState({
    numFacture: debtIdFromUrl || "",
    dateRelance: new Date().toISOString().split("T")[0],
    typeRelance: "email",
    statutRelance: "en_attente",
    commentaire: ""
  });

  const [debtsWithClients, setDebtsWithClients] = useState<any[]>([]);
  const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080/api";

  // Fetch debts with remaining balance
  useEffect(() => {
    const fetchDebts = async () => {
      try {
        // Fetch debts
        const response = await fetch(`${API_URL}/creances`, {
          headers: {
            "Authorization": `Bearer ${authToken}`,
            "Content-Type": "application/json"
          }
        });

        if (!response.ok) {
          throw new Error("Erreur lors du chargement des créances");
        }

        const debts = await response.json();

        // Fetch clients to get their email addresses
        const clientsResponse = await fetch(`${API_URL}/clients`, {
          headers: {
            "Authorization": `Bearer ${authToken}`,
            "Content-Type": "application/json"
          }
        });

        if (!clientsResponse.ok) {
          throw new Error("Erreur lors du chargement des clients");
        }

        const clients = await clientsResponse.json();

        // Filter out paid debts and enrich with client email
        const unpaidDebts = debts
          .filter((debt: any) => debt.statut !== "PAYEE")
          .map((debt: any) => {
            const client = clients.find((c: any) => c.raisonSociale === debt.clientName);
            return {
              ...debt,
              email: client?.email
            };
          });

        setDebtsWithClients(unpaidDebts);
      } catch (error) {
        console.error("Error fetching debts:", error);
        toast.error("Impossible de charger les créances");
      }
    };

    fetchDebts();
  }, [authToken]);

  useEffect(() => {
    if (isEditing && id) {
      const fetchReminder = async () => {
        setLoading(true);
        try {
          const response = await fetch(`${API_URL}/relances/${id}`, {
            headers: {
              "Authorization": `Bearer ${authToken}`,
              "Content-Type": "application/json"
            }
          });

          if (!response.ok) throw new Error("Relance non trouvée");

          const reminder = await response.json();
          setFormData({
            numFacture: reminder.numFacture,
            dateRelance: new Date(reminder.dateRelance).toISOString().split("T")[0],
            typeRelance: reminder.typeRelance.toLowerCase(),
            statutRelance: reminder.statutRelance.toLowerCase(),
            commentaire: reminder.commentaire || ""
          });
        } catch (error) {
          toast.error("Erreur lors du chargement de la relance");
          navigate("/reminders");
        } finally {
          setLoading(false);
        }
      };

      fetchReminder();
    }
  }, [id, isEditing, authToken, navigate]);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleSelectChange = (name: string, value: string) => {
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    // Validation
    if (!formData.numFacture || !formData.dateRelance || !formData.typeRelance || !formData.statutRelance) {
      toast.error("Veuillez remplir tous les champs obligatoires");
      return;
    }

    const payload = {
      numFacture: formData.numFacture,
      dateRelance: formData.dateRelance,
      typeRelance: formData.typeRelance.toUpperCase(),
      statutRelance: formData.statutRelance.toUpperCase(),
      commentaire: formData.commentaire || null,
      agentName: currentUser?.name || "Agent"
    };

    try {
      const url = isEditing
        ? `${API_URL}/relances/${id}`
        : `${API_URL}/relances`;

      const methode = isEditing ? "PUT" : "POST";

      const response = await fetch(url, {
        method: methode,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${authToken}`
        },
        body: JSON.stringify(payload)
      });

      if (!response.ok) throw new Error();

      // If this is a new email reminder, send the email
      if (!isEditing && formData.typeRelance.toLowerCase() === "email") {
        // Get the client's email from the selected debt
        const selectedDebt = debtsWithClients.find(debt => debt.numFacture === formData.numFacture);
        if (selectedDebt?.email) {
          // Send email using the sendMail endpoint
          const emailPayload = {
            recipient: selectedDebt.email,
            subject: `Relance pour la facture ${formData.numFacture}`,
            msgBody: formData.commentaire || `Nous vous rappelons le paiement de la facture ${formData.numFacture}.`
          };

          const emailResponse = await fetch(`${API_URL}/sendMail`, {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
              "Authorization": `Bearer ${authToken}`
            },
            body: JSON.stringify(emailPayload)
          });

          if (!emailResponse.ok) {
            console.error("Failed to send email");
            toast.error("La relance a été créée mais l'envoi de l'email a échoué");
          } else {
            toast.success("Relance créée et email envoyé avec succès");
          }
        } else {
          toast.error("Impossible de trouver l'email du client");
        }
      } else {
        toast.success(isEditing ? "Relance modifiée avec succès" : "Relance ajoutée avec succès");
      }

      navigate("/reminders");
    } catch (err) {
      toast.error("Erreur lors de l'enregistrement de la relance");
    }
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
                      {debtsWithClients.map((debt: any) => (
                        <SelectItem key={debt.numFacture} value={debt.numFacture}>
                          {debt.numFacture} - {debt.clientName} ({formatCurrency(debt.montantFacture - (debt.montantEncaisse || 0))} MAD)
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
                      <SelectItem value="email">Email</SelectItem>
                      <SelectItem value="telephone">Téléphone</SelectItem>
                      <SelectItem value="courrier">Courrier</SelectItem>
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
                      <SelectItem value="en_attente">En attente</SelectItem>
                      <SelectItem value="envoyee">Envoyée</SelectItem>
                      <SelectItem value="repondue">Répondue</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-2 sm:col-span-2">
                  <Label htmlFor="commentaire">Contenue Email</Label>
                  <Textarea
                    id="commentaire"
                    name="commentaire"
                    placeholder="Saisissez le contenue de l'email sur cette relance..."
                    value={formData.commentaire}
                    onChange={handleInputChange}
                    rows={4}
                    className="resize-none"
                  />
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
                <Button type="submit" className="bg-debt-blue hover:bg-debt-lightBlue">
                  {isEditing ? "Modifier" : "Ajouter"} la relance
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
