package org.jquantlib.model.marketmodels;

/**
 *
 * @author Ueli Hofstetter
 *
 */
public abstract class BrownianGeneratorFactory {

    public BrownianGeneratorFactory() {
    }

    public abstract BrownianGenerator create(int factors, int steps);

}
