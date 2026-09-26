# Database Schema Description for AI Text-to-SQL

This document provides a comprehensive schema description that the AI service will use to generate SQL queries from natural language.

## Schema Overview

The RecouvTek database contains the following main entities:

### Tables

#### 1. `creance` (Debt/Invoice)
**Description:** Represents a debt or invoice that needs to be collected.

**Columns:**
- `id` (BIGINT, PRIMARY KEY): Unique identifier
- `num_facture` (VARCHAR, UNIQUE): Invoice number
- `date_emission` (DATE): Invoice emission date
- `echeance` (DATE): Due date for payment
- `montant_facture` (DOUBLE): Original invoice amount
- `montant_encaisse` (DOUBLE): Amount already paid
- `montant_penalites` (DOUBLE): Penalty amount (default: 0.0)
- `date_calcul_penalites` (DATE): Date when penalties were calculated
- `statut` (ENUM): Debt status - values: PAYEE, IMPAYEE, EN_RETARD, PENALISEE, PARTIELLEMENT_PAYEE
- `agent_recouv` (BIGINT, FOREIGN KEY): Reference to utilisateur.id (collection agent)
- `client_id` (BIGINT, FOREIGN KEY): Reference to client.id

**Business Rules:**
- `statut = PAYEE`: Debt is fully paid (montant_encaisse >= montant_facture)
- `statut = IMPAYEE`: No payment received yet
- `statut = EN_RETARD`: Payment is overdue (days between echeance and today > 0)
- `statut = PENALISEE`: Overdue by 60+ days
- `statut = PARTIELLEMENT_PAYEE`: Partial payment received (0 < montant_encaisse < montant_facture)

**Computed Fields:**
- `solde` = (montant_facture + montant_penalites) - montant_encaisse (remaining balance)
- `montant_total` = montant_facture + montant_penalites (total due)
- `jours_retard` = days between echeance and today (if overdue)

#### 2. `client` (Client/Debtor)
**Description:** Represents a client or debtor who owes money.

