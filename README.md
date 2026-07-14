# CatchMySTM

Real-time transit catch-probability tracker for Montreal STM.

## Quick Start

### Backend
```bash
cd backend
mvn clean install
mvn spring-boot:run
```
Runs on http://localhost:8080

### Frontend
```bash
cd frontend
npm install
ng serve
```
Runs on http://localhost:4200

## Project Structure
- `backend/` — Spring Boot REST API + WebSocket server
- `frontend/` — Angular SPA for tracking buses/trains
- `docs/` — Architecture decisions, API docs (to be created)

## Status
Phase 0: Project scaffolding
