# Architecture and Business Flows

## Components

- `frontend/`: Vue single-page application and print-preview UI
- `backend/`: Spring Boot REST API, authentication, validation, and transactions
- MySQL: operational records, master data, stock balances, and audit history
- Flyway: database schema migrations

The frontend calls `/api/v1`. During development, Vite proxies `/api` to the backend. The backend controls permissions, document transitions, version checks, and stock mutations.

## Main workflows

### Sales

1. Create a sales order and add products.
2. Open Print Preview.
3. Confirm the first successful print; inventory is deducted once.
4. Add the printed sales order to a dispatch sheet.
5. Approve the dispatch sheet. Approval does not deduct stock again.

### Sales returns

1. Create a sales return, optionally linked to its original sales order.
2. Add it to a return warehouse receipt.
3. Approve the warehouse receipt; inventory increases at this point.

### Purchasing

1. Create a purchase order.
2. Create linked purchase receipts as goods arrive.
3. Approve a receipt. Positive lines increase stock; negative lines reduce stock.
4. Negative stock is allowed by the current business rules.

### Other expenses

Expense documents record an account, handler, categories, and line amounts. Approval makes the document read-only. Print status is tracked independently.

## Security model

- JWT authentication and role-based API access
- Password change for new or reset accounts
- Lockout after repeated failed sign-ins
- Environment-only secrets
- Optimistic versions to prevent silent concurrent overwrites
- Audit records for master-data and account operations
