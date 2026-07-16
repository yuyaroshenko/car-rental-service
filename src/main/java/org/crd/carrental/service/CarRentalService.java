package org.crd.carrental.service;

import org.crd.carrental.model.CarType;
import org.crd.carrental.model.Reservation;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Public API for the car rental system: register cars, reserve one for a
 * period, and look up a car's reservation history.
 */
public interface CarRentalService {

    /**
     * Registers a new car of the given type.
     *
     * @param carId caller-supplied unique id (for example, a VIN or fleet tag)
     * @param type  the car's type
     * @throws org.crd.carrental.exception.InvalidRequestException if {@code carId} is null or blank
     * @throws org.crd.carrental.exception.CarAlreadyExistsException if {@code carId} is already registered
     */
    void addCar(String carId, CarType type);

    /**
     * Reserves a car of the given type for a period starting at {@code start}
     * and lasting {@code numberOfDays} days.
     *
     * @param type         the requested car type
     * @param start        the start of the desired period
     * @param numberOfDays length of the reservation, in days; must be positive
     * @return the created reservation
     * @throws org.crd.carrental.exception.InvalidRequestException if {@code numberOfDays} is not positive
     * @throws org.crd.carrental.exception.NoAvailableCarException if no car of this type is free for the whole period
     */
    Reservation reserveCar(CarType type, LocalDateTime start, int numberOfDays);

    /**
     * @param carId the car id to look up
     * @return every reservation for this car, in creation order; empty if none exist
     */
    List<Reservation> getReservationsForCar(String carId);

}
