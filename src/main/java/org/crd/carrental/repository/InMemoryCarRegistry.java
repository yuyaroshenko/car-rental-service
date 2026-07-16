package org.crd.carrental.repository;

import org.crd.carrental.exception.CarAlreadyExistsException;
import org.crd.carrental.model.Car;
import org.crd.carrental.model.CarType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory {@link CarRegistry}. Thread-safe on its own: {@code carTable}
 * and {@code indexByType} are backed by {@link ConcurrentHashMap}, the
 * index's list values are {@link CopyOnWriteArrayList}, and reads return a
 * defensive copy. Adding a car updates both maps in two separate atomic
 * steps, not one transaction, so there is a brief window where a new car
 * is visible through one lookup method but not the other.
 */
public class InMemoryCarRegistry implements CarRegistry {

    private final Map<String, Car> carTable = new ConcurrentHashMap<>();
    private final Map<CarType, List<Car>> indexByType = new ConcurrentHashMap<>();

    @Override
    public String add(Car car) {
        Objects.requireNonNull(car, "car cannot be null");

        if (carTable.putIfAbsent(car.carId(), car) != null) {
            throw new CarAlreadyExistsException(car);
        }
        indexByType.computeIfAbsent(car.type(), k -> new CopyOnWriteArrayList<>()).add(car);
        return car.carId();
    }

    @Override
    public Optional<Car> findById(String carId) {
        Objects.requireNonNull(carId, "carId cannot be null");
        return Optional.ofNullable(carTable.get(carId));
    }

    @Override
    public List<Car> findByCarType(CarType type) {
        Objects.requireNonNull(type, "type cannot be null");
        return List.copyOf(indexByType.getOrDefault(type, List.of()));
    }
}
