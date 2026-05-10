/*
 Copyright (C) 2007 Richard Gomes

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
 Copyright (C) 2002, 2003 Ferdinando Ametrano
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2003, 2004, 2005, 2006 StatPro Italia srl
*/

package org.jquantlib.methods.montecarlo;

import org.jquantlib.math.randomnumbers.RandomSequenceGeneratorIntf;
import org.jquantlib.processes.StochasticProcess1D;
import org.jquantlib.time.TimeGrid;

/**
 * Generates random paths using a sequence generator.
 *
 * <p>Generates random paths with drift({@code S},{@code t}) and variance(
 * {@code S},{@code t}) using a Gaussian sequence generator.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/methods/montecarlo/pathgenerator.hpp} (Phase 5h.5-MC-INFRA WI-3).
 * The Java version is type-erased over the GSG parameter (any
 * {@link RandomSequenceGeneratorIntf} works) and is specialised to a
 * single-factor {@link StochasticProcess1D}, mirroring the C++
 * dynamic_pointer_cast that is performed in the constructor.
 *
 * <p>Behavioural parity with C++:
 *   <ul>
 *     <li>{@code next()} draws the next sequence and applies the
 *         per-step drift+stdDev evolution.</li>
 *     <li>{@code antithetic()} reuses the last drawn sequence with
 *         negated normal increments.</li>
 *     <li>The sample returned by both methods <strong>aliases</strong>
 *         the same internal {@link Path} buffer (matches C++
 *         {@code mutable sample_type next_}). Callers must consume
 *         the value before the next draw or copy it explicitly.</li>
 *   </ul>
 *
 * <p>Brownian-bridge transformation is wired via {@link BrownianBridge}
 * when {@code brownianBridge=true}.
 *
 * @author JQuantLib
 */
public class PathGenerator</*<RNG extends RandomNumberGenerator,*/ GSG extends RandomSequenceGeneratorIntf> {

    //
    // private fields
    //

    private final boolean brownianBridge_;
    private final GSG generator_;
    private final /*@NonNegative*/ int dimension_;
    private final TimeGrid timeGrid_;
    private final StochasticProcess1D process_;
    private final Sample<Path> next_;
    private final double[] temp_;
    private final BrownianBridge bb_;


    //
    // public constructors
    //

    /**
     * Length-and-step constructor; mirrors C++
     * {@code PathGenerator(StochasticProcess&, Time length, Size timeSteps,
     * GSG, bool brownianBridge)}.
     */
    public PathGenerator(
            final StochasticProcess1D process,
            final /*@Time*/ double length,
            final /*@NonNegative*/ int timeSteps,
            final GSG generator,
            final boolean brownianBridge) {
        this.brownianBridge_ = brownianBridge;
        this.generator_ = generator;
        this.dimension_ = generator.dimension();
        this.timeGrid_ = new TimeGrid(length, timeSteps);
        this.process_ = process;
        this.next_ = new Sample<Path>(new Path(this.timeGrid_), 1.0);
        this.temp_ = new double[this.dimension_];
        this.bb_ = new BrownianBridge(this.timeGrid_);

        if (dimension_ != timeSteps) {
            throw new IllegalArgumentException(
                    "sequence generator dimensionality (" + dimension_
                            + ") != timeSteps (" + timeSteps + ")");
        }
    }

    /**
     * Time-grid constructor; mirrors C++
     * {@code PathGenerator(StochasticProcess&, TimeGrid, GSG, bool brownianBridge)}.
     */
    public PathGenerator(
            final StochasticProcess1D process,
            final TimeGrid timeGrid,
            final GSG generator,
            final boolean brownianBridge) {
        this.brownianBridge_ = brownianBridge;
        this.generator_ = generator;
        this.dimension_ = generator.dimension();
        this.timeGrid_ = timeGrid;
        this.process_ = process;
        this.next_ = new Sample<Path>(new Path(this.timeGrid_), 1.0);
        this.temp_ = new double[this.dimension_];
        this.bb_ = new BrownianBridge(this.timeGrid_);

        if (dimension_ != timeGrid_.size() - 1) {
            throw new IllegalArgumentException(
                    "sequence generator dimensionality (" + dimension_
                            + ") != timeSteps (" + (timeGrid_.size() - 1) + ")");
        }
    }


    //
    // inspectors
    //

    public int size() /* @ReadOnly */ {
        return dimension_;
    }

    public TimeGrid timeGrid() /* @ReadOnly */ {
        return timeGrid_;
    }


    //
    // path generation
    //

    /**
     * Draws the next path. The returned {@link Sample} aliases an
     * internal buffer that is overwritten by subsequent calls; copy if
     * you need to retain the values.
     */
    public final Sample<Path> next() /* @ReadOnly */ {
        return next(false);
    }

    /**
     * Draws the antithetic of the last sequence. Must be called after
     * {@link #next()} (which drives the underlying generator forward).
     */
    public final Sample<Path> antithetic() /* @ReadOnly */ {
        return next(true);
    }

    private Sample<Path> next(final boolean antithetic) {
        final Sample<double[]> sequence = antithetic
                ? generator_.lastSequence()
                : generator_.nextSequence();

        final double[] sv = sequence.value();
        if (brownianBridge_) {
            bb_.transform(sv, temp_);
        } else {
            // Mirrors std::copy(sequence.begin(), end(), temp.begin()).
            System.arraycopy(sv, 0, temp_, 0, dimension_);
        }

        next_.setWeight(sequence.weight());

        final Path path = next_.value();
        path.setFront(process_.x0());

        for (int i = 1; i < path.length(); i++) {
            final /*@Time*/ double t = timeGrid_.get(i - 1);
            final /*@Time*/ double dt = timeGrid_.dt(i - 1);
            final double dw = antithetic ? -temp_[i - 1] : temp_[i - 1];
            path.set(i, process_.evolve(t, path.get(i - 1), dt, dw));
        }

        return next_;
    }
}
