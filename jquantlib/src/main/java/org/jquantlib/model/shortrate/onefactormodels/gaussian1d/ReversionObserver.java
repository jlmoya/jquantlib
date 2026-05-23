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
 * {@link Observer} that forwards Quote updates back to a model's reversion-parameter
 * update routine.
 *
 * <p>Java port of the nested C++ struct in
 * {@code ql/models/shortrate/onefactormodels/gsr.hpp} lines 181-185 (v1.42.1 @ 099987f0):
 * <pre>
 *   struct ReversionObserver : public Observer {
 *       explicit ReversionObserver(Gsr *p) : p_(p) {}
 *       void update() override { p_-&gt;updateReversion(); }
 *       Gsr *p_;
 *   };
 * </pre>
 *
 * <p>In C++ this is a named struct because Observer is a stateful interface that requires
 * a back-pointer to its parent model. In idiomatic Java the same wiring is often done with
 * an anonymous {@code Observer} or lambda — see the original wiring in {@code Gsr.java} for
 * the embedded variant. This class provides the explicit named form for symmetry with the
 * C++ structure (useful for reviewers diffing against v1.42.1) and any future model that
 * wants the reversion observer wired in a non-anonymous way.
 *
 * <p>The constructor accepts a {@link Runnable} callback rather than a hard reference to a
 * specific {@code Gsr} class so that any short-rate model (Gsr, GsrProcess, MarkovFunctional
 * extensions) can reuse this hook.
 *
 * @author Peter Caspers (C++ original)
 * @author JQuantLib contributors (Java port)
 */
public final class ReversionObserver implements Observer {

    private final Runnable updateReversionCallback_;

    /**
     * @param updateReversionCallback the model's {@code updateReversion()} method, e.g.
     *                                {@code gsr::updateReversion}.
     */
    public ReversionObserver(final Runnable updateReversionCallback) {
        if ( updateReversionCallback == null ) {
            throw new IllegalArgumentException("updateReversionCallback must not be null");
        }
        this.updateReversionCallback_ = updateReversionCallback;
    }

    @Override
    public void update() {
        updateReversionCallback_.run();
    }
}
