/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
*/

/*
 Copyright (C) 2004 Ferdinando Ametrano
*/

package org.jquantlib.math.randomnumbers;

import org.jquantlib.QL;
import org.jquantlib.methods.montecarlo.Sample;

import java.util.function.Supplier;

/**
 * Randomized (random-shift) low-discrepancy sequence generator.
 *
 * <p>Direct port of C++ v1.42.1
 * {@code ql/math/randomnumbers/randomizedlds.hpp}. Random-shifts a uniform low-discrepancy sequence of dimension
 * {@code N} by adding (modulo 1 per coordinate) a pseudo-random uniform deviate in {@code (0,1)^N}. Used to implement
 * Randomized Quasi Monte Carlo.
 *
 * <p>Java-specific adaptation: the C++ template ({@code class LDS, class PRS})
 * is replaced with two functional adapters &mdash; {@link Lds} for the underlying low-discrepancy generator (must
 * expose {@code nextSequence} and {@code dimension}) and a pseudo-random {@link RandomSequenceGeneratorIntf} for the
 * shift draws. A {@link Supplier} of {@code Lds} captures the "pristine" generator state so that
 * {@link #nextRandomizer()} can re-seed the LDS exactly as the C++ copy-construct does
 * ({@code ldsg_ = pristineldsg_}).
 *
 * <p>Static factories are provided for the two combinations exercised by
 * the QuantLib test-suite: {@link #ofSobol(int, long, long)} and {@link #ofLattice(int, double[], int, long)}.
 */
public class RandomizedLDS {

    private final Supplier< Lds > factory_;
    private final RandomSequenceGeneratorIntf prsg_;
    private final int dimension_;
    private Lds ldsg_;
    private final double[] x_;
    private final double[] randomizer_;
    private double weight_;
    private double randomizerWeight_;
    /**
     * Construct from a pre-built LDS factory and an explicit PRS. The factory must produce a fresh, deterministic
     * instance on each invocation so that {@link #nextRandomizer()} can restore the LDS to its starting state (matching
     * C++ {@code ldsg_ = pristineldsg_}).
     */
    public RandomizedLDS(final Supplier< Lds > ldsFactory, final RandomSequenceGeneratorIntf prsg) {
        this.factory_ = ldsFactory;
        this.ldsg_ = ldsFactory.get();
        this.prsg_ = prsg;
        this.dimension_ = ldsg_.dimension();
        QL.require(prsg_.dimension() == dimension_,
                "generator mismatch: " + dimension_ + "-dim low discrepancy and " + prsg_.dimension()
                        + "-dim pseudo random");
        this.x_ = new double[dimension_];
        this.randomizer_ = new double[dimension_];
        this.weight_ = 1.0;
        // Initial randomizer draw — matches C++ randomizer_ = prsg_.nextSequence();
        final Sample< double[] > r = prsg_.nextSequence();
        this.randomizerWeight_ = r.weight();
        System.arraycopy(r.value(), 0, randomizer_, 0, dimension_);
    }

    /**
     * Equivalent of the C++ ctor
     * {@code RandomizedLDS<SobolRsg, RandomSequenceGenerator<MT>>(Size, BigNatural, BigNatural)} — Sobol LDS with the
     * default direction-integer table and MT-seeded PRS.
     */
    public static RandomizedLDS ofSobol(final int dimensionality, final long ldsSeed, final long prsSeed) {
        final Supplier< Lds > factory = () -> sobolAdapter(dimensionality, ldsSeed);
        final RandomSequenceGenerator< MersenneTwisterUniformRng > prsg = new RandomSequenceGenerator< MersenneTwisterUniformRng >(
                MersenneTwisterUniformRng.class, dimensionality, prsSeed);
        return new RandomizedLDS(factory, prsg);
    }

    /**
     * Adapter that exposes {@link SobolRsg} through the {@link Lds} interface. Public so callers may build
     * {@link RandomizedLDS} instances from a specific Sobol direction-integer table by passing
     * {@code () -> RandomizedLDS.sobolAdapter(d, seed)} as the factory.
     */
    public static Lds sobolAdapter(final int dimensionality, final long seed) {
        final SobolRsg rsg = new SobolRsg(dimensionality, seed);
        return new Lds() {
            @Override
            public double[] nextSequence() {
                return rsg.nextSequence().value();
            }

            @Override
            public int dimension() {
                return rsg.dimension();
            }
        };
    }

    /**
     * Convenience for {@link LatticeRsg}: rebuilds a fresh LatticeRsg on each invocation using the supplied generator
     * vector (which is read-only).
     */
    public static RandomizedLDS ofLattice(final int dimensionality, final double[] z, final int n, final long prsSeed) {
        final Supplier< Lds > factory = () -> latticeAdapter(dimensionality, z, n);
        final RandomSequenceGenerator< MersenneTwisterUniformRng > prsg = new RandomSequenceGenerator< MersenneTwisterUniformRng >(
                MersenneTwisterUniformRng.class, dimensionality, prsSeed);
        return new RandomizedLDS(factory, prsg);
    }

    /**
     * Adapter that exposes {@link LatticeRsg} through the {@link Lds} interface.
     */
    public static Lds latticeAdapter(final int dimensionality, final double[] z, final int n) {
        final LatticeRsg rsg = new LatticeRsg(dimensionality, z, n);
        return new Lds() {
            @Override
            public double[] nextSequence() {
                return rsg.nextSequence().value;
            }

            @Override
            public int dimension() {
                return rsg.dimension();
            }
        };
    }

    public int dimension() {
        return dimension_;
    }

    /** Returns the next randomized sample value vector. */
    public double[] nextSequence() {
        final double[] sample = ldsg_.nextSequence();
        // Weight follows C++: x.weight = randomizer_.weight * sample.weight.
        // The Lds adapter does not expose per-call weight, so we assume 1.0
        // (matches Sobol, Halton, Lattice in QuantLib v1.42.1).
        weight_ = randomizerWeight_;
        for ( int i = 0; i < dimension_; i++ ) {
            double v = randomizer_[i] + sample[i];
            if ( v > 1.0 ) {
                v -= 1.0;
            }
            x_[i] = v;
        }
        return x_;
    }

    // ---------------------------------------------------------------
    // Static convenience factories
    // ---------------------------------------------------------------

    public double[] lastSequence() {
        return x_;
    }

    public double lastWeight() {
        return weight_;
    }

    /**
     * Update the randomizing vector and re-initialize the low-discrepancy generator. Mirrors C++
     * {@code nextRandomizer()}.
     */
    public void nextRandomizer() {
        final Sample< double[] > r = prsg_.nextSequence();
        randomizerWeight_ = r.weight();
        System.arraycopy(r.value(), 0, randomizer_, 0, dimension_);
        ldsg_ = factory_.get();
    }

    /**
     * Minimal adapter for a low-discrepancy sequence used as the underlying LDS of {@link RandomizedLDS}. The
     * implementation must mutate internal state on each {@link #nextSequence()} call; the supplier (passed to the
     * constructor) is used to obtain a fresh instance for {@link RandomizedLDS#nextRandomizer()}.
     */
    public interface Lds {
        double[] nextSequence();

        int dimension();
    }
}
