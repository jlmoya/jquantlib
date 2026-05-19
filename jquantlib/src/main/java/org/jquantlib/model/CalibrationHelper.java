/*
Copyright (C)
2008 Praneet Tiwari
2009 Ueli Hofstetter
2026 JQuantLib migration

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
package org.jquantlib.model;

/**
 * Abstract base class for calibration helpers. Mirrors C++ v1.42.1 ql/models/calibrationhelper.hpp lines 39-44.
 */
public interface CalibrationHelper {
    /** Returns the error resulting from the model valuation. */
    double calibrationError();
}
