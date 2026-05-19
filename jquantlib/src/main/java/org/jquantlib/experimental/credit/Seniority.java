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
 Copyright (C) 2009 StatPro Italia srl
 Copyright (C) 2009 Jose Aparicio
*/

package org.jquantlib.experimental.credit;

/**
 * Seniority of a bond, also serving as ISDA tier/seniority used for CDS conventional spreads.
 *
 * <p>Java port of QuantLib v1.42.1 enum {@code QuantLib::Seniority}
 * ({@code ql/experimental/credit/defaulttype.hpp}). The C++ enum re-uses underlying ordinal slots for synonyms (Markit
 * parlance); Java doesn't allow duplicate enum constants, so the Markit aliases are exposed as
 * {@code public static final} references to the canonical constants. Use {@link #ordinal()} to recover the C++ integer
 * code.
 *
 * <ul>
 *   <li>{@link #SecDom} = 0 (also {@link #SeniorSec})</li>
 *   <li>{@link #SnrFor} = 1 (also {@link #SeniorUnSec})</li>
 *   <li>{@link #SubLT2} = 2 (also {@link #SubLoweTier2})</li>
 *   <li>{@link #JrSubT2} = 3 (also {@link #SubUpperTier2})</li>
 *   <li>{@link #PrefT1} = 4 (also {@link #SubTier1})</li>
 *   <li>{@link #NoSeniority} = 5 (placeholder for default RR quote)</li>
 * </ul>
 *
 * <p>Phase 4m foundation.
 */
public enum Seniority {
    SecDom, SnrFor, SubLT2, JrSubT2, PrefT1,
    /** Unassigned value, allows for default RR quote. */
    NoSeniority;

    // Markit synonyms (C++ enum value aliases).
    public static final Seniority SeniorSec = SecDom;
    public static final Seniority SeniorUnSec = SnrFor;
    public static final Seniority SubTier1 = PrefT1;
    public static final Seniority SubUpperTier2 = JrSubT2;
    public static final Seniority SubLoweTier2 = SubLT2;
}
