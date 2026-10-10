package org.semanticweb.HermiT.owl_wg_tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.semanticweb.owlapi.util.OWLAPIStreamUtils.asSet;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.semanticweb.HermiT.Configuration;
import org.semanticweb.HermiT.EntailmentChecker;
import org.semanticweb.HermiT.Reasoner;
import org.semanticweb.HermiT.owl_wg_tests.WGTestDescriptor.TestType;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyIRIMapper;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.ReasonerInterruptedException;
import org.semanticweb.owlapi.search.EntitySearcher;

/**
 * Runs the approved test cases of the W3C OWL 2 test suite that apply to an
 * OWL 2 DL reasoner: every consistency, inconsistency, positive entailment and
 * negative entailment test whose species includes DL and whose semantics
 * includes the Direct Semantics. HermiT runs with its default configuration.
 * <p>
 * The test cases are an unmodified copy of
 * https://www.w3.org/2009/11/owl-test/approved/all.rdf, pinned by SHA-256;
 * see w3c/README.md next to it. Imports resolve to the ontologies that the
 * suite itself ships (test:importedOntology), and loading any other IRI fails,
 * so the suite never uses the network.
 * <p>
 * Cases that HermiT is known to fail are listed in {@link #EXPECTED_FAILURES}
 * with a reason. Such a case still runs and must still fail; when it starts to
 * pass, the test fails until the entry is removed. Cases whose running time
 * varies widely between runs are listed in {@link #RETRY_ON_TIMEOUT}; only
 * they are run again after a timeout, and the summary reports every retry.
 */
