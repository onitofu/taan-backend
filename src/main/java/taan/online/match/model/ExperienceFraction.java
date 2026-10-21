package taan.online.match.model;

import java.math.BigInteger;

record ExperienceFraction(BigInteger numerator, BigInteger denominator) {
    ExperienceFraction {
        if (denominator.signum() <= 0) {
            throw new IllegalArgumentException("Invalid experience denominator");
        }
    }

    static ExperienceFraction of(long numerator, long denominator) {
        return new ExperienceFraction(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }

    ExperienceFraction plus(ExperienceFraction other) {
        return new ExperienceFraction(
            numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
            denominator.multiply(other.denominator)
        );
    }

    ExperienceFraction times(long numeratorFactor, long denominatorFactor) {
        return new ExperienceFraction(
            numerator.multiply(BigInteger.valueOf(numeratorFactor)),
            denominator.multiply(BigInteger.valueOf(denominatorFactor))
        );
    }

    long truncate() {
        return numerator.divide(denominator).longValueExact();
    }
}
