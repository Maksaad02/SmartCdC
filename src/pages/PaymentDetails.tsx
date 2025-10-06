import React, { useState, useEffect } from "react";
import { useParams, Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { formatDate, formatCurrency } from "@/utils/formatters";
import { ArrowLeft, CreditCard, Building, Receipt, Calendar, FileText, Download, Edit } from "lucide-react";
import { useAuth } from "@/contexts/AuthContext";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";

interface Payment {
  id: string;
  numFacture: string;
  montant: number;
  dateReglement: string;
  modePaiement: string;
  reference?: string;
  statut: "EFFECTUE" | "NON_EFFECTUE";
  debt?: {
    numFacture: string;
    clientName: string;
    montantFacture: number;
    solde: number;
    dateEmission: string;
    echeance: string;
  };
}

const PaymentDetails: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const [payment, setPayment] = useState<Payment | null>(null);
  const [loading, setLoading] = useState(true);
  const { authToken } = useAuth();

  const API_URL = "http://localhost:8080/api";

  useEffect(() => {
    const fetchPaymentDetails = async () => {
      try {
        setLoading(true);
        const response = await fetch(`${API_URL}/reglements/${id}`, {
          headers: {
            "Authorization": `Bearer ${authToken}`,
            "Content-Type": "application/json",
          },
        });

        if (!response.ok) {
          throw new Error(`Error ${response.status}`);
        }

        const data = await response.json();
        setPayment(data);
      } catch (error) {
        console.error("Error fetching payment details:", error);
        toast.error("Unable to load payment details");
      } finally {
        setLoading(false);
      }
    };

    if (id) {
      fetchPaymentDetails();
    }
  }, [id, authToken]);

  const getPaymentMethodIcon = (method: string) => {
    switch (method.toLowerCase()) {
      case "virement":
        return <Receipt className="h-5 w-5" />;
      case "cheque":
        return <Receipt className="h-5 w-5" />;
      case "carte":
        return <CreditCard className="h-5 w-5" />;
      case "especes":
        return <CreditCard className="h-5 w-5" />;
      default:
        return <CreditCard className="h-5 w-5" />;
    }
  };

  const getPaymentMethodStyle = (method: string) => {
    switch (method.toLowerCase()) {
      case "virement":
        return "bg-blue-100 text-blue-800";
      case "cheque":
        return "bg-purple-100 text-purple-800";
      case "carte":
        return "bg-green-100 text-green-800";
      case "especes":
        return "bg-yellow-100 text-yellow-800";
      default:
        return "bg-gray-100 text-gray-800";
    }
  };

  const handleDownloadReceipt = async () => {
    try {
      const response = await fetch(`${API_URL}/reglements/${id}/receipt`, {
        headers: {
          "Authorization": `Bearer ${authToken}`,
        },
      });

      if (!response.ok) {
        throw new Error("Failed to download receipt");
      }

      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `receipt-${payment?.reference || id}.pdf`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    } catch (error) {
      console.error("Error downloading receipt:", error);
      toast.error("Unable to download receipt");
    }
  };

  const handleToggleStatus = async () => {
    if (!payment) return;

    const newStatus = payment.statut === "EFFECTUE" ? "NON_EFFECTUE" : "EFFECTUE";
    console.log("Attempting to update status to:", newStatus); // Debug log

    try {
      const response = await fetch(`http://localhost:8080/api/reglements/${id}/status`, {
        method: "PATCH",
        headers: {
          "Authorization": `Bearer ${authToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(newStatus)  // Send just the status string as the backend expects
      });

      console.log("Response status:", response.status); // Debug log

      if (!response.ok) {
        const errorData = await response.text();
        console.error("Error response:", errorData); // Debug log
        throw new Error(`Error ${response.status}: ${errorData}`);
      }

      const updatedPayment = await response.json();
      console.log("Updated payment:", updatedPayment); // Debug log
      setPayment(updatedPayment);
      toast.success("Statut du paiement mis à jour avec succès");
    } catch (error) {
      console.error("Error updating payment status:", error);
      toast.error("Impossible de mettre à jour le statut du paiement");
    }
  };

  if (loading) {
    return <div className="flex items-center justify-center h-96">Loading...</div>;
  }

  if (!payment) {
    return <div className="text-center">Payment not found</div>;
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Link to="/payments">
            <Button variant="outline">
              <ArrowLeft className="h-4 w-4 mr-2" />
              Back
            </Button>
          </Link>
          <h1 className="text-2xl font-bold tracking-tight">Payment Details</h1>
        </div>
        <Button onClick={handleDownloadReceipt}>
          <Download className="h-4 w-4 mr-2" />
          Download Receipt
        </Button>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <CreditCard className="h-5 w-5" />
              Payment Information
            </CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="space-y-4">
              <div>
                <dt className="text-sm text-muted-foreground">Amount</dt>
                <dd className="text-2xl font-bold">{formatCurrency(payment.montant)} MAD</dd>
              </div>
              <div>
                <dt className="text-sm text-muted-foreground">Payment Method</dt>
                <dd>
                  <Badge className={getPaymentMethodStyle(payment.modePaiement)}>
                    {payment.modePaiement === "virement"
                      ? "Virement"
                      : payment.modePaiement === "cheque"
                      ? "Chèque"
                      : payment.modePaiement === "carte"
                      ? "Carte"
                      : payment.modePaiement === "especes"
                      ? "Espèces"
                      : payment.modePaiement}
                  </Badge>
                </dd>
              </div>
              <div>
                <dt className="text-sm text-muted-foreground">Status</dt>
                <dd className="flex items-center gap-2">
                  <Badge className={payment.statut === "EFFECTUE" ? "bg-green-100 text-green-800" : "bg-yellow-100 text-yellow-800"}>
                    {payment.statut === "EFFECTUE" ? "Effectué" : "Non effectué"}
                  </Badge>
                  <Button 
                    variant="outline" 
                    size="sm"
                    onClick={handleToggleStatus}
                    className="ml-2"
                  >
                    {payment.statut === "EFFECTUE" ? "Marquer comme non effectué" : "Marquer comme effectué"}
                  </Button>
                </dd>
              </div>
              <div>
                <dt className="text-sm text-muted-foreground">Payment Date</dt>
                <dd className="flex items-center gap-2">
                  <Calendar className="h-4 w-4 text-muted-foreground" />
                  {formatDate(payment.dateReglement)}
                </dd>
              </div>
              {payment.reference && (
                <div>
                  <dt className="text-sm text-muted-foreground">Reference</dt>
                  <dd>{payment.reference}</dd>
                </div>
              )}
            </dl>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Building className="h-5 w-5" />
              Invoice Information
            </CardTitle>
          </CardHeader>
          <CardContent>
            {payment.debt ? (
              <dl className="space-y-4">
                <div>
                  <dt className="text-sm text-muted-foreground">Invoice Number</dt>
                  <dd className="text-lg font-medium">
                    <Link to={`/debts/${payment.numFacture}`} className="text-primary hover:underline">
                      {payment.debt.numFacture}
                    </Link>
                  </dd>
                </div>
                <div>
                  <dt className="text-sm text-muted-foreground">Client</dt>
                  <dd className="text-primary">
                    {payment.debt.clientName || "Client inconnu"}
                  </dd>
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <dt className="text-sm text-muted-foreground">Invoice Amount</dt>
                    <dd>{formatCurrency(payment.debt.montantFacture)} MAD</dd>
                  </div>
                  <div>
                    <dt className="text-sm text-muted-foreground">Remaining Balance</dt>
                    <dd>{formatCurrency(payment.debt.solde)} MAD</dd>
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <dt className="text-sm text-muted-foreground">Issue Date</dt>
                    <dd>{formatDate(payment.debt.dateEmission)}</dd>
                  </div>
                  <div>
                    <dt className="text-sm text-muted-foreground">Due Date</dt>
                    <dd>{formatDate(payment.debt.echeance)}</dd>
                  </div>
                </div>
              </dl>
            ) : (
              <p className="text-muted-foreground">No invoice information available</p>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

export default PaymentDetails; 