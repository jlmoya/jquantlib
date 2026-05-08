// migration-harness/cpp/probes/methods/finitedifferences/schemes/fdm_schemes_probe.cpp
//
// Reference values for ExplicitEulerScheme, CrankNicolsonScheme,
// CraigSneydScheme, ModifiedCraigSneydScheme, MethodOfLinesScheme,
// and TrBDF2Scheme.
//
// Test oracle: 1D heat equation  u_t = u_xx  on [0, pi]
//   u(0, t) = u(pi, t) = 0  (Dirichlet BCs, zero boundary)
//   u(x, 0) = sin(x)
//   Exact solution: u(x, t) = sin(x) * exp(-t)
//
// The spatial operator is the finite-difference Laplacian on a uniform
// N-point interior mesh. Dirichlet BCs are embedded by zeroing the two
// ghost-boundary contributions (the FD stencil already satisfies them via
// the operator construction below with h^2 denominators).
//
// For each scheme we roll back from t = T to t = 0 (i.e. backward in time
// as QuantLib's PDE solvers do) using N time steps of size dt = T / N.
// We report the L-inf error vs the exact solution at t = 0.
//
// Phase 2l Track C schemes probe.

#include <ql/version.hpp>
#include <ql/methods/finitedifferences/schemes/expliciteulerscheme.hpp>
#include <ql/methods/finitedifferences/schemes/cranknicolsonscheme.hpp>
#include <ql/methods/finitedifferences/schemes/craigsneydscheme.hpp>
#include <ql/methods/finitedifferences/schemes/modifiedcraigsneydscheme.hpp>
#include <ql/methods/finitedifferences/schemes/methodoflinesscheme.hpp>
#include <ql/methods/finitedifferences/schemes/trbdf2scheme.hpp>
#include <ql/methods/finitedifferences/operators/fdmlinearopcomposite.hpp>
#include <ql/methods/finitedifferences/operators/fdmlinearop.hpp>
#include <ql/math/array.hpp>
#include <ql/shared_ptr.hpp>
#include "../../../common.hpp"

#include <cmath>
#include <vector>
#include <algorithm>
#include <string>

using namespace QuantLib;
using namespace jqml_harness;

// ---------------------------------------------------------------------------
// Minimal 1D FdmLinearOpComposite: central-difference Laplacian on [0,pi]
// with N interior points and Dirichlet BCs (u=0 at boundaries).
// ---------------------------------------------------------------------------
class HeatOp1D : public FdmLinearOpComposite {
public:
    explicit HeatOp1D(Size n)
    : n_(n), h_(M_PI / (n + 1)), invH2_(1.0 / (h_ * h_)) {
        // grid x_i = (i+1)*h, i=0..n-1
    }

    Size size() const override { return 1; }
    void setTime(Time, Time) override {}

    // Apply the Laplacian: (A * r)_i = (r_{i-1} - 2*r_i + r_{i+1}) / h^2
    // with r_{-1} = r_N = 0 (Dirichlet).
    Array apply(const Array& r) const override {
        Array out(n_, 0.0);
        for (Size i = 0; i < n_; ++i) {
            const Real left  = (i > 0)     ? r[i-1] : 0.0;
            const Real right = (i < n_-1)  ? r[i+1] : 0.0;
            out[i] = (left - 2.0*r[i] + right) * invH2_;
        }
        return out;
    }

    // Mixed part: zero for 1D
    Array apply_mixed(const Array& r) const override {
        return Array(n_, 0.0);
    }

    // Directional apply (direction 0 = full operator in 1D)
    Array apply_direction(Size, const Array& r) const override {
        return apply(r);
    }

