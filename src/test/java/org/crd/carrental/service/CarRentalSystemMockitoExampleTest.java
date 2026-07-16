package org.crd.carrental.service;

import org.crd.carrental.exception.NoAvailableCarException;
import org.crd.carrental.model.Car;
import org.crd.carrental.model.CarType;
import org.crd.carrental.model.Reservation;
import org.crd.carrental.repository.CarRegistry;
import org.crd.carrental.repository.FleetRepositories;
import org.crd.carrental.repository.ReservationRegistry;
import org.crd.carrental.service.strategy.CarSelectionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A single illustrative example of testing {@link CarRentalSystem} with
 * Mockito instead of the real {@code InMemory*} registries — see
 * README.md for why the rest of the suite deliberately uses the
 * real registries instead. Mocking {@link CarRegistry}/{@link ReservationRegistry}/
 * {@link CarSelectionStrategy} isolates {@code CarRentalSystem}'s own
 * orchestration logic: these tests fail only if {@code CarRentalSystem}
 * calls its collaborators incorrectly, never because of how a registry
 * happens to be implemented internally.
 */
@ExtendWith(MockitoExtension.class)
class CarRentalSystemMockitoExampleTest {

    @Mock
    private CarRegistry carRegistry;

    @Mock
    private ReservationRegistry reservationRegistry;

    @Mock
    private CarSelectionStrategy carSelectionStrategy;

    private CarRentalSystem rentalSystem;

    @BeforeEach
    void setUp() {
        rentalSystem = new CarRentalSystem(
                new FleetRepositories(carRegistry, reservationRegistry), carSelectionStrategy);
    }

    @Test
    void addCarDelegatesToCarRegistry() {
        rentalSystem.addCar("SEDAN-1", CarType.SEDAN);

        verify(carRegistry).add(new Car("SEDAN-1", CarType.SEDAN));
    }

    @Test
    void reserveCarAsksTheStrategyToChooseAmongCarsOfTheRequestedType() {
        Car car1 = new Car("SEDAN-1", CarType.SEDAN);
        Car car2 = new Car("SEDAN-2", CarType.SEDAN);
        List<Car> candidates = List.of(car1, car2);
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime end = start.plusDays(2);
        Reservation expected = new Reservation(1L, "SEDAN-2", CarType.SEDAN, start, end);

        when(carRegistry.findByCarType(CarType.SEDAN)).thenReturn(candidates);
        when(carSelectionStrategy.selectCar(eq(CarType.SEDAN), eq(candidates), any())).thenReturn(Optional.of(car2));
        when(reservationRegistry.create(car2, start, end)).thenReturn(expected);

        Reservation actual = rentalSystem.reserveCar(CarType.SEDAN, start, 2);

        assertEquals(expected, actual);
        verify(reservationRegistry).create(car2, start, end);
    }

    @Test
    void reserveCarThrowsWhenStrategyFindsNoCar() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 10, 0);
        when(carRegistry.findByCarType(CarType.SEDAN)).thenReturn(List.of());
        when(carSelectionStrategy.selectCar(eq(CarType.SEDAN), eq(List.of()), any())).thenReturn(Optional.empty());

        assertThrows(NoAvailableCarException.class, () -> rentalSystem.reserveCar(CarType.SEDAN, start, 2));

        verifyNoInteractions(reservationRegistry);
    }

    @Test
    void getReservationsForCarDelegatesToReservationRegistry() {
        List<Reservation> expected = List.of(new Reservation(1L, "SEDAN-1", CarType.SEDAN,
                LocalDateTime.of(2026, 1, 1, 10, 0), LocalDateTime.of(2026, 1, 3, 10, 0)));
        when(reservationRegistry.findByCarId("SEDAN-1")).thenReturn(expected);

        assertEquals(expected, rentalSystem.getReservationsForCar("SEDAN-1"));
    }
}
