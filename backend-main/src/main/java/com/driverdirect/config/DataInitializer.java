package com.driverdirect.config;

    import com.driverdirect.model.ApplicationStatus;
import com.driverdirect.model.ComplianceDocument;
import com.driverdirect.model.Customer;
import com.driverdirect.model.DocumentStatus;
import com.driverdirect.model.DocumentType;
import com.driverdirect.model.Driver;
import com.driverdirect.model.DriverAvailability;
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
import com.driverdirect.repository.ComplianceDocumentRepository;
import com.driverdirect.repository.CustomerRepository;
import com.driverdirect.repository.DriverAvailabilityRepository;
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
    @Autowired private CustomerRepository customerRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private TransportOrderRepository transportOrderRepository;
    @Autowired private ShipmentRepository shipmentRepository;
    @Autowired private ShipmentLineRepository shipmentLineRepository;
    @Autowired private StopRepository stopRepository;
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
                Driver.CDLType.CLASS_A, 8, LocalDate.now().plusYears(3));
        Driver mairead = createDriver("mairead.osullivan@example.com", "DL-22302-IE",
                "Mairead", "O'Sullivan", "0851000002",
                Driver.CDLType.CLASS_B, 4, LocalDate.now().plusMonths(6));
        Driver kieran = createDriver("kieran.doyle@example.com", "DL-22303-IE",
                "Kieran", "Doyle", "0851000003",
                Driver.CDLType.CLASS_A, 12, LocalDate.now().plusYears(1));
        Driver siobhan = createDriver("siobhan.kennedy@example.com", "DL-22304-IE",
                "Siobhan", "Kennedy", "0851000004",
                Driver.CDLType.CLASS_C, 2, LocalDate.now().plusYears(4));
        Driver patrick = createDriver("patrick.fitzgerald@example.com", "DL-22305-IE",
                "Patrick", "Fitzgerald", "0851000005",
                Driver.CDLType.CLASS_B, 6, LocalDate.now().plusMonths(9));

        Driver seedDriver = driverRepository.findByEmail("driver@example.com").orElseThrow();

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

        // ---- Phase 0 TMS data model: backfill Customer / TransportOrder /
        // Shipment / Stops / Location alongside the existing Job rows. The
        // Job-shaped API stays the source of truth until the façade is swapped.
        backfillTmsTree();
    }

    private void backfillTmsTree() {
        // One default Customer per Employer.
        java.util.Map<Long, Customer> customerByEmployer = new java.util.HashMap<>();
        for (Employer e : employerRepository.findAll()) {
            Customer c = customerRepository.findFirstByEmployerOrderByIdAsc(e).orElseGet(() ->
                    customerRepository.save(new Customer(e, e.getCompanyName() + " (default)")));
            customerByEmployer.put(e.getId(), c);
        }

        for (Job job : jobRepository.findAll()) {
            // Locations: look up by name+country, else create as ad-hoc.
            Location pickupLoc = upsertLocation(job.getPickupLocation(), job.getPickupCountry());
            Location deliveryLoc = upsertLocation(job.getDeliveryLocation(), job.getDeliveryCountry());

            // TransportOrder mirroring the Job's customer-facing metadata.
            TransportOrder order = new TransportOrder();
            order.setEmployer(job.getEmployer());
            order.setCustomer(customerByEmployer.get(job.getEmployer().getId()));
            order.setTitle(job.getTitle());
            order.setDescription(job.getDescription());
            order.setDateNeeded(job.getDateNeeded());
            order.setCurrency(job.getCurrency());
            order.setStatus(mapOrderStatus(job.getStatus()));
            order = transportOrderRepository.save(order);

            // Shipment mirroring the physical move.
            Shipment shipment = new Shipment();
            shipment.setEmployer(job.getEmployer());
            shipment.setMode(Shipment.Mode.ROAD);
            shipment.setStatus(mapShipmentStatus(job.getStatus()));
            shipment.setCurrency(job.getCurrency());
            shipment.setOriginCountry(job.getPickupCountry());
            shipment.setDestinationCountry(job.getDeliveryCountry());
            shipment = shipmentRepository.save(shipment);

            // Two ordered stops.
            Stop pickup = new Stop();
            pickup.setShipment(shipment);
            pickup.setSequence(1);
            pickup.setType(Stop.StopType.PICKUP);
            pickup.setLocation(pickupLoc);
            pickup.setEarliestAt(job.getDateNeeded() != null ? job.getDateNeeded().atTime(8, 0) : null);
            pickup.setLatestAt(job.getDateNeeded() != null ? job.getDateNeeded().atTime(11, 0) : null);
            stopRepository.save(pickup);

            Stop delivery = new Stop();
            delivery.setShipment(shipment);
            delivery.setSequence(2);
            delivery.setType(Stop.StopType.DELIVERY);
            delivery.setLocation(deliveryLoc);
            delivery.setEarliestAt(job.getDateNeeded() != null ? job.getDateNeeded().atTime(13, 0) : null);
            delivery.setLatestAt(job.getDateNeeded() != null ? job.getDateNeeded().atTime(18, 0) : null);
            stopRepository.save(delivery);

            // Link order to shipment.
            ShipmentLine line = new ShipmentLine();
            line.setShipment(shipment);
            line.setOrder(order);
            shipmentLineRepository.save(line);
        }
    }

    private Location upsertLocation(String name, String country) {
        if (name == null || name.isBlank()) return null;
        String iso = country == null ? "IE" : country;
        return locationRepository.findFirstByNameIgnoreCaseAndCountry(name, iso).orElseGet(() -> {
            Location loc = new Location();
            loc.setName(name);
            loc.setAddressLine(name); // free-text source — same string is the best we have
            loc.setCountry(iso);
            return locationRepository.save(loc);
        });
    }

    private TransportOrder.OrderStatus mapOrderStatus(JobStatus js) {
        switch (js) {
            case OPEN:        return TransportOrder.OrderStatus.NEW;
            case ASSIGNED:    return TransportOrder.OrderStatus.PLANNED;
            case IN_PROGRESS: return TransportOrder.OrderStatus.IN_EXECUTION;
            case COMPLETED:   return TransportOrder.OrderStatus.COMPLETED;
            case CANCELLED:   return TransportOrder.OrderStatus.CANCELLED;
            default:          return TransportOrder.OrderStatus.NEW;
        }
    }

    private Shipment.ShipmentStatus mapShipmentStatus(JobStatus js) {
        switch (js) {
            case OPEN:        return Shipment.ShipmentStatus.PLANNED;
            case ASSIGNED:    return Shipment.ShipmentStatus.ACCEPTED;
            case IN_PROGRESS: return Shipment.ShipmentStatus.IN_TRANSIT;
            case COMPLETED:   return Shipment.ShipmentStatus.DELIVERED;
            case CANCELLED:   return Shipment.ShipmentStatus.CANCELLED;
            default:          return Shipment.ShipmentStatus.PLANNED;
        }
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
                                Driver.CDLType cdl, int years, LocalDate licenceExpiry) {
        Driver d = new Driver(email, passwordEncoder.encode("password123"),
                licenseNumber, licenceExpiry);
        d.setFirstName(firstName);
        d.setLastName(lastName);
        d.setPhone(phone);
        d.setCdlType(cdl);
        d.setYearsExperience(years);
        d.setRoles(rolesOf(Role.RoleType.ROLE_DRIVER));
        return driverRepository.save(d);
    }

    private Job createJob(Employer employer, String title, String description,
                          String pickup, String delivery,
                          double hours, LocalDate dateNeeded, BigDecimal rate,
                          Driver.CDLType cdl, JobStatus status, Driver assigned) {
        Job j = new Job();
        j.setEmployer(employer);
        j.setTitle(title);
        j.setDescription(description);
        j.setPickupLocation(pickup);
        j.setDeliveryLocation(delivery);
        j.setEstimatedDurationHours(hours);
        j.setDateNeeded(dateNeeded);
        j.setRatePerHour(rate);
        j.setRequiredCdlType(cdl);
        j.setStatus(status);
        j.setAssignedDriver(assigned);
        return jobRepository.save(j);
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
