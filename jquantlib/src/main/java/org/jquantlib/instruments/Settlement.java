/*
Copyright (C) 2008 Praneet Tiwari

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
package org.jquantlib.instruments;

import org.jquantlib.QL;

/**
 * Settlement information for swaptions.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/instruments/swaption.hpp} (the
 * {@code Settlement} struct).
 *
 * @author Praneet Tiwari
 */
public class Settlement {

    /**
     * Settlement type: how the swaption settles when exercised.
     */
    public static enum Type {
        Physical,
        Cash
    }

    /**
     * Settlement method: details of how cash or physical settlement is performed.
     * Mirrors C++ {@code Settlement::Method}.
     */
    public static enum Method {
        PhysicalOTC,
        PhysicalCleared,
        CollateralizedCashPrice,
        ParYieldCurve
    }

    /**
     * Check consistency of settlement type and method.
     * Mirrors C++ {@code Settlement::checkTypeAndMethodConsistency}.
     *
     * @throws IllegalStateException if {@code type}/{@code method} are incompatible
     */
    public static void checkTypeAndMethodConsistency(
            final Settlement.Type type,
            final Settlement.Method method) {
        if (type == Type.Physical) {
            QL.require(method == Method.PhysicalOTC || method == Method.PhysicalCleared,
                    "invalid settlement method for physical settlement");
        }
        if (type == Type.Cash) {
            QL.require(method == Method.CollateralizedCashPrice || method == Method.ParYieldCurve,
                    "invalid settlement method for cash settlement");
        }
    }

    private Settlement() {
        // utility holder
    }
}
