#!/usr/bin/env python3
"""Ground-truth coverage audit: every C++ QuantLib class vs every ported Java class.

Denominator = the pinned C++ submodule (ql/**/*.hpp), whatever it is pinned to.
             Currently v1.43 @ 6b57206e. The scan reads the submodule live, so this
             line is documentation only -- but keep it honest, it drifted once.
Match key  = class/struct NAME. QuantLib -> JQuantLib preserves PascalCase class
             names (only package paths differ). A C++ `class ClaytonCopula` is
             "ported" iff some Java file declares `class|interface|enum|record
             ClaytonCopula` (inner classes count -- Java often nests what C++
             keeps standalone, e.g. *Impl / Arguments / Results).

OVER-reports missing (nested helper structs, template tag types, detail:: helpers,
forward-decls that slip the filter); UNDER-reports when Java renames a class. It is
a reproducible proxy, not gospel -- but it is the denominator we drive to zero.

"Done" is defined mechanically: UNFLAGGED == 0, where every C++ class is either
(a) ported (a Java class of the same name exists), or (b) on the reviewed ALLOWLIST
below with a one-line rationale. The ALLOWLIST captures verified false-positives of
the exact-name match (case-renames, inner classes, C++-only template idioms,
traits/policy structs folded into Java generics, classes commented out upstream).
Full evidence record: docs/migration/gap-classification-ledger.md.

Run from the jquantlib repo root.
"""

from __future__ import annotations

import collections
import pathlib
import re

REPO = pathlib.Path(__file__).resolve().parent.parent
CPP = REPO / "migration-harness" / "cpp" / "quantlib" / "ql"
JAVA_ROOTS = [
    REPO / "jquantlib" / "src" / "main" / "java",
    REPO / "jquantlib-helpers" / "src" / "main" / "java",
    REPO / "jquantlib-contrib" / "src" / "main" / "java",
]

CPP_DECL = re.compile(r"^\s*(?:class|struct)\s+([A-Z][A-Za-z0-9_]*)\s*(?:[:{]|$)", re.MULTILINE)
JAVA_DECL = re.compile(
    r"\b(?:class|interface|enum|record)\s+([A-Z][A-Za-z0-9_]*)", re.MULTILINE
)

