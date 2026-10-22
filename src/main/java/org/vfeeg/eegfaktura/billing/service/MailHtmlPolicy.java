package org.vfeeg.eegfaktura.billing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.owasp.html.AttributePolicy;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.HtmlSanitizer;
import org.owasp.html.HtmlStreamEventReceiver;
import org.owasp.html.HtmlStreamRenderer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Allowlist sanitizer for EEG-edited mail HTML, driven by {@code mail-html-policy.json} (the same
 * file is in eegfaktura-backend; change both together). OWASP java-html-sanitizer does the
 * parsing and filtering; this class only translates the rule file into a policy.
 *
 * <ul>
 *   <li>elements and their attributes as listed in {@code elements};</li>
 *   <li>URL attributes ({@code urlSchemes}, e.g. {@code a.href}) only with the listed schemes, no
 *       relative URLs; {@code img src="mailfile:<id>"} only for the ids passed in (the files of
 *       this tenant and mail);</li>
 *   <li>{@code style} only with the properties/values in {@code styles};</li>
 *   <li>elements in {@code required} without their required attribute are unwrapped;</li>
 *   <li>links get {@code rel} from {@code linkRel}.</li>
 * </ul>
 */
@Component
public class MailHtmlPolicy {

    public static final String MAILFILE_SCHEME = "mailfile";
    public static final String PLACEHOLDER_ATTRIBUTE = "data-ph";
    /** Built-in image every tenant may use ({@code mailfile:eegfaktura-logo}), as in eegfaktura-backend. */
    public static final String BUILTIN_LOGO_ID = "eegfaktura-logo";

