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
 Copyright (C) 2014 Peter Caspers
 Copyright (C) 2026 Aaditya Panikath

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.volatilities;

import org.jquantlib.experimental.volatility.ZabrSmileSection;

/**
 * Sealed marker hierarchy for the four ZABR evaluation tags exposed by
 * QuantLib v1.42.1 {@code ql/termstructures/volatility/zabrsmilesection.hpp}
 * lines 41-45:
 *
 * <pre>
 *   struct ZabrShortMaturityLognormal {};
 *   struct ZabrShortMaturityNormal {};
 *   struct ZabrLocalVolatility {};
 *   struct ZabrFullFd {};
 * </pre>
 *
 * <p>In C++ these are empty {@code struct}s used as <i>template parameters</i>
 * to pick the appropriate specialisation of {@code ZabrSmileSection<Evaluation>}
 * / {@code ZabrInterpolatedSmileSection<Evaluation>}. The Java port collapses
 * the four template specialisations into a single
 * {@link ZabrSmileSection.Evaluation enum} with runtime dispatch (see the
 * {@code switch (evaluation_)} blocks in {@link ZabrSmileSection}).
 *
 * <p>To preserve a 1:1 type-level mirror of the C++ surface for callers and
 * automated audits, this file defines a JDK 25 <b>sealed</b> hierarchy with
 * one {@code record} per tag. Each tag exposes its corresponding
 * {@link ZabrSmileSection.Evaluation evaluation enum constant} via
 * {@link ZabrEvaluationTag#evaluation()}.
 *
 * <p>The four nested record subtypes — {@link ZabrShortMaturityLognormal},
 * {@link ZabrShortMaturityNormal}, {@link ZabrLocalVolatility},
 * {@link ZabrFullFd} — are intentionally minimal (singleton-style records
 * with no state) since the C++ structs are stateless tag types.
 *
 * <p>L2-C Phase 2 forward closure — audit IDs
 * {@code ZabrShortMaturityLognormal}, {@code ZabrShortMaturityNormal},
 * {@code ZabrLocalVolatility}, {@code ZabrFullFd}.
 */
public sealed interface ZabrEvaluationTag
        permits ZabrEvaluationTag.ZabrShortMaturityLognormal,
                ZabrEvaluationTag.ZabrShortMaturityNormal,
                ZabrEvaluationTag.ZabrLocalVolatility,
                ZabrEvaluationTag.ZabrFullFd {

    /** Singleton instance — mirrors C++ {@code ZabrShortMaturityLognormal}. */
    ZabrShortMaturityLognormal SHORT_MATURITY_LOGNORMAL = new ZabrShortMaturityLognormal();
    /** Singleton instance — mirrors C++ {@code ZabrShortMaturityNormal}. */
    ZabrShortMaturityNormal SHORT_MATURITY_NORMAL = new ZabrShortMaturityNormal();
    /** Singleton instance — mirrors C++ {@code ZabrLocalVolatility}. */
    ZabrLocalVolatility LOCAL_VOLATILITY = new ZabrLocalVolatility();
    /** Singleton instance — mirrors C++ {@code ZabrFullFd}. */
    ZabrFullFd FULL_FD = new ZabrFullFd();

    /** Corresponding evaluation enum constant for dispatch. */
    ZabrSmileSection.Evaluation evaluation();

    /** Type-level mirror of C++ {@code struct ZabrShortMaturityLognormal}. */
    record ZabrShortMaturityLognormal() implements ZabrEvaluationTag {
        @Override
        public ZabrSmileSection.Evaluation evaluation() {
            return ZabrSmileSection.Evaluation.ShortMaturityLognormal;
        }
    }

    /** Type-level mirror of C++ {@code struct ZabrShortMaturityNormal}. */
    record ZabrShortMaturityNormal() implements ZabrEvaluationTag {
        @Override
        public ZabrSmileSection.Evaluation evaluation() {
            return ZabrSmileSection.Evaluation.ShortMaturityNormal;
        }
    }

    /** Type-level mirror of C++ {@code struct ZabrLocalVolatility}. */
    record ZabrLocalVolatility() implements ZabrEvaluationTag {
        @Override
        public ZabrSmileSection.Evaluation evaluation() {
            return ZabrSmileSection.Evaluation.LocalVolatility;
        }
    }

    /** Type-level mirror of C++ {@code struct ZabrFullFd}. */
    record ZabrFullFd() implements ZabrEvaluationTag {
        @Override
        public ZabrSmileSection.Evaluation evaluation() {
            return ZabrSmileSection.Evaluation.FullFd;
        }
    }
}
