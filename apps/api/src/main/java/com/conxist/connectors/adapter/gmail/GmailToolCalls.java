package com.conxist.connectors.adapter.gmail;

import static com.conxist.connectors.adapter.api.ToolArguments.encode;
import static com.conxist.connectors.adapter.api.ToolArguments.ordered;
import static com.conxist.connectors.adapter.api.ToolArguments.text;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.conxist.connectors.application.IntegrationApi;
import com.conxist.connectors.application.IntegrationApi.Problem;
import com.conxist.connectors.domain.GmailTools;
import com.conxist.connectors.domain.Integration;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a call to one of ConXist's Gmail tools into GET requests and a short answer (ADR 0021). Every search runs
 * inside one allowed label at a time (or all mail but Spam and Trash), at most 20 labels and 50 emails read, and every
 * email read is checked to be in one of them; one that is not answers as if it did not exist. Emails are untrusted
 * data, passed on as data.
 */
final class GmailToolCalls {

    static final int MAX_RESULTS = 25;
    static final int MAX_LABELS_SEARCHED = 20;
    static final int MAX_THREAD = 50;
    static final int MAX_EMAIL_CHARACTERS = 20_000;
    static final int MAX_THREAD_EMAIL_CHARACTERS = 4_000;
    /** At most this many emails are read in one search, newest of each label first, before keeping 25. */
    static final int MAX_FETCHED = MAX_RESULTS * 2;
    /** HTML past this is not read: enough for 20,000 characters of text, and a bound on the work. */
    static final int MAX_HTML = 400_000;
    private static final Set<String> SKIPPED_ELEMENTS = Set.of("script", "style", "head");
    private static final Pattern ENTITY = Pattern.compile("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|nbsp|lt|gt|quot|apos|amp);");
    private static final Set<String> NEVER = Set.of("SPAM", "TRASH");
    /** Gmail search words that reach Spam, Trash or everywhere: {@code in:} in any form. */
    private static final Pattern IN = Pattern.compile("(?i)^[-+({]*in:.*");

    private final Getter gmail;
    private final JsonMapper json = JsonMapper.builder().build();

    GmailToolCalls(Getter gmail) {
        this.gmail = gmail;
    }

    /** One GET request to Gmail's API for the signed-in person; see {@link HttpGmailApi}. */
    @FunctionalInterface
    interface Getter {
        String get(String credential, String path, List<String[]> query);
    }

    String call(Integration integration, String credential, String tool, Map<String, Object> arguments) {
        return switch (tool) {
            case GmailTools.SEARCH_EMAILS -> search(integration, credential, arguments);
            case GmailTools.GET_EMAIL -> email(integration, credential, arguments);
            case GmailTools.GET_THREAD -> thread(integration, credential, arguments);
            case GmailTools.LIST_LABELS -> write(ordered("allMail", integration.allMail(),
                    "labels", integration.allMail() ? scope(integration, credential).names() : integration.repositories()));
            default -> throw new Problem("ConXist has no Gmail tool called " + tool + ".");
        };
    }

