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
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2003, 2004, 2005, 2006 StatPro Italia srl
*/

package org.jquantlib.methods.montecarlo;

import org.jquantlib.time.TimeGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Correlated multiple-asset paths.
 *
 * <p>{@code MultiPath} contains the list of paths for each asset, i.e.,
 * {@code multipath.get(j)} is the path followed by the {@code j}-th asset.
 *
 * <p>Java port of {@code QuantLib v1.42.1 ql/methods/montecarlo/multipath.hpp}
 * (Phase 5h.5-MC-INFRA WI-2).
 *
 * @author JQuantLib
 */
public class MultiPath {

    //
    // private fields
    //

    private final List< Path > multiPath_;

    //
    // public constructors
    //

    /**
     * Constructs an empty multi-path. Useful as a default-constructed sample value before the first generator draw
     * populates it.
     */
    public MultiPath() {
        this.multiPath_ = new ArrayList<>();
    }

    /**
     * Constructs a multi-path with {@code nAsset} sub-paths, each sharing the same {@link TimeGrid}. Mirrors C++
     * {@code MultiPath(Size nAsset, const TimeGrid&)}.
     */
    public MultiPath(final int nAsset, final TimeGrid timeGrid) {
        if ( nAsset <= 0 ) {
            throw new IllegalArgumentException("number of assets must be positive (got " + nAsset + ")");
        }
        this.multiPath_ = new ArrayList<>(nAsset);
        for ( int j = 0; j < nAsset; j++ ) {
            this.multiPath_.add(new Path(timeGrid));
        }
    }

    /**
     * Constructs a multi-path from an explicit list of sub-paths. Mirrors C++ {@code MultiPath(std::vector<Path>)}.
     */
    public MultiPath(final List< Path > multiPath) {
        // Defensive copy so caller mutations cannot leak into us.
        this.multiPath_ = new ArrayList<>(multiPath);
    }

    //
    // inspectors
    //

    /**
     * Mirrors C++ {@code MultiPath::assetNumber()}.
     */
    public int assetNumber() /* @ReadOnly */ {
        return multiPath_.size();
    }

    /**
     * Mirrors C++ {@code MultiPath::pathSize()} — number of points per sub-path. Pre-condition: at least one asset.
     */
    public int pathSize() /* @ReadOnly */ {
        if ( multiPath_.isEmpty() ) {
            throw new IllegalStateException("MultiPath::pathSize() called on empty multi-path");
        }
        return multiPath_.get(0).length();
    }

    /**
     * Read/write access to the {@code j}-th sub-path. Mirrors C++ {@code MultiPath::operator[](Size)}.
     */
    public Path get(final int j) {
        return multiPath_.get(j);
    }

    /**
     * Bounds-checked variant of {@link #get(int)}. Mirrors C++ {@code MultiPath::at(Size)}.
     */
    public Path at(final int j) {
        if ( j < 0 || j >= multiPath_.size() ) {
            throw new IndexOutOfBoundsException(
                    "MultiPath.at: index " + j + " out of range [0," + multiPath_.size() + ")");
        }
        return multiPath_.get(j);
    }

    /**
     * Replaces the {@code j}-th sub-path. Mirrors C++ non-const {@code operator[](Size)} returning a reference to a
     * {@link Path} member.
     */
    public void set(final int j, final Path p) {
        multiPath_.set(j, p);
    }
}
