package com.driverdirect.config;

    import com.driverdirect.model.ApplicationStatus;
import com.driverdirect.model.CabotageOperation;
import com.driverdirect.model.ComplianceDocument;
import com.driverdirect.model.Customer;
import com.driverdirect.model.DocumentStatus;
import com.driverdirect.model.DocumentType;
import com.driverdirect.model.Carrier;
import com.driverdirect.model.CarrierAvailability;
import com.driverdirect.model.CarrierLane;
import com.driverdirect.model.CarrierTimeSlot;
import com.driverdirect.model.Shipper;
import com.driverdirect.model.Load;
import com.driverdirect.model.LoadApplication;
import com.driverdirect.model.LoadStatus;
import com.driverdirect.model.Location;
import com.driverdirect.model.Rating;
import com.driverdirect.model.Role;
import com.driverdirect.model.Shipment;
import com.driverdirect.model.ShipmentLine;
import com.driverdirect.model.Stop;
import com.driverdirect.model.TransportOrder;
import com.driverdirect.model.User;
import com.driverdirect.repository.CabotageOperationRepository;
import com.driverdirect.repository.ComplianceDocumentRepository;
import com.driverdirect.repository.CustomerRepository;
import com.driverdirect.repository.CarrierAvailabilityRepository;
import com.driverdirect.repository.CarrierLaneRepository;
import com.driverdirect.repository.CarrierRepository;
import com.driverdirect.repository.CarrierTimeSlotRepository;
import com.driverdirect.repository.ShipperRepository;
import com.driverdirect.repository.LoadApplicationRepository;
import com.driverdirect.repository.LoadRepository;
import com.driverdirect.repository.LocationRepository;
import com.driverdirect.repository.RatingRepository;
import com.driverdirect.repository.RoleRepository;
import com.driverdirect.repository.ShipmentLineRepository;
import com.driverdirect.repository.ShipmentRepository;
import com.driverdirect.repository.StopRepository;
import com.driverdirect.repository.TransportOrderRepository;
import com.driverdirect.repository.UserRepository;
import com.driverdirect.service.PricingService;
import com.driverdirect.service.TmsTreeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CarrierRepository carrierRepository;
    @Autowired private ShipperRepository shipperRepository;
    @Autowired private LoadRepository loadRepository;
    @Autowired private LoadApplicationRepository loadApplicationRepository;
    @Autowired private ComplianceDocumentRepository complianceDocumentRepository;
    @Autowired private RatingRepository ratingRepository;
    @Autowired private CarrierAvailabilityRepository carrierAvailabilityRepository;
    @Autowired private CarrierTimeSlotRepository carrierTimeSlotRepository;
    @Autowired private CarrierLaneRepository carrierLaneRepository;
    @Autowired private CabotageOperationRepository cabotageOperationRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private TmsTreeService tmsTreeService;
    @Autowired private PricingService pricingService;
    @Autowired private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        initRoles();
        if (userRepository.count() == 0) {
            createTestUsers();
            createDemoData();
        }
    }

    private void initRoles() {
        if (roleRepository.count() == 0) {
            roleRepository.save(new Role(null, Role.RoleType.ROLE_ADMIN));
            roleRepository.save(new Role(null, Role.RoleType.ROLE_SHIPPER));
            roleRepository.save(new Role(null, Role.RoleType.ROLE_CARRIER));
        }
    }

    private void createTestUsers() {
        User admin = new User("admin@driverdirect.com", passwordEncoder.encode("admin123"));
        admin.setFirstName("Admin");
        admin.setLastName("User");
        admin.setRoles(rolesOf(Role.RoleType.ROLE_ADMIN));
        userRepository.save(admin);

        Shipper shipper = new Shipper(
                "employer@company.com",
                passwordEncoder.encode("employer123"),
                "Acme Logistics"
        );
        shipper.setFirstName("Shipper");
        shipper.setLastName("User");
        shipper.setPhone("01234567890");
        shipper.setIndustry(Shipper.Industry.LOGISTICS);
        shipper.setCompanySize(50);
        shipper.setHeadquartersLocation("Dublin");
        shipper.setRoles(rolesOf(Role.RoleType.ROLE_SHIPPER));
        shipperRepository.save(shipper);

        Carrier carrier = new Carrier(
                "driver@example.com",
                passwordEncoder.encode("driver123"),
                "DL-12345-IE",
                LocalDate.now().plusYears(2)
        );
        carrier.setFirstName("Carrier");
        carrier.setLastName("User");
        carrier.setPhone("09876543210");
        carrier.setCdlType(Carrier.CDLType.CLASS_A);
        carrier.setYearsExperience(5);
        carrier.setRoles(rolesOf(Role.RoleType.ROLE_CARRIER));
        carrierRepository.save(carrier);
    }

    private void createDemoData() {
        // ---- Additional shippers ----
        Shipper murphy = createShipper("contact@murphyhaulage.ie", "Murphy Haulage Ltd",
                "Sean", "Murphy", "0851234567",
                Shipper.Industry.TRANSPORTATION, 25, "Cork");
        Shipper fresh = createShipper("ops@freshfoods.ie", "Fresh Foods Distribution",
                "Aoife", "Kelly", "0867654321",
                Shipper.Industry.FOOD_SERVICE, 120, "Galway");
        Shipper buildwell = createShipper("yard@buildwell.ie", "Buildwell Construction",
                "Liam", "Walsh", "0871112233",
                Shipper.Industry.CONSTRUCTION, 80, "Limerick");

        Shipper acme = shipperRepository.findByEmail("employer@company.com").orElseThrow();

        // ---- Additional carriers ----
        Carrier liamByrne = createCarrier("liam.byrne@example.com", "DL-22301-IE",
                "Liam", "Byrne", "0851000001",
                Carrier.CDLType.CLASS_A, 8, LocalDate.now().plusYears(3), "IE");
        Carrier mairead = createCarrier("mairead.osullivan@example.com", "DL-22302-IE",
                "Mairead", "O'Sullivan", "0851000002",
                Carrier.CDLType.CLASS_B, 4, LocalDate.now().plusMonths(6), "GB");
        Carrier kieran = createCarrier("kieran.doyle@example.com", "DL-22303-IE",
                "Kieran", "Doyle", "0851000003",
                Carrier.CDLType.CLASS_A, 12, LocalDate.now().plusYears(1), "IE");
        Carrier siobhan = createCarrier("siobhan.kennedy@example.com", "DL-22304-IE",
                "Siobhan", "Kennedy", "0851000004",
                Carrier.CDLType.CLASS_C, 2, LocalDate.now().plusYears(4), "IE");
        // Foreign-based carrier — drives into IE/FR/DE, so their domestic moves
        // there count as cabotage (see seedCabotage below).
        Carrier patrick = createCarrier("patrick.fitzgerald@example.com", "DL-22305-IE",
                "Patrick", "Fitzgerald", "0851000005",
                Carrier.CDLType.CLASS_B, 6, LocalDate.now().plusMonths(9), "PL");

        Carrier seedCarrier = carrierRepository.findByEmail("driver@example.com").orElseThrow();
        // The primary demo login. Base it in IE so its cabotage ops abroad
        // (seeded below) populate the dashboard's cabotage panel. Multi-modal so
        // it can carry the non-road demo legs; other seed carriers stay road-only.
        seedCarrier.setHomeCountry("IE");
        seedCarrier.setSupportedModes(new java.util.HashSet<>(java.util.List.of(
                Shipment.Mode.ROAD, Shipment.Mode.OCEAN, Shipment.Mode.AIR)));
        carrierRepository.save(seedCarrier);

        // ---- Reference geography (M3): typed port/airport/terminal nodes ----
        // Seeded BEFORE loads so the tree builder's name+country upsert reuses
        // them — existing road/sea/air/rail loads then anchor on real nodes.
        seedNode("Dublin Port", "IE", Location.LocationType.SEAPORT, "IEDUB", null);
        seedNode("Port of Rotterdam", "NL", Location.LocationType.SEAPORT, "NLRTM", null);
        seedNode("Ringaskiddy Port", "IE", Location.LocationType.SEAPORT, "IERAK", null);
        seedNode("Cork Airport", "IE", Location.LocationType.AIRPORT, null, "ORK");
        seedNode("Paris Charles de Gaulle", "IE", Location.LocationType.AIRPORT, null, "CDG");
        seedNode("Madrid Barajas", "ES", Location.LocationType.AIRPORT, null, "MAD");
        seedNode("Dublin North Wall", "IE", Location.LocationType.RAIL_TERMINAL, "IEDUB", null);
        seedNode("Ballina Railhead", "IE", Location.LocationType.RAIL_TERMINAL, null, null);

        // ---- Loads across all statuses ----
        Load j1 = createLoad(acme, "Pallet run Dublin → Cork",
                "Standard pallet delivery, 4 stops along the route.",
                "Dublin Port", "Cork City",
                3.5, LocalDate.now().plusDays(2), new BigDecimal("28.00"),
                Carrier.CDLType.CLASS_A, LoadStatus.OPEN, null);

        Load j2 = createLoad(acme, "Refrigerated Dublin → Limerick",
                "Time-sensitive chilled goods, must arrive before 11am.",
                "Naas Distribution Centre", "Limerick Cold Store",
                4.0, LocalDate.now().plusDays(4), new BigDecimal("32.00"),
                Carrier.CDLType.CLASS_A, LoadStatus.OPEN, null);

        Load j3 = createLoad(murphy, "Equipment haul Cork → Galway",
                "Heavy plant equipment transport for civil engineering project.",
                "Cork Industrial Estate", "Galway Site B",
                5.5, LocalDate.now().plusDays(7), new BigDecimal("35.00"),
                Carrier.CDLType.CLASS_A, LoadStatus.OPEN, null);

        Load j4 = createLoad(fresh, "Daily produce run",
                "Morning produce delivery to restaurants across Galway city centre.",
                "Galway Wholesale Market", "Galway City Centre",
                2.0, LocalDate.now().plusDays(1), new BigDecimal("22.00"),
                Carrier.CDLType.CLASS_C, LoadStatus.OPEN, null);

        // ---- Multi-modal demo loads (M1: transport mode is now selectable) ----
        Load jAir = createLoad(fresh, "Air freight Cork → Paris CDG",
                "Time-critical chilled pharma on a palletised ULD.",
                "Cork Airport", "Paris Charles de Gaulle",
                3.0, LocalDate.now().plusDays(2), new BigDecimal("80.00"),
                Carrier.CDLType.CLASS_C, LoadStatus.OPEN, null, Shipment.Mode.AIR);

        Load jSea = createLoad(acme, "Sea freight Dublin → Rotterdam",
                "FCL container on the Dublin–Rotterdam lane, drayage included.",
                "Dublin Port", "Port of Rotterdam",
                8.0, LocalDate.now().plusDays(6), new BigDecimal("12.00"),
                Carrier.CDLType.CLASS_A, LoadStatus.OPEN, null, Shipment.Mode.OCEAN);

        Load jRail = createLoad(murphy, "Rail intermodal Dublin → Ballina",
                "Containerised rail haul, terminal-to-terminal block-train slot.",
                "Dublin North Wall", "Ballina Railhead",
                6.0, LocalDate.now().plusDays(5), new BigDecimal("18.00"),
                Carrier.CDLType.CLASS_A, LoadStatus.OPEN, null, Shipment.Mode.RAIL);

        // ---- Intermodal demo (M2): true multi-leg door-to-door movement ----
        // Dublin → Amsterdam: road drayage → ocean main leg → road delivery,
        // priced per leg then rolled up to the itinerary grand total.
        tmsTreeService.createIntermodalTreeFor(acme, new TmsTreeService.IntermodalOrderInput(
                "Intermodal Dublin → Amsterdam",
                "Door-to-door: road drayage to port, ocean main leg, road delivery.",
                LocalDate.now().plusDays(9), "EUR",
                List.of(
                        // ROAD drayage priced per-km (hits the €150 minimum).
                        new TmsTreeService.LegInput(Shipment.Mode.ROAD,
                                "Acme Dublin Warehouse", "Dublin Port", "IE", "IE",
                                new BigDecimal("30.00"), 1.5, "C",
                                new BigDecimal("12"), null, null, null, null),
                        // OCEAN main leg priced per-container (2 × FEU).
                        new TmsTreeService.LegInput(Shipment.Mode.OCEAN,
                                "Dublin Port", "Port of Rotterdam", "IE", "NL",
                                new BigDecimal("12.00"), 8.0, "C",
                                null, null, null, 2, null),
                        // ROAD delivery priced per-km (hits the €150 minimum).
                        new TmsTreeService.LegInput(Shipment.Mode.ROAD,
                                "Port of Rotterdam", "Amsterdam DC", "NL", "NL",
                                new BigDecimal("28.00"), 2.0, "C",
                                new BigDecimal("25"), null, null, null, null))));

        // A second intermodal demo with an AIR leg — showcases chargeable-weight
        // pricing where volumetric weight (0.6 m³ → 100 kg) beats the 80 kg actual.
        tmsTreeService.createIntermodalTreeFor(fresh, new TmsTreeService.IntermodalOrderInput(
                "Express intermodal Cork → Madrid",
                "Road feeder to Cork Airport, air main leg, road delivery in Madrid.",
                LocalDate.now().plusDays(4), "EUR",
                List.of(
                        new TmsTreeService.LegInput(Shipment.Mode.ROAD,
                                "Fresh Foods Depot", "Cork Airport", "IE", "IE",
                                new BigDecimal("30.00"), 1.0, "C",
                                new BigDecimal("8"), null, null, null, null),
                        new TmsTreeService.LegInput(Shipment.Mode.AIR,
                                "Cork Airport", "Madrid Barajas", "IE", "ES",
                                new BigDecimal("80.00"), 3.0, "C",
                                null, new BigDecimal("80"), new BigDecimal("0.6"), null, null),
                        new TmsTreeService.LegInput(Shipment.Mode.ROAD,
                                "Madrid Barajas", "Madrid DC", "ES", "ES",
                                new BigDecimal("28.00"), 1.5, "C",
                                new BigDecimal("15"), null, null, null, null))));

        Load j5 = createLoad(buildwell, "Concrete blocks to site",
                "Building materials delivery, forklift access on site.",
                "Buildwell Yard, Limerick", "Mungret Site",
                1.5, LocalDate.now().plusDays(3), new BigDecimal("24.00"),
                Carrier.CDLType.CLASS_B, LoadStatus.ASSIGNED, mairead);

        Load j6 = createLoad(acme, "Long-haul Dublin → Belfast",
                "Cross-border pallet delivery, customs paperwork prepared.",
                "Dublin Port", "Belfast Distribution Hub",
                4.5, LocalDate.now().plusDays(5), new BigDecimal("38.00"),
                Carrier.CDLType.CLASS_A, LoadStatus.ASSIGNED, liamByrne);

        Load j7 = createLoad(murphy, "Cork ferry collection",
                "Container collection from Ringaskiddy ferry terminal.",
                "Ringaskiddy Port", "Mahon Distribution",
                2.5, LocalDate.now(), new BigDecimal("30.00"),
                Carrier.CDLType.CLASS_A, LoadStatus.IN_PROGRESS, kieran);

        Load j8 = createLoad(fresh, "Restaurant route — south Galway",
                "Multi-drop chilled goods delivery, 8 stops.",
                "Fresh Foods Depot", "Salthill and Barna restaurants",
                3.0, LocalDate.now(), new BigDecimal("26.00"),
                Carrier.CDLType.CLASS_C, LoadStatus.IN_PROGRESS, siobhan);

        Load j9 = createLoad(acme, "Dublin → Waterford completed",
                "Pallet delivery completed on schedule.",
                "Dublin Port", "Waterford Industrial Estate",
                3.0, LocalDate.now().minusDays(3), new BigDecimal("28.00"),
                Carrier.CDLType.CLASS_A, LoadStatus.COMPLETED, seedCarrier);

        Load j10 = createLoad(murphy, "Cork → Wexford completed",
                "Heavy haulage completed without issue.",
                "Cork Industrial Estate", "Wexford Yard",
                4.0, LocalDate.now().minusDays(7), new BigDecimal("33.00"),
                Carrier.CDLType.CLASS_A, LoadStatus.COMPLETED, kieran);

        Load j11 = createLoad(fresh, "Galway → Sligo produce",
                "Refrigerated produce, delivered.",
                "Galway Wholesale Market", "Sligo Distribution",
                3.5, LocalDate.now().minusDays(10), new BigDecimal("25.00"),
                Carrier.CDLType.CLASS_B, LoadStatus.COMPLETED, patrick);

        Load j12 = createLoad(buildwell, "Cancelled construction haul",
                "Load cancelled — site delays.",
                "Limerick Yard", "Ennis Site",
                2.0, LocalDate.now().minusDays(2), new BigDecimal("24.00"),
                Carrier.CDLType.CLASS_B, LoadStatus.CANCELLED, null);

        Load j13 = createLoad(acme, "Cancelled Dublin run",
                "Customer cancelled the order.",
                "Dublin Port", "Drogheda Warehouse",
                1.5, LocalDate.now().plusDays(8), new BigDecimal("26.00"),
                Carrier.CDLType.CLASS_A, LoadStatus.CANCELLED, null);

        // ---- Load applications ----
        createApplication(j1, liamByrne, ApplicationStatus.PENDING,
                "12 years on Class A, regular Dublin–Cork runs.");
        createApplication(j1, kieran, ApplicationStatus.PENDING,
                "Available all of next week, own GPS tracking.");
        createApplication(j2, liamByrne, ApplicationStatus.PENDING,
                "Refrigerated experience with previous chilled goods runs.");
        createApplication(j3, kieran, ApplicationStatus.PENDING,
                "Heavy haulage specialist, can supply own straps.");
        createApplication(j4, siobhan, ApplicationStatus.PENDING,
                "Know the Galway routes well, careful with fresh produce.");
        createApplication(j5, mairead, ApplicationStatus.ACCEPTED,
                "Available on the day, familiar with Mungret area.");
        createApplication(j5, patrick, ApplicationStatus.REJECTED,
                "Could swap shifts if needed.");
        createApplication(j6, liamByrne, ApplicationStatus.ACCEPTED,
                "Cross-border paperwork experience, current TIR.");
        createApplication(j6, kieran, ApplicationStatus.REJECTED,
                "Available but no recent NI runs.");
        createApplication(j2, mairead, ApplicationStatus.WITHDRAWN,
                "Found another conflicting booking, sorry.");

        // ---- Compliance documents ----
        createDocument(liamByrne, DocumentType.DRIVING_LICENCE, "DL-LB-2024-001",
                LocalDate.now().plusYears(4), DocumentStatus.VERIFIED);
        createDocument(liamByrne, DocumentType.CPC_CARD, "CPC-LB-7788",
                LocalDate.now().plusYears(2), DocumentStatus.VERIFIED);
        createDocument(liamByrne, DocumentType.TACHOGRAPH_CARD, "TACH-LB-9912",
                LocalDate.now().plusYears(1), DocumentStatus.VERIFIED);
        createDocument(liamByrne, DocumentType.INSURANCE, "INS-LB-2026",
                LocalDate.now().plusMonths(8), DocumentStatus.VERIFIED);

        createDocument(kieran, DocumentType.DRIVING_LICENCE, "DL-KD-2023-019",
                LocalDate.now().plusYears(2), DocumentStatus.VERIFIED);
        createDocument(kieran, DocumentType.CPC_CARD, "CPC-KD-1144",
                LocalDate.now().plusMonths(11), DocumentStatus.PENDING);

        createDocument(mairead, DocumentType.DRIVING_LICENCE, "DL-MOS-2025-007",
                LocalDate.now().plusYears(3), DocumentStatus.PENDING);
        createDocument(mairead, DocumentType.INSURANCE, "INS-MOS-2025",
                LocalDate.now().plusMonths(4), DocumentStatus.PENDING);

        createDocument(siobhan, DocumentType.DRIVING_LICENCE, "DL-SK-2024-033",
                LocalDate.now().plusYears(5), DocumentStatus.PENDING);

        createDocument(patrick, DocumentType.DRIVING_LICENCE, "DL-PF-2022-099",
                LocalDate.now().minusMonths(2), DocumentStatus.EXPIRED);
        createDocument(patrick, DocumentType.TACHOGRAPH_CARD, "TACH-PF-3344",
                LocalDate.now().plusYears(1), DocumentStatus.VERIFIED);

        createDocument(seedCarrier, DocumentType.DRIVING_LICENCE, "DL-12345-IE",
                LocalDate.now().plusYears(2), DocumentStatus.VERIFIED);
        createDocument(seedCarrier, DocumentType.CPC_CARD, "CPC-DD-0001",
                LocalDate.now().plusYears(2), DocumentStatus.VERIFIED);

        // ---- Ratings on completed loads (both directions) ----
        createRating(j9, acme, seedCarrier, 5, "Excellent communication, on time.");
        createRating(j9, seedCarrier, acme, 4, "Clear instructions, would work with again.");
        createRating(j10, murphy, kieran, 5, "Top-tier haulage, immaculate paperwork.");
        createRating(j10, kieran, murphy, 5, "Fair rate, prompt payment.");
        createRating(j11, fresh, patrick, 3, "Delivery completed but ~40 mins late.");

        // ---- Carrier availability for the next 14 days ----
        // EU tachograph caps: 9h/day standard, 10h up to twice/week.
        seedAvailability(seedCarrier, new double[]{8, 9, 0, 9, 9, 6, 0,  9, 10, 0, 9, 9, 8, 0});
        seedAvailability(liamByrne,  new double[]{9, 9, 9, 9, 10, 0, 0, 9, 9, 9, 10, 0, 0, 8});
        seedAvailability(kieran,     new double[]{10, 9, 9, 0, 0, 9, 9, 10, 9, 9, 0, 0, 9, 9});
        seedAvailability(mairead,    new double[]{8, 8, 0, 8, 8, 0, 0,  8, 8, 0, 8, 8, 6, 0});
        seedAvailability(siobhan,    new double[]{6, 0, 6, 6, 6, 0, 0,  6, 6, 0, 6, 6, 6, 0});
        seedAvailability(patrick,    new double[]{0, 0, 8, 8, 8, 0, 0,  8, 8, 8, 8, 0, 0, 8});

        // ---- Multi-stop international loads (exercise the route renderer +
        //      bookkeeping StopTypes: FERRY_TERMINAL / EUROTUNNEL / BORDER / REST) ----
        Load jFerry = createMultiStopLoad(acme,
                "Dublin → London (ferry + multi-drop)",
                "Cross-channel pallet run via Holyhead, overnight rest near Birmingham.",
                8.0, LocalDate.now().plusDays(1), new BigDecimal("34.00"),
                Carrier.CDLType.CLASS_A, LoadStatus.OPEN, null,
                List.of(
                        stop(Stop.StopType.PICKUP,         "Dublin Port",            "Dublin",     "IE"),
                        stop(Stop.StopType.FERRY_TERMINAL, "Holyhead Ferry Port",    "Holyhead",   "GB"),
                        stop(Stop.StopType.REST,           "Hilton Park Services M6","Birmingham", "GB"),
                        stop(Stop.StopType.DELIVERY,       "London Gateway DC",      "London",     "GB")));

        Load jTunnel = createMultiStopLoad(acme,
                "Dover → Paris (Eurotunnel)",
                "Time-critical chilled goods through the Channel Tunnel, customs at Calais.",
                8.5, LocalDate.now().plusDays(3), new BigDecimal("39.00"),
                Carrier.CDLType.CLASS_A, LoadStatus.OPEN, null,
                List.of(
                        stop(Stop.StopType.PICKUP,      "Dover Cold Store",      "Dover",   "GB"),
                        stop(Stop.StopType.EUROTUNNEL,  "Folkestone Eurotunnel", "Folkestone", "GB"),
                        stop(Stop.StopType.BORDER,      "Calais Border Post",    "Calais",  "FR"),
                        stop(Stop.StopType.REST,        "Aire de Saint-Léger",   "Arras",   "FR"),
                        stop(Stop.StopType.DELIVERY,    "Rungis Market",         "Paris",   "FR")));

        // ---- Carrier lanes (origin → destination country opt-ins) ----
        // Demo carrier gets a domestic lane (keeps existing IE loads visible) plus
        // the two cross-border lanes that match the multi-stop loads above.
        addLanes(seedCarrier, "IE", "IE", "IE", "GB", "GB", "FR");
        addLanes(liamByrne, "IE", "IE", "IE", "GB");
        addLanes(kieran, "IE", "IE");

        // ---- Cabotage history for the demo carrier (IE-based) ----
        // GB: 2 ops in the rolling 7-day window → under the limit of 3.
        // FR: 3 ops → AT the limit, so the dashboard shows the red "blocked" tag.
        // deliveryLocation is provenance only (the unload point); the country
        // string is what the limit is counted on. A couple of ops are left
        // without a location to mirror back-filled/imported history.
        Location gbDc = adHocLocation("Birmingham RDC", "Birmingham", "GB");
        Location frDc = adHocLocation("Paris-Sud Plateforme", "Paris", "FR");
        Location deDc = adHocLocation("München Logistikzentrum", "Munich", "DE");

        recordCabotage(seedCarrier, "GB", LocalDate.now().minusDays(1), gbDc);
        recordCabotage(seedCarrier, "GB", LocalDate.now().minusDays(4), null);
        recordCabotage(seedCarrier, "FR", LocalDate.now(), frDc);
        recordCabotage(seedCarrier, "FR", LocalDate.now().minusDays(2), frDc);
        recordCabotage(seedCarrier, "FR", LocalDate.now().minusDays(5), null);
        // Foreign (PL) carrier doing domestic German moves — a second worked example.
        recordCabotage(patrick, "DE", LocalDate.now().minusDays(3), deDc);
        recordCabotage(patrick, "DE", LocalDate.now().minusDays(6), deDc);
    }

    private Location adHocLocation(String name, String city, String country) {
        Location loc = new Location();
        loc.setName(name);
        loc.setAddressLine(name);
        loc.setCity(city);
        loc.setCountry(country);
        return locationRepository.save(loc);
    }

    /** Convenience for a route stop with a default appointment window. */
    private TmsTreeService.TmsStopInput stop(Stop.StopType type, String name,
                                             String city, String country) {
        return new TmsTreeService.TmsStopInput(
                type, name, name, city, country, null, null, null, null);
    }

    private Load createMultiStopLoad(Shipper shipper, String title, String description,
                                   double hours, LocalDate dateNeeded, BigDecimal rate,
                                   Carrier.CDLType cdl, LoadStatus status, Carrier assigned,
                                   List<TmsTreeService.TmsStopInput> stops) {
        Load j = new Load();
        j.setShipper(shipper);
        j.setEstimatedDurationHours(hours);
        j.setRatePerHour(rate);
        j.setRequiredCdlType(cdl);
        j.setCurrency(shipper.getCurrency() != null ? shipper.getCurrency() : "EUR");
        j.setStatus(status);
        j.setAssignedCarrier(assigned);
        j = loadRepository.save(j);

        // Origin/destination countries come from the first PICKUP / last DELIVERY
        // in the route; the tree builder reads the stops list in preference to
        // the legacy pickup/delivery pair (passed null here).
        String currency = shipper.getCurrency() != null ? shipper.getCurrency() : "EUR";
        String pickupCountry = stops.get(0).country();
        String deliveryCountry = stops.get(stops.size() - 1).country();
        TmsTreeService.TmsOrderInput input = new TmsTreeService.TmsOrderInput(
                title, description, dateNeeded,
                null, null, pickupCountry, deliveryCountry, currency, stops);
        tmsTreeService.createTreeFor(j, input);
        j = loadRepository.save(j);
        pricingService.priceLoad(j);
        return j;
    }

    /** Add lanes from a flat (origin, destination, origin, destination, ...) list. */
    private void addLanes(Carrier carrier, String... pairs) {
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            CarrierLane lane = new CarrierLane();
            lane.setCarrier(carrier);
            lane.setOriginCountry(pairs[i]);
            lane.setDestinationCountry(pairs[i + 1]);
            carrierLaneRepository.save(lane);
        }
    }

    private void recordCabotage(Carrier carrier, String country, LocalDate performedAt,
                                Location deliveryLocation) {
        CabotageOperation op = new CabotageOperation();
        op.setCarrier(carrier);
        op.setCountry(country);
        op.setPerformedAt(performedAt);
        op.setDeliveryLocation(deliveryLocation);
        cabotageOperationRepository.save(op);
    }

    private void seedAvailability(Carrier carrier, double[] hoursForNext14Days) {
        LocalDate start = LocalDate.now();
        for (int i = 0; i < hoursForNext14Days.length; i++) {
            double h = hoursForNext14Days[i];
            if (h <= 0) continue;
            LocalDate date = start.plusDays(i);
            carrierAvailabilityRepository.save(new CarrierAvailability(carrier, date, h));
            seedTimeSlotsForDay(carrier, date, h);
        }
    }

    private void seedTimeSlotsForDay(Carrier carrier, LocalDate date, double totalHours) {
        // Split into one or two realistic windows so the calendar looks lived-in.
        // <= 5h: single morning slot. > 5h: morning + afternoon either side of lunch.
        if (totalHours <= 5) {
            LocalTime startT = LocalTime.of(9, 0);
            LocalTime endT = startT.plusMinutes((long) Math.round(totalHours * 60));
            carrierTimeSlotRepository.save(new CarrierTimeSlot(carrier, date, startT, endT));
        } else {
            double morning = Math.min(4, totalHours / 2);
            double afternoon = totalHours - morning;
            LocalTime mStart = LocalTime.of(9, 0);
            LocalTime mEnd = mStart.plusMinutes((long) Math.round(morning * 60));
            LocalTime aStart = LocalTime.of(13, 30);
            LocalTime aEnd = aStart.plusMinutes((long) Math.round(afternoon * 60));
            carrierTimeSlotRepository.save(new CarrierTimeSlot(carrier, date, mStart, mEnd));
            carrierTimeSlotRepository.save(new CarrierTimeSlot(carrier, date, aStart, aEnd));
        }
    }

    // ---- helpers ----

    private Shipper createShipper(String email, String company,
                                    String firstName, String lastName, String phone,
                                    Shipper.Industry industry, int size, String hq) {
        Shipper e = new Shipper(email, passwordEncoder.encode("password123"), company);
        e.setFirstName(firstName);
        e.setLastName(lastName);
        e.setPhone(phone);
        e.setIndustry(industry);
        e.setCompanySize(size);
        e.setHeadquartersLocation(hq);
        e.setRoles(rolesOf(Role.RoleType.ROLE_SHIPPER));
        return shipperRepository.save(e);
    }

    private Carrier createCarrier(String email, String licenseNumber,
                                String firstName, String lastName, String phone,
                                Carrier.CDLType cdl, int years, LocalDate licenceExpiry,
                                String homeCountry) {
        Carrier d = new Carrier(email, passwordEncoder.encode("password123"),
                licenseNumber, licenceExpiry);
        d.setFirstName(firstName);
        d.setLastName(lastName);
        d.setPhone(phone);
        d.setCdlType(cdl);
        d.setYearsExperience(years);
        d.setHomeCountry(homeCountry);
        d.setRoles(rolesOf(Role.RoleType.ROLE_CARRIER));
        return carrierRepository.save(d);
    }

    private Load createLoad(Shipper shipper, String title, String description,
                          String pickup, String delivery,
                          double hours, LocalDate dateNeeded, BigDecimal rate,
                          Carrier.CDLType cdl, LoadStatus status, Carrier assigned) {
        return createLoad(shipper, title, description, pickup, delivery, hours,
                dateNeeded, rate, cdl, status, assigned, Shipment.Mode.ROAD);
    }

    private Load createLoad(Shipper shipper, String title, String description,
                          String pickup, String delivery,
                          double hours, LocalDate dateNeeded, BigDecimal rate,
                          Carrier.CDLType cdl, LoadStatus status, Carrier assigned,
                          Shipment.Mode mode) {
        // Load-level fields only — customer metadata lives on the tree.
        Load j = new Load();
        j.setShipper(shipper);
        j.setEstimatedDurationHours(hours);
        j.setRatePerHour(rate);
        j.setRequiredCdlType(cdl);
        j.setCurrency(shipper.getCurrency() != null ? shipper.getCurrency() : "EUR");
        j.setStatus(status);
        j.setAssignedCarrier(assigned);
        j = loadRepository.save(j);

        // Compose the tree (TransportOrder + Shipment + 2 Stops + Locations).
        String country = shipper.getCountry() != null ? shipper.getCountry() : "IE";
        String currency = shipper.getCurrency() != null ? shipper.getCurrency() : "EUR";
        TmsTreeService.TmsOrderInput input = new TmsTreeService.TmsOrderInput(
                title, description, dateNeeded,
                pickup, delivery, country, country, currency, mode);
        tmsTreeService.createTreeFor(j, input);
        j = loadRepository.save(j);
        pricingService.priceLoad(j);
        return j;
    }

    /** Upsert a typed geography node (port / airport / terminal) by name+country. */
    private void seedNode(String name, String country, Location.LocationType type,
                          String unlocode, String iata) {
        Location loc = locationRepository.findFirstByNameIgnoreCaseAndCountry(name, country)
                .orElseGet(Location::new);
        loc.setName(name);
        if (loc.getAddressLine() == null) loc.setAddressLine(name);
        loc.setCountry(country);
        loc.setLocationType(type);
        loc.setUnlocode(unlocode);
        loc.setIata(iata);
        locationRepository.save(loc);
    }

    private void createApplication(Load load, Carrier carrier, ApplicationStatus status, String note) {
        LoadApplication a = new LoadApplication();
        a.setLoad(load);
        a.setCarrier(carrier);
        a.setStatus(status);
        a.setCoverNote(note);
        a.setAppliedAt(LocalDateTime.now().minusDays(2));
        loadApplicationRepository.save(a);
    }

    private void createDocument(Carrier carrier, DocumentType type, String number,
                                LocalDate expiry, DocumentStatus status) {
        ComplianceDocument doc = new ComplianceDocument();
        doc.setCarrier(carrier);
        doc.setDocumentType(type);
        doc.setDocumentNumber(number);
        doc.setExpiryDate(expiry);
        doc.setStatus(status);
        doc.setUploadedAt(LocalDateTime.now().minusDays(5));
        if (status == DocumentStatus.VERIFIED) {
            doc.setVerifiedAt(LocalDateTime.now().minusDays(2));
        }
        complianceDocumentRepository.save(doc);
    }

    private void createRating(Load load, User reviewer, User reviewee, int score, String comment) {
        Rating r = new Rating();
        r.setLoad(load);
        r.setReviewer(reviewer);
        r.setReviewee(reviewee);
        r.setScore(score);
        r.setComment(comment);
        ratingRepository.save(r);
    }

    private Set<Role> rolesOf(Role.RoleType... roleTypes) {
        Set<Role> roles = new HashSet<>();
        for (Role.RoleType rt : roleTypes) {
            roles.add(roleRepository.findByName(rt)
                    .orElseThrow(() -> new RuntimeException("Role not found: " + rt)));
        }
        return roles;
    }
}
