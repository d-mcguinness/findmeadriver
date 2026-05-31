package com.driverdirect.config;

    import com.driverdirect.model.ApplicationStatus;
import com.driverdirect.model.CabotageOperation;
import com.driverdirect.model.ComplianceDocument;
import com.driverdirect.model.Customer;
import com.driverdirect.model.DocumentStatus;
import com.driverdirect.model.DocumentType;
import com.driverdirect.model.Driver;
import com.driverdirect.model.DriverAvailability;
import com.driverdirect.model.DriverLane;
import com.driverdirect.model.DriverTimeSlot;
import com.driverdirect.model.Employer;
import com.driverdirect.model.Job;
import com.driverdirect.model.JobApplication;
import com.driverdirect.model.JobStatus;
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
import com.driverdirect.repository.DriverAvailabilityRepository;
import com.driverdirect.repository.DriverLaneRepository;
import com.driverdirect.repository.DriverRepository;
import com.driverdirect.repository.DriverTimeSlotRepository;
import com.driverdirect.repository.EmployerRepository;
import com.driverdirect.repository.JobApplicationRepository;
import com.driverdirect.repository.JobRepository;
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
    @Autowired private DriverRepository driverRepository;
    @Autowired private EmployerRepository employerRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private JobApplicationRepository jobApplicationRepository;
    @Autowired private ComplianceDocumentRepository complianceDocumentRepository;
    @Autowired private RatingRepository ratingRepository;
    @Autowired private DriverAvailabilityRepository driverAvailabilityRepository;
    @Autowired private DriverTimeSlotRepository driverTimeSlotRepository;
    @Autowired private DriverLaneRepository driverLaneRepository;
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
            roleRepository.save(new Role(null, Role.RoleType.ROLE_EMPLOYER));
            roleRepository.save(new Role(null, Role.RoleType.ROLE_DRIVER));
        }
    }

    private void createTestUsers() {
        User admin = new User("admin@driverdirect.com", passwordEncoder.encode("admin123"));
        admin.setFirstName("Admin");
        admin.setLastName("User");
        admin.setRoles(rolesOf(Role.RoleType.ROLE_ADMIN));
        userRepository.save(admin);

        Employer employer = new Employer(
                "employer@company.com",
                passwordEncoder.encode("employer123"),
                "Acme Logistics"
        );
        employer.setFirstName("Employer");
        employer.setLastName("User");
        employer.setPhone("01234567890");
        employer.setIndustry(Employer.Industry.LOGISTICS);
        employer.setCompanySize(50);
        employer.setHeadquartersLocation("Dublin");
        employer.setRoles(rolesOf(Role.RoleType.ROLE_EMPLOYER));
        employerRepository.save(employer);

        Driver driver = new Driver(
                "driver@example.com",
                passwordEncoder.encode("driver123"),
                "DL-12345-IE",
                LocalDate.now().plusYears(2)
        );
        driver.setFirstName("Driver");
        driver.setLastName("User");
        driver.setPhone("09876543210");
        driver.setCdlType(Driver.CDLType.CLASS_A);
        driver.setYearsExperience(5);
        driver.setRoles(rolesOf(Role.RoleType.ROLE_DRIVER));
        driverRepository.save(driver);
    }

    private void createDemoData() {
        // ---- Additional employers ----
        Employer murphy = createEmployer("contact@murphyhaulage.ie", "Murphy Haulage Ltd",
                "Sean", "Murphy", "0851234567",
                Employer.Industry.TRANSPORTATION, 25, "Cork");
        Employer fresh = createEmployer("ops@freshfoods.ie", "Fresh Foods Distribution",
                "Aoife", "Kelly", "0867654321",
                Employer.Industry.FOOD_SERVICE, 120, "Galway");
        Employer buildwell = createEmployer("yard@buildwell.ie", "Buildwell Construction",
                "Liam", "Walsh", "0871112233",
                Employer.Industry.CONSTRUCTION, 80, "Limerick");

        Employer acme = employerRepository.findByEmail("employer@company.com").orElseThrow();

        // ---- Additional drivers ----
        Driver liamByrne = createDriver("liam.byrne@example.com", "DL-22301-IE",
                "Liam", "Byrne", "0851000001",
                Driver.CDLType.CLASS_A, 8, LocalDate.now().plusYears(3), "IE");
        Driver mairead = createDriver("mairead.osullivan@example.com", "DL-22302-IE",
                "Mairead", "O'Sullivan", "0851000002",
                Driver.CDLType.CLASS_B, 4, LocalDate.now().plusMonths(6), "GB");
        Driver kieran = createDriver("kieran.doyle@example.com", "DL-22303-IE",
                "Kieran", "Doyle", "0851000003",
                Driver.CDLType.CLASS_A, 12, LocalDate.now().plusYears(1), "IE");
        Driver siobhan = createDriver("siobhan.kennedy@example.com", "DL-22304-IE",
                "Siobhan", "Kennedy", "0851000004",
                Driver.CDLType.CLASS_C, 2, LocalDate.now().plusYears(4), "IE");
        // Foreign-based driver — drives into IE/FR/DE, so their domestic moves
        // there count as cabotage (see seedCabotage below).
        Driver patrick = createDriver("patrick.fitzgerald@example.com", "DL-22305-IE",
                "Patrick", "Fitzgerald", "0851000005",
                Driver.CDLType.CLASS_B, 6, LocalDate.now().plusMonths(9), "PL");

        Driver seedDriver = driverRepository.findByEmail("driver@example.com").orElseThrow();
        // The primary demo login. Base it in IE so its cabotage ops abroad
        // (seeded below) populate the dashboard's cabotage panel.
        seedDriver.setHomeCountry("IE");
        driverRepository.save(seedDriver);

        // ---- Reference geography (M3): typed port/airport/terminal nodes ----
        // Seeded BEFORE jobs so the tree builder's name+country upsert reuses
        // them — existing road/sea/air/rail jobs then anchor on real nodes.
        seedNode("Dublin Port", "IE", Location.LocationType.SEAPORT, "IEDUB", null);
        seedNode("Port of Rotterdam", "NL", Location.LocationType.SEAPORT, "NLRTM", null);
        seedNode("Ringaskiddy Port", "IE", Location.LocationType.SEAPORT, "IERAK", null);
        seedNode("Cork Airport", "IE", Location.LocationType.AIRPORT, null, "ORK");
        seedNode("Paris Charles de Gaulle", "IE", Location.LocationType.AIRPORT, null, "CDG");
        seedNode("Madrid Barajas", "ES", Location.LocationType.AIRPORT, null, "MAD");
        seedNode("Dublin North Wall", "IE", Location.LocationType.RAIL_TERMINAL, "IEDUB", null);
        seedNode("Ballina Railhead", "IE", Location.LocationType.RAIL_TERMINAL, null, null);

        // ---- Jobs across all statuses ----
        Job j1 = createJob(acme, "Pallet run Dublin → Cork",
                "Standard pallet delivery, 4 stops along the route.",
                "Dublin Port", "Cork City",
                3.5, LocalDate.now().plusDays(2), new BigDecimal("28.00"),
                Driver.CDLType.CLASS_A, JobStatus.OPEN, null);

        Job j2 = createJob(acme, "Refrigerated Dublin → Limerick",
                "Time-sensitive chilled goods, must arrive before 11am.",
                "Naas Distribution Centre", "Limerick Cold Store",
                4.0, LocalDate.now().plusDays(4), new BigDecimal("32.00"),
                Driver.CDLType.CLASS_A, JobStatus.OPEN, null);

        Job j3 = createJob(murphy, "Equipment haul Cork → Galway",
                "Heavy plant equipment transport for civil engineering project.",
                "Cork Industrial Estate", "Galway Site B",
                5.5, LocalDate.now().plusDays(7), new BigDecimal("35.00"),
                Driver.CDLType.CLASS_A, JobStatus.OPEN, null);

        Job j4 = createJob(fresh, "Daily produce run",
                "Morning produce delivery to restaurants across Galway city centre.",
                "Galway Wholesale Market", "Galway City Centre",
                2.0, LocalDate.now().plusDays(1), new BigDecimal("22.00"),
                Driver.CDLType.CLASS_C, JobStatus.OPEN, null);

        // ---- Multi-modal demo jobs (M1: transport mode is now selectable) ----
        Job jAir = createJob(fresh, "Air freight Cork → Paris CDG",
                "Time-critical chilled pharma on a palletised ULD.",
                "Cork Airport", "Paris Charles de Gaulle",
                3.0, LocalDate.now().plusDays(2), new BigDecimal("80.00"),
                Driver.CDLType.CLASS_C, JobStatus.OPEN, null, Shipment.Mode.AIR);

        Job jSea = createJob(acme, "Sea freight Dublin → Rotterdam",
                "FCL container on the Dublin–Rotterdam lane, drayage included.",
                "Dublin Port", "Port of Rotterdam",
                8.0, LocalDate.now().plusDays(6), new BigDecimal("12.00"),
                Driver.CDLType.CLASS_A, JobStatus.OPEN, null, Shipment.Mode.OCEAN);

        Job jRail = createJob(murphy, "Rail intermodal Dublin → Ballina",
                "Containerised rail haul, terminal-to-terminal block-train slot.",
                "Dublin North Wall", "Ballina Railhead",
                6.0, LocalDate.now().plusDays(5), new BigDecimal("18.00"),
                Driver.CDLType.CLASS_A, JobStatus.OPEN, null, Shipment.Mode.RAIL);

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

        Job j5 = createJob(buildwell, "Concrete blocks to site",
                "Building materials delivery, forklift access on site.",
                "Buildwell Yard, Limerick", "Mungret Site",
                1.5, LocalDate.now().plusDays(3), new BigDecimal("24.00"),
                Driver.CDLType.CLASS_B, JobStatus.ASSIGNED, mairead);

        Job j6 = createJob(acme, "Long-haul Dublin → Belfast",
                "Cross-border pallet delivery, customs paperwork prepared.",
                "Dublin Port", "Belfast Distribution Hub",
                4.5, LocalDate.now().plusDays(5), new BigDecimal("38.00"),
                Driver.CDLType.CLASS_A, JobStatus.ASSIGNED, liamByrne);

        Job j7 = createJob(murphy, "Cork ferry collection",
                "Container collection from Ringaskiddy ferry terminal.",
                "Ringaskiddy Port", "Mahon Distribution",
                2.5, LocalDate.now(), new BigDecimal("30.00"),
                Driver.CDLType.CLASS_A, JobStatus.IN_PROGRESS, kieran);

        Job j8 = createJob(fresh, "Restaurant route — south Galway",
                "Multi-drop chilled goods delivery, 8 stops.",
                "Fresh Foods Depot", "Salthill and Barna restaurants",
                3.0, LocalDate.now(), new BigDecimal("26.00"),
                Driver.CDLType.CLASS_C, JobStatus.IN_PROGRESS, siobhan);

        Job j9 = createJob(acme, "Dublin → Waterford completed",
                "Pallet delivery completed on schedule.",
                "Dublin Port", "Waterford Industrial Estate",
                3.0, LocalDate.now().minusDays(3), new BigDecimal("28.00"),
                Driver.CDLType.CLASS_A, JobStatus.COMPLETED, seedDriver);

        Job j10 = createJob(murphy, "Cork → Wexford completed",
                "Heavy haulage completed without issue.",
                "Cork Industrial Estate", "Wexford Yard",
                4.0, LocalDate.now().minusDays(7), new BigDecimal("33.00"),
                Driver.CDLType.CLASS_A, JobStatus.COMPLETED, kieran);

        Job j11 = createJob(fresh, "Galway → Sligo produce",
                "Refrigerated produce, delivered.",
                "Galway Wholesale Market", "Sligo Distribution",
                3.5, LocalDate.now().minusDays(10), new BigDecimal("25.00"),
                Driver.CDLType.CLASS_B, JobStatus.COMPLETED, patrick);

        Job j12 = createJob(buildwell, "Cancelled construction haul",
                "Job cancelled — site delays.",
                "Limerick Yard", "Ennis Site",
                2.0, LocalDate.now().minusDays(2), new BigDecimal("24.00"),
                Driver.CDLType.CLASS_B, JobStatus.CANCELLED, null);

        Job j13 = createJob(acme, "Cancelled Dublin run",
                "Customer cancelled the order.",
                "Dublin Port", "Drogheda Warehouse",
                1.5, LocalDate.now().plusDays(8), new BigDecimal("26.00"),
                Driver.CDLType.CLASS_A, JobStatus.CANCELLED, null);

        // ---- Job applications ----
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

        createDocument(seedDriver, DocumentType.DRIVING_LICENCE, "DL-12345-IE",
                LocalDate.now().plusYears(2), DocumentStatus.VERIFIED);
        createDocument(seedDriver, DocumentType.CPC_CARD, "CPC-DD-0001",
                LocalDate.now().plusYears(2), DocumentStatus.VERIFIED);

        // ---- Ratings on completed jobs (both directions) ----
        createRating(j9, acme, seedDriver, 5, "Excellent communication, on time.");
        createRating(j9, seedDriver, acme, 4, "Clear instructions, would work with again.");
        createRating(j10, murphy, kieran, 5, "Top-tier haulage, immaculate paperwork.");
        createRating(j10, kieran, murphy, 5, "Fair rate, prompt payment.");
        createRating(j11, fresh, patrick, 3, "Delivery completed but ~40 mins late.");

        // ---- Driver availability for the next 14 days ----
        // EU tachograph caps: 9h/day standard, 10h up to twice/week.
        seedAvailability(seedDriver, new double[]{8, 9, 0, 9, 9, 6, 0,  9, 10, 0, 9, 9, 8, 0});
        seedAvailability(liamByrne,  new double[]{9, 9, 9, 9, 10, 0, 0, 9, 9, 9, 10, 0, 0, 8});
        seedAvailability(kieran,     new double[]{10, 9, 9, 0, 0, 9, 9, 10, 9, 9, 0, 0, 9, 9});
        seedAvailability(mairead,    new double[]{8, 8, 0, 8, 8, 0, 0,  8, 8, 0, 8, 8, 6, 0});
        seedAvailability(siobhan,    new double[]{6, 0, 6, 6, 6, 0, 0,  6, 6, 0, 6, 6, 6, 0});
        seedAvailability(patrick,    new double[]{0, 0, 8, 8, 8, 0, 0,  8, 8, 8, 8, 0, 0, 8});

        // ---- Multi-stop international jobs (exercise the route renderer +
        //      bookkeeping StopTypes: FERRY_TERMINAL / EUROTUNNEL / BORDER / REST) ----
        Job jFerry = createMultiStopJob(acme,
                "Dublin → London (ferry + multi-drop)",
                "Cross-channel pallet run via Holyhead, overnight rest near Birmingham.",
                8.0, LocalDate.now().plusDays(1), new BigDecimal("34.00"),
                Driver.CDLType.CLASS_A, JobStatus.OPEN, null,
                List.of(
                        stop(Stop.StopType.PICKUP,         "Dublin Port",            "Dublin",     "IE"),
                        stop(Stop.StopType.FERRY_TERMINAL, "Holyhead Ferry Port",    "Holyhead",   "GB"),
                        stop(Stop.StopType.REST,           "Hilton Park Services M6","Birmingham", "GB"),
                        stop(Stop.StopType.DELIVERY,       "London Gateway DC",      "London",     "GB")));

        Job jTunnel = createMultiStopJob(acme,
                "Dover → Paris (Eurotunnel)",
                "Time-critical chilled goods through the Channel Tunnel, customs at Calais.",
                8.5, LocalDate.now().plusDays(3), new BigDecimal("39.00"),
                Driver.CDLType.CLASS_A, JobStatus.OPEN, null,
                List.of(
                        stop(Stop.StopType.PICKUP,      "Dover Cold Store",      "Dover",   "GB"),
                        stop(Stop.StopType.EUROTUNNEL,  "Folkestone Eurotunnel", "Folkestone", "GB"),
                        stop(Stop.StopType.BORDER,      "Calais Border Post",    "Calais",  "FR"),
                        stop(Stop.StopType.REST,        "Aire de Saint-Léger",   "Arras",   "FR"),
                        stop(Stop.StopType.DELIVERY,    "Rungis Market",         "Paris",   "FR")));

        // ---- Driver lanes (origin → destination country opt-ins) ----
        // Demo driver gets a domestic lane (keeps existing IE jobs visible) plus
        // the two cross-border lanes that match the multi-stop jobs above.
        addLanes(seedDriver, "IE", "IE", "IE", "GB", "GB", "FR");
        addLanes(liamByrne, "IE", "IE", "IE", "GB");
        addLanes(kieran, "IE", "IE");

        // ---- Cabotage history for the demo driver (IE-based) ----
        // GB: 2 ops in the rolling 7-day window → under the limit of 3.
        // FR: 3 ops → AT the limit, so the dashboard shows the red "blocked" tag.
        // deliveryLocation is provenance only (the unload point); the country
        // string is what the limit is counted on. A couple of ops are left
        // without a location to mirror back-filled/imported history.
        Location gbDc = adHocLocation("Birmingham RDC", "Birmingham", "GB");
        Location frDc = adHocLocation("Paris-Sud Plateforme", "Paris", "FR");
        Location deDc = adHocLocation("München Logistikzentrum", "Munich", "DE");

        recordCabotage(seedDriver, "GB", LocalDate.now().minusDays(1), gbDc);
        recordCabotage(seedDriver, "GB", LocalDate.now().minusDays(4), null);
        recordCabotage(seedDriver, "FR", LocalDate.now(), frDc);
        recordCabotage(seedDriver, "FR", LocalDate.now().minusDays(2), frDc);
        recordCabotage(seedDriver, "FR", LocalDate.now().minusDays(5), null);
        // Foreign (PL) driver doing domestic German moves — a second worked example.
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

    private Job createMultiStopJob(Employer employer, String title, String description,
                                   double hours, LocalDate dateNeeded, BigDecimal rate,
                                   Driver.CDLType cdl, JobStatus status, Driver assigned,
                                   List<TmsTreeService.TmsStopInput> stops) {
        Job j = new Job();
        j.setEmployer(employer);
        j.setEstimatedDurationHours(hours);
        j.setRatePerHour(rate);
        j.setRequiredCdlType(cdl);
        j.setCurrency(employer.getCurrency() != null ? employer.getCurrency() : "EUR");
        j.setStatus(status);
        j.setAssignedDriver(assigned);
        j = jobRepository.save(j);

        // Origin/destination countries come from the first PICKUP / last DELIVERY
        // in the route; the tree builder reads the stops list in preference to
        // the legacy pickup/delivery pair (passed null here).
        String currency = employer.getCurrency() != null ? employer.getCurrency() : "EUR";
        String pickupCountry = stops.get(0).country();
        String deliveryCountry = stops.get(stops.size() - 1).country();
        TmsTreeService.TmsOrderInput input = new TmsTreeService.TmsOrderInput(
                title, description, dateNeeded,
                null, null, pickupCountry, deliveryCountry, currency, stops);
        tmsTreeService.createTreeFor(j, input);
        j = jobRepository.save(j);
        pricingService.priceJob(j);
        return j;
    }

    /** Add lanes from a flat (origin, destination, origin, destination, ...) list. */
    private void addLanes(Driver driver, String... pairs) {
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            DriverLane lane = new DriverLane();
            lane.setDriver(driver);
            lane.setOriginCountry(pairs[i]);
            lane.setDestinationCountry(pairs[i + 1]);
            driverLaneRepository.save(lane);
        }
    }

    private void recordCabotage(Driver driver, String country, LocalDate performedAt,
                                Location deliveryLocation) {
        CabotageOperation op = new CabotageOperation();
        op.setDriver(driver);
        op.setCountry(country);
        op.setPerformedAt(performedAt);
        op.setDeliveryLocation(deliveryLocation);
        cabotageOperationRepository.save(op);
    }

    private void seedAvailability(Driver driver, double[] hoursForNext14Days) {
        LocalDate start = LocalDate.now();
        for (int i = 0; i < hoursForNext14Days.length; i++) {
            double h = hoursForNext14Days[i];
            if (h <= 0) continue;
            LocalDate date = start.plusDays(i);
            driverAvailabilityRepository.save(new DriverAvailability(driver, date, h));
            seedTimeSlotsForDay(driver, date, h);
        }
    }

    private void seedTimeSlotsForDay(Driver driver, LocalDate date, double totalHours) {
        // Split into one or two realistic windows so the calendar looks lived-in.
        // <= 5h: single morning slot. > 5h: morning + afternoon either side of lunch.
        if (totalHours <= 5) {
            LocalTime startT = LocalTime.of(9, 0);
            LocalTime endT = startT.plusMinutes((long) Math.round(totalHours * 60));
            driverTimeSlotRepository.save(new DriverTimeSlot(driver, date, startT, endT));
        } else {
            double morning = Math.min(4, totalHours / 2);
            double afternoon = totalHours - morning;
            LocalTime mStart = LocalTime.of(9, 0);
            LocalTime mEnd = mStart.plusMinutes((long) Math.round(morning * 60));
            LocalTime aStart = LocalTime.of(13, 30);
            LocalTime aEnd = aStart.plusMinutes((long) Math.round(afternoon * 60));
            driverTimeSlotRepository.save(new DriverTimeSlot(driver, date, mStart, mEnd));
            driverTimeSlotRepository.save(new DriverTimeSlot(driver, date, aStart, aEnd));
        }
    }

    // ---- helpers ----

    private Employer createEmployer(String email, String company,
                                    String firstName, String lastName, String phone,
                                    Employer.Industry industry, int size, String hq) {
        Employer e = new Employer(email, passwordEncoder.encode("password123"), company);
        e.setFirstName(firstName);
        e.setLastName(lastName);
        e.setPhone(phone);
        e.setIndustry(industry);
        e.setCompanySize(size);
        e.setHeadquartersLocation(hq);
        e.setRoles(rolesOf(Role.RoleType.ROLE_EMPLOYER));
        return employerRepository.save(e);
    }

    private Driver createDriver(String email, String licenseNumber,
                                String firstName, String lastName, String phone,
                                Driver.CDLType cdl, int years, LocalDate licenceExpiry,
                                String homeCountry) {
        Driver d = new Driver(email, passwordEncoder.encode("password123"),
                licenseNumber, licenceExpiry);
        d.setFirstName(firstName);
        d.setLastName(lastName);
        d.setPhone(phone);
        d.setCdlType(cdl);
        d.setYearsExperience(years);
        d.setHomeCountry(homeCountry);
        d.setRoles(rolesOf(Role.RoleType.ROLE_DRIVER));
        return driverRepository.save(d);
    }

    private Job createJob(Employer employer, String title, String description,
                          String pickup, String delivery,
                          double hours, LocalDate dateNeeded, BigDecimal rate,
                          Driver.CDLType cdl, JobStatus status, Driver assigned) {
        return createJob(employer, title, description, pickup, delivery, hours,
                dateNeeded, rate, cdl, status, assigned, Shipment.Mode.ROAD);
    }

    private Job createJob(Employer employer, String title, String description,
                          String pickup, String delivery,
                          double hours, LocalDate dateNeeded, BigDecimal rate,
                          Driver.CDLType cdl, JobStatus status, Driver assigned,
                          Shipment.Mode mode) {
        // Load-level fields only — customer metadata lives on the tree.
        Job j = new Job();
        j.setEmployer(employer);
        j.setEstimatedDurationHours(hours);
        j.setRatePerHour(rate);
        j.setRequiredCdlType(cdl);
        j.setCurrency(employer.getCurrency() != null ? employer.getCurrency() : "EUR");
        j.setStatus(status);
        j.setAssignedDriver(assigned);
        j = jobRepository.save(j);

        // Compose the tree (TransportOrder + Shipment + 2 Stops + Locations).
        String country = employer.getCountry() != null ? employer.getCountry() : "IE";
        String currency = employer.getCurrency() != null ? employer.getCurrency() : "EUR";
        TmsTreeService.TmsOrderInput input = new TmsTreeService.TmsOrderInput(
                title, description, dateNeeded,
                pickup, delivery, country, country, currency, mode);
        tmsTreeService.createTreeFor(j, input);
        j = jobRepository.save(j);
        pricingService.priceJob(j);
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

    private void createApplication(Job job, Driver driver, ApplicationStatus status, String note) {
        JobApplication a = new JobApplication();
        a.setJob(job);
        a.setDriver(driver);
        a.setStatus(status);
        a.setCoverNote(note);
        a.setAppliedAt(LocalDateTime.now().minusDays(2));
        jobApplicationRepository.save(a);
    }

    private void createDocument(Driver driver, DocumentType type, String number,
                                LocalDate expiry, DocumentStatus status) {
        ComplianceDocument doc = new ComplianceDocument();
        doc.setDriver(driver);
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

    private void createRating(Job job, User reviewer, User reviewee, int score, String comment) {
        Rating r = new Rating();
        r.setJob(job);
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
