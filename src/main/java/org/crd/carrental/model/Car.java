package org.crd.carrental.model;

import java.util.Objects;

/**
 * A single physical car in the fleet. Immutable: a car's id and type never
 * change after registration.
 *
 * @param carId caller-supplied unique identifier (for example, a VIN or fleet tag)
 * @param type  the car's type
 */
public record Car(
        String carId,
        CarType type)
{
    /** Rejects a {@code null} id or type; see the class docs for the field contract. */
    public Car {
        Objects.requireNonNull(carId, "carId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
    }
}
