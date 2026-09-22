# Logique de Calcul des Pénalités

## Règles Métier

### 1. Calcul du Statut
- **< 60 jours de retard** : `EN_RETARD`
- **≥ 60 jours de retard** : `PENALISEE`

### 2. Calcul des Pénalités
- **Seuil de pénalisation** : 60 jours
- **Taux mensuel** : 0.85% par mois
- **Base de calcul** : Montant initial de la facture
- **Période** : Mois complets après 60 jours

### 3. Formule de Calcul
```
Pénalités = Montant_Facture × 0.85% × Nombre_Mois_Après_60_Jours
```

## Exemples de Calculs

| Jours de Retard | Statut | Mois après 60j | Pénalités (10,000 DH) |
|-----------------|--------|----------------|----------------------|
| 30 jours | EN_RETARD | 0 | 0 DH |
| 60 jours | PENALISEE | 0 | 0 DH |
| 90 jours | PENALISEE | 1 | 85 DH |
| 120 jours | PENALISEE | 2 | 170 DH |
| 150 jours | PENALISEE | 3 | 255 DH |

## Implémentation Technique

### Services
- `PenaliteService` : Calcul des pénalités
- `CreanceService` : Intégration avec les opérations CRUD

### Champs Ajoutés
- `montantPenalites` : Montant des pénalités calculées
- `dateCalculPenalites` : Date du dernier calcul (optimisation)

### Méthodes Principales
- `calculerPenalites(Creance)` : Calcul des pénalités
- `calculerJoursRetard(Creance)` : Calcul des jours de retard
- `mettreAJourPenalites(Creance)` : Mise à jour automatique

## Optimisations

1. **Cache des calculs** : Évite les recalculs inutiles
2. **Calcul à la demande** : Seulement quand nécessaire
3. **Mise à jour automatique** : À chaque consultation

## API Endpoints

- `GET /api/creances` : Retourne les créances avec pénalités calculées
- `POST /api/creances/recalculer-penalites` : Force le recalcul de toutes les pénalités 