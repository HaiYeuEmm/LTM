# Database

Database schema changes are stored in `migrations/` as ordered SQL files.

## First migration

Run `migrations/V1__create_users.sql` against the configured MySQL database to create the initial `users` table. The script is safe to run again if the table already exists.

With the MySQL command-line client installed, run this from the repository root in PowerShell. The client prompts for the database password:

```powershell
Get-Content .\database\migrations\V1__create_users.sql -Raw | mysql --host=mysql-ltm2026-vothanhai020106-ltm.d.aivencloud.com --port=18745 --user=avnadmin --password --ssl-mode=REQUIRED ltm
```

The `email` column uses a case-insensitive collation and a unique constraint. The application should trim and lowercase emails before storing them. Store only a password hash in `password_hash`; never store a raw password.

The initial roles are `STUDENT`, `TEACHER`, and `ADMIN`. New schema changes should be added as a new migration instead of editing a migration that has already been applied.

The server currently does not run these migration files automatically; run them explicitly with a MySQL client.
