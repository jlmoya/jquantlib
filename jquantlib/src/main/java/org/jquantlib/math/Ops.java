package org.jquantlib.math;

/**
 * This is an interim interface which will be replaced in future by an interface of same name from JSR-166y-extra
 *
 * @author Richard Gomes
 * @see <a href="http://gee.cs.oswego.edu/dl/jsr166/dist/extra166ydocs/extra166y/Ops.Op.html">Op</a>
 */
public interface Ops {

    /**
     * This is an interim method which will be replaced in future by a method of same name from JSR-166y-extra
     *
     * @author Richard Gomes
     * @see <a href="http://gee.cs.oswego.edu/dl/jsr166/dist/extra166ydocs/extra166y/Ops.Op.html">Op</a>
     */
    interface Op< A, R > {
        R op(A a);
    }

    /**
     * This is an interim method which will be replaced in future by a method of same name from JSR-166y-extra
     *
     * @author Richard Gomes
     * @see <a href="http://gee.cs.oswego.edu/dl/jsr166/dist/extra166ydocs/extra166y/Ops.DoubleOp.html">DoubleOp</a>
     */
    interface DoubleOp {

        double op(double x);
    }

    /**
     * This is an interim method which will be replaced in future by a method of same name from JSR-166y-extra
     *
     * @author Richard Gomes
     * @see <a
     * href="http://gee.cs.oswego.edu/dl/jsr166/dist/extra166ydocs/extra166y/Ops.BinaryDoubleOp.html">BinaryDoubleOp</a>
     */
    interface BinaryDoubleOp {
        double op(double x, double y);
    }

    /**
     * This is an interim method which will be replaced in future by a method of same name from JSR-166y-extra
     *
     * @author Richard Gomes
     * @see <a
     * href="http://gee.cs.oswego.edu/dl/jsr166/dist/extra166ydocs/extra166y/Ops.IntToDouble.html">IntToDouble</a>
     */
    interface IntToDouble {
        double op(int x);
    }

    /**
     * This is an interim method which will be replaced in future by a method of same name from JSR-166y-extra
     *
     * @author Richard Gomes
     * @see <a
     * href="http://gee.cs.oswego.edu/dl/jsr166/dist/extra166ydocs/extra166y/ObjectToDouble.html">ObjectToDouble</a>
     */
    interface ObjectToDouble< A > {
        double op(A a);
    }

    /**
     * This is an interim method which will be replaced in future by a method of same name from JSR-166y-extra
     *
     * @author Richard Gomes
     * @see <a
     * href="http://gee.cs.oswego.edu/dl/jsr166/dist/extra166ydocs/extra166y/Ops.DoublePredicate.html">DoublePredicate</a>
     */
    interface DoublePredicate {
        boolean op(double a);
    }

    /**
     * This is an interim method which will be replaced in future by a method of same name from JSR-166y-extra
     *
     * @author Richard Gomes
     * @see <a
     * href="http://gee.cs.oswego.edu/dl/jsr166/dist/extra166ydocs/extra166y/Ops.BinaryDoublePredicate.html">BinaryDoublePredicate</a>
     */
    interface BinaryDoublePredicate {
        boolean op(double a, double b);
    }

    /**
     * This is an interim method which will be replaced in future by a method of same name from JSR-166y-extra
     *
     * @author Zahid Hussain.
     * @see <a
     * href="http://gee.cs.oswego.edu/dl/jsr166/dist/extra166ydocs/extra166y/Ops.DoubleGenerator.html">DoubleGenerator</a>
     */
    interface DoubleGenerator {
        double op();
    }
}