**Columns:**
- `id` (BIGINT, PRIMARY KEY): Unique identifier
- `raison_sociale` (VARCHAR, UNIQUE): Company name or legal name
- `email` (VARCHAR, UNIQUE): Email address
- `telephone` (VARCHAR, UNIQUE): Phone number
- `rc` (VARCHAR, UNIQUE): Registration number (RC)
- `adresse` (VARCHAR, UNIQUE): Address
- `ice` (VARCHAR, UNIQUE): ICE (Identifiant Commun de l'Entreprise)
- `identite_fiscale` (VARCHAR, UNIQUE): Tax identification number
- `id_agent_recouv` (BIGINT, FOREIGN KEY): Reference to utilisateur.id (assigned collection agent)

#### 3. `reglement` (Payment)
**Description:** Represents a payment made towards a debt.

**Columns:**
- `id` (BIGINT, PRIMARY KEY): Unique identifier
- `montant` (DOUBLE): Payment amount
- `date_reglement` (DATE): Payment date
- `mode_paiement` (ENUM): Payment method - values: ESPECE, CHEQUE, VIREMENT, CARTE
- `statut` (ENUM): Payment status - values: EFFECTUE, NON_EFFECTUE
- `reference` (VARCHAR): Payment reference number
- `creance_id` (BIGINT, FOREIGN KEY): Reference to creance.id
- `id_agent_recouv` (BIGINT, FOREIGN KEY): Reference to utilisateur.id

#### 4. `relance` (Reminder)
**Description:** Represents a reminder sent to a client about an unpaid debt.

**Columns:**
- `id` (BIGINT, PRIMARY KEY): Unique identifier
- `date_relance` (DATE): Reminder date
- `type_relance` (ENUM): Reminder type - values: EMAIL, TELEPHONE, LETTRE
- `statut` (ENUM): Reminder status - values: ENVOYE, EN_ATTENTE, ECHOUE
- `creance_id` (BIGINT, FOREIGN KEY): Reference to creance.id
- `id_agent_recouv` (BIGINT, FOREIGN KEY): Reference to utilisateur.id

#### 5. `utilisateur` (User/Agent)
**Description:** Represents a system user, typically a collection agent.

**Columns:**
- `id` (BIGINT, PRIMARY KEY): Unique identifier
- `nom` (VARCHAR): First name
- `prenom` (VARCHAR): Last name
- `email` (VARCHAR, UNIQUE): Email address
- `password` (VARCHAR): Encrypted password
- `role` (ENUM): User role - values: ADMIN, AGENT_RECOUVREMENT

### Relationships

1. **Client → Creance**: One-to-Many (one client can have multiple debts)
2. **Creance → Reglement**: One-to-Many (one debt can have multiple payments)
3. **Creance → Relance**: One-to-Many (one debt can have multiple reminders)
4. **Utilisateur → Creance**: One-to-Many (one agent can manage multiple debts)
5. **Utilisateur → Client**: One-to-Many (one agent can manage multiple clients)

## Common Query Patterns

### Status Queries
- "How many debts are in progress?" → `SELECT COUNT(*) FROM creance WHERE statut IN ('IMPAYEE', 'EN_RETARD', 'PENALISEE', 'PARTIELLEMENT_PAYEE')`
- "Show me all paid debts" → `SELECT * FROM creance WHERE statut = 'PAYEE'`
- "List overdue debts" → `SELECT * FROM creance WHERE statut IN ('EN_RETARD', 'PENALISEE')`

### Amount Queries
- "What's the total amount of unpaid debts?" → `SELECT SUM(montant_facture + montant_penalites - montant_encaisse) FROM creance WHERE statut != 'PAYEE'`
- "Show debts over 1000" → `SELECT * FROM creance WHERE montant_facture > 1000`
- "Total collected this month" → `SELECT SUM(montant) FROM reglement WHERE MONTH(date_reglement) = MONTH(CURRENT_DATE) AND YEAR(date_reglement) = YEAR(CURRENT_DATE)`

### Client Queries
- "Clients with more than 3 unpaid debts" → `SELECT c.*, COUNT(cr.id) as unpaid_count FROM client c JOIN creance cr ON c.id = cr.client_id WHERE cr.statut != 'PAYEE' GROUP BY c.id HAVING unpaid_count > 3`
- "Find client by email" → `SELECT * FROM client WHERE email = '...'`

### Date Queries
- "Debts due this month" → `SELECT * FROM creance WHERE MONTH(echeance) = MONTH(CURRENT_DATE) AND YEAR(echeance) = YEAR(CURRENT_DATE)`
- "Oldest unpaid debts" → `SELECT * FROM creance WHERE statut != 'PAYEE' ORDER BY date_emission ASC`

### Agent Queries
- "Debts assigned to agent X" → `SELECT * FROM creance WHERE agent_recouv = (SELECT id FROM utilisateur WHERE email = '...')`
- "Agent performance" → `SELECT u.nom, COUNT(CASE WHEN c.statut = 'PAYEE' THEN 1 END) as paid_count, COUNT(c.id) as total_count FROM utilisateur u LEFT JOIN creance c ON u.id = c.agent_recouv GROUP BY u.id`

## SQL Generation Guidelines

When generating SQL from natural language:

1. **Always use table aliases** for readability
2. **Use JOINs** instead of subqueries when possible
3. **Include WHERE clauses** for filtering
4. **Use aggregate functions** (COUNT, SUM, AVG) when asking for totals/counts
5. **Order results** when asking for "top", "oldest", "newest"
6. **Handle date comparisons** carefully (use DATE functions)
7. **Respect enum values** exactly as defined
8. **Use proper JOIN syntax** for relationships

## Example Prompts and Expected SQL

| Natural Language | Expected SQL |
|-----------------|--------------|
| "How many debts are in progress?" | `SELECT COUNT(*) FROM creance WHERE statut IN ('IMPAYEE', 'EN_RETARD', 'PENALISEE', 'PARTIELLEMENT_PAYEE')` |
| "Show me debts over 1000 DHS" | `SELECT * FROM creance WHERE montant_facture > 1000` |
| "What's the total unpaid amount?" | `SELECT SUM(montant_facture + montant_penalites - montant_encaisse) as total_unpaid FROM creance WHERE statut != 'PAYEE'` |
| "List clients with unpaid debts" | `SELECT DISTINCT c.* FROM client c JOIN creance cr ON c.id = cr.client_id WHERE cr.statut != 'PAYEE'` |
| "How many reminders were sent this month?" | `SELECT COUNT(*) FROM relance WHERE MONTH(date_relance) = MONTH(CURRENT_DATE) AND YEAR(date_relance) = YEAR(CURRENT_DATE)` |

## Schema JSON for LangChain

```json
{
  "database": "recouvdb",
  "tables": [
    {
      "name": "creance",
      "description": "Debt or invoice that needs to be collected",
      "columns": [
        {"name": "id", "type": "BIGINT", "description": "Primary key"},
        {"name": "num_facture", "type": "VARCHAR", "description": "Invoice number, unique"},
        {"name": "date_emission", "type": "DATE", "description": "Invoice emission date"},
        {"name": "echeance", "type": "DATE", "description": "Payment due date"},
        {"name": "montant_facture", "type": "DOUBLE", "description": "Original invoice amount"},
        {"name": "montant_encaisse", "type": "DOUBLE", "description": "Amount already paid"},
        {"name": "montant_penalites", "type": "DOUBLE", "description": "Penalty amount"},
        {"name": "statut", "type": "ENUM", "description": "Debt status: PAYEE (fully paid), IMPAYEE (unpaid), EN_RETARD (overdue), PENALISEE (penalized, 60+ days overdue), PARTIELLEMENT_PAYEE (partially paid)"},
        {"name": "client_id", "type": "BIGINT", "description": "Foreign key to client table"},
        {"name": "agent_recouv", "type": "BIGINT", "description": "Foreign key to utilisateur table (collection agent)"}
      ]
    },
    {
      "name": "client",
      "description": "Client or debtor who owes money",
      "columns": [
        {"name": "id", "type": "BIGINT", "description": "Primary key"},
        {"name": "raison_sociale", "type": "VARCHAR", "description": "Company or legal name"},
        {"name": "email", "type": "VARCHAR", "description": "Email address"},
        {"name": "telephone", "type": "VARCHAR", "description": "Phone number"},
        {"name": "adresse", "type": "VARCHAR", "description": "Address"}
      ]
    },
    {
      "name": "reglement",
      "description": "Payment made towards a debt",
      "columns": [
        {"name": "id", "type": "BIGINT", "description": "Primary key"},
        {"name": "montant", "type": "DOUBLE", "description": "Payment amount"},
        {"name": "date_reglement", "type": "DATE", "description": "Payment date"},
        {"name": "mode_paiement", "type": "ENUM", "description": "Payment method: ESPECE (cash), CHEQUE (check), VIREMENT (transfer), CARTE (card)"},
        {"name": "statut", "type": "ENUM", "description": "Payment status: EFFECTUE (completed), NON_EFFECTUE (not completed)"},
        {"name": "creance_id", "type": "BIGINT", "description": "Foreign key to creance table"}
      ]
    },
    {
      "name": "relance",
      "description": "Reminder sent to client about unpaid debt",
      "columns": [
        {"name": "id", "type": "BIGINT", "description": "Primary key"},
        {"name": "date_relance", "type": "DATE", "description": "Reminder date"},
        {"name": "type_relance", "type": "ENUM", "description": "Reminder type: EMAIL, TELEPHONE, LETTRE (letter)"},
        {"name": "statut", "type": "ENUM", "description": "Reminder status: ENVOYE (sent), EN_ATTENTE (pending), ECHOUE (failed)"},
        {"name": "creance_id", "type": "BIGINT", "description": "Foreign key to creance table"}
      ]
    }
  ]
}
```

## Usage in LangChain

```python
from langchain.utilities import SQLDatabase

# Create database connection
db = SQLDatabase.from_uri("mysql+pymysql://user:pass@host:3306/recouvdb")

# Get schema info
schema_info = db.get_table_info()

# Use in SQLDatabaseChain
from langchain.chains import SQLDatabaseChain
chain = SQLDatabaseChain.from_llm(llm, db, verbose=True)
```

This schema description should be provided to the LLM when generating SQL queries to ensure accurate query generation.

