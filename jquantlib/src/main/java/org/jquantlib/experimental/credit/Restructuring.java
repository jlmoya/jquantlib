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
 * Restructuring type. ISDA-defined, scoped via an enclosing struct in C++.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::Restructuring::Type}
 * from {@code ql/experimental/credit/defaulttype.hpp}.  The Markit
 * shorthand aliases (XR / MR / MM / CR) re-use underlying enum slots in
 * C++ and are exposed as {@code public static final} references in Java.
 *
 * <p>Phase 4m foundation.
 */
public final class Restructuring {

    private Restructuring() {
        // utility
    }

    public enum Type {
        NoRestructuring,
        ModifiedRestructuring,
        ModifiedModifiedRestructuring,
        FullRestructuring,
        AnyRestructuring
    }

    // Markit notation aliases.
    public static final Type XR = Type.NoRestructuring;
    public static final Type MR = Type.ModifiedRestructuring;
    public static final Type MM = Type.ModifiedModifiedRestructuring;
    public static final Type CR = Type.FullRestructuring;
}
