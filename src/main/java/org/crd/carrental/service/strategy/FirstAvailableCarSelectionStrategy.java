package org.crd.carrental.service.strategy;

import org.crd.carrental.model.Car;
import org.crd.carrental.model.CarType;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Always prefers the earliest-registered free car of the requested type.
 */
public class FirstAvailableCarSelectionStrategy implements CarSelectionStrategy {

    @Override
    public Optional<Car> selectCar(CarType type, List<Car> candidates, Predicate<Car> isAvailable) {
        return candidates.stream().filter(isAvailable).findFirst();
    }
}
