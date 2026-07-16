package org.crd.carrental.repository;

import org.crd.carrental.model.Car;
import org.crd.carrental.model.CarType;

import java.util.List;
import java.util.Optional;

/**
 * Stores the fleet's cars, keyed by id and indexed by {@link CarType}.
 * Implementations are expected to be thread-safe on their own, without
 * relying on an external lock.
 */
public interface CarRegistry {

    /**
     * Registers a new car.
     *
     * @param car the car to add
     * @return the added car's id, same as {@code car.carId()}
     * @throws org.crd.carrental.exception.CarAlreadyExistsException if a car
     *         with this id is already registered
     */
    String add(Car car);

    /**
     * @param carId the id to look up
     * @return the matching car, or empty if no car with this id is registered
     */
    Optional<Car> findById(String carId);

    /**
     * @param type the type to look up
     * @return every registered car of this type, in registration order;
     *         empty if none are registered
     */
    List<Car> findByCarType(CarType type);
}
