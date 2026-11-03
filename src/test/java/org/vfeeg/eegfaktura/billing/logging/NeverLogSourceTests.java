package org.vfeeg.eegfaktura.billing.logging;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Never-log test of the logging concept (W-261008-ca2, point 4): reads the source and fails on a log statement that
 * names personal data or credentials, or that logs a whole entity or DTO (Lombok's {@code toString} prints every
 * field). The encoder's masking catches patterns, this test catches the names and addresses no pattern knows.
 *
 * <p>Works on the text, on purpose: no new dependency. A real exception goes into {@link #ALLOWED} with its reason,
 * and an entry that no longer matches fails too, so the list cannot go stale.
 */
class NeverLogSourceTests {

    private static final Path SOURCE = Path.of("src/main/java");
    private static final String MODEL_PACKAGES = "org/vfeeg/eegfaktura/billing/";

    private static final Pattern NAMES = Pattern.compile("(?i)first_?name|last_?name|sur_?name|vorname|nachname"
            + "|bank_?owner|street|phone|e-?mail|iban|passw|pwd|token|secret|jwt|bearer");
    private static final Pattern LOGGER_DECLARATION = Pattern.compile("\\b(?:Logger|LoggingEventBuilder)\\s+(\\w+)\\b");
    private static final String NO_MEMBER = "(?!\\s*\\.(?!\\s*toString\\b))";
    private static final Pattern STRING = Pattern.compile("\"(?:[^\"\\\\\\n]|\\\\.)*\"");

    /** Exceptions: a statement of a file that may name what {@link #NAMES} forbids, and why. */
    private static final List<Allowed> ALLOWED = List.of(
            new Allowed("JwtRequestFilter.java", "\"Token does not grant the requested tenant\"",
                    "text only: no value, the token is not logged"),
            new Allowed("JwtTokenService.java", "\"Token rejected\"",
                    "text only: a fixed message and the exception's type, the token is not logged"));

    record Allowed(String file, String statement, String reason) {
    }

    record Hit(String file, int line, String statement, String why) {
        boolean isAllowed(Allowed allowed) {
            return file.equals(allowed.file()) && statement.contains(allowed.statement());
        }

        @Override
        public String toString() {
            return file + ":" + line + " " + why + ": " + statement;
        }
    }

    record Scan(List<Hit> hits, int statements) {
    }

    @Test
    void noLogStatementNamesPersonalDataOrWholeEntities() throws IOException {
        Set<String> modelTypes = modelTypes();
        assertTrue(modelTypes.contains("BillingDocument") && modelTypes.contains("DoBillingParams"), "" + modelTypes);
        assertFalse(modelTypes.contains("BillingRunStatus"), "an enum prints its name only");

        List<Hit> found = new ArrayList<>();
        int statements = 0;
        for (Path file : javaFiles()) {
            Scan scan = scan(file.getFileName().toString(), Files.readString(file), modelTypes);
            found.addAll(scan.hits());
            statements += scan.statements();
        }
        assertTrue(statements > 20, "the scan reads the log statements, found only " + statements);

        List<Hit> open = found.stream().filter(h -> ALLOWED.stream().noneMatch(h::isAllowed)).toList();
        assertEquals(List.of(), open, "never-log: fix the statement; allowlist it with a reason only if it is harmless");
        List<Allowed> stale = ALLOWED.stream().filter(a -> found.stream().noneMatch(h -> h.isAllowed(a))).toList();
        assertEquals(List.of(), stale, "allowlist entries that match nothing");
    }

    @Test
    void findsNamesInArgumentsTextAndKeyValuePairs() {
        for (String statement : List.of("log.info(\"sent to {}\", participant.getParticipantEmail());",
                "log.warn(\"iban={}\", value);", "log.atInfo().addKeyValue(\"token\", value).log(\"x\");",
                "log.error(\"first \" + lastName);", "log\n  .debug(\n \"x {}\",\n passwordHash);")) {
            assertEquals(1, hits("class A { void f() { " + statement + " } }").size(), statement);
        }
    }

    @Test
    void findsLoggersOfAnyNameAndAContinuedBuilder() {
        assertEquals(1, hits("class A { static final Logger ACCESS = null; void f() { ACCESS.atInfo().log(iban); } }").size());
        assertEquals(1, hits("class A { void f() { LoggingEventBuilder b = x(); b = b.addKeyValue(\"email\", v); } }").size());
    }

    @Test
    void findsWholeEntities() {
        for (String body : List.of("void f(BillingDocument d) { log.info(\"doc {}\", d); }",
                "void f(BillingDocument d) { log.info(\"doc \" + d); }",
                "void f(BillingDocument d) { log.info(\"doc {}\", d.toString()); }",
                "void f(BillingDocument d) { log.info(\"doc {}\", String.valueOf(d)); }",
                "void f(List<BillingDocument> docs) { log.info(\"docs {}\", docs); }",
                "void f(Item i) { log.info(\"doc {}\", i.getBillingDocument()); }")) {
            assertEquals(1, hits("class A { " + body + " }").size(), body);
        }
    }

    @Test
    void passesIdsCommentsAndOtherCode() {
        for (String body : List.of("void f(BillingDocument d) { log.info(\"doc {}\", d.getId()); }",
                "void f(BillingDocument d) { d.setEmail(email); }",
                "void f() { // log.info(\"{}\", email);\n }", "void f() { /* log.info(iban); */ }",
                "void f(String tenant) { log.warn(\"Mail template of {} not usable\", tenant); }",
                "String f() { return log.isDebugEnabled() ? email : null; }")) {
            assertEquals(List.of(), hits("class A { " + body + " }"), body);
        }
    }

    private static List<Hit> hits(String source) {
        return scan("A.java", source, Set.of("BillingDocument")).hits();
    }

    /** Regular classes of the packages {@code domain} and {@code model}; an enum only prints its name. */
    private static Set<String> modelTypes() throws IOException {
        Set<String> types = new HashSet<>();
        for (Path file : javaFiles()) {
            String path = file.toString().replace('\\', '/');
            String type = file.getFileName().toString().replace(".java", "");
            if ((path.contains(MODEL_PACKAGES + "domain/") || path.contains(MODEL_PACKAGES + "model/"))
                    && !Pattern.compile("\\benum\\s+" + type + "\\b").matcher(Files.readString(file)).find()) {
                types.add(type);
            }
        }
        return types;
    }

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCE)) {
            return files.filter(f -> f.toString().endsWith(".java")).toList();
        }
    }

    static Scan scan(String file, String source, Set<String> modelTypes) {
        String code = withoutComments(source);
        Set<String> loggers = new HashSet<>(Set.of("log"));
        LOGGER_DECLARATION.matcher(code).results().forEach(m -> loggers.add(m.group(1)));
        Pattern call = Pattern.compile("\\b(?:" + String.join("|", loggers) + ")\\s*\\.\\s*"
                + "(?:trace|debug|info|warn|error|atTrace|atDebug|atInfo|atWarn|atError|addKeyValue|setCause|log)\\s*\\(");
        Set<String> models = modelVariables(code, modelTypes);

        List<Hit> hits = new ArrayList<>();
        int statements = 0;
        Matcher m = call.matcher(code);
        int end = 0;
        while (m.find(end)) {
            end = statementEnd(code, m.start());
            statements++;
            String statement = code.substring(m.start(), end).replaceAll("\\s+", " ").strip();
            int line = (int) code.substring(0, m.start()).chars().filter(c -> c == '\n').count() + 1;
            Matcher name = NAMES.matcher(statement);
            if (name.find()) {
                hits.add(new Hit(file, line, statement, "names " + name.group()));
            }
            String model = wholeModel(STRING.matcher(statement).replaceAll("\"\""), models, modelTypes);
            if (model != null) {
                hits.add(new Hit(file, line, statement, "logs the whole " + model));
            }
        }
        return new Scan(hits, statements);
    }

    /** Names of the variables and parameters of a model type, also inside a collection. */
    private static Set<String> modelVariables(String code, Set<String> modelTypes) {
        String types = String.join("|", modelTypes);
        return Pattern.compile("\\b(?:" + types + ")(?:<[^>]*>|\\[])?\\s+(\\w+)|<\\s*(?:" + types + ")\\s*>\\s+(\\w+)")
                .matcher(code).results().map(r -> r.group(1) != null ? r.group(1) : r.group(2))
                .collect(Collectors.toSet());
    }

    /** The model a statement prints whole: bare, by toString, or as what a getter returns. */
    private static String wholeModel(String statement, Set<String> models, Set<String> modelTypes) {
        for (String variable : models) {
            // not a part of a longer name, not a method, no member access except toString
            if (Pattern.compile("(?<![\\w.])" + variable + "(?!\\w)(?!\\s*\\()" + NO_MEMBER).matcher(statement).find()) {
                return variable;
            }
        }
        for (String type : modelTypes) {
            if (Pattern.compile("\\.get" + type + "\\(\\)" + NO_MEMBER).matcher(statement).find()) {
                return type;
            }
        }
        return null;
    }

    /** Index behind the {@code ;} that ends the statement, skipping strings and characters. */
    private static int statementEnd(String code, int start) {
        int depth = 0;
        for (int i = start; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '"' || c == '\'') {
                i = literalEnd(code, i);
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ';' && depth == 0) {
                return i + 1;
            }
        }
        return code.length();
    }

    /** Index of the quote that closes the string or character at {@code start}; a text block counts as well. */
    private static int literalEnd(String code, int start) {
        char quote = code.charAt(start);
        if (code.startsWith("\"\"\"", start)) {
            int close = code.indexOf("\"\"\"", start + 3);
            return close < 0 ? code.length() : close + 2;
        }
        for (int i = start + 1; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == quote) {
                return i;
            }
        }
        return code.length();
    }

    /** The source with comments blanked out; strings stay, a {@code //} inside a string is no comment. */
    private static String withoutComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '"' || c == '\'') {
                int end = Math.min(literalEnd(source, i), source.length() - 1);
                out.append(source, i, end + 1);
                i = end;
            } else if (source.startsWith("//", i) || source.startsWith("/*", i)) {
                int end = source.startsWith("//", i) ? source.indexOf('\n', i) : source.indexOf("*/", i) + 1;
                end = end <= 0 ? source.length() - 1 : end;
                source.substring(i, end + 1).chars().filter(ch -> ch == '\n').forEach(ch -> out.append('\n'));
                i = end;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