# ---------------------------------------------------------------------------
# Reviewed allowlist: C++ class names that are VERIFIED false-positives of the
# exact-name audit. Each was checked against the Java tree by reading the actual
# files (see docs/migration/gap-classification-ledger.md for per-class evidence).
# Key = C++ class name (the audit's missing key); value = one-line rationale.
# Categories: case-rename (Java has it, different case/spelling); inner-of (Java
# realizes it as an inner class/enum/static-factory/Arguments/Results); cpp-idiom
# (C++ template/CRTP/proxy/tag/policy/detail-helper with no Java runtime analog);
# traits-folded (C++ traits/policy struct folded into Java generics); and
# commented-out-upstream (the class is inside /* */ or // upstream).
#
# NOTHING is allowlisted silently: adding an entry here is a reviewed decision with
# a stated reason. The 42 genuinely-missing classes are NOT here -- they get ported.
# ---------------------------------------------------------------------------
ALLOWLIST = {
    # --- case-rename: Java has the class under a different case/spelling ---
    "Error": "case-rename -> lang.exceptions.LibraryException",
    "BFGS": "case-rename -> math.optimization.Bfgs",
    "SABR": "case-rename -> math.interpolations.Sabr",
    "BiCGstab": "case-rename -> math.matrixutilities.BiCGStab",
    "TARGET": "case-rename -> time.calendars.Target",
    "Solver1D": "case-rename -> math.AbstractSolver1D",
    "Bicubic": "case-rename -> math.interpolations.factories.BicubicSpline",
    "CubicNaturalSpline": "case-rename -> math.interpolations.NaturalCubicInterpolation",
    "MonotonicCubicNaturalSpline": "case-rename -> math.interpolations.MonotonicNaturalCubicInterpolation",
    "Parabolic": "case-rename -> math.interpolations.ParabolicCubicInterpolation",
    "MonotonicParabolic": "case-rename -> math.interpolations.MonotonicParabolicCubicInterpolation",
    "SyntheticCDO": "case-rename -> experimental.credit.SyntheticCdo",
    "MidPointCDOEngine": "case-rename -> experimental.credit.MidPointCdoEngine",
    "IntegralCDOEngine": "case-rename -> experimental.credit.IntegralCdoEngine",
    "GJRGARCHModel": "case-rename -> model.equity.GjrGarchModel",
    "GJRGARCHProcess": "case-rename -> processes.GjrGarchProcess",
    "MCEuropeanGJRGARCHEngine": "case-rename -> pricingengines.vanilla.MCEuropeanGjrGarchEngine",
    "VolatilityInterpolationSpecifierabcd": "case-rename -> VolatilityInterpolationSpecifierAbcd",
    "HestonSLVProcess": "case-rename -> experimental.processes.HestonStochasticLocalVolProcess",
    "Cdi": "case-rename -> indexes.ibor.Brlcdi",
    "IrrFinder": "case-rename -> IRRFinder (cashflows)",
    "Average": "case-rename -> instruments.AverageType (enum-holder struct)",
    "Barrier": "case-rename -> instruments.BarrierType (enum-holder struct)",
    "DoubleBarrier": "case-rename -> experimental.barrieroption.DoubleBarrierType (enum-holder)",
    "BlackCompoundingOvernightIndexedCouponPricer": "case-rename -> cashflow.BlackOvernightIndexedCouponPricer",
    "PiecewiseYoYOptionletVolatilityCurve": "case-rename -> experimental.inflation.PiecewiseYoYOptionletVolatility",
    "ZeroInflationTraits": "case-rename -> termstructures.inflation.InflationTraits",

    # --- inner-of: Java realizes it as an inner class/enum/factory/Arguments/Results ---
    "IntegrationFactory": "inner-of -> experimental.credit.LatentModel (static createLMIntegration)",
    "VectorIntegrator": "inner-of -> math.integrals.GaussianQuadMultidimIntegrator (inlined helper)",
    "Function": "inner-of -> cashflow.NumericHaganPricer (private functor)",
    "PriceHelper": "inner-of -> cashflow.LinearTsrPricer (private functor)",
    "VegaRatioHelper": "inner-of -> cashflow.LinearTsrPricer (private functor)",
    "ForwardOptionArguments": "inner-of -> instruments.ForwardVanillaOption (Arguments)",
    "QuantoOptionResults": "inner-of -> instruments.QuantoVanillaOption (Results)",
    "Thirty360_Impl": "inner-of -> daycounters.Thirty360 (DayCounter.Impl subclasses)",
    "SingleStepCalibrationResult": "inner-of -> AndreasenHugeVolatilityInterpl.StepResult",
    "FdmVPPStepConditionMesher": "inner-of -> experimental.finitedifferences.FdmVPPStepCondition.Mesher",
    "FdmVPPStepConditionParams": "inner-of -> experimental.finitedifferences.FdmVPPStepCondition.Params",
    "LinearFlatInterpolation": "inner-of -> experimental.shortrate.GeneralizedHullWhite (LinearFlatInterpolationAdapter)",
    "InterpolationData": "inner-of -> experimental.models.NormalCLVModel (private impl helper; NormalCLVModel tested)",

    # --- cpp-idiom: C++ template / CRTP / proxy / tag / policy / detail helper ---
    "RandomLM": "cpp-idiom CRTP base -> collapsed into RandomDefaultLM/RandomLossLM",
    "IntegrationBase": "cpp-idiom CRTP -> GaussianQuadLMIntegration + MultidimIntegralLMIntegration",
    "BaseCurrencyProxy": "cpp-idiom -> Money operator-assignment proxy struct (Java uses static field)",
    "ConversionTypeProxy": "cpp-idiom -> Money operator-assignment proxy struct (Java uses static field)",
    "AcyclicVisitor": "cpp-idiom -> patterns/visitor (Java util.Visitor/PolymorphicVisitor)",
    "CuriouslyRecurringTemplate": "cpp-idiom -> CRTP base (Java uses generics/virtual dispatch)",
    "Foo": "cpp-idiom -> doc-comment example class in patterns/singleton.hpp (not real)",
    "Proxy": "cpp-idiom -> patterns/observable inner shared_ptr-lifetime helper (Java GC)",
    "Singleton": "cpp-idiom -> CRTP singleton template (Java uses plain singletons/Settings)",
    "UpdateChecker": "cpp-idiom -> patterns/lazyobject RAII guard (no Java analog needed)",
    "Clone": "cpp-idiom -> utilities/clone deep-copy wrapper (Java reference semantics)",
    "Null": "cpp-idiom -> utilities/null type-specific sentinel (Java boxed/null)",
    "Tracing": "cpp-idiom -> utilities/tracing debug singleton (Java uses SLF4J)",
    "AbcdCoeffHolder": "cpp-idiom -> detail Impl base folded into AbcdInterpolation inner Impl",
    "BackwardflatLinear": "cpp-idiom -> template factory-traits functor (interpolator ported)",
    "BicubicSplineDerivatives": "cpp-idiom -> detail abstract accessor folded into BicubicSplineInterpolation",
    "CubicInterpolationBaseImpl": "cpp-idiom -> detail template Impl base folded into CubicInterpolation",
    "SABRWrapper": "cpp-idiom -> detail helper functor inlined into SABRInterpolation.SABRSpecs",
    "UpdatedYInterpolation": "cpp-idiom -> detail Impl mixin folded into LagrangeInterpolation",
    "EmptyArg": "cpp-idiom -> multicubicspline template termination tag (Java flat arrays)",
    "EmptyDim": "cpp-idiom -> multicubicspline template termination tag (Java flat arrays)",
    "EmptyRes": "cpp-idiom -> multicubicspline template termination tag (Java flat arrays)",
    "LinearFct": "cpp-idiom -> details template functor folded into LinearRegression",
    "LinearFcts": "cpp-idiom -> details template builder folded into LinearRegression",
    "EarlyExerciseTraits": "cpp-idiom -> montecarlo template traits struct (Java generics)",
    "OperatorTraits": "cpp-idiom -> finitedifferences template traits struct (Java inlines typedefs)",
    "FdmBatesSolver": "cpp-idiom -> thin LazyObject solver inlined into FdBatesVanillaEngine",
    "FdmCIRSolver": "cpp-idiom -> thin LazyObject solver inlined into FdCIRVanillaEngine",
    "FdmSimple3dExtOUJumpSolver": "cpp-idiom -> inlined into FdSimpleExtOUJumpSwingEngine (SwingOptionTest covers)",
    "DistributionRandomWalk": "cpp-idiom -> firefly template intermediate (Java concrete walks subclass RandomWalk)",
    "Ziggurat": "cpp-idiom -> RNG traits struct (generator ZigguratRng is ported)",
    "NoArbSabr": "cpp-idiom -> interpolation traits-factory tag (NoArbSabrInterpolation ported)",
    "SwaptionVolCubeNoArbSabrModel": "cpp-idiom -> template tag folded into NoArbSabrSwaptionVolatilityCube",
    "VannaVolga": "cpp-idiom -> interpolation traits-factory tag (VannaVolgaInterpolation ported)",
    "LinearFlat": "cpp-idiom -> interpolation traits-factory tag folded into GeneralizedHullWhite",
    "PrivateObserver": "cpp-idiom -> SabrSwaptionVolatilityCube rebuilds guard in performCalculations()",
    "XabrSwaptionVolatilityCube": "cpp-idiom CRTP base -> concrete Sabr/Zabr/NoArbSabr SwaptionVolatilityCube",

    # --- traits-folded: C++ traits/policy struct folded into Java generics/class ---
    "AffineHazardRate": "traits-folded -> experimental.credit.InterpolatedAffineHazardRateCurve",
    "InterpolatedCurve": "traits-folded -> termstructures.yieldcurves.Interpolated*Curve (own fields)",
    "RelativeDateBootstrapHelper": "traits-folded -> termstructures.yieldcurves.RelativeDateRateHelper",
    "SwaptionVolCubeSabrModel": "traits-folded -> SabrSwaptionVolatilityCube (policy struct)",
    "SwaptionVolCubeZabrModel": "traits-folded -> ZabrSwaptionVolatilityCube (policy struct)",
    "XabrModelTraits": "traits-folded -> the 3 concrete swaption vol cubes",
    "YoYInflationVolatilityTraits": "traits-folded -> experimental.inflation.PiecewiseYoYOptionletVolatility",
    "IborLegCashFlows": "traits-folded -> experimental.basismodels.SwaptionCashFlows (base fields collapsed)",
    "SwapCashFlows": "traits-folded -> experimental.basismodels.SwaptionCashFlows (base fields collapsed)",
    "MixedInterpolation": "traits-folded -> MixedLinearCubicInterpolation.Behavior enum-holder",

    # --- commented-out-upstream: inside /* */ or // upstream (never compiled) ---
    "RatchetPayoff_2": "commented-out-upstream -> instruments/stickyratchet.hpp:168-227 block comment",
    "StickyPayoff_2": "commented-out-upstream -> instruments/stickyratchet.hpp:168-227 block comment",
    "StickyRatchetPayoff": "commented-out-upstream -> instruments/stickyratchet.hpp:168-227 block comment",
    "ESFIntegrator": "commented-out-upstream -> credit/saddlepointlossmodel.hpp:355-379 block comment",
}


