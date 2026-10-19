# CareDesk: Hospital Management System

A full-stack hospital management web app. Staff sign in, register patients, manage doctors, book appointments, and use **CareDesk AI** to ask questions, check symptoms, book by plain text, and summarize notes.

**Stack:** Java 17 (built-in HTTP server, no framework) · PostgreSQL (Neon) · HTML, CSS and JavaScript frontend · Gemini API for the AI features.

## Features

- Admin login, with admin-created staff accounts (Receptionist, Doctor, Nurse). Passwords are stored hashed (PBKDF2).
- Patients, doctors and appointments, saved in a database.
- Dashboard with live counts and the next appointments.
- **CareDesk AI** (English, Hindi and Hinglish):
  - **Ask:** questions about your hospital data
  - **Symptoms:** suggests a specialist and urgency (guidance, never a diagnosis)
  - **Book by text:** "Book Meera with Dr. Khan on 2026-10-28"
  - **Notes summary:** rough doctor notes to a clean visit summary
  - **Insights:** a short read of the current data
- Responsive layout with light and dark mode.

## Project structure

```
src/main/java/hospital/      Java backend
  HospitalManagement.java    server, routes, validation
  Patient, Doctor, Appointment, User   model classes
  Db.java                    database access (JDBC)
  Auth.java                  password hashing, sessions, rate limits
  Gemini.java                AI calls (key stays on the server)
src/main/resources/static/   frontend (index.html, css/, js/)
legacy-console/              the original console version of this project
Dockerfile                   used for deployment
DEPLOY.md                    step-by-step deployment guide
```

## Run it locally

You need Java 17+, Maven, and a free [Neon](https://neon.tech) database.

```bash
cp .env.example .env        # then edit .env with your values
mvn package
java -jar target/hospital-management.jar
```

Open http://localhost:8080 and sign in with `ADMIN_USER` and `ADMIN_PASSWORD`. Tables are created automatically on first start.

## Deploy

See [DEPLOY.md](DEPLOY.md) for the free setup (GitHub, Neon, Render, Gemini).

## API (all JSON, under `/api`)

| Route | Notes |
|---|---|
| `POST /login`, `POST /logout`, `GET /me` | session cookie |
| `GET, POST /patients` · `/doctors` · `/appointments` | signed-in staff |
| `GET, POST /users`, `DELETE /users/{id}` | admin only |
| `POST /ai` | `{ "mode": "ask|sym|book|note|ins", "text": "..." }` |

## Security and privacy notes

- Use sample data only. This is a learning project, not certified for real patient records.
- Only the data an AI feature needs is sent to Gemini. On Google's free tier, inputs may be used to improve their models.
- Login attempts and AI requests are rate limited. Sessions use a signed, HttpOnly cookie.
- AI replies are guidance only. Medical decisions belong to a doctor.
