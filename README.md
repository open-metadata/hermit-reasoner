# HermiT, OpenMetadata build

HermiT is an OWL 2 DL reasoner by the University of Oxford, maintained
upstream at https://github.com/owlcs/hermit-reasoner. This repository,
https://github.com/open-metadata/hermit-reasoner, is OpenMetadata's build of
it: current upstream `master` on Java 21 and OWL API 5.5.1, tested in CI
against HermiT's own suite and the W3C OWL 2 conformance test cases. The
upstream README is [readme.txt](readme.txt).

## OpenMetadata build

| | |
|---|---|
| Upstream repository | https://github.com/owlcs/hermit-reasoner |
| Based on upstream commit | `df7e00b96def27855a2665b83f1a927ddfa18b08` (`master`, 2026-10-03, "Fix class expression simplification for OWL API 5 (#11)"), version `1.4.6.519-SNAPSHOT` |
| Maven coordinates | `org.open-metadata:hermit-reasoner:1.4.6.519-om.1` |
| Java | 21 (class files are Java 21) |
| OWL API | `net.sourceforge.owlapi:owlapi-distribution:5.5.1` |
| Licence | LGPL-3.0 or later, unchanged ([lgpl-3.0.txt](lgpl-3.0.txt), [gpl.txt](gpl.txt)) |

The coordinates are deliberately not `net.sourceforge.owlapi:org.semanticweb.hermit`,
so this build can never be mistaken for an official HermiT release. The
version is the upstream `master` version with `-om.N` in place of `-SNAPSHOT`.

### Patches on top of upstream

Modified by OpenMetadata, 2026-10-08. One line each; the first five are meant
for upstream.

1. Build for Java 21: `--release 21`, maven-compiler-plugin 3.13.0, maven-bundle-plugin 6.0.0 (bnd 3.2 fails on Java 21 class files).
2. NormalizationTest: expected strings updated to the OWL API 5.5.1 rendering (no space before `)`), fixing 7 failures.
3. OWLLinkTest: drop test 11, whose empty `owl:unionOf`/`owl:intersectionOf`/`owl:oneOf` OWL API 5.5.1 cannot parse.
4. ExpressionManager: a `DataIntersectionOf` that simplifies to no operands returns `rdfs:Literal` instead of throwing a `NullPointerException` under OWL API 5.5.1.
5. W3CConformanceTest: runs the pinned W3C OWL 2 approved test cases in `mvn verify`, with explicit expected failures.
6. OpenMetadata coordinates and this README (fork only).
7. GitHub Actions CI and release workflows (fork only).

## Building

```
mvn -B -ntp verify
```

This compiles, runs HermiT's test suite and the W3C OWL 2 conformance test
cases, and builds `target/hermit-reasoner-<version>.jar` and its sources jar.
The main jar is an OSGi bundle that also embeds a few dependency jars for
Protégé; on a plain classpath those nested jars are ignored and the
dependencies come from the POM.

## Tests

With JDK 21: 1,712 tests, 0 failures, 0 errors, 1 skipped
(`InversePropertiesBadInferenceTest`, marked `@Ignore("to fix")` upstream).

The W3C OWL 2 conformance test cases are the 355 approved cases from
https://www.w3.org/2009/11/owl-test/approved/all.rdf, pinned by SHA-256. Of
the 350 tests that apply to an OWL 2 DL reasoner:

| Test type | Run | Passed | Expected failures |
|---|---:|---:|---:|
| Consistency | 169 | 167 | 2 |
| Inconsistency | 97 | 97 | 0 |
| Positive entailment | 75 | 74 | 1 |
| Negative entailment | 9 | 8 | 1 |
| **Total** | **350** | **346** | **4** |

The four expected failures are WebOnt-description-logic-208 and -209, which
time out with HermiT's default configuration (disjunction learning on) and
finish in under a second without it. See
[src/test/resources/org/semanticweb/HermiT/owl_wg_tests/w3c/README.md](src/test/resources/org/semanticweb/HermiT/owl_wg_tests/w3c/README.md)
for the source, licence, scope, expected failures and the one case whose
running time varies between runs.

## Releases

Pushing a tag `v<version>` (for example `v1.4.6.519-om.1`, matching the POM
version) builds and tests the tag and publishes a GitHub Release with the
jar, the sources jar and `SHA256SUMS`.