def cpp_subsystem(hpp: pathlib.Path) -> str:
    rel = hpp.relative_to(CPP).parts
    return rel[0] if len(rel) > 1 else "(root)"


def main() -> None:
    cpp: dict[str, str] = {}
    cpp_files = 0
    for hpp in CPP.rglob("*.hpp"):
        if hpp.name == "all.hpp":
            continue
        cpp_files += 1
        sub = cpp_subsystem(hpp)
        for name in CPP_DECL.findall(hpp.read_text(errors="ignore")):
            cpp.setdefault(name, sub)

    java: set[str] = set()
    for root in JAVA_ROOTS:
        if not root.exists():
            continue
        for jf in root.rglob("*.java"):
            java.update(JAVA_DECL.findall(jf.read_text(errors="ignore")))

    missing = {n: s for n, s in cpp.items() if n not in java}
    present = {n: s for n, s in cpp.items() if n in java}
    allowlisted = {n: s for n, s in missing.items() if n in ALLOWLIST}
    unflagged = {n: s for n, s in missing.items() if n not in ALLOWLIST}

    # Hygiene: an allowlist entry that no longer appears in `missing` is stale
    # (the class got ported, or the C++ name changed) -- surface it so the
    # allowlist never silently rots.
    stale = sorted(k for k in ALLOWLIST if k not in missing)

    total_by_sub = collections.Counter(cpp.values())
    unflagged_by_sub = collections.Counter(unflagged.values())

    print(f"C++ headers scanned (excl all.hpp): {cpp_files}")
    print(f"C++ distinct class/struct names:    {len(cpp)}")
    print(f"  ported (name found in Java):      {len(present)}")
    print(f"  missing (exact-name):             {len(missing)}")
    print(f"    - allowlisted (reviewed):       {len(allowlisted)}")
    print(f"    - UNFLAGGED (must reach 0):      {len(unflagged)}")
    print(f"  coverage (ported+allowlisted):    "
          f"{100 * (len(present) + len(allowlisted)) / len(cpp):.1f}%")
    if stale:
        print(f"  !! stale allowlist entries ({len(stale)}): {', '.join(stale)}")
    print()
    print(f"{'subsystem':<22}{'total':>7}{'unflagged':>11}")
    print("-" * 40)
    for sub in sorted(total_by_sub, key=lambda s: -unflagged_by_sub[s]):
        uf = unflagged_by_sub[sub]
        if uf:
            print(f"{sub:<22}{total_by_sub[sub]:>7}{uf:>11}")
    if not unflagged:
        print("(no subsystem has unflagged gaps)")

    out = REPO / "migration-harness" / "coverage-gaps.csv"
    with out.open("w") as f:
        f.write("subsystem,class,status\n")
        for n, s in sorted(allowlisted.items(), key=lambda kv: (kv[1], kv[0])):
            f.write(f"{s},{n},allowlisted\n")
        for n, s in sorted(unflagged.items(), key=lambda kv: (kv[1], kv[0])):
            f.write(f"{s},{n},UNFLAGGED\n")
    print()
    print(f"full gap list (allowlisted + unflagged) -> {out.relative_to(REPO)}")
    if not unflagged:
        print("\nDONE: 0 unflagged gaps. Every C++ class is ported or allowlisted-with-rationale.")


if __name__ == "__main__":
    main()
