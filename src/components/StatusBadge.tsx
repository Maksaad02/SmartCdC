import React from "react";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

export type StatusType = "PAYEE" | "IMPAYEE" | "EN_RETARD" | "PENALISEE" | "PARTIELLEMENT_PAYEE";

interface StatusBadgeProps {
  status: StatusType;
  className?: string;
}

const StatusBadge: React.FC<StatusBadgeProps> = ({ status, className }) => {
  let color = "";
  let label = "";

  switch (status) {
    case "PAYEE":
      color = "bg-status-paid text-white";
      label = "Payée";
      break;
    case "IMPAYEE":
      color = "bg-gray-500 text-white";
      label = "Impayée";
      break;
    case "EN_RETARD":
      color = "bg-status-late text-white";
      label = "En retard";
      break;
    case "PENALISEE":
      color = "bg-red-600 text-white";
      label = "Pénalisée";
      break;
    case "PARTIELLEMENT_PAYEE":
      color = "bg-status-pending text-white";
      label = "Partiellement payée";
      break;
    default:
      color = "bg-muted text-foreground";
      label = status;
  }

  return (
    <Badge className={cn(color, "font-medium text-xs px-3 py-1", className)}>
      {label}
    </Badge>
  );
};

export default StatusBadge;
