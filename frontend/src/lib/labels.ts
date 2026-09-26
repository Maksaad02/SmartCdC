/** Libelles et styles des enums de relance, partages par les pages (Relances, Details). */
export const REMINDER_TYPE_LABELS: Record<string, { label: string; className: string }> = {
  EMAIL: { label: "Email", className: "bg-blue-100 text-blue-800" },
  TELEPHONE: { label: "Téléphone", className: "bg-orange-100 text-orange-800" },
  COURRIER: { label: "Courrier", className: "bg-purple-100 text-purple-800" },
  VISITE: { label: "Visite", className: "bg-gray-100 text-gray-800" },
};

export const REMINDER_STATUS_LABELS: Record<string, { label: string; className: string }> = {
  EN_ATTENTE: { label: "En attente", className: "bg-yellow-100 text-yellow-800" },
  ENVOYEE: { label: "Envoyée", className: "bg-blue-100 text-blue-800" },
  EFFECTUEE: { label: "Effectuée", className: "bg-green-100 text-green-800" },
  ANNULEE: { label: "Annulée", className: "bg-red-100 text-red-800" },
  ECHEC: { label: "Échec", className: "bg-red-100 text-red-800" },
  REPORTEE: { label: "Reportée", className: "bg-orange-100 text-orange-800" },
};
