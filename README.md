# FindMeADriver

**FindMeADriver** is a multi-modal freight marketplace and lightweight TMS (Transport Management System). **Shippers** post **Loads**; self-employed **Carriers** (any transport mode) apply for and carry them. Loads move by **road, rail, sea or air**, and a true **intermodal** movement is modelled as a sequence of single-mode legs. The platform earns a **per-mode commission** layered on top of the carrier's cost, priced from a **per-mode rate card** (road per-km, sea/rail per-container, air per chargeable-kg, with an hourly fallback). On top of the marketplace sits a TMS-lite data model (Customer → Order → Shipment legs → Stops → Locations, sequenced by Itineraries) plus mode-aware eligibility, credential and duty/rest-hours compliance.

> **Naming / brand note.** The domain vocabulary is **Shipper / Carrier / Load** (renamed from the original Employer / Driver / Job). For continuity, several internal identifiers are deliberately **not** renamed: the Maven package stays `com.driverdirect`, the Spring Boot main class stays `DriverDirectApplication`, the user-facing brand stays **FindMeADriver**, and the demo-login emails are unchanged.

---

## Tech Stack

### Backend (`backend-main/`)
- **Java 17**, **Maven**
- **Spring Boot 3.2.3** REST API
- **Spring Security + JWT** (`jjwt` 0.11.5) — a `JwtAuthenticationFilter` extracts Bearer tokens ahead of `UsernamePasswordAuthenticationFilter`
- **Spring Data JPA / Hibernate**, **H2** in-memory database
- **JPA joined-table inheritance:** `User` → `Shipper` / `Carrier`
- **Testing:** Spring Boot Test, JUnit 5, Mockito, AssertJ
- CORS open (`*`) for development

### Frontend (`frontend-main/`)
- **SvelteKit 2** + **Svelte 5** (runes: `$state`, `$derived`, `$effect`; snippets)
- **Vite 5**
- **IBM Carbon Design System** — `carbon-components-svelte` (g10 theme) + `carbon-icons-svelte`
- **Google Maps JS API** (`@googlemaps/js-api-loader`) for location autocomplete and route/distance estimates
- TypeScript throughout; JWT auth stored in `localStorage` and parsed at module load

---

## Repository Structure

```
findmeadriver/
├── CLAUDE.md
├── README.md                        # (this file)
├── .github/workflows/maven-publish.yml
│
├── backend-main/                    # Spring Boot Java backend
│   ├── pom.xml
│   ├── settings.xml
│   ├── src/main/resources/application.properties
│   └── src/main/java/com/driverdirect/
│       ├── config/                  # DataInitializer (CommandLineRunner seed)
│       ├── controller/              # 7 REST controllers
│       ├── dto/                     # ~44 request/response DTOs (static from(...) factories)
│       ├── model/                   # ~26 JPA entities + enums
│       ├── repository/              # ~20 Spring Data repositories
│       ├── service/                 # ~27 service / policy beans
│       ├── util/                    # CountryCodes
│       └── security/                # base + config/ (SecurityConfig)
│                                    #        + filter/ (JwtAuthenticationFilter)
│                                    #        + util/  (JWT helpers)
│   └── src/test/java/com/driverdirect/
│       ├── model/                   # 2 entity unit tests
│       └── service/                 # 6 service unit tests
│
└── frontend-main/                   # SvelteKit / TypeScript frontend
    ├── package.json
    ├── svelte.config.js
    ├── tsconfig.json
    ├── vite.config.ts               # dev proxy /api -> :8080
    ├── static/
    └── src/
        ├── app.html · app.d.ts · app.css
        ├── lib/
        │   ├── api.ts               # fetch wrapper (auto Bearer token)
        │   ├── types.ts             # shared domain types
        │   ├── transport-modes.ts   # mode labels + Carbon tag colours
        │   ├── pricing.ts           # client-side rate-card / commission mirror
        │   ├── money.ts · countries.ts · licence-categories.ts
        │   ├── google-maps.ts
        │   ├── components/
        │   │   ├── LocationPicker.svelte
        │   │   ├── admin/           # UsersTable · LoadsTable · DocumentsTable
        │   │   └── dashboards/      # AdminDashboard · CarrierDashboard
        │   │                        # · ShipperDashboard · StatsRow
        │   └── stores/              # auth.svelte.ts · carrierState · shipperState
        └── routes/
            ├── +layout.svelte · +layout.ts · +page.svelte   # multimodal landing
            ├── login/ · register/ · pricing/ · carrier/ · shipper/
            └── dashboard/
                ├── +layout.* · +page.svelte                 # role chooser/redirect
                ├── (carrier)/  capabilities/
                ├── (shipper)/  loads/post/  loads/post-intermodal/  loads/[id]/edit/  itineraries/
                └── (admin)/    loads/  users/  documents/  analytics/  settings/
```

