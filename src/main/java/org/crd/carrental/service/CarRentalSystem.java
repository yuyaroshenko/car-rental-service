package org.crd.carrental.service;

import org.crd.carrental.exception.InvalidRequestException;
import org.crd.carrental.exception.NoAvailableCarException;
import org.crd.carrental.model.Car;
import org.crd.carrental.model.CarType;
import org.crd.carrental.model.Reservation;
import org.crd.carrental.repository.FleetRepositories;
import org.crd.carrental.service.strategy.CarSelectionStrategy;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A {@link CarRentalService} implementation. Guards each
 * {@link CarType} with its own lock, so {@code addCar}/{@code reserveCar}
 * calls for one type never block calls for a different type, while still
 * serializing everything needed to prevent double-booking within one type.
 *
 * {@code getReservationsForCar} takes no lock: it is a single read of an
 * already thread-safe registry.
 */
public class CarRentalSystem implements CarRentalService {

    private final FleetRepositories fleetRepos;
    private final CarSelectionStrategy carSelectionStrategy;

    /**
     * One lock per {@code CarType}, populated once with every enum constant
     * at construction time. From that point on the map is only ever read,
     * never structurally modified.
     */
    private final Map<CarType, Lock> locksByCarType = createLocksByCarType();

    /**
     * @param fleetRepos            the car and reservation repositories to use
     * @param carSelectionStrategy  the policy for choosing which free car to reserve
     */
    public CarRentalSystem(FleetRepositories fleetRepos, CarSelectionStrategy carSelectionStrategy) {
        this.fleetRepos = fleetRepos;
        this.carSelectionStrategy = Objects.requireNonNull(carSelectionStrategy, "carSelectionStrategy cannot be null");
    }

    @Override
    public void addCar(String carId, CarType type) {
        checkCarId(carId);
        Objects.requireNonNull(type, "type cannot be null");

        Car car = new Car(carId, type);
        Lock lock = locksByCarType.get(type);
        lock.lock();
        try {
            fleetRepos.carRepository().add(car);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Reservation reserveCar(CarType type, LocalDateTime start, int numberOfDays) {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(start, "start cannot be null");
        if (numberOfDays <= 0) {
            throw new InvalidRequestException("numberOfDays must be positive");
        }
        Lock lock = locksByCarType.get(type);
        lock.lock();
        try {
            List<Car> cars = fleetRepos.carRepository().findByCarType(type);
            LocalDateTime end = start.plusDays(numberOfDays);

            Car car = carSelectionStrategy
                    .selectCar(type, cars, candidate -> isAvailable(candidate, start, end))
                    .orElseThrow(() -> new NoAvailableCarException(type));
            return fleetRepos.reservationRepository().create(car, start, end);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Reservation> getReservationsForCar(String carId) {
        return fleetRepos.reservationRepository().findByCarId(carId);
    }


    private boolean isAvailable(Car car, LocalDateTime start, LocalDateTime end) {
        List<Reservation> existing = fleetRepos.reservationRepository().findByCarId(car.carId());
        return existing.stream().noneMatch(r -> r.startDateTime().isBefore(end) && start.isBefore(r.endDateTime()));
    }

    private void checkCarId(String carId) {
        if (carId == null || carId.isBlank()) {
            throw new InvalidRequestException("carId cannot be null or empty");
        }
    }

    private static Map<CarType, Lock> createLocksByCarType() {
        Map<CarType, Lock> locks = new EnumMap<>(CarType.class);
        for (CarType type : CarType.values()) {
            locks.put(type, new ReentrantLock());
        }
        return locks;
    }

}
