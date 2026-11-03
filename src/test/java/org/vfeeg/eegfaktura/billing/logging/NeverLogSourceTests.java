package org.vfeeg.eegfaktura.billing.logging;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Never-log test of the logging concept (W-261008-ca2, point 4): reads the source and fails on a log statement whose
 * code names personal data or credentials, or that logs a whole value of a record with such a component (a record's
 * {@code toString} prints every component). The encoder's masking catches patterns, this test catches the names and
 * addresses no pattern knows.
 *
 * <p>Identifiers are split into words (camelCase, snake_case) and compared as whole words with {@link #WORDS} (an
 * entry may span several words: {@code firstName}, {@code api_key}), also with a plural {@code s}; in an identifier all
 * in lower case a word of five or more letters counts as a part too. String literals are not scanned, a fixed text
 * names no value, except a list word directly before a placeholder ({@code "token: {}"}). Values are not skipped by
 * type: a text scan does not know the types. Works on the text, on purpose: no new dependency. A real exception goes into {@link #ALLOWED} with
 * its reason, and an entry that no longer matches fails too, so the list cannot go stale.
 */
class NeverLogSourceTests {

    private static final Path SOURCE = Path.of("src/main/java");

    /** The never-log words common to all eegfaktura services; a blank joins the words of a compound. */
    private static final List<String> LIST = List.of("password", "passwd", "pwd", "secret", "token", "jwt", "bearer",
            "authorization", "credential", "creds", "cookie", "api key", "iban", "bic", "firstname", "lastname",
            "vorname", "nachname", "name1", "name2", "street", "phone", "email", "mail address", "recipient", "sentto",
            "owner", "accountholder", "username", "userid", "sub", "subject", "payload", "body");
    /** {@link #LIST} as identifier words: lower case, compounds joined. */
    private static final Set<String> WORDS = LIST.stream().map(w -> w.replace(" ", "")).collect(Collectors.toSet());
    /** A list word directly before a placeholder of a format string: {@code "token: {}"}, {@code "password=%s"}. */
    private static final Pattern BEFORE_PLACEHOLDER = Pattern.compile("(?i)(?<![a-z])("
            + LIST.stream().sorted((a, b) -> b.length() - a.length()).map(w -> w.replace(" ", "[ _-]?"))
                    .collect(Collectors.joining("|"))
            + ")s?[ \\t]*[:=]?[ \\t]*(?:\\{}|%[-#+ 0,(\\d.]*[a-zA-Z])");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Pattern WORD_BOUNDARY = Pattern.compile("_+|(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");
    private static final Pattern LOGGER_DECLARATION = Pattern.compile("\\b(?:Logger|LoggingEventBuilder)\\s+(\\w+)\\b");
    private static final Pattern RECORD = Pattern.compile("\\brecord\\s+(\\w+)\\s*(?:<[^>]*>)?\\s*\\(([^)]*)\\)");

    /** Exceptions: a statement of a file that may name what {@link #WORDS} forbids, and why. */
    private static final List<Allowed> ALLOWED = List.of();

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
    void noLogStatementNamesPersonalDataOrWholeRecords() throws IOException {
        Set<String> records = new HashSet<>();
        for (Path file : javaFiles()) {
            records.addAll(personalRecords(Files.readString(file)));
        }
        assertTrue(records.containsAll(Set.of("TestRequest", "TestResult")), "" + records);
        assertFalse(records.contains("MailFile") || records.contains("Conflict"), "" + records);

        List<Hit> found = new ArrayList<>();
        int statements = 0;
        for (Path file : javaFiles()) {
            Scan scan = scan(file.getFileName().toString(), Files.readString(file), records);
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
    void findsNamesInIdentifiers() {
        for (String statement : List.of("log.info(\"sent to {}\", participant.getParticipantEmail());",
                "log.error(\"first \" + lastName);", "log\n  .debug(\n \"x {}\",\n passwordHash);",
                "log.warn(\"x {}\", MAIL_PASSWORD);", "log.info(\"{}\", firstName);", "log.info(\"{}\", api_key);",
                "log.atInfo().addKeyValue(\"x\", request.recipient()).log(\"x\");", "log.info(\"{}\", sentTo);",
                // plural s
                "log.info(\"{}\", tokens);", "log.info(\"{}\", emails);", "log.info(\"{}\", passwords);",
                "log.info(\"{}\", recipients);", "log.info(\"{}\", IBANs);", "log.info(\"{}\", memberIbans);",
                // a word of five or more letters inside an identifier all in lower case
                "log.info(\"{}\", accesstoken);", "log.info(\"{}\", userpassword);", "log.info(\"{}\", mailaddress);",
                "log.info(\"{}\", tokenizer);")) {
            assertEquals(1, hits("class A { void f() { " + statement + " } }").size(), statement);
        }
    }

    @Test
    void findsAListWordDirectlyBeforeAPlaceholder() {
        for (String statement : List.of("log.warn(\"iban={}\", value);", "log.info(\"token: {}\", value);",
                "log.info(String.format(\"password=%s\", value));", "log.info(\"Mail address {} rejected\", v);",
                "log.info(\"secrets {}\", v);", "log.info(\"\"\"\n  api_key: {}\n  \"\"\", v);")) {
            assertEquals(1, hits("class A { void f() { " + statement + " } }").size(), statement);
        }
    }

    @Test
    void findsLoggersOfAnyNameAndAContinuedBuilder() {
        assertEquals(1, hits("class A { static final Logger ACCESS = null; void f() { ACCESS.atInfo().log(iban); } }").size());
        assertEquals(1, hits("class A { void f() { LoggingEventBuilder b = x(); b = b.addKeyValue(\"k\", email); } }").size());
    }

    @Test
    void findsWholeRecordsWithAPersonalComponent() {
        assertEquals(Set.of("Mail"), personalRecords("record Mail(String recipient, int size) {} "
                + "record File(Map<String, List<String>> parts, long size) {}"));
        for (String body : List.of("void f(Mail m) { log.info(\"mail {}\", m); }",
                "void f(Mail m) { log.info(\"mail \" + m); }",
                "void f(Mail m) { log.info(\"mail {}\", m.toString()); }",
                "void f(Models.Mail m) { log.info(\"mail {}\", String.valueOf(m)); }",
                "void f(List<Mail> mails) { log.info(\"mails {}\", mails); }")) {
            assertEquals(1, hits("class A { " + body + " }").size(), body);
        }
    }

    @Test
    void passesLiteralsOtherWordsCommentsAndOtherCode() {
        for (String body : List.of("void f() { log.warn(\"Token does not grant the requested tenant\"); }",
                "void f() { log.warn(\"no iban for member {}\", id); }",
                "void f() { log.atInfo().addKeyValue(\"token\", value).log(\"x\"); }",
                "void f() { log.info(\"\"\"\n  email\n  \"\"\"); }",
                "void f() { log.info(\"{} {} {}\", bicycle, subscriptionId, nobody); }",
                "void f(Mail m) { log.info(\"mail {}\", m.size()); }",
                "void f(Mail m) { m.setEmail(email); }",
                "void f() { // log.info(\"{}\", email);\n }", "void f() { /* log.info(iban); */ }",
                "void f(String tenant) { log.warn(\"Mail template of {} not usable\", tenant); }",
                "String f() { return log.isDebugEnabled() ? email : null; }")) {
            assertEquals(List.of(), hits("class A { " + body + " }"), body);
        }
    }

    private static List<Hit> hits(String source) {
        return scan("A.java", source, Set.of("Mail")).hits();
    }

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCE)) {
            return files.filter(f -> f.toString().endsWith(".java")).toList();
        }
    }

    /** Records with a component whose name is a never-log word: their {@code toString} prints it. */
    static Set<String> personalRecords(String source) {
        Set<String> records = new HashSet<>();
        Matcher m = RECORD.matcher(withoutComments(source));
        while (m.find()) {
            String components = m.group(2);
            for (String previous = ""; !previous.equals(components); ) {
                previous = components;
                components = components.replaceAll("<[^<>]*>", "");
            }
            for (String component : components.split(",")) {
                Matcher name = Pattern.compile("(\\w+)\\s*$").matcher(component);
                if (name.find() && personal(name.group(1)) != null) {
                    records.add(m.group(1));
                }
            }
        }
        return records;
    }

    /**
     * The never-log word an identifier contains as one or more whole words, also with a plural {@code s}, or
     * {@code null}. In an identifier all in lower case a word of five or more letters counts as a part too
     * ({@code accesstoken}).
     */
    static String personal(String identifier) {
        String[] words = WORD_BOUNDARY.split(identifier);
        for (int from = 0; from < words.length; from++) {
            StringBuilder joined = new StringBuilder();
            for (int to = from; to < words.length; to++) {
                String word = joined.append(words[to].toLowerCase(Locale.ROOT)).toString();
                if (WORDS.contains(word)) {
                    return word;
                }
                if (word.endsWith("s") && WORDS.contains(word.substring(0, word.length() - 1))) {
                    return word.substring(0, word.length() - 1);
                }
            }
        }
        if (identifier.equals(identifier.toLowerCase(Locale.ROOT))) {
            for (String word : WORDS) {
                if (word.length() >= 5 && identifier.contains(word)) {
                    return word;
                }
            }
        }
        return null;
    }

    static Scan scan(String file, String source, Set<String> records) {
        String code = withoutComments(source);
        Set<String> loggers = new HashSet<>(Set.of("log"));
        LOGGER_DECLARATION.matcher(code).results().forEach(m -> loggers.add(m.group(1)));
        Pattern call = Pattern.compile("\\b(?:" + String.join("|", loggers) + ")\\s*\\.\\s*"
                + "(?:trace|debug|info|warn|error|atTrace|atDebug|atInfo|atWarn|atError|addKeyValue|setCause|log)\\s*\\(");
        Set<String> recordVariables = recordVariables(code, records);

        List<Hit> hits = new ArrayList<>();
        int statements = 0;
        Matcher m = call.matcher(code);
        int end = 0;
        while (m.find(end)) {
            end = statementEnd(code, m.start());
            statements++;
            String statement = code.substring(m.start(), end).replaceAll("\\s+", " ").strip();
            int line = (int) code.substring(0, m.start()).chars().filter(c -> c == '\n').count() + 1;
            String withoutLiterals = withoutLiterals(statement);
            Matcher identifier = IDENTIFIER.matcher(withoutLiterals);
            while (identifier.find()) {
                String word = personal(identifier.group());
                if (word != null) {
                    hits.add(new Hit(file, line, statement, "names " + word));
                    break;
                }
            }
            for (String literal : literals(statement)) {
                Matcher placeholder = BEFORE_PLACEHOLDER.matcher(literal);
                if (placeholder.find()) {
                    hits.add(new Hit(file, line, statement, "formats a value as " + placeholder.group(1)));
                    break;
                }
            }
            for (String variable : recordVariables) {
                // the value itself or its toString, not one of its components
                if (Pattern.compile("(?<![\\w.])" + variable + "\\b(?!\\s*[.(])|(?<![\\w.])" + variable
                        + "\\s*\\.\\s*toString\\b").matcher(withoutLiterals).find()) {
                    hits.add(new Hit(file, line, statement, "logs the whole record " + variable));
                }
            }
        }
        return new Scan(hits, statements);
    }

    /** Names of the variables and parameters of a record type of {@code records}, also inside a collection. */
    private static Set<String> recordVariables(String code, Set<String> records) {
        if (records.isEmpty()) {
            return Set.of();
        }
        String types = String.join("|", records);
        return Pattern.compile("\\b(?:" + types + ")(?:\\[])?\\s+(\\w+)|<\\s*(?:\\w+\\.)*(?:" + types + ")\\s*>\\s+(\\w+)")
                .matcher(code).results().map(r -> r.group(1) != null ? r.group(1) : r.group(2))
                .collect(Collectors.toSet());
    }

    /** The statement with every string, text block and character literal emptied. */
    private static String withoutLiterals(String statement) {
        StringBuilder out = new StringBuilder(statement.length());
        for (int i = 0; i < statement.length(); i++) {
            char c = statement.charAt(i);
            if (c == '"' || c == '\'') {
                i = literalEnd(statement, i);
                out.append(c).append(c);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** The string literals and text blocks of the statement, with their quotes. */
    private static List<String> literals(String statement) {
        List<String> literals = new ArrayList<>();
        for (int i = 0; i < statement.length(); i++) {
            if (statement.charAt(i) == '"') {
                int close = literalEnd(statement, i);
                literals.add(statement.substring(i, Math.min(close + 1, statement.length())));
                i = close;
            } else if (statement.charAt(i) == '\'') {
                i = literalEnd(statement, i);
            }
        }
        return literals;
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
