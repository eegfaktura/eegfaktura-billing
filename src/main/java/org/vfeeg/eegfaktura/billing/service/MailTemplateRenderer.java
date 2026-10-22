package org.vfeeg.eegfaktura.billing.service;

import org.owasp.html.HtmlStreamEventReceiver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders an EEG mail text: placeholders are replaced as values only (never a template engine on
 * EEG text). In the body the whole {@code <span|div data-ph>} element is replaced by the escaped
 * value; images {@code mailfile:<id>} get the src the caller chooses (cid: for sending, data: for
 * the preview). The body is sanitized again on every render.
 */
@Component
public class MailTemplateRenderer {

    /** Placeholders in the subject: plain-text tokens {@code {key}} (section H); a lone brace stays text. */
    private static final Pattern SUBJECT_PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9.]{0,63})}");
    private static final Set<String> VOID_ELEMENTS = Set.of("br", "img");

    /** Fixed frame around the EEG text, as today's BillingEmailDefaultTemplate.ftl. */
    private static final String FRAME = """
            <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
            <html lang="de">
            <head>
              <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
              <style type="text/css">
                a {text-decoration: none}
                #MAILSTYLE ul {list-style-type: disc; padding-inline-start: 40px; margin: 0px;}
                #MAILSTYLE ol {list-style-type: decimal; padding-inline-start: 40px; margin: 0px;}
                #MAILSTYLE img {max-width: 100%; height: auto;}
              </style>
            </head>
            <body style="background-color:#eee;">
            <table width="100%" height="80%" cellpadding="10" cellspacing="0" border="0" style="background-color:#eee;">
              <tr><td width="50%">&nbsp;</td><td align="center">
                  <table id="MAILSTYLE" cellpadding="0" cellspacing="0" border="0" style="empty-cells: show; width: 595px; border-collapse: collapse; background-color: white;">
            {{LOGO}}        <tr valign="top">
                      <td style="padding: 10px 25px 20px 30px; text-align: left;">
            {{BODY}}
                      </td>
                    </tr>
                  </table>
                </td><td width="50%">&nbsp;</td></tr>
            </table>
            </body>
            </html>
            """;

    private static final String LOGO_ROW = """
                    <tr valign="top" style="height:205px">
                      <td align="center"><p><img src="{{SRC}}" alt="Logo"/></p></td>
                    </tr>
            """;

    private final MailHtmlPolicy mailHtmlPolicy;

    public MailTemplateRenderer(MailHtmlPolicy mailHtmlPolicy) {
        this.mailHtmlPolicy = mailHtmlPolicy;
    }

    /** The placeholder keys used in a subject. */
    public static Set<String> subjectPlaceholders(String subject) {
        Set<String> keys = new LinkedHashSet<>();
        Matcher matcher = SUBJECT_PLACEHOLDER.matcher(subject == null ? "" : subject);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    /**
     * The subject with its placeholders replaced. Line breaks and control characters (also in the
     * values) become spaces; the result is a single header line.
     *
     * @throws IllegalStateException for a placeholder that is not in {@code knownKeys}
     */
    public String renderSubject(String subject, Map<String, String> values, Set<String> knownKeys) {
        Matcher matcher = SUBJECT_PLACEHOLDER.matcher(subject == null ? "" : subject);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!knownKeys.contains(key)) {
                throw new IllegalStateException("unknown placeholder in subject: " + key);
            }
            String value = values.get(key);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(sb);
        return sb.toString().replaceAll("[\\p{Cntrl}\\u2028\\u2029]+", " ").replaceAll(" {2,}", " ").strip();
    }

    /**
     * The body HTML with placeholders replaced by their escaped values. Line breaks in a value
     * become {@code <br>}; a block placeholder written as {@code <div>} stays a {@code <div>}.
     *
     * @param imageSrc maps a mailfile id (lowercase UUID) to the src to use; null drops the image
     * @throws IllegalStateException for a placeholder that is not in {@code knownKeys}
     */
    public String renderBody(String bodyHtml, Set<String> fileIds, Map<String, String> values,
                             Set<String> knownKeys, Set<String> blockKeys, Function<String, String> imageSrc) {
        return mailHtmlPolicy.process(bodyHtml, fileIds,
                out -> new Substitution(out, values, knownKeys, blockKeys, imageSrc));
    }

    /** The complete mail HTML: frame, optional logo on top (src as given), and the rendered body. */
    public String frame(String renderedBody, String logoSrc) {
        String logo = logoSrc == null ? "" : LOGO_ROW.replace("{{SRC}}", logoSrc);
        // the body last, so text in it is never taken for a frame marker
        return FRAME.replace("{{LOGO}}", logo).replace("{{BODY}}", renderedBody);
    }

    /** Replaces placeholder elements and image sources on the way to the renderer. */
    private static final class Substitution implements HtmlStreamEventReceiver {
        private final HtmlStreamEventReceiver out;
        private final Map<String, String> values;
        private final Set<String> knownKeys;
        private final Set<String> blockKeys;
        private final Function<String, String> imageSrc;
        /** > 0 while inside a placeholder element whose content is replaced. */
        private int skipDepth;

        Substitution(HtmlStreamEventReceiver out, Map<String, String> values, Set<String> knownKeys,
                     Set<String> blockKeys, Function<String, String> imageSrc) {
            this.out = out;
            this.values = values;
            this.knownKeys = knownKeys;
            this.blockKeys = blockKeys;
            this.imageSrc = imageSrc;
        }

        @Override
        public void openDocument() {
            out.openDocument();
        }

        @Override
        public void closeDocument() {
            out.closeDocument();
        }

        @Override
        public void openTag(String name, List<String> attrs) {
            if (skipDepth > 0) {
                if (!VOID_ELEMENTS.contains(name)) {
                    skipDepth++;
                }
                return;
            }
            String key = MailHtmlPolicy.attribute(attrs, MailHtmlPolicy.PLACEHOLDER_ATTRIBUTE);
            if (key != null) {
                if (!knownKeys.contains(key)) {
                    throw new IllegalStateException("unknown placeholder: " + key);
                }
                emitValue(name, key);
                skipDepth = 1;
                return;
            }
            if ("img".equals(name)) {
                String id = MailHtmlPolicy.mailfileId(MailHtmlPolicy.attribute(attrs, "src"));
                String src = id == null ? null : imageSrc.apply(id);
                if (src == null) {
                    return;
                }
                List<String> rewritten = new ArrayList<>(attrs);
                for (int i = 0; i + 1 < rewritten.size(); i += 2) {
                    if ("src".equals(rewritten.get(i))) {
                        rewritten.set(i + 1, src);
                    }
                }
                out.openTag(name, rewritten);
                return;
            }
            out.openTag(name, attrs);
        }

        private void emitValue(String element, String key) {
            String value = values.get(key);
            if (value == null || value.isEmpty()) {
                return; // a placeholder without value renders as empty text, never as the raw key
            }
            // line breaks in a value (e.g. the footer text) become <br>; block placeholders
            // written as <div> stay a block
            boolean div = "div".equals(element) && blockKeys.contains(key);
            if (div) {
                out.openTag("div", List.of());
            }
            String[] lines = value.split("\n");
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) {
                    out.openTag("br", List.of());
                }
                out.text(lines[i]);
            }
            if (div) {
                out.closeTag("div");
            }
        }

        @Override
        public void closeTag(String name) {
            if (skipDepth > 0) {
                if (!VOID_ELEMENTS.contains(name)) {
                    skipDepth--;
                }
                return;
            }
            out.closeTag(name);
        }

        @Override
        public void text(String text) {
            if (skipDepth == 0) {
                out.text(text);
            }
        }
    }
}
