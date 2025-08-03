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
import { formatCurrency } from "../utils/formatters";
import { toast } from "sonner";
import { useAuth } from "../contexts/AuthContext";

const PaymentForm = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const isEditing = !!id;
  const { authToken, currentUser } = useAuth();
  const [loading, setLoading] = useState(false);

  // Get debt ID from URL query params if it exists
  const urlParams = new URLSearchParams(window.location.search);
  const debtIdFromUrl = urlParams.get('debtId');

  const [formData, setFormData] = useState({
    debtId: debtIdFromUrl || "",
    montantEncaisse: "",
    dateReglement: "",
    modePaiement: "virement",
    reference: "",
    statut: "NON_EFFECTUE"
  });

  const [debtsWithClients, setDebtsWithClients] = useState<any[]>([]);

  // Fetch debts with remaining balance
  useEffect(() => {
    const fetchDebts = async () => {
      try {
        const response = await fetch("http://localhost:8080/api/creances", {
          method: "GET",
          headers: {
            "Authorization": `Bearer ${authToken}`,
            "Content-Type": "application/json"
          }
        });

        if (!response.ok) {
          console.error("Error response:", response.status);
          throw new Error("Erreur lors du chargement des créances");
        }

        const debts = await response.json();
        console.log("Fetched debts:", debts); // Debug log
        
        // Filter debts with remaining balance
        const debtsWithRemaining = debts
          .map((debt: any) => ({
            ...debt,
            remaining: debt.montantFacture - debt.montantEncaisse
          }))
          .filter((debt: any) => debt.remaining > 0);

        console.log("Processed debts:", debtsWithRemaining); // Debug log
        setDebtsWithClients(debtsWithRemaining);
      } catch (error) {
        console.error("Error fetching debts:", error);
        toast.error("Impossible de charger les créances");
      }
    };

    fetchDebts();
  }, [authToken]);

  useEffect(() => {
    if (isEditing && id) {
      const fetchPayment = async () => {
        setLoading(true);
        try {
          const response = await fetch(`http://localhost:8080/api/reglements/${id}`, {
            headers: {
              "Authorization": `Bearer ${authToken}`,
              "Content-Type": "application/json"
            }
          });

          if (!response.ok) throw new Error("Paiement non trouvé");

          const payment = await response.json();
          setFormData({
            debtId: payment.debtId,
            montantEncaisse: payment.montantEncaisse.toString(),
            dateReglement: new Date(payment.dateReglement).toISOString().split("T")[0],
            modePaiement: payment.modePaiement,
            reference: payment.reference || "",
            statut: payment.statut
          });
        } catch (error) {
          toast.error("Erreur lors du chargement du paiement");
          navigate("/payments");
        } finally {
          setLoading(false);
        }
      };

      fetchPayment();
    } else {
      // Set default date to today for new payments
      setFormData(prev => ({
        ...prev,
        dateReglement: new Date().toISOString().split("T")[0]
      }));
    }
  }, [id, isEditing, authToken, navigate]);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleSelectChange = (name: string, value: string) => {
    setFormData(prev => ({ ...prev, [name]: value }));
    
    // If debt is selected, set the default amount to the remaining balance
    if (name === "debtId") {
      const selectedDebt = debtsWithClients.find(d => d.numFacture === value);
      if (selectedDebt) {
        console.log("Selected debt:", selectedDebt); // Debug log
        setFormData(prev => ({ 
          ...prev, 
          debtId: value,
          montantEncaisse: selectedDebt.remaining.toString() 
        }));
      }
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    console.log(authToken)  // check if the token is available
    e.preventDefault();
    
    // Validation
    if (!formData.debtId || !formData.montantEncaisse || !formData.dateReglement || !formData.modePaiement) {
      toast.error("Veuillez remplir tous les champs obligatoires");
      return;
    }

    // Additional validation for payment amount
    const selectedDebt = debtsWithClients.find(d => d.numFacture === formData.debtId);
    const paymentAmount = parseFloat(formData.montantEncaisse);
    
    if (!selectedDebt) {
      toast.error("Veuillez sélectionner une créance valide");
      return;
    }
    
    if (paymentAmount <= 0) {
      toast.error("Le montant du paiement doit être supérieur à 0");
      return;
    }
    
    if (paymentAmount > selectedDebt.remaining) {
      toast.error(`Le montant du paiement ne peut pas dépasser le solde restant (${formatCurrency(selectedDebt.remaining)} MAD)`);
      return;
    }

    const payload = {
      numFacture: formData.debtId,
      montant: parseFloat(formData.montantEncaisse),
      dateReglement: formData.dateReglement,
      modePaiement: formData.modePaiement.toUpperCase(),
      reference: formData.reference || `VIR-${String(Math.floor(Math.random() * 9999)).padStart(4, "0")}-${formData.debtId}`,
      agentName: currentUser?.name || "Agent",
      statut: formData.statut
    };

    try {
      const url = isEditing
        ? `http://localhost:8080/api/reglements/${id}`
        : "http://localhost:8080/api/reglements";

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

      toast.success(isEditing ? "Règlement modifié avec succès" : "Règlement ajouté avec succès");
      navigate("/payments");
    } catch (err) {
      toast.error("Erreur lors de l'enregistrement du règlement");
    }
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
                      {debtsWithClients.map((debt: any) => (
                        <SelectItem key={debt.numFacture} value={debt.numFacture}>
                          {debt.numFacture} - {debt.clientName} ({formatCurrency(debt.remaining)} MAD)
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="montantEncaisse">Montant encaissé (€)</Label>
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
                      <SelectItem value="virement">Virement</SelectItem>
                      <SelectItem value="cheque">Chèque</SelectItem>
                      <SelectItem value="carte">Carte</SelectItem>
                      <SelectItem value="especes">Espèces</SelectItem>
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
                <Button type="submit" className="bg-debt-blue hover:bg-debt-lightBlue">
                  {isEditing ? "Modifier" : "Ajouter"} le règlement
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
