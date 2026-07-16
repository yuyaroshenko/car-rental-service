package org.crd.carrental.service;

import org.crd.carrental.exception.CarAlreadyExistsException;
import org.crd.carrental.exception.InvalidRequestException;
import org.crd.carrental.exception.NoAvailableCarException;
import org.crd.carrental.model.CarType;
import org.crd.carrental.model.Reservation;
import org.crd.carrental.repository.FleetRepositories;
import org.crd.carrental.repository.InMemoryCarRegistry;
import org.crd.carrental.repository.InMemoryReservationRegistry;
import org.crd.carrental.service.strategy.FirstAvailableCarSelectionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarRentalSystemTest {

    private CarRentalSystem rentalSystem;

    @BeforeEach
    void setUp() {
        rentalSystem = new CarRentalSystem(
                new FleetRepositories(new InMemoryCarRegistry(), new InMemoryReservationRegistry()),
                new FirstAvailableCarSelectionStrategy());
    }

    @Test
    void addCarRejectsBlankCarId() {
        assertThrows(InvalidRequestException.class, () -> rentalSystem.addCar(" ", CarType.SEDAN));
    }

    @Test
    void addCarRejectsNullCarId() {
        assertThrows(InvalidRequestException.class, () -> rentalSystem.addCar(null, CarType.SEDAN));
    }

    @Test
    void addCarRejectsNullType() {
        assertThrows(NullPointerException.class, () -> rentalSystem.addCar("SEDAN-1", null));
    }

    @Test
    void addCarRejectsDuplicateId() {
        rentalSystem.addCar("SEDAN-1", CarType.SEDAN);

        assertThrows(CarAlreadyExistsException.class, () -> rentalSystem.addCar("SEDAN-1", CarType.SUV));
    }

    @Test
    void reserveCarRejectsNonPositiveNumberOfDays() {
        rentalSystem.addCar("SEDAN-1", CarType.SEDAN);
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 10, 0);

        assertThrows(InvalidRequestException.class, () -> rentalSystem.reserveCar(CarType.SEDAN, start, 0));
    }

    @Test
    void reserveCarThrowsWhenNoCarsOfTypeExist() {
        assertThrows(NoAvailableCarException.class,
                () -> rentalSystem.reserveCar(CarType.SUV, LocalDateTime.of(2026, 1, 1, 10, 0), 2));
    }

    @Test
    void reserveCarReturnsReservationForRequestedTypeAndPeriod() {
        rentalSystem.addCar("SEDAN-1", CarType.SEDAN);
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 10, 0);

        Reservation reservation = rentalSystem.reserveCar(CarType.SEDAN, start, 3);

        assertEquals("SEDAN-1", reservation.carId());
        assertEquals(CarType.SEDAN, reservation.carType());
        assertEquals(start, reservation.startDateTime());
        assertEquals(start.plusDays(3), reservation.endDateTime());
    }

    @Test
    void reserveCarFallsBackToNextCarWhenFirstIsAlreadyBooked() {
        rentalSystem.addCar("SEDAN-1", CarType.SEDAN);
        rentalSystem.addCar("SEDAN-2", CarType.SEDAN);
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 10, 0);

        Reservation first = rentalSystem.reserveCar(CarType.SEDAN, start, 2);
        Reservation second = rentalSystem.reserveCar(CarType.SEDAN, start, 2);

        assertNotEquals(first.carId(), second.carId());
    }

    @Test
    void reserveCarThrowsWhenAllCarsOfTypeAreBookedForOverlappingPeriod() {
        rentalSystem.addCar("SEDAN-1", CarType.SEDAN);
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 10, 0);
        rentalSystem.reserveCar(CarType.SEDAN, start, 3);

        assertThrows(NoAvailableCarException.class,
                () -> rentalSystem.reserveCar(CarType.SEDAN, start.plusDays(1), 2));
    }

    @Test
    void reserveCarAllowsBackToBackBookingsOnTheSameCar() {
        rentalSystem.addCar("SEDAN-1", CarType.SEDAN);
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 10, 0);
        Reservation first = rentalSystem.reserveCar(CarType.SEDAN, start, 3);

        Reservation second = rentalSystem.reserveCar(CarType.SEDAN, first.endDateTime(), 2);

        assertEquals(first.carId(), second.carId());
        assertEquals(first.endDateTime(), second.startDateTime());
    }

    @Test
    void reserveCarAllowsAnEarlierBookingOnceALaterOneAlreadyExistsOnTheSameCar() {
        rentalSystem.addCar("SEDAN-1", CarType.SEDAN);
        LocalDateTime laterStart = LocalDateTime.of(2026, 1, 10, 10, 0);
        Reservation later = rentalSystem.reserveCar(CarType.SEDAN, laterStart, 2);

        LocalDateTime earlierStart = LocalDateTime.of(2026, 1, 1, 10, 0);
        Reservation earlier = rentalSystem.reserveCar(CarType.SEDAN, earlierStart, 5);

        assertEquals(later.carId(), earlier.carId());
        assertTrue(earlier.endDateTime().isBefore(later.startDateTime()));
    }

    @Test
    void getReservationsForCarReturnsAllReservationsForThatCar() {
        rentalSystem.addCar("SEDAN-1", CarType.SEDAN);
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 10, 0);
        Reservation first = rentalSystem.reserveCar(CarType.SEDAN, start, 2);
        Reservation second = rentalSystem.reserveCar(CarType.SEDAN, first.endDateTime(), 2);

        assertEquals(List.of(first, second), rentalSystem.getReservationsForCar("SEDAN-1"));
    }

    @Test
    void getReservationsForCarReturnsEmptyListForCarWithoutReservations() {
        rentalSystem.addCar("SEDAN-1", CarType.SEDAN);

        assertTrue(rentalSystem.getReservationsForCar("SEDAN-1").isEmpty());
    }
}
