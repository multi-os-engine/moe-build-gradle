/*
Copyright 2014-2016 Intel Corporation

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package org.moe.gradle.util

import org.apache.commons.lang.StringEscapeUtils

import java.math.RoundingMode

/**
 * Test collector for JUnit tests.
 */
public class JUnitTestCollector {

    /**
     * JUnit test message IDs.
     */
    private class MessageIDs {

        /**
         * Test run start message ID.
         */
        public static final String TEST_RUN_START = "%TESTC  :";

        /**
         * Test definition message ID.
         */
        public static final String TEST_DEFINE = "%TESTD  :";

        /**
         * Test start message ID.
         */
        public static final String TEST_START = "%TESTS  :";

        /**
         * Test end message ID.
         */
        public static final String TEST_END = "%TESTE  :";

        /**
         * Test error message ID.
         */
        public static final String TEST_ERROR = "%ERROR  :";

        /**
         * Test failed message ID.
         */
        public static final String TEST_FAILED = "%FAILED :";

        /**
         * Test ignored message ID.
         */
        public static final String TEST_IGNORED = "%IGNORED:";

        /**
         * Test run end message ID.
         */
        public static final String TEST_RUN_END = "%RUNTIME:";
    }

    /**
     * Number of tests.
     */
    private int numTests
    /**
     * Number of started tests.
     */
    private int numStarted
    /**
     * Number of failed tests.
     */
    private int numFailures
    /**
     * Number of tests with errors.
     */
    private int numErrors
    /**
     * Number of ignored tests.
     */
    private int numIgnored
    /**
     * Test received last message flag.
     */
    private boolean hasEnded

    /**
     * Test suites.
     */
    private final HashMap<String, TestSuite> suites = new HashMap<>();

    /**
     * Class representing a test suite.
     */
    private static class TestSuite {
        /**
         * Name of the suite.
         */
        String name
        /**
         * Suite execution time.
         */
        long time
        /**
         * Test cases in suite.
         */
        final HashMap<String, TestCase> cases = new HashMap<>()
    }

    /**
     * Class representing a test suite.
     */
    private static class TestCase {
        /**
         * Name of the case.
         */
        String name
        /**
         * Case execution time.
         */
        long time
        /**
         * Failure string or null.
         */
        StringBuilder failure
        /**
         * Case test was started.
         */
        boolean wasStarted = false
        /**
         * Case test was ignored.
         */
        boolean wasIgnored = false
    }

    private TestCase currentTest

    /**
     * Append a line to the test.
     * @param line line to append
     */
    public void appendLine(String line) {
        if (line == null || line.length() == 0) {
            return
        }

        if (line.startsWith(MessageIDs.TEST_RUN_START)) {

        } else if (line.startsWith(MessageIDs.TEST_DEFINE)) {
            getTestCase(line.substring(9))
            ++numTests

        } else if (line.startsWith(MessageIDs.TEST_START)) {
            currentTest = getTestCase(line.substring(9))
            currentTest.time = new Date().time
            currentTest.wasStarted = true
            ++numStarted

        } else if (line.startsWith(MessageIDs.TEST_END)) {
            currentTest.time = new Date().time - currentTest.time
            currentTest = null

        } else if (line.startsWith(MessageIDs.TEST_ERROR)) {
            // JUnit 4+ simplifies errors to failures
            ++numErrors

        } else if (line.startsWith(MessageIDs.TEST_FAILED)) {
            if (currentTest != null) {
                currentTest.failure = new StringBuilder(line.substring(9))
                ++numFailures
            }

        } else if (line.startsWith(MessageIDs.TEST_IGNORED)) {
            def testCase = getTestCase(line.substring(9))
            testCase.wasIgnored = true
            ++numIgnored

        } else if (line.startsWith(MessageIDs.TEST_RUN_END)) {
            hasEnded = true

            suites.each { suiteEntry ->
                TestSuite tSuite = suiteEntry.value
                tSuite.time = 0
                tSuite.cases.each { caseEntry ->
                    TestCase tCase = caseEntry.value
                    tSuite.time += tCase.time
                }
            }
        } else {
            if (currentTest != null && currentTest.failure != null) {
                currentTest.failure.append("\n").append(line)
            }
        }
    }