    private String search(Integration integration, String credential, Map<String, Object> arguments) {
        String query = plainQuery(text(arguments, "query", 300, false));
        String label = text(arguments, "label", 225, false);
        Scope scope = scope(integration, credential);
        List<String> within;
        if (!label.isEmpty()) {
            String id = scope.byName().get(label.toLowerCase(Locale.ROOT));
            if (id == null || !scope.allows(Set.of(id))) {
                throw new Problem("Use one of the allowed labels: " + String.join(", ", scope.names()) + ".");
            }
            within = List.of(id);
        } else {
            within = scope.allMail() ? List.of("") : scope.allowed().stream().limit(MAX_LABELS_SEARCHED).toList();
        }
        int notSearched = label.isEmpty() && !scope.allMail() ? Math.max(0, scope.allowed().size() - within.size()) : 0;
        List<List<String>> perLabel = new ArrayList<>();
        for (String id : within) {
            List<String[]> parameters = new ArrayList<>();
            parameters.add(new String[] {"maxResults", String.valueOf(MAX_RESULTS)});
            if (!id.isEmpty()) {
                parameters.add(new String[] {"labelIds", id});
            }
            if (!query.isEmpty()) {
                parameters.add(new String[] {"q", query});
            }
            List<String> found = new ArrayList<>();
            read(gmail.get(credential, "/messages", parameters)).path("messages")
                    .forEach(each -> found.add(each.path("id").asString("")));
            perLabel.add(found);
        }
        Set<String> ids = newestOfEach(perLabel);
        List<JsonNode> emails = new ArrayList<>();
        for (String id : ids) {
            if (!GmailTools.ID.matcher(id).matches()) {
                continue;
            }
            JsonNode email = read(gmail.get(credential, "/messages/" + encode(id), List.of(
                    new String[] {"format", "metadata"}, new String[] {"metadataHeaders", "From"},
                    new String[] {"metadataHeaders", "Subject"}, new String[] {"metadataHeaders", "Date"})));
            if (scope.allows(labelIds(email))) {
                emails.add(email);
            }
        }
        emails.sort(Comparator.comparingLong((JsonNode email) -> email.path("internalDate").asLong(0)).reversed());
        List<Map<String, Object>> found = new ArrayList<>();
        for (JsonNode email : emails.subList(0, Math.min(MAX_RESULTS, emails.size()))) {
            Map<String, String> headers = headers(email);
            found.add(ordered("id", email.path("id").asString(""), "threadId", email.path("threadId").asString(""),
                    "from", headers.getOrDefault("from", ""), "subject", headers.getOrDefault("subject", ""),
                    "date", headers.getOrDefault("date", ""), "preview", email.path("snippet").asString(""),
                    "labels", scope.namesOf(labelIds(email))));
        }
        boolean more = emails.size() > found.size() || perLabel.stream().anyMatch(each -> each.size() >= MAX_RESULTS)
                || ids.size() >= MAX_FETCHED;
        return write(notSearched > 0
                ? ordered("emails", found, "more", more, "labelsNotSearched", notSearched)
                : ordered("emails", found, "more", more));
    }

    /** Gmail lists each label newest first: take the first of each in turn, so one label can't crowd out the rest. */
    static Set<String> newestOfEach(List<List<String>> perLabel) {
        Set<String> ids = new LinkedHashSet<>();
        for (int at = 0; ids.size() < MAX_FETCHED; at++) {
            boolean any = false;
            for (List<String> found : perLabel) {
                if (at < found.size()) {
                    any = true;
                    if (ids.size() < MAX_FETCHED) {
                        ids.add(found.get(at));
                    }
                }
            }
            if (!any) {
                break;
            }
        }
        return ids;
    }

    private String email(Integration integration, String credential, Map<String, Object> arguments) {
        String id = id(arguments);
        Scope scope = scope(integration, credential);
        JsonNode email = read(gmail.get(credential, "/messages/" + encode(id), List.<String[]>of(new String[] {"format", "full"})));
        if (!scope.allows(labelIds(email))) {
            throw new IntegrationApi.Missing("not in the allowed labels");
        }
        return write(shown(email, scope, MAX_EMAIL_CHARACTERS));
    }

    private String thread(Integration integration, String credential, Map<String, Object> arguments) {
        String id = id(arguments);
        Scope scope = scope(integration, credential);
        JsonNode thread = read(gmail.get(credential, "/threads/" + encode(id), List.<String[]>of(new String[] {"format", "full"})));
        List<Map<String, Object>> emails = new ArrayList<>();
        int left = 0;
        for (JsonNode email : thread.path("messages")) {
            if (!scope.allows(labelIds(email))) {
                continue;
            }
            if (emails.size() >= MAX_THREAD) {
                left++;
                continue;
            }
            emails.add(shown(email, scope, MAX_THREAD_EMAIL_CHARACTERS));
        }
        if (emails.isEmpty()) {
            throw new IntegrationApi.Missing("no email of this conversation is in the allowed labels");
        }
        return write(ordered("threadId", id, "emails", emails, "more", left > 0));
    }

