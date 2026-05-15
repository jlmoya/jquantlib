// migration-harness/cpp/probes/math/matrixutilities/pseudosqrt_spectral_probe.cpp
// Reference values for SymmetricSchurDecomposition + PseudoSqrt(Spectral) on
// the 4x4 correlation matrix used by the Himalaya/Everest/Pagoda cached MC
// tests in test-suite/{himalayaoption,everestoption,pagodaoption}.cpp.
//
// Phase 5e.5b-CFC-d-27: Java's PseudoSqrt(Spectral) decorrelated normals
// vs C++ because Java's SymmetricSchurDecomposition was missing the
// eigen-pair sort (descending by eigenvalue) + sign normalization
// (first row of each column non-negative) that C++ pseudosqrt.cpp depends
// on (see symmetricschurdecomposition.cpp lines 115-138).
//
// Tier: TIGHT (abs 1e-12, rel 1e-14) for both eigenvalues and eigenvectors;
// PseudoSqrt(Spectral) result also TIGHT.

#include <ql/version.hpp>
#include <ql/math/matrix.hpp>
#include <ql/math/matrixutilities/pseudosqrt.hpp>
#include <ql/math/matrixutilities/symmetricschurdecomposition.hpp>
#include "../../common.hpp"

#include <cmath>
#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

// 4x4 correlation matrix — exactly the one used in Himalaya/Everest/Pagoda
// testCached cases (see test-suite/himalayaoption.cpp:78-94 et al.).
Matrix himalayaCorrelation() {
    Matrix corr(4, 4);
    corr[0][0] = 1.00; corr[0][1] = 0.50; corr[0][2] = 0.30; corr[0][3] = 0.10;
    corr[1][0] = 0.50; corr[1][1] = 1.00; corr[1][2] = 0.20; corr[1][3] = 0.40;
    corr[2][0] = 0.30; corr[2][1] = 0.20; corr[2][2] = 1.00; corr[2][3] = 0.60;
    corr[3][0] = 0.10; corr[3][1] = 0.40; corr[3][2] = 0.60; corr[3][3] = 1.00;
    return corr;
}

// 3x3 simple SPD matrix for cross-check.
Matrix small3x3() {
    Matrix m(3, 3);
    m[0][0] = 4.0; m[0][1] = 1.0; m[0][2] = 0.5;
    m[1][0] = 1.0; m[1][1] = 3.0; m[1][2] = 0.2;
    m[2][0] = 0.5; m[2][1] = 0.2; m[2][2] = 2.0;
    return m;
}

json matrixToJson(const Matrix& m) {
    json j;
    j["rows"]    = static_cast<int>(m.rows());
    j["columns"] = static_cast<int>(m.columns());
    json data = json::array();
    for (Size i = 0; i < m.rows(); ++i) {
        json row = json::array();
        for (Size jj = 0; jj < m.columns(); ++jj) {
            row.push_back(m[i][jj]);
        }
        data.push_back(row);
    }
    j["data"] = data;
    return j;
}

json arrayToJson(const std::vector<Real>& v) {
    json j = json::array();
    for (Real x : v) j.push_back(x);
    return j;
}

template <typename Container>
json containerToJson(const Container& c) {
    json j = json::array();
    for (Size i = 0; i < c.size(); ++i) j.push_back(c[i]);
    return j;
}

} // anonymous namespace

int main() {
    ReferenceWriter out("math/matrixutilities/pseudosqrt_spectral",
                        QL_VERSION,
                        "pseudosqrt_spectral_probe");

    // --- Case 1: Himalaya correlation, Schur eigen-decomposition ---
    {
        const Matrix corr = himalayaCorrelation();
        SymmetricSchurDecomposition jd(corr);

        json inputs;
        inputs["matrix"] = matrixToJson(corr);

        json expected;
        expected["eigenvalues"]  = containerToJson(jd.eigenvalues());
        expected["eigenvectors"] = matrixToJson(jd.eigenvectors());
        out.addCase("himalaya_4x4_schur", inputs, expected);
    }

    // --- Case 2: Himalaya correlation, PseudoSqrt(Spectral) ---
    {
        const Matrix corr = himalayaCorrelation();
        const Matrix sqrtCorr = pseudoSqrt(corr, SalvagingAlgorithm::Spectral);

        json inputs;
        inputs["matrix"]    = matrixToJson(corr);
        inputs["algorithm"] = "Spectral";

        json expected;
        expected["sqrt"] = matrixToJson(sqrtCorr);
        out.addCase("himalaya_4x4_spectral", inputs, expected);
    }

    // --- Case 3: 3x3 SPD, Schur ---
    {
        const Matrix m = small3x3();
        SymmetricSchurDecomposition jd(m);

        json inputs;
        inputs["matrix"] = matrixToJson(m);

        json expected;
        expected["eigenvalues"]  = containerToJson(jd.eigenvalues());
        expected["eigenvectors"] = matrixToJson(jd.eigenvectors());
        out.addCase("small_3x3_schur", inputs, expected);
    }

    // --- Case 4: 3x3 SPD, PseudoSqrt(Spectral) ---
    {
        const Matrix m = small3x3();
        const Matrix sqrtM = pseudoSqrt(m, SalvagingAlgorithm::Spectral);

        json inputs;
        inputs["matrix"]    = matrixToJson(m);
        inputs["algorithm"] = "Spectral";

        json expected;
        expected["sqrt"] = matrixToJson(sqrtM);
        out.addCase("small_3x3_spectral", inputs, expected);
    }

    // --- Case 5: Himalaya correlation, PseudoSqrt(None = Cholesky) ---
    // Confirms None / Cholesky path is unaffected by Schur ordering fix.
    {
        const Matrix corr = himalayaCorrelation();
        const Matrix sqrtCorr = pseudoSqrt(corr, SalvagingAlgorithm::None);

        json inputs;
        inputs["matrix"]    = matrixToJson(corr);
        inputs["algorithm"] = "None";

        json expected;
        expected["sqrt"] = matrixToJson(sqrtCorr);
        out.addCase("himalaya_4x4_cholesky", inputs, expected);
    }

    out.write();
    return 0;
}
