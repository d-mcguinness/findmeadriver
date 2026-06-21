package com.driverdirect.service;

import com.driverdirect.model.ApplicationStatus;
import com.driverdirect.model.Carrier;
import com.driverdirect.model.Load;
import com.driverdirect.model.LoadApplication;
import com.driverdirect.model.LoadStatus;
import com.driverdirect.model.Shipper;
import com.driverdirect.repository.LoadApplicationRepository;
import com.driverdirect.repository.LoadRepository;
import com.driverdirect.service.CabotageService.CabotageCheck;
import com.driverdirect.service.CabotageService.CheckResult;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Accept-time cabotage re-check. The apply-time CABOTAGE gate can go stale —
 * a carrier can cross the 3-in-7 limit (via other completed loads) between
 * applying and being accepted — so {@link LoadApplicationServiceImpl#acceptApplication}
 * re-runs {@link CabotageService#check} and refuses to assign an over-limit carrier.
 * Repositories + CabotageService are mocked; we drive the accept flow directly.
 */
class AcceptApplicationCabotageTest {

    private final LoadApplicationRepository appRepo = mock(LoadApplicationRepository.class);
    private final LoadRepository loadRepo = mock(LoadRepository.class);
    private final CabotageService cabotage = mock(CabotageService.class);
    private final LoadApplicationServiceImpl service =
            new LoadApplicationServiceImpl(appRepo, loadRepo, null, null, null, cabotage, null, null);

    private final Shipper shipper = shipper(7L);

    private Shipper shipper(Long id) {
        Shipper s = new Shipper();
        s.setId(id);
        return s;
    }

    private Carrier carrier(Long id) {
        Carrier c = new Carrier();
        c.setId(id);
        c.setFirstName("Jan");
        c.setLastName("Kowalski");
        c.setEmail("jan@example.com");
        return c;
    }

    /** A PENDING application by {@code carrier} for an OPEN load owned by {@link #shipper}. */
    private LoadApplication pendingApplication(Carrier carrier) {
        Load load = new Load();
        load.setId(100L);
        load.setStatus(LoadStatus.OPEN);
        load.setShipper(shipper);
        LoadApplication app = new LoadApplication();
        app.setId(55L);
        app.setLoad(load);
        app.setCarrier(carrier);
        app.setStatus(ApplicationStatus.PENDING);
        return app;
    }

    @Test
    void accept_blockedWhenCarrierOverCabotageLimit() {
        Carrier carrier = carrier(1L);
        LoadApplication app = pendingApplication(carrier);
        when(appRepo.findById(55L)).thenReturn(Optional.of(app));
        when(cabotage.check(carrier, app.getLoad()))
                .thenReturn(new CabotageCheck(CheckResult.OVER_LIMIT, "FR", 3, 3));

        assertThatThrownBy(() -> service.acceptApplication(55L, shipper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cabotage")
                .hasMessageContaining("FR")
                .hasMessageContaining("3 of 3");

        // The guard sits BEFORE any mutation: nothing was accepted or assigned.
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(app.getLoad().getStatus()).isEqualTo(LoadStatus.OPEN);
        assertThat(app.getLoad().getAssignedCarrier()).isNull();
        verify(appRepo, never()).save(any());
        verify(loadRepo, never()).save(any());
    }

    @Test
    void accept_succeedsWhenNotOverCabotageLimit() {
        Carrier carrier = carrier(2L);
        LoadApplication app = pendingApplication(carrier);
        when(appRepo.findById(55L)).thenReturn(Optional.of(app));
        when(cabotage.check(carrier, app.getLoad()))
                .thenReturn(new CabotageCheck(CheckResult.OK, "FR", 1, 3));

        assertThatCode(() -> service.acceptApplication(55L, shipper)).doesNotThrowAnyException();

        // Normal accept side-effects still happen when cabotage is clear.
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.ACCEPTED);
        assertThat(app.getLoad().getStatus()).isEqualTo(LoadStatus.ASSIGNED);
        assertThat(app.getLoad().getAssignedCarrier()).isEqualTo(carrier);
        verify(appRepo).save(app);
        verify(loadRepo).save(app.getLoad());
    }
}
