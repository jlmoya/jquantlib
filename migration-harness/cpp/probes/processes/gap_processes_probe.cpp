// migration-harness/cpp/probes/processes/gap_processes_probe.cpp
// Reference values for the 7 gap stochastic-process classes ported in
// port(processes): Black/BlackScholes/GarmanKohlagen processes,
// EndEulerDiscretization, G2Process, G2ForwardProcess, JointStochasticProcess.
// QuantLib v1.42.1. Java ports cross-validate against these.

#include <ql/version.hpp>
#include "../common.hpp"

#include <ql/processes/blackscholesprocess.hpp>
#include <ql/processes/endeulerdiscretization.hpp>
#include <ql/processes/eulerdiscretization.hpp>
#include <ql/processes/g2process.hpp>
#include <ql/processes/geometricbrownianprocess.hpp>
#include <ql/processes/jointstochasticprocess.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/termstructures/volatility/equityfx/blackconstantvol.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>

using namespace jqml_harness;
using namespace QuantLib;

namespace {

const Date REF_DATE(22, April, 2026);

Handle<YieldTermStructure> flatCurve(Real rate) {
    Settings::instance().evaluationDate() = REF_DATE;
    return Handle<YieldTermStructure>(
        ext::make_shared<FlatForward>(REF_DATE, rate, Actual365Fixed()));
}

Handle<BlackVolTermStructure> flatVol(Real vol) {
    return Handle<BlackVolTermStructure>(
        ext::make_shared<BlackConstantVol>(
            REF_DATE, NullCalendar(), vol, Actual365Fixed()));
}

// ---- Minimal concrete JointStochasticProcess for cross-validation ----
// Zero cross-model correlation, state-independent, identity numeraire,
// trivial pre/postEvolve. The Java probe-test mirrors this exact subclass.
class TestJointProcess : public JointStochasticProcess {
  public:
    explicit TestJointProcess(std::vector<ext::shared_ptr<StochasticProcess> > l)
    : JointStochasticProcess(std::move(l), Null<Size>()) {}

    void preEvolve(Time, const Array&, Time, const Array&) const override {}
    Array postEvolve(Time, const Array&, Time, const Array&,
                     const Array& y0) const override { return y0; }
    DiscountFactor numeraire(Time, const Array&) const override { return 1.0; }
    bool correlationIsStateDependent() const override { return false; }
    Matrix crossModelCorrelation(Time, const Array&) const override {
        return Matrix(size(), size(), 0.0);
    }
};

// Same as TestJointProcess but with a NON-ZERO cross-model correlation, so the
// joint covariance is genuinely non-diagonal. This exercises the diffusion()/
// stdDeviation() pseudoSqrt path, which uses the DEFAULT salvaging algorithm
// (SalvagingAlgorithm::None == Cholesky); the resulting factor is lower-triangular
// and differs from the Spectral factor. rho is the off-diagonal correlation; the
// diagonal stays 0.0 so only the cross-covariance is added.
class TestCorrelatedJointProcess : public JointStochasticProcess {
  public:
    TestCorrelatedJointProcess(std::vector<ext::shared_ptr<StochasticProcess> > l, Real rho)
    : JointStochasticProcess(std::move(l), Null<Size>()), rho_(rho) {}

    void preEvolve(Time, const Array&, Time, const Array&) const override {}
    Array postEvolve(Time, const Array&, Time, const Array&,
                     const Array& y0) const override { return y0; }
    DiscountFactor numeraire(Time, const Array&) const override { return 1.0; }
    bool correlationIsStateDependent() const override { return false; }
    Matrix crossModelCorrelation(Time, const Array&) const override {
        Matrix m(size(), size(), 0.0);
        // off-diagonal cross-model correlation between the two GBM constituents
        m[0][1] = rho_;
        m[1][0] = rho_;
        return m;
    }
  private:
    Real rho_;
};

// Helper: a GeneralizedBlackScholesProcess (the "plain" parent) using the same
// dividend/risk-free/vol curves, to confirm subclasses differ correctly.
void emitBsFamilyCase(ReferenceWriter& out, const std::string& name,
                      const GeneralizedBlackScholesProcess& proc,
                      Real t, Real x, Real t0, Real dt, Real dw) {
    json inputs = {{"t", t}, {"x", x}, {"t0", t0}, {"dt", dt}, {"dw", dw}};
    json expected = {
        {"x0", proc.x0()},
        {"drift", proc.drift(t, x)},
        {"diffusion", proc.diffusion(t, x)},
        {"evolve", proc.evolve(t0, x, dt, dw)}
    };
    out.addCase(name, inputs, expected);
}

} // namespace

