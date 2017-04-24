package com.phrozenspectrum;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Stopwatch;
import org.unix4j.Unix4j;
import org.unix4j.unix.Grep;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static java.nio.file.Files.*;

/**
 * Hello world!
 */
public class App {
    public static void main(String[] args) throws IOException {
        if(args.length < 1) throw new IllegalArgumentException("Requires file path as first argument.");

        System.out.println("This program modifies all .java files in place and is not 100% perfect. " +
        "Make sure the files are version controlled and your workspace is clean before proceeding.");

        System.out.println("Press any key to contine...");

        new Scanner(System.in).nextLine();

        System.out.println("Starting.");

        Path root = FileSystems.getDefault().getPath(args[0]);
        Stopwatch watch = Stopwatch.createStarted();
        int count = loggingRefactor(root);
        System.out.println("Modified " + count + " files in " + watch.elapsed(TimeUnit.MILLISECONDS) + " milliseconds");
    }

    public static int loggingRefactor(Path root) throws IOException {
        AtomicInteger count = new AtomicInteger(0);
        walk(root)
                .parallel()
                .filter(App::javaFile)
                .filter(App::containsStr)
                .forEach((Path p) -> {
                    try {
                        String content = new String(Files.readAllBytes(p), "UTF-8");
                        if (!hasLogger(content)) {
                            List<String> lines = splitOnNewlines(content);
                            int importLine = findLastImport(lines);
                            lines.add(importLine + 1, "import org.slf4j.Logger;");
                            lines.add(importLine + 2, "import org.slf4j.LoggerFactory;");

                            int classBracketLine = findClassBracket(lines);
                            lines.add(classBracketLine + 1, "    private static final Logger LOGGER = LoggerFactory.getLogger(" + p.toFile().getName().split("\\.")[0] + ".class);");

                            content = Joiner.on("\n").join(lines);
                        }

                        content = content.replace("System.out.println", "LOGGER.debug");

                        Files.write(p, content.getBytes(StandardCharsets.UTF_8));

                        count.incrementAndGet();
                    } catch (RuntimeException | IOException e) {
                        throw new RuntimeException("Failed to process file " + p.getFileName(), e);
                    }
                });
        return count.get();
    }

    public static boolean javaFile(Path p) {
        return p.getFileName().toString().endsWith(".java");
    }

    public static boolean containsStr(Path p) {
        String fileName = Unix4j.grep(Grep.Options.l, "System.out.println", p.toFile()).toStringResult();
        return !fileName.isEmpty();
    }

    public static List<String> splitOnNewlines(String str) {
        return new ArrayList<>(Splitter.onPattern("\\n").splitToList(str));
    }

    public static boolean hasLogger(String str) {
        return str.contains("import org.slf4j.Logger;");
    }

    private static Pattern importLinePattern = Pattern.compile("^\\s*import\\s\\w+");
    private static Pattern classLine = Pattern.compile("(^\\s*(private\\s+|public\\s+)*(final\\s+)*(abstract\\s+)*(final\\s+)*class)");

    public static int findClassBracket(List<String> lines) {
        boolean foundClass = false;
        for (int x = 1; x < lines.size(); x++) {
            if (foundClass) {
                if (lines.get(x).contains("{")) {
                    return x;
                }
            } else if (classLine.matcher(lines.get(x)).find()) {
                if (lines.get(x).contains("{")) {
                    return x;
                }

                foundClass = true;
            }
        }

        throw new IllegalArgumentException("Unable to find class definition with opening bracket in string.");
    }

    public static int findLastImport(List<String> lines) {
        int importLine = 1;
        // Skip package line.
        for (int x = 1; x < lines.size(); x++) {
            String line = lines.get(x);
            if (importLinePattern.matcher(line).find()) {
                importLine = x;
            } else if (classLine.matcher(line).find()) {
                return importLine;
            }
        }

        throw new IllegalArgumentException("Unable to find imports in string.");
    }

    public static List<String> writeLineAt(List<String> lines, int lineNumber, String toInsert) {
        lines.add(lineNumber, toInsert);
        return lines;
    }
}
