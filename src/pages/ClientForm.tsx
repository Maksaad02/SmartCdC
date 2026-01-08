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
import { toast } from "sonner";
import { useAuth } from "../contexts/AuthContext";

const ClientForm = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const isEditing = !!id;
  const { authToken, currentUser } = useAuth();
  const [loading, setLoading] = useState(false);

  const [formData, setFormData] = useState({
    raisonSociale: "",
    email: "",
    telephone: "",
    registreCommerce: "",
    adresse: "",
    identiteFiscale: "",
    ice: ""
  });

  const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080/api";

  useEffect(() => {
    if (isEditing && id) {
      const fetchClient = async () => {
        setLoading(true);
        try {
          const response = await fetch(`${API_URL}/clients/${id}`, {
            headers: {
              "Content-Type": "application/json",
              "Authorization": `Bearer ${authToken}`
            }
          });

          if (!response.ok) throw new Error("Client non trouvé");

          const client = await response.json();
          setFormData({
            raisonSociale: client.raisonSociale,
            email: client.email,
            telephone: client.telephone,
            registreCommerce: client.rc || "",
            adresse: client.adresse,
            identiteFiscale: client.identiteFiscale || "",
            ice: client.ice || ""
          });
        } catch (error) {
          toast.error("Erreur lors du chargement du client");
          navigate("/clients");
        } finally {
          setLoading(false);
        }
      };

      fetchClient();
    }
  }, [id, isEditing, navigate, authToken]);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    // Validation
    if (!formData.raisonSociale || !formData.email || !formData.telephone || !formData.adresse) {
      toast.error("Veuillez remplir tous les champs obligatoires");
      return;
    }

    // Email validation
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(formData.email)) {
      toast.error("Veuillez entrer une adresse email valide");
      return;
    }

    try {
      const url = isEditing
        ? `${API_URL}/clients/${id}`
        : `${API_URL}/clients`;

      const method = isEditing ? "PUT" : "POST";

      const payload = {
        raisonSociale: formData.raisonSociale,
        email: formData.email,
        telephone: formData.telephone,
        rc: formData.registreCommerce,
        adresse: formData.adresse,
        identiteFiscale: formData.identiteFiscale,
        ice: formData.ice,
        agentName: currentUser?.name || "Agent" // Add the agent name like in DebtForm
      };

      const response = await fetch(url, {
        method: method,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${authToken}`
        },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        const errorData = await response.text();
        throw new Error(errorData || "Erreur lors de l'enregistrement");
      }

      toast.success(isEditing ? "Client modifié avec succès" : "Client ajouté avec succès");
      navigate("/clients");
    } catch (error) {
      console.error("Error:", error);
      toast.error(error instanceof Error ? error.message : "Une erreur est survenue");
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight">
          {isEditing ? "Modifier le client" : "Nouveau client"}
        </h1>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Informations du client</CardTitle>
          <CardDescription>
            Remplissez les informations pour {isEditing ? "modifier" : "ajouter"} un client
          </CardDescription>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="text-center py-8">Chargement...</div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-6">
              <div className="grid grid-cols-1 gap-6 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="raisonSociale">Nom / Raison sociale <span className="text-red-500">*</span></Label>
                  <Input
                    id="raisonSociale"
                    name="raisonSociale"
                    placeholder="Nom du client"
                    value={formData.raisonSociale}
                    onChange={handleInputChange}
                    required
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="email">Email <span className="text-red-500">*</span></Label>
                  <Input
                    id="email"
                    name="email"
                    type="email"
                    placeholder="email@example.com"
                    value={formData.email}
                    onChange={handleInputChange}
                    required
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="telephone">Téléphone <span className="text-red-500">*</span></Label>
                  <Input
                    id="telephone"
                    name="telephone"
                    placeholder="01 23 45 67 89"
                    value={formData.telephone}
                    onChange={handleInputChange}
                    required
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="registreCommerce">N° Registre du commerce</Label>
                  <Input
                    id="registreCommerce"
                    name="registreCommerce"
                    placeholder="RCS12345"
                    value={formData.registreCommerce}
                    onChange={handleInputChange}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="identiteFiscale">Identité Fiscale</Label>
                  <Input
                    id="identiteFiscale"
                    name="identiteFiscale"
                    placeholder="Identité fiscale"
                    value={formData.identiteFiscale}
                    onChange={handleInputChange}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="ice">ICE</Label>
                  <Input
                    id="ice"
                    name="ice"
                    placeholder="ICE"
                    value={formData.ice}
                    onChange={handleInputChange}
                  />
                </div>

                <div className="space-y-2 sm:col-span-2">
                  <Label htmlFor="adresse">Adresse <span className="text-red-500">*</span></Label>
                  <Input
                    id="adresse"
                    name="adresse"
                    placeholder="Adresse complète"
                    value={formData.adresse}
                    onChange={handleInputChange}
                    required
                  />
                </div>
              </div>

              <div className="flex justify-end space-x-4">
                <Button type="button" variant="outline" onClick={() => navigate("/clients")}>
                  Annuler
                </Button>
                <Button type="submit" className="bg-debt-blue hover:bg-debt-lightBlue">
                  {isEditing ? "Modifier" : "Ajouter"} le client
                </Button>
              </div>
            </form>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

export default ClientForm;
