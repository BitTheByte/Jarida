package com.jarida.jadxfrida.state;

import com.jarida.jadxfrida.model.HookSpec;
import jadx.gui.settings.JadxProject;
import jadx.gui.ui.MainWindow;

import javax.swing.JFrame;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Manages saving and loading of Jarida state within JADX project files.
 * State is stored in the .jadx project file using JADX's pluginOptions API.
 */
public class JaridaStateManager {
    private static final String PLUGIN_KEY = "jarida.state";

    private final Consumer<String> logger;

    public JaridaStateManager(Consumer<String> logger) {
        this.logger = logger != null ? logger : s -> {};
    }

    /**
     * Save the current state to the JADX project file.
     */
    public boolean saveState(JFrame mainFrame, Map<String, HookSpec> hookSpecs,
                             Map<String, Boolean> activeStates, String customScripts) {
        JadxProject project = getProject(mainFrame);
        if (project == null) {
            return false;
        }
        JaridaState state = JaridaState.fromHooks(hookSpecs, activeStates, customScripts);
        try {
            String json = toJson(state);
            project.updatePluginOptions(options -> {
                options.put(PLUGIN_KEY, json);
            });
            logger.accept("Jarida state saved to project.");
            return true;
        } catch (Exception e) {
            logger.accept("Failed to save Jarida state: " + e.getMessage());
            return false;
        }
    }

    /**
     * Load the state from the JADX project file if it exists.
     */
    public JaridaState loadState(JFrame mainFrame) {
        JadxProject project = getProject(mainFrame);
        if (project == null) {
            return null;
        }
        try {
            String json = project.getPluginOption(PLUGIN_KEY);
            if (json == null || json.trim().isEmpty()) {
                return null;
            }
            JaridaState state = fromJson(json);
            logger.accept("Jarida state loaded from project.");
            return state;
        } catch (Exception e) {
            logger.accept("Failed to load Jarida state: " + e.getMessage());
            return null;
        }
    }

    /**
     * Check if a saved state exists in the project.
     */
    public boolean hasState(JFrame mainFrame) {
        JadxProject project = getProject(mainFrame);
        if (project == null) {
            return false;
        }
        String json = project.getPluginOption(PLUGIN_KEY);
        return json != null && !json.trim().isEmpty();
    }

