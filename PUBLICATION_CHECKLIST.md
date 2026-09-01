# Public GitHub Publication Checklist

## Current decision

**PRIVATE RELEASE CANDIDATE — source cleanup is complete, but do not switch the repository to public yet.**

The English source-only copy has completed localization and automated verification. Public visibility remains blocked only by the license decision and final browser/screenshot acceptance.

## Included in the local English copy

- Backend application source
- Backend automated tests, except the local business test-data generator
- Flyway database migrations
- Frontend application source
- Frontend package and TypeScript configuration
- A schema-only SQL file
- Safe environment-variable template
- Public-repository ignore rules

## Intentionally excluded

- Git history and remote configuration
- Real database files and backups
- Legacy-system screenshots and phone photographs
- Requirements, internal handoff notes, acceptance reports, and business evidence
- Product spreadsheets and real import files
- Seed SQL derived from legacy product data
- Runtime logs and build output
- `node_modules`, `dist`, `target`, local tools, and temporary files
- Local test-data generator containing business-shaped data
- Cloudflare URLs, cloud-server credentials, SSH keys, and deployment secrets

## Blocking work before public release

- [x] Translate fixed user-visible frontend strings into English.
- [x] Translate backend validation and error messages into English.
- [x] Use the approved English demo identity, Xinfa Paint Factory.
- [x] Translate print templates, CSV headers, exported filenames, and audit text.
- [x] Translate meaningful source comments and test fixtures.
- [x] Review every Flyway migration for company-specific category or expense data.
- [x] Replace company-specific seeded values with fictional English examples.
- [x] Remove temporary tunnel domains from the Vite development allow-list.
- [x] Require database credentials and `JWT_SECRET` from the environment; do not provide usable defaults.
- [x] Verify that no committed password, token, private URL, or real business record remains. Placeholder environment values and fictional demo records are intentional.
- [x] Add fictional demo data that is safe to publish.
- [x] Add English setup, architecture, business-flow, and testing documentation.
- [x] Add backend and frontend GitHub Actions workflows.
- [ ] Decide on a license before publication.
- [ ] Complete browser acceptance for every business workflow. Login, dashboard, and all 24 routed application pages now open successfully against an isolated English demo database with no visible Chinese text and no browser console warnings or errors. Product and customer demo records render correctly. Purchase filters and the create/cancel dialogs for sales orders, sales returns, return warehousing, dispatch sheets, purchases, and other expenses have passed. Sales-order customer lookup by phone and product lookup by code both passed through real Enter-key interaction. Print Preview controls are present in source and are intentionally record-dependent on list pages; end-to-end save, approval, print confirmation, inventory, and ledger reconciliation still require browser acceptance with linked demo documents. Public-safe desktop screenshots are included. Backend tests, frontend build, and a fresh 23-migration database have passed.
- [x] Run a final tracked-source language scan, secret review, and Git diff check.

## Files requiring special review

- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/db/migration/V11__product_category_tree.sql`
- `backend/src/main/resources/db/migration/V12__internal_supply_and_classification.sql`
- `backend/src/main/resources/db/migration/V13__correct_huada_category_name.sql`
- `backend/src/main/resources/db/migration/V22__other_expense.sql`
- `frontend/vite.config.ts`
- `frontend/src/App.vue`
- All files under `frontend/src/views/`
- All backend controllers that return user-visible messages
- `database-schema.sql`

## Publication recommendation

Keep the GitHub repository private until a license is selected and the final English browser acceptance/screenshots are complete. Then run one last history-aware secret scan before changing visibility.
