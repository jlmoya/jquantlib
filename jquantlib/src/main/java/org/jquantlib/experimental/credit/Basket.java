/*
 Copyright (C) 2026 JQuantLib migration

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
 Copyright (C) 2008 Roland Lichters
 Copyright (C) 2009, 2014 Jose Aparicio
*/

package org.jquantlib.experimental.credit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.instruments.Claim;
import org.jquantlib.instruments.FaceValueClaim;
import org.jquantlib.time.Date;
import org.jquantlib.util.LazyObject;

/**
 * Credit basket: a collection of credit names with associated notionals,
 * a {@link Pool} (issuers), and tranche information (attachment /
 * detachment ratios).
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::Basket}
 * ({@code ql/experimental/credit/basket.{hpp,cpp}}).
 *
 * <p>The basket is the central data structure for CDO, NTD, and
 * CdsOption pricing. The {@link DefaultLossModel} attached via
 * {@link #setLossModel(DefaultLossModel)} provides loss-distribution
 * statistics; queries that delegate to the model fail loudly if no
 * model is attached.
 *
 * <p>Phase 4m.5 — central data structure for CDO/NTD.
 */
public class Basket extends LazyObject {

    private final List<Double> notionals;
    private final Pool pool;
    /** The claim is the same for all names. */
    private final Claim claim;

    private final double attachmentRatio;
    private final double detachmentRatio;
    private double basketNotional;
    /** Basket tranched inception attachment amount. */
    private double attachmentAmount;
    /** Basket tranched inception detachment amount. */
    private double detachmentAmount;
    /** Basket tranched notional amount. */
    private double trancheNotional;

    /* Caches. */
    private double evalDateSettledLoss;
    private double evalDateRemainingNot;
    private double evalDateAttachAmount;
    private double evalDateDetachAmount;
    private List<Integer> evalDateLiveList = new ArrayList<>();
    private List<Double> evalDateLiveNotionals = new ArrayList<>();
    private List<String> evalDateLiveNames = new ArrayList<>();
    private List<DefaultProbKey> evalDateLiveKeys = new ArrayList<>();

    /** Basket inception date. */
    private final Date refDate;

    private DefaultLossModel lossModel;

    public Basket(final Date refDate,
                  final List<String> names,
                  final List<Double> notionals,
                  final Pool pool,
                  final double attachmentRatio,
                  final double detachmentRatio,
                  final Claim claim) {
        this.notionals = new ArrayList<>(notionals);
        this.pool = pool;
        this.claim = claim;
        this.attachmentRatio = attachmentRatio;
        this.detachmentRatio = detachmentRatio;
        this.refDate = refDate;
        QL.require(!this.notionals.isEmpty(), "notionals empty");
        QL.require(attachmentRatio >= 0
                && attachmentRatio <= detachmentRatio
                && detachmentRatio <= 1,
                "invalid attachment/detachment ratio");
        QL.require(pool != null, "Empty pool pointer.");
        QL.require(this.notionals.size() == pool.size(),
                "unmatched data entry sizes in basket");

        // observability — settings + claim
        new Settings().evaluationDate().addObserver(this);
        if (claim != null) {
            claim.addObserver(this);
        }

        computeBasket();

        for (final double notional : this.notionals) {
            basketNotional += notional;
            attachmentAmount += notional * attachmentRatio;
            detachmentAmount += notional * detachmentRatio;
        }
        trancheNotional = detachmentAmount - attachmentAmount;
    }

    public Basket(final Date refDate,
                  final List<String> names,
                  final List<Double> notionals,
                  final Pool pool,
                  final double attachmentRatio,
                  final double detachmentRatio) {
        this(refDate, names, notionals, pool, attachmentRatio, detachmentRatio, new FaceValueClaim());
    }

    public Basket(final Date refDate,
                  final List<String> names,
                  final List<Double> notionals,
                  final Pool pool) {
        this(refDate, names, notionals, pool, 0.0, 1.0, new FaceValueClaim());
    }

    @Override
    public void update() {
        computeBasket();
        super.update();
    }

    /** Recompute eval-date cache values. Mirrors C++ {@code computeBasket()}. */
    private void computeBasket() {
        final Date today = new Settings().evaluationDate();
        // ordering matters; evalDateLiveKeys must be populated first
        evalDateLiveKeys = remainingDefaultKeys(today);
        evalDateSettledLoss = settledLoss(today);
        evalDateRemainingNot = remainingNotional(today);
        evalDateLiveNotionals = remainingNotionals(today);
        evalDateLiveNames = remainingNames(today);
        evalDateAttachAmount = remainingAttachmentAmount(today);
        evalDateDetachAmount = remainingDetachmentAmount(today);
        evalDateLiveList = liveList(today);
    }

    @Override
    protected void performCalculations() {
        computeBasket();
        QL.require(lossModel != null, "Basket has no default loss model assigned.");
        lossModel.setBasket(this);
    }

    public int size() {
        return pool.size();
    }

    public List<String> names() {
        return pool.names();
    }

    public List<Double> notionals() {
        return notionals;
    }

