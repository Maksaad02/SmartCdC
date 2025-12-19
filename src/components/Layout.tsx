import React from "react";
import { Outlet, Navigate } from "react-router-dom";
import Navbar from "./Navbar";
import { useAuth } from "../contexts/AuthContext";
import Chatbot from "./Chatbot";


const Layout: React.FC = () => {
  const { isAuthenticated } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" />;
  }

  return (
    <div className="min-h-screen bg-[#F3F4F6]">
      <Navbar />
      <main className="pt-16 px-6">
        <Outlet />
        <Chatbot />
      </main>
    </div>
  );
};

export default Layout;
