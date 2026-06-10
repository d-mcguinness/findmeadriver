# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FindMeADriver — a **multi-modal freight marketplace / lightweight TMS**. **Shippers** post **loads**; self-employed **carriers** (originally spare-hours road drivers, now any-mode) apply and carry them. Loads move by **road, rail, sea or air**, and true **intermodal** movements are a sequence of single-mode legs. The platform charges a **per-mode commission** on top of the carrier cost, with a per-mode **rate-card** pricing basis. Spring Boot 3.2.3 backend + SvelteKit 2 frontend with IBM Carbon Design System.

> **Naming/brand note:** the domain vocabulary is **Shipper / Carrier / Load** (renamed from the original Employer / Driver / Job). The Maven package stays `com.driverdirect`, the Spring Boot main class stays `DriverDirectApplication`, and the user-facing brand stays **FindMeADriver** — these are deliberately *not* renamed. Demo-login emails are likewise unchanged (see below).

## Build & Run Commands

### Backend (`backend-main/`)
```bash
cd backend-main
mvn spring-boot:run          # Run on http://localhost:8080
mvn clean install            # Build
mvn test                     # Unit tests (pricing / credential / compliance / licence core).
                             # First run must be online to fetch the surefire JUnit-platform provider.
```
Override the port with `-Dspring-boot.run.arguments=--server.port=8090`.

### Frontend (`frontend-main/`)
```bash
cd frontend-main
npm install
npm run dev                  # Dev server on http://localhost:5173 (next free port if taken)
npm run build
npm run check                # Svelte/TypeScript type checking
```
The Vite dev server proxies `/api` → `http://localhost:8080` (`vite.config.ts`).

> **Seeding gotcha:** the DB is **H2 in-memory, `ddl-auto=create-drop`** (`jdbc:h2:mem:driverdirectdb`) — schema + seed data are rebuilt on every restart, so no migrations are needed in dev (production would need Flyway; all multimodal columns are additive/nullable). `DataInitializer` is a `CommandLineRunner` that runs **after** Spring logs "Started" — when scripting verification, wait for the seed to finish (a few seconds), don't probe immediately.

## Architecture

### Backend (Java 17, Maven, `com.driverdirect`)
- **Spring Boot 3.2.3** REST API, Spring Security + JWT. `security/filter/JwtAuthenticationFilter` extracts Bearer tokens before `UsernamePasswordAuthenticationFilter`.
- **H2 in-memory**, JPA/Hibernate, `create-drop`.
- **JPA joined-table inheritance:** `User` → `Shipper` and `Carrier` subclasses.
- Packages: `controller/ model/ dto/ repository/ service/ security/ config/ util/`.
- CORS wide open (`*`) for dev.
- DTOs map via static `from(...)` factories; relocated fields keep `@Deprecated` getter shims (house style — keep them).

### TMS data model (the Phase-0 tree, extended)
```
Customer ──< TransportOrder ──< ShipmentLine >── Shipment (= one single-mode LEG)
                                                   ├── Stops ──> Location (typed: ADDRESS/SEAPORT/AIRPORT/RAIL_TERMINAL/...)
                                                   └── Load  (= the carrier-assignment for that leg)
Itinerary ──< Shipment   (intermodal: sequences N single-mode leg-Shipments for one order)
```
- **Shipment** = the physical leg. Carries `mode` (`Shipment.Mode`), execution status, the pricing fields (`totalRate` = carrier cost, `commissionPercent`, `commissionAmount`, `shipperTotal`), the per-mode quantity metrics (`distanceKm`, `weightKg`, `volumeM3`, `containerCount`, `pieceCount`), the resolved `chargeUnit`/`chargeableQuantity`, and intermodal membership (`itinerary`, `legSequence`).
- **Load** = the carrier-assignment "load" (was `Job`). Owns `ratePerHour`, `estimatedDurationHours`, `currency`, `requiredLicenceCategory`, `assignedCarrier`, `status` (`LoadStatus`). Tree-navigation getter shims (`getTitle`, `getPickupLocation`, `getMode`, …) read off the linked Shipment/Order/Stops.
- **Itinerary** = a door-to-door intermodal movement; rolls up `carrierCostTotal`/`commissionTotal`/`grandTotal` from its legs (`getMode()` derives INTERMODAL when legs span >1 mode).
- **LoadApplication** — a carrier applies to a load (PENDING → ACCEPTED/REJECTED/WITHDRAWN; accepting auto-rejects others + sets the load ASSIGNED).
- **Carrier** — `licenceCategory`, `supportedModes` (`Set<Shipment.Mode>`), `credentials` (mode-tagged `"AIR:ATPL"`), `endorsements`, `homeCountry`. **Shipper** — `companyName`, `country`, `currency`, `industry`.
- Other: **Rating**, **ComplianceDocument**, **CarrierLane** (country-pair lanes), **CarrierAvailability** / **CarrierTimeSlot**, **CabotageOperation**, **Location** (+`LocationType`/`unlocode`/`iata`), **Role**.

