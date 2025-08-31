import groovy.json.JsonOutput
import groovy.xml.XmlParser

// Hardened XML parser that allows DOCTYPE but disables external entities
def xmlParser = new XmlParser(false, false)
xmlParser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
xmlParser.setFeature("http://xml.org/sax/features/external-general-entities", false)
xmlParser.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
xmlParser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)

// --- Base directory for reports ---
def reportsDir = new File(project.build.directory, "site/reports")
reportsDir.mkdirs()

// --- 1. Test Pass Percentage ---
def surefireDir = new File(project.build.directory, "surefire-reports")
if (surefireDir.exists()) {
    int totalTests = 0
    int failures = 0
    int errors = 0
    surefireDir.eachFileMatch(~/TEST-.*\.xml/) { file ->
        def report = xmlParser.parse(file)
        totalTests += report.'@tests'.toInteger()
        failures += report.'@failures'.toInteger()
        errors += report.'@errors'.toInteger()
    }

    int passed = totalTests - failures - errors
    def percentage = totalTests > 0 ? (passed / totalTests) * 100 : 100
    def color = percentage < 95 ? "red" : "green"

    def testJson = [
            schemaVersion: 1,
            label: "Tests Passing",
            message: String.format("%d / %d (%.1f%%)", passed, totalTests, percentage),
            color: color
    ]
    new File(reportsDir, "tests.json").text = JsonOutput.toJson(testJson)
}

// --- 2. JaCoCo Code Coverage ---
def jacocoXml = new File(project.build.directory, "site/jacoco/jacoco.xml")
if (jacocoXml.exists()) {
    def report = xmlParser.parse(jacocoXml)
    def lines = report.get('counter').find { it.'@type' == 'LINE' }

    def linesMissed = lines.'@missed'.toInteger()
    def linesCovered = lines.'@covered'.toInteger()
    def totalLines = linesMissed + linesCovered

    def percentage = totalLines > 0 ? (linesCovered / totalLines) * 100 : 0
    def color = percentage < 70 ? "red" : (percentage < 90 ? "orange" : "green")

    def coverageJson = [
            schemaVersion: 1,
            label: "Tests LOC Coverage",
            message: String.format("%d / %d (%.1f%%)", linesCovered, totalLines, percentage),
            color: color
    ]
    new File(reportsDir, "coverage.json").text = JsonOutput.toJson(coverageJson)
}

// --- 3. PMD Cyclomatic Complexity (avg/max over project) ---
def pmdXml = new File(project.build.directory, "pmd.xml")
if (pmdXml.exists()) {
    def pmdReport = xmlParser.parse(pmdXml)

    // Collect all CyclomaticComplexity violation messages, which include numeric values
    def ccValues = []
    pmdReport.file.each { f ->
        f.violation.findAll { v ->
            // Rule name attribute depends on PMD version; match by rule attribute and/or message content
            (v.'@rule'?.toString()?.equalsIgnoreCase('CyclomaticComplexity')) ||
                    (v.text()?.toLowerCase()?.contains('cyclomatic complexity'))
        }.each { v ->
            def matcher = ((v.text() ?: "") =~ /(?i)(?:Cyclomatic Complexity|complexity)\D+(\d+)/)
            if (matcher.find()) {
                try {
                    ccValues << Integer.parseInt(matcher.group(1))
                } catch (ignored) { }
            }
        }
    }

    if (!ccValues.isEmpty()) {
        def avg = ccValues.sum() as double
        avg = avg / ccValues.size()
        def max = ccValues.max() as int

        // Color thresholds for average CC
        def color = avg <= 3 ? "green" : (avg <= 6 ? "orange" : "red")

        def json = [
                schemaVersion: 1,
                label: "Cyclomatic Complexity",
                message: String.format("avg %.2f, max %d", avg, max),
                color: color
        ]
        new File(reportsDir, "pmd-complexity.json").text = JsonOutput.toJson(json)
    }
}

// --- 4. PMD CPD: Duplicate Blocks (>= 10 lines) & Duplicated Lines % ---
def cpdXml = new File(project.build.directory, "cpd.xml")
if (cpdXml.exists()) {
    def cpdReport = xmlParser.parse(cpdXml)

    // Count duplicate blocks with at least 7 lines
    def duplications = cpdReport.duplication?.findAll { d ->
        def linesAttr = d.'@lines'?.toString()
        linesAttr && linesAttr.isInteger() && linesAttr.toInteger() >= 10
    } ?: []

    // Badge 1: number of duplicate blocks (>= 10 lines)
    def blocksCount = duplications.size()
    def blocksColor = blocksCount == 0 ? "green" : (blocksCount <= 5 ? "orange" : "red")
    def blocksJson = [
            schemaVersion: 1,
            label: "Duplicate Blocks (≥10 lines)",
            message: blocksCount.toString(),
            color: blocksColor
    ]
    new File(reportsDir, "cpd-blocks.json").text = JsonOutput.toJson(blocksJson)

    // Badge 2: duplicated lines percentage over total lines of src/main/java
    // Sum duplicated lines across all occurrences (count each duplicated block's lines per file)
    long duplicatedLinesTotal = 0
    duplications.each { d ->
        def lines = d.'@lines'.toInteger()
        // Each duplication node has N <file> entries; duplicated lines counted per occurrence
        def occurrences = d.file?.size() ?: 0
        if (occurrences >= 2) {
            // Count all occurrences; this aligns with common CPD "duplicated lines" totals
            duplicatedLinesTotal += (long) lines * occurrences
        }
    }

    // Calculate total lines of code in src/main/java (all lines, simple and robust)
    long totalLoc = 0
    def srcMainJava = new File(project.basedir, "src/main/java")
    if (srcMainJava.exists()) {
        srcMainJava.eachFileRecurse { f ->
            if (f.isFile() && f.name.endsWith(".java")) {
                // Count all lines; if you prefer non-blank only, add a trim().length() > 0 filter
                totalLoc += f.readLines().size()
            }
        }
    }

    double dupPct = (totalLoc > 0) ? (duplicatedLinesTotal * 100.0 / totalLoc) : 0.0
    def dupColor = dupPct < 3.0 ? "green" : (dupPct < 10.0 ? "orange" : "red")

    def dupJson = [
            schemaVersion: 1,
            label: "Duplicated Lines",
            message: String.format("%d / %d (%.2f%%)", duplicatedLinesTotal, totalLoc, dupPct),
            color: dupColor
    ]
    new File(reportsDir, "cpd-duplication.json").text = JsonOutput.toJson(dupJson)
}