    // Solve (I - s * L) * x = r  by Thomas algorithm (tridiagonal)
    Array solve_splitting(Size, const Array& r, Real s) const override {
        // Tridiagonal: sub = super = s * invH2_, main = 1 - 2*s*invH2_
        const Real sub   =  s * invH2_;  // lower diagonal coeff
        const Real sup_  =  s * invH2_;  // upper diagonal coeff
        const Real main_ = 1.0 - 2.0 * s * invH2_;

        Array x(n_);
        std::vector<Real> c(n_), d(n_);
        // Forward sweep
        Real m = main_;
        c[0] = sup_ / m;
        d[0] = r[0] / m;
        for (Size i = 1; i < n_; ++i) {
            m = main_ - sub * c[i-1];
            c[i] = sup_ / m;
            d[i] = (r[i] - sub * d[i-1]) / m;
        }
        // Back-substitution
        x[n_-1] = d[n_-1];
        for (int i = (int)n_-2; i >= 0; --i) {
            x[i] = d[i] - c[i] * x[i+1];
        }
        return x;
    }
    // Preconditioner: same as solve_splitting for 1D
    Array preconditioner(const Array& r, Real s) const override {
        return solve_splitting(0, r, s);
    }

    // toMatrixDecomp not needed for our tests; throw per base default
    // (toMatrix() on FdmLinearOpComposite calls toMatrixDecomp() which
    //  already QL_FAILs by default — no override needed here.)

    // Grid
    Array grid() const {
        Array g(n_);
        for (Size i = 0; i < n_; ++i) g[i] = (i+1) * h_;
        return g;
    }

private:
    Size n_;
    Real h_, invH2_;
};

// ---------------------------------------------------------------------------
// Exact solution: sin(x) * exp(-t)
// ---------------------------------------------------------------------------
static Array exactSolution(const Array& x, Real t) {
    Array u(x.size());
    for (Size i = 0; i < x.size(); ++i)
        u[i] = std::sin(x[i]) * std::exp(-t);
    return u;
}

static Real linfError(const Array& a, const Array& b) {
    Real err = 0.0;
    for (Size i = 0; i < a.size(); ++i)
        err = std::max(err, std::abs(a[i] - b[i]));
    return err;
}

// ---------------------------------------------------------------------------
// Helpers: initial condition at t=T (backward: we start at T, end at 0)
// ---------------------------------------------------------------------------
static Array ic(const Array& x, Real T) {
    return exactSolution(x, T);  // start of backward roll
}

// ---- Per-scheme test runners -----------------------------------------------

static void addExplicitEuler(ReferenceWriter& out,
                             const std::string& label,
                             Size n, Size nSteps, Real T) {
    auto op = ext::make_shared<HeatOp1D>(n);
    ExplicitEulerScheme scheme(op);

    Array x = op->grid();
    Array u = ic(x, T);
    Real dt = T / nSteps;
    scheme.setStep(dt);

    for (Size k = 0; k < nSteps; ++k) {
        Real t = T - k * dt;
        scheme.step(u, t);
    }
    Real err = linfError(u, exactSolution(x, 0.0));
    out.addCase(label,
        {{"n", (int)n}, {"nSteps", (int)nSteps}, {"T", T}},
        err);
}

static void addCrankNicolson(ReferenceWriter& out,
                             const std::string& label,
                             Size n, Size nSteps, Real T, Real theta) {
    auto op = ext::make_shared<HeatOp1D>(n);
    CrankNicolsonScheme scheme(theta, op);

    Array x = op->grid();
    Array u = ic(x, T);
    Real dt = T / nSteps;
    scheme.setStep(dt);

    for (Size k = 0; k < nSteps; ++k) {
        Real t = T - k * dt;
        scheme.step(u, t);
    }
    Real err = linfError(u, exactSolution(x, 0.0));
    out.addCase(label,
        {{"n", (int)n}, {"nSteps", (int)nSteps}, {"T", T}, {"theta", theta}},
        err);
}

static void addCraigSneyd(ReferenceWriter& out,
                          const std::string& label,
                          Size n, Size nSteps, Real T, Real theta, Real mu) {
    auto op = ext::make_shared<HeatOp1D>(n);
    CraigSneydScheme scheme(theta, mu, op);

    Array x = op->grid();
    Array u = ic(x, T);
    Real dt = T / nSteps;
    scheme.setStep(dt);

    for (Size k = 0; k < nSteps; ++k) {
        Real t = T - k * dt;
        scheme.step(u, t);
    }
    Real err = linfError(u, exactSolution(x, 0.0));
    out.addCase(label,
        {{"n", (int)n}, {"nSteps", (int)nSteps}, {"T", T}, {"theta", theta}, {"mu", mu}},
        err);
}

