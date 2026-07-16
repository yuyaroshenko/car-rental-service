package org.crd.carrental.service.strategy;

import org.crd.carrental.model.Car;
import org.crd.carrental.model.CarType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Spreads reservations across the fleet instead of always favouring the
 * first-registered car: each call starts its scan from the position after the
 * previous call's starting point for that type, wrapping around and skipping
 * unavailable candidates. The per-type cursor is thread-safe on its own, so
 * this strategy is safe to use even outside of CarRentalSystem's lock.
 */
public class RoundRobinCarSelectionStrategy implements CarSelectionStrategy {

    private final Map<CarType, AtomicInteger> cursors = new ConcurrentHashMap<>();

    @Override
    public Optional<Car> selectCar(CarType type, List<Car> candidates, Predicate<Car> isAvailable) {
        int size = candidates.size();
        if (size == 0) {
            return Optional.empty();
        }

        int startIndex = cursors.computeIfAbsent(type, t -> new AtomicInteger(0))
                .getAndUpdate(i -> (i + 1) % size);

        for (int offset = 0; offset < size; offset++) {
            Car candidate = candidates.get((startIndex + offset) % size);
            if (isAvailable.test(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
