package org.crd.carrental.service;

import org.crd.carrental.exception.CarAlreadyExistsException;
import org.crd.carrental.exception.NoAvailableCarException;
import org.crd.carrental.model.CarType;
import org.crd.carrental.model.Reservation;
import org.crd.carrental.repository.FleetRepositories;
import org.crd.carrental.repository.InMemoryCarRegistry;
import org.crd.carrental.repository.InMemoryReservationRegistry;
import org.crd.carrental.service.strategy.CarSelectionStrategy;
import org.crd.carrental.service.strategy.FirstAvailableCarSelectionStrategy;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises CarRentalSystem under real thread contention. All worker threads are
 * released together through a ready/start/done latch rendezvous so the race window
 * is as wide as possible, and every await() is bounded so a hung run (deadlock)
 * fails the test instead of blocking the build.
 */
class CarRentalSystemConcurrencyTest {

    @Test
    void concurrentReservationsForSameTypeNeverDoubleBookACar() throws InterruptedException {
        CarRentalSystem rentalSystem = newRentalSystem();
        int carCount = 5;
        for (int i = 0; i < carCount; i++) {
            rentalSystem.addCar("SEDAN-" + i, CarType.SEDAN);
        }

        int threadCount = 20; // more contenders than cars, to force contention on every car
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 10, 0);
        List<Reservation> successes = new CopyOnWriteArrayList<>();
        AtomicInteger noAvailableCarFailures = new AtomicInteger();

        runConcurrently(threadCount, index -> {
            try {
                successes.add(rentalSystem.reserveCar(CarType.SEDAN, start, 2));
            } catch (NoAvailableCarException e) {
                noAvailableCarFailures.incrementAndGet();
            }
        });

        assertEquals(carCount, successes.size());
        assertEquals(threadCount - carCount, noAvailableCarFailures.get());

