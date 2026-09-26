/**
 * Schemas zod des reponses de l'API backend.
 *
 * TypeScript ne verifie rien a l'execution : `const data: User[] = await r.json()`
 * n'est qu'une affirmation. Ces schemas valident reellement ce qui arrive du
 * reseau ; une reponse inattendue (contrat modifie, erreur renvoyee sous forme
 * d'objet) est detectee a la frontiere plutot que de faire planter un composant
 * plus loin. Les types TypeScript en sont deduits (z.infer) : une seule source.
 *
 * Les champs sont alignes sur les *ResponseDTO du backend.
 */
import { z } from "zod";

export const statutCreanceSchema = z.enum([
  "PAYEE",
  "IMPAYEE",
  "EN_RETARD",
  "PENALISEE",
  "PARTIELLEMENT_PAYEE",
]);
export const statutReglementSchema = z.enum(["EFFECTUE", "NON_EFFECTUE"]);
export const modePaiementSchema = z.enum([
  "ESPECES",
  "CHEQUE",
  "VIREMENT",
  "CARTE_BANCAIRE",
  "TRAITE",
]);
export const typeRelanceSchema = z.enum(["EMAIL", "COURRIER", "TELEPHONE", "VISITE"]);
export const statutRelanceSchema = z.enum([
  "EN_ATTENTE",
  "ENVOYEE",
  "EFFECTUEE",
  "ANNULEE",
  "ECHEC",
  "REPORTEE",
]);

export const jwtResponseSchema = z.object({
  token: z.string().min(1),
  /** Duree de vie du jeton d'acces, en secondes : sert a planifier le renouvellement. */
  expiresIn: z.number().positive().optional(),
});

export const utilisateurSchema = z.object({
  id: z.number(),
  nom: z.string().nullish(),
  email: z.string(),
  role: z.string().nullish(),
  /** null pour un ADMIN (vision globale de l'entreprise). */
  departementId: z.number().nullish(),
  departementNom: z.string().nullish(),
});

/** Departement (succursale ou filiale interne) : GET /departements. */
export const departementSchema = z.object({
  id: z.number(),
  nom: z.string(),
  code: z.string(),
  actif: z.boolean(),
});

/** Ligne du comparatif des departements (GET /dashboard/departements, ADMIN). */
export const departementStatsSchema = z.object({
  departementId: z.number().nullish(),
  nom: z.string(),
  nbCreances: z.number(),
  nbEnRetard: z.number(),
  montantFacture: z.number(),
  montantPenalites: z.number(),
  montantEncaisse: z.number(),
  solde: z.number(),
  tauxRecouvrement: z.number(),
});

export const dashboardDepartementsSchema = z.object({
  global: departementStatsSchema,
  departements: z.array(departementStatsSchema),
});

/** Role tel que renvoye par GET /roles : l'id est necessaire a la creation d'un compte (roleId). */
export const roleSchema = z.object({
  id: z.number(),
  nom: z.string(),
});

export const clientSchema = z.object({
  id: z.number(),
  raisonSociale: z.string(),
  email: z.string().nullish(),
  telephone: z.string().nullish(),
  rc: z.string().nullish(),
  adresse: z.string().nullish(),
  ice: z.string().nullish(),
  identiteFiscale: z.string().nullish(),
  agentName: z.string().nullish(),
  departementId: z.number().nullish(),
  departementNom: z.string().nullish(),
});

export const creanceSchema = z.object({
  id: z.number().nullish(),
  numFacture: z.string(),
  echeance: z.string().nullish(),
  montantFacture: z.number(),
  montantEncaisse: z.number(),
  solde: z.number(),
  montantPenalites: z.number(),
  montantTotal: z.number(),
  joursRetard: z.number(),
  statut: statutCreanceSchema,
  agentName: z.string().nullish(),
  clientName: z.string().nullish(),
  departementId: z.number().nullish(),
  departementNom: z.string().nullish(),
});

export const reglementSchema = z.object({
  id: z.number(),
  montant: z.number().nullish(),
  dateReglement: z.string().nullish(),
  modePaiement: modePaiementSchema.nullish(),
  statut: statutReglementSchema.nullish(),
  reference: z.string().nullish(),
  numFacture: z.string().nullish(),
  agentName: z.string().nullish(),
  clientName: z.string().nullish(),
});

export const relanceSchema = z.object({
  id: z.number(),
  numFacture: z.string().nullish(),
  agentName: z.string().nullish(),
  dateRelance: z.string().nullish(),
  typeRelance: typeRelanceSchema.nullish(),
  statutRelance: statutRelanceSchema.nullish(),
  commentaire: z.string().nullish(),
  message: z.string().nullish(),
  clientName: z.string().nullish(),
  echeance: z.string().nullish(),
  joursRetard: z.number().default(0),
});

/**
 * Page renvoyee par les endpoints de liste (Spring Data, serialisation PagedModel) :
 * { content: [...], page: { size, number, totalElements, totalPages } }.
 */
export const pageSchema = <T extends z.ZodTypeAny>(item: T) =>
  z.object({
    content: z.array(item),
    page: z.object({
      size: z.number(),
      number: z.number(),
      totalElements: z.number(),
      totalPages: z.number(),
    }),
  });

export type JwtResponse = z.infer<typeof jwtResponseSchema>;
export type Utilisateur = z.infer<typeof utilisateurSchema>;
export type DepartementDto = z.infer<typeof departementSchema>;
export type DepartementStats = z.infer<typeof departementStatsSchema>;
export type DashboardDepartements = z.infer<typeof dashboardDepartementsSchema>;
export type ClientDto = z.infer<typeof clientSchema>;
export type CreanceDto = z.infer<typeof creanceSchema>;
export type ReglementDto = z.infer<typeof reglementSchema>;
export type RelanceDto = z.infer<typeof relanceSchema>;
export type Page<T> = {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
};
