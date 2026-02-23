package com.intelli.webrunner.script;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelli.webrunner.state.HeaderEntryState;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// TODO: refactor: all the functions should be sseparated classes. Not just store it there
public class ScriptRuntime {
    private final ObjectMapper mapper = new ObjectMapper();

    public void runScript(String script, ScriptContext context) {
        if (script == null || script.trim().isEmpty()) {
            return;
        }
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            Scriptable requestObj = buildScriptRequest(context.request, cx, scope);
            Scriptable rawRequestObj = buildScriptRequest(context.rawRequest, cx, scope);
            Scriptable responseObj = buildScriptResponse(context, cx, scope);
            Scriptable varsObj = buildVarsObject(context, cx, scope);

            BaseFunction logFn = new BaseFunction() {
                @Override
                public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
                    StringBuilder builder = new StringBuilder();
                    for (int i = 0; i < args.length; i++) {
                        if (i > 0) {
                            builder.append(' ');
                        }
                        builder.append(formatLogValue(args[i], cx, scope));
                    }
                    context.log.log(builder.toString());
                    return null;
                }
            };

            BaseFunction assertFn = new BaseFunction() {
                @Override
                public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
                    Object actual = args.length > 0 ? args[0] : null;
                    Object expected = args.length > 1 ? args[1] : null;
                    Object message = args.length > 2 ? args[2] : null;
                    context.helpers.assertValue(actual, expected, message == null ? null : String.valueOf(message));
                    return null;
                }
            };

            BaseFunction uuidFn = new BaseFunction() {
                @Override
                public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
                    return context.helpers.uuid();
                }
            };

            BaseFunction stringifyFn = new BaseFunction() {
                @Override
                public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
                    if (args.length == 0 || args[0] == null || args[0] instanceof Undefined) {
                        return "null";
                    }
                    Object value = args[0];
                    if (value instanceof String) {
                        return value;
                    }
                    try {
                        Object javaValue = Context.jsToJava(value, Object.class);
                        Object normalized = normalizeScriptValue(value, javaValue);
                        return mapper.writeValueAsString(normalized);
                    } catch (Exception ignored) {
                        return String.valueOf(value);
                    }
                }
            };

            BaseFunction jsonifyFn = new BaseFunction() {
                @Override
                public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
                    if (args.length == 0 || args[0] == null || args[0] instanceof Undefined) {
                        return cx.newObject(scope);
                    }
                    Object value = args[0];
                    if (value instanceof Scriptable) {
                        return value;
                    }
                    if (value instanceof String str) {
                        String trimmed = str.trim();
                        if (trimmed.isEmpty()) {
                            return cx.newObject(scope);
                        }
                        try {
                            Object parsed = mapper.readValue(trimmed, Object.class);
                            return toNativeJs(parsed, cx, scope);
                        } catch (Exception ignored) {
                            return value;
                        }
                    }
                    Object javaValue = Context.jsToJava(value, Object.class);
                    if (javaValue instanceof Map<?, ?> || javaValue instanceof List<?>) {
                        return toNativeJs(javaValue, cx, scope);
                    }
                    return value;
                }
            };

            Scriptable contextObj = cx.newObject(scope);
            ScriptableObject.putProperty(contextObj, "vars", varsObj);
            ScriptableObject.putProperty(contextObj, "log", logFn);
            ScriptableObject.putProperty(contextObj, "helpers", Context.javaToJS(context.helpers, scope));
            ScriptableObject.putProperty(contextObj, "request", requestObj);
            ScriptableObject.putProperty(contextObj, "rawRequest", rawRequestObj);
            ScriptableObject.putProperty(contextObj, "response", responseObj);
            ScriptableObject.putProperty(contextObj, "stringify", stringifyFn);
            ScriptableObject.putProperty(contextObj, "jsonify", jsonifyFn);

            ScriptableObject.putProperty(scope, "vars", varsObj);
            ScriptableObject.putProperty(scope, "request", requestObj);
            ScriptableObject.putProperty(scope, "rawRequest", rawRequestObj);
            ScriptableObject.putProperty(scope, "response", responseObj);
            ScriptableObject.putProperty(scope, "context", contextObj);
            ScriptableObject.putProperty(scope, "log", logFn);
            ScriptableObject.putProperty(scope, "assert", assertFn);
            ScriptableObject.putProperty(scope, "uuid", uuidFn);
            ScriptableObject.putProperty(scope, "stringify", stringifyFn);
            ScriptableObject.putProperty(scope, "jsonify", jsonifyFn);

            cx.evaluateString(scope, script, "user-script", 1, null);
            updateRequestFromScript(context, requestObj);
        } finally {
            Context.exit();
        }
    }

    private Scriptable buildScriptRequest(ScriptRequest request, Context cx, Scriptable scope) {
        Scriptable requestObj = cx.newObject(scope);
        if (request == null) {
            return requestObj;
        }
        Object requestBody = buildScriptBody(request.getBody(), cx, scope);
        ScriptableObject.putProperty(requestObj, "body", requestBody);
        ScriptableObject.putProperty(requestObj, "headers", buildHeaderArray(request.getHeaders(), cx, scope));
        ScriptableObject.putProperty(requestObj, "params", buildHeaderArray(request.getParams(), cx, scope));
        return requestObj;
    }

    private Scriptable buildScriptResponse(ScriptContext context, Context cx, Scriptable scope) {
        if (context.response == null) {
            return cx.newObject(scope);
        }
        Scriptable responseObj = cx.newObject(scope);
        if (context.response instanceof com.intelli.webrunner.execution.HttpExecutionResponse http) {
            ScriptableObject.putProperty(responseObj, "statusCode", http.statusCode);
            ScriptableObject.putProperty(responseObj, "headers", buildHeaderMap(http.headers, cx, scope));
            ScriptableObject.putProperty(responseObj, "body", buildScriptBody(http.body, cx, scope));
            return responseObj;
        }
        if (context.response instanceof com.intelli.webrunner.grpc.GrpcExecutionResponse grpc) {
            ScriptableObject.putProperty(responseObj, "statusCode", grpc.statusCode);
            ScriptableObject.putProperty(responseObj, "statusMessage", grpc.statusMessage);
            ScriptableObject.putProperty(responseObj, "headers", buildHeaderMap(grpc.headers, cx, scope));
            ScriptableObject.putProperty(responseObj, "body", buildScriptBody(grpc.body, cx, scope));
            return responseObj;
        }
        Object fallback = Context.javaToJS(context.response, scope);
        ScriptableObject.putProperty(responseObj, "value", fallback);
        return responseObj;
    }

    private Object buildScriptBody(String body, Context cx, Scriptable scope) {
        if (body == null) {
            return cx.newObject(scope);
        }
        String trimmed = body.trim();
        if (trimmed.isEmpty()) {
            return cx.newObject(scope);
        }
        if (looksLikeJson(trimmed)) {
            try {
                Object parsed = mapper.readValue(trimmed, Object.class);
                return toNativeJs(parsed, cx, scope);
            } catch (Exception ignored) {
                try {
                    String normalized = normalizeTemplateJson(trimmed);
                    Object parsed = mapper.readValue(normalized, Object.class);
                    return toNativeJs(parsed, cx, scope);
                } catch (Exception ignoredAgain) {
                }
            }
        }
        return body;
    }

    private void updateRequestFromScript(ScriptContext context, Scriptable requestObj) {
        Object bodyValue = ScriptableObject.getProperty(requestObj, "body");
        if (!(bodyValue == ScriptableObject.NOT_FOUND || bodyValue instanceof Undefined)) {
            if (bodyValue instanceof String) {
                context.request.setBody((String) bodyValue);
            } else {
                try {
                    Object javaValue = Context.jsToJava(bodyValue, Object.class);
                    Object normalized = normalizeScriptValue(bodyValue, javaValue);
                    if (normalized instanceof String) {
                        context.request.setBody((String) normalized);
                    } else if (normalized instanceof Map || normalized instanceof List) {
                        context.request.setBody(mapper.writeValueAsString(normalized));
                    } else {
                        context.request.setBody(String.valueOf(normalized));
                    }
                } catch (Exception ignored) {
                }
            }
        }
        updateHeadersFromScript(context, requestObj);
        updateParamsFromScript(context, requestObj);
    }

    private void updateHeadersFromScript(ScriptContext context, Scriptable requestObj) {
        Object headersValue = ScriptableObject.getProperty(requestObj, "headers");
        if (headersValue == ScriptableObject.NOT_FOUND || headersValue instanceof Undefined) {
            return;
        }
        try {
            List<HeaderEntryState> headers = toHeaderEntries(headersValue);
            if (headers != null) {
                context.request.setHeaders(headers);
            }
        } catch (Exception ignored) {
        }
    }

    private Object normalizeScriptValue(Object scriptValue, Object javaValue) {
        if (javaValue instanceof NativeObject || scriptValue instanceof NativeObject) {
            return Context.jsToJava(scriptValue, Map.class);
        }
        if (javaValue instanceof NativeArray || scriptValue instanceof NativeArray) {
            return Context.jsToJava(scriptValue, List.class);
        }
        if (javaValue instanceof Scriptable) {
            return Context.jsToJava(scriptValue, Object.class);
        }
        return javaValue;
    }

    private String formatLogValue(Object value, Context cx, Scriptable scope) {
        if (value == null || value instanceof Undefined) {
            return "null";
        }
        if (value instanceof String str) {
            return str;
        }
        try {
            Object javaValue = Context.jsToJava(value, Object.class);
            Object normalized = normalizeScriptValue(value, javaValue);
            if (normalized instanceof Map || normalized instanceof List) {
                return mapper.writeValueAsString(normalized);
            }
            return String.valueOf(normalized);
        } catch (Exception ignored) {
            return String.valueOf(value);
        }
    }

    private Scriptable buildVarsObject(ScriptContext context, Context cx, Scriptable scope) {
        Scriptable varsObj = cx.newObject(scope);

        BaseFunction getFn = new BaseFunction() {
            @Override
            public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
                if (args.length == 0) {
                    return Undefined.instance;
                }
                String key = String.valueOf(args[0]);
                Object value = context.vars.get(key);
                return toNativeJs(value, cx, scope);
            }
        };

        BaseFunction setFn = new BaseFunction() {
            @Override
            public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
                if (args.length == 0) {
                    return null;
                }
                String key = String.valueOf(args[0]);
                Object value = args.length > 1 ? args[1] : null;
                Object javaValue = Context.jsToJava(value, Object.class);
                Object normalized = normalizeScriptValue(value, javaValue);
                context.vars.add(key, normalized);
                return null;
            }
        };

        BaseFunction allFn = new BaseFunction() {
            @Override
            public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
                return toNativeJs(context.vars.entries(), cx, scope);
            }
        };

        ScriptableObject.putProperty(varsObj, "get", getFn);
        ScriptableObject.putProperty(varsObj, "set", setFn);
        ScriptableObject.putProperty(varsObj, "add", setFn);
        ScriptableObject.putProperty(varsObj, "all", allFn);
        return varsObj;
    }

    private void updateParamsFromScript(ScriptContext context, Scriptable requestObj) {
        Object paramsValue = ScriptableObject.getProperty(requestObj, "params");
        if (paramsValue == ScriptableObject.NOT_FOUND || paramsValue instanceof Undefined) {
            return;
        }
        try {
            List<HeaderEntryState> params = toHeaderEntries(paramsValue);
            if (params != null) {
                context.request.setParams(params);
            }
        } catch (Exception ignored) {
        }
    }

    private List<HeaderEntryState> toHeaderEntries(Object value) {
        if (value == null || value == ScriptableObject.NOT_FOUND || value instanceof Undefined) {
            return null;
        }
        Object normalized = value;
        if (!(value instanceof List<?>)) {
            try {
                normalized = Context.jsToJava(value, List.class);
            } catch (Exception ignored) {
            }
        }
        if (!(normalized instanceof List<?> list)) {
            return null;
        }
        List<HeaderEntryState> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof HeaderEntryState header) {
                result.add(header);
                continue;
            }
            if (item instanceof Map<?, ?> map) {
                HeaderEntryState entry = new HeaderEntryState();
                Object name = map.get("name");
                Object val = map.get("value");
                Object enabled = map.get("enabled");
                entry.name = name == null ? "" : String.valueOf(name);
                entry.value = val == null ? "" : String.valueOf(val);
                entry.enabled = enabled == null || Boolean.TRUE.equals(enabled);
                result.add(entry);
                continue;
            }
            if (item instanceof Scriptable scriptable) {
                HeaderEntryState entry = new HeaderEntryState();
                Object name = ScriptableObject.getProperty(scriptable, "name");
                Object val = ScriptableObject.getProperty(scriptable, "value");
                Object enabled = ScriptableObject.getProperty(scriptable, "enabled");
                entry.name = name == ScriptableObject.NOT_FOUND ? "" : String.valueOf(name);
                entry.value = val == ScriptableObject.NOT_FOUND ? "" : String.valueOf(val);
                entry.enabled = enabled == ScriptableObject.NOT_FOUND || Boolean.TRUE.equals(enabled);
                result.add(entry);
            }
        }
        return result;
    }

    private boolean looksLikeJson(String value) {
        return value.startsWith("{") || value.startsWith("[");
    }

    private String normalizeTemplateJson(String value) {
        String normalized = value.replaceAll("\\{\\{\\s*[\\w.-]+\\s*}}", "null");
        normalized = normalized.replaceAll("(\"(?:[^\"\\\\]|\\\\.)*\"|\\btrue\\b|\\bfalse\\b|\\bnull\\b|-?\\d+(?:\\.\\d+)?)(\\s*)(\"[^\"]+\"\\s*:)", "$1,$2$3");
        return normalized;
    }

    private Scriptable buildHeaderArray(List<HeaderEntryState> headers, Context cx, Scriptable scope) {
        List<HeaderEntryState> source = headers == null ? List.of() : headers;
        NativeArray array = (NativeArray) cx.newArray(scope, source.size());
        for (int i = 0; i < source.size(); i++) {
            HeaderEntryState header = source.get(i);
            NativeObject obj = (NativeObject) cx.newObject(scope);
            ScriptableObject.putProperty(obj, "name", header == null || header.name == null ? "" : header.name);
            ScriptableObject.putProperty(obj, "value", header == null || header.value == null ? "" : header.value);
            ScriptableObject.putProperty(obj, "enabled", header != null && header.enabled);
            array.put(i, array, obj);
        }
        return array;
    }

    private Scriptable buildHeaderMap(Map<String, List<String>> headers, Context cx, Scriptable scope) {
        NativeObject obj = (NativeObject) cx.newObject(scope);
        if (headers == null) {
            return obj;
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            List<String> values = entry.getValue() == null ? List.of() : entry.getValue();
            NativeArray array = (NativeArray) cx.newArray(scope, values.toArray());
            ScriptableObject.putProperty(obj, entry.getKey(), array);
        }
        return obj;
    }

    private Object toNativeJs(Object value, Context cx, Scriptable scope) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            NativeObject obj = (NativeObject) cx.newObject(scope);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                if (key == null) {
                    continue;
                }
                Object converted = toNativeJs(entry.getValue(), cx, scope);
                ScriptableObject.putProperty(obj, String.valueOf(key), converted);
            }
            return obj;
        }
        if (value instanceof List<?> list) {
            NativeArray array = (NativeArray) cx.newArray(scope, list.size());
            for (int i = 0; i < list.size(); i++) {
                Object converted = toNativeJs(list.get(i), cx, scope);
                array.put(i, array, converted);
            }
            return array;
        }
        return value;
    }
}
