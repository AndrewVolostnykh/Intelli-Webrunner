package com.intelli.webrunner.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.intelli.webrunner.state.FormEntryState;
import com.intelli.webrunner.state.HeaderEntryState;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateEngine {
    private static final Pattern TEMPLATE = Pattern.compile("\\{\\{\\s*([\\w.-]+)\\s*}}");
    private static final String BARE_TEMPLATE_PREFIX = "__WEBRUNNER_BARE_TEMPLATE_";
    private static final String BARE_TEMPLATE_SUFFIX = "__";
    private final ObjectMapper mapper = new ObjectMapper();

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
            String key = matcher.group(1);
            Object replacement = vars == null ? null : vars.get(key);
            matcher.appendReplacement(buffer, replacement == null ? "" : Matcher.quoteReplacement(String.valueOf(replacement)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private JsonNode replaceJsonNode(JsonNode node, Map<String, Object> vars) {
        if (node.isTextual()) {
            String value = node.asText();
            String bareTemplateKey = bareTemplateKey(value);
            if (bareTemplateKey != null) {
                return vars != null && vars.containsKey(bareTemplateKey)
                    ? mapper.valueToTree(vars.get(bareTemplateKey))
                    : mapper.valueToTree(null);
            }
            Matcher matcher = TEMPLATE.matcher(value);
            if (matcher.matches()) {
                String key = matcher.group(1);
                Object replacement = vars == null ? null : vars.get(key);
                return replacement == null ? node : mapper.valueToTree(replacement);
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
}
