# Public Repository Contents

## Include

- `backend/src/main/java/` application source
- `backend/src/test/` automated tests that use fictional fixtures only
- `backend/pom.xml`
- `frontend/src/` application source
- `frontend/public/` public static assets
- Frontend package, lock, TypeScript, and Vite configuration
- Sanitized Flyway schema migrations
- `.env.example` with placeholders only
- `.github/workflows/ci.yml` and the pull-request template
- README, architecture, testing, security, publication, and license-status documents

## Never include

- Real databases, backups, SQL exports, or production configuration
- Real customers, suppliers, employees, products, prices, stock, sales, purchases, or expenses
- Product spreadsheets, imported Excel/CSV files, or generated exports
- Screenshots showing real business records
- Passwords, JWT secrets, API keys, SSH keys, cloud credentials, or private URLs
- `.env`, logs, `node_modules`, `dist`, `target`, IDE settings, or local toolchains
- Internal handoff notes, legacy evidence, phone photographs, and temporary localization scripts

## Required before making the repository public

1. Replace legacy-derived seeded categories and expense records with fictional English demo data.
2. Translate backend validation messages and export/audit labels at their source.
3. Create a clean database from the sanitized migrations.
4. Generate fictional demo transactions and English screenshots.
5. Run CI, browser acceptance, and a final secret/data scan.
6. Choose an open-source license or explicitly keep the repository source-available.
