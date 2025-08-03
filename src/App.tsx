// import { Toaster } from "@/components/ui/toaster";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { AuthProvider } from "./contexts/AuthContext";

// Pages
import Dashboard from "./pages/Dashboard";
import Layout from "./components/Layout";
import Login from "./pages/Login";
import Register from "./pages/Register";
import Debts from "./pages/Debts";
import DebtForm from "./pages/DebtForm";
import DebtDetail from "./pages/DebtDetails";
import Payments from "./pages/Payments";
import PaymentForm from "./pages/PaymentForm";
import Reminders from "./pages/Reminders";
import ReminderForm from "./pages/ReminderForm";
import Clients from "./pages/Clients";
import ClientForm from "./pages/ClientForm";
import ClientDetails from "./pages/ClientDetails";
import Profile from "./pages/Profile";
import NotFound from "./pages/NotFound";
import PaymentDetails from "./pages/PaymentDetails";
import DebtDetails from "./pages/DebtDetails";
import ReminderDetails from "./pages/ReminderDetails";
import UserManagement from "./pages/UserManagement";

const queryClient = new QueryClient();

const App = () => (
  <QueryClientProvider client={queryClient}>
    <AuthProvider>
      <TooltipProvider>
        {/* <Toaster /> */}
        <Sonner />
        <BrowserRouter>
          <Routes>
            {/* Auth routes */}
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
            
            {/* Protected routes */}
            <Route element={<Layout />}>
              <Route path="/" element={<Dashboard />} />
              <Route path="/dashboard" element={<Dashboard />} />
              
              {/* Debts routes */}
              <Route path="/debts" element={<Debts />} />
              <Route path="/debts/new" element={<DebtForm />} />
              <Route path="/debts/:id" element={<DebtForm />} />
              <Route path="/debts/:id/details" element={<DebtDetails />} />
              <Route path="/debts/:id/edit" element={<DebtForm />} />
              
              {/* Payments routes */}
              <Route path="/payments" element={<Payments />} />
              <Route path="/payments/new" element={<PaymentForm />} />
              <Route path="/payments/:id" element={<PaymentForm />} />
              <Route path="/payments/:id/details" element={<PaymentDetails />} />

              
              {/* Reminders routes */}
              <Route path="/reminders" element={<Reminders />} />
              <Route path="/reminders/new" element={<ReminderForm />} />
              <Route path="/reminders/:id" element={<ReminderForm />} />
              <Route path="/reminders/:id/details" element={<ReminderDetails />} />
              
              {/* Clients routes */}
              <Route path="/clients" element={<Clients />} />
              <Route path="/clients/new" element={<ClientForm />} />
              <Route path="/clients/:id" element={<ClientDetails />} />
              <Route path="/clients/edit/:id" element={<ClientForm />} />
              <Route path="/clients/:id/details" element={<ClientDetails />} />
              
              {/* User Profile */}
              <Route path="/profile" element={<Profile />} />

              {/* User Management */}
              <Route path="/utilisateurs" element={<UserManagement />} />
            </Route>
            
            {/* Not found */}
            <Route path="*" element={<NotFound />} />
          </Routes>
        </BrowserRouter>
      </TooltipProvider>
    </AuthProvider>
  </QueryClientProvider>
);

export default App;
