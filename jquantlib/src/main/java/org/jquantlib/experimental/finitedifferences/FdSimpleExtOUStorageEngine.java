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
 Copyright (C) 2011 Klaus Spanderen
 Copyright (C) 2014 Ralph Schreyer
 */
package org.jquantlib.experimental.finitedifferences;

import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.experimental.finitedifferences.FdmExpExtOUInnerValueCalculator.ShapePoint;
import org.jquantlib.experimental.processes.ExtendedOrnsteinUhlenbeckProcess;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.termstructures.YieldTermStructure;

/**
 * Finite-differences engine for a simple gas-storage option driven by the
 * extended Ornstein–Uhlenbeck process.
 * <p>
 * Java port of v1.42.1
 * {@code ql/experimental/finitedifferences/fdsimpleextoustorageengine.{hpp,cpp}}.
 *
 * <p><strong>Phase 5e.5b-CFC-d-171 status:</strong> the engine constructor and
 * argument-handling are faithful Java ports. The {@link #calculate()} body,
 * however, requires three classes that are not yet ported to Java:</p>
 * <ul>
 *   <li>{@code VanillaStorageOption} (instrument & arguments class);</li>
 *   <li>{@code FdmSimpleStorageCondition} (Bermudan storage step condition);</li>
 *   <li>{@code FdmSimple2dExtOUSolver} (2D solver wrapper around
 *       {@link FdmExtendedOrnsteinUhlenbeckOp}).</li>
 * </ul>
 *
 * <p>The engine is typed against {@link OneAssetOption} arguments/results so
 * sibling classes can reference the type while we wait for
 * {@code VanillaStorageOption} to land. When all three dependencies are
 * ported, the {@code calculate()} body can be filled in following the C++
 * implementation step-by-step:</p>
 * <ol>
 *   <li>build the {@code (xMesher, storageMesher)} composite mesh;</li>
 *   <li>create {@code FdmStorageValue} + {@link FdmExpExtOUInnerValueCalculator};</li>
 *   <li>wrap a Bermudan {@code FdmSimpleStorageCondition} step condition;</li>
 *   <li>assemble a {@link org.jquantlib.methods.finitedifferences.solvers.FdmSolverDesc};</li>
 *   <li>delegate to {@code FdmSimple2dExtOUSolver(rTS_, solverDesc, schemeDesc_)}.</li>
 * </ol>
 *
 * @author Phase 5e.5b-CFC-d-171 port (skeleton)
 */
public class FdSimpleExtOUStorageEngine
        extends GenericEngine<OneAssetOption.Arguments, OneAssetOption.Results> {

    private final ExtendedOrnsteinUhlenbeckProcess process_;
    private final YieldTermStructure rTS_;
    private final int tGrid_;
    private final int xGrid_;
    private final Integer yGrid_;
    private final List<ShapePoint> shape_;
    private final FdmSchemeDesc schemeDesc_;

    /**
     * Convenience constructor — C++ defaults
     * ({@code tGrid=50, xGrid=100, yGrid=null (elevator mesh), no shape,
     *  Douglas scheme}).
     */
    public FdSimpleExtOUStorageEngine(final ExtendedOrnsteinUhlenbeckProcess process,
                                      final YieldTermStructure rTS) {
        this(process, rTS, 50, 100, null, null, FdmSchemeDesc.Douglas());
    }

    public FdSimpleExtOUStorageEngine(final ExtendedOrnsteinUhlenbeckProcess process,
                                      final YieldTermStructure rTS,
                                      final int tGrid,
                                      final int xGrid) {
        this(process, rTS, tGrid, xGrid, null, null, FdmSchemeDesc.Douglas());
    }

    public FdSimpleExtOUStorageEngine(final ExtendedOrnsteinUhlenbeckProcess process,
                                      final YieldTermStructure rTS,
                                      final int tGrid,
                                      final int xGrid,
                                      final Integer yGrid,
                                      final List<ShapePoint> shape,
                                      final FdmSchemeDesc schemeDesc) {
        super(new OneAssetOption.ArgumentsImpl(), new OneAssetOption.ResultsImpl());
        QL.require(process != null, "null ExtendedOrnsteinUhlenbeckProcess");
        QL.require(rTS != null, "null risk-free term structure");
        QL.require(schemeDesc != null, "null FDM scheme descriptor");
        this.process_ = process;
        this.rTS_ = rTS;
        this.tGrid_ = tGrid;
        this.xGrid_ = xGrid;
        this.yGrid_ = yGrid;
        this.shape_ = shape;
        this.schemeDesc_ = schemeDesc;
    }

    /** Returns the driving process. */
    public ExtendedOrnsteinUhlenbeckProcess process() {
        return process_;
    }

    /** Returns the risk-free term structure. */
    public YieldTermStructure rTS() {
        return rTS_;
    }

    /** Returns the number of time-grid points. */
    public int tGrid() {
        return tGrid_;
    }

    /** Returns the number of x-grid (log-spot) points. */
    public int xGrid() {
        return xGrid_;
    }

    /**
     * Returns the y-grid size (number of storage levels for the uniform
     * mesher, or {@code null} for the C++ "elevator" mesher built from the
     * capacity/change-rate of the storage option).
     */
    public Integer yGrid() {
        return yGrid_;
    }

    /** Returns the time-shape descriptor (may be {@code null}). */
    public List<ShapePoint> shape() {
        return shape_;
    }

    /** Returns the FDM scheme descriptor. */
    public FdmSchemeDesc schemeDesc() {
        return schemeDesc_;
    }

    /**
     * Storage engine calculation. Currently a stub — see class-level
     * Javadoc for the missing dependencies and the C++ algorithm outline.
     */
    @Override
    public void calculate() {
        throw new LibraryException(
                "FdSimpleExtOUStorageEngine.calculate(): the storage engine "
              + "pricing path requires VanillaStorageOption, "
              + "FdmSimpleStorageCondition, and FdmSimple2dExtOUSolver, none of "
              + "which are yet ported to Java (Phase 5e.5b-CFC-d-171 "
              + "carry-forward). The engine constructor and accessors are "
              + "fully functional so sibling code can reference the type.");
    }
}
