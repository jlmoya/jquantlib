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
 Copyright (C) 2007 Cristina Duminuco

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */

package org.jquantlib.cashflow;

/**
 * Replication parameters for {@link DigitalCoupon}.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/cashflows/replication.hpp}
 * (the {@code DigitalReplication} class).
 *
 * @author Cristina Duminuco (C++ original)
 */
public class DigitalReplication {

    private final Replication.Type replicationType_;
    private final double gap_;


    //
    // public constructors
    //

    /** Default: Central replication, gap = 1e-4. */
    public DigitalReplication() {
        this(Replication.Type.Central, 1.0e-4);
    }

    public DigitalReplication(final Replication.Type t) {
        this(t, 1.0e-4);
    }

    public DigitalReplication(final Replication.Type t, final double gap) {
        this.replicationType_ = t;
        this.gap_ = gap;
    }


    //
    // public inspectors
    //

    public Replication.Type replicationType() {
        return replicationType_;
    }

    public double gap() {
        return gap_;
    }
}
