package com.driverdirect.service;

/**
 * Decides whether a driver/operator holding credential {@code have} satisfies a
 * job requiring {@code required}. Road uses the HGV/CDL covers-lattice; other
 * modes get their own matcher as mode-specific credentials are modelled (M4).
 */
@FunctionalInterface
public interface CredentialMatcher {
    boolean satisfies(String have, String required);
}