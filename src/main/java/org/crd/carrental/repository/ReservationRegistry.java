package org.crd.carrental.repository;

import org.crd.carrental.model.Car;
import org.crd.carrental.model.Reservation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Stores reservations, keyed by id and indexed by car id. Implementations
 * are expected to be thread-safe on their own, without relying on an
 * external lock. Reservations are append-only: there is no update or
 * delete operation.
 */
public interface ReservationRegistry {

    /**
     * Creates and stores a new reservation.
     *
     * @param car           the reserved car
     * @param startDateTime the start of the booked period (inclusive)
     * @param endDateTime   the end of the booked period (exclusive)
     * @return the newly created reservation, with a generated {@code reservationId}
     * @throws IllegalArgumentException if {@code startDateTime} is not before {@code endDateTime}
     */
    Reservation create(Car car, LocalDateTime startDateTime, LocalDateTime endDateTime);

    /**
     * @param reservationId the id to look up
     * @return the matching reservation, or empty if none exists with this id
     */
    Optional<Reservation> findById(long reservationId);

    /**
     * @param carId the car id to look up
     * @return every reservation for this car, in creation order; empty if none exist
     */
    List<Reservation> findByCarId(String carId);

}
