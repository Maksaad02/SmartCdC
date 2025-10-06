import React, { useState, useEffect } from "react";
import { useParams, Link, useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { formatDate, formatCurrency } from "@/utils/formatters";
import { ArrowLeft, Building, Mail, Phone, MapPin, FileText, CreditCard, Trash2, PenSquare } from "lucide-react";
import { Client, Debt } from "@/models/types";
import { useAuth } from "@/contexts/AuthContext";
import { toast } from "sonner";
import StatusBadge, { StatusType } from "@/components/StatusBadge";
import jsPDF from "jspdf";
import html2canvas from "html2canvas";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";

const API_URL = "http://localhost:8080/api";

const ClientDetails: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [client, setClient] = useState<Client | null>(null);
  const [debts, setDebts] = useState<Debt[]>([]);
  const [loading, setLoading] = useState(true);
  const { authToken, currentUser } = useAuth();

  const generatePdf = async () => {
    const input = document.getElementById("client-details-pdf");

    if (!input) {
      toast.error("Élément introuvable pour export");
      return;
    }

    try {
      const canvas = await html2canvas(input);
      const imgData = canvas.toDataURL("image/png");

      const pdf = new jsPDF("p", "mm", "a4");
      const pdfWidth = pdf.internal.pageSize.getWidth();
      const pdfHeight = pdf.internal.pageSize.getHeight();
      const scale = pdfWidth / canvas.width;
      const scaledHeight = canvas.height * scale;

      pdf.addImage(imgData, "PNG", 0, 0, pdfWidth, scaledHeight);
      pdf.save(`client-details-${client?.raisonSociale || "rapport"}.pdf`);
      
      toast.success("Rapport généré avec succès");
    } catch (error) {
      console.error("Error generating PDF:", error);
      toast.error("Erreur lors de la génération du rapport");
    }
  };

  useEffect(() => {
    const fetchClientDetails = async () => {
      try {
        setLoading(true);
        const response = await fetch(`${API_URL}/clients/${id}`, {
          headers: {
            "Authorization": `Bearer ${authToken}`,
            "Content-Type": "application/json",
          },
        });

        if (!response.ok) {
          throw new Error(`Error ${response.status}`);
        }

        const clientData = await response.json();
        setClient(clientData);

        // Fetch client's debts
        const debtsResponse = await fetch(`${API_URL}/creances`, {
          headers: {
            "Authorization": `Bearer ${authToken}`,
            "Content-Type": "application/json",
          },
        });
        const allDebts = await debtsResponse.json();
        
        // Filter debts to only include those belonging to this client
        const clientDebts = allDebts.filter((debt: Debt) => 
          debt.clientName === clientData.raisonSociale
        );
        
        setDebts(clientDebts);
      } catch (error) {
        console.error("Error fetching client details:", error);
        toast.error("Unable to load client details");
      } finally {
        setLoading(false);
      }
    };

    if (id) {
      fetchClientDetails();
    }
  }, [id, authToken]);

  const calculateTotalDebt = () => {
    return debts.reduce((total, debt) => total + debt.solde, 0);
  };

  const calculateOverdueDebt = () => {
    const today = new Date();
    return debts
      .filter(debt => new Date(debt.echeance) < today)
      .reduce((total, debt) => total + debt.solde, 0);
  };

  const handleDelete = async () => {
    try {
      const response = await fetch(`${API_URL}/clients/${id}`, {
        method: "DELETE",
        headers: {
          "Authorization": `Bearer ${authToken}`,
          "Content-Type": "application/json",
        },
      });

      if (!response.ok) {
        throw new Error(`Error ${response.status}`);
      }

      toast.success("Client supprimé avec succès");
      navigate("/clients");
    } catch (error) {
      console.error("Error deleting client:", error);
      toast.error("Impossible de supprimer le client");
    }
  };

  if (loading) {
    return <div className="flex items-center justify-center h-96">Loading...</div>;
  }

  if (!client) {
    return <div className="text-center">Client not found</div>;
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Link to="/clients">
            <Button variant="outline">
              <ArrowLeft className="h-4 w-4 mr-2" />
              Back
            </Button>
          </Link>
          <h1 className="text-2xl font-bold tracking-tight">Details Client</h1>
        </div>
        <div className="flex items-center gap-2">
          <Link to={`/clients/edit/${id}`}>
            <Button variant="outline" size="sm">
              <PenSquare className="h-4 w-4 mr-2" />
              Modifier
            </Button>
          </Link>
          <Button onClick={generatePdf}>
            <FileText className="h-4 w-4 mr-2" />
            Generer Rapport
          </Button>
          {currentUser?.role === 'admin' && (
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button variant="destructive" size="sm">
                  <Trash2 className="h-4 w-4 mr-2" />
                  Supprimer
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>Êtes-vous sûr de vouloir supprimer ce client ?</AlertDialogTitle>
                  <AlertDialogDescription>
                    Cette action est irréversible. Le client et toutes ses données associées seront définitivement supprimés.
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>Annuler</AlertDialogCancel>
                  <AlertDialogAction onClick={handleDelete} className="bg-red-600 hover:bg-red-700">
                    Supprimer
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          )}
        </div>
      </div>

      <div id="client-details-pdf" className="grid gap-6 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Building className="h-5 w-5" />
              Information Client
            </CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="space-y-4">
              <div>
                <dt className="text-sm text-muted-foreground">Company Name</dt>
                <dd className="text-lg font-medium">{client.raisonSociale}</dd>
              </div>
              {client.rc && (
                <div>
                  <dt className="text-sm text-muted-foreground">Registration Number</dt>
                  <dd>{client.rc}</dd>
                </div>
              )}
              {client.identiteFiscale && (
                <div>
                  <dt className="text-sm text-muted-foreground">Identité Fiscale</dt>
                  <dd>{client.identiteFiscale}</dd>
                </div>
              )}
              {client.ice && (
                <div>
                  <dt className="text-sm text-muted-foreground">ICE</dt>
                  <dd>{client.ice}</dd>
                </div>
              )}
              <div className="flex items-center gap-2">
                <Mail className="h-4 w-4 text-muted-foreground" />
                <span>{client.email}</span>
              </div>
              <div className="flex items-center gap-2">
                <Phone className="h-4 w-4 text-muted-foreground" />
                <span>{client.telephone}</span>
              </div>
              <div className="flex items-start gap-2">
                <MapPin className="h-4 w-4 text-muted-foreground mt-1" />
                <span>{client.adresse}</span>
              </div>
            </dl>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <CreditCard className="h-5 w-5" />
              Aperçu financier
            </CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="space-y-4">
              <div>
                <dt className="text-sm text-muted-foreground">Total Outstanding</dt>
                <dd className="text-2xl font-bold">{formatCurrency(calculateTotalDebt())}</dd>
              </div>
              <div>
                <dt className="text-sm text-muted-foreground">Overdue Amount</dt>
                <dd className="text-xl font-semibold text-red-600">{formatCurrency(calculateOverdueDebt())}</dd>
              </div>
              <div>
                <dt className="text-sm text-muted-foreground">Active Invoices</dt>
                <dd className="text-xl font-semibold">{debts.length}</dd>
              </div>
            </dl>
          </CardContent>
        </Card>

        <Card className="md:col-span-2">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <FileText className="h-5 w-5" />
              Historique des Factures
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b">
                    <th className="text-left py-3 px-4">Facture #</th>
                    <th className="text-left py-3 px-4">Echeance</th>
                    <th className="text-right py-3 px-4">Montant</th>
                    <th className="text-right py-3 px-4">Balance</th>
                    <th className="text-center py-3 px-4">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {debts.map((debt) => (
                    <tr key={debt.id} className="hover:bg-muted/50">
                      <td className="py-3 px-4">
                        <Link to={`/debts/${debt.id}`} className="text-primary hover:underline">
                          {debt.numFacture}
                        </Link>
                      </td>
                      <td className="py-3 px-4">{formatDate(debt.echeance)}</td>
                      <td className="py-3 px-4 text-right">{formatCurrency(debt.montantFacture)}</td>
                      <td className="py-3 px-4 text-right">{formatCurrency(debt.solde)}</td>
                      <td className="py-3 px-4">
                        <div className="flex justify-center">
                          <StatusBadge status={debt.statut as StatusType} />
                        </div>
                      </td>
                    </tr>
                  ))}
                  {debts.length === 0 && (
                    <tr>
                      <td colSpan={6} className="py-8 text-center text-muted-foreground">
                        No invoices found
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

export default ClientDetails; 