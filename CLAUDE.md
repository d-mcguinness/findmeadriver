# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FindMeADriver — a spare hours marketplace connecting self-employed commercial drivers (who have unused tachograph hours) with employers needing short-haul deliveries. Spring Boot 3.2.3 backend + SvelteKit 2 frontend with IBM Carbon Design System.

## Build & Run Commands

### Backend (`backend-main/`)
```bash
cd backend-main
mvn spring-boot:run          # Run on http://localhost:8080
mvn clean install            # Build
mvn test                     # Run tests (no tests exist yet)
```

### Frontend (`frontend-main/`)
```bash
cd frontend-main
npm install                  # Install dependencies
npm run dev                  # Dev server on http://localhost:5173
npm run build                # Production build
npm run check                # Svelte/TypeScript type checking
```

The Vite dev server proxies `/api` requests to `http://localhost:8080` (configured in `vite.config.ts`).

## Architecture

### Backend (Java 17, Maven)
- **Spring Boot 3.2.3** REST API with Spring Security + JWT authentication
- **H2 in-memory database** with JPA/Hibernate (auto-DDL update mode)
- **JPA Joined Table Inheritance**: `User` base entity → `Driver` and `Employer` subclasses
- **JWT filter** (`security/filter/JwtAuthenticationFilter`): extracts Bearer tokens, sets SecurityContext. Registered before `UsernamePasswordAuthenticationFilter` in `SecurityConfig`.
- **Package structure** (`com.driverdirect`): `controller/`, `model/`, `dto/`, `repository/`, `service/`, `security/`, `config/`
- `DataInitializer` seeds roles and test users on startup (admin@driverdirect.com, employer@company.com/Acme Logistics, driver@example.com/CLASS_A — all with simple passwords)
- CORS is wide open (`*`) for development

### Core Domain Models
- **DriverAvailability** — per-day available hours with EU tachograph validation (max 9h/day, 10h twice/week, 56h/week, 90h/fortnight)
- **Job** — employer posts delivery jobs (title, route, date, duration, rate, CDL type). Statuses: OPEN → ASSIGNED → IN_PROGRESS → COMPLETED (or CANCELLED). Has `assignedDriver` set when application accepted.
- **JobApplication** — driver applies for jobs. Statuses: PENDING → ACCEPTED/REJECTED/WITHDRAWN. Accepting one auto-rejects others and sets job to ASSIGNED.
- **Rating** — both parties rate each other (1-5 stars + comment) after job completion. Unique constraint on (job, reviewer).
- **ComplianceDocument** — driver uploads document metadata (licence, insurance, CPC card, tachograph card) with expiry dates. Statuses: PENDING → VERIFIED (by admin) or EXPIRED.

### Frontend (SvelteKit 2, Svelte 5 Runes)
- **IBM Carbon Design System** (`carbon-components-svelte`, g10 theme)
- **Auth store** (`src/lib/stores/auth.svelte.ts`): Svelte Rune-based ($state/$derived), JWT parsing, localStorage persistence
- **API client** (`src/lib/api.ts`): fetch wrapper that auto-attaches Bearer token
- **Types** (`src/lib/types.ts`): shared TypeScript interfaces for all API responses
- **Driver dashboard** (tabbed): Availability, Compliance, Browse Jobs, My Applications (with rating prompt for completed jobs)
- **Employer dashboard** (tabbed): Post a Job, My Jobs (with Start/Complete/Cancel buttons, application review modal with driver ratings and verification badges)
- **Landing page**: spare hours marketplace messaging with how-it-works sections

### API Endpoints
**Public:** `POST /api/user/login`, `POST /api/user/register/driver`, `POST /api/user/register/employer`

**Driver (`/api/driver/**`):** availability (GET/PUT), jobs (GET), apply (POST), applications (GET), withdraw, rate employer, compliance docs (GET/POST/DELETE)

**Employer (`/api/employer/**`):** jobs CRUD, status updates (with validated transitions), view/accept/reject applications, rate driver

**Admin (`/api/admin/**`):** compliance document review (GET pending, PUT verify)

### Key Enums
- **CDLType**: CLASS_A, CLASS_B, CLASS_C, NON_CDL
- **JobStatus**: OPEN, ASSIGNED, IN_PROGRESS, COMPLETED, CANCELLED (valid transitions enforced in service)
- **ApplicationStatus**: PENDING, ACCEPTED, REJECTED, WITHDRAWN
- **DocumentType**: DRIVING_LICENCE, INSURANCE, CPC_CARD, TACHOGRAPH_CARD, OTHER
- **DocumentStatus**: PENDING, VERIFIED, EXPIRED
