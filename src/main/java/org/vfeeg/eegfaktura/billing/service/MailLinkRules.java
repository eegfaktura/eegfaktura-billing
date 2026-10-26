package org.vfeeg.eegfaktura.billing.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Link rules of EEG-editable mails (Variante A, decided 2026-10-26; the same rules as in
 * eegfaktura-backend, shared cases in {@code mail-link-cases.json}): no IP hosts, no URL shorteners
 * ({@code links.blockedHosts} of {@code mail-html-policy.json}), a link text that shows an address
 * shows the link's own address, at most {@code maxLinks} links and {@code maxLinkDomains} domains.
 * Runs over the sanitized body only; the server's fixed footer is not part of it.
 */
class MailLinkRules {

    /** A broken rule: a key of the shared case file and the German message for the editor. */
    record Violation(String key, String message) {
    }

    /** A host whose last label is a number (decimal or 0x hex) is an IPv4 address to browsers. */
    private static final Pattern NUMERIC_LABEL = Pattern.compile("[0-9]+|0x[0-9a-f]*");
    /** A bare domain, optionally with a path: "example.at", "example.at/pfad". */
    private static final Pattern BARE_DOMAIN =
            Pattern.compile("[\\p{L}\\p{N}-]+(\\.[\\p{L}\\p{N}-]+)*\\.\\p{L}{2,}([/?#:]\\S*)?");
    private static final Pattern EMAIL = Pattern.compile("[^\\s@/:]+@[^\\s@/]+\\.[^\\s@/]+");

    private final MailHtmlPolicy policy;

    MailLinkRules(MailHtmlPolicy policy) {
        this.policy = policy;
    }

    /** The broken rules of a sanitized body, one per key, in the order of the case file's keys. */
    List<Violation> check(String sanitizedHtml) {
        MailHtmlPolicy.LinkLimits limits = policy.linkLimits();
        List<MailHtmlPolicy.Link> links = policy.links(sanitizedHtml);
        Set<String> ipHosts = new LinkedHashSet<>();
        Set<String> shortenerHosts = new LinkedHashSet<>();
        List<String> mismatches = new ArrayList<>();
        Set<String> domains = new LinkedHashSet<>();
        for (MailHtmlPolicy.Link link : links) {
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
                domains.add(withoutWww(host));
            }
            String shown = shownAddress(link.text());
            if (shown != null && !shown.equals(mailto != null ? mailto : host == null ? null : withoutWww(host))) {
                mismatches.add(link.text().strip() + " → " + (mailto != null ? mailto : host));
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

    /** The host of what follows "scheme://": without user info and port, lower case, no trailing dot. */
    private static String host(String afterScheme) {
        String authority = afterScheme.split("[/?#\\s]", 2)[0];
        authority = authority.substring(authority.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
        if (authority.startsWith("[")) {
            int end = authority.indexOf(']');
            return end < 0 ? authority : authority.substring(0, end + 1);
        }
        int colon = authority.indexOf(':');
        String host = colon < 0 ? authority : authority.substring(0, colon);
        return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    }

    private static boolean isIp(String host) {
        if (host.startsWith("[")) {
            return true; // IPv6 literal
        }
        String last = host.substring(host.lastIndexOf('.') + 1);
        return NUMERIC_LABEL.matcher(last).matches();
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

    private static String withoutWww(String host) {
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private static String mailtoAddress(String href) {
        String address = href.substring("mailto:".length()).split("\\?", 2)[0];
        return address.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * The address a link text shows, normalised for comparison (e-mail lower case, host lower case
     * without "www."), or null if the text does not look like an address.
     */
    private static String shownAddress(String text) {
        String t = text.strip();
        int scheme = t.indexOf("://");
        if (scheme >= 0) {
            return withoutWww(host(t.substring(scheme + 3)));
        }
        if (t.toLowerCase(Locale.ROOT).startsWith("mailto:")) {
            t = t.substring("mailto:".length());
        }
        if (EMAIL.matcher(t).matches()) {
            return t.toLowerCase(Locale.ROOT);
        }
        if (t.regionMatches(true, 0, "www.", 0, 4) || BARE_DOMAIN.matcher(t).matches()) {
            return withoutWww(host(t));
        }
        return null;
    }
}
