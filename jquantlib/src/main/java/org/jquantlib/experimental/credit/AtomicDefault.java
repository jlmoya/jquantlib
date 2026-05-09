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
 * Atomic (single contractual event) default events.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::AtomicDefault::Type}
 * (struct + nested enum) from
 * {@code ql/experimental/credit/defaulttype.hpp}. The C++ wrapping struct
 * exists to scope the enum; in Java we materialise the enclosing struct as
 * a class with a nested {@code enum Type} so call sites read identically:
 * {@code AtomicDefault.Type.Bankruptcy}.
 *
 * <p>Phase 4m foundation.
 */
public final class AtomicDefault {

    private AtomicDefault() {
        // utility
    }

    /**
     * Default types defined as enum to allow easy aggregation. ISDA-aligned.
     * The C++ enum aliases (ObligationAcceleration, ObligationDefault,
     * CrossDefault) re-use underlying ordinal slots; Java exposes them as
     * {@code public static final Type} references on the enclosing class.
     */
    public enum Type {
        /** Includes one of the restructuring cases. */
        Restructuring,
        Bankruptcy,
        FailureToPay,
        RepudiationMoratorium,
        Acceleration,
        Default,
        /** Non-ISDA, not in FpML. */
        Downgrade,
        /** Non-ISDA, not in FpML. */
        MergerEvent
    }

    // C++ enum aliases mapped to Java references.
    public static final Type ObligationAcceleration = Type.Acceleration;
    public static final Type ObligationDefault = Type.Default;
    public static final Type CrossDefault = Type.Default;
}
