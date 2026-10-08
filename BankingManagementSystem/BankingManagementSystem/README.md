# Banking Management System

A beginner-friendly banking app:

```
Browser (Streamlit, port 8501)
    <-> HTTP + JSON
Java REST API (port 8080, com.sun.net.httpserver)
    <->
Flat files: backend/data/accounts.txt and transactions.txt
```

No Maven, Gradle, Spring or Java libraries. Plain `javac` + `java`.

## Requirements
- JDK 11 or newer (`javac -version` must work, a JRE alone is not enough)
- Python 3.9+ with pip

## Run it (two terminals)

**Terminal 1 - backend**

| OS | Command |
|----|---------|
| Windows | `cd backend` then `run_backend.bat` |
| macOS / Linux | `cd backend` then `sh run_backend.sh` |

You should see `Banking API running at http://localhost:8080/api/health`.

**Terminal 2 - frontend**

| OS | Command |
|----|---------|
| Windows | `cd frontend` then `run_frontend.bat` |
| macOS / Linux | `cd frontend` then `sh run_frontend.sh` |

Open http://localhost:8501.

**VS Code:** open this folder, press `Ctrl+Shift+P` -> *Tasks: Run Task* -> *Start Full App*.

## How to Test
1. Open the **Register** page, create an account with an initial deposit of 1000. Note the account number (first one is `100001`).
2. Register a second account (`100002`), then **Login** as `100001`.
3. Deposit 250, then try to withdraw 99999 - you should see "Insufficient balance".
4. Transfer 300 to `100002`, then open the **History** tab - you should see the deposit and transfer rows with balance-after. Try transferring to your own account to see the validation error.
5. Logout, choose **Admin**, log in with `admin` / `admin123` and check the account and transaction lists. You can also run `curl http://localhost:8080/api/health`.

## API summary
| Method | Path | Body / query |
|--------|------|--------------|
| GET | /api/health | - |
| POST | /api/register | name, email, password, accountType, initialDeposit |
| POST | /api/login | accountNumber, password |
| GET | /api/account | ?accountNumber= |
| POST | /api/deposit, /api/withdraw | accountNumber, amount |
| POST | /api/transfer | fromAccount, toAccount, amount |
| GET | /api/transactions | ?accountNumber= |
| POST | /api/admin/login | username, password (returns a token) |
| GET | /api/admin/accounts, /api/admin/transactions | header `X-Admin-Token` |

Errors always look like `{"success": false, "error": "..."}`.

## Known limitations (it is a learning project)
- Customer endpoints identify the account by number only (login is checked once, but later calls are not token-protected). Do not expose port 8080 to a network.
- Admin credentials are hardcoded (`admin` / `admin123`).
- SHA-256 without a salt is used because the brief required it; real systems use bcrypt/Argon2.
- Text files are rewritten on every change, which is fine for small data only.

## Future enhancements
1. Per-user session tokens so every customer endpoint is authenticated.
2. Salted password hashing (bcrypt/PBKDF2) and a password-change screen.
3. Replace the text files with SQLite or MySQL through JDBC.
4. Admin charts and a CSV export of transactions.
5. Multiple accounts per customer, plus loans and interest calculation.
