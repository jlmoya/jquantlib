# Phase 2 L5-C audit — experimental/{exoticoptions, copulas}

**Cluster:** L5-C per `phase2-L5-experimental-plan.md`.
**Scope:** C++ v1.42.1 (@099987f0) `ql/experimental/exoticoptions/*.hpp` plus `ql/experimental/copulas/*.hpp`.

## Headline

**Net new ports: 0.** Cluster is a no-op port-wise. All C++ headers either:
- already have a Java implementation (active classes), or
- are deprecated/empty stubs in v1.42.1 (the corresponding class was moved out of `experimental` in mainline C++ and already lives in a non-experimental Java package), or
- have no Java caller and are deprecated in C++.

## copulas

`ql/experimental/copulas/` does **not exist** in C++ v1.42.1. Nothing to port.

## exoticoptions — triage table

C++ header list comes from `ql/experimental/exoticoptions/*.hpp` (22 headers, excluding `all.hpp`).

| C++ header | C++ status v1.42.1 | Java status | Disposition |
|------------|--------------------|-------------|-------------|
| analyticholderextensibleoptionengine | DEPRECATED (empty) | `experimental.exoticoptions.AnalyticHolderExtensibleOptionEngine` (active) | SKIP — Java already ports the pre-deprecation impl |
| analyticpartialtimebarrieroptionengine | DEPRECATED (empty) | `pricingengines.barrier.AnalyticPartialTimeBarrierOptionEngine` (active) | SKIP — moved out of experimental in both langs |
| analyticpdfhestonengine | DEPRECATED (empty) | `pricingengines.vanilla.AnalyticPDFHestonEngine` (active) | SKIP — moved out of experimental in both langs |
| analytictwoassetbarrierengine | DEPRECATED (empty) | `pricingengines.barrier.AnalyticTwoAssetBarrierEngine` (active) | SKIP — moved out of experimental in both langs |
| analytictwoassetcorrelationengine | DEPRECATED (empty) | `experimental.exoticoptions.AnalyticTwoAssetCorrelationEngine` (active) | SKIP — Java already ports the pre-deprecation impl |
| analyticwriterextensibleoptionengine | DEPRECATED (empty) | `experimental.exoticoptions.AnalyticWriterExtensibleOptionEngine` (active) | SKIP — Java already ports the pre-deprecation impl |
| continuousarithmeticasianlevyengine | DEPRECATED (empty) | `pricingengines.asian.ContinuousArithmeticAsianLevyEngine` (active) | SKIP — moved out of experimental in both langs |
| continuousarithmeticasianvecerengine | ACTIVE | `experimental.exoticoptions.ContinuousArithmeticAsianVecerEngine` (active, 280 LOC) | SKIP — already ported |
| everestoption | ACTIVE | `experimental.exoticoptions.EverestOption` (active, 132 LOC) | SKIP — already ported |
| himalayaoption | ACTIVE | `experimental.exoticoptions.HimalayaOption` (active, 110 LOC) | SKIP — already ported |
| holderextensibleoption | DEPRECATED (empty) | `instruments.HolderExtensibleOption` (active) | SKIP — moved to mainline instruments in both langs |
| kirkspreadoptionengine | DEPRECATED (empty) | (none) | SKIP — no Java caller; C++ removed |
| mceverestengine | ACTIVE | `experimental.exoticoptions.MCEverestEngine` (active, 181 LOC) | SKIP — already ported |
| mchimalayaengine | ACTIVE | `experimental.exoticoptions.MCHimalayaEngine` (active, 167 LOC) | SKIP — already ported |
| mcpagodaengine | ACTIVE | `experimental.exoticoptions.MCPagodaEngine` (active, 165 LOC) | SKIP — already ported |
| pagodaoption | ACTIVE | `experimental.exoticoptions.PagodaOption` (active, 120 LOC) | SKIP — already ported |
| partialtimebarrieroption | DEPRECATED (empty) | `experimental.exoticoptions.PartialTimeBarrierOption` + `pricingengines.barrier.AnalyticPartialTimeBarrierOptionEngine` (active) | SKIP — Java already covers both deprecation paths |
| spreadoption | DEPRECATED (empty) | (none) | SKIP — no Java caller; C++ removed |
| twoassetbarrieroption | DEPRECATED (empty) | `instruments.TwoAssetBarrierOption` (active) | SKIP — moved to mainline instruments |
| twoassetcorrelationoption | DEPRECATED (empty) | `experimental.exoticoptions.TwoAssetCorrelationOption` (active) | SKIP — Java already ports the pre-deprecation impl |
| writerextensibleoption | DEPRECATED (empty) | `instruments.WriterExtensibleOption` (active) | SKIP — moved to mainline instruments |

### Java-only extras (no C++ counterpart in v1.42.1)

These exist in Java `experimental.exoticoptions` but their C++ headers are gone (moved out of `experimental` in mainline C++ to `ql/instruments/`):

- `AnalyticComplexChooserEngine` — C++ source is `ql/instruments/complexchooseroption.{hpp,cpp}` (mainline)
- `AnalyticSimpleChooserEngine` — C++ source is `ql/instruments/simplechooseroption.{hpp,cpp}` (mainline)
- `EverestMultiPathPricer`, `HimalayaMultiPathPricer`, `PagodaMultiPathPricer` — inner helpers of the MC engines
- `MakeMCEverestEngine`, `MakeMCHimalayaEngine`, `MakeMCPagodaEngine` — fluent builders
- `PartialBarrier`, `SoftBarrierOption` — auxiliary types referenced by barrier engines

Cross-package relocation is out of scope for L5-C (L5 charter: "port OR document SKIP" only).

## Verification

```
mvn -pl jquantlib test-compile   # BUILD SUCCESS
```

No production code changed in this commit; no functional regression possible.

## Conclusion

L5-C complete: 22 headers triaged, 0 ports needed.
