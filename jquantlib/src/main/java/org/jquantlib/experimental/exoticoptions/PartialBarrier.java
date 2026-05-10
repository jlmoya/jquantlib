/*
 Copyright (C) 2026 JQuantLib migration

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
 Copyright (C) 2014 Master IMAFA - Polytech'Nice Sophia - Université de Nice Sophia Antipolis
*/

package org.jquantlib.experimental.exoticoptions;

/**
 * Choice of time range for partial-time barrier options.
 * <p>
 * Mirrors {@code QuantLib::PartialBarrier::Range} from
 * {@code ql/instruments/partialtimebarrieroption.hpp} (v1.42.1).
 *
 * <ul>
 *   <li>{@link #Start}: monitor the barrier from the start of the option lifetime
 *       until the so-called cover event.</li>
 *   <li>{@link #EndB1}: monitor the barrier from the cover event to the exercise
 *       date; trigger a knock-out only if the barrier is hit or crossed from either
 *       side, regardless of the underlying value when monitoring starts.</li>
 *   <li>{@link #EndB2}: monitor the barrier from the cover event to the exercise
 *       date; immediately trigger a knock-out if the underlying value is on the
 *       wrong side of the barrier when monitoring starts.</li>
 * </ul>
 *
 * @author JQuantLib migration
 */
public enum PartialBarrier {
    Start,
    EndB1,
    EndB2
}
