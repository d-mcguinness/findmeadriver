package com.driverdirect.service;

import java.util.Set;

/**
 * Decides whether a carrier satisfies a load's credential requirement, dispatched
 * by transport mode (M1c seam, M4 credentials). Road uses the HGV/CDL covers-
 * lattice on {@code roadLicence}; non-road modes check the carrier's mode-tagged
 * {@code credentials}.
 */
@FunctionalInterface
public interface CredentialMatcher {
    boolean satisfies(String roadLicence, Set<String> credentials, String required);
}