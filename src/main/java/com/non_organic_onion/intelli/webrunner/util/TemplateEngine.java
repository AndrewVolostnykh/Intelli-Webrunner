package com.non_organic_onion.intelli.webrunner.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.non_organic_onion.intelli.webrunner.script.ScriptHelpers;
import com.non_organic_onion.intelli.webrunner.state.FormEntryState;
import com.non_organic_onion.intelli.webrunner.state.HeaderEntryState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateEngine {
    private static final Pattern TEMPLATE = Pattern.compile("\\{\\{\\s*(.*?)\\s*}}");
    private static final Pattern FUNCTION = Pattern.compile("([A-Za-z_]\\w*)\\s*\\((.*)\\)");
    private static final String BARE_TEMPLATE_PREFIX = "__WEBRUNNER_BARE_TEMPLATE_";
    private static final String BARE_TEMPLATE_SUFFIX = "__";
    private final ObjectMapper mapper = new ObjectMapper();
    private final ScriptHelpers helpers = new ScriptHelpers(message -> {});

    public String applyToBody(String body, Map<String, Object> vars) {
        if (body == null) {
            return "";
        }
        try {
            JsonNode parsed = mapper.readTree(body);
            JsonNode replaced = replaceJsonNode(parsed, vars);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(replaced);
        } catch (Exception ignored) {
            try {
                JsonNode parsed = mapper.readTree(quoteBareJsonTemplates(body));
                JsonNode replaced = replaceJsonNode(parsed, vars);
                return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(replaced);
            } catch (Exception ignoredAgain) {
                return interpolate(body, vars);
            }
        }
    }

    public List<HeaderEntryState> applyToHeaders(List<HeaderEntryState> headers, Map<String, Object> vars) {
        for (HeaderEntryState header : headers) {
            header.name = interpolate(header.name, vars);
            header.value = interpolate(header.value, vars);
        }
        return headers;
    }

    public List<HeaderEntryState> applyToParams(List<HeaderEntryState> params, Map<String, Object> vars) {
        for (HeaderEntryState param : params) {
            param.name = interpolate(param.name, vars);
            param.value = interpolate(param.value, vars);
        }
        return params;
    }

    public List<FormEntryState> applyToFormData(List<FormEntryState> entries, Map<String, Object> vars) {
        for (FormEntryState entry : entries) {
            entry.name = interpolate(entry.name, vars);
            entry.value = interpolate(entry.value, vars);
        }
        return entries;
    }

    public String applyToText(String value, Map<String, Object> vars) {
        return interpolate(value, vars);
    }

    private String interpolate(String value, Map<String, Object> vars) {
        if (value == null) {
            return "";
        }
        Matcher matcher = TEMPLATE.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            TemplateValue replacement = resolve(matcher.group(1), vars);
            matcher.appendReplacement(
                buffer,
                replacement.found ? Matcher.quoteReplacement(String.valueOf(replacement.value)) : ""
            );
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private JsonNode replaceJsonNode(JsonNode node, Map<String, Object> vars) {
        if (node.isTextual()) {
            String value = node.asText();
            String bareTemplateKey = bareTemplateKey(value);
            if (bareTemplateKey != null) {
                TemplateValue replacement = resolve(bareTemplateKey, vars);
                return replacement.found
                    ? mapper.valueToTree(replacement.value)
                    : mapper.valueToTree(null);
            }
            Matcher matcher = TEMPLATE.matcher(value);
            if (matcher.matches()) {
                TemplateValue replacement = resolve(matcher.group(1), vars);
                return replacement.found ? mapper.valueToTree(replacement.value) : node;
            }
            return new TextNode(interpolate(value, vars));
        }
        if (node.isArray()) {
            ArrayNode array = mapper.createArrayNode();
            node.forEach(child -> array.add(replaceJsonNode(child, vars)));
            return array;
        }
        if (node.isObject()) {
            ObjectNode object = mapper.createObjectNode();
            node.fields().forEachRemaining(entry -> object.set(entry.getKey(), replaceJsonNode(entry.getValue(), vars)));
            return object;
        }
        return node;
    }

    private String quoteBareJsonTemplates(String value) {
        StringBuilder result = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        int index = 0;
        while (index < value.length()) {
            char current = value.charAt(index);
            if (inString) {
                result.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                index++;
                continue;
            }
            if (current == '"') {
                inString = true;
                result.append(current);
                index++;
                continue;
            }
            Matcher matcher = TEMPLATE.matcher(value);
            matcher.region(index, value.length());
            if (matcher.lookingAt()) {
                String marker = BARE_TEMPLATE_PREFIX + matcher.group(1) + BARE_TEMPLATE_SUFFIX;
                result.append('"').append(marker).append('"');
                index = matcher.end();
                continue;
            }
            result.append(current);
            index++;
        }
        return result.toString();
    }

    private String bareTemplateKey(String value) {
        if (value == null || !value.startsWith(BARE_TEMPLATE_PREFIX) || !value.endsWith(BARE_TEMPLATE_SUFFIX)) {
            return null;
        }
        return value.substring(
            BARE_TEMPLATE_PREFIX.length(),
            value.length() - BARE_TEMPLATE_SUFFIX.length()
        );
    }

    private TemplateValue resolve(String expression, Map<String, Object> vars) {
        String trimmed = expression == null ? "" : expression.trim();
        Matcher function = FUNCTION.matcher(trimmed);
        if (function.matches()) {
            return resolveFunction(function.group(1), parseArguments(function.group(2)));
        }
        if (vars != null && vars.containsKey(trimmed)) {
            return TemplateValue.found(vars.get(trimmed));
        }
        return TemplateValue.missing();
    }

    private TemplateValue resolveFunction(String name, List<String> args) {
        try {
            return switch (name) {
                case "uuid" -> noArgs(args, helpers.uuid());
                case "randomString" -> TemplateValue.found(helpers.randomString(intArg(args, 0)));
                case "randomEmail" -> noArgs(args, helpers.randomEmail());
                case "randomIsoDate" -> noArgs(args, helpers.randomIsoDate());
                case "randomRfcDate" -> noArgs(args, helpers.randomRfcDate());
                case "randomDateTime" -> noArgs(args, helpers.randomDateTime());
                case "randomDate" -> noArgs(args, helpers.randomDate());
                case "randomTime" -> noArgs(args, helpers.randomTime());
                case "randomMillilsDate" -> noArgs(args, helpers.randomMillilsDate());
                case "randomEpochSecondsDate" -> noArgs(args, helpers.randomEpochSecondsDate());
                case "currentIsoDate" -> noArgs(args, helpers.currentIsoDate());
                case "currentRfcDate" -> noArgs(args, helpers.currentRfcDate());
                case "currentDateTime" -> noArgs(args, helpers.currentDateTime());
                case "currentDate" -> noArgs(args, helpers.currentDate());
                case "currentTime" -> noArgs(args, helpers.currentTime());
                case "currentMillilsDate" -> noArgs(args, helpers.currentMillilsDate());
                case "currentEpochSecondsDate" -> noArgs(args, helpers.currentEpochSecondsDate());
                case "randomNumber" -> TemplateValue.found(helpers.randomNumber(intArg(args, 0), intArg(args, 1)));
                case "randomDouble" -> args.size() >= 3
                    ? TemplateValue.found(helpers.randomDouble(doubleArg(args, 0), doubleArg(args, 1), intArg(args, 2)))
                    : TemplateValue.found(helpers.randomDouble(doubleArg(args, 0), doubleArg(args, 1)));
                default -> TemplateValue.missing();
            };
        } catch (RuntimeException error) {
            return TemplateValue.missing();
        }
    }

    private TemplateValue noArgs(List<String> args, Object value) {
        return args.isEmpty() ? TemplateValue.found(value) : TemplateValue.missing();
    }

    private List<String> parseArguments(String value) {
        List<String> args = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) {
            return args;
        }
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (inString) {
                current.append(ch);
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == quote) {
                    inString = false;
                }
                continue;
            }
            if (ch == '\'' || ch == '"') {
                inString = true;
                quote = ch;
                current.append(ch);
                continue;
            }
            if (ch == ',') {
                args.add(unquote(current.toString().trim()));
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        args.add(unquote(current.toString().trim()));
        return args;
    }

    private String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
                return value.substring(1, value.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\'", "'")
                    .replace("\\\\", "\\");
            }
        }
        return value;
    }

    private int intArg(List<String> args, int index) {
        if (args.size() <= index) {
            throw new IllegalArgumentException("Missing argument");
        }
        return Integer.parseInt(args.get(index));
    }

    private double doubleArg(List<String> args, int index) {
        if (args.size() <= index) {
            throw new IllegalArgumentException("Missing argument");
        }
        return Double.parseDouble(args.get(index));
    }

    private record TemplateValue(boolean found, Object value) {
        private static TemplateValue found(Object value) {
            return new TemplateValue(true, value);
        }

        private static TemplateValue missing() {
            return new TemplateValue(false, null);
        }
    }
}
