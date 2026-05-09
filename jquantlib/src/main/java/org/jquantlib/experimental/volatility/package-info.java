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
*/

/**
 * Phase 4f experimental volatility package — SVI / ZABR / NoArbSABR smiles
 * and abstract Black volatility (smile) surfaces.
 *
 * <p>Mirrors C++ v1.42.1 {@code ql/experimental/volatility/}. SVI ports
 * faithfully; ZABR/NoArbSABR are scaffolded for Phase 4f.5 because their
 * full implementations require ODE solvers (RungeKutta), Dupire/ZABR FD
 * operators, and a 1.2M-entry pre-computed absorption-probability table
 * (see {@code noarbsabrabsprobs.cpp}, ~10K LOC of generated data).
 */
package org.jquantlib.experimental.volatility;
