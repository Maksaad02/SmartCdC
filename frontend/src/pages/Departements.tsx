import React, { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Building2, Pencil, Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { apiFetch, errorMessage } from "@/lib/apiClient";
import { useDepartements } from "@/lib/departements";
import QueryError from "@/components/QueryError";
import type { DepartementDto } from "@/schemas";

type Formulaire = { nom: string; code: string; actif: boolean };
const VIDE: Formulaire = { nom: "", code: "", actif: true };
const CODE_VALIDE = /^[A-Z0-9_-]{2,30}$/;

/** Gestion des departements (succursales / filiales internes) : reservee aux ADMIN. */
const Departements: React.FC = () => {
  const queryClient = useQueryClient();
  const query = useDepartements();
  const [edition, setEdition] = useState<DepartementDto | "nouveau" | null>(null);
  const [form, setForm] = useState<Formulaire>(VIDE);

  const ouvrir = (d: DepartementDto | "nouveau") => {
    setEdition(d);
    setForm(d === "nouveau" ? VIDE : { nom: d.nom, code: d.code, actif: d.actif });
  };

  const rafraichir = () => {
    queryClient.invalidateQueries({ queryKey: ["/departements"] });
    queryClient.invalidateQueries({ queryKey: ["/dashboard/departements"] });
  };

  const save = useMutation({
    mutationFn: (payload: Formulaire) =>
      edition && edition !== "nouveau"
        ? apiFetch(`/departements/${edition.id}`, { method: "PUT", body: payload })
        : apiFetch("/departements", { method: "POST", body: payload }),
    onSuccess: () => {
      toast.success(edition === "nouveau" ? "Département créé" : "Département modifié");
      rafraichir();
      setEdition(null);
    },
    onError: (err) => toast.error(errorMessage(err, "Impossible d'enregistrer le département")),
  });

  const supprimer = useMutation({
    mutationFn: (id: number) => apiFetch(`/departements/${id}`, { method: "DELETE" }),
    onSuccess: () => {
      toast.success("Département supprimé");
      rafraichir();
    },
    // Un departement qui porte des utilisateurs, clients ou creances ne se supprime pas : on le desactive.
    onError: () =>
      toast.error("Suppression impossible : ce département est utilisé. Désactivez-le à la place."),
  });

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const code = form.code.trim().toUpperCase();
    if (!form.nom.trim()) {
      toast.error("Le nom est obligatoire");
      return;
    }
    if (!CODE_VALIDE.test(code)) {
      toast.error("Le code doit contenir 2 à 30 caractères : majuscules, chiffres, - ou _");
      return;
    }
    save.mutate({ nom: form.nom.trim(), code, actif: form.actif });
  };

  const departements = query.data ?? [];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight">Gestion des départements</h1>
        <Button onClick={() => ouvrir("nouveau")}>
          <Plus className="mr-2 h-4 w-4" /> Nouveau département
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Building2 className="h-5 w-5" /> Départements de l'entreprise
          </CardTitle>
        </CardHeader>
        <CardContent>
          {query.isError ? (
            <QueryError what="les départements" error={query.error} onRetry={() => query.refetch()} />
          ) : query.isLoading ? (
            <p className="py-8 text-center text-gray-500">Chargement…</p>
          ) : (
            <div className="rounded-md border overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Nom</TableHead>
                    <TableHead>Code</TableHead>
                    <TableHead>Statut</TableHead>
                    <TableHead className="text-right">Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {departements.map((d) => (
                    <TableRow key={d.id}>
                      <TableCell className="font-medium">{d.nom}</TableCell>
                      <TableCell>{d.code}</TableCell>
                      <TableCell>{d.actif ? "Actif" : "Désactivé"}</TableCell>
                      <TableCell className="text-right space-x-2">
                        <Button variant="outline" size="sm" onClick={() => ouvrir(d)} aria-label={`Modifier ${d.nom}`}>
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          disabled={supprimer.isPending}
                          onClick={() => supprimer.mutate(d.id)}
                          aria-label={`Supprimer ${d.nom}`}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                  {departements.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={4} className="py-8 text-center text-gray-500">
                        Aucun département
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>

      <Dialog open={edition !== null} onOpenChange={(ouvert) => !ouvert && setEdition(null)}>
        <DialogContent>
          <form onSubmit={submit} className="space-y-4">
            <DialogHeader>
              <DialogTitle>{edition === "nouveau" ? "Nouveau département" : "Modifier le département"}</DialogTitle>
              <DialogDescription>
                Un département regroupe les utilisateurs, clients et créances d'une succursale.
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-2">
              <Label htmlFor="dep-nom">Nom</Label>
              <Input id="dep-nom" value={form.nom} maxLength={255} onChange={(e) => setForm({ ...form, nom: e.target.value })} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="dep-code">Code</Label>
              <Input
                id="dep-code"
                value={form.code}
                maxLength={30}
                placeholder="CASA"
                onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })}
              />
            </div>
            <div className="flex items-center gap-3">
              <Switch id="dep-actif" checked={form.actif} onCheckedChange={(actif) => setForm({ ...form, actif })} />
              <Label htmlFor="dep-actif">Actif (proposé à la création de clients et d'utilisateurs)</Label>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setEdition(null)}>Annuler</Button>
              <Button type="submit" disabled={save.isPending}>{save.isPending ? "Enregistrement…" : "Enregistrer"}</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default Departements;