static void addModifiedCraigSneyd(ReferenceWriter& out,
                                  const std::string& label,
                                  Size n, Size nSteps, Real T, Real theta, Real mu) {
    auto op = ext::make_shared<HeatOp1D>(n);
    ModifiedCraigSneydScheme scheme(theta, mu, op);

    Array x = op->grid();
    Array u = ic(x, T);
    Real dt = T / nSteps;
    scheme.setStep(dt);

    for (Size k = 0; k < nSteps; ++k) {
        Real t = T - k * dt;
        scheme.step(u, t);
    }
    Real err = linfError(u, exactSolution(x, 0.0));
    out.addCase(label,
        {{"n", (int)n}, {"nSteps", (int)nSteps}, {"T", T}, {"theta", theta}, {"mu", mu}},
        err);
}

static void addMethodOfLines(ReferenceWriter& out,
                             const std::string& label,
                             Size n, Size nSteps, Real T, Real eps, Real relInitStep) {
    auto op = ext::make_shared<HeatOp1D>(n);
    MethodOfLinesScheme scheme(eps, relInitStep, op);

    Array x = op->grid();
    Array u = ic(x, T);
    Real dt = T / nSteps;
    scheme.setStep(dt);

    for (Size k = 0; k < nSteps; ++k) {
        Real t = T - k * dt;
        scheme.step(u, t);
    }
    Real err = linfError(u, exactSolution(x, 0.0));
    out.addCase(label,
        {{"n", (int)n}, {"nSteps", (int)nSteps}, {"T", T}, {"eps", eps}, {"relInitStep", relInitStep}},
        err);
}

static void addTrBDF2(ReferenceWriter& out,
                      const std::string& label,
                      Size n, Size nSteps, Real T, Real alpha, Real theta) {
    auto op = ext::make_shared<HeatOp1D>(n);
    // Trapezoidal sub-scheme (Crank-Nicolson with given theta)
    auto cn = ext::make_shared<CrankNicolsonScheme>(theta, op);
    TrBDF2Scheme<CrankNicolsonScheme> scheme(alpha, op, cn);

    Array x = op->grid();
    Array u = ic(x, T);
    Real dt = T / nSteps;
    scheme.setStep(dt);

    for (Size k = 0; k < nSteps; ++k) {
        Real t = T - k * dt;
        scheme.step(u, t);
    }
    Real err = linfError(u, exactSolution(x, 0.0));
    out.addCase(label,
        {{"n", (int)n}, {"nSteps", (int)nSteps}, {"T", T}, {"alpha", alpha}, {"theta", theta}},
        err);
}