Each `dashboard/` route group is guarded by its own `+layout.ts`: `(carrier)` admits carriers, `(admin)` admits admins, and `(shipper)` admits shippers **and** admins (so an admin can act on a shipper's behalf).

---

## Getting Started — Build & Run

### Backend

```bash
cd backend-main

mvn spring-boot:run          # run on http://localhost:8080
mvn clean install            # build
mvn test                     # unit tests
```

Override the port:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9090"
```

Key `application.properties` defaults:

```properties
server.port=8080
spring.datasource.url=jdbc:h2:mem:driverdirectdb
spring.jpa.hibernate.ddl-auto=create-drop
spring.h2.console.enabled=true          # http://localhost:8080/h2-console
jwt.secret=${JWT_SECRET:default-dev-secret-change-in-production-must-be-at-least-32-chars}
```

> **H2 reseeds on every restart.** The dev database is **H2 in-memory with `ddl-auto=create-drop`**, so the schema and all seed data are rebuilt each time the app starts — no migrations are needed in dev. `DataInitializer` is a `CommandLineRunner` that runs **after** Spring logs `Started ...`, so when scripting verification, wait a few seconds for the seed to finish rather than probing immediately.

> **`mvn test` must be online once.** The first test run needs internet connectivity to fetch the Surefire JUnit-platform provider. After it has been downloaded, subsequent runs work offline.

### Frontend

```bash
cd frontend-main

npm install
npm run dev                  # dev server on http://localhost:5173 (next free port if taken)
npm run build                # production build
npm run check                # svelte-check + TypeScript type checking
npm run preview              # preview the production build
```

The Vite dev server proxies **`/api` → `http://localhost:8080`** (`vite.config.ts`), so run the backend alongside the frontend.

### Full stack

1. **Terminal 1:** `cd backend-main && mvn spring-boot:run` — wait for `Started ...` plus the seed.
2. **Terminal 2:** `cd frontend-main && npm install && npm run dev`.
3. Open **http://localhost:5173** and sign in with a demo account (below).

---

## Architecture

### TMS data model

```
Customer ──< TransportOrder ──< ShipmentLine >── Shipment (= one single-mode LEG)
                                                   ├── Stops ──> Location (typed)
                                                   └── Load   (= the carrier assignment for that leg)

Itinerary ──< Shipment   (intermodal: an ordered sequence of single-mode leg-Shipments for one order)
```

- **Customer** — the buyer of transport, distinct from the platform Shipper account. Every Shipper gets an auto-created `(default)` Customer in v1.
- **TransportOrder** — the customer's request (*what* they want delivered): title, service level, date needed, status. Decoupled from physical movement and carrier assignment. Optional **OrderItem** lines carry weight/cube/hazmat detail.
- **ShipmentLine** — the many-to-many linker between Order and Shipment that enables consolidation (1 Shipment ← N Orders) and splitting (1 Order → N Shipments). v1 keeps it 1:1.
- **Shipment** = the physical **leg**. Carries `mode`, an execution `status` (PLANNED → TENDERED → ACCEPTED → DISPATCHED → IN_TRANSIT → DELIVERED), the pricing fields (`totalRate` = carrier cost, `commissionPercent`, `commissionAmount`, `shipperTotal`), the per-mode quantity metrics (`distanceKm`, `weightKg`, `volumeM3`, `containerCount`, `pieceCount`), the resolved `chargeUnit` / `chargeableQuantity`, and its intermodal membership (`itinerary`, `legSequence`).
- **Stop** — an ordered (1-indexed) physical event on a Shipment. Commercial types: `PICKUP`, `DELIVERY`, `WAYPOINT`. Bookkeeping types that affect timing/cost without moving goods: `REST`, `BORDER`, `FERRY_TERMINAL`, `EUROTUNNEL`. Each points at a **Location**.
- **Location** — a first-class typed address record (`ADDRESS`/`SEAPORT`/`AIRPORT`/`RAIL_TERMINAL`/`INLAND_TERMINAL`) with optional `unlocode`, `iata`, lat/long and timezone. Owned by a Shipper when curated (warehouse/DC), or ownerless for ad-hoc form entries.
- **Load** = the **carrier-assignment** for one leg (the entity formerly called *Job*). Owns `ratePerHour`, `estimatedDurationHours`, `currency`, `requiredLicenceCategory`, `assignedCarrier` and `status` (`LoadStatus`). Tree-navigation getter shims (`getTitle`, `getDescription`, `getDateNeeded`, `getPickupLocation`, `getDeliveryLocation`, `getPickupCountry`, `getDeliveryCountry`, `getMode`) read straight off the linked Shipment / Order / Stops so existing callers compile unchanged.
- **Itinerary** = a door-to-door intermodal movement that sequences N single-mode leg-Shipments for one order and rolls up `carrierCostTotal` / `commissionTotal` / `grandTotal`. `getMode()` derives `INTERMODAL` when its legs span more than one mode.
- **LoadApplication** — a carrier's bid on a Load (`PENDING → ACCEPTED / REJECTED / WITHDRAWN`; accepting auto-rejects the others and sets the Load `ASSIGNED`). Unique per `(load, carrier)`.
- **Carrier** — `licenceCategory`, `supportedModes` (`Set<Shipment.Mode>`), `credentials` (mode-tagged, e.g. `AIR:ATPL`), `endorsements`, `homeCountry`, plus owned `CarrierLane`, `CarrierAvailability`, `CarrierTimeSlot`, `ComplianceDocument` and `CabotageOperation` records. **Shipper** — `companyName`, `country`, `currency`, `industry`.

> **Compat shims (house style — keep them).** Relocated/legacy fields keep `@Deprecated` getters: `Carrier.getCdlType()` reads `licenceCategory`, `Load.getRequiredCdlType()` reads `requiredLicenceCategory`, and the `Load` tree-navigation getters above read off the linked tree. DTOs map via static `from(...)` factories.

### Pricing (`PricingPolicy` + `PricingService`)

- **`PricingPolicy`** is a code-default config bean holding, per mode, a **commission %** and a **`RateCard`** (`ChargeUnit`, `baseFee`, `ratePerUnit`, `minimumCharge`), plus the IATA air volumetric divisor.

  | Mode | Commission | Charge unit | Base fee | Rate / unit | Minimum |
  |------|-----------:|-------------|---------:|------------:|--------:|
  | ROAD | 10% | `PER_KM` | €50 | €1.20 / km | €150 |
  | RAIL | 12% | `PER_CONTAINER` | €0 | €600 / container | €600 |
  | OCEAN | 15% | `PER_CONTAINER` | €350 | €1800 / container | €1800 |
  | AIR | 20% | `PER_CHARGEABLE_KG` | €0 | €3.20 / kg | €75 |
  | PARCEL | 18% | `PER_PIECE` | €0 | €8.50 / piece | €8.50 |
  | INTERMODAL | 12% | — (never a leg mode) | — | — | — |

  The IATA volumetric divisor is **6000 cm³/kg**; air chargeable weight = `max(actual kg, volumetric kg)` where `volumetric kg = volume_m³ × 1,000,000 / 6000`.

- **`PricingService.priceLoad(load)`** computes the carrier cost as `max(minimumCharge, baseFee + ratePerUnit × quantity)` on the mode's basis when the leg carries the relevant quantity (road per-km, sea/rail per-container, air per chargeable-kg, parcel per-piece), and otherwise falls back to `ratePerHour × hours`. It then applies the per-mode commission to reach `shipperTotal`, and snapshots `chargeUnit` / `chargeableQuantity` onto the Shipment. All money is `BigDecimal` at 2dp, `HALF_UP`.
- **`recalcItinerary(itinerary)`** rolls priced legs up into `carrierCostTotal` / `commissionTotal` / `grandTotal` (mixed currencies across legs are rejected — FX is out of scope) and caches origin/destination country from the first pickup / last delivery.
- A client-side mirror of the commission %s and rate cards lives in `frontend-main/src/lib/pricing.ts` for the live post-a-load preview, but **the server is authoritative**; `GET /api/pricing/modes` exposes the live rates.

### Eligibility & compliance (mode-aware)

`LoadApplicationServiceImpl.evaluate(...)` is the single, query-light rule set used identically for a single check, an apply, and the batch admin preview. Ordered gates resolve to an `Eligibility` value — **first failure wins**:

```
LOAD_NOT_OPEN → ALREADY_APPLIED → MODE_UNSUPPORTED → LICENCE → AVAILABILITY → CABOTAGE → OK
```

- **Mode capability:** the carrier must `supportsMode(load.mode)` (an empty `supportedModes` means road-only); otherwise `MODE_UNSUPPORTED`.
- **Credentials** are dispatched by `CredentialMatcherRegistry`: **ROAD** uses the `LicenceCategory` covers-lattice (HGV/CDL, with UK↔EU equivalence such as `HGV_CLASS_1 ≡ C+E`); **AIR / OCEAN / RAIL** require the carrier to hold ≥1 credential tagged for that mode (`AIR:`, `OCEAN:`, `RAIL:`); **INTERMODAL / PARCEL** are open. Failure → `LICENCE`.
- **Availability / per-mode duty clocks:** remaining hours = `max(0, declared_on_mode(dateNeeded) − committed_on_mode(dateNeeded))`; if that is below the load's `estimatedDurationHours` → `AVAILABILITY`. `CarrierAvailability` and `CarrierTimeSlot` are recorded **per `(carrier, date, mode)`** so a multi-modal carrier keeps separate calendars per mode, each governed by that mode's own ceilings (`ComplianceRuleSet` / `ComplianceRuleSetRegistry`): ROAD EU 561/2006 (10h day / 56h week / 90h fortnight), AIR EASA FTL (13h/60h/110h), OCEAN STCW (14h/91h/182h), RAIL EU rail directive (12h/60h/120h). `AvailabilityServiceImpl.validateEntry` enforces the daily, extended-day, weekly and fortnightly ceilings; `getDutyClocks` assembles a per-mode declared/committed/remaining `DutyClock` over week and fortnight windows.
- **Cabotage** (EU Regulation 1072/2009) and the **EU-561 tachograph** ceilings are **ROAD-only**. `CabotageService` short-circuits non-road legs; for a domestic ROAD move by a foreign-based carrier it caps at **3 operations in a rolling 7-day window** (recorded when a Load reaches `COMPLETED`). Failure → `CABOTAGE`.
- The batch admin preview (`GET /api/admin/loads/{id}/carrier-eligibility`) resolves applications, remaining hours, cabotage counts, supported modes and credentials in a **constant number of queries** (4–5 total, not per-carrier) — no N+1.

### Tree building (`TmsTreeService`)

- `createTreeFor(load, input)` — the single-leg path (used by live single-load create and the seed): creates the Customer / TransportOrder / Shipment / Stops / Locations / ShipmentLine for one leg.
- `createIntermodalTreeFor(shipper, input)` — builds one TransportOrder + an Itinerary, then for each leg creates the Load + Shipment + Stops + ShipmentLine and prices the leg via `PricingService.priceLoad`, finally rolling everything up with `recalcItinerary`.

---

## Key Enums

| Enum | Values |
|------|--------|
| `Shipment.Mode` | ROAD, RAIL, OCEAN, AIR, INTERMODAL, PARCEL |
| `ShipmentStatus` | PLANNED, TENDERED, ACCEPTED, DISPATCHED, IN_TRANSIT, DELIVERED, CANCELLED |
| `Itinerary.ItineraryStatus` | PLANNED, IN_TRANSIT, DELIVERED, CANCELLED |
| `TransportOrder.OrderStatus` | NEW, PLANNED, IN_EXECUTION, COMPLETED, CANCELLED |
| `TransportOrder.ServiceLevel` | STANDARD, EXPRESS, ECONOMY, TIME_DEFINITE |
| `Stop.StopType` | PICKUP, DELIVERY, WAYPOINT, REST, BORDER, FERRY_TERMINAL, EUROTUNNEL |
| `Location.LocationType` | ADDRESS, SEAPORT, AIRPORT, RAIL_TERMINAL, INLAND_TERMINAL |
| `LoadStatus` | OPEN, ASSIGNED, IN_PROGRESS, COMPLETED, CANCELLED (validated transitions) |
| `ApplicationStatus` | PENDING, ACCEPTED, REJECTED, WITHDRAWN |
| `Eligibility` | OK, LOAD_NOT_OPEN, ALREADY_APPLIED, MODE_UNSUPPORTED, LICENCE, AVAILABILITY, CABOTAGE |
| `ChargeUnit` | PER_KM, PER_HOUR, PER_CHARGEABLE_KG, PER_CONTAINER, PER_PIECE, FLAT |
| `DocumentType` | DRIVING_LICENCE, INSURANCE, CPC_CARD, TACHOGRAPH_CARD, OTHER |
| `DocumentStatus` | PENDING, VERIFIED, EXPIRED |
| `Role.RoleType` | ROLE_ADMIN, ROLE_SHIPPER, ROLE_CARRIER |
| `Shipper.Industry` | LOGISTICS, TRANSPORTATION, MANUFACTURING, RETAIL, CONSTRUCTION, AGRICULTURE, FOOD_SERVICE, ENERGY, OTHER |
| `LicenceCategory` | EU: C, C1, CE, C1E, D, D1, DE, D1E · UK: HGV_CLASS_1, HGV_CLASS_2 · US: CLASS_A, CLASS_B, CLASS_C, NON_CDL (with a `covers()` lattice + UK↔EU equivalence) |
| `Carrier.CDLType` *(legacy)* | CLASS_A, CLASS_B, CLASS_C, NON_CDL |

---

## API Surface

All controllers allow CORS from `*`. Everything under `/api/carrier`, `/api/shipper` and `/api/admin` requires a JWT; the public endpoints are open.

### Public
- `POST /api/user/login` — authenticate, returns a JWT
- `POST /api/user/register/carrier`
- `POST /api/user/register/shipper`
- `GET  /api/pricing/modes` — per-mode commission % + pricing basis
- `GET  /api/compliance/rules` — per-mode duty/rest limits (reference data)

### Carrier (`/api/carrier/**`)
- **Capabilities:** `GET`/`PUT /capabilities` (supported modes + credentials)
- **Availability / duty clock:** `GET`/`PUT /availability`; `GET`/`POST /timeslots`, `DELETE /timeslots/{id}`
- **Loads & applications:** `GET /loads` (availability/capability-matched), `GET /loads/all` (all OPEN), `POST /loads/{id}/apply`, `GET /applications`, `PUT /applications/{id}/withdraw`
- **Ratings:** `POST /loads/{id}/rate`, `GET /loads/{id}/rated`, `GET /ratings`
- **Lanes:** `GET`/`POST /lanes`, `DELETE /lanes/{id}`
- **Compliance:** `GET`/`POST /compliance`, `DELETE /compliance/{id}`
- **Cabotage:** `GET /cabotage-exposure`, `PUT /home-country`

### Shipper (`/api/shipper/**`)
- **Loads:** `POST /loads`, `GET /loads`, `GET /loads/{id}`, `PUT /loads/{id}` (edit — OPEN-only, ownership-guarded), `PUT /loads/{id}/status`, `PUT /loads/{id}/cancel`
- **Intermodal:** `POST /itineraries`, `GET /itineraries`, `GET /itineraries/{id}`
- **Applications:** `GET /loads/{id}/applications`, `PUT /applications/{id}/accept`, `PUT /applications/{id}/reject`
- **Ratings:** `POST /loads/{id}/rate`, `GET /loads/{id}/rated`, `GET /ratings`

### Admin (`/api/admin/**`)
- **Users & entities:** `GET /users`, `GET /users/{id}`, `GET /carriers`, `GET /shippers`
- **Stats:** `GET /stats`
- **Loads (operator view):** `POST /loads` (on behalf of a shipper via `shipperId`), `GET /loads`, `GET /loads/{id}`, `PUT /loads/{id}` (edit any load), `GET /loads/{id}/applications`, `PUT /loads/{id}/cancel`
- **Eligibility & applications:** `GET /loads/{id}/carrier-eligibility` (runs the full gate ladder per carrier, no N+1), `GET /carriers/{id}/applications`, `POST /applications` (apply on behalf of a carrier)
- **Compliance review:** `GET /compliance/pending`, `PUT /compliance/{id}/verify`
- **Read-only TMS views:** `GET /orders`, `GET /orders/{id}`, `GET /shipments`, `GET /shipments/{id}`, `POST`/`GET /itineraries`, `GET /itineraries/{id}`, `GET /locations`, `GET /locations/{id}`

---

## Demo Logins

The seed is rebuilt on every backend restart by `DataInitializer` (a `CommandLineRunner` that runs **after** `Started ...`).

| Role | Email | Password | Notes |
|------|-------|----------|-------|
| Admin | `admin@driverdirect.com` | `admin123` | `ROLE_ADMIN` |
| Shipper | `employer@company.com` | `employer123` | "Acme Logistics" |
| Carrier | `driver@example.com` | `driver123` | Multi-modal — supports ROAD / OCEAN / AIR; holds `AIR:ATPL` + `OCEAN:STCW`; home country IE |

The seed also creates additional shippers and carriers, road/rail/sea/air loads across all statuses (including international multi-stop routes with ferry / Eurotunnel / border stops), two intermodal itineraries, typed port/airport/terminal Locations, ~14 days of per-mode carrier availability, compliance documents, bidirectional ratings on completed loads, and cabotage-operation history.

---

## Testing

**Stack:** JUnit 5 + Mockito + AssertJ, run with `mvn test`.

**50 `@Test` methods across 8 classes:**

| Test class | Package | Tests | Covers |
|------------|---------|------:|--------|
| `LicenceCategoryTest` | `model` | 5 | `satisfies()` covers-lattice, cross-regime equivalence, exact match, null/blank |
| `CarrierTest` | `model` | 3 | `supportsMode()` — empty = road-only, explicit mode gating |
| `ComplianceRuleSetRegistryTest` | `service` | 3 | per-mode duty/rest ceilings + carrier→ruleset resolution |
| `CredentialMatcherRegistryTest` | `service` | 4 | per-mode credential dispatch (road lattice; air/sea/rail tags; intermodal/parcel open) |
| `EligibilityEvaluateTest` | `service` | 15 | the full gate ladder, ordering guarantees, all failure paths in isolation |
| `DutyClockServiceTest` | `service` | 8 | per-mode ceilings (not collapsed to ROAD), declared − committed netting, browse pre-filter |
| `PricingPolicyTest` | `service` | 5 | per-mode commission, rate-card units, road minimum, IATA volumetric divisor |
| `PricingServiceTest` | `service` | 7 | per-mode rate-card basis, volumetric weight, min charge, hourly fallback, itinerary roll-up, mixed-currency rejection |

**Not yet covered:** controllers/REST endpoints, repositories/persistence, end-to-end integration flows, and the intermodal tree builder.

> The first `mvn test` run must be online to fetch the Surefire JUnit-platform provider; later runs work offline.

---

## Status / Roadmap

### Shipped

- The multi-modal TMS build: road / rail / sea / air modes, intermodal itineraries (sequenced single-mode legs), per-mode rate-card pricing with per-mode commission, mode-aware credential matching, ROAD-only cabotage and EU-561 enforcement, and per-mode duty clocks (declared calendars + load-consumption netting).
- **Itinerary editing & cancellation** — `PUT /api/{shipper,admin}/itineraries/{id}` reshapes an existing itinerary's legs in place (the leg count can't change here — legs have no cascade/orphanRemoval, so adding/removing one would mean deleting a whole Load/Shipment/Stop chain; cancel and repost instead). `PUT .../itineraries/{id}/cancel` cascades to each leg's Shipment *and* Load, since nothing does that automatically — otherwise a carrier could still apply to a leg whose itinerary was cancelled.
- **Recommended-route mapping** (`RouteTransferMap`) — the post-a-load map draws a real recommended route per mode instead of a straight line: Routes-API driving directions for Road, best-effort transit directions for Rail, an approximate shipping lane through major canal/strait chokepoints (Gibraltar / Suez / Panama) for Sea, great-circle for Air — colour-matched to the map legend. A "Cheapest ways to move this load" panel ranks Road/Rail/Sea/Air door-to-door estimates on the existing rate cards. `/dashboard/loads/post-intermodal` is merged into `/dashboard/loads/post`: picking "Multimodal" in the Transport Mode select swaps the single-leg form into a multi-leg builder in place, each leg with its own map, plus a full-itinerary overview map linking every leg with its own mode's route.
- **Order-level flexible-window dates** — `TransportOrder` gained three nullable columns (`earliestReadyDate`, `latestHandoverDate`, `arrivalDeadline`) alongside the existing `dateNeeded`, which stays the one required, authoritative date every current consumer reads — the new fields are optional richer context a routing search would populate. Threaded through `CreateLoadRequest`/`CreateIntermodalLoadRequest`, `TmsTreeService` (create + edit, both single-leg and intermodal), and `LoadResponse`/`ItineraryResponse`. This closes the "time model is thin" gap below, at the order level — `CarrierLane` timetables (the *service*-level scheduling gap) are still open, see the build order above.

