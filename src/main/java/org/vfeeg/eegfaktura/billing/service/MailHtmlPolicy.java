package org.vfeeg.eegfaktura.billing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.owasp.html.AttributePolicy;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.HtmlSanitizer;
import org.owasp.html.HtmlStreamEventReceiver;
import org.owasp.html.HtmlStreamRenderer;
import org.owasp.html.PolicyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
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

    /** File ids are lower-case UUIDs, compared exactly (an upper-case id is not a file, as in the backend). */
    private static final Pattern FILE_ID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

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
        fragmentPolicy = builder(Set.of()).toFactory();
    }

    /** Policy for HTML values without own images (e.g. the footer text), built once. */
    private final PolicyFactory fragmentPolicy;

    /** Sanitizes an HTML value (no mailfile images except the built-in logo). */
    public String sanitizeFragment(String html) {
        return fragmentPolicy.sanitize(html == null ? "" : html);
    }

    /**
     * Passes the elements and text of already sanitized HTML to {@code out}, without document
     * events; images are left out (a value has no images of its own).
     */
    public void forward(String sanitizedHtml, HtmlStreamEventReceiver out) {
        HtmlSanitizer.sanitize(sanitizedHtml == null ? "" : sanitizedHtml, new HtmlSanitizer.Policy() {
            @Override public void openDocument() { }
            @Override public void closeDocument() { }
            @Override public void openTag(String name, List<String> attrs) {
                if (!"img".equals(name)) {
                    out.openTag(name, attrs);
                }
            }
            @Override public void closeTag(String name) {
                if (!"img".equals(name)) {
                    out.closeTag(name);
                }
            }
            @Override public void text(String text) {
                out.text(text);
            }
        });
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

    /**
     * The file id of a {@code mailfile:<id>} src (scheme case-insensitive, id exact): a lower-case
     * UUID or the built-in logo id; otherwise null.
     */
    public static String mailfileId(String src) {
        if (src == null) {
            return null;
        }
        String trimmed = src.trim();
        int colon = trimmed.indexOf(':');
        if (colon <= 0 || !MAILFILE_SCHEME.equals(trimmed.substring(0, colon).toLowerCase(Locale.ROOT))) {
            return null;
        }
        String id = trimmed.substring(colon + 1);
        return BUILTIN_LOGO_ID.equals(id) || FILE_ID.matcher(id).matches() ? id : null;
    }

    /**
     * Attributes an element cannot do without: {@code required} of the rule file plus its URL
     * attributes ({@code a.href}, {@code img.src}), as in the backend. Without them a span/div/a
     * is unwrapped (its content stays) and an img is removed.
     */
    private List<String> requiredAttributes(String element) {
        List<String> attributes = new ArrayList<>(required.getOrDefault(element, List.of()));
        for (String key : urlSchemes.keySet()) {
            int dot = key.indexOf('.');
            if (dot > 0 && key.substring(0, dot).equals(element)) {
                attributes.add(key.substring(dot + 1));
            }
        }
        return attributes;
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
            List<String> requiredAttributes = requiredAttributes(name);
            if (requiredAttributes.isEmpty()) {
                builder.allowElements(name);
            } else {
                // runs after the attribute policies: a removed src/href/data-ph counts as missing
                builder.allowElements((elementName, attrs) -> requiredAttributes.stream()
                        .allMatch(a -> attribute(attrs, a) != null) ? elementName : null, name);
            }
            for (String attribute : element.getValue()) {
                builder.allowAttributes(attribute)
                        .matching(attributePolicy(name, attribute, allowedFileIds))
                        .onElements(name);
            }
        }
        if (!linkRel.isEmpty()) {
            builder.requireRelsOnLinks(linkRel.toArray(String[]::new));
        }
        return builder;
    }

    private AttributePolicy attributePolicy(String element, String attribute, Set<String> allowedFileIds) {
        if ("style".equals(attribute)) {
            return (e, a, value) -> style(value);
        }
        // data-ph is kept as written; an unknown key is a 400 of the service (as in the backend)
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
        if ("https".equals(scheme)) {
            // an https link needs a host (as in the backend), e.g. not "https:/path"
            try {
                String host = new URI(trimmed).getHost();
                if (host == null || host.isEmpty()) {
                    return null;
                }
            } catch (URISyntaxException e) {
                return null;
            }
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
                kept.add(property + ": " + propertyValue);
            }
        }
        // same output as the backend: "text-align: center"; empty declarations (";;") are skipped
        return kept.isEmpty() ? null : String.join("; ", kept);
    }
}
