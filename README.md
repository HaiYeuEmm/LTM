# Online Exam

Repository for an online examination system. The backend is a Spring Boot application built with Maven and Java 21.

## Current structure

```text
Online_exam/
├── .agents/                 # Project planning and agent guidance
├── server/                  # Spring Boot backend
│   ├── .mvn/wrapper/         # Maven Wrapper configuration
│   ├── src/main/java/        # Backend source code
│   ├── src/main/resources/   # Application configuration
│   ├── src/test/java/        # Backend tests
│   ├── mvnw, mvnw.cmd
│   └── pom.xml
├── desktop-client/          # JavaFX sign-in and registration UI
│   ├── src/main/java/       # JavaFX screens and interaction logic
│   ├── src/main/resources/  # JavaFX stylesheets
│   └── pom.xml
├── database/                # MySQL schema migrations
│   ├── migrations/
│   └── README.md
├── Online_exam.code-workspace
└── README.md
```

The current JavaFX client contains the shared sign-in and registration screen. Teacher and student workflows can be added as separate screens or applications as the product grows.

## Run the backend

From the repository root:

```powershell
cd server
.\mvnw.cmd spring-boot:run
```

The Maven Wrapper keeps the Maven version consistent without requiring a global Maven installation.

## Configure the MySQL database

The server connects to the Aiven MySQL database using TLS. For local development, copy `server/application-local.example.properties` to `server/application-local.properties` and set your database password there. The local file is ignored by Git and should never be committed. You can also provide `DB_PASSWORD` as an environment variable.

In PowerShell, from the repository root, create the private local config once:

```powershell
Copy-Item .\server\application-local.example.properties .\server\application-local.properties
notepad .\server\application-local.properties
cd server
.\mvnw.cmd spring-boot:run
```

Replace the example password with your rotated Aiven password before starting the server.

The host, port, database, and username have defaults in `server/src/main/resources/application.properties`. Override them with `DB_HOST`, `DB_PORT`, `DB_NAME`, or `DB_USERNAME` if the Aiven service details change. `server/.env.example` lists the supported variables; it is a template only and is not loaded automatically by Spring Boot.

## Run the JavaFX client

Requires JDK 21 and Maven. From the repository root:

```powershell
cd desktop-client
mvn javafx:run
```

The JavaFX client calls the server's `/api/auth` endpoints. A teacher login opens the teacher dashboard; other roles open the general welcome screen. Start the server first, then start the client. The client defaults to `http://localhost:8080`; set `ONLINE_EXAM_API_URL` or the `onlineexam.api.url` JVM property to use another server URL.

When the backend starts from `server/`, Spring runs `database/migrations/V1__create_users.sql` to create the `users` table if needed. Passwords are hashed with BCrypt before storage. The migration uses `CREATE TABLE IF NOT EXISTS`, so subsequent starts leave an existing table in place.
