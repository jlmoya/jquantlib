package org.jquantlib.math.optimization;

import org.jquantlib.math.matrixutilities.Array;

public interface ParametersTransformation {
    Array direct(Array x);

    Array inverse(Array x);
}
