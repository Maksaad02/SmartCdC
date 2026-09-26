import React from "react";
import { Loader2 } from "lucide-react";
import { Outlet, Navigate } from "react-router-dom";
import Navbar from "./Navbar";
import { useAuth } from "../contexts/AuthContext";
import Chatbot from "./Chatbot";


const Layout: React.FC = () => {
  const { isAuthenticated, isLoading, currentUser } = useAuth();

  // Attendre la fin de la revalidation avant de trancher : rediriger pendant le
  // chargement déconnectait l'utilisateur à chaque rafraîchissement de page.
  if (isLoading) {
    return (
      <div className="min-h-screen bg-[#F3F4F6] flex items-center justify-center">
        <Loader2 className="w-8 h-8 animate-spin text-gray-400" aria-label="Chargement" />
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return (
    <div className="min-h-screen bg-[#F3F4F6]">
      <Navbar />
      <main className="pt-16 px-6">
        <Outlet />
        {/* Assistant IA : reserve aux administrateurs (module inactif pour le moment). Masquer l'interface
            n'est pas une autorisation : les donnees restent cloisonnees par departement cote serveur. */}
        {currentUser?.role === "admin" && <Chatbot />}
      </main>
    </div>
  );
};

export default Layout;
