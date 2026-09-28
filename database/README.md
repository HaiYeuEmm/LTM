# Database

Database schema changes live in `migrations/` as ordered MySQL scripts.

## Migration order

- `V1__create_users.sql`: shared teacher/student accounts.
- `V2__teacher_classroom_and_exam.sql`: classes, members, question banks, exams, sessions, attempts, results, and audit records.
- `V3__class_chat.sql`: class chat rooms, members, and messages.
- `V4__auth_sessions.sql`: expiring login sessions used by authenticated APIs.
- `V5__class_assignments.sql`: uploaded files attached to a teacher's class group.

The class detail screen supports adding multiple students at once, viewing the class roster, sending group messages, and uploading/downloading assignment files. Assignment files are stored in `class_assignments.file_data` (maximum 15 MB per file); apply V5 before starting the server.

The server runs these scripts automatically when started from the `server/` directory. Each uses `CREATE TABLE IF NOT EXISTS` for initial provisioning. Add future schema changes as a new migration instead of editing scripts already applied.

To apply them manually with the MySQL command-line client from the repository root, run this in PowerShell. It prompts for the database password:

```powershell
Get-Content .\database\migrations\V1__create_users.sql, .\database\migrations\V2__teacher_classroom_and_exam.sql, .\database\migrations\V3__class_chat.sql, .\database\migrations\V4__auth_sessions.sql, .\database\migrations\V5__class_assignments.sql -Raw | mysql --host=mysql-ltm2026-vothanhhai020106-ltm.d.aivencloud.com --port=18745 --user=avnadmin --password --ssl-mode=REQUIRED ltm
```

The `users.email` column is case-insensitive and unique. The application should trim and lowercase email addresses. Store only a password hash in `password_hash`, never a raw password. Roles begin as `STUDENT`, `TEACHER`, and `ADMIN`.

## Teacher demo data

After applying migrations, run `seed/teacher-demo-data.sql` in MySQL Workbench/DBeaver or with the MySQL CLI. It creates sample classes and members, class chat rooms/messages, a question bank with sample questions/choices, a published exam, an open exam session, two attempts, sample answers and a result. It reuses existing accounts and does not create accounts or change passwords. The script expects `teacher.tam@school.edu.vn` and the student addresses declared at its top; edit those values if your `users` table uses different accounts. It resolves database IDs from email/class/question content instead of assuming IDs like `1`, `2`, or `4`, and can be run again safely.

See [teacher-schema.md](teacher-schema.md) for the entity relationships, access rules, and design rationale.
