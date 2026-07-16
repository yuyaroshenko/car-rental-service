package org.crd.carrental.service;

import org.crd.carrental.model.CarType;
import org.crd.carrental.model.Reservation;
import org.crd.carrental.repository.FleetRepositories;
import org.crd.carrental.repository.InMemoryCarRegistry;
import org.crd.carrental.repository.InMemoryReservationRegistry;
import org.crd.carrental.service.strategy.FirstAvailableCarSelectionStrategy;
import org.crd.carrental.service.strategy.RoundRobinCarSelectionStrategy;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that CarRentalSystem actually delegates car selection to the
 * strategy it was configured with, using a scenario where the strategies'
 * outcomes visibly differ.
 */
class CarRentalSystemCarSelectionStrategyTest {

    @Test
    void constructorRejectsNullStrategy() {
        FleetRepositories fleetRepos = new FleetRepositories(new InMemoryCarRegistry(), new InMemoryReservationRegistry());

        assertThrows(NullPointerException.class, () -> new CarRentalSystem(fleetRepos, null));
    }

    @Test
    void firstAvailableStrategyAlwaysPrefersTheEarliestRegisteredFreeCar() {
        CarRentalSystem rentalSystem = new CarRentalSystem(
                new FleetRepositories(new InMemoryCarRegistry(), new InMemoryReservationRegistry()),
                new FirstAvailableCarSelectionStrategy());
        rentalSystem.addCar("SEDAN-1", CarType.SEDAN);
        rentalSystem.addCar("SEDAN-2", CarType.SEDAN);
        rentalSystem.addCar("SEDAN-3", CarType.SEDAN);

        List<String> reservedCarIds = reserveNonOverlappingSlots(rentalSystem, 6);

        assertEquals(List.of("SEDAN-1", "SEDAN-1", "SEDAN-1", "SEDAN-1", "SEDAN-1", "SEDAN-1"), reservedCarIds);
    }

    @Test
    void roundRobinStrategySpreadsReservationsAcrossTheFleet() {
        CarRentalSystem rentalSystem = new CarRentalSystem(
                new FleetRepositories(new InMemoryCarRegistry(), new InMemoryReservationRegistry()),
                new RoundRobinCarSelectionStrategy());
        rentalSystem.addCar("SEDAN-1", CarType.SEDAN);
        rentalSystem.addCar("SEDAN-2", CarType.SEDAN);
        rentalSystem.addCar("SEDAN-3", CarType.SEDAN);

        List<String> reservedCarIds = reserveNonOverlappingSlots(rentalSystem, 6);

        assertEquals(
                List.of("SEDAN-1", "SEDAN-2", "SEDAN-3", "SEDAN-1", "SEDAN-2", "SEDAN-3"),
                reservedCarIds);
    }

    private static List<String> reserveNonOverlappingSlots(CarRentalSystem rentalSystem, int count) {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 10, 0);
        List<String> reservedCarIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Reservation reservation = rentalSystem.reserveCar(CarType.SEDAN, start.plusDays(i * 2L), 1);
            reservedCarIds.add(reservation.carId());
        }
        return reservedCarIds;
    }
}
