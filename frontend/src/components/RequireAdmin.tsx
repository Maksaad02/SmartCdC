import React from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";

/**
 * Garde de route réservée aux ADMIN.
 *
 * Le backend refuse déjà ces appels (403), donc aucune donnée ne fuite. Mais
 * sans garde côté client, la route restait directement accessible et la page
 * affichait « Aucun utilisateur trouvé » — un refus déguisé en base vide.
 * Ceci n'est PAS un contrôle de sécurité: c'est de la lisibilité. Le contrôle
 * reste le @PreAuthorize côté serveur.
 */
const RequireAdmin: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { currentUser, isLoading } = useAuth();

  if (isLoading) {
    return null;
  }

  if (currentUser?.role !== "admin") {
    return <Navigate to="/dashboard" replace />;
  }

  return <>{children}</>;
};

export default RequireAdmin;