// ---------------------------------------------------------------------------
// main
// ---------------------------------------------------------------------------
int main() {
    ReferenceWriter out("methods/finitedifferences/schemes/fdm_schemes",
                        QL_VERSION,
                        "fdm_schemes_probe");

    // ---- C.1 ExplicitEulerScheme -------------------------------------------
    // Conditionally stable: dt <= h^2/2.  Use small T with many steps.
    // n=50: h=pi/51~0.0616, h^2/2~0.0019. Use dt=0.001 (nSteps=T/dt).
    addExplicitEuler(out, "explicit_euler_n50_T0.1_nSteps50",    50,   50, 0.1);
    addExplicitEuler(out, "explicit_euler_n50_T0.1_nSteps100",   50,  100, 0.1);
    addExplicitEuler(out, "explicit_euler_n100_T0.05_nSteps100", 100, 100, 0.05);
    addExplicitEuler(out, "explicit_euler_n20_T0.05_nSteps200",   20, 200, 0.05);

    // ---- C.2 CrankNicolsonScheme -------------------------------------------
    // theta=0.5: CN; theta=1.0: pure implicit (ImplicitEuler)
    addCrankNicolson(out, "crank_nicolson_theta0.5_n50_T1_nSteps50",   50, 50, 1.0, 0.5);
    addCrankNicolson(out, "crank_nicolson_theta0.5_n100_T1_nSteps100",100,100, 1.0, 0.5);
    addCrankNicolson(out, "crank_nicolson_theta1.0_n50_T1_nSteps50",   50, 50, 1.0, 1.0);
    addCrankNicolson(out, "crank_nicolson_theta0.5_n50_T0.5_nSteps20", 50, 20, 0.5, 0.5);

    // ---- C.3 CraigSneydScheme ----------------------------------------------
    // For 1D: mixed part is zero, so CS reduces to Douglas scheme.
    addCraigSneyd(out, "craig_sneyd_theta0.5_mu0.5_n50_T1_nSteps50",   50, 50, 1.0, 0.5, 0.5);
    addCraigSneyd(out, "craig_sneyd_theta0.5_mu0.3_n50_T1_nSteps50",   50, 50, 1.0, 0.5, 0.3);
    addCraigSneyd(out, "craig_sneyd_theta0.5_mu0.5_n100_T1_nSteps100",100,100, 1.0, 0.5, 0.5);
    addCraigSneyd(out, "craig_sneyd_theta0.5_mu0.5_n50_T2_nSteps100",  50,100, 2.0, 0.5, 0.5);

    // ---- C.4 ModifiedCraigSneydScheme --------------------------------------
    addModifiedCraigSneyd(out, "mod_craig_sneyd_theta0.5_mu0.5_n50_T1_nSteps50",   50, 50,1.0,0.5,0.5);
    addModifiedCraigSneyd(out, "mod_craig_sneyd_theta0.5_mu0.3_n50_T1_nSteps50",   50, 50,1.0,0.5,0.3);
    addModifiedCraigSneyd(out, "mod_craig_sneyd_theta0.5_mu0.5_n100_T1_nSteps100",100,100,1.0,0.5,0.5);
    addModifiedCraigSneyd(out, "mod_craig_sneyd_theta0.5_mu0.5_n50_T2_nSteps100",  50,100,2.0,0.5,0.5);

    // ---- C.5 MethodOfLinesScheme -------------------------------------------
    // Uses adaptive RK; eps controls integration error. relInitStep is relative to dt.
    addMethodOfLines(out, "mol_eps1e-6_relStep0.01_n50_T1_nSteps20",  50, 20, 1.0, 1e-6, 0.01);
    addMethodOfLines(out, "mol_eps1e-6_relStep0.01_n50_T0.5_nSteps10",50, 10, 0.5, 1e-6, 0.01);
    addMethodOfLines(out, "mol_eps1e-8_relStep0.01_n30_T1_nSteps10",  30, 10, 1.0, 1e-8, 0.01);
    addMethodOfLines(out, "mol_eps1e-6_relStep0.01_n100_T1_nSteps20",100, 20, 1.0, 1e-6, 0.01);

    // ---- C.6 TrBDF2Scheme --------------------------------------------------
    // alpha = 2 - sqrt(2) ~ 0.5858 is optimal; theta=0.5 for CN sub-step
    addTrBDF2(out, "trbdf2_alpha0.5858_theta0.5_n50_T1_nSteps50",  50, 50, 1.0, 2.0-std::sqrt(2.0), 0.5);
    addTrBDF2(out, "trbdf2_alpha0.5858_theta0.5_n100_T1_nSteps50",100, 50, 1.0, 2.0-std::sqrt(2.0), 0.5);
    addTrBDF2(out, "trbdf2_alpha0.5_theta0.5_n50_T1_nSteps50",     50, 50, 1.0, 0.5, 0.5);
    addTrBDF2(out, "trbdf2_alpha0.5858_theta0.5_n50_T2_nSteps100", 50,100, 2.0, 2.0-std::sqrt(2.0), 0.5);

    out.write();
    return 0;
}
