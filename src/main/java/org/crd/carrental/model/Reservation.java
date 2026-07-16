package org.crd.carrental.model;

import java.time.LocalDateTime;

/**
 * A confirmed booking of one car for a date range. Immutable and
 * append-only: once created, a reservation is never changed or removed.
 *
 * @param reservationId server-generated unique identifier
 * @param carId         the reserved car's id
 * @param carType       the reserved car's type, duplicated here for convenience
 * @param startDateTime the start of the booked period (inclusive)
 * @param endDateTime   the end of the booked period (exclusive)
 */
public record Reservation (
    long reservationId,
    String carId,
    CarType carType,
    LocalDateTime startDateTime,
    LocalDateTime endDateTime)
{}
