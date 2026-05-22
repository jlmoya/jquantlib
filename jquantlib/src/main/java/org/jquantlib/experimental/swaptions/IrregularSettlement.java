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

package org.jquantlib.experimental.swaptions;

import org.jquantlib.lang.exceptions.LibraryException;

/**
 * Irregular swaption settlement type.
 *
 * <p>Phase 4i port of C++ QuantLib v1.42.1
 * {@code ql/experimental/swaptions/irregularswaption.hpp::IrregularSettlement}. Mirrors the C++ struct holding a
 * {@code Type} enum (Physical / Cash).
 */
public final class IrregularSettlement {

    private IrregularSettlement() {
    }

    public enum Type {
        Physical, Cash;

        @Override
        public String toString() {
            return switch (this) {
                case Physical -> "Delivery";
                case Cash -> "Cash";
                default -> throw new LibraryException("unknown IrregularSettlement.Type(" + ordinal() + ")");
            };
        }
    }
}
