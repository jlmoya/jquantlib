// One-shot probe for StochasticCollocationInvCDF cross-validation.
// Phase 5e.5b-CFC-d-193.
#include <ql/quantlib.hpp>
#include <boost/math/distributions/non_central_chi_squared.hpp>
#include <iostream>
#include <iomanip>

namespace {
    using QuantLib::Real;
    class InvNCC2 {
        const boost::math::non_central_chi_squared_distribution<Real> dist_;
      public:
        InvNCC2(Real df, Real ncp) : dist_(df, ncp) {}
        Real operator()(Real x) const { return boost::math::quantile(dist_, x); }
    };
}

int main() {
    using namespace QuantLib;
    std::cout << std::setprecision(17);

    // 1. abscissa ordering & sigma rescaling.
    GaussHermiteIntegration gh(10);
    Array x = gh.x();
    Array x2 = M_SQRT2 * x;
    std::cout << "x_front=" << x[0]
              << " x_back=" << x[x.size() - 1] << "\n";
    std::cout << "sqrt2_x_front=" << x2[0]
              << " sqrt2_x_back=" << x2[x2.size() - 1] << "\n";
    InverseCumulativeNormal invN;
    std::cout << "invN(0.9999999)=" << invN(0.9999999) << "\n";
    GaussHermiteIntegration gh30(30);
    Array x30 = M_SQRT2 * gh30.x();
    std::cout << "sqrt2*x30 front=" << x30[0]
              << " back=" << x30[x30.size() - 1] << "\n";
    std::cout << "sigma(pMax=0.9999999, n=30) ="
              << (x30[x30.size() - 1] / invN(0.9999999)) << "\n";

    // 2. C++ test reference
    const Real k = 3.0, lambda = 1.0;
    const InvNCC2 invCDF(k, lambda);
    const CumulativeNormalDistribution normalCDF;
    const StochasticCollocationInvCDF scInvCDF10(invCDF, 10);
    const StochasticCollocationInvCDF scInvCDF30(invCDF, 30, 0.9999999);

    std::cout << "# x, u, scInvCDF10(u), scInvCDF10.value(x), invCDF(u), scInvCDF30(u)\n";
    for (Real xx = -3.0; xx < 3.0 + 1e-9; xx += 0.5) {
        Real u = normalCDF(xx);
        std::cout << xx << " " << u
                  << " " << scInvCDF10(u)
                  << " " << scInvCDF10.value(xx)
                  << " " << invCDF(u)
                  << " " << scInvCDF30(u)
                  << "\n";
    }
    return 0;
}
