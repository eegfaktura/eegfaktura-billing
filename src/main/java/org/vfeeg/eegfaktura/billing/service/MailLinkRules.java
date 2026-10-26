package org.vfeeg.eegfaktura.billing.service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Link rules of EEG-editable mails (Variante A, decided 2026-10-26; the same rules as in
 * eegfaktura-backend, shared cases in {@code mail-link-cases.json}): no IP hosts, no URL shorteners
 * ({@code links.blockedHosts} of {@code mail-html-policy.json}), a link text that shows an address
 * shows the link's own address, no placeholder inside a link, at most {@code maxLinks} links and
 * {@code maxLinkDomains} main domains. Runs over the sanitized body only; the server's fixed footer
 * is not part of it.
 */
class MailLinkRules {

    /** A broken rule: a key of the shared case file and the German message for the editor. */
    record Violation(String key, String message) {
    }

    /** A host label that is a number to browsers (decimal or 0x hex). */
    private static final Pattern NUMERIC_LABEL = Pattern.compile("[0-9]+|0x[0-9a-f]*");

    // The address extraction of a link text is the same algorithm as in eegfaktura-backend
    // (links.go): the text is split into words, and every word that is an address must show the
    // link's own address.
    private static final Pattern ZERO_WIDTH = Pattern.compile("[​‌‍⁠﻿]");
    private static final Pattern TEXT_SPACE = Pattern.compile("[\\p{Z}\\t\\n\\x0B\\f\\r]+");
    /** A word like office@example.at. */
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+");
    /** A word like example.at or example.at/pfad. */
    private static final Pattern BARE_DOMAIN =
            Pattern.compile("[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}([/?#]\\S*)?");
    /** Stripped from both ends of a word, "." only from its end. */
    private static final String WORD_PUNCT = "()[]<>\"'„“”‚‘,;:!?";

    private final MailHtmlPolicy policy;

    MailLinkRules(MailHtmlPolicy policy) {
        this.policy = policy;
    }

    /** {@link #check(String, Map)} with the placeholder keys in the messages. */
    List<Violation> check(String sanitizedHtml) {
        return check(sanitizedHtml, Map.of());
    }

    /**
     * The broken rules of a sanitized body, one per key, in the order of the case file's keys;
     * {@code labels} (placeholder key → label) names placeholders in the messages.
     */
    List<Violation> check(String sanitizedHtml, Map<String, String> labels) {
        MailHtmlPolicy.LinkLimits limits = policy.linkLimits();
        List<MailHtmlPolicy.Link> links = policy.links(sanitizedHtml);
        Set<String> ipHosts = new LinkedHashSet<>();
        Set<String> shortenerHosts = new LinkedHashSet<>();
        List<String> mismatches = new ArrayList<>();
        Set<String> placeholders = new LinkedHashSet<>();
        Set<String> domains = new LinkedHashSet<>();
        for (MailHtmlPolicy.Link link : links) {
            link.placeholders().forEach(key -> placeholders.add(labels.getOrDefault(key, key)));
            String href = link.href().trim();
            String lower = href.toLowerCase(Locale.ROOT);
            String mailto = lower.startsWith("mailto:") ? mailtoAddress(href) : null;
            String host = lower.startsWith("https://") ? host(href.substring("https://".length())) : null;
            if (host != null) {
                if (isIp(host)) {
                    ipHosts.add(host);
                } else if (isBlocked(host, limits.blockedHosts())) {
                    shortenerHosts.add(host);
                }
                domains.add(isIp(host) ? host : mainDomain(host, limits.secondLevelSuffixes()));
            }
            String text = normalizeText(link.text());
            if (!textMatches(text, mailto, host, limits.fileExtensions())) {
                mismatches.add(text + " → " + (mailto != null ? mailto : host));
            }
        }
        List<Violation> violations = new ArrayList<>();
        if (!shortenerHosts.isEmpty()) {
            violations.add(new Violation("shortener",
                    "Links zu Kurz-Link-Diensten sind nicht erlaubt: " + String.join(", ", shortenerHosts)));
        }
        if (!ipHosts.isEmpty()) {
            violations.add(new Violation("ip",
                    "Links zu IP-Adressen sind nicht erlaubt: " + String.join(", ", ipHosts)));
        }
        if (!mismatches.isEmpty()) {
            violations.add(new Violation("text-mismatch",
                    "Der Linktext zeigt eine andere Adresse als das Ziel: " + String.join("; ", mismatches)));
        }
        if (!placeholders.isEmpty()) {
            // eeg.website/eeg.email become the real address when sent: another address than the target
            violations.add(new Violation("placeholder-in-link",
                    "Platzhalter dürfen nicht in einem Link stehen: " + String.join(", ", placeholders)));
        }
        if (links.size() > limits.maxLinks()) {
            violations.add(new Violation("too-many-links",
                    "Höchstens " + limits.maxLinks() + " Links pro Mail (enthalten: " + links.size() + ")."));
        }
        if (domains.size() > limits.maxLinkDomains()) {
            violations.add(new Violation("too-many-domains", "Links zu höchstens " + limits.maxLinkDomains()
                    + " verschiedenen Domains pro Mail (enthalten: " + String.join(", ", domains) + ")."));
        }
        return violations;
    }

    /**
     * The host of what follows "https://": without user info and port, lower case, the dots
     * browsers accept (。．｡) as ".", no trailing dot.
     */
    private static String host(String afterScheme) {
        String host = mapDots(hostOfAuthority(afterScheme.split("[/?#\\s]", 2)[0]).toLowerCase(Locale.ROOT));
        return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    }

