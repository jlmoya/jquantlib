/*
 Copyright (C) 2008 Srinivas Hasti

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
/*
 Copyright (C) 2005, 2006 Theo Boafo
 Copyright (C) 2006, 2007 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */

package org.jquantlib.instruments;

import org.jquantlib.math.matrixutilities.Array;

import java.util.ArrayList;
import java.util.List;

/**
 * Useful discretized discount bond asset.
 * <p>
 * Port of C++ v1.42.1 {@code ql/discretizedasset.hpp} {@code DiscretizedDiscountBond}. Minimal helper used by
 * {@code DiscretizedSwap} to roll back the discount factor to a given pay date: {@code reset(size)} fills values with
 * 1.0 and {@code mandatoryTimes()} is empty (the lattice itself is responsible for the discount-factor evolution).
 */
public class DiscretizedDiscountBond extends DiscretizedAsset {

    public DiscretizedDiscountBond() {
        super();
    }

    @Override
    public void reset(final int size) {
        values_ = new Array(size).fill(1.0);
    }

    @Override
    public List</*@Time*/ Double > mandatoryTimes() {
        return new ArrayList<>();
    }
}
