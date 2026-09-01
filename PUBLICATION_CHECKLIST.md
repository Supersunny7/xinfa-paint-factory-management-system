# Public GitHub Publication Checklist

## Current decision

**BLOCKED — do not publish this copy as a public repository yet.**

The initial source-only copy has been created, but it was taken from an active working tree with uncommitted changes. It is suitable for localization and cleanup work, not public release.

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

- [x] Translate fixed user-visible frontend strings into English. Legacy business records remain untranslated and must be replaced before screenshots or demos are published.
- [ ] Translate backend validation and error messages into English.
- [x] Use the approved English demo identity, Xinfa Paint Factory.
- [ ] Translate print templates, CSV headers, exported filenames, and audit text.
- [ ] Translate meaningful source comments and test fixtures.
- [x] Review every Flyway migration for company-specific category or expense data.
- [x] Replace company-specific seeded values with fictional English examples.
- [x] Remove the `.trycloudflare.com` Vite development allow-list.
- [x] Require database credentials and `JWT_SECRET` from the environment; do not provide usable defaults.
- [ ] Verify that no password, token, phone number, address, private URL, or real business record remains.
- [x] Add fictional demo data that is safe to publish.
- [x] Add English setup, architecture, business-flow, and testing documentation.
- [x] Add backend and frontend GitHub Actions workflows.
- [ ] Decide on a license before publication.
- [ ] Run backend tests, frontend production build, fresh-database migration, and browser acceptance in English. Backend tests, frontend build, and a fresh 23-migration database have passed; browser acceptance remains pending.
- [ ] Run a final secret scan and inspect the complete Git diff before the first commit.

## Files requiring special review

- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/db/migration/V11__product_category_tree.sql`
- `backend/src/main/resources/db/migration/V12__internal_supply_and_classification.sql`
- `backend/src/main/resources/db/migration/V13__correct_huada_category_name.sql`
- `backend/src/main/resources/db/migration/V22__other_expense.sql`
- `frontend/vite.config.ts`
- `frontend/src/App.vue`
- All files under `frontend/src/views/`
- All backend controllers that return Chinese messages
- `database-schema.sql`

## Publication recommendation

Use a private GitHub repository during translation and security cleanup. Make it public only after every blocking item above has passed review.
