package org.crd.carrental.exception;

import org.crd.carrental.model.CarType;

/**
 * Thrown when {@link org.crd.carrental.service.CarRentalSystem#reserveCar}
 * finds no car of the requested type that is free for the whole requested
 * period.
 */
public class NoAvailableCarException extends RuntimeException {

    /**
     * @param type the car type that had no available car
     */
    public NoAvailableCarException(CarType type) {
        super("No available car for type " + type);
    }
}
