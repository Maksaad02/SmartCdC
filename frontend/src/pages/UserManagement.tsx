import React, { useState } from 'react';
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
import { Input } from '@/components/ui/input';
import { Users, UserCheck, UserCog, UserX, Search } from 'lucide-react';
import { utilisateurSchema } from '@/schemas';
import { usePagedList, useTotalCount } from '@/lib/pagedQueries';
import PaginationBar from '@/components/PaginationBar';
import QueryError from '@/components/QueryError';
import CreateUserDialog from '@/components/CreateUserDialog';
import EditUserRoleDialog from '@/components/EditUserRoleDialog';

const ROLE_LABELS: Record<string, { label: string; className: string }> = {
  ADMIN: { label: 'Administrateur', className: 'bg-red-100 text-red-800' },
  MANAGER: { label: 'Gestionnaire', className: 'bg-purple-100 text-purple-800' },
  AGENT: { label: 'Agent', className: 'bg-blue-100 text-blue-800' },
};

const UserManagement: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState('');

  // Liste paginee et recherche cote serveur ; les compteurs viennent de requetes de taille 1
  // (auparavant ils etaient calcules en telechargeant tous les utilisateurs).
  const { items: users, data, setPage, isLoading, isFetching, error, refetch } =
    usePagedList('/utilisateurs', utilisateurSchema, { q: searchTerm });
  const { data: totalCount = 0 } = useTotalCount('/utilisateurs', utilisateurSchema);
  const { data: adminCount = 0 } = useTotalCount('/utilisateurs', utilisateurSchema, { role: 'ADMIN' });
  const { data: managerCount = 0 } = useTotalCount('/utilisateurs', utilisateurSchema, { role: 'MANAGER' });
  const { data: agentCount = 0 } = useTotalCount('/utilisateurs', utilisateurSchema, { role: 'AGENT' });

  return (
    <div className="space-y-6">
      {/* Statistiques */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <Card>
          <CardContent className="p-4 flex items-center space-x-2">
            <Users className="h-4 w-4 text-blue-600" />
            <div>
              <p className="text-sm text-gray-600">Total Utilisateurs</p>
              <p className="text-2xl font-bold">{totalCount}</p>
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
            <UserCog className="h-4 w-4 text-purple-600" />
            <div>
              <p className="text-sm text-gray-600">Gestionnaires</p>
              <p className="text-2xl font-bold">{managerCount}</p>
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
        <CardHeader className="flex flex-row items-center justify-between space-y-0">
          <CardTitle className="flex items-center gap-2">
            <Users className="h-5 w-5" />
            Gestion des Utilisateurs
          </CardTitle>
          <CreateUserDialog />
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
                  <TableHead className="align-middle">Utilisateur</TableHead>
                  <TableHead className="align-middle">Rôle</TableHead>
                  <TableHead className="align-middle">Département</TableHead>
                  <TableHead className="text-right align-middle"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {users.map((user) => {
                  const role = ROLE_LABELS[user.role ?? ''] ?? ROLE_LABELS.AGENT;
                  return (
                    <TableRow key={user.id}>
                      <TableCell className="align-middle">
                        <div className="flex items-center space-x-3">
                          <div className="w-8 h-8 bg-blue-600 text-white rounded-full flex items-center justify-center font-bold text-sm">
                            {(user.nom ?? user.email)
                              .split(' ')
                              .map((n) => n[0])
                              .join('')
                              .toUpperCase()}
                          </div>
                          <div>
                            <div className="font-medium text-gray-900">{user.nom ?? user.email}</div>
                            <div className="text-sm text-gray-500">{user.email}</div>
                          </div>
                        </div>
                      </TableCell>

                      <TableCell className="align-middle">
                        <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${role.className}`}>
                          {role.label}
                        </span>
                      </TableCell>

                      <TableCell className="align-middle text-sm">
                        {user.departementNom ?? <span className="text-gray-400">Tous (entreprise)</span>}
                      </TableCell>

                      <TableCell className="text-right align-middle">
                        <EditUserRoleDialog user={user} />
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>

            {isLoading && <p className="py-8 text-center text-gray-500">Chargement…</p>}
            {error && !data && (
              <div className="p-4"><QueryError what="les utilisateurs" error={error} onRetry={() => refetch()} /></div>
            )}
            {!isLoading && !error && users.length === 0 && (
              <div className="text-center py-12">
                <Users className="h-12 w-12 text-gray-400 mx-auto mb-4" />
                <p className="text-gray-500 text-lg font-medium">Aucun utilisateur trouvé</p>
                <p className="text-gray-400">Essayez un autre critère</p>
              </div>
            )}
          </div>

          <PaginationBar data={data} onPageChange={setPage} isFetching={isFetching} />
        </CardContent>
      </Card>
    </div>
  );
};

export default UserManagement;
