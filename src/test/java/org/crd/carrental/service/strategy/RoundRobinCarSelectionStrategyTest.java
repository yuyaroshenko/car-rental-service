package org.crd.carrental.service.strategy;

import org.crd.carrental.model.Car;
import org.crd.carrental.model.CarType;
import org.crd.carrental.service.strategy.RoundRobinCarSelectionStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundRobinCarSelectionStrategyTest {

    private final RoundRobinCarSelectionStrategy strategy = new RoundRobinCarSelectionStrategy();

    @Test
    void rotatesThroughCandidatesOnConsecutiveCalls() {
        Car car1 = new Car("C1", CarType.SEDAN);
        Car car2 = new Car("C2", CarType.SEDAN);
        Car car3 = new Car("C3", CarType.SEDAN);
        List<Car> candidates = List.of(car1, car2, car3);

        assertEquals(car1, strategy.selectCar(CarType.SEDAN, candidates, c -> true).orElseThrow());
        assertEquals(car2, strategy.selectCar(CarType.SEDAN, candidates, c -> true).orElseThrow());
        assertEquals(car3, strategy.selectCar(CarType.SEDAN, candidates, c -> true).orElseThrow());
        assertEquals(car1, strategy.selectCar(CarType.SEDAN, candidates, c -> true).orElseThrow());
    }

    @Test
    void skipsUnavailableCandidatesWithinASingleCallButKeepsRotating() {
        Car car1 = new Car("C1", CarType.SEDAN);
        Car car2 = new Car("C2", CarType.SEDAN);
        Car car3 = new Car("C3", CarType.SEDAN);
        List<Car> candidates = List.of(car1, car2, car3);

        // car2 is never available, so the rotation must skip it whenever it's the start point.
        // Cursor start positions across the three calls are 0, 1, 2:
        //   call 1 starts at car1 (available)                       -> car1
        //   call 2 starts at car2 (busy), skips forward to car3     -> car3
        //   call 3 starts at car3 directly (available)              -> car3
        Optional<Car> first = strategy.selectCar(CarType.SEDAN, candidates, c -> !c.equals(car2));
        Optional<Car> second = strategy.selectCar(CarType.SEDAN, candidates, c -> !c.equals(car2));
        Optional<Car> third = strategy.selectCar(CarType.SEDAN, candidates, c -> !c.equals(car2));

        assertEquals(car1, first.orElseThrow());
        assertEquals(car3, second.orElseThrow());
        assertEquals(car3, third.orElseThrow());
    }

    @Test
    void maintainsIndependentCursorsPerCarType() {
        Car sedan1 = new Car("SEDAN-1", CarType.SEDAN);
        Car sedan2 = new Car("SEDAN-2", CarType.SEDAN);
        Car suv1 = new Car("SUV-1", CarType.SUV);
        Car suv2 = new Car("SUV-2", CarType.SUV);

        assertEquals(sedan1, strategy.selectCar(CarType.SEDAN, List.of(sedan1, sedan2), c -> true).orElseThrow());
        assertEquals(suv1, strategy.selectCar(CarType.SUV, List.of(suv1, suv2), c -> true).orElseThrow());
        assertEquals(sedan2, strategy.selectCar(CarType.SEDAN, List.of(sedan1, sedan2), c -> true).orElseThrow());
        assertEquals(suv2, strategy.selectCar(CarType.SUV, List.of(suv1, suv2), c -> true).orElseThrow());
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
}
