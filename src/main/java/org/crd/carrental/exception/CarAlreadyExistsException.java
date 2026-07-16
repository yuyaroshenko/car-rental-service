package org.crd.carrental.exception;

import org.crd.carrental.model.Car;

/**
 * Thrown when {@link org.crd.carrental.repository.CarRegistry#add} is
 * called with a {@code carId} that is already registered.
 */
public class CarAlreadyExistsException extends RuntimeException {

    /**
     * @param car the car whose id was already registered
     */
    public CarAlreadyExistsException(Car car) {
        super("The car with carId='" + car.carId() + "' has already been registered");
    }

}
