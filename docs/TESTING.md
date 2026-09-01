# Testing Guide

## Automated checks

```bash
cd backend
mvn test

cd ../frontend
pnpm install --frozen-lockfile
pnpm run typecheck
pnpm run build
```

## Browser acceptance checklist

- Sign in, forced password change, sign out, and expired-session handling
- Create/edit/void draft sales orders; keyboard customer/product lookup; zero-price lines
- Print preview and first-print confirmation; verify stock changes once only
- Reprint and confirm that stock is not deducted again
- Create sales returns; import eligible original lines; reject fully returned orders
- Approve return warehouse receipts and verify returned stock increases
- Create purchase orders and receipts with positive and negative lines
- Approve receipts and verify corresponding inventory movements
- Create, edit, approve, print, and void eligible other-expense drafts
- Build dispatch sheets from printed sales orders and approve them
- Open linked documents by double-clicking every ledger row
- Sort document lists by date and document number in both directions
- Filter approved/unapproved, printed/unprinted, active/voided documents
- Verify long phone lists, narrow screens, scroll areas, and empty states
- Verify every printable module exposes a visible **Print Preview** action

## Corner cases

- Duplicate clicks and concurrent version conflicts
- Same product on positive and negative receipt lines
- Negative inventory and large decimal quantities
- Zero sales price and zero-total documents
- Extremely long names, phone lists, notes, and specifications
- Duplicate customer/product codes with case or punctuation differences
- Empty/reversed date ranges and dates across month/year boundaries
- Disabled master data referenced by historical documents
- Printing cancelled after preview versus confirmed successful print
- Network failure immediately before or after save/approval responses