    public double notional() {
        double sum = 0.0;
        for (final double n : notionals) {
            sum += n;
        }
        return sum;
    }

    public List<DefaultProbKey> defaultKeys() {
        return pool.defaultKeys();
    }

    public Pool pool() {
        return pool;
    }

    public Date refDate() {
        return refDate;
    }

    public double attachmentRatio() {
        return attachmentRatio;
    }

    public double detachmentRatio() {
        return detachmentRatio;
    }

    public double basketNotional() {
        return basketNotional;
    }

    public double trancheNotional() {
        return trancheNotional;
    }

    public double attachmentAmount() {
        return attachmentAmount;
    }

    public double detachmentAmount() {
        return detachmentAmount;
    }

    public Claim claim() {
        return claim;
    }

    /** Sum of inception notionals attributed to {@code name} (handles duplicates). */
    public double exposure(final String name, final Date d) {
        final List<String> poolNames = pool.names();
        int found = -1;
        for (int i = 0; i < poolNames.size(); i++) {
            if (poolNames.get(i).equals(name)) {
                found = i;
                break;
            }
        }
        QL.require(found >= 0, "Name not in basket.");
        double total = 0.0;
        for (int i = found; i < poolNames.size(); i++) {
            if (poolNames.get(i).equals(name)) {
                total += notionals.get(i);
            }
        }
        return total;
    }

    public double exposure(final String name) {
        return exposure(name, new Date());
    }

    /** Cumulative default probability to date d for all issuers. */
    public List<Double> probabilities(final Date d) {
        final List<Double> prob = new ArrayList<>(size());
        final List<DefaultProbKey> defKeys = defaultKeys();
        for (int j = 0; j < size(); j++) {
            prob.add(pool.get(pool.names().get(j))
                    .defaultProbability(defKeys.get(j))
                    .currentLink().defaultProbability(d));
        }
        return prob;
    }

    public double cumulatedLoss() {
        return evalDateSettledLoss;
    }

    public double cumulatedLoss(final Date endDate) {
        QL.require(endDate.compareTo(refDate) >= 0,
                "Target date lies before basket inception");
        double loss = 0.0;
        for (int i = 0; i < size(); i++) {
            final DefaultEvent credEvent = pool.get(pool.names().get(i))
                    .defaultedBetween(refDate, endDate, pool.defaultKeys().get(i));
            if (credEvent != null && credEvent.hasSettled()) {
                loss += claim.amount(credEvent.date(),
                        exposure(pool.names().get(i), credEvent.date()),
                        credEvent.settlement().recoveryRate(
                                pool.defaultKeys().get(i).seniority()));
            }
        }
        return loss;
    }

    public double settledLoss() {
        return evalDateSettledLoss;
    }

    public double settledLoss(final Date endDate) {
        QL.require(endDate.compareTo(refDate) >= 0,
                "Target date lies before basket inception");
        double loss = 0.0;
        for (int i = 0; i < size(); i++) {
            final DefaultEvent credEvent = pool.get(pool.names().get(i))
                    .defaultedBetween(refDate, endDate, pool.defaultKeys().get(i));
            if (credEvent != null && credEvent.hasSettled()) {
                loss += claim.amount(credEvent.date(),
                        exposure(pool.names().get(i), credEvent.date()),
                        credEvent.settlement().recoveryRate(
                                pool.defaultKeys().get(i).seniority()));
            }
        }
        return loss;
    }

    public double remainingNotional() {
        return evalDateRemainingNot;
    }

    public double remainingNotional(final Date endDate) {
        double notional = 0.0;
        final List<DefaultProbKey> defKeys = defaultKeys();
        for (int i = 0; i < size(); i++) {
            if (pool.get(pool.names().get(i))
                    .defaultedBetween(refDate, endDate, defKeys.get(i)) == null) {
                notional += notionals.get(i);
            }
        }
        return notional;
    }

    public List<Double> remainingNotionals() {
        return evalDateLiveNotionals;
    }

    public List<Double> remainingNotionals(final Date endDate) {
        QL.require(endDate.compareTo(refDate) >= 0,
                "Target date lies before basket inception");
        final List<Integer> alive = liveList(endDate);
        final List<Double> result = new ArrayList<>(alive.size());
        for (final int i : alive) {
            result.add(exposure(pool.names().get(i), endDate));
        }
        return result;
    }

    public List<String> remainingNames() {
        return evalDateLiveNames;
    }

    public List<String> remainingNames(final Date endDate) {
        QL.require(endDate.compareTo(refDate) >= 0,
                "Target date lies before basket inception");
        final List<Integer> alive = liveList(endDate);
        final List<String> result = new ArrayList<>(alive.size());
        for (final int i : alive) {
            result.add(pool.names().get(i));
        }
        return result;
    }

    public List<DefaultProbKey> remainingDefaultKeys() {
        return evalDateLiveKeys;
    }

