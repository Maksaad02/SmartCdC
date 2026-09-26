// import { Toaster } from "@/components/ui/toaster";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ApiError } from "@/lib/apiClient";
import { Suspense, lazy } from "react";
import { BrowserRouter, Routes, Route, Navigate, useLocation } from "react-router-dom";
import { AuthProvider } from "./contexts/AuthContext";
import ErrorBoundary from "./components/ErrorBoundary";
import RequireAdmin from "./components/RequireAdmin";


// Pages : chargees a la demande (un chunk par page) pour ne pas telecharger d'un coup toute
// l'application, dont les bibliotheques d'export (xlsx, PDF), des la page de connexion.
const Dashboard = lazy(() => import("./pages/Dashboard"));
const Login = lazy(() => import("./pages/Login"));
const Debts = lazy(() => import("./pages/Debts"));
const DebtForm = lazy(() => import("./pages/DebtForm"));
const Payments = lazy(() => import("./pages/Payments"));
const PaymentForm = lazy(() => import("./pages/PaymentForm"));
const Reminders = lazy(() => import("./pages/Reminders"));
const ReminderForm = lazy(() => import("./pages/ReminderForm"));
const Clients = lazy(() => import("./pages/Clients"));
const ClientForm = lazy(() => import("./pages/ClientForm"));
const ClientDetails = lazy(() => import("./pages/ClientDetails"));
const Profile = lazy(() => import("./pages/Profile"));
const NotFound = lazy(() => import("./pages/NotFound"));
const PaymentDetails = lazy(() => import("./pages/PaymentDetails"));
const DebtDetails = lazy(() => import("./pages/DebtDetails"));
const ReminderDetails = lazy(() => import("./pages/ReminderDetails"));
const UserManagement = lazy(() => import("./pages/UserManagement"));
const Departements = lazy(() => import("./pages/Departements"));
import Layout from "./components/Layout";

// Reessais utiles seulement pour les pannes passageres (reseau, 5xx) : jamais pour une
// erreur du client (400/401/403/404), qui ne se corrigera pas en reessayant.
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error) =>
        !(error instanceof ApiError && error.status >= 400 && error.status < 500) && failureCount < 2,
      staleTime: 30_000,
      refetchOnWindowFocus: false,
    },
  },
});

// Boundary de page : reinitialisee a chaque changement de route, et "Reessayer"
// remonte reellement la page fautive.
const RouteBoundary = ({ children }: { children: React.ReactNode }) => {
  const location = useLocation();
  return (
    <ErrorBoundary resetKey={location.pathname} variant="page">
      <Suspense fallback={<div className="flex justify-center py-16"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" /></div>}>
        {children}
      </Suspense>
    </ErrorBoundary>
  );
};

const App = () => (
  <QueryClientProvider client={queryClient}>
    <AuthProvider>
      <TooltipProvider>
        {/* <Toaster /> */}
        <Sonner />
        <BrowserRouter>
          <RouteBoundary>
          <Routes>
            {/* Root redirect to login */}
            <Route path="/" element={<Navigate to="/login" replace />} />
            
            {/* Auth routes */}
            <Route path="/login" element={<Login />} />
            
            {/* Protected routes */}
            <Route element={<Layout />}>
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

              {/* Departements (ADMIN) */}
              <Route
                path="/departements"
                element={
                  <RequireAdmin>
                    <Departements />
                  </RequireAdmin>
                }
              />

              {/* User Management */}
              <Route
                path="/utilisateurs"
                element={
                  <RequireAdmin>
                    <UserManagement />
                  </RequireAdmin>
                }
              />
            </Route>
            
            {/* Not found */}
            <Route path="*" element={<NotFound />} />
          </Routes>
          </RouteBoundary>
        </BrowserRouter>
      </TooltipProvider>
    </AuthProvider>
  </QueryClientProvider>
);

export default App;
