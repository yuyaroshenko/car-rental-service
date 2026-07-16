package org.crd.carrental.repository;

import org.crd.carrental.exception.CarAlreadyExistsException;
import org.crd.carrental.model.Car;
import org.crd.carrental.model.CarType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryCarRegistryTest {

    private InMemoryCarRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemoryCarRegistry();
    }

    @Test
    void addReturnsTheCarId() {
        String id = registry.add(new Car("SEDAN-1", CarType.SEDAN));

        assertEquals("SEDAN-1", id);
    }

    @Test
    void addRejectsDuplicateCarId() {
        registry.add(new Car("SEDAN-1", CarType.SEDAN));

        assertThrows(CarAlreadyExistsException.class,
                () -> registry.add(new Car("SEDAN-1", CarType.SUV)));
    }

    @Test
    void findByIdReturnsRegisteredCar() {
        Car car = new Car("SEDAN-1", CarType.SEDAN);
        registry.add(car);

        assertEquals(car, registry.findById("SEDAN-1").orElseThrow());
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        assertTrue(registry.findById("UNKNOWN").isEmpty());
    }

    @Test
    void findByCarTypeReturnsOnlyMatchingCars() {
        Car sedan = new Car("SEDAN-1", CarType.SEDAN);
        Car suv = new Car("SUV-1", CarType.SUV);
        registry.add(sedan);
        registry.add(suv);

        assertEquals(List.of(sedan), registry.findByCarType(CarType.SEDAN));
    }

    @Test
    void findByCarTypeReturnsEmptyListWhenNoneRegistered() {
        assertTrue(registry.findByCarType(CarType.VAN).isEmpty());
    }
}