    /**
     * Return a test case for the specified name
     * @param testCase Test case name
     * @return test case object
     */
    private TestCase getTestCase(String testCase) {
        try {
            int idx = testCase.lastIndexOf('-');
            def suiteName = testCase.substring(0, idx)
            def caseName = testCase.substring(idx + 1)

            TestSuite tSuite = suites.get(suiteName)
            if (tSuite == null) {
                tSuite = new TestSuite()
                tSuite.name = suiteName
                suites.put(suiteName, tSuite)
            }

            TestCase tCase = tSuite.cases.get(caseName)
            if (tCase == null) {
                tCase = new TestCase()
                tCase.name = caseName
                tSuite.cases.put(caseName, tCase)
            }

            return tCase
        } catch (Exception ex) {
            // Ignore
        }
        return new TestCase()
    }

    private static String getXMLString(String str) {
        return StringEscapeUtils.escapeXml(str);
    }

    public String getXMLReport() {
        StringBuilder report = new StringBuilder(16 * 1024)

        report.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        report.append("<testrun " +
                "tests=\"$numTests\" " +
                "started=\"$numStarted\" " +
                "failures=\"$numFailures\" " +
                "errors=\"$numErrors\" " +
                "ignored=\"$numIgnored\">\n")

        suites.each { suiteEntry ->
            TestSuite tSuite = suiteEntry.value
            report.append("    ")
            report.append("<testsuite name=\"${getXMLString(tSuite.name)}\" time=\"${getAsSeconds(tSuite.time)}\">\n")
            tSuite.cases.each { caseEntry ->
                TestCase tCase = caseEntry.value
                report.append("        ")
                report.append("<testcase name=\"${getXMLString(tCase.name)}\" ")
                report.append("classname=\"${getXMLString(tSuite.name)}\" ")
                report.append("time=\"${getAsSeconds(tCase.time)}\" ")

                if (tCase.wasIgnored) {
                    report.append("ignored=\"true\" ")
                }

                if (tCase.failure != null) {
                    report.append(">\n");
                    report.append("            ")
                    report.append("<failure>");
                    report.append(getXMLString(tCase.failure.toString()));
                    report.append("\n            ")
                    report.append("</failure>\n");


                    report.append("        ")
                    report.append("</testcase>\n");

                } else {
                    report.append("/>\n")
                }
            }
            report.append("    ")
            report.append("</testsuite>\n")
        }

        report.append("</testrun>\n")
    }

    private static String getHTMLString(String str) {
        return StringEscapeUtils.escapeHtml(str);
    }

