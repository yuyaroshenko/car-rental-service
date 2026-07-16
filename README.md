# Car Rental System

## Scope

### Original task

See [Technical_Assessment.pdf](./Technical_Assessment.pdf) for the original document.

> Technical Exercise
>
> Please design and implement a simulated Car Rental system using object-oriented principles.
> Requirements:
> - The system should allow reservation of a car of a given type at a desired date and time for a given number of days.
> - There are 3 types of cars (Sedan, SUV and Van).
> - The number of cars of each type is limited.
> - Use unit tests to prove the system satisfies the requirements.

This document lists the main decisions and the alternatives I considered
for each one.

## Assumptions

The requirements leave many details open. Each item below states what
I assumed and which alternative(s) I considered.

### Domain assumptions

- **"The number of cars of each type is limited" means "as many cars as were added
  with `addCar`."** It is not a separate capacity setting. If the type capacities need to be configured outside, I will consider the following alternative.  
  *Alternative considered*: Adding type capacities as a constructor parameter `CarRentalSystem(..., Map<CarType, Integer> capacityPerType)`.
  This would set a maximum capacity in advance and reject `addCar` calls
  once the limit is reached.


- **There is no separate "setup phase" that must finish first.** The service allows to add cars and make reservations at the same time, even from
  different threads.  
  *Alternative considered*: a two-step lifecycle, for example, adding all cars, 
  "garage" sealing, after which `addCar` is rejected.


- **"The system should allow reservation ... for a given number of days."**
  means simple calendar-day math: `start.plusDays(numberOfDays)`. 
  
  *Alternative considered*: accept an explicit end `LocalDateTime`, or a `Duration`, instead of a day count.


- **Two bookings on the same car can be back-to-back, with no gap between
  them.** If one booking ends at the exact moment the next one starts, this
  does not count as an overlap. 

  *Alternative considered*: a cleaning/turnaround/maintanance buffer that will be added to each reservation.


- **The caller supplies `carId`.** I assume the caller already has a way
  to keep these unique (for example, a VIN or a fleet tag). This is
  different from `reservationId`, which the system generates itself.

  
- **Any car of the requested type is interchangeable.** There is no model, trim level, mileage,
  or condition attached to a car — just the car Id and car type. This allows to indicate *which*
  free car of *what* type will be get picked. Adding additional car properties requires extension
  interfaces and increases the complexity of the solution.  

  *Alternative considered*: let the caller ask for a car with one specific car property or set of properties (e.g. by ID, model, color, etc.).


- **Reservations can only be added, never changed or removed.** There is
  no cancel, modify, or return operation due to this is not required explicitly.  

  *Alternative considered*: add a `cancelReservation(reservationId)` method.


- **There is no way to remove a car or mark it unavailable.** Once a car is
  added, it stays eligible for reservation forever. Nothing marks it
  `IN_REPAIR` or `OUT_OF_SERVICE`. This is not required explicitly.  

  *Alternative considered*: add a status field on `Car`, or a separate `removeCar`/`setCarStatus` operation.


- **There is no customer entity.** A `Reservation` records *what* was
  booked and *when* — not *who* booked it.  

  *Alternative considered*: add a `customerId` field to `Reservation`, plus a customer registry, plus extension
  interfaces.


- **There are no opening hours, blackout dates, or minimum lead time.** A reservation can start at any 
  date and time (24/7 availability).  

  *Alternative considered*: check `start` against a business calendar, plus a business calendar registry, plus extension
  interfaces.


- **There is no time zone handling.** I use `LocalDateTime`, not `ZonedDateTime` or `Instant`.



## Technical decisions

- **The repositories `*Registry` are interfaces.** `InMemory*` is just 
  one implementation of them that can be replaced by another (e.g. `ExternalDB*`).
  `CarRentalSystem` receives its repositories through the constructor in the
  `FleetRepositories` container, instead of creating them itself.


- **The system runs as a single instance, in memory only.** There is no
  support for multiple instances sharing state. There is no persistence.
  All data is lost when the process restarts.


- **The caller choose a `CarSelectionStrategy` once, when they build
  `CarRentalSystem`.** The caller cannot choose a different strategy per `reserveCar` call.
  There are two implementations of the `CarSelectionStrategy`:
  - `RoundRobinCarSelectionStrategy`
  - `FirstAvailableCarSelectionStrategy`
  
  New implementations can be added without changing `CarRentalSystem`.


- **Expected failures use unchecked exceptions**
  (`NoAvailableCarException`, `CarAlreadyExistsException`, `InvalidRequestException`).  
  *Alternative considered*: a sealed `ReservationResult` return type
  (`Success` / `NoCarAvailable`) that would force every caller to handle
  both cases at compile time.


- **Invalid input (blank `carId`, non-positive `numberOfDays`) throws a
  custom `InvalidRequestException`, not `IllegalArgumentException`.** A
  null `type`/`start` still throws a plain `NullPointerException` via
  `Objects.requireNonNull`.  
  *Alternative considered*: keep using the standard
  `IllegalArgumentException` for input validation.

## Architecture at a glance

```
CarRentalService(interface)          <- public API: addCar / reserveCar / getReservationsForCar
    |
CarRentalSystem  ── uses ──>     CarSelectionStrategy(interface)
    |                              |                          |
    |                   FirstAvailableCarSelectionStrategy   RoundRobinCarSelectionStrategy
    |
   FleetRepositories (registry container)    
    |                         |
CarRegistry(interface)   ReservationRegistry(interface)
    |                         |
InMemoryCarRegistry     InMemoryReservationRegistry
```


## Where the effort actually went: concurrency correctness

