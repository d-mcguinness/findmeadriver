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
- Client-supplied mode strings are parsed by two near-identical `parseMode` helpers (`TmsOrderInput.parseMode` for single-leg create/edit, `LoadServiceImpl.parseMode` for intermodal legs) — both reject `INTERMODAL` (`IllegalArgumentException` → 400 via `GlobalExceptionHandler`). It's a derived, multi-leg-only label (`Itinerary.getMode()` returns it when legs span >1 mode); a concrete leg always resolves to ROAD/RAIL/OCEAN/AIR/PARCEL, and `PricingPolicy` has no rate card for INTERMODAL by design.

### Routing engine skeleton (`com.driverdirect.routing`) — INERT, design-only
Class shapes for a proposed time-dependent multi-criteria (cost, CO2) route planner that will *propose* itineraries instead of the shipper authoring every leg. **Nothing here is wired up**: no Spring beans, no DB tables; `RoutingGraphBuilder.build()` and `RoutePlanner.findOptions()` both throw `UnsupportedOperationException`. The full design + build order live in root `README.md` ("Proposed: multimodal routing engine") — read that before implementing anything here.
- `ServiceEdge` (interface) + `ScheduledServiceEdge` (rail/sea/air) + `RoadEdge` (virtual — generated on demand within a radius, never stored). `CarrierLane` now has the timetable to back scheduled edges: nullable `serviceMode`/`originLocation`/`destinationLocation` + a recurring weekly pattern (`departureDays` CSV of `DayOfWeek` names, `departureTime`, `transitDurationHours`), `isTimetabled()`, and `nextDeparture(after)` (the exact contract `ScheduledServiceEdge.nextDeparture` will delegate to — origin-local time; timezone resolution is the graph build's job). Untimetabled lanes stay pure country-pair browse preferences; `CarrierLaneService.add` upserts a schedule onto the existing (carrier, origin, destination) lane, rejects partial timetables and INTERMODAL serviceMode, and a plain country-pair re-add never wipes an existing schedule.
- `TransferProfile` — mode-change cost/dwell keyed by `(location, fromMode, toMode)`; deliberately a lookup the search consults, **not** a graph edge (edges-as-transfers would force `(Location, Mode)` graph nodes).
- `RoutingGraph` (adjacency lists `Map<LocationId, List<ServiceEdge>>`) + `RoutingGraphBuilder` — a rebuildable in-memory snapshot; source of truth stays relational.
- `Label` — search state as a predecessor-pointer chain (never a copied leg list); dominance compares only within the same `(location, arrivalMode)` bucket because future transfer cost depends on arrival mode; cost/co2 are primitive `double` in the hot path, snapshotted to `BigDecimal` only at the `Shipment` boundary.
- `CargoDetails` — deliberately mirrors `Shipment`'s per-mode quantities; **all mapping goes through `CargoDetails.from(Shipment)`** so the shapes can't drift — extend that factory if a quantity is added on either side.
- `RouteQuery`/`RouteOption`/`RoutePlanner` — the entry point. `TransportOrder` already has the matching nullable date columns (`earliestReadyDate`/`latestHandoverDate`/`arrivalDeadline`, threaded through the create/edit DTOs and responses); `dateNeeded` stays the one required, authoritative date every existing consumer reads.

### Frontend (SvelteKit 2, Svelte 5 runes, IBM Carbon g10)
- **Auth store** `src/lib/stores/auth.svelte.ts` (`$state`/`$derived`, JWT parsing, localStorage): `isCarrier` / `isShipper` / `isAdmin`. **Impersonation ("mimic"):** `impersonate()`/`stopImpersonating()`/`isImpersonating` stash the admin token under `fmad_impersonator`, swap the active `token`, and back a sticky "Return to admin" banner in the root `+layout.svelte` (admins mimic a user from the users table).
- **API client** `src/lib/api.ts` (auto-attaches Bearer token). **Types** `src/lib/types.ts`.
- Libs: `transport-modes.ts` (mode labels/colours + commission mirror), `pricing.ts` (rate-card mirror + `loadModePricing`), `money.ts`, `countries.ts`, `google-maps.ts`, `licence-categories.ts`.
- **Design layer** `src/app.css` (global, loaded after Carbon g10): the app-wide visual language — tokens (`--fmad-accent`, `--fmad-grad`) + reusable utilities `.eyebrow`, `.section-heading` (gradient accent underline, absolutely-positioned so it survives flex headers), `.icon-badge`, `.gradient-cta`, `.fmad-card` (hover-lift), plus a soft radius+shadow on every Carbon `.bx--tile`. Used across all routes for a cohesive look — prefer these utilities over bespoke per-page CSS, and don't restyle dense functional UI (tables/forms/modals/duty-clock editors).
- **Route groups** under `dashboard/`: `(carrier)`, `(shipper)`, `(admin)` (each guarded by its `+layout.ts`; the `(shipper)` group also admits admins). Dashboards: `CarrierDashboard`, `ShipperDashboard`, `AdminDashboard`; `LoadsTable` for the load grids. The whole `/dashboard/**` tree renders with `ssr = false` (root `+layout.ts`) — it's a client-only SPA, so `curl`ing a dashboard route only ever returns the generic app shell; verifying anything there needs a real browser with JS execution (e.g. Playwright), not an HTTP check.
- **`RouteTransferMap`** `src/lib/components/RouteTransferMap.svelte` — the Google Map shared by the post-a-load page, the edit page, each leg card in the multimodal builder, and the admin itinerary-overview map. Plots numbered stop markers and draws a *recommended route* per mode instead of a straight line: real driving directions for Road and best-effort transit for Rail (`calculateRoutePath` in `google-maps.ts`, via the Routes API), an approximate shipping lane through major canal/strait chokepoints — Gibraltar/Suez/Panama — for Sea (`recommendedSeaLane`, a coarse lat/lng-bounding-box heuristic, no real maritime API exists), great-circle for Air; falls back to a dashed placeholder line if live routing fails, always labelled as an estimate. Route colour matches the map legend (Road/Rail/Sea/Air). Also renders a "Cheapest ways to move this load" panel ranking Road/Rail/Sea/Air door-to-door estimates on the existing rate cards + nearby-transfer lookups (`showRouteOptions={false}` to suppress it, used by the overview map). Takes either a single `{stops, mode}` (one leg) or a `legs: {mode, stops}[]` array — a multi-leg overview, each leg drawn with its own mode's routing, stops numbered continuously across the whole sequence.
- Notable pages: `/` (multimodal landing — decluttered to the essentials: gradient hero + the four per-mode-accented pricing cards + gradient CTA; the old "how it works" / "why" tile sections were dropped to reduce busy-ness), `/pricing`, `/dashboard/(shipper)/loads/post` — single-leg **and** multi-leg posting in one page: the Transport Mode select's "Multimodal" option swaps the form in place into a leg-by-leg builder (each leg with its own `LocationPicker` + `RouteTransferMap`) instead of navigating to a separate route; live pricing preview; a shipper/admin "autofill sample data" demo button (admin also gets a second "autofill multimodal sample" button that always fills a random itinerary regardless of the mode currently selected); when Multimodal is selected, shippers and admins both also see a full-itinerary overview map atop the legs section linking every leg with its own mode's route; a per-stop "Transfers nearby" check — `findNearbyTransfers` in `google-maps.ts` queries the Google Places API New for the nearest airport/rail-station/ferry-terminal within 50 km per mode, fails soft; needs "Places API (New)" enabled on the key. `/dashboard/(shipper)/loads/post-intermodal` now just redirects here with `?mode=INTERMODAL` (old links/bookmarks still resolve), `/dashboard/(shipper)/loads/[id]/edit` (edit an OPEN load — shipper/admin, prefills + PUTs; + the same `RouteTransferMap`), `/dashboard/(shipper)/itineraries`, `/dashboard/(carrier)/capabilities` (modes + credentials editor), `/dashboard/(admin)/documents/[id]/edit` (admin review page — approve a pending compliance document, reached via the Review row action; verifies via `PUT /compliance/{id}/verify`).

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
