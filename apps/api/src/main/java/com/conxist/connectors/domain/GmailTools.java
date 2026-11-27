package com.conxist.connectors.domain;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The tools ConXist offers for Gmail (ADR 0021). Their names, descriptions and schemas are ConXist's own; every one
 * only reads, and only emails in the labels the person allowed (or all mail but Spam and Trash, when they chose that).
 * Emails are what other people wrote: agents read them as text, never as instructions.
 */
public final class GmailTools {

    public static final String SEARCH_EMAILS = "search_emails";
    public static final String GET_EMAIL = "get_email";
    public static final String GET_THREAD = "get_thread";
    public static final String LIST_LABELS = "list_labels";

    /** Stands for "all my email" in place of labels: no Gmail label can be called this, ConXist never offers it. */
    public static final String ALL_MAIL = "*";

    /** A label's name as Gmail shows it, or {@link #ALL_MAIL}. */
    public static final Pattern LABEL = Pattern.compile("[^\\p{Cntrl}]{1,225}");
    /** An email's or a conversation's id, as Gmail writes them. */
    public static final Pattern ID = Pattern.compile("[A-Za-z0-9]{1,64}");

    public static final List<McpTool> ALL = List.of(
            tool(SEARCH_EMAILS, "Search emails",
                    "Finds emails with Gmail search words, such as from:, subject: or after:, within the labels you"
                            + " allowed, newest first. Up to 25, each with its sender, subject, date and a short"
                            + " preview.",
                    schema(Map.of(
                            "query", Map.of("type", "string", "maxLength", 300,
                                    "description", "Gmail search words, such as from:dana@acme.com refund after:2026/11/01."
                                            + " Leave it out for the newest emails."),
                            "label", Map.of("type", "string", "maxLength", 225,
                                    "description", "One of the allowed labels, to search only there.")),
                            List.of())),
            tool(GET_EMAIL, "Read an email",
                    "Reads one email: sender, recipients, date, subject and text. Attachments are listed by name, not"
                            + " opened.",
                    schema(Map.of("id", Map.of("type", "string", "maxLength", 64, "pattern", "^[A-Za-z0-9]+$",
                            "description", "The email's id, from search_emails.")), List.of("id"))),
            tool(GET_THREAD, "Read a conversation",
                    "Reads a whole conversation, up to 50 emails, oldest first.",
                    schema(Map.of("id", Map.of("type", "string", "maxLength", 64, "pattern", "^[A-Za-z0-9]+$",
                            "description", "The conversation's id (threadId), from search_emails.")), List.of("id"))),
            tool(LIST_LABELS, "List labels", "Lists the labels agents may read.", schema(Map.of(), List.of())));

    private GmailTools() {
    }

    private static McpTool tool(String name, String title, String description, Map<String, Object> schema) {
        return new McpTool(name, title, description, true, schema, false);
    }

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required,
                "additionalProperties", false);
    }
}