### Proposed: multimodal routing engine

A design conversation (July 2026) sketched a proprietary route-planning engine that *proposes* itineraries for a shipper to accept, inverting today's flow where the shipper authors every leg explicitly via `CreateIntermodalLoadRequest`. Not started — captured here as a Claude Code hand-off so the shape is agreed before code lands, and cross-checked below against what the codebase actually has today.

- **Goal.** Given origin, destination, cargo, a flexible handover window `[readyDate, latestHandover]`, and a hard arrival deadline, return 3–6 Pareto-best options on **(cost, CO2)** — the deadline is a hard filter, never an optimisation axis. If nothing satisfies it, rerun optimising for speed alone and report "fastest possible arrival is X".
- **Graph, three layers, no graph database** — an in-memory adjacency-list graph (`Map<LocationId, List<ServiceEdge>>`) built at startup from the relational tables; the query itself is a custom time-dependent Pareto search, not a stored graph.
  - *Physical* — `Location` already carries everything needed (`unlocode`, `iata`, `latitude`/`longitude`, `timezone`, `operatingHours` — all present today). Add **transfer profiles**: cost/time to move cargo between modes at a given terminal (vessel→rail etc.) — this is the key realism differentiator.
  - *Service* — carrier services as scheduled edges. `CarrierLane` is the natural host, but today it's only a directional country pair (`originCountry`/`destinationCountry`) with **no timetable at all** — needs frequency/departures/transit-duration/capacity added (`CarrierTimeSlot`/`CarrierAvailability` may feed this). Road stays **virtual**: synthetic truck legs generated on demand between locations within a radius, priced by road distance — never stored as edges.
  - *Commercial* — rates + emission factors attached to lanes/services, versioned with validity periods, mirroring the existing `PricingPolicy` snapshot pattern.
