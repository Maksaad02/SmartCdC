// Types partages cote client. Les types des donnees de l'API (clients, creances, reglements,
// relances) sont deduits des schemas zod de src/schemas : une seule source de verite, validee
// a l'execution.

export type Role = "admin" | "manager" | "agent";

export type User = {
  id: string;
  name: string;
  email: string;
  /** admin : toute l'entreprise ; manager : son departement ; agent : son portefeuille dans son departement. */
  role: Role;
  /** null pour un admin. */
  departementId: number | null;
  departementNom: string | null;
  createdAt: Date;
};
