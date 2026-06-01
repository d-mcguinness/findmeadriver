package com.driverdirect.service;

/**
 * Decides whether a carrier/operator holding credential {@code have} satisfies a
 * load requiring {@code required}. Road uses the HGV/CDL covers-lattice; other
 * modes get their own matcher as mode-specific credentials are modelled (M4).
 */
@FunctionalInterface
public interface CredentialMatcher {
    boolean satisfies(String have, String required);
}