    /**
     * Clear the saved state from the project.
     */
    public boolean clearState(JFrame mainFrame) {
        JadxProject project = getProject(mainFrame);
        if (project == null) {
            return false;
        }
        try {
            project.updatePluginOptions(options -> {
                options.remove(PLUGIN_KEY);
            });
            return true;
        } catch (Exception e) {
            logger.accept("Failed to clear state: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get the JadxProject from the MainWindow.
     */
    private JadxProject getProject(JFrame frame) {
        if (frame instanceof MainWindow) {
            return ((MainWindow) frame).getProject();
        }
        return null;
    }

    /**
     * Convert JaridaState to JSON string.
     * Uses simple manual JSON serialization to avoid external dependencies.
     */
    private String toJson(JaridaState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"version\":").append(state.getVersion()).append(",");
        sb.append("\"customScriptPaths\":").append(escapeJsonString(state.getCustomScriptPaths())).append(",");
        sb.append("\"hooks\":[");
        List<JaridaState.HookEntry> hooks = state.getHooks();
        for (int i = 0; i < hooks.size(); i++) {
            sb.append(hookEntryToJson(hooks.get(i)));
            if (i < hooks.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    private String hookEntryToJson(JaridaState.HookEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"hookKey\":").append(escapeJsonString(entry.getHookKey())).append(",");
        sb.append("\"active\":").append(entry.isActive()).append(",");
        sb.append("\"templateEnabled\":").append(entry.isTemplateEnabled()).append(",");
        sb.append("\"templateName\":").append(escapeJsonString(entry.getTemplateName())).append(",");
        sb.append("\"templateContent\":").append(escapeJsonString(entry.getTemplateContent())).append(",");
        sb.append("\"templatePosition\":").append(escapeJsonString(entry.getTemplatePosition())).append(",");
        sb.append("\"extraScript\":").append(escapeJsonString(entry.getExtraScript())).append(",");
        sb.append("\"target\":").append(methodTargetToJson(entry.getTarget())).append(",");
        sb.append("\"options\":").append(scriptOptionsToJson(entry.getOptions())).append(",");
        sb.append("\"patchRule\":").append(patchRuleToJson(entry.getPatchRule()));
        sb.append("}");
        return sb.toString();
    }

    private String methodTargetToJson(JaridaState.MethodTargetData target) {
        if (target == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"className\":").append(escapeJsonString(target.getClassName())).append(",");
        sb.append("\"methodName\":").append(escapeJsonString(target.getMethodName())).append(",");
        sb.append("\"returnType\":").append(escapeJsonString(target.getReturnType())).append(",");
        sb.append("\"argTypes\":").append(stringListToJson(target.getArgTypes())).append(",");
        sb.append("\"isStatic\":").append(target.isStatic()).append(",");
        sb.append("\"isConstructor\":").append(target.isConstructor()).append(",");
        sb.append("\"displaySignature\":").append(escapeJsonString(target.getDisplaySignature()));
        sb.append("}");
        return sb.toString();
    }

    private String scriptOptionsToJson(JaridaState.ScriptOptionsData options) {
        if (options == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"logArgs\":").append(options.isLogArgs()).append(",");
        sb.append("\"logReturn\":").append(options.isLogReturn()).append(",");
        sb.append("\"logThread\":").append(options.isLogThread()).append(",");
        sb.append("\"printStack\":").append(options.isPrintStack()).append(",");
        sb.append("\"printThis\":").append(options.isPrintThis()).append(",");
        sb.append("\"prettyPrint\":").append(options.isPrettyPrint());
        sb.append("}");
        return sb.toString();
    }

    private String patchRuleToJson(JaridaState.ReturnPatchRuleData rule) {
        if (rule == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"enabled\":").append(rule.isEnabled()).append(",");
        sb.append("\"mode\":").append(escapeJsonString(rule.getMode())).append(",");
        sb.append("\"constantValue\":").append(escapeJsonString(rule.getConstantValue())).append(",");
        sb.append("\"expression\":").append(escapeJsonString(rule.getExpression())).append(",");
        sb.append("\"condition\":").append(escapeJsonString(rule.getCondition())).append(",");
        sb.append("\"thenValue\":").append(escapeJsonString(rule.getThenValue())).append(",");
        sb.append("\"elseValue\":").append(escapeJsonString(rule.getElseValue())).append(",");
        sb.append("\"scriptBody\":").append(escapeJsonString(rule.getScriptBody()));
        sb.append("}");
        return sb.toString();
    }

    private String stringListToJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append(escapeJsonString(list.get(i)));
            if (i < list.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJsonString(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    /**
     * Parse JSON string to JaridaState.
     * Uses simple manual JSON parsing to avoid external dependencies.
     */
    private JaridaState fromJson(String json) {
        JaridaState state = new JaridaState();
        JsonParser parser = new JsonParser(json);
        parser.parseObject(state);
        return state;
    }

    /**
     * Simple JSON parser for JaridaState.
     */
    private static class JsonParser {
        private final String json;
        private int pos = 0;

        JsonParser(String json) {
            this.json = json;
        }

        void parseObject(JaridaState state) {
            skipWhitespace();
            expect('{');
            while (true) {
                skipWhitespace();
                if (peek() == '}') {
                    pos++;
                    break;
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                switch (key) {
                    case "version":
                        state.setVersion(parseInt());
                        break;
                    case "customScriptPaths":
                        state.setCustomScriptPaths(parseString());
                        break;
                    case "hooks":
                        state.setHooks(parseHooksArray());
                        break;
                    default:
                        skipValue();
                }
                skipWhitespace();
                if (peek() == ',') {
                    pos++;
                }
            }
        }

        List<JaridaState.HookEntry> parseHooksArray() {
            List<JaridaState.HookEntry> hooks = new ArrayList<>();
            expect('[');
            while (true) {
                skipWhitespace();
                if (peek() == ']') {
                    pos++;
                    break;
                }
                hooks.add(parseHookEntry());
                skipWhitespace();
                if (peek() == ',') {
                    pos++;
                }
            }
            return hooks;
        }

        JaridaState.HookEntry parseHookEntry() {
            JaridaState.HookEntry entry = new JaridaState.HookEntry();
            expect('{');
            while (true) {
                skipWhitespace();
                if (peek() == '}') {
                    pos++;
                    break;
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                switch (key) {
                    case "hookKey":
                        entry.setHookKey(parseString());
                        break;
                    case "active":
                        entry.setActive(parseBoolean());
                        break;
                    case "templateEnabled":
                        entry.setTemplateEnabled(parseBoolean());
                        break;
                    case "templateName":
                        entry.setTemplateName(parseString());
                        break;
                    case "templateContent":
                        entry.setTemplateContent(parseString());
                        break;
                    case "templatePosition":
                        entry.setTemplatePosition(parseString());
                        break;
                    case "extraScript":
                        entry.setExtraScript(parseString());
                        break;
                    case "target":
                        entry.setTarget(parseMethodTarget());
                        break;
                    case "options":
                        entry.setOptions(parseScriptOptions());
                        break;
                    case "patchRule":
                        entry.setPatchRule(parsePatchRule());
                        break;
                    default:
                        skipValue();
                }
                skipWhitespace();
                if (peek() == ',') {
                    pos++;
                }
            }
            return entry;
        }

        JaridaState.MethodTargetData parseMethodTarget() {
            if (checkNull()) {
                return null;
            }
            JaridaState.MethodTargetData target = new JaridaState.MethodTargetData();
            expect('{');
            while (true) {
                skipWhitespace();
                if (peek() == '}') {
                    pos++;
                    break;
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                switch (key) {
                    case "className":
                        target.setClassName(parseString());
                        break;
                    case "methodName":
                        target.setMethodName(parseString());
                        break;
                    case "returnType":
                        target.setReturnType(parseString());
                        break;
                    case "argTypes":
                        target.setArgTypes(parseStringArray());
                        break;
                    case "isStatic":
                        target.setStatic(parseBoolean());
                        break;
                    case "isConstructor":
                        target.setConstructor(parseBoolean());
                        break;
                    case "displaySignature":
                        target.setDisplaySignature(parseString());
                        break;
                    default:
                        skipValue();
                }
                skipWhitespace();
                if (peek() == ',') {
                    pos++;
                }
            }
            return target;
        }

        JaridaState.ScriptOptionsData parseScriptOptions() {
            if (checkNull()) {
                return null;
            }
            JaridaState.ScriptOptionsData options = new JaridaState.ScriptOptionsData();
            expect('{');
            while (true) {
                skipWhitespace();
                if (peek() == '}') {
                    pos++;
                    break;
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                switch (key) {
                    case "logArgs":
                        options.setLogArgs(parseBoolean());
                        break;
                    case "logReturn":
                        options.setLogReturn(parseBoolean());
                        break;
                    case "logThread":
                        options.setLogThread(parseBoolean());
                        break;
                    case "printStack":
                        options.setPrintStack(parseBoolean());
                        break;
                    case "printThis":
                        options.setPrintThis(parseBoolean());
                        break;
                    case "prettyPrint":
                        options.setPrettyPrint(parseBoolean());
                        break;
                    default:
                        skipValue();
                }
                skipWhitespace();
                if (peek() == ',') {
                    pos++;
                }
            }
            return options;
        }

        JaridaState.ReturnPatchRuleData parsePatchRule() {
            if (checkNull()) {
                return null;
            }
            JaridaState.ReturnPatchRuleData rule = new JaridaState.ReturnPatchRuleData();
            expect('{');
            while (true) {
                skipWhitespace();
                if (peek() == '}') {
                    pos++;
                    break;
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                switch (key) {
                    case "enabled":
                        rule.setEnabled(parseBoolean());
                        break;
                    case "mode":
                        rule.setMode(parseString());
                        break;
                    case "constantValue":
                        rule.setConstantValue(parseString());
                        break;
                    case "expression":
                        rule.setExpression(parseString());
                        break;
                    case "condition":
                        rule.setCondition(parseString());
                        break;
                    case "thenValue":
                        rule.setThenValue(parseString());
                        break;
                    case "elseValue":
                        rule.setElseValue(parseString());
                        break;
                    case "scriptBody":
                        rule.setScriptBody(parseString());
                        break;
                    default:
                        skipValue();
                }
                skipWhitespace();
                if (peek() == ',') {
                    pos++;
                }
            }
            return rule;
        }

        List<String> parseStringArray() {
            List<String> list = new ArrayList<>();
            expect('[');
            while (true) {
                skipWhitespace();
                if (peek() == ']') {
                    pos++;
                    break;
                }
                list.add(parseString());
                skipWhitespace();
                if (peek() == ',') {
                    pos++;
                }
            }
            return list;
        }

        String parseString() {
            skipWhitespace();
            if (checkNull()) {
                return null;
            }
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < json.length()) {
                char c = json.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\' && pos < json.length()) {
                    char next = json.charAt(pos++);
                    switch (next) {
                        case '"':
                            sb.append('"');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case '/':
                            sb.append('/');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'u':
                            if (pos + 4 <= json.length()) {
                                String hex = json.substring(pos, pos + 4);
                                sb.append((char) Integer.parseInt(hex, 16));
                                pos += 4;
                            }
                            break;
                        default:
                            sb.append(next);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new RuntimeException("Unterminated string");
        }

        int parseInt() {
            skipWhitespace();
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
                pos++;
            }
            return Integer.parseInt(json.substring(start, pos));
        }

        boolean parseBoolean() {
            skipWhitespace();
            if (json.substring(pos).startsWith("true")) {
                pos += 4;
                return true;
            } else if (json.substring(pos).startsWith("false")) {
                pos += 5;
                return false;
            }
            throw new RuntimeException("Expected boolean at position " + pos);
        }

        boolean checkNull() {
            skipWhitespace();
            if (json.substring(pos).startsWith("null")) {
                pos += 4;
                return true;
            }
            return false;
        }

        void skipValue() {
            skipWhitespace();
            char c = peek();
            if (c == '"') {
                parseString();
            } else if (c == '{') {
                skipObject();
            } else if (c == '[') {
                skipArray();
            } else if (c == 't' || c == 'f') {
                parseBoolean();
            } else if (c == 'n') {
                checkNull();
            } else if (c == '-' || Character.isDigit(c)) {
                parseInt();
            }
        }

        void skipObject() {
            expect('{');
            int depth = 1;
            while (depth > 0 && pos < json.length()) {
                char c = json.charAt(pos++);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                } else if (c == '"') {
                    pos--;
                    parseString();
                }
            }
        }

        void skipArray() {
            expect('[');
            int depth = 1;
            while (depth > 0 && pos < json.length()) {
                char c = json.charAt(pos++);
                if (c == '[') {
                    depth++;
                } else if (c == ']') {
                    depth--;
                } else if (c == '"') {
                    pos--;
                    parseString();
                }
            }
        }

        void skipWhitespace() {
            while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
                pos++;
            }
        }

        char peek() {
            return pos < json.length() ? json.charAt(pos) : '\0';
        }

        void expect(char c) {
            skipWhitespace();
            if (pos >= json.length() || json.charAt(pos) != c) {
                throw new RuntimeException("Expected '" + c + "' at position " + pos + " but found '" + peek() + "'");
            }
            pos++;
        }
    }
}
