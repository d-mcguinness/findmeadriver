package com.driverdirect.service;

import com.driverdirect.model.ApplicationStatus;
import com.driverdirect.model.Load;
import com.driverdirect.model.LoadApplication;
import com.driverdirect.model.LoadStatus;
import com.driverdirect.model.Shipment;
import com.driverdirect.service.LoadApplicationService.Eligibility;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The eligibility gate ladder — {@link LoadApplicationServiceImpl#evaluate}, the single
 * query-free rule set shared by the apply path, the single-carrier check, and the batch
 * admin preview so they can never drift. Order matters: the first failing gate wins —
 * LOAD_NOT_OPEN → ALREADY_APPLIED → MODE_UNSUPPORTED → LICENCE → AVAILABILITY → CABOTAGE → OK.
 *
 * evaluate() takes already-resolved facts and its only collaborator is the (pure)
 * CredentialMatcherRegistry, so the ladder is exercised directly with plain objects and
 * the real registry — no repositories, no mocks. The unused constructor collaborators are
 * never touched by evaluate(), hence null is safe.
 */
class EligibilityEvaluateTest {

    private final LoadApplicationServiceImpl service =
            new LoadApplicationServiceImpl(null, null, null, null, null, null,
                    new CredentialMatcherRegistry(), null);

    private Load load(LoadStatus status, Shipment.Mode mode, String requiredLicence, double hours) {
        Shipment s = new Shipment();
        s.setMode(mode);                       // Load.getMode() reads off the linked Shipment
        Load l = new Load();
        l.setShipment(s);
        l.setStatus(status);
        l.setRequiredLicenceCategory(requiredLicence);
        l.setEstimatedDurationHours(hours);
        return l;
    }

    /** An OPEN road load needing a C licence and 8 hours — the baseline most tests vary. */
    private Load openRoadLoad() {
        return load(LoadStatus.OPEN, Shipment.Mode.ROAD, "C", 8.0);
    }

    private LoadApplication application(ApplicationStatus status) {
        LoadApplication a = new LoadApplication();
        a.setStatus(status);
        return a;
    }

    // ---- happy path ----------------------------------------------------------------

    @Test
    void allGatesPass_isOK() {
        // open · no prior app · road mode supported · CE covers C · 10h ≥ 8h · no cabotage
        assertThat(service.evaluate(openRoadLoad(), null, "CE", Set.of(), true, 10.0, false))
                .isEqualTo(Eligibility.OK);
    }

    @Test
    void withdrawnPriorApplicationIsRevivable() {
        // a WITHDRAWN application does NOT block a fresh attempt — it falls through to OK.
        assertThat(service.evaluate(openRoadLoad(), application(ApplicationStatus.WITHDRAWN),
                "CE", Set.of(), true, 10.0, false))
                .isEqualTo(Eligibility.OK);
    }

    @Test
    void noLicenceRequirement_isOK() {
        Load l = load(LoadStatus.OPEN, Shipment.Mode.ROAD, null, 8.0);   // no requirement
        assertThat(service.evaluate(l, null, null, Set.of(), true, 10.0, false))
                .isEqualTo(Eligibility.OK);
    }

    @Test
    void airWithCredential_isOK() {
        Load air = load(LoadStatus.OPEN, Shipment.Mode.AIR, null, 8.0);
        assertThat(service.evaluate(air, null, "C", Set.of("AIR:ATPL"), true, 10.0, false))
                .isEqualTo(Eligibility.OK);
    }

    // ---- each gate in isolation ----------------------------------------------------

    @Test
    void loadNotOpen() {
        Load l = load(LoadStatus.ASSIGNED, Shipment.Mode.ROAD, "C", 8.0);
        assertThat(service.evaluate(l, null, "CE", Set.of(), true, 10.0, false))
                .isEqualTo(Eligibility.LOAD_NOT_OPEN);
    }

    @Test
    void alreadyApplied_whenActivePriorApplication() {
        // PENDING / ACCEPTED / REJECTED all block re-applying; only WITHDRAWN is revivable.
        for (ApplicationStatus s : new ApplicationStatus[]{
                ApplicationStatus.PENDING, ApplicationStatus.ACCEPTED, ApplicationStatus.REJECTED}) {
            assertThat(service.evaluate(openRoadLoad(), application(s), "CE", Set.of(), true, 10.0, false))
                    .as("an active %s application should block", s)
                    .isEqualTo(Eligibility.ALREADY_APPLIED);
        }
    }

    @Test
    void modeUnsupported() {
        Load ocean = load(LoadStatus.OPEN, Shipment.Mode.OCEAN, null, 8.0);
        assertThat(service.evaluate(ocean, null, null, Set.of("OCEAN:STCW"), false, 10.0, false))
                .isEqualTo(Eligibility.MODE_UNSUPPORTED);
    }

    @Test
    void licence_road_whenCategoryTooLow() {
        // road load requires CE; carrier holds only C → C does not cover CE in the lattice.
        Load l = load(LoadStatus.OPEN, Shipment.Mode.ROAD, "CE", 8.0);
        assertThat(service.evaluate(l, null, "C", Set.of(), true, 10.0, false))
                .isEqualTo(Eligibility.LICENCE);
    }

    @Test
    void licence_air_whenNoAirCredential() {
        // air requires a mode credential; the road licence string is irrelevant for air.
        Load air = load(LoadStatus.OPEN, Shipment.Mode.AIR, null, 8.0);
        assertThat(service.evaluate(air, null, "C", Set.of(), true, 10.0, false))
                .isEqualTo(Eligibility.LICENCE);
        // holding an unrelated (ocean) credential is still not an air credential.
        assertThat(service.evaluate(air, null, "C", Set.of("OCEAN:STCW"), true, 10.0, false))
                .isEqualTo(Eligibility.LICENCE);
    }

    @Test
    void availability_whenInsufficientHours() {
        Load l = load(LoadStatus.OPEN, Shipment.Mode.ROAD, "C", 8.0);
        assertThat(service.evaluate(l, null, "CE", Set.of(), true, 5.0, false))   // 5 < 8
                .isEqualTo(Eligibility.AVAILABILITY);
    }

    @Test
    void availability_exactlyEnoughHoursIsOK() {
        Load l = load(LoadStatus.OPEN, Shipment.Mode.ROAD, "C", 8.0);
        assertThat(service.evaluate(l, null, "CE", Set.of(), true, 8.0, false))   // 8 is not < 8
                .isEqualTo(Eligibility.OK);
    }

    @Test
    void cabotage_whenBlocking() {
        assertThat(service.evaluate(openRoadLoad(), null, "CE", Set.of(), true, 10.0, true))
                .isEqualTo(Eligibility.CABOTAGE);
    }

    // ---- ordering: the first failing gate wins -------------------------------------

    @Test
    void notOpenBeatsEveryOtherFailure() {
        // closed AND mode unsupported AND prior app AND no hours AND cabotage → LOAD_NOT_OPEN.
        Load closed = load(LoadStatus.CANCELLED, Shipment.Mode.OCEAN, "CE", 8.0);
        assertThat(service.evaluate(closed, application(ApplicationStatus.PENDING),
                "C", Set.of(), false, 0.0, true))
                .isEqualTo(Eligibility.LOAD_NOT_OPEN);
    }

    @Test
    void modeBeatsLicenceAndCabotage() {
        // unsupported mode AND bad licence AND cabotage → MODE_UNSUPPORTED (the earlier gate).
        Load ocean = load(LoadStatus.OPEN, Shipment.Mode.OCEAN, "CE", 8.0);
        assertThat(service.evaluate(ocean, null, "C", Set.of(), false, 0.0, true))
                .isEqualTo(Eligibility.MODE_UNSUPPORTED);
    }

    @Test
    void availabilityBeatsCabotage() {
        // both availability and cabotage fail → AVAILABILITY (it sits earlier in the ladder).
        assertThat(service.evaluate(openRoadLoad(), null, "CE", Set.of(), true, 1.0, true))
                .isEqualTo(Eligibility.AVAILABILITY);
    }
}