    public List<DefaultProbKey> remainingDefaultKeys(final Date endDate) {
        QL.require(endDate.compareTo(refDate) >= 0,
                "Target date lies before basket inception");
        final List<Integer> alive = liveList(endDate);
        final List<DefaultProbKey> result = new ArrayList<>(alive.size());
        for (final int i : alive) {
            result.add(pool.defaultKeys().get(i));
        }
        return result;
    }

    public int remainingSize() {
        return evalDateLiveList.size();
    }

    public int remainingSize(final Date d) {
        return remainingDefaultKeys(d).size();
    }

    public List<Double> remainingProbabilities(final Date d) {
        QL.require(d.compareTo(refDate) >= 0, "Target date lies before basket inception");
        final List<Integer> alive = liveList();
        final List<Double> prob = new ArrayList<>(alive.size());
        for (final int i : alive) {
            prob.add(pool.get(pool.names().get(i))
                    .defaultProbability(pool.defaultKeys().get(i))
                    .currentLink().defaultProbability(d, true));
        }
        return prob;
    }

    public double remainingAttachmentAmount() {
        return evalDateAttachAmount;
    }

    public double remainingAttachmentAmount(final Date endDate) {
        QL.require(endDate.compareTo(refDate) >= 0,
                "Target date lies before basket inception");
        final double loss = settledLoss(endDate);
        return Math.min(detachmentAmount,
                attachmentAmount + Math.max(0.0, loss - attachmentAmount));
    }

    public double remainingDetachmentAmount() {
        return evalDateDetachAmount;
    }

    public double remainingDetachmentAmount(final Date endDate) {
        QL.require(endDate.compareTo(refDate) >= 0,
                "Target date lies before basket inception");
        return detachmentAmount;
    }

    public double remainingTrancheNotional() {
        calculate();
        return evalDateDetachAmount - evalDateAttachAmount;
    }

    public double remainingTrancheNotional(final Date endDate) {
        calculate();
        return remainingDetachmentAmount(endDate) - remainingAttachmentAmount(endDate);
    }

    public List<Integer> liveList() {
        return evalDateLiveList;
    }

    public List<Integer> liveList(final Date endDate) {
        final List<Integer> result = new ArrayList<>();
        for (int i = 0; i < size(); i++) {
            if (pool.get(pool.names().get(i))
                    .defaultedBetween(refDate, endDate, pool.defaultKeys().get(i)) == null) {
                result.add(i);
            }
        }
        return result;
    }

    /** Assigns the default loss model to this basket. Resets calculations. */
    public void setLossModel(final DefaultLossModel lossModel) {
        if (this.lossModel != null) {
            this.lossModel.deleteObserver(this);
        }
        this.lossModel = lossModel;
        if (this.lossModel != null) {
            this.lossModel.addObserver(this);
        }
        // mark stale
        super.update();
    }

    //
    // basket loss statistics — delegate to model
    //

    public double expectedTrancheLoss(final Date d) {
        calculate();
        return cumulatedLoss() + lossModel.expectedTrancheLoss(d);
    }

    public double probOverLoss(final Date d, final double lossFraction) {
        calculate();
        if (evalDateRemainingNot == 0.0) {
            return 1.0;
        }
        // turn into live (remaining) tranche units
        final double xPtfl = attachmentAmount + (detachmentAmount - attachmentAmount) * lossFraction;
        final double xPrim = (xPtfl - evalDateAttachAmount)
                / (detachmentAmount - evalDateAttachAmount);
        if (xPtfl < 0.0) {
            return 1.0;
        }
        return lossModel.probOverLoss(d, xPrim);
    }

    public double percentile(final Date d, final double prob) {
        calculate();
        return lossModel.percentile(d, prob);
    }

    public double expectedShortfall(final Date d, final double prob) {
        calculate();
        return lossModel.expectedShortfall(d, prob);
    }

    public List<Double> splitVaRLevel(final Date d, final double loss) {
        calculate();
        return lossModel.splitVaRLevel(d, loss);
    }

    public Map<Double, Double> lossDistribution(final Date d) {
        calculate();
        return lossModel.lossDistribution(d);
    }

    public double densityTrancheLoss(final Date d, final double lossFraction) {
        calculate();
        return lossModel.densityTrancheLoss(d, lossFraction);
    }

    public double defaultCorrelation(final Date d, final int iName, final int jName) {
        calculate();
        return lossModel.defaultCorrelation(d, iName, jName);
    }

    public List<Double> probsBeingNthEvent(final int n, final Date d) {
        final int alreadyDefaulted = pool.size() - remainingNames().size();
        if (alreadyDefaulted >= n) {
            final List<Double> zeros = new ArrayList<>(remainingNames().size());
            for (int i = 0; i < remainingNames().size(); i++) {
                zeros.add(0.0);
            }
            return zeros;
        }
        calculate();
        return lossModel.probsBeingNthEvent(n - alreadyDefaulted, d);
    }

    public double probAtLeastNEvents(final int n, final Date d) {
        calculate();
        return lossModel.probAtLeastNEvents(n, d);
    }

    public double recoveryRate(final Date d, final int iName) {
        calculate();
        return lossModel.expectedRecovery(d, iName, pool.defaultKeys().get(iName));
    }
}
