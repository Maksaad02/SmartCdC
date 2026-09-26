import React, { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { UserPlus } from "lucide-react";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { apiFetch, errorMessage } from "@/lib/apiClient";
import { roleSchema } from "@/schemas";
import { useDepartements } from "@/lib/departements";
import { ASSIGNABLE_ROLES } from "@/lib/roles";

/** Alignee sur la validation serveur (UtilisateurRequestDTO) : le serveur reste juge. */
const MIN_PASSWORD = 12;

const EMPTY = { nom: "", email: "", motDePasse: "", role: "AGENT", departementId: "" };

/** Creation d'un compte (POST /utilisateurs) par un administrateur ou un super administrateur. */
const CreateUserDialog: React.FC = () => {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState(EMPTY);

  // Le serveur attend un roleId : on resout AGENT / ADMIN depuis la liste des roles.
  const rolesQuery = useQuery({
    queryKey: ["/roles"],
    queryFn: ({ signal }) => apiFetch("/roles", { schema: z.array(roleSchema), signal }),
    enabled: open,
  });
  const departementsQuery = useDepartements(open);
  const departementsActifs = (departementsQuery.data ?? []).filter((d) => d.actif);
  // Un ADMIN voit toute l'entreprise : aucun departement. Manager et agent en ont exactement un.
  const besoinDepartement = form.role !== "ADMIN";

  const createMutation = useMutation({
    mutationFn: (payload: { nom: string; email: string; motDePasse: string; roleId: number; departementId?: number }) =>
      apiFetch("/utilisateurs", { method: "POST", body: payload }),
    onSuccess: () => {
      toast.success("Utilisateur créé. Communiquez-lui son mot de passe de façon sécurisée.");
      queryClient.invalidateQueries({ queryKey: ["/utilisateurs"] });
      setOpen(false);
      setForm(EMPTY);
    },
    onError: (err) => toast.error(errorMessage(err, "Impossible de créer l'utilisateur.")),
  });

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const roleId = rolesQuery.data?.find((r) => r.nom === form.role)?.id;
    if (!form.nom.trim() || !form.email.trim() || !form.motDePasse) {
      toast.error("Veuillez remplir tous les champs");
      return;
    }
    if (form.motDePasse.length < MIN_PASSWORD) {
      toast.error(`Le mot de passe doit contenir au moins ${MIN_PASSWORD} caractères`);
      return;
    }
    if (roleId === undefined) {
      toast.error("Rôles indisponibles : réessayez dans un instant");
      return;
    }
    if (besoinDepartement && !form.departementId) {
      toast.error("Choisissez le département de l'utilisateur");
      return;
    }
    createMutation.mutate({
      nom: form.nom.trim(),
      email: form.email.trim(),
      motDePasse: form.motDePasse,
      roleId,
      ...(besoinDepartement ? { departementId: Number(form.departementId) } : {}),
    });
  };

  const set = (name: keyof typeof EMPTY) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((prev) => ({ ...prev, [name]: e.target.value }));

  return (
    <Dialog open={open} onOpenChange={(next) => { setOpen(next); if (!next) setForm(EMPTY); }}>
      <DialogTrigger asChild>
        <Button size="sm"><UserPlus className="h-4 w-4 mr-2" />Nouvel utilisateur</Button>
      </DialogTrigger>
      <DialogContent>
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>Nouvel utilisateur</DialogTitle>
            <DialogDescription>
              Un administrateur voit toute l'entreprise ; un gestionnaire ou un agent est rattaché à un département.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-2">
            <Label htmlFor="nu-nom">Nom</Label>
            <Input id="nu-nom" value={form.nom} onChange={set("nom")} maxLength={100} autoComplete="off" />
          </div>
          <div className="space-y-2">
            <Label htmlFor="nu-email">Email</Label>
            <Input id="nu-email" type="email" value={form.email} onChange={set("email")} autoComplete="off" />
          </div>
          <div className="space-y-2">
            <Label htmlFor="nu-mdp">Mot de passe initial</Label>
            <Input
              id="nu-mdp"
              type="password"
              value={form.motDePasse}
              onChange={set("motDePasse")}
              autoComplete="new-password"
              maxLength={72}
            />
            <p className="text-xs text-gray-500">{MIN_PASSWORD} caractères minimum.</p>
          </div>
          <div className="space-y-2">
            <Label>Rôle</Label>
            <Select value={form.role} onValueChange={(role) => setForm((prev) => ({ ...prev, role }))}>
              <SelectTrigger aria-label="Rôle"><SelectValue /></SelectTrigger>
              <SelectContent>
                {ASSIGNABLE_ROLES.map((r) => <SelectItem key={r.nom} value={r.nom}>{r.label}</SelectItem>)}
              </SelectContent>
            </Select>
          </div>
          {besoinDepartement && (
            <div className="space-y-2">
              <Label>Département</Label>
              <Select value={form.departementId} onValueChange={(departementId) => setForm((prev) => ({ ...prev, departementId }))}>
                <SelectTrigger aria-label="Département"><SelectValue placeholder="Choisir un département" /></SelectTrigger>
                <SelectContent>
                  {departementsActifs.map((d) => <SelectItem key={d.id} value={String(d.id)}>{d.nom}</SelectItem>)}
                </SelectContent>
              </Select>
            </div>
          )}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>Annuler</Button>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? "Création…" : "Créer"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
};

export default CreateUserDialog;