- **Search.** Time-dependent, earliest-departure-aware label-setting search (multi-criteria Dijkstra / MLC; RAPTOR/CSA ideas adapt well). Labels carry `(arrivalTime, cost, co2, legs)`; dominance with ~5% tolerance to trim near-duplicate options; deadline pruning via an A*-style lower bound (great-circle distance ÷ fastest mode speed). Dwell at a terminal — waiting for a cheaper departure — is a first-class branch, not friction. Flexible-window v1: one full search per candidate handover day (a 30-day window ≈ 30 fast searches), merged/deduped across days, keeping the *latest* viable handover per option (free warehouse dwell at the shipper vs. paid terminal dwell). Only reach for range-RAPTOR-style profile search if per-day search proves too slow.
- **CO2.** A new `EmissionPolicy` mirroring `PricingPolicy` — GLEC Framework / ISO 14083 factors per tonne-km per mode (approx. sea 10–15 g CO2e/t·km, rail 20–30, road 60–100, air 600–1000), computed per leg as `distance × weight × factor` and snapshotted onto the `Shipment` leg exactly like pricing already is. Low effort, and immediately gives every itinerary an emissions figure even before the full search engine exists — worth doing early.
- **Integration point.** A new `com.driverdirect.routing` package (graph build, search, option assembly). Output is a draft `Itinerary` with ordered `Shipment` legs; the shipper accepts it, then each leg posts through the existing `Load`-per-leg marketplace flow unchanged — the planner proposes legs, the marketplace still fills them with carriers. UI ideas: a cost-vs-time scatter with CO2 as colour/size, and a calendar heatmap of best price per handover day (surfaces weekly sailing cadence).
- **Suggested build order:** (1) Postgres + migrations, (2) timetables on `CarrierLane` + transfer profiles on `Location`, (3) graph build + single-criterion (cheapest) time-dependent search with deadline pruning, (4) two-criteria Pareto (cost, CO2) + `EmissionPolicy`, (5) flexible-window loop + cross-day merge/dedupe, (6) options UI.

