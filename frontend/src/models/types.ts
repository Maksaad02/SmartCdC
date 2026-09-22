// Types for the debt collection application

export type User = {
  id: string;
  name: string;
  email: string;
  role: "admin" | "user";
  createdAt: Date;
};

export type Client = {
  id: string;
  raisonSociale: string;
  email: string;
  telephone: string;
  rc?: string;
  adresse: string;
  identiteFiscale?: string;
  ice?: string;
  createdAt: Date;
};

export type Debt = {
  id: string | null;
  numFacture: string;
  dateEmission: string;
  echeance: string;
  montantFacture: number;
  montantEncaisse: number;
  montantPenalites: number;
  solde: number;
  joursRetard: number;
  statut: "PAYEE" | "IMPAYEE" | "EN_RETARD" | "PENALISEE" | "PARTIELLEMENT_PAYEE";
  clientName?: string;
  agentName?: string;
};

export type Payment = {
  id: string;
  debtId: string;
  debt?: Debt;
  dateReglement: Date;
  montantEncaisse: number;
  modePaiement: "virement" | "cheque" | "carte" | "especes";
  reference?: string;
  createdAt: Date;
};

export type Reminder = {
  id: string;
  numFacture: string;
  creance?: Debt;
  dateRelance: Date;
  typeRelance: "email" | "telephone" | "courrier";
  statutRelance: "en_attente" | "envoyee" | "repondue";
  commentaire?: string;
  createdAt: Date;
};
