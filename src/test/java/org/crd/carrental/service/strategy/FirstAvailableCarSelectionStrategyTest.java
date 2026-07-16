package org.crd.carrental.service.strategy;

import org.crd.carrental.model.Car;
import org.crd.carrental.model.CarType;
import org.crd.carrental.service.strategy.FirstAvailableCarSelectionStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirstAvailableCarSelectionStrategyTest {

    private final FirstAvailableCarSelectionStrategy strategy = new FirstAvailableCarSelectionStrategy();

    @Test
    void picksTheFirstAvailableCandidate() {
        Car car1 = new Car("C1", CarType.SEDAN);
        Car car2 = new Car("C2", CarType.SEDAN);

        Optional<Car> selected = strategy.selectCar(CarType.SEDAN, List.of(car1, car2), c -> true);

        assertEquals(car1, selected.orElseThrow());
    }

    @Test
    void skipsUnavailableCandidatesInOrder() {
        Car car1 = new Car("C1", CarType.SEDAN);
        Car car2 = new Car("C2", CarType.SEDAN);

        Optional<Car> selected = strategy.selectCar(CarType.SEDAN, List.of(car1, car2), c -> c.equals(car2));

        assertEquals(car2, selected.orElseThrow());
    }

    @Test
    void returnsEmptyWhenNoCandidateIsAvailable() {
        Car car1 = new Car("C1", CarType.SEDAN);

        assertTrue(strategy.selectCar(CarType.SEDAN, List.of(car1), c -> false).isEmpty());
    }

    @Test
    void returnsEmptyForEmptyCandidateList() {
        assertTrue(strategy.selectCar(CarType.SEDAN, List.of(), c -> true).isEmpty());
    }

    @Test
    void alwaysPrefersTheEarlierCarEvenAfterRepeatedCalls() {
        Car car1 = new Car("C1", CarType.SEDAN);
        Car car2 = new Car("C2", CarType.SEDAN);

        for (int i = 0; i < 3; i++) {
            Optional<Car> selected = strategy.selectCar(CarType.SEDAN, List.of(car1, car2), c -> true);
            assertEquals(car1, selected.orElseThrow());
        }
    }
}