    /** What the labels allow, with Gmail's ids for them; Spam and Trash never. */
    private Scope scope(Integration integration, String credential) {
        Map<String, String> byName = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (JsonNode label : read(gmail.get(credential, "/labels", List.of())).path("labels")) {
            String id = label.path("id").asString("");
            String name = label.path("name").asString("");
            if (!id.isEmpty() && !name.isEmpty()) {
                byName.putIfAbsent(name.toLowerCase(Locale.ROOT), id);
                names.put(id, name);
            }
        }
        if (integration.allMail()) {
            return new Scope(true, List.of(), byName, names);
        }
        List<String> allowed = integration.repositories().stream()
                .map(name -> byName.get(name.toLowerCase(Locale.ROOT))).filter(id -> id != null).toList();
        if (allowed.isEmpty()) {
            throw new Problem("None of the allowed labels is in the mailbox any more. Ask the person to pick labels"
                    + " again in ConXist.");
        }
        return new Scope(false, allowed, byName, names);
    }

    private record Scope(boolean allMail, List<String> allowed, Map<String, String> byName,
            Map<String, String> labelNames) {

        boolean allows(Set<String> labelIds) {
            if (labelIds.stream().anyMatch(NEVER::contains)) {
                return false;
            }
            return allMail || labelIds.stream().anyMatch(allowed::contains);
        }

        /** The labels agents may name: the allowed ones, or with all mail the person's own (never Spam or Trash). */
        List<String> names() {
            return allMail
                    ? labelNames.entrySet().stream().filter(label -> label.getKey().startsWith("Label_"))
                            .map(Map.Entry::getValue).sorted(String.CASE_INSENSITIVE_ORDER).toList()
                    : allowed.stream().map(labelNames::get).toList();
        }

        /** The names of the email's labels agents may see: the allowed ones, or the person's own with all mail. */
        List<String> namesOf(Set<String> labelIds) {
            return labelIds.stream().filter(id -> allMail ? id.startsWith("Label_") : allowed.contains(id))
                    .map(labelNames::get).filter(name -> name != null).toList();
        }
    }

    private Map<String, Object> shown(JsonNode email, Scope scope, int maxCharacters) {
        Map<String, String> headers = headers(email);
        List<String> attachments = new ArrayList<>();
        String text = bodyText(email.path("payload"), attachments);
        boolean cut = text.length() > maxCharacters;
        return ordered("id", email.path("id").asString(""), "threadId", email.path("threadId").asString(""),
                "from", headers.getOrDefault("from", ""), "to", headers.getOrDefault("to", ""),
                "cc", headers.getOrDefault("cc", ""), "date", headers.getOrDefault("date", ""),
                "subject", headers.getOrDefault("subject", ""), "labels", scope.namesOf(labelIds(email)),
                "text", cut ? text.substring(0, maxCharacters) + "\n(cut here)" : text, "attachments", attachments);
    }

    /** The email's plain text, else its HTML as text; attachments by name only, never opened. */
    private static String bodyText(JsonNode payload, List<String> attachments) {
        List<String> plain = new ArrayList<>();
        List<String> html = new ArrayList<>();
        collect(payload, plain, html, attachments, 0);
        if (!plain.isEmpty()) {
            return String.join("\n", plain).strip();
        }
        return html.stream().map(GmailToolCalls::fromHtml).reduce((a, b) -> a + "\n" + b).orElse("").strip();
    }

    private static void collect(JsonNode part, List<String> plain, List<String> html, List<String> attachments,
            int depth) {
        if (depth > 10) {
            return;
        }
        String filename = part.path("filename").asString("");
        String type = part.path("mimeType").asString("").toLowerCase(Locale.ROOT);
        if (!filename.isEmpty()) {
            if (attachments.size() < 50) {
                attachments.add(filename.length() > 200 ? filename.substring(0, 200) : filename);
            }
        } else if (type.equals("text/plain")) {
            plain.add(decoded(part.path("body").path("data").asString("")));
        } else if (type.equals("text/html")) {
            html.add(decoded(part.path("body").path("data").asString("")));
        }
        for (JsonNode child : part.path("parts")) {
            collect(child, plain, html, attachments, depth + 1);
        }
    }

