package org.jquantlib.model.marketmodels;

/**
 * 
 * @author Ueli Hofstetter
 *
 */
// TODO: code review :: license, class comments, comments for access modifiers, comments for @Override
public abstract class  BrownianGeneratorFactory {

    public BrownianGeneratorFactory() {
    }

    public abstract BrownianGenerator create(int factors,int steps) ;

}