### Pricing (`PricingPolicy` + `PricingService`)
- **`PricingPolicy`** (code-default config bean): per-mode **commission %** + a per-mode **`RateCard`** (`ChargeUnit`, `baseFee`, `ratePerUnit`, `minimumCharge`) + the IATA air volumetric divisor.
- **`PricingService.priceLoad(load)`**: carrier cost = `max(min, base + ratePerUnit × quantity)` on the mode's basis when the leg carries the quantity (road per-km, sea/rail per-container, air per chargeable-kg), else falls back to `ratePerHour × hours`; then per-mode commission → `shipperTotal`; snapshots `chargeUnit`/`chargeableQuantity` onto the Shipment.
- **`recalcItinerary(itinerary)`** rolls priced legs up (mixed currencies rejected — FX out of scope).
- A client-side mirror of the commission %s + rate cards lives in `frontend-main/src/lib/pricing.ts` for the live post-a-load preview; **the server is authoritative** and `GET /api/pricing/modes` exposes the rates.

### Eligibility & compliance (mode-aware)
`LoadApplicationServiceImpl.evaluate(...)` is the single rule set (query-free; same logic for single check, apply, batch admin preview). Ordered gates → `Eligibility`: `LOAD_NOT_OPEN → ALREADY_APPLIED → MODE_UNSUPPORTED → LICENCE → AVAILABILITY → CABOTAGE`.
- **Mode capability:** a carrier must `supportsMode(load.mode)` (empty `supportedModes` = road-only). `MODE_UNSUPPORTED` otherwise.
- **Credentials:** dispatched by `CredentialMatcherRegistry` — ROAD uses the `LicenceCategory` HGV/CDL covers-lattice; AIR/OCEAN/RAIL require the carrier to hold ≥1 credential tagged for that mode; INTERMODAL/PARCEL open. `LICENCE` otherwise.
- **Cabotage** (EU 1072/2009) and the **EU-561 tachograph** ceilings are **ROAD-only** — `CabotageService` short-circuits non-road; tachograph validation lives in `AvailabilityServiceImpl`.
- The batch admin preview (`/loads/{id}/carrier-eligibility`) resolves applications/availability/cabotage/modes/credentials in a **constant number of queries** (no N+1) — preserve that when editing.
- **Carrier load browse** (`LoadServiceImpl.getMatchingLoads`, behind `GET /api/carrier/loads`) pre-filters OPEN loads by lane match **+ per-mode remaining duty-clock hours**: `remaining(mode, date) = max(0, declared − committed)`, where committed is derived from the carrier's assigned/in-progress/completed loads (`AvailabilityService.getRemainingHoursByModeAndDate` → `LoadRepository.sumCommittedHoursByDateAndMode`, **constant queries / no N+1**). A load only surfaces when **that mode's** clock has room for its `estimatedDurationHours`, so browse agrees with the apply-time `AVAILABILITY` gate — which stays authoritative (browse is only a coarse pre-filter).

### Tree building (`TmsTreeService`)
- `createTreeFor(load, input)` — single-leg path (used by live single-load create + seed).
- `createIntermodalTreeFor(shipper, IntermodalOrderInput)` — builds an Itinerary + N leg-Shipments, prices each leg, rolls up.

### Frontend (SvelteKit 2, Svelte 5 runes, IBM Carbon g10)
- **Auth store** `src/lib/stores/auth.svelte.ts` (`$state`/`$derived`, JWT parsing, localStorage): `isCarrier` / `isShipper` / `isAdmin`. **Impersonation ("mimic"):** `impersonate()`/`stopImpersonating()`/`isImpersonating` stash the admin token under `fmad_impersonator`, swap the active `token`, and back a sticky "Return to admin" banner in the root `+layout.svelte` (admins mimic a user from the users table).
- **API client** `src/lib/api.ts` (auto-attaches Bearer token). **Types** `src/lib/types.ts`.
- Libs: `transport-modes.ts` (mode labels/colours + commission mirror), `pricing.ts` (rate-card mirror + `loadModePricing`), `money.ts`, `countries.ts`, `google-maps.ts`, `licence-categories.ts`.
- **Design layer** `src/app.css` (global, loaded after Carbon g10): the app-wide visual language — tokens (`--fmad-accent`, `--fmad-grad`) + reusable utilities `.eyebrow`, `.section-heading` (gradient accent underline, absolutely-positioned so it survives flex headers), `.icon-badge`, `.gradient-cta`, `.fmad-card` (hover-lift), plus a soft radius+shadow on every Carbon `.bx--tile`. Used across all routes for a cohesive look — prefer these utilities over bespoke per-page CSS, and don't restyle dense functional UI (tables/forms/modals/duty-clock editors).
- **Route groups** under `dashboard/`: `(carrier)`, `(shipper)`, `(admin)` (each guarded by its `+layout.ts`; the `(shipper)` group also admits admins). Dashboards: `CarrierDashboard`, `ShipperDashboard`, `AdminDashboard`; `LoadsTable` for the load grids.
- Notable pages: `/` (multimodal landing — decluttered to the essentials: gradient hero + the four per-mode-accented pricing cards + gradient CTA; the old "how it works" / "why" tile sections were dropped to reduce busy-ness), `/pricing`, `/dashboard/(shipper)/loads/post` (single-leg + mode dropdown + live pricing preview + a shipper/admin "autofill sample data" demo button), `/dashboard/(shipper)/loads/post-intermodal` (multi-leg builder), `/dashboard/(shipper)/loads/[id]/edit` (edit an OPEN load — shipper/admin, prefills + PUTs), `/dashboard/(shipper)/itineraries`, `/dashboard/(carrier)/capabilities` (modes + credentials editor), `/dashboard/(admin)/documents/[id]/edit` (admin review page — approve a pending compliance document, reached via the Review row action; verifies via `PUT /compliance/{id}/verify`).

