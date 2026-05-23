/*
 Copyright (C) 2026 JQuantLib contributors

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
 Copyright (C) 2013, 2015 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.model.shortrate.onefactormodels.gaussian1d;

import org.jquantlib.util.Observer;

/**
 * {@link Observer} that forwards Quote updates back to a model's volatility-parameter
 * update routine.
 *
 * <p>Java port of the nested C++ struct in
 * {@code ql/models/shortrate/onefactormodels/gsr.hpp} lines 176-180 (v1.42.1 @ 099987f0):
 * <pre>
 *   struct VolatilityObserver : public Observer {
 *       explicit VolatilityObserver(Gsr *p) : p_(p) {}
 *       void update() override { p_-&gt;updateVolatility(); }
 *       Gsr *p_;
 *   };
 * </pre>
 *
 * <p>Sibling of {@link ReversionObserver}. In C++ both are named structs because the
 * Observer interface needs a stateful back-pointer; this Java port abstracts the back-pointer
 * into a {@link Runnable} callback so any model holding a volatility parameter can wire it
 * in. The original Gsr.java implementation still uses inline anonymous Observers for
 * historical reasons; this named class is provided for parity with the C++ structure and
 * any future model that wants the volatility observer wired in a non-anonymous way.
 *
 * @author Peter Caspers (C++ original)
 * @author JQuantLib contributors (Java port)
 */
public final class VolatilityObserver implements Observer {

    private final Runnable updateVolatilityCallback_;

    /**
     * @param updateVolatilityCallback the model's {@code updateVolatility()} method, e.g.
     *                                 {@code gsr::updateVolatility}.
     */
    public VolatilityObserver(final Runnable updateVolatilityCallback) {
        if ( updateVolatilityCallback == null ) {
            throw new IllegalArgumentException("updateVolatilityCallback must not be null");
        }
        this.updateVolatilityCallback_ = updateVolatilityCallback;
    }

    @Override
    public void update() {
        updateVolatilityCallback_.run();
    }
}