int main() {
    ReferenceWriter out("processes/gap_processes", QL_VERSION, "gap_processes_probe");

    const Real S0 = 100.0;
    const Real R = 0.05;      // domestic / risk-free
    const Real Q = 0.03;      // dividend yield
    const Real RF = 0.02;     // foreign risk-free (Garman-Kohlhagen)
    const Real VOL = 0.20;
    const Real T = 0.5, X = 100.0, T0 = 0.0, DT = 0.25, DW = 0.3;

    Handle<Quote> spot(ext::make_shared<SimpleQuote>(S0));

    // ===== Black-Scholes family: each must differ per its carry convention =====
    {
        // BlackScholesProcess: NO dividend (q = 0) => drift = r - 0.5*sigma^2
        BlackScholesProcess bs(spot, flatCurve(R), flatVol(VOL));
        emitBsFamilyCase(out, "blackscholes", bs, T, X, T0, DT, DW);
    }
    {
        // BlackScholesMertonProcess: drift = r - q - 0.5*sigma^2 (the generalized parent)
        BlackScholesMertonProcess bsm(spot, flatCurve(Q), flatCurve(R), flatVol(VOL));
        emitBsFamilyCase(out, "merton", bsm, T, X, T0, DT, DW);
    }
    {
        // BlackProcess: risk-free used as dividend too => drift = -0.5*sigma^2
        BlackProcess bp(spot, flatCurve(R), flatVol(VOL));
        emitBsFamilyCase(out, "black", bp, T, X, T0, DT, DW);
    }
    {
        // GarmanKohlagenProcess: drift = r_dom - r_for - 0.5*sigma^2
        GarmanKohlagenProcess gk(spot, flatCurve(RF), flatCurve(R), flatVol(VOL));
        emitBsFamilyCase(out, "garmankohlagen", gk, T, X, T0, DT, DW);
    }

    // ===== EndEulerDiscretization vs EulerDiscretization on GBM 1D =====
    // GBM: drift(t,x)=mue*x, diffusion(t,x)=sigma*x (time-independent here, but
    // the END-point evaluation t0+dt still differs because x is the SAME but we
    // exercise both schemes on a process where evaluating at t0 vs t0+dt is wired.)
    // To make END vs START differ unambiguously, we also emit a GBM whose
    // drift/diffusion we evaluate at distinct points.
    {
        GeometricBrownianMotionProcess gbm(S0, 0.07, VOL);
        EulerDiscretization euler;
        EndEulerDiscretization endEuler;

        const Real gt0 = 1.0, gx0 = 120.0, gdt = 0.5;
        // For GBM the coefficients depend on x only, so START and END agree;
        // assert that explicitly (both schemes use x0, not the evolved x).
        json inputs = {{"t0", gt0}, {"x0", gx0}, {"dt", gdt}};
        json expected = {
            {"euler_drift",    euler.drift(gbm, gt0, gx0, gdt)},
            {"euler_diff",     euler.diffusion(gbm, gt0, gx0, gdt)},
            {"euler_var",      euler.variance(gbm, gt0, gx0, gdt)},
            {"endeuler_drift", endEuler.drift(gbm, gt0, gx0, gdt)},
            {"endeuler_diff",  endEuler.diffusion(gbm, gt0, gx0, gdt)},
            {"endeuler_var",   endEuler.variance(gbm, gt0, gx0, gdt)}
        };
        out.addCase("endeuler_gbm", inputs, expected);
    }
    {
        // A genuinely time-dependent 1D process: GeneralizedBlackScholesProcess
        // drift depends on the forward rate between t and t+0.0001, which is
        // flat here, but evaluating END vs START on a flat curve still yields
        // identical coefficients — so to PROVE the END-point wiring we use a
        // process whose diffusion is x-only (GBM) and confirm drift*dt and
        // diffusion*sqrt(dt) scale exactly. The math identity below is the
        // real discriminator: endEuler.drift == process.drift(t0+dt,x0)*dt.
        GeometricBrownianMotionProcess gbm(S0, 0.07, VOL);
        EndEulerDiscretization endEuler;
        const Real gt0 = 0.0, gx0 = 90.0, gdt = 2.0;
        json inputs = {{"t0", gt0}, {"x0", gx0}, {"dt", gdt}};
        json expected = {
            // raw process coefficients at the END point t0+dt
            {"proc_drift_end",  gbm.drift(gt0 + gdt, gx0)},
            {"proc_diff_end",   gbm.diffusion(gt0 + gdt, gx0)},
            {"endeuler_drift",  endEuler.drift(gbm, gt0, gx0, gdt)},
            {"endeuler_diff",   endEuler.diffusion(gbm, gt0, gx0, gdt)},
            {"endeuler_var",    endEuler.variance(gbm, gt0, gx0, gdt)}
        };
        out.addCase("endeuler_identity", inputs, expected);
    }

    // ===== G2Process =====
    {
        const Real a = 0.1, sigma = 0.01, b = 0.3, eta = 0.012, rho = -0.5;
        G2Process g2(a, sigma, b, eta, rho);

        Array iv = g2.initialValues();
        Array x(2); x[0] = 0.02; x[1] = -0.01;
        Array d = g2.drift(0.5, x);
        Matrix diff = g2.diffusion(0.5, x);

        const Real t0 = 0.25, dt = 1.5;
        Array x0(2); x0[0] = 0.02; x0[1] = -0.01;
        Array e = g2.expectation(t0, x0, dt);
        Matrix sd = g2.stdDeviation(t0, x0, dt);
        Matrix cov = g2.covariance(t0, x0, dt);

        json inputs = {{"a", a}, {"sigma", sigma}, {"b", b}, {"eta", eta},
                       {"rho", rho}, {"t", 0.5}, {"x", {0.02, -0.01}},
                       {"t0", t0}, {"dt", dt}};
        json expected = {
            {"size", static_cast<long long>(g2.size())},
            {"x0", g2.x0()}, {"y0", g2.y0()},
            {"a_acc", g2.a()}, {"sigma_acc", g2.sigma()},
            {"b_acc", g2.b()}, {"eta_acc", g2.eta()}, {"rho_acc", g2.rho()},
            {"initialValues", {iv[0], iv[1]}},
            {"drift", {d[0], d[1]}},
            {"diffusion", {diff[0][0], diff[0][1], diff[1][0], diff[1][1]}},
            {"expectation", {e[0], e[1]}},
            {"stdDeviation", {sd[0][0], sd[0][1], sd[1][0], sd[1][1]}},
            {"covariance", {cov[0][0], cov[0][1], cov[1][0], cov[1][1]}}
        };
        out.addCase("g2", inputs, expected);
    }

    // ===== G2ForwardProcess =====
    {
        const Real a = 0.1, sigma = 0.01, b = 0.3, eta = 0.012, rho = -0.5;
        const Real Tfwd = 5.0;
        G2ForwardProcess g2f(a, sigma, b, eta, rho);
        g2f.setForwardMeasureTime(Tfwd);

        Array iv = g2f.initialValues();
        Array x(2); x[0] = 0.02; x[1] = -0.01;
        Array d = g2f.drift(0.5, x);
        Matrix diff = g2f.diffusion(0.5, x);

        const Real t0 = 0.25, dt = 1.5;
        Array x0(2); x0[0] = 0.02; x0[1] = -0.01;
        Array e = g2f.expectation(t0, x0, dt);
        Matrix sd = g2f.stdDeviation(t0, x0, dt);
        Matrix cov = g2f.covariance(t0, x0, dt);

        json inputs = {{"a", a}, {"sigma", sigma}, {"b", b}, {"eta", eta},
                       {"rho", rho}, {"T", Tfwd}, {"t", 0.5}, {"x", {0.02, -0.01}},
                       {"t0", t0}, {"dt", dt}};
        json expected = {
            {"size", static_cast<long long>(g2f.size())},
            {"initialValues", {iv[0], iv[1]}},
            {"drift", {d[0], d[1]}},
            {"diffusion", {diff[0][0], diff[0][1], diff[1][0], diff[1][1]}},
            {"expectation", {e[0], e[1]}},
            {"stdDeviation", {sd[0][0], sd[0][1], sd[1][0], sd[1][1]}},
            {"covariance", {cov[0][0], cov[0][1], cov[1][0], cov[1][1]}}
        };
        out.addCase("g2forward", inputs, expected);
    }

    // ===== JointStochasticProcess (two independent GBM constituents) =====
    {
        std::vector<ext::shared_ptr<StochasticProcess> > l;
        l.push_back(ext::make_shared<GeometricBrownianMotionProcess>(100.0, 0.05, 0.20));
        l.push_back(ext::make_shared<GeometricBrownianMotionProcess>( 50.0, 0.03, 0.30));
        TestJointProcess jp(l);

        Array iv = jp.initialValues();
        Array x(2); x[0] = 110.0; x[1] = 55.0;
        Array d = jp.drift(0.5, x);

        const Real t0 = 0.0, dt = 0.25;
        Array x0(2); x0[0] = 100.0; x0[1] = 50.0;
        Matrix cov = jp.covariance(t0, x0, dt);
        Matrix cmc = jp.crossModelCorrelation(t0, x0);

        // apply: each constituent uses x+dx (GBM inherits StochasticProcess1D.apply)
        Array dx(2); dx[0] = 1.5; dx[1] = -0.5;
        Array ap = jp.apply(x0, dx);

        json inputs = {{"t", 0.5}, {"x", {110.0, 55.0}},
                       {"t0", t0}, {"dt", dt}};
        json expected = {
            {"size", static_cast<long long>(jp.size())},
            {"factors", static_cast<long long>(jp.factors())},
            {"initialValues", {iv[0], iv[1]}},
            {"drift", {d[0], d[1]}},
            {"covariance", {cov[0][0], cov[0][1], cov[1][0], cov[1][1]}},
            {"crossModelCorrelation", {cmc[0][0], cmc[0][1], cmc[1][0], cmc[1][1]}},
            {"apply", {ap[0], ap[1]}}
        };
        out.addCase("joint_two_gbm", inputs, expected);
    }

    // ===== JointStochasticProcess (two CORRELATED GBM constituents) =====
    // Non-zero cross-model correlation => non-diagonal covariance, so diffusion()
    // and stdDeviation() exercise the pseudoSqrt(..., None) Cholesky path. The
    // returned factor is LOWER-TRIANGULAR (a good cross-check that None != Spectral).
    {
        const Real RHO = 0.4;
        std::vector<ext::shared_ptr<StochasticProcess> > l;
        l.push_back(ext::make_shared<GeometricBrownianMotionProcess>(100.0, 0.05, 0.20));
        l.push_back(ext::make_shared<GeometricBrownianMotionProcess>( 50.0, 0.03, 0.30));
        TestCorrelatedJointProcess jp(l, RHO);

        const Real t  = 0.5;
        Array xd(2); xd[0] = 110.0; xd[1] = 55.0;   // state for diffusion(t,x)

        const Real t0 = 0.0, dt = 0.25;
        Array x0(2); x0[0] = 100.0; x0[1] = 50.0;   // state for cov/stdDeviation(t0,x0,dt)

        Matrix cov  = jp.covariance(t0, x0, dt);
        Matrix cmc  = jp.crossModelCorrelation(t0, x0);
        Matrix diff = jp.diffusion(t, xd);
        Matrix sd   = jp.stdDeviation(t0, x0, dt);

        json inputs = {{"rho", RHO}, {"t", t}, {"x", {110.0, 55.0}},
                       {"t0", t0}, {"dt", dt}};
        json expected = {
            {"size", static_cast<long long>(jp.size())},
            {"factors", static_cast<long long>(jp.factors())},
            {"covariance", {cov[0][0], cov[0][1], cov[1][0], cov[1][1]}},
            {"crossModelCorrelation", {cmc[0][0], cmc[0][1], cmc[1][0], cmc[1][1]}},
            {"diffusion", {diff[0][0], diff[0][1], diff[1][0], diff[1][1]}},
            {"stdDeviation", {sd[0][0], sd[0][1], sd[1][0], sd[1][1]}}
        };
        out.addCase("joint_two_gbm_correlated", inputs, expected);
    }

    out.write();
    return 0;
}
