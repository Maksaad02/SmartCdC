import React, { createContext, useState, useContext, ReactNode } from "react";
import { User } from "../models/types";
import { toast } from "sonner";

// Define JWT token type
interface JwtResponse {
  token: string;
}

interface AuthContextType {
  currentUser: User | null;
  login: (username: string, password: string) => Promise<boolean>;
  register: (name: string, email: string, password: string) => Promise<boolean>;
  logout: () => void;
  isAuthenticated: boolean;
  authToken: string | null;
} 

const API_URL = "http://34.226.195.59:8080/api"; // Replace with your Spring Boot API URL

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const [authToken, setAuthToken] = useState<string | null>(null);

  // Validate stored token on mount
  React.useEffect(() => {
    const validateStoredToken = async () => {
      const storedToken = localStorage.getItem("auth_token");
      const storedUser = localStorage.getItem("user");
      
      if (storedToken && storedUser) {
        try {
          // Validate token by making a test API call
          const response = await fetch(`${API_URL}/utilisateurs/me`, {
            headers: {
              "Authorization": `Bearer ${storedToken}`,
              "Content-Type": "application/json",
            },
          });
          
          if (response.ok) {
            // Token is valid, restore user session
            const userData = await response.json();
            const user: User = {
              id: userData.email || "unknown",
              name: userData.nom || "Agent",
              email: userData.email || "unknown",
              role: userData.role?.toLowerCase() || "user",
              createdAt: new Date()
            };
            
            setCurrentUser(user);
            setAuthToken(storedToken);
          } else {
            // Token is invalid, clear storage
            localStorage.removeItem("auth_token");
            localStorage.removeItem("user");
            setCurrentUser(null);
            setAuthToken(null);
          }
        } catch (error) {
          console.error("Token validation failed:", error);
          // Clear invalid token
          localStorage.removeItem("auth_token");
          localStorage.removeItem("user");
          setCurrentUser(null);
          setAuthToken(null);
        }
      }
    };

    validateStoredToken();
  }, []);

  // Register functionality connecting to Spring Boot backend
  const register = async (name: string, email: string, password: string) => {
    try {
      const response = await fetch(`${API_URL}/register`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          nom: name,
          email,
          motDePasse: password
        }),
      });

      if (!response.ok) {
        const errorData = await response.json();
        toast.error(errorData || "Erreur d'inscription");
        return false;
      }

      toast.success("Compte créé avec succès. Vous pouvez maintenant vous connecter.");
      return true;
    } catch (error) {
      console.error("Register error:", error);
      toast.error("Erreur de connexion au serveur");
      return false;
    }
  };

  // Login functionality connecting to Spring Boot backend
  const login = async (username: string, password: string) => {
    try {
      const response = await fetch(`${API_URL}/login`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          username,
          password,
        }),
      });

      console.log("Login status: ", response.status);
      console.log("All response headers: ", [...response.headers.entries()]);
      console.log("Authorization header: ", response.headers.get("Authorization"));

      if (!response.ok) {
        const errorText = await response.text();
        toast.error(errorText || "Identifiants incorrects");
        return false;
      }

      // const data: JwtResponse = await response.json();
      
      // Get user info from the token - in a real app, you might want to decode the JWT
      // or make a separate API call to get user details
      const data: JwtResponse = await response.json();
           console.log("JWT reçu (body): ", data.token);

      // After getting the token, fetch user details
      const userResponse = await fetch(`${API_URL}/utilisateurs/me`, {
        headers: {
          "Authorization": `Bearer ${data.token}`,
          "Content-Type": "application/json",
        },
      });

      if (!userResponse.ok) {
        toast.error("Erreur lors de la récupération des informations utilisateur");
        return false;
      }

      const userData = await userResponse.json();
      // Create a user object
      const user: User = {
        id: username, // Using username as ID temporarily
        name: userData.nom || "Agent", // Use nom field from UtilisateurResponseDTO
        email: userData.email || username,
        role: userData.role?.toLowerCase() || "user", // Convert role to lowercase to match frontend expectations
        createdAt: new Date()
      };

      setCurrentUser(user);
      setAuthToken(data.token);
      
      // Store token and user in localStorage
      localStorage.setItem("auth_token", data.token);
      localStorage.setItem("user", JSON.stringify(user));
      toast.success("Connexion réussie");
      return true;
    } catch (error) {
      console.error("Login error:", error);
      toast.error("Erreur de connexion au serveur");
      return false;
    }
  };

  // Logout functionality
  const logout = () => {
    setCurrentUser(null);
    setAuthToken(null);
    localStorage.removeItem("user");
    localStorage.removeItem("auth_token");
    toast.info("Déconnexion réussie");
  };

  //Log à chaque changement de token
  React.useEffect(() => {
    console.log("AuthContext -> authToken changed: ", authToken);
  }, [authToken]);

  const value = {
    currentUser,
    login,
    register,
    logout,
    isAuthenticated: currentUser !== null,
    authToken
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
