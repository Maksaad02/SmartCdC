// export const formatDate = (dateString: Date): string => {
//   const date = new Date(dateString);
//   return date.toLocaleDateString("fr-FR");
// };
export const formatDate = (input: string | Date | undefined): string => {
  if (!input) return "Date invalide";
  try {
    const date = new Date(input);
    if (isNaN(date.getTime())) return "Date invalide";
    return date.toLocaleDateString("fr-FR");
  } catch {
    return "Date invalide";
  }
};


export const formatCurrency = (amount: number): string => {
  return new Intl.NumberFormat("fr-FR").format(amount);
};

export const getStatusClass = (status: string): string => {
  switch (status.toUpperCase()) {
    case "PAYEE":
      return "bg-green-500 text-white";
    case "IMPAYEE":
      return "bg-gray-500 text-white";
    case "EN_RETARD":
      return "bg-orange-500 text-white";
    case "PARTIELLEMENT_PAYEE":
      return "bg-blue-500 text-white";
    default:
      return "bg-gray-500 text-white";
  }
};
