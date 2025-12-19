import React, { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

const Login: React.FC = () => {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { login, isAuthenticated } = useAuth();

  React.useEffect(() => {
    if (isAuthenticated) navigate("/dashboard");
  }, [isAuthenticated, navigate]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      const success = await login(email, password);
      if (success) navigate("/dashboard");
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  // Créer des “bills” animés
  const bills = Array.from({ length: 15 }).map((_, i) => ({
    id: i,
    left: Math.random() * 100,
    delay: Math.random() * 5,
    duration: 5 + Math.random() * 5,
  }));

  return (
    <div className="relative min-h-screen flex items-center justify-center bg-gradient-to-r from-blue-400 via-indigo-500 to-purple-500 overflow-hidden">
      
      {/* Animation billets */}
      {bills.map(bill => (
        <span
          key={bill.id}
          className="absolute text-yellow-300 text-2xl animate-fall"
          style={{
            left: `${bill.left}%`,
            animationDelay: `${bill.delay}s`,
            animationDuration: `${bill.duration}s`,
          }}
        >
          💸
        </span>
      ))}

      {/* Login Card */}
      <div className="bg-white/90 backdrop-blur-md p-10 rounded-3xl shadow-2xl w-full max-w-md z-10">
        <h1 className="text-4xl font-bold text-blue-500 text-center mb-4">
          <span className="text-yellow-300">SMaRt</span>CdC
        </h1>
        <p className="text-center text-gray-600 mb-6">
          Gestion de recouvrement de créances – fun et simple
        </p>

        <form onSubmit={handleSubmit} className="space-y-4">
          <Input
            type="email"
            placeholder="Email"
            value={email}
            onChange={e => setEmail(e.target.value)}
            required
          />
          <Input
            type="password"
            placeholder="Mot de passe"
            value={password}
            onChange={e => setPassword(e.target.value)}
            required
          />
          <Button type="submit" className="w-full bg-blue-500 hover:bg-blue-600">
            {loading ? "Connexion..." : "Se connecter"}
          </Button>
        </form>

        <p className="text-center text-gray-500 mt-4">
          Pas encore de compte?{" "}
          <Link to="/register" className="text-blue-500 hover:underline font-semibold">
            S'inscrire
          </Link>
        </p>
      </div>

      {/* Animation CSS */}
      <style>
        {`
          @keyframes fall {
            0% { transform: translateY(-100px) rotate(0deg); }
            100% { transform: translateY(110vh) rotate(360deg); }
          }
          .animate-fall {
            position: absolute;
            top: -50px;
            animation-name: fall;
            animation-timing-function: linear;
            animation-iteration-count: infinite;
          }
        `}
      </style>
    </div>
  );
};

export default Login;