    private static final Pattern PLACEHOLDER_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9.]{0,63}");
    private static final Pattern MAILFILE_SRC = Pattern.compile(
            "mailfile:([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");

    private final Map<String, List<String>> elements = new LinkedHashMap<>();
    private final Map<String, List<String>> urlSchemes = new LinkedHashMap<>();
    private final Map<String, List<String>> styles = new LinkedHashMap<>();
    private final Map<String, List<String>> required = new LinkedHashMap<>();
    private final List<String> linkRel = new ArrayList<>();

    public MailHtmlPolicy() {
        try (InputStream in = new ClassPathResource("mail-html-policy.json").getInputStream()) {
            JsonNode root = new ObjectMapper().readTree(in);
            readListMap(root.path("elements"), elements);
            readListMap(root.path("urlSchemes"), urlSchemes);
            readListMap(root.path("styles"), styles);
            readListMap(root.path("required"), required);
            for (String rel : root.path("linkRel").asText("").trim().split("\\s+")) {
                if (!rel.isEmpty()) {
                    linkRel.add(rel);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("mail-html-policy.json not readable", e);
        }
        if (elements.isEmpty()) {
            throw new IllegalStateException("mail-html-policy.json has no elements");
        }
    }

    private static void readListMap(JsonNode node, Map<String, List<String>> target) {
        node.fields().forEachRemaining(entry -> {
            List<String> values = new ArrayList<>();
            entry.getValue().forEach(v -> values.add(v.asText()));
            target.put(entry.getKey(), values);
        });
    }

    /** Sanitizes {@code html}; images are kept only for the given mailfile ids (lowercase UUIDs). */
    public String sanitize(String html, Set<String> allowedFileIds) {
        StringBuilder out = new StringBuilder(html == null ? 0 : html.length());
        HtmlStreamRenderer renderer = HtmlStreamRenderer.create(out, badHtml -> { });
        HtmlSanitizer.sanitize(html == null ? "" : html, builder(allowedFileIds).build(renderer));
        return out.toString();
    }

    /**
     * Re-renders already sanitized HTML, passing its events through {@code wrap} (to replace
     * placeholders and image sources). No policy is built: callers sanitize once (e.g. once per
     * send run) and walk the clean HTML per mail.
     */
    public String walk(String sanitizedHtml, UnaryOperator<HtmlStreamEventReceiver> wrap) {
        StringBuilder out = new StringBuilder(sanitizedHtml == null ? 0 : sanitizedHtml.length());
        HtmlStreamEventReceiver receiver = wrap.apply(HtmlStreamRenderer.create(out, badHtml -> { }));
        HtmlSanitizer.sanitize(sanitizedHtml == null ? "" : sanitizedHtml, new HtmlSanitizer.Policy() {
            @Override public void openDocument() {
                receiver.openDocument();
            }
            @Override public void closeDocument() {
                receiver.closeDocument();
            }
            @Override public void openTag(String name, List<String> attrs) {
                receiver.openTag(name, attrs);
            }
            @Override public void closeTag(String name) {
                receiver.closeTag(name);
            }
            @Override public void text(String text) {
                receiver.text(text);
            }
        });
        return out.toString();
    }

    /** The placeholder keys and mailfile ids in already sanitized HTML, in document order. */
    public Inspection inspect(String sanitizedHtml) {
        Inspection inspection = new Inspection(new LinkedHashSet<>(), new LinkedHashSet<>(), new StringBuilder());
        HtmlSanitizer.sanitize(sanitizedHtml == null ? "" : sanitizedHtml, new HtmlSanitizer.Policy() {
            @Override public void openDocument() { }
            @Override public void closeDocument() { }
            @Override public void closeTag(String name) { }
            @Override public void text(String text) {
                inspection.text().append(text);
            }
            @Override public void openTag(String name, List<String> attrs) {
                String placeholder = attribute(attrs, PLACEHOLDER_ATTRIBUTE);
                if (placeholder != null) {
                    inspection.placeholderKeys().add(placeholder);
                }
                String fileId = "img".equals(name) ? mailfileId(attribute(attrs, "src")) : null;
                if (fileId != null) {
                    inspection.fileIds().add(fileId);
                }
            }
        });
        return inspection;
    }

    /** Placeholder keys, mailfile ids and the plain text of a sanitized HTML. */
    public record Inspection(Set<String> placeholderKeys, Set<String> fileIds, StringBuilder text) {
        /** True if the HTML has neither visible text, nor placeholders, nor images. */
        public boolean isEmpty() {
            return text.toString().isBlank() && placeholderKeys.isEmpty() && fileIds.isEmpty();
        }
    }

    /** The value of an attribute in OWASP's flat name/value list, or null. */
    public static String attribute(List<String> attrs, String name) {
        for (int i = 0; i + 1 < attrs.size(); i += 2) {
            if (name.equals(attrs.get(i))) {
                return attrs.get(i + 1);
            }
        }
        return null;
    }

    /** The lowercase file id of a {@code mailfile:<uuid>} src, the built-in logo id, or null. */
    public static String mailfileId(String src) {
        if (src == null) {
            return null;
        }
        if ((MAILFILE_SCHEME + ":" + BUILTIN_LOGO_ID).equalsIgnoreCase(src.trim())) {
            return BUILTIN_LOGO_ID;
        }
        var matcher = MAILFILE_SRC.matcher(src.trim());
        return matcher.matches() ? matcher.group(1).toLowerCase(Locale.ROOT) : null;
    }

    private HtmlPolicyBuilder builder(Set<String> allowedFileIds) {
        HtmlPolicyBuilder builder = new HtmlPolicyBuilder();
        Set<String> allSchemes = new LinkedHashSet<>();
        urlSchemes.values().forEach(allSchemes::addAll);
        if (!allSchemes.isEmpty()) {
            builder.allowUrlProtocols(allSchemes.toArray(String[]::new));
        }
        for (Map.Entry<String, List<String>> element : elements.entrySet()) {
            String name = element.getKey();
            builder.allowElements(name);
            for (String attribute : element.getValue()) {
                builder.allowAttributes(attribute)
                        .matching(attributePolicy(name, attribute, allowedFileIds))
                        .onElements(name);
            }
        }
        required.keySet().forEach(builder::disallowWithoutAttributes);
        if (!linkRel.isEmpty()) {
            builder.requireRelsOnLinks(linkRel.toArray(String[]::new));
        }
        return builder;
    }

    private AttributePolicy attributePolicy(String element, String attribute, Set<String> allowedFileIds) {
        if ("style".equals(attribute)) {
            return (e, a, value) -> style(value);
        }
        if (PLACEHOLDER_ATTRIBUTE.equals(attribute)) {
            return (e, a, value) -> PLACEHOLDER_KEY.matcher(value).matches() ? value : null;
        }
        List<String> schemes = urlSchemes.get(element + "." + attribute);
        if (schemes != null) {
            return (e, a, value) -> url(value, schemes, allowedFileIds);
        }
        return AttributePolicy.IDENTITY_ATTRIBUTE_POLICY;
    }

    private static String url(String value, List<String> schemes, Set<String> allowedFileIds) {
        String trimmed = value.trim();
        int colon = trimmed.indexOf(':');
        if (colon <= 0) {
            return null; // relative URL or none at all
        }
        String scheme = trimmed.substring(0, colon).toLowerCase(Locale.ROOT);
        if (!schemes.contains(scheme)) {
            return null;
        }
        if (MAILFILE_SCHEME.equals(scheme)) {
            String id = mailfileId(trimmed);
            return id != null && (BUILTIN_LOGO_ID.equals(id) || allowedFileIds != null && allowedFileIds.contains(id))
                    ? MAILFILE_SCHEME + ":" + id : null;
        }
        return trimmed;
    }

    private String style(String value) {
        List<String> kept = new ArrayList<>();
        for (String declaration : value.split(";")) {
            int colon = declaration.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String property = declaration.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String propertyValue = declaration.substring(colon + 1).trim().toLowerCase(Locale.ROOT);
            List<String> allowedValues = styles.get(property);
            if (allowedValues != null && allowedValues.contains(propertyValue)) {
                kept.add(property + ":" + propertyValue);
            }
        }
        return kept.isEmpty() ? null : String.join(";", kept);
    }
}
