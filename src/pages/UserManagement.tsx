import React, { useEffect, useState } from 'react';
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Input } from '@/components/ui/input';
import { useToast } from '@/hooks/use-toast';
import { Users, UserCheck, UserX, Search } from 'lucide-react';

interface User {
  id: number;
  nom: string;
  email: string;
  role: 'admin' | 'agent';
}

const UserManagement: React.FC = () => {
  const { toast } = useToast();
  const [users, setUsers] = useState<User[]>([]);
  const [searchTerm, setSearchTerm] = useState('');

  // ✅ Charger tous les utilisateurs au montage
  useEffect(() => {
    fetchUsers();
  }, []);

  const fetchUsers = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/utilisateurs', {
        headers: {
          Authorization: `Bearer ${localStorage.getItem('auth_token')}`,
        },
      });
      const data = await response.json();
      console.log('Received users data:', data); // Debug log

      // Make sure we properly map the roles from the backend
      const mappedUsers = data.map(user => ({
        ...user,
        // Ensure role is lowercase to match our type
        role: user.role?.toLowerCase() === 'admin' ? 'admin' : 'agent'
      }));

      console.log('Mapped users:', mappedUsers); // Debug log
      setUsers(mappedUsers);
    } catch (error) {
      console.error('Error in fetchUsers:', error);
      toast({
        title: 'Erreur',
        description: "Impossible de récupérer les utilisateurs.",
      });
    }
  };

  // 🔁 Modifier le rôle
  const handleRoleChange = async (userId: number, newRole: 'admin' | 'agent') => {
    try {
      console.log('Updating role for user:', userId, 'to:', newRole); // Debug log
      
      await fetch(`http://localhost:8080/api/utilisateurs/${userId}/role`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${localStorage.getItem('auth_token')}`,
        },
        body: JSON.stringify({ role: newRole }),
      });

      toast({
        title: 'Rôle modifié',
        description: `L'utilisateur a maintenant le rôle ${newRole === 'admin' ? 'Administrateur' : 'Agent'}.`,
      });

      fetchUsers(); // rafraîchir la liste
    } catch (error) {
      console.error('Error in handleRoleChange:', error);
      toast({
        title: 'Erreur',
        description: 'Impossible de modifier le rôle.',
      });
    }
  };

  const filteredUsers = users.filter((user) =>
    user.nom.toLowerCase().includes(searchTerm.toLowerCase()) ||
    user.email.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const adminCount = users.filter((user) => user.role === 'admin').length;
  const agentCount = users.filter((user) => user.role === 'agent').length;

  return (
    <div className="space-y-6">
      {/* Statistiques */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card>
          <CardContent className="p-4 flex items-center space-x-2">
            <Users className="h-4 w-4 text-blue-600" />
            <div>
              <p className="text-sm text-gray-600">Total Utilisateurs</p>
              <p className="text-2xl font-bold">{users.length}</p>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4 flex items-center space-x-2">
            <UserCheck className="h-4 w-4 text-green-600" />
            <div>
              <p className="text-sm text-gray-600">Administrateurs</p>
              <p className="text-2xl font-bold">{adminCount}</p>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4 flex items-center space-x-2">
            <UserX className="h-4 w-4 text-orange-600" />
            <div>
              <p className="text-sm text-gray-600">Agents</p>
              <p className="text-2xl font-bold">{agentCount}</p>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Gestion des utilisateurs */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Users className="h-5 w-5" />
            Gestion des Utilisateurs
          </CardTitle>
        </CardHeader>

        <CardContent>
          {/* Recherche */}
          <div className="mb-6">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 h-4 w-4" />
              <Input
                placeholder="Rechercher par nom ou email..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="pl-10 max-w-md"
              />
            </div>
          </div>

          {/* Tableau */}
          <div className="rounded-md border overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-1/3 align-middle">Utilisateur</TableHead>
                  <TableHead className="w-1/3 align-middle">Rôle</TableHead>
                  <TableHead className="w-1/3 text-right align-middle"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {filteredUsers.map((user) => (
                  <TableRow key={user.id}>
                    <TableCell className="align-middle">
                      <div className="flex items-center space-x-3">
                        <div className="w-8 h-8 bg-blue-600 text-white rounded-full flex items-center justify-center font-bold text-sm">
                          {user.nom
                            .split(' ')
                            .map((n) => n[0])
                            .join('')
                            .toUpperCase()}
                        </div>
                        <div>
                          <div className="font-medium text-gray-900">{user.nom}</div>
                          <div className="text-sm text-gray-500">{user.email}</div>
                        </div>
                      </div>
                    </TableCell>

                    <TableCell className="align-middle">
                      <span
                        className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${
                          user.role === 'admin'
                            ? 'bg-red-100 text-red-800'
                            : 'bg-blue-100 text-blue-800'
                        }`}
                      >
                        {user.role === 'admin' ? 'Administrateur' : 'Agent'}
                      </span>
                    </TableCell>

                    <TableCell className="text-right align-middle">
                      <Select
                        value={user.role}
                        onValueChange={(value: 'admin' | 'agent') =>
                          handleRoleChange(user.id, value)
                        }
                      >
                        <SelectTrigger className="w-32 h-8 text-sm">
                          <SelectValue placeholder={user.role === 'admin' ? 'Administrateur' : 'Agent'} />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="admin">Administrateur</SelectItem>
                          <SelectItem value="agent">Agent</SelectItem>
                        </SelectContent>
                      </Select>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

            {filteredUsers.length === 0 && (
              <div className="text-center py-12">
                <Users className="h-12 w-12 text-gray-400 mx-auto mb-4" />
                <p className="text-gray-500 text-lg font-medium">Aucun utilisateur trouvé</p>
                <p className="text-gray-400">Essayez un autre critère</p>
              </div>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
};

export default UserManagement;