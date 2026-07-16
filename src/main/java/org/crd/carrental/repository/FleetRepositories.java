package org.crd.carrental.repository;

/**
 * Bundles the two repositories {@link org.crd.carrental.service.CarRentalSystem}
 * needs: one for cars, one for reservations. Passed in through the
 * constructor so the in-memory implementations can be swapped for
 * persistent ones without changing the service.
 *
 * @param carRepository         stores the fleet's cars
 * @param reservationRepository stores reservations
 */
public record FleetRepositories(
        CarRegistry carRepository,
        ReservationRegistry reservationRepository) {
}
