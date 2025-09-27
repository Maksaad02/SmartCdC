import React, { useState, useEffect } from "react";
import { useParams, Link, useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { formatDate } from "@/utils/formatters";
import { ArrowLeft, Mail, Phone, FileText, Building, Calendar, Trash2 } from "lucide-react";
import { Reminder } from "@/models/types";
import { useAuth } from "@/contexts/AuthContext";
import { toast } from "sonner";
import StatusBadge, { StatusType } from "@/components/StatusBadge";
import { Badge } from "@/components/ui/badge";
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

const ReminderDetails: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [reminder, setReminder] = useState<Reminder | null>(null);
  const [loading, setLoading] = useState(true);
  const { authToken, currentUser } = useAuth();

  const API_URL = "http://34.226.195.59:8080/api";

  useEffect(() => {
    const fetchReminderDetails = async () => {
      try {
        setLoading(true);
        const response = await fetch(`${API_URL}/relances/${id}`, {
          headers: {
            "Authorization": `Bearer ${authToken}`,
            "Content-Type": "application/json",
          },
        });

        if (!response.ok) {
          throw new Error(`Error ${response.status}`);
        }

        const data = await response.json();
        setReminder(data);
      } catch (error) {
        console.error("Error fetching reminder details:", error);
        toast.error("Unable to load reminder details");
      } finally {
        setLoading(false);
      }
    };

    if (id) {
      fetchReminderDetails();
    }
  }, [id, authToken]);

  const getReminderTypeIcon = (type: string) => {
    switch (type) {
      case "email":
        return <Mail className="h-5 w-5" />;
      case "telephone":
        return <Phone className="h-5 w-5" />;
      case "courrier":
        return <FileText className="h-5 w-5" />;
      default:
        return <Mail className="h-5 w-5" />;
    }
  };

  const getTypeStyle = (type: string) => {
    switch (type) {
      case "email":
        return "bg-blue-100 text-blue-800";
      case "telephone":
        return "bg-orange-100 text-orange-800";
      case "courrier":
        return "bg-purple-100 text-purple-800";
      default:
        return "bg-gray-100 text-gray-800";
    }
  };

  const handleDelete = async () => {
    try {
      const response = await fetch(`${API_URL}/relances/${id}`, {
        method: "DELETE",
        headers: {
          "Authorization": `Bearer ${authToken}`,
          "Content-Type": "application/json",
        },
      });

      if (!response.ok) {
        throw new Error(`Error ${response.status}`);
      }

      toast.success("Relance supprimée avec succès");
      navigate("/reminders");
    } catch (error) {
      console.error("Error deleting reminder:", error);
      toast.error("Impossible de supprimer la relance");
    }
  };

  if (loading) {
    return <div className="flex items-center justify-center h-96">Loading...</div>;
  }

  if (!reminder) {
    return <div className="text-center">Reminder not found</div>;
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Link to="/reminders">
            <Button variant="outline">
              <ArrowLeft className="h-4 w-4 mr-2" />
              Retour
            </Button>
          </Link>
          <h1 className="text-2xl font-bold tracking-tight">Détails de la relance</h1>
        </div>
        <div className="flex items-center gap-2">
          <StatusBadge status={reminder.statutRelance.toUpperCase() as StatusType} />
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
                  <AlertDialogTitle>Êtes-vous sûr de vouloir supprimer cette relance ?</AlertDialogTitle>
                  <AlertDialogDescription>
                    Cette action est irréversible. La relance sera définitivement supprimée.
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

      <div className="grid gap-6 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              {getReminderTypeIcon(reminder.typeRelance)}
              Reminder Information
            </CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="space-y-4">
              <div>
                <dt className="text-sm text-muted-foreground">Type</dt>
                <dd>
                  <Badge className={getTypeStyle(reminder.typeRelance)}>
                    {reminder.typeRelance}
                  </Badge>
                </dd>
              </div>
              <div>
                <dt className="text-sm text-muted-foreground">Status</dt>
                <dd>
                  <StatusBadge status={reminder.statutRelance.toUpperCase() as StatusType} />
                </dd>
              </div>
              <div>
                <dt className="text-sm text-muted-foreground">Reminder Date</dt>
                <dd>{formatDate(reminder.dateRelance)}</dd>
              </div>
              {reminder.commentaire && (
                <div>
                  <dt className="text-sm text-muted-foreground">Comments</dt>
                  <dd className="whitespace-pre-wrap">{reminder.commentaire}</dd>
                </div>
              )}
            </dl>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Building className="h-5 w-5" />
              Related Debt Information
            </CardTitle>
          </CardHeader>
          <CardContent>
            {reminder.creance ? (
              <dl className="space-y-4">
                <div>
                  <dt className="text-sm text-muted-foreground">Invoice Number</dt>
                  <dd className="text-lg font-medium">{reminder.numFacture}</dd>
                </div>
                <div>
                  <dt className="text-sm text-muted-foreground">Client</dt>
                  <dd>{reminder.creance.clientName || "Client inconnu"}</dd>
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <dt className="text-sm text-muted-foreground">Issue Date</dt>
                    <dd>{formatDate(reminder.creance.dateEmission)}</dd>
                  </div>
                  <div>
                    <dt className="text-sm text-muted-foreground">Due Date</dt>
                    <dd>{formatDate(reminder.creance.echeance)}</dd>
                  </div>
                </div>
                <div>
                  <dt className="text-sm text-muted-foreground">Status</dt>
                  <dd>
                    <StatusBadge status={reminder.creance.statut.toUpperCase() as StatusType} />
                  </dd>
                </div>
              </dl>
            ) : (
              <p className="text-muted-foreground">No related debt information available</p>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

export default ReminderDetails; 