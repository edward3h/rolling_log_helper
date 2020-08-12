
package org.ethelred.log_helper

import picocli.CommandLine
import spock.lang.Specification

import java.nio.file.Path
import static java.nio.file.Files.*

class AppTest extends Specification {
    Path testDir
    def cmd

    def setup() {
        testDir = createTempDirectory(getClass().simpleName)
        cmd = new CommandLine(new App())
    }

    def cleanup() {
        testDir.deleteDir()
    }

    def "test the default behaviour"() {
        setup:
        def base = "test1"
        def ext = ".log"
        def logPath = testDir.resolve(base + ext)
        def testLine = "Hello, world!"

        when:
        withInput(testLine + "\n") {
            cmd.execute(logPath.toString())
        }

        then:
        isDirectory(testDir)
        isSymbolicLink(logPath)

        when:
        def children = children(testDir)

        then:
        children.size() == 2

        when:
        def logFile = children.find { isRegularFile(it)}

        then:
        logFile != null
        "${logFile.fileName}".startsWith(base)
        "${logFile.fileName}".endsWith(ext)
        isSameFile(logFile, readSymbolicLink(logPath))

        when:
        def lines = logFile.readLines()

        then:
        lines.size() == 1
        lines.first().contains(testLine)
    }

    static def withInput(String text, Closure closure) {
        def oldIn = System.in
        try {
            def ba = new ByteArrayInputStream(text.bytes)
            System.setIn(ba)
            closure()
        } finally {
            System.setIn(oldIn)
        }
    }

    static def children(Path dir) {
        newDirectoryStream(dir).collect()
    }
}