    private static String decoded(String data) {
        try {
            return new String(Base64.getUrlDecoder().decode(data.replace('+', '-').replace('/', '_').replace("=", "")),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException unreadable) {
            return "(a part of this email could not be read)";
        }
    }

    /**
     * HTML as text in one pass over at most {@link #MAX_HTML} characters: tags become spaces, scripts, styles and the
     * head are dropped, entities are decoded. Every step moves forward, so hostile HTML can't make it slow.
     */
    static String fromHtml(String html) {
        String source = html.length() > MAX_HTML ? html.substring(0, MAX_HTML) : html;
        String lower = asciiLower(source);
        StringBuilder text = new StringBuilder();
        int at = 0;
        while (at < source.length()) {
            int open = source.indexOf('<', at);
            if (open < 0) {
                text.append(source, at, source.length());
                break;
            }
            text.append(source, at, open).append(' ');
            int close = source.indexOf('>', open);
            if (close < 0) {
                break;
            }
            String element = elementName(lower, open + 1, close);
            at = close + 1;
            if (SKIPPED_ELEMENTS.contains(element)) {
                int end = lower.indexOf("</" + element, at);
                int endClose = end < 0 ? -1 : source.indexOf('>', end);
                at = endClose < 0 ? source.length() : endClose + 1;
            }
        }
        String decoded = ENTITY.matcher(text).replaceAll(match -> Matcher.quoteReplacement(entity(match.group(1))));
        return decoded.replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll(" ?\n ?", "\n").replaceAll("\n{3,}", "\n\n")
                .strip();
    }

    /** Lower case for A to Z only, so every index still points at the same character. */
    private static String asciiLower(String value) {
        char[] characters = value.toCharArray();
        for (int i = 0; i < characters.length; i++) {
            if (characters[i] >= 'A' && characters[i] <= 'Z') {
                characters[i] = (char) (characters[i] + ('a' - 'A'));
            }
        }
        return new String(characters);
    }

    private static String elementName(String lower, int from, int to) {
        int end = from;
        while (end < to && Character.isLetterOrDigit(lower.charAt(end))) {
            end++;
        }
        return lower.substring(from, end);
    }

    private static String entity(String name) {
        return switch (name) {
            case "nbsp" -> " ";
            case "lt" -> "<";
            case "gt" -> ">";
            case "quot" -> "\"";
            case "apos" -> "'";
            case "amp" -> "&";
            default -> {
                int code = name.charAt(1) == 'x' || name.charAt(1) == 'X'
                        ? Integer.parseInt(name.substring(2), 16) : Integer.parseInt(name.substring(1));
                yield Character.isValidCodePoint(code) && code != 0 ? Character.toString(code) : " ";
            }
        };
    }

    private static Map<String, String> headers(JsonNode email) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (JsonNode header : email.path("payload").path("headers")) {
            String value = header.path("value").asString("");
            headers.putIfAbsent(header.path("name").asString("").toLowerCase(Locale.ROOT),
                    value.length() > 1_000 ? value.substring(0, 1_000) : value);
        }
        return headers;
    }

    private static Set<String> labelIds(JsonNode email) {
        Set<String> ids = new LinkedHashSet<>();
        email.path("labelIds").forEach(id -> ids.add(id.asString("")));
        return ids;
    }

    /** The person's Gmail search words, without any that reach Spam, Trash or everywhere ({@code in:}). */
    static String plainQuery(String query) {
        List<String> kept = new ArrayList<>();
        for (String word : query.strip().split("\\s+")) {
            if (!word.isEmpty() && !IN.matcher(word).matches()) {
                kept.add(word);
            }
        }
        return String.join(" ", kept);
    }

    private static String id(Map<String, Object> arguments) {
        String id = text(arguments, "id", 64, true);
        if (!GmailTools.ID.matcher(id).matches()) {
            throw new Problem("Give the id search_emails returned, letters and digits only.");
        }
        return id;
    }

    private JsonNode read(String body) {
        try {
            return json.readTree(body);
        } catch (JacksonException unreadable) {
            throw new IntegrationApi.Unreachable("Gmail answered with something that is not JSON");
        }
    }

    private String write(Object value) {
        return json.writeValueAsString(value);
    }
}