@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class W3CConformanceTest {
    static final String TEST_CASES = "w3c/approved-all.rdf";
    static final String TEST_CASES_SHA256 = "6236dab71c2264903131042808e79e4aa41afab528f7cf6e633d2e3926b89b04";
    /**
     * Per attempt. Apart from the cases below, every case finished in under a
     * second in each of 30 runs. Override with -Dhermit.conformance.timeoutMillis.
     */
    static final int TIMEOUT_MILLIS = Integer.getInteger("hermit.conformance.timeoutMillis", 20_000).intValue();
    static final int ATTEMPTS_ON_TIMEOUT = 3;

    /** Keyed by "identifier test-type", e.g. "WebOnt-I5.8-008 consistency". */
    static final Map<String, String> EXPECTED_FAILURES = new TreeMap<>();
    static {
        String disjunctionLearning = "times out with the default configuration (disjunction learning on): it did not"
                + " finish within 20 minutes with OWL API 5.5.1 on Java 21 or with OWL API 5.1.9 on Java 11. With"
                + " Configuration.useDisjunctionLearning=false it finishes in under a second, which is how"
                + " HermiT's own ConsistencyTest and EntailmentTest run it";
        EXPECTED_FAILURES.put("WebOnt-description-logic-208 consistency", disjunctionLearning);
        EXPECTED_FAILURES.put("WebOnt-description-logic-208 positive entailment", disjunctionLearning);
        EXPECTED_FAILURES.put("WebOnt-description-logic-209 consistency", disjunctionLearning);
        EXPECTED_FAILURES.put("WebOnt-description-logic-209 negative entailment", disjunctionLearning);
    }

    /** Keyed like {@link #EXPECTED_FAILURES}; the value says why a timeout is retried. */
    static final Map<String, String> RETRY_ON_TIMEOUT = new TreeMap<>();
    static {
        String heavyTail = "running time differs between runs of the same input: over 100 runs of the consistency"
                + " check, median 0.06 s, 90th percentile 1.4 s, 99th percentile 36 s, and one run over 120 s."
                + " OWL API 5.1.9 on Java 11 shows the same spread";
        RETRY_ON_TIMEOUT.put("WebOnt-description-logic-661 consistency", heavyTail);
        RETRY_ON_TIMEOUT.put("WebOnt-description-logic-661 positive entailment", heavyTail);
    }

    static final TestType[] REASONING_TEST_TYPES = { TestType.CONSISTENCY, TestType.INCONSISTENCY,
            TestType.POSITIVE_ENTAILMENT, TestType.NEGATIVE_ENTAILMENT };

    private static final Map<TestType, int[]> RESULTS = new EnumMap<>(TestType.class);
    private static final Map<String, String> UNEXPECTED_FAILURES = new TreeMap<>();
    private static final Map<String, String> RETRIED = new TreeMap<>();
    private static final Map<String, Integer> NOT_APPLICABLE = new TreeMap<>();
    private static final Map<IRI, IRI> IMPORTED_ONTOLOGY_DOCUMENTS = new HashMap<>();
    private static int s_testCases;

    private final String m_key;
    private final WGTestDescriptor m_testCase;
    private final TestType m_testType;

    public W3CConformanceTest(String key, WGTestDescriptor testCase, TestType testType) {
        m_key = key;
        m_testCase = testCase;
        m_testType = testType;
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> testCases() throws Exception {
        verifyPinnedTestCases();
        WGTestRegistry registry = new WGTestRegistry(TEST_CASES);
        writeImportedOntologies(registry.m_testContainer);
        List<Object[]> parameters = new ArrayList<>();
        for (WGTestDescriptor testCase : registry.getTestDescriptors()) {
            s_testCases++;
            if (testCase.status != WGTestDescriptor.Status.APPROVED)
                throw new IllegalStateException(testCase.identifier + " is not an approved test case");
            for (TestType testType : REASONING_TEST_TYPES) {
                if (!testCase.testTypes.contains(testType))
                    continue;
                if (testCase.isDLTest())
                    parameters.add(new Object[] { testCase.identifier + " " + label(testType), testCase, testType });
                else
                    NOT_APPLICABLE.merge(label(testType) + " tests of OWL 2 Full ontologies (species FULL only)",
                            Integer.valueOf(1), Integer::sum);
            }
            if (testCase.testTypes.contains(TestType.PROFILE_IDENTIFICATION))
                NOT_APPLICABLE.merge("profile identification tests (syntax, not reasoning)", Integer.valueOf(1),
                        Integer::sum);
        }
        for (Map<String, String> listed : Arrays.asList(EXPECTED_FAILURES, RETRY_ON_TIMEOUT))
            for (String key : listed.keySet())
                if (parameters.stream().noneMatch(p -> key.equals(p[0])))
                    throw new IllegalStateException(key + " is listed but is not an applicable case");
        return parameters;
    }

    @Test
    public void test() throws Throwable {
        Throwable failure;
        int attempt = 0;
        do {
            attempt++;
            failure = null;
            try {
                runTestCase();
            } catch (Throwable t) {
                failure = t;
            }
        } while (failure instanceof TimeoutError && RETRY_ON_TIMEOUT.containsKey(m_key)
                && attempt < ATTEMPTS_ON_TIMEOUT);
        if (attempt > 1)
            synchronized (RESULTS) {
                RETRIED.put(m_key, (failure == null ? "passed on attempt " : "failed on attempt ") + attempt);
            }
        String expectedFailureReason = EXPECTED_FAILURES.get(m_key);
        record(failure, expectedFailureReason);
        if (expectedFailureReason == null) {
            if (failure != null)
                throw failure;
        } else if (failure == null)
            fail(m_key + " passed but is listed in EXPECTED_FAILURES (" + expectedFailureReason
                    + "); remove the entry");
    }

    protected void runTestCase() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        manager.getIRIMappers().set(new SuiteImportsMapper());
        OWLOntology premise = m_testCase.getPremiseOntology(manager);
        Configuration configuration = new Configuration();
        configuration.throwInconsistentOntologyException = false;
        Reasoner reasoner = new Reasoner(configuration, premise);
        AbstractTest.InterruptTimer timer = new AbstractTest.InterruptTimer(TIMEOUT_MILLIS, reasoner);
        timer.start();
        try {
            switch (m_testType) {
            case CONSISTENCY:
            case INCONSISTENCY:
                assertEquals("consistent", Boolean.valueOf(m_testType == TestType.CONSISTENCY),
                        Boolean.valueOf(reasoner.isConsistent()));
                break;
            case POSITIVE_ENTAILMENT:
            case NEGATIVE_ENTAILMENT:
                boolean positive = m_testType == TestType.POSITIVE_ENTAILMENT;
                // A separate manager, because some premises and conclusions share an ontology IRI.
                OWLOntology conclusion = m_testCase.getConclusionOntology(OWLManager.createOWLOntologyManager(),
                        positive);
                EntailmentChecker checker = new EntailmentChecker(reasoner, manager.getOWLDataFactory());
                assertEquals("entailed", Boolean.valueOf(positive),
                        Boolean.valueOf(checker.entails(asSet(conclusion.logicalAxioms()))));
                break;
            default:
                throw new IllegalStateException("Unexpected test type " + m_testType);
            }
        } catch (ReasonerInterruptedException e) {
            throw new TimeoutError(e);
        } finally {
            timer.stopTiming();
            timer.join();
            reasoner.dispose();
        }
    }

    /**
     * Writes every ontology the suite ships for import to a temporary file. The
     * mapping is shared by all cases because imports can be cyclic: the
     * ontology that WebOnt-miscellaneous-001 imports imports the premise of
     * WebOnt-miscellaneous-001, which the suite ships for
     * WebOnt-miscellaneous-002.
     */
    static void writeImportedOntologies(OWLOntology testContainer) throws Exception {
        OWLDataFactory df = testContainer.getOWLOntologyManager().getOWLDataFactory();
        OWLObjectProperty importedOntologyIRI = df
                .getOWLObjectProperty(IRI.create(WGTestRegistry.URI_BASE + "importedOntologyIRI"));
        for (OWLObjectPropertyAssertionAxiom assertion : asSet(testContainer
                .axioms(AxiomType.OBJECT_PROPERTY_ASSERTION).filter(a -> importedOntologyIRI.equals(a.getProperty())))) {
            IRI ontologyIRI = assertion.getObject().asOWLNamedIndividual().getIRI();
            String document = null;
            for (String property : new String[] { "fsInputOntology", "owlXmlInputOntology", "rdfXmlInputOntology" }) {
                document = EntitySearcher
                        .getDataPropertyValues(assertion.getSubject(),
                                df.getOWLDataProperty(IRI.create(WGTestRegistry.URI_BASE + property)), testContainer)
                        .map(OWLLiteral::getLiteral).findFirst().orElse(null);
                if (document != null)
                    break;
            }
            if (document == null)
                throw new InvalidWGTestException("Imported ontology " + ontologyIRI + " has no content");
            File file = File.createTempFile("w3c-owl2-import", ".owl");
            file.deleteOnExit();
            Files.write(file.toPath(), document.getBytes(StandardCharsets.UTF_8));
            if (IMPORTED_ONTOLOGY_DOCUMENTS.put(ontologyIRI, IRI.create(file)) != null)
                throw new InvalidWGTestException("Imported ontology " + ontologyIRI + " is given twice");
        }
    }

    protected void record(Throwable failure, String expectedFailureReason) {
        synchronized (RESULTS) {
            int[] counts = RESULTS.computeIfAbsent(m_testType, t -> new int[3]);
            if (failure == null)
                counts[0]++;
            else if (expectedFailureReason != null)
                counts[1]++;
            else {
                counts[2]++;
                UNEXPECTED_FAILURES.put(m_key, String.valueOf(failure));
            }
        }
    }

    @AfterClass
    public static void printSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("W3C OWL 2 conformance: ").append(s_testCases).append(" approved test cases in ")
                .append(TEST_CASES).append(", timeout ").append(TIMEOUT_MILLIS).append(" ms per case\n");
        String row = "  %-20s %5s %7s %9s %11s%n";
        summary.append(String.format(row, "test type", "run", "passed", "expected", "unexpected"));
        summary.append(String.format(row, "", "", "", "failures", "failures"));
        int[] total = new int[3];
        for (TestType testType : REASONING_TEST_TYPES) {
            int[] counts = RESULTS.getOrDefault(testType, new int[3]);
            summary.append(String.format(row, label(testType), String.valueOf(counts[0] + counts[1] + counts[2]),
                    String.valueOf(counts[0]), String.valueOf(counts[1]), String.valueOf(counts[2])));
            for (int i = 0; i < 3; i++)
                total[i] += counts[i];
        }
        summary.append(String.format(row, "total", String.valueOf(total[0] + total[1] + total[2]),
                String.valueOf(total[0]), String.valueOf(total[1]), String.valueOf(total[2])));
        summary.append("  Not run, because they do not apply to an OWL 2 DL reasoner:\n");
        NOT_APPLICABLE.forEach((what, count) -> summary.append("    ").append(count).append(' ').append(what)
                .append('\n'));
        summary.append("  Expected failures:").append(EXPECTED_FAILURES.isEmpty() ? " none" : "").append('\n');
        EXPECTED_FAILURES.forEach((key, reason) -> summary.append("    ").append(key).append(": ").append(reason)
                .append('\n'));
        RETRIED.forEach((key, outcome) -> summary.append("  Retried after a timeout: ").append(key).append(", ")
                .append(outcome).append(" (").append(RETRY_ON_TIMEOUT.get(key)).append(")\n"));
        UNEXPECTED_FAILURES.forEach((key, failure) -> summary.append("  UNEXPECTED FAILURE ").append(key)
                .append(": ").append(failure).append('\n'));
        System.out.print(summary);
    }

    static String label(TestType testType) {
        switch (testType) {
        case CONSISTENCY:
            return "consistency";
        case INCONSISTENCY:
            return "inconsistency";
        case POSITIVE_ENTAILMENT:
            return "positive entailment";
        case NEGATIVE_ENTAILMENT:
            return "negative entailment";
        default:
            return testType.toString();
        }
    }

    static void verifyPinnedTestCases() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = W3CConformanceTest.class.getResourceAsStream(TEST_CASES)) {
            if (in == null)
                throw new IllegalStateException("Missing test resource " + TEST_CASES);
            byte[] buffer = new byte[65536];
            for (int read = in.read(buffer); read != -1; read = in.read(buffer))
                digest.update(buffer, 0, read);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest())
            hex.append(String.format("%02x", Integer.valueOf(b & 0xff)));
        if (!TEST_CASES_SHA256.equals(hex.toString()))
            throw new IllegalStateException(TEST_CASES + " has SHA-256 " + hex + " instead of the pinned "
                    + TEST_CASES_SHA256 + "; it must stay identical to the W3C download");
    }

    protected static class TimeoutError extends AssertionError {
        private static final long serialVersionUID = 1L;

        protected TimeoutError(ReasonerInterruptedException cause) {
            super("timed out after " + TIMEOUT_MILLIS + " ms", cause);
        }
    }

    /** Resolves imports to the ontologies the suite ships; any other IRI would be a network fetch, so it fails. */
    protected static class SuiteImportsMapper implements OWLOntologyIRIMapper {
        private static final long serialVersionUID = 1L;

        @Override
        public IRI getDocumentIRI(IRI ontologyIRI) {
            IRI document = IMPORTED_ONTOLOGY_DOCUMENTS.get(ontologyIRI);
            if (document == null)
                throw new IllegalStateException("The conformance tests do not load ontologies from the network: "
                        + ontologyIRI);
            return document;
        }
    }
}