The basic requirements — reserve by type, date, and duration, with 3 types 
and a limited fleet — look easy. The interesting part is how the system works
in concurrent environments and stops double-booking when multiple threads use
it at the same time.

> **The rule that matters:** for one car, no two reservations may overlap
> in time. Checking and enforcing this rule takes several steps: scan the
> cars of a type, read each car's existing reservations, pick a free one,
> then write the new reservation. No single map operation can guarantee
> this rule on its own.

### Decision: one lock per `CarType`, not one lock for everything

`CarRentalSystem` keeps one `Lock` per `CarType` (`Map<CarType, Lock>`,
backed by an `EnumMap`, populated with every enum constant once at
construction time). From that point on the map is only ever
read, never structurally modified, so a plain `EnumMap` is safe to share
between threads without extra synchronization. *Alternative considered*:
a `ConcurrentHashMap` with lazy `computeIfAbsent`-style population — not
needed here since `CarType` is a small, fixed, compile-time-known set of
values with nothing to lazily create.
`addCar` and `reserveCar` hold the lock for their own `CarType` for the
whole operation. `getReservationsForCar` does not take any lock.

### Decision: the registries protect themselves too, not just the caller's lock

`InMemoryCarRegistry` and `InMemoryReservationRegistry` use
`ConcurrentHashMap` for their tables and indexes, `putIfAbsent` to detect
duplicate cars, `CopyOnWriteArrayList` for the list values inside each
index, and return a defensive `List.copyOf(...)` copy whenever something is
read. *Alternative considered*: trust `CarRentalSystem`'s lock completely,
and use plain `HashMap`/`ArrayList` inside the registries.

### Known gap: adding a car is not one single atomic step

`InMemoryCarRegistry.add()` updates `carTable` and `indexByType` as two
separate steps, not one. For a very short moment, a new car can be visible
through one lookup method but not the other. *Alternatives considered*: run
the index update inside `carTable.compute(...)`; use one lock that covers
both maps; merge the two indexes into a single structure.


## Known weaknesses

- `reserveCar`'s search for a free car checks every car of that type, one
  by one, while holding the lock.
- There is no persistence. All data is lost when the process restarts.
- `FleetRepositories`'s methods are public. Any code holding the same
  instance passed into `CarRentalSystem` could call the registries
  directly and skip the per-type lock.

## Testing

### Test suite

- `InMemoryCarRegistryTest`, `InMemoryReservationRegistryTest` — unit tests
  for the two registries (add/find, duplicate detection, validation).
- `CarRentalSystemTest` — unit tests for `CarRentalSystem`'s public API
  (`addCar`, `reserveCar`, `getReservationsForCar`), including the overlap
  rule and back-to-back bookings.
- `FirstAvailableCarSelectionStrategyTest`, `RoundRobinCarSelectionStrategyTest`
  — unit tests for each `CarSelectionStrategy` implementation.
- `CarRentalSystemCarSelectionStrategyTest` — confirms `CarRentalSystem`
  actually uses whichever strategy it was built with, comparing
  `FirstAvailable` and `RoundRobin` behavior side by side.
- `CarRentalSystemConcurrencyTest` — see below. Covers: no double-booking
  under contention on one type; only one winner when adding a duplicate
  `carId` concurrently; all cars are visible after concurrent `addCar`
  calls with distinct ids; different `CarType`s don't block each other;
  concurrent `addCar` and `reserveCar` on the *same* type never double-book;
  a randomized stress/fuzz pass across all three types at once.
- `CarRentalSystemMockitoExampleTest` — one illustrative example of testing
  `CarRentalSystem` with Mockito instead of the real registries. Not a
  replacement for the tests above; see below.

### Testing decisions

- **Tests run `CarRentalSystem` against the real `InMemory*` registries**,
  not mocks, for the rest of the suite. Alternative shown, not just
  considered: `CarRentalSystemMockitoExampleTest` mocks
  `CarRegistry`/`ReservationRegistry`/`CarSelectionStrategy` to verify
  `CarRentalSystem`'s own orchestration logic (which collaborator it calls,
  with what arguments) in isolation from any registry's actual behavior.
  Kept as a single example rather than the default for the whole suite.
- **Concurrency correctness is tested with a dedicated
  `CarRentalSystemConcurrencyTest`.** Worker threads are released together
  through a ready/start/done `CountDownLatch` rendezvous, so the race
  window is as wide as possible, instead of just starting threads one by
  one. Every wait has a timeout, so a hang (deadlock) fails the test
  instead of blocking the build. Alternative considered: rely on manual or
  exploratory concurrency testing without an automated latch-based test.
- **Lock independence between car types is tested with a blocking test
  double, not wall-clock timing.** A custom `CarSelectionStrategy` blocks
  only `SEDAN` selection until released; the test then asserts that
  concurrent `SUV`/`VAN` reservations complete within a bounded
  `Future.get(...)` while `SEDAN` is still blocked. Alternative considered:
  measure wall-clock latency and assert it's "fast" — rejected as a
  flaky-by-construction pattern; a bounded, deterministic wait is used
  instead.
- **Coverage is enforced by JaCoCo** (`mvn verify`): minimum 90% line
  coverage and 90% branch coverage, `Main.class` excluded. Alternative
  considered: no enforced coverage threshold.
- **A randomized stress/fuzz test runs 100 threads through a randomized mix
  of `addCar`/`reserveCar`/`getReservationsForCar` across all three car
  types**, with small random timing jitter between operations, then checks
  that no successful reservation ever overlaps another one for the same
  car. This is just one small example, and does not cover all aspects of 
  stress testing. A single test run can still miss a bug that only shows up
  under rare timing.
