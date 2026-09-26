import React from "react";
import { Link, useLocation } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";
import {
  BarChart3,
  FileTextIcon,
  BanknoteIcon,
  Bell,
  Building2,
  Users,
  LogOut
} from "lucide-react";
import { cn } from "@/lib/utils";

const Navbar: React.FC = () => {
  const location = useLocation();
  const { logout, currentUser } = useAuth();

  const isActive = (path: string) => location.pathname === path;

  const navItem = (
    path: string,
    label: string,
    Icon: React.ElementType
  ) => (
    <Link
      to={path}
      className={cn(
        "flex items-center gap-2 px-4 py-2 text-sm rounded-md transition-all duration-300",
        isActive(path)
          ? "bg-blue-600 text-white shadow"
          : "text-gray-200 hover:bg-blue-500/20 hover:text-blue-300"
      )}
    >
      <Icon
        className={cn(
          "h-5 w-5",
          isActive(path) ? "text-white" : "text-gray-400 group-hover:text-blue-300"
        )}
      />
      {label}
    </Link>
  );

  return (
    <header className="fixed top-0 left-0 right-0 h-16 bg-blue-700 text-gray-200 shadow-md z-50">
      <div className="h-full px-6 flex items-center justify-between">

        {/* Logo */}
        <div className="text-2xl font-bold tracking-wide">
          <span className="text-yellow-400">SMaRt</span>
          <span className="text-[#4F9CF9]">CdC</span>
        </div>

        {/* Navigation */}
        <nav className="hidden md:flex items-center gap-2">
          {navItem("/dashboard", "Dashboard", BarChart3)}
          {navItem("/debts", "Créances", FileTextIcon)}
          {navItem("/payments", "Règlements", BanknoteIcon)}
          {navItem("/reminders", "Relances", Bell)}
          {navItem("/clients", "Clients", Users)}
          {currentUser?.role === "admin" && navItem("/departements", "Départements", Building2)}
          {currentUser?.role === "admin" && navItem("/utilisateurs", "Utilisateurs", Users)}
        </nav>

        {/* Profil & logout */}
        <div className="flex items-center gap-4">
          <Link
            to="/profile"
            className="flex items-center gap-2 px-3 py-2 rounded-md hover:bg-blue-500/30 transition duration-300"
          >
            <div className="h-8 w-8 rounded-full bg-blue-500 flex items-center justify-center font-semibold text-white">
              {currentUser?.name?.charAt(0).toUpperCase() || "U"}
            </div>
            <span className="hidden lg:block text-sm font-medium text-white">
              {currentUser?.name || "Utilisateur"}
            </span>
          </Link>

          <button
            onClick={logout}
            aria-label="Se déconnecter"
            className="flex items-center gap-2 px-3 py-2 rounded-md text-white hover:bg-blue-600 transition duration-300"
          >
            <LogOut className="h-5 w-5" />
          </button>
        </div>
      </div>
    </header>
  );
};

export default Navbar;
