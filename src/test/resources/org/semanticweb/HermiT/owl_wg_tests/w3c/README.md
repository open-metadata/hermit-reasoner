# W3C OWL 2 conformance test cases

`approved-all.rdf` holds the approved test cases of the W3C OWL 2 test suite,
copied byte for byte. `W3CConformanceTest` runs them in `mvn verify`.

| | |
|---|---|
| Source | https://www.w3.org/2009/11/owl-test/approved/all.rdf |
| Last-Modified (HTTP header) | Wed, 18 Nov 2009 15:49:36 GMT |
| Downloaded | 2026-10-08 |
| Size | 2,100,744 bytes |
| SHA-256 | `6236dab71c2264903131042808e79e4aa41afab528f7cf6e633d2e3926b89b04` |
| Licence | W3C Software and Document License, see `LICENSE-W3C.txt` |

The OWL 2 Conformance recommendation
(https://www.w3.org/TR/owl2-conformance/) names
https://www.w3.org/2009/owl-test-cases as the repository of approved test
cases; that page points to the archive at https://www.w3.org/2009/11/owl-test/,
and `approved/all.rdf` is the archive's export of the 355 cases whose status is
Approved. The archive has no licence statement of its own. W3C test suites are
also offered under the W3C test suite licence and the W3C 3-clause BSD licence
(https://www.w3.org/copyright/test-suites-licenses/).

`W3CConformanceTest` checks the SHA-256 before it runs anything, and
`.gitattributes` keeps Git from changing line endings, so the file cannot drift
from the download. To move to a new download, replace the file and update
`TEST_CASES_SHA256` and this README.

## What runs

HermiT is an OWL 2 DL reasoner for the Direct Semantics, so the test runs every
consistency, inconsistency, positive entailment and negative entailment test of
a case whose species includes DL and whose semantics includes DIRECT, with
HermiT's default configuration:

| Test type | Cases run |
|---|---:|
| Consistency | 169 |
| Inconsistency | 97 |
| Positive entailment | 75 |
| Negative entailment | 9 |
| **Total** | **350** |

Not run, because they do not apply to an OWL 2 DL reasoner: the 68 consistency,
21 inconsistency and 68 positive entailment tests of the 89 cases whose species
is FULL only (OWL 2 Full ontologies), and the profile identification test that
every case carries, which checks syntax rather than reasoning.

Imports resolve to the ontologies that the suite itself ships
(`test:importedOntology`); loading any other IRI fails, so the test never uses
the network.

Each attempt has a 20 second timeout (`-Dhermit.conformance.timeoutMillis`
overrides it). All cases except the ones below finished in under a second in
each of 30 repeated runs.

## Expected failures

`EXPECTED_FAILURES` lists the cases HermiT is known to fail, with the reason.
They still run and must still fail; a case that starts to pass fails the build
until its entry is removed.

| Case | Reason |
|---|---|
| WebOnt-description-logic-208 consistency | Times out with the default configuration (disjunction learning on); see below |
| WebOnt-description-logic-208 positive entailment | Same |
| WebOnt-description-logic-209 consistency | Same |
| WebOnt-description-logic-209 negative entailment | Same |

With disjunction learning on, the consistency check of either premise did not
finish within 20 minutes, with OWL API 5.5.1 on Java 21 and with OWL API 5.1.9
on Java 11. With `Configuration.useDisjunctionLearning = false` it finishes in
under a second; HermiT's own `ConsistencyTest` and `EntailmentTest` have always
run these two cases that way.

## Retried after a timeout

`RETRY_ON_TIMEOUT` lists the cases whose running time varies widely between
runs of the same input. Only these are run again after a timeout, at most three
attempts, and the summary that the test prints names every retry.

| Case | Observed running time |
|---|---|
| WebOnt-description-logic-661 consistency | 100 runs: median 0.06 s, 90th percentile 1.4 s, 99th percentile 36 s, one run over 120 s |
| WebOnt-description-logic-661 positive entailment | Same premise; one of 30 runs over 60 s |

OWL API 5.1.9 on Java 11 shows the same spread (99th percentile 22 s, slowest
78 s), so it predates this build.

## Relation to `../ontologies/all.rdf`

`ConsistencyTest` and `EntailmentTest` are HermiT's older harness. They read
`ontologies/all.rdf`, an export of the OWL working group wiki of unknown date
that also contains proposed cases and cases without a status. Its 355 approved
cases match `approved-all.rdf` except five: Chain2trans, Consistent-but-all-unsat
and Qualified-cardinality-boolean have different ontology IRIs, and
Somevaluesfrom2bnode and WebOnt-someValuesFrom-003 lack their EL profile
annotation.