    public String getHTMLReport(String targetName) {
        StringBuilder report = new StringBuilder(16 * 1024)

        report.append("""<!DOCTYPE html>
<html>
<head>
<title>JUnit Test Results</title>
\t<style>
body { background-color: #FFFFFF; }
h1 { margin-left: 20px; }
h2 { margin-left: 30px; }
table { border-spacing: 0; width: 98%; margin-left: auto; margin-right: auto; border: 1px solid black; }
table > * > tr > td { padding: 2px 5px; border-left: 1px solid black; border-bottom: 1px solid black; }
table > * > tr > td:first-child { border-left: 0px solid black; }
table > thead { background-color: #6D6D6D; color: #FFFFFF; }
table > thead > tr > td { font-weight: bold; font-size: 105%; letter-spacing: 0.05em; }
table > tbody > tr:last-child > td { border-bottom: 0px solid black; }
.cases { margin: 20px; }
.failure { padding: 20px; }

.color-ok { background-color: #B7E57F; }
.color-warn { background-color: #E4DF7C; }
.color-fail { background-color: #E57F84; }
.color-ignore { background-color: #DCDCDC; }

.even { background-color: #FFFFFF; }
.odd { background-color: #D7D7D7; }
\t</style>
\t<script type="text/javascript">
function toggleCell(name) {
\tvar elem = document.getElementById(name);
\tif (elem.style.display == 'none') {
\t\telem.style.display = 'table-row'
\t} else {
\t\telem.style.display = 'none'
\t}
}
function hideAllSuites() {
\tvar classes = document.querySelectorAll('.suite-cases');
\tfor (var i = 0; i < classes.length; i++) { classes[i].style.display = 'none'; }
}
\t</script>
</head>
<body onload="hideAllSuites()">
""")
        report.append("<h1>Test report for &lt;${getHTMLString(targetName)}&gt;</h1>\n")
        report.append("""<h2>Summary</h2>
<table class="summary">
\t<thead>
\t\t<tr><td>Tests</td><td>Started</td><td>Failures</td><td>Errors</td><td>Ignored</td></tr>
\t</thead>
\t<tbody>
\t\t<tr><td>$numTests</td><td>$numStarted</td><td${ numFailures > 0 ? " class=\"color-fail\"" : ""}>$numFailures</td><td>$numErrors</td><td>$numIgnored</td></tr>
\t</tbody>
</table>
""")


        report.append("""<h2>Results</h2>
<table class="results">
\t<thead>
\t\t<tr><td>Suite Name</td><td>Time (sec)</td><td>Success/Failed/Ignored</td><td></td></tr>
\t</thead>
\t<tbody>
""")
        int suiteIndex = 0
        suites.each { suiteEntry ->
            TestSuite tSuite = suiteEntry.value
            suiteIndex++

            int numSucc = 0
            int numFailed = 0
            int numIgnored = 0
            tSuite.cases.each { caseEntry ->
                TestCase tCase = caseEntry.value
                if (tCase.wasIgnored) {
                    ++numIgnored
                } else if (tCase.failure != null) {
                    ++numFailed
                } else {
                    ++numSucc
                }
            }
            int numSum = numSucc + numFailed + numIgnored

            String sumColor = "color-ok"
            if (numSum == numIgnored) {
                sumColor = "color-ignore"
            } else if (numFailed == numSum - numIgnored) {
                sumColor = "color-fail"
            } else if (numFailed > 0) {
                sumColor = "color-warn"
            }

            report.append("\t\t<tr class=\"suite ${suiteIndex % 2 == 0 ?  "even" : "odd" }\">" +
                    "<td>${getHTMLString(tSuite.name)}</td>" +
                    "<td>${getAsSeconds(tSuite.time)}</td>" +
                    "<td class=\"$sumColor\">$numSucc/$numFailed/$numIgnored</td>" +
                    "<td><button onclick=\"toggleCell('suite${suiteIndex}')\">Show/Hide</button></td>" +
                    "</tr>\n")
            report.append("""\t\t<tr id="suite${suiteIndex}" class="suite-cases"><td colspan="3">
\t\t\t<table class="cases">
\t\t\t\t<thead>
\t\t\t\t\t<tr><td>Name</td><td>Time (sec)</td><td>Status</td></tr>
\t\t\t\t</thead>
\t\t\t\t<tbody>
""")

            int caseIndex = 0
            tSuite.cases.each { caseEntry ->
                TestCase tCase = caseEntry.value
                caseIndex++

                report.append("\t\t\t\t\t<tr " +
                        "class=\"${ caseIndex % 2 == 0 ? "even" : "odd" }\">" +
                        "<td>${getHTMLString(tCase.name)}</td>" +
                        "<td>${getAsSeconds(tCase.time)}</td>")
                if (tCase.wasIgnored) {
                    report.append("<td class=\"color-ignore\">Ignored</td>")
                } else if (tCase.failure != null) {
                    report.append("<td class=\"color-fail\">Failed</td>")
                } else {
                    report.append("<td class=\"color-ok\">OK</td>")
                }
                report.append("</tr>\n")

                if (tCase.failure != null) {
                    String message = tCase.failure.toString();
                    message = getHTMLString(message.trim());
                    message = message.replaceAll("\n", "<br>\n")
                    report.append("\t\t\t\t\t<tr><td class=\"failure\" colspan=\"3\"><code>${message}</code></td></tr>\n")
                }
            }
            report.append("""\t\t\t\t</tbody>
\t\t\t</table>
\t\t</td><td></td></tr>
""")
        }

        report.append("""\t</tbody>
</table>
</body>
</html>
""")
    }

    /**
     *
     * @param millis
     * @return
     */
    private static double getAsSeconds(long millis) {
        def t = ((double) millis) / (1000.0)
        return new BigDecimal(t).setScale(3, RoundingMode.HALF_UP).doubleValue()
    }

    /**
     * Returns the number of tests.
     * @return number of tests
     */
    int getNumTests() {
        return numTests
    }

    /**
     * Returns the number of started tests.
     * @return number of started tests
     */
    int getNumStarted() {
        return numStarted
    }

    /**
     * Returns the number of failed tests.
     * @return number of failed tests
     */
    int getNumFailures() {
        return numFailures
    }

    /**
     * Returns the number of tests with error.
     * @return number of tests with error
     */
    int getNumErrors() {
        return numErrors
    }

    /**
     * Returns the number of ignored tests.
     * @return number of ignored tests
     */
    int getNumIgnored() {
        return numIgnored
    }

    /**
     * Returns the has ended flag.
     * @return has ended flag
     */
    boolean getHasEnded() {
        return hasEnded
    }
}
