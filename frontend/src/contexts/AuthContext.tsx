import { createContext, useState, useContext, useEffect, useCallback, useRef, ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Role, User } from "../models/types";
import { toast } from "sonner";
import { ApiError, apiFetch, AUTH_EXPIRED_EVENT, errorMessage } from "@/lib/apiClient";
import { setAccessToken } from "@/lib/authToken";
import { logoutOnServer, refreshSession } from "@/lib/session";
import { getTokenExpiryMs } from "@/lib/jwt";
import { jwtResponseSchema, utilisateurSchema, type JwtResponse, type Utilisateur } from "@/schemas";

interface AuthContextType {
  currentUser: User | null;
  /** true tant que la session n'a pas fini d'etre reprise (renouvellement au chargement de la page). */
  isLoading: boolean;
  login: (username: string, password: string) => Promise<boolean>;
  logout: () => void;
  isAuthenticated: boolean;
}

// setTimeout stocke son delai sur 32 bits : au-dela, il se declencherait tout de suite.
const MAX_TIMER_MS = 2_147_000_000;
/** Renouveler avant l'expiration du jeton d'acces : a 80 % de sa duree de vie. */
const REFRESH_AT_FRACTION = 0.8;

const AuthContext = createContext<AuthContextType | undefined>(undefined);

/** Role inconnu ou absent : le moins privilegie. */
const toRole = (role: string | null | undefined): Role =>
  role === "ADMIN" ? "admin" : role === "MANAGER" ? "manager" : "agent";

const toUser = (data: Utilisateur, fallbackEmail: string): User => ({
  id: data.email || fallbackEmail,
  name: data.nom || "Agent",
  email: data.email || fallbackEmail,
  // Le backend renvoie ADMIN / MANAGER / AGENT. Le perimetre de donnees est decide par le serveur ;
  // le role ne sert ici qu'a afficher ou masquer des ecrans (ce n'est pas un controle de securite).
  role: toRole(data.role),
  departementId: data.departementId ?? null,
  departementNom: data.departementNom ?? null,
  createdAt: new Date(),
});

/** Delai avant de renouveler, en ms : depuis expiresIn (secondes) ou, a defaut, depuis le claim exp du jeton. */
const refreshDelayMs = (session: JwtResponse): number => {
  const lifetimeMs = session.expiresIn
    ? session.expiresIn * 1000
    : Math.max((getTokenExpiryMs(session.token) ?? Date.now() + 60_000) - Date.now(), 0);
  return Math.min(Math.max(lifetimeMs * REFRESH_AT_FRACTION, 1_000), MAX_TIMER_MS);
};

export function AuthProvider({ children }: { children: ReactNode }) {
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  // Demarre a true : la reprise de session est asynchrone, donc au premier rendu isAuthenticated
  // valait false et Layout redirigeait vers /login un utilisateur pourtant connecte, a chaque
  // rafraichissement de page.
  const [isLoading, setIsLoading] = useState(true);
  const refreshTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const queryClient = useQueryClient();

  const stopTimer = () => {
    if (refreshTimer.current) clearTimeout(refreshTimer.current);
    refreshTimer.current = null;
  };

  const endSession = useCallback((message?: string) => {
    stopTimer();
    setAccessToken(null);
    setCurrentUser(null);
    // Le cache contient les donnees de l'utilisateur precedent : ne jamais les montrer au suivant.
    queryClient.clear();
    if (message) toast.info(message);
  }, [queryClient]);

  /** Renouvelle le jeton d'acces avant son expiration, tant que la session (cookie) est valide. */
  const scheduleRefresh = useCallback((session: JwtResponse) => {
    stopTimer();
    refreshTimer.current = setTimeout(async () => {
      const renewed = await refreshSession();
      if (renewed) scheduleRefresh(renewed);
      else endSession("Votre session a expiré. Veuillez vous reconnecter.");
    }, refreshDelayMs(session));
  }, [endSession]);

  // Reprise de session au chargement : le cookie httpOnly de renouvellement, s'il est valide, donne un
  // nouveau jeton d'acces. Rien n'est plus lu depuis localStorage.
  useEffect(() => {
    // Nettoyage des anciennes versions, qui stockaient le jeton dans localStorage (lisible par XSS).
    try {
      localStorage.removeItem("auth_token");
      localStorage.removeItem("user");
    } catch {
      // stockage indisponible : sans importance
    }

    let cancelled = false;
    const restore = async () => {
      const session = await refreshSession();
      if (session && !cancelled) {
        try {
          const data = await apiFetch("/utilisateurs/me", { schema: utilisateurSchema });
          if (!cancelled) {
            setCurrentUser(toUser(data, "unknown"));
            scheduleRefresh(session);
          }
        } catch (error) {
          console.error("Reprise de session impossible :", error);
          setAccessToken(null);
        }
      }
      if (!cancelled) setIsLoading(false);
    };
    restore();
    return () => {
      cancelled = true;
      stopTimer();
    };
  }, [scheduleRefresh]);

  // Le serveur a refuse le jeton ET le renouvellement a echoue : session terminee.
  useEffect(() => {
    const onExpired = () => endSession("Session expirée. Veuillez vous reconnecter.");
    window.addEventListener(AUTH_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, onExpired);
  }, [endSession]);

  const login = async (username: string, password: string) => {
    try {
      const session = await apiFetch("/login", {
        method: "POST",
        body: { username, password },
        schema: jwtResponseSchema,
        authenticated: false,
      });

      // Le jeton doit etre en memoire avant l'appel suivant : apiFetch le lit dans lib/authToken.
      // Le cookie de renouvellement, lui, a ete pose par le serveur (httpOnly).
      setAccessToken(session.token);
      let data: Utilisateur;
      try {
        data = await apiFetch("/utilisateurs/me", { schema: utilisateurSchema });
      } catch {
        setAccessToken(null);
        toast.error("Erreur lors de la récupération des informations utilisateur");
        return false;
      }

      setCurrentUser(toUser(data, username));
      scheduleRefresh(session);
      toast.success("Connexion réussie");
      return true;
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        toast.error("Identifiants incorrects");
      } else {
        toast.error(errorMessage(error, "Erreur de connexion au serveur"));
      }
      return false;
    }
  };

  const logout = () => {
    // Revocation cote serveur (sans attendre) : le cookie ne doit plus permettre de rouvrir la session.
    void logoutOnServer();
    endSession("Déconnexion réussie");
  };

  const value = {
    currentUser,
    isLoading,
    login,
    logout,
    isAuthenticated: currentUser !== null,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