### API Endpoints
**Public:** `POST /api/user/login`, `POST /api/user/register/carrier`, `POST /api/user/register/shipper`, `POST /api/user/forgot-password`, `POST /api/user/reset-password`, `GET /api/pricing/modes`.

**Carrier (`/api/carrier/**`):** `capabilities` (GET/PUT — supportedModes + credentials), `availability` (GET/PUT), `loads` + `loads/all` (browse), `loads/{id}/apply`, `applications`, `applications/{id}/withdraw`, `loads/{id}/rate`, `ratings`, `lanes` (GET/POST/DELETE), `cabotage-exposure`, `home-country`, `compliance` (GET/POST/DELETE), `timeslots` (GET/POST/DELETE).

**Shipper (`/api/shipper/**`):** `loads` CRUD (`POST` / `GET` / `GET {id}` ownership-guarded / `PUT {id}` edit — OPEN-only) + `loads/{id}/status` + `loads/{id}/cancel`, `loads/{id}/applications` + accept/reject, `loads/{id}/rate`, `ratings`; **intermodal** `itineraries` (POST create / GET list / GET {id}).

**Admin (`/api/admin/**`):** `users` (+ `users/{id}/impersonate` — POST mints a login-shape token for any user so an admin can "mimic" them; mirrors `/api/user/login`, forbids self), `stats`, `shippers`, `carriers`, `loads` (POST on-behalf / GET / GET {id} / PUT {id} edit / cancel), `loads/{id}/carrier-eligibility`, `applications`, compliance review (`compliance/pending`, `compliance/{id}/verify`), and read-only TMS views `orders`, `shipments`, `itineraries` (+ POST on-behalf), `locations`.

### Key Enums
- **Shipment.Mode**: ROAD, RAIL, OCEAN, AIR, INTERMODAL, PARCEL
- **ChargeUnit**: PER_KM, PER_HOUR, PER_CHARGEABLE_KG, PER_CONTAINER, PER_PIECE, FLAT
- **LoadStatus**: OPEN, ASSIGNED, IN_PROGRESS, COMPLETED, CANCELLED (validated transitions)
- **ApplicationStatus**: PENDING, ACCEPTED, REJECTED, WITHDRAWN
- **Eligibility**: OK, LOAD_NOT_OPEN, ALREADY_APPLIED, MODE_UNSUPPORTED, LICENCE, AVAILABILITY, CABOTAGE
- **Location.LocationType**: ADDRESS, SEAPORT, AIRPORT, RAIL_TERMINAL, INLAND_TERMINAL
- **ShipmentStatus**: PLANNED, TENDERED, ACCEPTED, DISPATCHED, IN_TRANSIT, DELIVERED, CANCELLED
- **Itinerary.ItineraryStatus**: PLANNED, IN_TRANSIT, DELIVERED, CANCELLED
- **TransportOrder.OrderStatus**: NEW, PLANNED, IN_EXECUTION, COMPLETED, CANCELLED · **ServiceLevel**: STANDARD, EXPRESS, ECONOMY, TIME_DEFINITE
- **Role.RoleType**: ROLE_ADMIN, ROLE_SHIPPER, ROLE_CARRIER
- **Carrier.CDLType** (legacy road): CLASS_A, CLASS_B, CLASS_C, NON_CDL · **LicenceCategory**: EU/UK/US road categories with a `covers()` lattice
- **DocumentType**: DRIVING_LICENCE, INSURANCE, CPC_CARD, TACHOGRAPH_CARD, OTHER · **DocumentStatus**: PENDING, VERIFIED, EXPIRED

### Seed / demo logins (reseeded each restart)
`admin@driverdirect.com` / `admin123` · `employer@company.com` / `employer123` (Shipper "Acme Logistics") · `driver@example.com` / `driver123` (multi-modal Carrier — supports ROAD/OCEAN/AIR, holds AIR:ATPL + OCEAN:STCW). The seed includes road/rail/sea/air loads, two intermodal itineraries, and typed port/airport/terminal Locations.
