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
 Copyright (C) 2006 StatPro Italia srl
*/

package org.jquantlib.instruments;

import java.util.LinkedList;
import java.util.List;

import org.jquantlib.QL;

/**
 * Composite instrument — an aggregate of other instruments whose NPV is the sum of the components' NPVs, each
 * possibly multiplied by a given factor.
 *
 * <p>Faithful port of {@code ql/instruments/compositeinstrument.{hpp,cpp}} v1.42.1
 * (commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>Components register as observers of this composite, so notifications propagate. Each added component has
 * {@link Instrument#alwaysForwardNotifications()} enabled so that — should the composite be expired at the moment
 * a downstream consumer asks for its NPV — later evaluation-date changes still trigger a recalculation (mirrors
 * the C++ rationale documented in compositeinstrument.cpp).
 *
 * <p><b>Warning:</b> Methods that drive the calculation directly (such as {@link #recalculate()}, {@link #freeze()}
 * and similar) might not work correctly on a composite.
 */
public class CompositeInstrument extends Instrument {

    private static final class Component {
        final Instrument instrument;
        final double multiplier;

        Component(final Instrument instrument, final double multiplier) {
            this.instrument = instrument;
            this.multiplier = multiplier;
        }
    }

    private final List<Component> components_ = new LinkedList<>();

    /** Adds an instrument to the composite with the given multiplier. */
    public void add(final Instrument instrument, final double multiplier) {
        QL.require(instrument != null, "null instrument provided");
        components_.add(new Component(instrument, multiplier));
        instrument.addObserver(this);
        update();
        instrument.alwaysForwardNotifications();
    }

    /** Convenience overload — multiplier defaults to 1.0. */
    public void add(final Instrument instrument) {
        add(instrument, 1.0);
    }

    /** Shorts an instrument from the composite (multiplier negated). */
    public void subtract(final Instrument instrument, final double multiplier) {
        add(instrument, -multiplier);
    }

    /** Convenience overload — multiplier defaults to 1.0 (shorts at full notional). */
    public void subtract(final Instrument instrument) {
        add(instrument, -1.0);
    }

    @Override
    public boolean isExpired() {
        for (final Component component : components_) {
            if (!component.instrument.isExpired()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void performCalculations() {
        NPV = 0.0;
        for (final Component component : components_) {
            NPV += component.multiplier * component.instrument.NPV();
        }
    }
}
