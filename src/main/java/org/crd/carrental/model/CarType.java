package org.crd.carrental.model;

/**
 * The three car types the fleet is organized by. Each type has its own,
 * independently sized pool of cars and its own reservation lock.
 */
public enum CarType {
    /** A standard passenger sedan. */
    SEDAN,
    /** A sport utility vehicle. */
    SUV,
    /** A cargo/passenger van. */
    VAN
}
