package com.phrozenspectrum;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static java.nio.file.Files.*;

/**
 * Hello world!
 */
public class App {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) throw new IllegalArgumentException("Requires file path as first argument.");

        System.out.println("This program modifies all .java files in place and is not 100% perfect. " +
                "Make sure the files are version controlled and your workspace is clean before proceeding.");

        System.out.println("Press Enter key to continue...");

        new Scanner(System.in).nextLine();

        System.out.println("Starting.");

        Path root = FileSystems.getDefault().getPath(args[0]);
        Stopwatch watch = Stopwatch.createStarted();
        int count = loggingRefactor(root);
        System.out.println("Modified " + count + " files in " + watch.elapsed(TimeUnit.MILLISECONDS) + " milliseconds");
    }

    static class LoadedPath {
        public final Path path;
        public final String fileContent;

        public LoadedPath(Path path, String fileContent) {
            this.path = path;
            this.fileContent = fileContent;
        }
    }

    public static Map<Pattern, String> replacements = new LinkedHashMap<>();

    static {
        replacements.put(Pattern.compile("(?m)^(\\s*)System\\.out\\.println"), "$1LOGGER.debug");
        replacements.put(Pattern.compile("(?m)^(\\s*)System\\.out\\.print"), "$1LOGGER.debug");
        replacements.put(Pattern.compile("(?m)^(\\s*)e.printStackTrace\\(\\)"), "$1LOGGER.error(\"An exception occurred\", e)");
        replacements.put(Pattern.compile("(?m)^(\\s*)e.printStackTrace\\(out\\)"), "$1LOGGER.error(\"An exception occurred\", e)");
        replacements.put(Pattern.compile("(?m)^(\\s*)e.printStackTrace\\(System\\.out\\)"), "$1LOGGER.error(\"An exception occurred\", e)");
    }

    public static int loggingRefactor(Path root) throws IOException {
        AtomicInteger count = new AtomicInteger(0);
        walk(root)
                .parallel()
                .filter(App::javaFile)
                .map(App::loadFileContent)
                .filter(App::containsStr)
                .forEach((LoadedPath p) -> {
                    try {
                        String content = p.fileContent;
                        if (!hasLogger(content)) {
                            List<String> lines = splitOnNewlines(content);
                            int importLine = findLineToAddImport(lines);
                            lines.add(importLine + 1, "");
                            lines.add(importLine + 2, "import org.slf4j.Logger;");
                            lines.add(importLine + 3, "import org.slf4j.LoggerFactory;");

                            int classBracketLine = findClassBracket(lines);
                            lines.add(classBracketLine + 1, "    private static final Logger LOGGER = LoggerFactory.getLogger(" + p.path.toFile().getName().split("\\.")[0] + ".class);");

                            content = Joiner.on("\n").join(lines);
                        }

                        // Do replacements
                        for (Map.Entry<Pattern, String> replacement : replacements.entrySet()) {
                            content = replacement.getKey().matcher(content).replaceAll(replacement.getValue());
                        }

                        Files.write(p.path, content.getBytes(StandardCharsets.UTF_8));

                        count.incrementAndGet();
                    } catch (RuntimeException | IOException e) {
                        throw new RuntimeException("Failed to process file " + p.path.getFileName(), e);
                    }
                });
        return count.get();
    }

    public static LoadedPath loadFileContent(Path p) {
        try {
            return new LoadedPath(p, new String(Files.readAllBytes(p), StandardCharsets.UTF_8));

        } catch (IOException e) {
            throw new RuntimeException("Failed to read file " + p.getFileName().toString());
        }
    }

    public static boolean javaFile(Path p) {
        return p.getFileName().toString().endsWith(".java");
    }

    public static boolean containsStr(LoadedPath file) {
        for (Map.Entry<Pattern, String> replacement : replacements.entrySet()) {
            if(replacement.getKey().matcher(removeMultilineComments(file.fileContent)).find()) return true;
        }

        return false;
    }

    public static List<String> splitOnNewlines(String str) {
        return new ArrayList<>(Splitter.onPattern("\\n").splitToList(str));
    }

    public static boolean hasLogger(String str) {
        return str.contains("import org.slf4j.Logger;");
    }

    private static final Pattern importLinePattern = Pattern.compile("^\\s*import\\s+\\w+");
    private static final Pattern packageLinePattern = Pattern.compile("^\\s*package\\s+\\w+");
    private static final Pattern classLine = Pattern.compile("(^\\s*(private\\s+|public\\s+)*(final\\s+)*(abstract\\s+)*(final\\s+)*class)");

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

    public static int findLineToAddImport(List<String> lines) {
        int packageLine = findPackage(lines);

        assert (packageLine >= 0);

        int lastImportLine = findLastImport(lines, packageLine + 1);

        return (lastImportLine >= 0) ? lastImportLine : packageLine;
    }

    public static int findPackage(List<String> lines) {
        for (int x = 0; x < lines.size(); x++) {
            if (packageLinePattern.matcher(lines.get(x)).find())
                return x;
        }
        return -1;
    }

    public static int findLastImport(List<String> lines, int startLine) {
        int importLine = 0;

        // Skip package line.
        for (int x = startLine; x < lines.size(); x++) {
            String line = lines.get(x);
            if (importLinePattern.matcher(line).find()) {
                importLine = x;
            } else if (classLine.matcher(line).find()) {
                return importLine;
            }
        }

        throw new IllegalArgumentException("Unable to find imports in string.");
    }

    public static final Pattern multiline = Pattern.compile("(?s)(/\\*)(.*?)(\\*/)");
    public static String removeMultilineComments(String str) {
        return multiline.matcher(str).replaceAll("");
    }
}
