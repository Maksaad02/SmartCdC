import React, { useEffect, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Pencil } from "lucide-react";
import { Button } from "@/components/ui/button";
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
import { useDepartements } from "@/lib/departements";
import { ASSIGNABLE_ROLES } from "@/lib/roles";
import type { Utilisateur } from "@/schemas";

/** Modification du role et du departement d'un compte (PUT /utilisateurs/{id}/role). */
const EditUserRoleDialog: React.FC<{ user: Utilisateur }> = ({ user }) => {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [role, setRole] = useState(user.role ?? "AGENT");
  const [departementId, setDepartementId] = useState(user.departementId ? String(user.departementId) : "");
  const departementsQuery = useDepartements(open);
  const departements = (departementsQuery.data ?? []).filter((d) => d.actif || d.id === user.departementId);
  const besoinDepartement = role !== "ADMIN";

  useEffect(() => {
    if (open) {
      setRole(user.role ?? "AGENT");
      setDepartementId(user.departementId ? String(user.departementId) : "");
    }
  }, [open, user.role, user.departementId]);

  const mutation = useMutation({
    mutationFn: () =>
      apiFetch(`/utilisateurs/${user.id}/role`, {
        method: "PUT",
        body: { role, ...(besoinDepartement ? { departementId: Number(departementId) } : {}) },
      }),
    onSuccess: () => {
      toast.success("Rôle et département mis à jour. L'utilisateur devra se reconnecter.");
      queryClient.invalidateQueries({ queryKey: ["/utilisateurs"] });
      setOpen(false);
    },
    onError: (err) => toast.error(errorMessage(err, "Impossible de modifier le compte.")),
  });

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (besoinDepartement && !departementId) {
      toast.error("Choisissez un département");
      return;
    }
    mutation.mutate();
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" size="sm" aria-label={`Modifier ${user.nom ?? user.email}`}>
          <Pencil className="h-4 w-4" />
        </Button>
      </DialogTrigger>
      <DialogContent>
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>Rôle et département</DialogTitle>
            <DialogDescription>{user.nom ?? user.email}</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label>Rôle</Label>
            <Select value={role} onValueChange={setRole}>
              <SelectTrigger aria-label="Rôle"><SelectValue /></SelectTrigger>
              <SelectContent>
                {ASSIGNABLE_ROLES.map((r) => <SelectItem key={r.nom} value={r.nom}>{r.label}</SelectItem>)}
              </SelectContent>
            </Select>
          </div>
          {besoinDepartement && (
            <div className="space-y-2">
              <Label>Département</Label>
              <Select value={departementId} onValueChange={setDepartementId}>
                <SelectTrigger aria-label="Département"><SelectValue placeholder="Choisir un département" /></SelectTrigger>
                <SelectContent>
                  {departements.map((d) => <SelectItem key={d.id} value={String(d.id)}>{d.nom}</SelectItem>)}
                </SelectContent>
              </Select>
            </div>
          )}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>Annuler</Button>
            <Button type="submit" disabled={mutation.isPending}>{mutation.isPending ? "Enregistrement…" : "Enregistrer"}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
};

export default EditUserRoleDialog;
