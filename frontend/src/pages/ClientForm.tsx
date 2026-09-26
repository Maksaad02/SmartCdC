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
import { toast } from "sonner";
import { useAuth } from "../contexts/AuthContext";
import { apiFetch, errorMessage } from "@/lib/apiClient";
import { clientSchema } from "@/schemas";
import { useDepartements } from "@/lib/departements";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

const ClientForm = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const isEditing = !!id;
  const { currentUser } = useAuth();

  const [formData, setFormData] = useState({
    raisonSociale: "",
    email: "",
    telephone: "",
    registreCommerce: "",
    adresse: "",
    identiteFiscale: "",
    ice: ""
  });
  // Departement du client : choisi par un ADMIN a la creation ; impose par le serveur pour un manager ou
  // un agent (leur departement) ; non modifiable ensuite (ses creances et reglements le portent aussi).
  const [departementId, setDepartementId] = useState<string>("");
  const isAdmin = currentUser?.role === "admin";
  const departementsQuery = useDepartements(isAdmin && !isEditing);
  const departementsActifs = (departementsQuery.data ?? []).filter((d) => d.actif);

  const clientQuery = useQuery({
    queryKey: ["/clients", id],
    queryFn: ({ signal }) => apiFetch(`/clients/${id}`, { schema: clientSchema, signal }),
    enabled: isEditing,
  });
  const loading = isEditing && clientQuery.isLoading;

  useEffect(() => {
    const client = clientQuery.data;
    if (!client) return;
    setFormData({
      raisonSociale: client.raisonSociale,
      email: client.email ?? "",
      telephone: client.telephone ?? "",
      registreCommerce: client.rc || "",
      adresse: client.adresse ?? "",
      identiteFiscale: client.identiteFiscale || "",
      ice: client.ice || ""
    });
    setDepartementId(client.departementId ? String(client.departementId) : "");
  }, [clientQuery.data]);

  useEffect(() => {
    if (clientQuery.isError) {
      toast.error("Erreur lors du chargement du client");
      navigate("/clients");
    }
  }, [clientQuery.isError, navigate]);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const saveMutation = useMutation({
    mutationFn: (payload: object) =>
      apiFetch(isEditing ? `/clients/${id}` : "/clients", {
        method: isEditing ? "PUT" : "POST",
        body: payload,
      }),
    onSuccess: () => {
      toast.success(isEditing ? "Client modifié avec succès" : "Client ajouté avec succès");
      queryClient.invalidateQueries({ queryKey: ["/clients"] });
      navigate("/clients");
    },
    onError: (err) => toast.error(errorMessage(err, "Une erreur est survenue")),
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    if (!formData.raisonSociale || !formData.email || !formData.telephone || !formData.adresse) {
      toast.error("Veuillez remplir tous les champs obligatoires");
      return;
    }

    if (isAdmin && !isEditing && !departementId) {
      toast.error("Choisissez le département du client");
      return;
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(formData.email)) {
      toast.error("Veuillez entrer une adresse email valide");
      return;
    }

    saveMutation.mutate({
      raisonSociale: formData.raisonSociale,
      email: formData.email,
      telephone: formData.telephone,
      rc: formData.registreCommerce,
      adresse: formData.adresse,
      identiteFiscale: formData.identiteFiscale,
      ice: formData.ice,
      // A la creation, le responsable du dossier est l'utilisateur courant (un AGENT y est de toute facon
      // force par le serveur). A la modification, on ne le change pas : renvoyer son nom ferait du
      // dernier editeur le nouveau responsable.
      ...(isEditing ? {} : { agentName: currentUser?.name || undefined }),
      // Seul un ADMIN designe le departement, a la creation.
      ...(isAdmin && !isEditing ? { departementId: Number(departementId) } : {}),
    });
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

                {isAdmin && !isEditing && (
                  <div className="space-y-2 sm:col-span-2">
                    <Label>Département <span className="text-red-500">*</span></Label>
                    <Select value={departementId} onValueChange={setDepartementId}>
                      <SelectTrigger aria-label="Département"><SelectValue placeholder="Choisir un département" /></SelectTrigger>
                      <SelectContent>
                        {departementsActifs.map((d) => (
                          <SelectItem key={d.id} value={String(d.id)}>{d.nom}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                )}

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
                <Button type="submit" className="bg-debt-blue hover:bg-debt-lightBlue" disabled={saveMutation.isPending}>
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
