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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.methods.montecarlo;

import java.util.List;

import org.jquantlib.math.Ops;

/**
 * Base contract for early-exercise single-path pricers used by the
 * Longstaff-Schwartz Monte Carlo regression.
 *
 * <p>Phase 5h.5-MC port of {@code QuantLib::EarlyExercisePathPricer}
 * (v1.42.1 ql/methods/montecarlo/earlyexercisepathpricer.hpp). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The C++ template is parametrised by {@code PathType} (Path or MultiPath)
 * with traits-driven {@code StateType} (Real or Array). In Java we
 * specialise to {@code <PathType, StateType>}: callers using single-path
 * pricers instantiate {@code EarlyExercisePathPricer<Path, Double>}, and
 * multi-path pricers instantiate
 * {@code EarlyExercisePathPricer<MultiPath, Array>}.
 *
 * <p>{@link #operator(Object, int)} returns the path's payoff at exercise
 * step {@code t}; {@link #state(Object, int)} returns the regression-state
 * at that step (used to evaluate the basis system); {@link #basisSystem()}
 * returns the regression-basis functions of {@code StateType → Double}.
 *
 * @param <PathType>  the concrete path type (Path or MultiPath)
 * @param <StateType> the regression-state type (Double for Path, Array for MultiPath)
 */
public interface EarlyExercisePathPricer<PathType, StateType> {

    /** Path payoff at step {@code t}. Mirrors C++ {@code operator()(path, t)}. */
    double operator(PathType path, int t);

    /** Regression-state at step {@code t}. Mirrors C++ {@code state(path, t)}. */
    StateType state(PathType path, int t);

    /** Basis system used to regress the continuation value. */
    List<? extends Ops.Op<StateType, Double>> basisSystem();
}
