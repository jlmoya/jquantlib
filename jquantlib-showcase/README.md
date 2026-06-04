# JQuantLib Showcase

An interactive **Spring Boot** web application that demonstrates the
[JQuantLib](http://www.jquantlib.org) quantitative-finance library — the pure-Java port of
[QuantLib](https://www.quantlib.org) v1.42.1 — in the browser.

Every number rendered by this app is computed **live** by JQuantLib: option prices and Greeks,
Monte Carlo simulations, bond analytics, yield curves and market calendars. It also runs the
bundled `jquantlib-samples` example programs on demand and streams their output to the page.

> Java 25 · Spring Boot 4.0.6 · Thymeleaf · Chart.js (vendored, no CDN)

---

## Running it

The app depends on the `org.jquantlib:jquantlib` and `org.jquantlib:jquantlib-samples` artifacts.
If they aren't already in your local Maven repository, install them once from the repo root:

```bash
# from the repository root (…/jquantlib)
mvn -pl jquantlib,jquantlib-helpers,jquantlib-samples -am install -DskipTests
```

Then start the showcase:

```bash
cd jquantlib-showcase
mvn spring-boot:run
```

…or build a self-contained jar and run it:

```bash
cd jquantlib-showcase
mvn -DskipTests package
java -jar target/jquantlib-showcase-1.0.0.jar
```

Open **<http://localhost:8080>**.

---

## What it demonstrates

| Demo | Library features exercised |
|------|----------------------------|
| **Options & Greeks** | `EuropeanOption`, `BlackScholesMertonProcess`, `AnalyticEuropeanEngine`; live Δ/Γ/vega/θ/ρ; a 7-engine cross-check (analytic, integral, binomial CRR & Leisen-Reimer, finite differences, Monte Carlo pseudo-random & Sobol). |
| **American Options** | `AmericanExercise`; Barone-Adesi/Whaley, Bjerksund/Stensland, Ju, binomial and FD engines; early-exercise premium vs the European value. |
| **Monte Carlo** | `MCEuropeanEngine` & `MCEuropeanEngineLowDiscrepancy`; convergence to the analytic price with a 95% confidence band; pseudo-random vs Sobol error decay (log–log). |
| **Implied Volatility** | `VanillaOption.impliedVolatility(...)` solver; price-vs-volatility curve. |
| **Barrier Options** | `BarrierOption` + `AnalyticBarrierEngine`; knock-in/out (down/up) vs the vanilla value. |
| **Asian Options** | `DiscreteAveragingAsianOption` + analytic discrete geometric-average engine. |
| **Dividend Options** | `jquantlib-helpers` binomial (CRR) dividend engines — discrete cash dividends (European/American) vs none, with Greeks. |
| **Fixed-Rate Bonds** | `FixedRateBond`, `Schedule`, `DiscountingBondEngine`; clean/dirty price, accrued, yield, cash flows; price-vs-yield curve. |
| **Yield Curve** | `InterpolatedZeroCurve<Linear>`; discount factors, zero and forward rates across 30 years. |
| **Calendars & Schedules** | `Target` / `UnitedStates` / `UnitedKingdom` / `Japan` calendars; holidays, business-day counts, conventions, `Schedule` generation. |
| **Sample Programs** | Runs the `jquantlib-samples` programs live — Equity Options, Bonds, **Interest-Rate Swap**, FRA, Repo, Convertible Bonds, **Discrete Hedging**, **Bermudan Swaption**, term structures, processes, calendars, dates. |

---

## How it is built

```
jquantlib-showcase/
├── pom.xml                         ← parent = spring-boot-starter-parent 4.0.6; deps: jquantlib + jquantlib-samples + jquantlib-helpers
└── src/main/
    ├── java/org/jquantlib/showcase/
    │   ├── JQuantLibShowcaseApplication.java
    │   ├── service/   ← thin wrappers that call JQuantLib (OptionPricingService, ExoticOptionService,
    │   │                DividendOptionService, BondService, YieldCurveService, CalendarService,
    │   │                SamplesService) + Quant (eval-date guard)
    │   ├── dto/       ← Java records serialised to JSON
    │   └── web/       ← PageController (Thymeleaf pages) + *ApiController (JSON endpoints)
    └── resources/
        ├── templates/ ← one Thymeleaf page per demo + fragments/common.html
        └── static/    ← app.css, app.js, and vendored bootstrap + chart.js
```

**Interaction model.** Each page is a thin HTML shell; its JavaScript calls a JSON endpoint
(e.g. `GET /api/option/european?...`) backed by JQuantLib and renders the results and charts
client-side. Open your browser's network tab to see the library's raw output.

**Thread-safety.** JQuantLib's `Settings.evaluationDate` is process-global mutable state, so every
pricing call runs through `Quant.withEvaluationDate(...)`, which serialises the global-state
mutation and the calculation behind a lock.

**Self-contained front end.** Bootstrap and Chart.js are vendored under `static/vendor/`, so the
app needs no internet access at runtime and pulls no third-party scripts.