        Set<String> reservedCarIds = successes.stream().map(Reservation::carId).collect(Collectors.toSet());
        assertEquals(carCount, reservedCarIds.size(), "Two threads reserved the same car for an overlapping period");
    }

    @Test
    void concurrentAddCarWithSameIdOnlyOneWins() throws InterruptedException {
        CarRentalSystem rentalSystem = newRentalSystem();
        int threadCount = 20;
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger duplicateFailures = new AtomicInteger();

        runConcurrently(threadCount, index -> {
            try {
                rentalSystem.addCar("SEDAN-1", CarType.SEDAN);
                successes.incrementAndGet();
            } catch (CarAlreadyExistsException e) {
                duplicateFailures.incrementAndGet();
            }
        });

        assertEquals(1, successes.get());
        assertEquals(threadCount - 1, duplicateFailures.get());
    }

    @Test
    void concurrentAddCarWithDistinctIdsAreAllVisibleAfterwards() throws InterruptedException {
        CarRentalSystem rentalSystem = newRentalSystem();
        int threadCount = 30;

        runConcurrently(threadCount, index -> rentalSystem.addCar("VAN-" + index, CarType.VAN));

        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 10, 0);
        Set<String> reservedCarIds = new HashSet<>();
        for (int i = 0; i < threadCount; i++) {
            reservedCarIds.add(rentalSystem.reserveCar(CarType.VAN, start, 1).carId());
        }

        assertEquals(threadCount, reservedCarIds.size());
    }

    @Test
    void differentCarTypesDoNotBlockEachOther() throws Exception {
        CountDownLatch sedanEnteredSelection = new CountDownLatch(1);
        CountDownLatch releaseSedan = new CountDownLatch(1);
        FirstAvailableCarSelectionStrategy delegate = new FirstAvailableCarSelectionStrategy();

        // Blocks only SEDAN selection until released, so a concurrent SUV/VAN
        // reserveCar call can only complete quickly if its lock is independent
        // of SEDAN's lock.
        CarSelectionStrategy strategy = (type, candidates, isAvailable) -> {
            if (type == CarType.SEDAN) {
                sedanEnteredSelection.countDown();
                try {
                    releaseSedan.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return delegate.selectCar(type, candidates, isAvailable);
        };

        CarRentalSystem rentalSystem = new CarRentalSystem(
                new FleetRepositories(new InMemoryCarRegistry(), new InMemoryReservationRegistry()), strategy);
        rentalSystem.addCar("SEDAN-1", CarType.SEDAN);
        rentalSystem.addCar("SUV-1", CarType.SUV);
        rentalSystem.addCar("VAN-1", CarType.VAN);
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 10, 0);

        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<Reservation> sedanFuture = executor.submit(() -> rentalSystem.reserveCar(CarType.SEDAN, start, 1));
            assertTrue(sedanEnteredSelection.await(5, TimeUnit.SECONDS),
                    "Sedan reservation never reached car selection");

            // If SUV/VAN shared SEDAN's lock, these would block until releaseSedan
            // fires; the bounded get() below turns that into a clear failure
            // instead of an indefinite hang.
            Future<Reservation> suvFuture = executor.submit(() -> rentalSystem.reserveCar(CarType.SUV, start, 1));
            Future<Reservation> vanFuture = executor.submit(() -> rentalSystem.reserveCar(CarType.VAN, start, 1));

            assertEquals("SUV-1", suvFuture.get(5, TimeUnit.SECONDS).carId());
            assertEquals("VAN-1", vanFuture.get(5, TimeUnit.SECONDS).carId());

            releaseSedan.countDown();
            assertEquals("SEDAN-1", sedanFuture.get(5, TimeUnit.SECONDS).carId());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Executor failed to terminate");
        }
    }

    @Test
    void concurrentAddCarAndReserveCarForSameTypeNeverDoubleBook() throws InterruptedException {
        CarRentalSystem rentalSystem = newRentalSystem();
        rentalSystem.addCar("SEDAN-0", CarType.SEDAN);

        int addThreads = 15;
        int reserveThreads = 15;
        int totalThreads = addThreads + reserveThreads;
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 10, 0);

        List<Reservation> successes = new CopyOnWriteArrayList<>();
        AtomicInteger noAvailableCarFailures = new AtomicInteger();

        runConcurrently(totalThreads, index -> {
            if (index < addThreads) {
                rentalSystem.addCar("SEDAN-EXTRA-" + index, CarType.SEDAN);
            } else {
                try {
                    successes.add(rentalSystem.reserveCar(CarType.SEDAN, start, 2));
                } catch (NoAvailableCarException e) {
                    noAvailableCarFailures.incrementAndGet();
                }
            }
        });

        // Every reserve call ends in exactly one of the two expected outcomes -
        // no hang, no unexpected exception.
        assertEquals(reserveThreads, successes.size() + noAvailableCarFailures.get());

        Set<String> reservedCarIds = successes.stream().map(Reservation::carId).collect(Collectors.toSet());
        assertEquals(successes.size(), reservedCarIds.size(),
                "Two threads reserved the same car for an overlapping period");
    }

    /**
     * A long-running stress/fuzz pass: many more threads than the other tests,
     * each running a randomized sequence of operations (mostly reservations,
     * some new-car registrations, some plain reads) across all three car
     * types, with small random timing jitter between operations to scramble
     * the interleaving beyond the initial rendezvous release. This doesn't
     * prove correctness under every possible interleaving, but it exercises
     * far more of them than the targeted tests above, which each isolate one
     * specific scenario.
     */
    @Test
    void stressTestWithManyThreadsAndRandomTimingNeverDoubleBooksACar() throws InterruptedException {
        CarRentalSystem rentalSystem = newRentalSystem();
        int carsPerType = 4;
        for (CarType type : CarType.values()) {
            for (int i = 0; i < carsPerType; i++) {
                rentalSystem.addCar(type + "-seed-" + i, type);
            }
        }

        int threadCount = 100;
        int operationsPerThread = 20;
        LocalDateTime baseStart = LocalDateTime.of(2026, 1, 1, 0, 0);
        AtomicInteger nextNewCarId = new AtomicInteger();

        List<Reservation> successes = new CopyOnWriteArrayList<>();
        AtomicInteger unexpectedFailures = new AtomicInteger();

        runConcurrently(threadCount, index -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            CarType[] types = CarType.values();
            for (int op = 0; op < operationsPerThread; op++) {
                // Occasional random jitter so operations land at scattered points
                // in time relative to each other, not just at the initial release.
                if (random.nextInt(5) == 0) {
                    LockSupport.parkNanos(random.nextLong(1, 500_000));
                }

                CarType type = types[random.nextInt(types.length)];
                int roll = random.nextInt(100);
                try {
                    if (roll < 20) {
                        rentalSystem.addCar(type + "-new-" + nextNewCarId.incrementAndGet(), type);
                    } else if (roll < 90) {
                        LocalDateTime start = baseStart.plusDays(random.nextInt(0, 6));
                        int days = random.nextInt(1, 4);
                        successes.add(rentalSystem.reserveCar(type, start, days));
                    } else {
                        rentalSystem.getReservationsForCar(type + "-seed-0");
                    }
                } catch (NoAvailableCarException e) {
                    // Expected under contention: the fleet ran out of free cars for that window.
                } catch (RuntimeException e) {
                    unexpectedFailures.incrementAndGet();
                }
            }
        });

        assertEquals(0, unexpectedFailures.get(),
                "A reservation attempt failed with an exception other than NoAvailableCarException");

        Map<String, List<Reservation>> reservationsByCarId =
                successes.stream().collect(Collectors.groupingBy(Reservation::carId));
        for (Map.Entry<String, List<Reservation>> entry : reservationsByCarId.entrySet()) {
            List<Reservation> carReservations = entry.getValue();
            for (int i = 0; i < carReservations.size(); i++) {
                for (int j = i + 1; j < carReservations.size(); j++) {
                    Reservation a = carReservations.get(i);
                    Reservation b = carReservations.get(j);
                    boolean overlap = a.startDateTime().isBefore(b.endDateTime())
                            && b.startDateTime().isBefore(a.endDateTime());
                    assertFalse(overlap, "Car " + entry.getKey() + " was double-booked: " + a + " overlaps " + b);
                }
            }
        }
    }

    private static CarRentalSystem newRentalSystem() {
        return new CarRentalSystem(new FleetRepositories(new InMemoryCarRegistry(), new InMemoryReservationRegistry()),
                new FirstAvailableCarSelectionStrategy());
    }

    /**
     * Releases threadCount worker threads at the same instant and waits for them all
     * to finish, with bounded timeouts on both rendezvous points so a deadlock in the
     * system under test surfaces as a failed assertion rather than a hung test run.
     */
    private void runConcurrently(int threadCount, IntConsumer task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            int index = i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    task.accept(index);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        try {
            assertTrue(ready.await(5, TimeUnit.SECONDS), "Worker threads never reached the starting line");
            start.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS),
                    "Worker threads did not finish within the timeout - possible deadlock");
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Executor failed to terminate");
        }
    }
}
