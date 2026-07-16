package org.crd;

import org.crd.carrental.exception.NoAvailableCarException;
import org.crd.carrental.model.CarType;
import org.crd.carrental.model.Reservation;
import org.crd.carrental.repository.FleetRepositories;
import org.crd.carrental.repository.InMemoryCarRegistry;
import org.crd.carrental.repository.InMemoryReservationRegistry;
import org.crd.carrental.service.CarRentalService;
import org.crd.carrental.service.CarRentalSystem;
import org.crd.carrental.service.strategy.FirstAvailableCarSelectionStrategy;

import java.time.LocalDateTime;

public class Main {
    public static void main(String[] args) {
        FleetRepositories fleetRepos = new FleetRepositories(
                new InMemoryCarRegistry(),
                new InMemoryReservationRegistry());
        CarRentalService rentalService = new CarRentalSystem(fleetRepos, new FirstAvailableCarSelectionStrategy());

        // Set up a limited fleet: 2 sedans, 1 SUV, 1 van
        rentalService.addCar("SEDAN-1", CarType.SEDAN);
        rentalService.addCar("SEDAN-2", CarType.SEDAN);
        rentalService.addCar("SUV-1", CarType.SUV);
        rentalService.addCar("VAN-1", CarType.VAN);

        LocalDateTime tripStart = LocalDateTime.of(2026, 8, 1, 10, 0);

        // Reserve both sedans for an overlapping period
        Reservation firstSedanReservation = rentalService.reserveCar(CarType.SEDAN, tripStart, 3);
        System.out.println("Reserved: " + firstSedanReservation);

        Reservation secondSedanReservation = rentalService.reserveCar(CarType.SEDAN, tripStart, 3);
        System.out.println("Reserved: " + secondSedanReservation);

        // No sedans left for an overlapping window, so this reservation should fail
        try {
            rentalService.reserveCar(CarType.SEDAN, tripStart.plusDays(1), 2);
        } catch (NoAvailableCarException e) {
            System.out.println("Expected failure: " + e.getMessage());
        }

        // A later, non-overlapping request for the same car type succeeds again
        Reservation laterSedanReservation = rentalService.reserveCar(CarType.SEDAN, tripStart.plusDays(3), 2);
        System.out.println("Reserved: " + laterSedanReservation);

        // Reserve the SUV and the van too
        Reservation suvReservation = rentalService.reserveCar(CarType.SUV, tripStart, 5);
        System.out.println("Reserved: " + suvReservation);

        Reservation vanReservation = rentalService.reserveCar(CarType.VAN, tripStart, 1);
        System.out.println("Reserved: " + vanReservation);

        // Show the full reservation history for one specific car
        System.out.println("Reservations for SEDAN-1: " + rentalService.getReservationsForCar("SEDAN-1"));
    }
}