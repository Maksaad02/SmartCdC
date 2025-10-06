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
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Info } from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "../contexts/AuthContext";
import { Client, Debt } from "../models/types";

const DebtForm = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const isEditing = !!id;
  const { authToken, currentUser } = useAuth();
  const [loading, setLoading] = useState(false);
  const [clients, setClients] = useState<Client[]>([]);

  const [formData, setFormData] = useState({
    numFacture: "",
    clientName: "",
    dateEmission: "",
    echeance: "",
    montantFacture: "",
    montantEncaisse: "0",
    statut: "IMPAYEE",
    actions: ""
  });

  // Fetch clients
  useEffect(() => {
    const fetchClients = async () => {
      try {
        const response = await fetch("http://localhost:8080/api/clients", {
          headers: {
            Authorization: `Bearer ${authToken}`
          }
        });
        const data = await response.json();
        setClients(data);
      } catch (err) {
        toast.error("Impossible de charger les clients");
      }
    };

    fetchClients();
  }, [authToken]);

  // Fetch debt if editing
  useEffect(() => {
    if (isEditing && id) {
      const fetchDebt = async () => {
        setLoading(true);
        try {
          const response = await fetch(`http://localhost:8080/api/creances/${id}`, {
            headers: {
              "Content-Type": "application/json",
              Authorization: `Bearer ${authToken}`
            }
          });

          if (!response.ok) {
            throw new Error(`Error ${response.status}`);
          }

          const data = await response.json();
          console.log("Fetched debt data:", data); // Debug log

          setFormData({
            numFacture: data.numFacture,
            clientName: data.clientName || "",
            dateEmission: data.dateEmission ? new Date(data.dateEmission).toISOString().split('T')[0] : "",
            echeance: data.echeance ? new Date(data.echeance).toISOString().split('T')[0] : "",
            montantFacture: data.montantFacture?.toString() || "",
            montantEncaisse: data.montantEncaisse?.toString() || "0",
            statut: data.statut || "IMPAYEE",
            actions: ""
          });
        } catch (err) {
          console.error("Error fetching debt:", err);
          toast.error("Erreur lors du chargement de la créance");
          navigate("/debts");
        } finally {
          setLoading(false);
        }
      };

      fetchDebt();
    } else {
      // Generate invoice number for new debts
      const newNum = `F${new Date().getFullYear()}-${String(Math.floor(Math.random() * 999)).padStart(3, "0")}`;
      setFormData((prev) => ({
        ...prev,
        numFacture: newNum,
        dateEmission: new Date().toISOString().split("T")[0]
      }));
    }
  }, [id, isEditing, authToken, navigate]);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const handleSelectChange = (name: string, value: string) => {
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    const {
      numFacture,
      clientName,
      dateEmission,
      echeance,
      montantFacture,
      montantEncaisse,
      statut
    } = formData;

    if (!clientName || !dateEmission || !echeance || !montantFacture || !statut) {
      toast.error("Veuillez remplir tous les champs obligatoires");
      return;
    }

    const payload = {
      numFacture,
      clientName,
      agentName: currentUser?.name || "agent",
      dateEmission,
      echeance,
      montantFacture: parseFloat(montantFacture),
      montantEncaisse: parseFloat(montantEncaisse),
      statut
    };

    try {
      const url = isEditing
        ? `http://localhost:8080/api/creances/${id}`
        : "http://localhost:8080/api/creances";

      const response = await fetch(url, {
        method: isEditing ? "PUT" : "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${authToken}`
        },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.message || `Error ${response.status}`);
      }

      toast.success(isEditing ? "Créance modifiée avec succès" : "Créance créée avec succès");
      navigate("/debts");
    } catch (err) {
      console.error("Error saving debt:", err);
      toast.error(err instanceof Error ? err.message : (isEditing ? "Erreur lors de la modification de la créance" : "Erreur lors de la création de la créance"));
    }
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
                    className="bg-muted"
                    readOnly
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

                <div>
                  <Label>Montant encaissé (MAD)</Label>
                  <Input
                    type="number"
                    step="0.01"
                    min="0"
                    name="montantEncaisse"
                    value={formData.montantEncaisse}
                    onChange={handleInputChange}
                    placeholder="0.00"
                  />
                </div>

                <div>
                  <Label>Statut</Label>
                  <Select
                    value={formData.statut}
                    onValueChange={(value) => handleSelectChange("statut", value)}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="Sélectionner un statut" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="PAYEE">Payée</SelectItem>
                      <SelectItem value="IMPAYEE">Impayée</SelectItem>
                      <SelectItem value="EN_RETARD">En retard</SelectItem>
                      <SelectItem value="PENALISEE">Pénalisée</SelectItem>
                      <SelectItem value="PARTIELLEMENT_PAYEE">Partiellement payée</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>

              <div className="flex justify-end gap-4">
                <Button type="button" variant="outline" onClick={() => navigate("/debts")}>
                  Annuler
                </Button>
                <Button type="submit" className="bg-debt-blue hover:bg-debt-lightBlue">
                  {isEditing ? "Modifier" : "Créer"} la créance
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