### Pre-engine hardening

From the same review — mostly relevant regardless of whether the routing engine gets built, and each item below was verified against the current code, not just carried over from the design conversation:

- **Migrate off H2 create-drop.** `application.properties` already flags this ("replace with `validate` and use Flyway/Liquibase migrations") but hasn't committed to one tool; do this before the entity model grows further for the graph layers above. All multimodal columns so far are additive/nullable, so the migration-friendliness holds up.
- **Resolve the Load/Shipment pricing duality.** `Load.ratePerHour` / `estimatedDurationHours` are still `@Column(nullable = false)` today even though `Shipment` (with its per-mode quantities) is the real pricing anchor once `PricingService.priceLoad` has run — make the Load-side fields nullable legacy.
- ~~**The time model is thin.**~~ Resolved at the order level (see "Shipped" above) — `TransportOrder` now carries a flexible handover window + deadline, not just one `dateNeeded`. What's still open: the routing engine needs real departures/schedules living on `CarrierLane`, which today has none — that's build-order step 2, unaffected by this change.
- **Pre-deploy security list:** move the JWT out of `localStorage` into an httpOnly cookie, tighten CORS from `*` (confirmed still wide open on every controller), and drop `logging.level.org.springframework.security=DEBUG` from `application.properties` (still present).
