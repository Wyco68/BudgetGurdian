package com.budgetguardian.integration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enforces the project's core rule automatically: business-logic packages must
 * not use the Java Collections Framework as storage. This test fails the build
 * if a forbidden {@code java.util} collection is imported anywhere under
 * {@code datastructures}, {@code model}, {@code repository}, {@code service}
 * or {@code algorithm}.
 *
 * <p>The UI packages ({@code view}, {@code app}) are exempt: JavaFX requires
 * {@code ObservableList} interop, and view-local scratch lists are a
 * documented UI-boundary allowance.</p>
 */
class ArchitectureGuardTest {

    /** Collection types banned from business logic (custom versions exist for each). */
    private static final List<String> FORBIDDEN = List.of(
            "java.util.ArrayList", "java.util.LinkedList", "java.util.HashMap",
            "java.util.HashSet", "java.util.TreeMap", "java.util.TreeSet",
            "java.util.PriorityQueue", "java.util.Stack", "java.util.ArrayDeque",
            "java.util.Vector", "java.util.Hashtable", "java.util.Deque",
            "java.util.Queue", "java.util.List<", "java.util.Map<", "java.util.Set<");

    private static final List<String> GUARDED_PACKAGES = List.of(
            "datastructures", "model", "repository", "service", "algorithm");

    @Test
    void businessLogicUsesNoJavaUtilCollections() throws IOException {
        Path base = Path.of("src", "main", "java", "com", "budgetguardian");
        List<String> violations = new ArrayList<>();

        for (String pkg : GUARDED_PACKAGES) {
            Path dir = base.resolve(pkg);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    scan(file, violations);
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "Forbidden java.util collection usage in business logic:\n" + String.join("\n", violations));
    }

    private void scan(Path file, List<String> violations) throws IOException {
        List<String> lines = Files.readAllLines(file);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!line.startsWith("import ")) {
                continue;
            }
            for (String banned : FORBIDDEN) {
                if (line.contains(banned)) {
                    violations.add(file.getFileName() + ":" + (i + 1) + "  " + line);
                }
            }
        }
    }
}
