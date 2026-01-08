import React, { useState, useEffect } from "react";
import { useParams, Link } from "react-router-dom";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { toast } from "sonner";
import { useAuth } from "../contexts/AuthContext";
//import { apiClient } from "../utils/api";
import { Debt, Reminder } from "../models/types";
import DebtFormLoading from "@/components/debt/DebtFormLoading";
import StatusBadge, { StatusType } from "../components/StatusBadge";
import { formatDate, formatCurrency } from "../utils/formatters";
import { ArrowLeft, CalendarIcon, FileText, Edit, Calendar, Building, CreditCard, AlertCircle, AlertTriangle } from "lucide-react";
import { Separator } from "@/components/ui/separator";
import { cn } from "@/lib/utils";
import { Badge } from "@/components/ui/badge";
import { Alert, AlertDescription } from "@/components/ui/alert";

interface Payment {
  id: string;
  numFacture: string;
  dateReglement: string;
  montant: number;
  modePaiement: string;
  reference?: string;
}

const DebtDetails: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const { authToken } = useAuth();
  const [loading, setLoading] = useState(true);
  const [debt, setDebt] = useState<Debt | null>(null);
  const [payments, setPayments] = useState<Payment[]>([]);
  const [reminders, setReminders] = useState<Reminder[]>([]);

  const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080/api";

  useEffect(() => {
    const fetchDebtDetails = async () => {
      try {
        setLoading(true);
        const response = await fetch(`${API_URL}/creances/${id}`, {
          headers: {
            "Authorization": `Bearer ${authToken}`,
            "Content-Type": "application/json",
          },
        });

        if (!response.ok) {
          throw new Error(`Error ${response.status}`);
        }

        const debtData = await response.json();
        console.log("Debt data received:", debtData); // Debug log
        setDebt(debtData);

        // Fetch related payments - using numFacture to match the debt
        const paymentsResponse = await fetch(`${API_URL}/reglements?numFacture=${debtData.numFacture}`, {
          headers: {
            "Authorization": `Bearer ${authToken}`,
            "Content-Type": "application/json",
          },
        });
        const paymentsData = await paymentsResponse.json();

        // Additional filter to ensure we only get payments for this specific debt
        const filteredPayments = paymentsData.filter(
          (payment: Payment) => payment.numFacture === debtData.numFacture
        );

        setPayments(filteredPayments);

        // Fetch related reminders - using both numFacture and debtId to ensure we only get reminders for this debt
        const remindersResponse = await fetch(`${API_URL}/relances?numFacture=${debtData.numFacture}&debtId=${id}`, {
          headers: {
            "Authorization": `Bearer ${authToken}`,
            "Content-Type": "application/json",
          },
        });
        const remindersData = await remindersResponse.json();

        // Additional filter to ensure we only get reminders for this specific debt
        const filteredReminders = remindersData.filter(
          (reminder: Reminder) => reminder.numFacture === debtData.numFacture
        );

        setReminders(filteredReminders);

      } catch (error) {
        console.error("Error fetching debt details:", error);
        toast.error("Unable to load debt details");
      } finally {
        setLoading(false);
      }
    };

    if (id) {
      fetchDebtDetails();
    }
  }, [id, authToken]);

  const calculateDaysLate = (dateEcheance: string | undefined): number => {
    if (!dateEcheance) return 0;
    try {
      const today = new Date();
      const dueDate = new Date(dateEcheance);
      return today > dueDate ? Math.floor((today.getTime() - dueDate.getTime()) / (1000 * 60 * 60 * 24)) : 0;
    } catch (error) {
      console.error("Error calculating days late:", error);
      return 0;
    }
  };

  const calculatePaymentStats = () => {
    // Use the debt's actual values instead of calculating from payments
    const totalPaid = debt?.montantEncaisse || 0;
    const totalInvoiced = debt?.montantFacture || 0;
    const remaining = debt?.solde || 0;
    const paymentProgress = totalInvoiced > 0 ? Math.min(100, (totalPaid / totalInvoiced) * 100) : 0;

    return {
      totalPaid,
      totalInvoiced,
      remaining,
      paymentProgress
    };
  };

  if (loading) {
    return (
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-bold tracking-tight">Détails de la créance</h1>
        </div>
        <Card>
          <CardContent className="pt-6">
            <DebtFormLoading />
          </CardContent>
        </Card>
      </div>
    );
  }

  if (!debt) {
    return (
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-bold tracking-tight">Détails de la créance</h1>
        </div>
        <Card>
          <CardContent className="pt-6 text-center">
            <p className="text-lg text-muted-foreground">Créance non trouvée</p>
            <Button className="mt-4" asChild>
              <Link to="/debts">
                <ArrowLeft className="mr-2 h-4 w-4" /> Retour à la liste
              </Link>
            </Button>
          </CardContent>
        </Card>
      </div>
    );
  }

  const daysLate = calculateDaysLate(debt.echeance);
  const isOverdue = daysLate > 0;
  const isPenalized = daysLate >= 60;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center space-x-2">
          <Link to="/debts">
            <Button variant="outline" size="icon">
              <ArrowLeft className="h-4 w-4" />
            </Button>
          </Link>
          <h1 className="text-2xl font-bold tracking-tight">Facture {debt.numFacture}</h1>
        </div>
        <div className="flex space-x-2">
          <Link to={`/debts/${debt.numFacture}/edit`}>
            <Button variant="outline">
              <Edit className="mr-2 h-4 w-4" /> Modifier
            </Button>
          </Link>
          <Link to={`/reminders/new?debtId=${debt.numFacture}`}>
            <Button>Créer une relance</Button>
          </Link>
        </div>
      </div>

      {/* Alert for overdue debts */}
      {isOverdue && (
        <Alert className={isPenalized ? "border-red-200 bg-red-50" : "border-orange-200 bg-orange-50"}>
          <AlertTriangle className="h-4 w-4" />
          <AlertDescription>
            {isPenalized
              ? `Cette créance est en retard de ${daysLate} jours et est automatiquement pénalisée (0.85% par mois après 60 jours).`
              : `Cette créance est en retard de ${daysLate} jours.`
            }
          </AlertDescription>
        </Alert>
      )}

      <div className="grid grid-cols-2 gap-6">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center space-x-2">
              <Building className="h-5 w-5" />
              <span>Détails de la facture</span>
            </CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="space-y-4">
              <div className="flex justify-between">
                <dt className="font-medium text-muted-foreground">Statut</dt>
                <dd><StatusBadge status={debt.statut as StatusType} /></dd>
              </div>
              <div className="flex justify-between">
                <dt className="font-medium text-muted-foreground">Numéro de facture</dt>
                <dd>{debt.numFacture}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="font-medium text-muted-foreground">Client</dt>
                <dd>{debt.clientName || "Client inconnu"}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="font-medium text-muted-foreground">Agent</dt>
                <dd>{debt.agentName || "Non assigné"}</dd>
              </div>
              <Separator />
              <div className="flex justify-between">
                <dt className="font-medium text-muted-foreground">Date d'échéance</dt>
                <dd className="flex items-center">
                  <CalendarIcon className="mr-2 h-4 w-4" />
                  {formatDate(debt.echeance)}
                </dd>
              </div>
              {daysLate > 0 && (
                <div className="flex justify-between">
                  <dt className="font-medium">Jours de retard</dt>
                  <dd className={cn(
                    "font-medium",
                    isPenalized ? "text-red-600" : "text-orange-600"
                  )}>
                    {daysLate} jours
                  </dd>
                </div>
              )}
              <Separator />
              <div className="flex justify-between">
                <dt className="font-medium text-muted-foreground">Montant facturé</dt>
                <dd className="font-semibold">{formatCurrency(debt.montantFacture)} MAD</dd>
              </div>
              <div className="flex justify-between">
                <dt className="font-medium text-muted-foreground">Montant encaissé</dt>
                <dd className="font-semibold text-green-600">{formatCurrency(debt.montantEncaisse)} MAD</dd>
              </div>
              {debt.montantPenalites > 0 && (
                <div className="flex justify-between">
                  <dt className="font-medium text-muted-foreground">Pénalités</dt>
                  <dd className="font-semibold text-red-600">{formatCurrency(debt.montantPenalites)} MAD</dd>
                </div>
              )}
              <div className="flex justify-between">
                <dt className="font-medium text-muted-foreground">Solde restant</dt>
                <dd className="font-semibold text-red-600">{formatCurrency(debt.solde)} MAD</dd>
              </div>
            </dl>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Historique des paiements</CardTitle>
            <CardDescription>
              Liste des règlements associés à cette créance
            </CardDescription>
          </CardHeader>
          <CardContent>
            {payments.length > 0 ? (
              <div className="space-y-4">
                {payments.map((payment) => (
                  <div key={payment.id} className="rounded-md border p-4">
                    <div className="flex justify-between items-center mb-2">
                      <div className="font-medium">{formatDate(payment.dateReglement)}</div>
                      <div className="text-green-600 font-bold">{formatCurrency(payment.montant)} MAD</div>
                    </div>
                    <div className="flex justify-between text-sm text-muted-foreground">
                      <div>Mode: {payment.modePaiement}</div>
                      {payment.reference && <div>Réf: {payment.reference}</div>}
                    </div>
                  </div>
                ))}
                <div className="mt-4 space-y-4 pt-4 border-t">
                  <div className="flex justify-between items-center">
                    <span>Montant facturé</span>
                    <span className="font-bold">{formatCurrency(debt.montantFacture)} MAD</span>
                  </div>
                  <div className="flex justify-between items-center">
                    <span>Total encaissé</span>
                    <span className="font-bold text-green-600">{formatCurrency(debt.montantEncaisse)} MAD</span>
                  </div>
                  {debt.montantPenalites > 0 && (
                    <div className="flex justify-between items-center">
                      <span>Pénalités</span>
                      <span className="font-bold text-red-600">{formatCurrency(debt.montantPenalites)} MAD</span>
                    </div>
                  )}
                  <div className="flex justify-between items-center">
                    <span>Reste à payer</span>
                    <span className="font-bold text-red-600">{formatCurrency(debt.solde)} MAD</span>
                  </div>
                  <div className="mt-2">
                    <div className="flex justify-between text-sm mb-1">
                      <span>Progression du paiement</span>
                      <span>{Math.round(calculatePaymentStats().paymentProgress)}%</span>
                    </div>
                    <div className="w-full bg-gray-200 rounded-full h-2.5">
                      <div
                        className={cn(
                          "h-2.5 rounded-full transition-all duration-300",
                          calculatePaymentStats().paymentProgress === 100 ? "bg-green-600" : "bg-blue-600"
                        )}
                        style={{ width: `${calculatePaymentStats().paymentProgress}%` }}
                      ></div>
                    </div>
                  </div>
                </div>
              </div>
            ) : (
              <div className="text-center py-8 text-muted-foreground">
                <p>Aucun règlement enregistré</p>
                <Button className="mt-4" asChild>
                  <Link to={`/payments/new?debtId=${debt.numFacture}`}>
                    Ajouter un règlement
                  </Link>
                </Button>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <AlertCircle className="h-5 w-5" />
            <span>Historique des relances</span>
          </CardTitle>
        </CardHeader>
        <CardContent>
          {reminders.length > 0 ? (
            <div className="space-y-4">
              {reminders.map((reminder) => (
                <div key={reminder.id} className="flex justify-between items-center p-3 border rounded">
                  <div>
                    <p className="font-medium capitalize">{reminder.typeRelance}</p>
                    <p className="text-sm text-muted-foreground">{formatDate(reminder.dateRelance)}</p>
                  </div>
                  <div className="text-right">
                    <Badge className={cn(
                      "capitalize",
                      reminder.statutRelance === "en_attente" ? "bg-gray-500" :
                        reminder.statutRelance === "envoyee" ? "bg-orange-500" :
                          "bg-green-500",
                      "text-white"
                    )}>
                      {reminder.statutRelance === "en_attente" ? "En attente" :
                        reminder.statutRelance === "envoyee" ? "Envoyée" :
                          "Répondue"}
                    </Badge>
                    {reminder.commentaire && (
                      <p className="text-sm text-muted-foreground mt-1">{reminder.commentaire}</p>
                    )}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-muted-foreground text-center">Aucune relance envoyée</p>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

export default DebtDetails;