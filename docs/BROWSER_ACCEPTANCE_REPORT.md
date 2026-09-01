# English Browser Acceptance Report

Date: 2026-09-02 (Asia/Shanghai)

## Environment

- Frontend: `http://127.0.0.1:5177`
- Backend: local Spring Boot service
- Database: isolated English demo database with 23 Flyway migrations
- Data policy: fictional public-safe customers, suppliers, products, employees, and business documents only

## Results

All 24 routed application pages opened successfully. No visible Chinese text, browser console warnings, or browser console errors were found.

The following end-to-end workflows passed through the browser:

1. Sales order
   - Resolved a customer by code and by telephone number through Enter-key interaction.
   - Added a product by code and through the product-search picker.
   - Saved sales order `XS260902-001`; the list located and highlighted the new document.
   - Opened Print Preview and confirmed the first successful print.
   - Confirmed that stock changed from 48 to 47 and that the inventory ledger recorded the sales outbound movement.

2. Purchase and receipt
   - Saved purchase order `CG260902-001`.
   - Created receipt `CS260902-001` from the purchase order and completed supervisor approval.
   - Confirmed that stock changed from 47 to 48 and that the purchase receipt appeared in the inventory and purchase ledgers.

3. Sales return and return warehousing
   - Imported the remaining returnable line from the original sales order.
   - Saved sales return `XT260902-001`; the list located and highlighted the new document.
   - Created return warehouse receipt `TJ260902-001` and approved stock-in.
   - Confirmed that the linked sales return became approved, stock changed from 48 to 49, and the inventory ledger recorded the return movement.

4. Other expense and cashflow
   - Saved and approved other-expense document `ZC260902-001` for 25.00.
   - Confirmed that Edit was unavailable after approval.
   - Confirmed that the cashflow ledger contained the expense and that double-click opened the source document.

5. Dispatch
   - Added printed sales order `XS260902-001` to dispatch sheet `CB260902-001`.
   - Approved the dispatch sheet.
   - Confirmed that the sales order displayed Printed, Added to Dispatch Sheet, and Approved/Dispatched progress.

6. Ledger navigation
   - Double-click opened the corresponding sales order, sales return, purchase receipt, inventory source document, and other-expense document.

## Automated verification already completed

- Backend: 78 tests run, 0 failures; 7 optional external legacy-file tests skipped.
- Frontend: TypeScript project build and Vite production build passed.
- Tracked-source language scan and secret-pattern scan passed.

## Publication status

English localization and browser acceptance are complete. The remaining decision before changing the GitHub repository from private to public is the repository license.