    /** The host of an authority: without user info (up to the last "@") and port. */
    private static String hostOfAuthority(String authority) {
        String a = authority.substring(authority.lastIndexOf('@') + 1);
        if (a.startsWith("[")) {
            int end = a.indexOf(']');
            return end < 0 ? a : a.substring(0, end + 1);
        }
        int colon = a.indexOf(':');
        return colon < 0 ? a : a.substring(0, colon);
    }

    private static String mapDots(String s) {
        return s.replace('。', '.').replace('．', '.').replace('｡', '.');
    }

    /**
     * An IPv6 literal, or labels that are all numbers in decimal or 0x hex (1.2.3.4, 3232235777,
     * 0x7f.1). A single hex word like "cafe" is a name.
     */
    private static boolean isIp(String host) {
        if (host.startsWith("[")) {
            return true; // IPv6 literal
        }
        for (String label : host.split("\\.", -1)) {
            if (!NUMERIC_LABEL.matcher(label).matches()) {
                return false;
            }
        }
        return true;
    }

    /** The host or one of its parent domains is on the list. */
    private static boolean isBlocked(String host, Set<String> blocked) {
        for (String domain = host; ; domain = domain.substring(domain.indexOf('.') + 1)) {
            if (blocked.contains(domain)) {
                return true;
            }
            if (domain.indexOf('.') < 0) {
                return false;
            }
        }
    }

    /**
     * The main (registrable) domain: the last two labels, or the last three when the last two are a
     * second-level suffix such as co.at ("portal.salzburgnetz.at" → "salzburgnetz.at",
     * "www.land.gv.at" → "land.gv.at").
     */
    private static String mainDomain(String host, Set<String> secondLevelSuffixes) {
        String[] labels = host.split("\\.");
        int n = labels.length;
        if (n <= 2) {
            return host;
        }
        String lastTwo = labels[n - 2] + "." + labels[n - 1];
        return secondLevelSuffixes.contains(lastTwo) ? labels[n - 3] + "." + lastTwo : lastTwo;
    }

    /** The host a link text is compared with: lower case, dots mapped, no trailing dot, no "www.". */
    private static String domainOf(String host) {
        String h = mapDots(host.toLowerCase(Locale.ROOT));
        h = h.endsWith(".") ? h.substring(0, h.length() - 1) : h;
        return h.startsWith("www.") ? h.substring(4) : h;
    }

    /** The address of a mailto href: up to "?" or "#", percent-decoded, lower case. */
    private static String mailtoAddress(String href) {
        String address = href.substring("mailto:".length()).split("[?#]", 2)[0];
        return percentDecode(address).strip().toLowerCase(Locale.ROOT);
    }

    /** %XX decoded as UTF-8 ("+" stays); a malformed escape leaves the value as it is (as in Go). */
    private static String percentDecode(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length);
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] != '%') {
                out.write(bytes[i]);
                continue;
            }
            int high = i + 2 < bytes.length ? Character.digit(bytes[i + 1], 16) : -1;
            int low = i + 2 < bytes.length ? Character.digit(bytes[i + 2], 16) : -1;
            if (high < 0 || low < 0) {
                return s;
            }
            out.write(high * 16 + low);
            i += 2;
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    /** Zero-width characters removed, dots mapped, every Unicode space one plain space, trimmed. */
    private static String normalizeText(String text) {
        String t = TEXT_SPACE.matcher(mapDots(ZERO_WIDTH.matcher(text).replaceAll(""))).replaceAll(" ");
        int start = t.startsWith(" ") ? 1 : 0;
        int end = t.length() > start && t.endsWith(" ") ? t.length() - 1 : t.length();
        return t.substring(start, end);
    }

    /**
     * Every address the link text shows is the link's own: a web address the host of an https link,
     * an e-mail address the address of a mailto link. A text without addresses is fine.
     */
    private static boolean textMatches(String text, String mailto, String host, Set<String> fileExtensions) {
        for (String word : text.split(" ")) {
            int start = 0;
            int end = word.length();
            while (start < end && WORD_PUNCT.indexOf(word.charAt(start)) >= 0) {
                start++;
            }
            while (end > start && (WORD_PUNCT.indexOf(word.charAt(end - 1)) >= 0 || word.charAt(end - 1) == '.')) {
                end--;
            }
            String w = word.substring(start, end);
            String lower = w.toLowerCase(Locale.ROOT);
            String mail = lower.startsWith("mailto:") ? lower.substring("mailto:".length()) : lower;
            String shownHost;
            if (w.isEmpty()) {
                continue;
            } else if (w.contains("://")) {
                shownHost = hostOfAuthority(upToPathEnd(w.substring(w.indexOf("://") + 3)));
            } else if (lower.startsWith("www.")) {
                shownHost = upToPathEnd(w);
            } else if (EMAIL.matcher(mail).matches()) {
                if (!mail.equals(mailto)) {
                    return false;
                }
                continue;
            } else if (BARE_DOMAIN.matcher(w).matches() && !isFileName(upToPathEnd(w), fileExtensions)) {
                shownHost = upToPathEnd(w);
            } else {
                continue;
            }
            if (host == null || !domainOf(shownHost).equals(domainOf(host))) {
                return false;
            }
        }
        return true;
    }

    private static String upToPathEnd(String s) {
        return s.split("[/?#]", 2)[0];
    }

    /** "anleitung.pdf" is a file name, not an address. */
    private static boolean isFileName(String host, Set<String> fileExtensions) {
        return fileExtensions.contains(host.substring(host.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT));
    }
}
