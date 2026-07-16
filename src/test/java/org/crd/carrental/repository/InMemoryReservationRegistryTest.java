package org.crd.carrental.repository;

import org.crd.carrental.model.Car;
import org.crd.carrental.model.CarType;
import org.crd.carrental.model.Reservation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryReservationRegistryTest {

    private final Car car = new Car("SEDAN-1", CarType.SEDAN);

    private InMemoryReservationRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemoryReservationRegistry();
    }

    @Test
    void createReturnsReservationMatchingTheRequestedPeriod() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime end = start.plusDays(2);

        Reservation reservation = registry.create(car, start, end);

        assertEquals(car.carId(), reservation.carId());
        assertEquals(car.type(), reservation.carType());
        assertEquals(start, reservation.startDateTime());
        assertEquals(end, reservation.endDateTime());
    }

    @Test
    void createAssignsDistinctIdsForEachReservation() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 10, 0);

        Reservation first = registry.create(car, start, start.plusDays(1));
        Reservation second = registry.create(car, start.plusDays(1), start.plusDays(2));

        assertNotEquals(first.reservationId(), second.reservationId());
    }

    @Test
    void createRejectsStartEqualToEnd() {
        LocalDateTime same = LocalDateTime.of(2026, 1, 1, 10, 0);

        assertThrows(IllegalArgumentException.class, () -> registry.create(car, same, same));
    }

    @Test
    void createRejectsStartAfterEnd() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 2, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 1, 10, 0);

        assertThrows(IllegalArgumentException.class, () -> registry.create(car, start, end));
    }

    @Test
    void findByIdReturnsCreatedReservation() {
        Reservation reservation = registry.create(car,
                LocalDateTime.of(2026, 1, 1, 10, 0), LocalDateTime.of(2026, 1, 2, 10, 0));

        assertEquals(reservation, registry.findById(reservation.reservationId()).orElseThrow());
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        assertTrue(registry.findById(999L).isEmpty());
    }

    @Test
    void findByCarIdReturnsOnlyReservationsForThatCar() {
        Car otherCar = new Car("SEDAN-2", CarType.SEDAN);
        Reservation forCar = registry.create(car,
                LocalDateTime.of(2026, 1, 1, 10, 0), LocalDateTime.of(2026, 1, 2, 10, 0));
        registry.create(otherCar,
                LocalDateTime.of(2026, 1, 1, 10, 0), LocalDateTime.of(2026, 1, 2, 10, 0));

        assertEquals(List.of(forCar), registry.findByCarId(car.carId()));
    }

    @Test
    void findByCarIdReturnsEmptyListWhenNoneExist() {
        assertTrue(registry.findByCarId("UNKNOWN").isEmpty());
    }
}
