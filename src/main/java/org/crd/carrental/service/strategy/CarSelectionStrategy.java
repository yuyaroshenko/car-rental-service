package org.crd.carrental.service.strategy;

import org.crd.carrental.model.Car;
import org.crd.carrental.model.CarType;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Decides which free car to reserve among the cars of a requested type.
 * {@link org.crd.carrental.service.CarRentalSystem} supplies the candidates
 * and an availability check; the strategy owns the fairness policy — which
 * car, and in what order to look. Implementations are expected to be
 * thread-safe on their own, not merely correct under whatever lock the
 * caller happens to hold.
 */
public interface CarSelectionStrategy {

    /**
     * @param type       the requested car type, provided so a stateful strategy can
     *                   keep independent state per type
     * @param candidates every car of {@code type} currently registered, in registry order
     * @param isAvailable tells whether a given candidate is free for the requested period
     * @return the chosen car, or empty if none of the candidates are available
     */
    Optional<Car> selectCar(CarType type, List<Car> candidates, Predicate<Car> isAvailable);
}
