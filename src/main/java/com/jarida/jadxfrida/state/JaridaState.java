package com.jarida.jadxfrida.state;

import com.jarida.jadxfrida.model.HookSpec;
import com.jarida.jadxfrida.model.MethodTarget;
import com.jarida.jadxfrida.model.ReturnPatchMode;
import com.jarida.jadxfrida.model.ReturnPatchRule;
import com.jarida.jadxfrida.model.ScriptOptions;
import com.jarida.jadxfrida.model.TemplatePosition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializable representation of Jarida's hook state for persistence with JADX projects.
 * This class stores all hook configurations so they can be saved and restored when
 * reopening a project.
 */
public class JaridaState {
    private int version = 1;
    private List<HookEntry> hooks = new ArrayList<>();
    private String customScriptPaths = "";

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public List<HookEntry> getHooks() {
        return hooks;
    }

    public void setHooks(List<HookEntry> hooks) {
        this.hooks = hooks;
    }

    public String getCustomScriptPaths() {
        return customScriptPaths;
    }

    public void setCustomScriptPaths(String customScriptPaths) {
        this.customScriptPaths = customScriptPaths;
    }

    /**
     * Serializable representation of a single hook entry.
     */
    public static class HookEntry {
        private String hookKey;
        private boolean active;
        private MethodTargetData target;
        private ScriptOptionsData options;
        private ReturnPatchRuleData patchRule;
        private String extraScript;
        private String templatePosition;
        private boolean templateEnabled;
        private String templateName;
        private String templateContent;

        public String getHookKey() {
            return hookKey;
        }

        public void setHookKey(String hookKey) {
            this.hookKey = hookKey;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }

        public MethodTargetData getTarget() {
            return target;
        }

        public void setTarget(MethodTargetData target) {
            this.target = target;
        }

        public ScriptOptionsData getOptions() {
            return options;
        }

        public void setOptions(ScriptOptionsData options) {
            this.options = options;
        }

        public ReturnPatchRuleData getPatchRule() {
            return patchRule;
        }

        public void setPatchRule(ReturnPatchRuleData patchRule) {
            this.patchRule = patchRule;
        }

        public String getExtraScript() {
            return extraScript;
        }

        public void setExtraScript(String extraScript) {
            this.extraScript = extraScript;
        }

        public String getTemplatePosition() {
            return templatePosition;
        }

        public void setTemplatePosition(String templatePosition) {
            this.templatePosition = templatePosition;
        }

        public boolean isTemplateEnabled() {
            return templateEnabled;
        }

        public void setTemplateEnabled(boolean templateEnabled) {
            this.templateEnabled = templateEnabled;
        }

        public String getTemplateName() {
            return templateName;
        }

        public void setTemplateName(String templateName) {
            this.templateName = templateName;
        }

        public String getTemplateContent() {
            return templateContent;
        }

        public void setTemplateContent(String templateContent) {
            this.templateContent = templateContent;
        }

        /**
         * Convert a HookSpec and active state to a HookEntry for serialization.
         */
        public static HookEntry fromHookSpec(HookSpec spec, boolean active) {
            HookEntry entry = new HookEntry();
            entry.setHookKey(spec.getHookId());
            entry.setActive(active);
            entry.setTarget(MethodTargetData.fromMethodTarget(spec.getTarget()));
            entry.setOptions(ScriptOptionsData.fromScriptOptions(spec.getOptions()));
            entry.setPatchRule(ReturnPatchRuleData.fromReturnPatchRule(spec.getReturnPatchRule()));
            entry.setExtraScript(spec.getExtraScript());
            entry.setTemplatePosition(spec.getTemplatePosition() != null ? spec.getTemplatePosition().name() : "APPEND");
            entry.setTemplateEnabled(spec.isTemplateEnabled());
            entry.setTemplateName(spec.getTemplateName());
            entry.setTemplateContent(spec.getTemplateContent());
            return entry;
        }

        /**
         * Convert this entry back to a HookSpec.
         */
        public HookSpec toHookSpec() {
            MethodTarget methodTarget = target != null ? target.toMethodTarget() : null;
            ScriptOptions scriptOptions = options != null ? options.toScriptOptions() : new ScriptOptions();
            ReturnPatchRule rule = patchRule != null ? patchRule.toReturnPatchRule() : null;
            TemplatePosition pos = TemplatePosition.APPEND;
            if (templatePosition != null) {
                try {
                    pos = TemplatePosition.valueOf(templatePosition);
                } catch (IllegalArgumentException ignored) {
                }
            }
            return new HookSpec(methodTarget, scriptOptions, rule, extraScript, pos, hookKey,
                    templateEnabled, templateName, templateContent);
        }
    }

    /**
     * Serializable representation of MethodTarget.
     */
    public static class MethodTargetData {
        private String className;
        private String methodName;
        private String returnType;
        private List<String> argTypes;
        private boolean isStatic;
        private boolean isConstructor;
        private String displaySignature;

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        public String getReturnType() {
            return returnType;
        }

        public void setReturnType(String returnType) {
            this.returnType = returnType;
        }

        public List<String> getArgTypes() {
            return argTypes;
        }

        public void setArgTypes(List<String> argTypes) {
            this.argTypes = argTypes;
        }

        public boolean isStatic() {
            return isStatic;
        }

        public void setStatic(boolean aStatic) {
            isStatic = aStatic;
        }

        public boolean isConstructor() {
            return isConstructor;
        }

        public void setConstructor(boolean constructor) {
            isConstructor = constructor;
        }

        public String getDisplaySignature() {
            return displaySignature;
        }

        public void setDisplaySignature(String displaySignature) {
            this.displaySignature = displaySignature;
        }

        public static MethodTargetData fromMethodTarget(MethodTarget target) {
            if (target == null) {
                return null;
            }
            MethodTargetData data = new MethodTargetData();
            data.setClassName(target.getClassName());
            data.setMethodName(target.getMethodName());
            data.setReturnType(target.getReturnType());
            data.setArgTypes(new ArrayList<>(target.getArgTypes()));
            data.setStatic(target.isStatic());
            data.setConstructor(target.isConstructor());
            data.setDisplaySignature(target.getDisplaySignature());
            return data;
        }

        public MethodTarget toMethodTarget() {
            return new MethodTarget(className, methodName, returnType,
                    argTypes != null ? argTypes : new ArrayList<>(),
                    isStatic, isConstructor, displaySignature);
        }
    }

    /**
     * Serializable representation of ScriptOptions.
     */
    public static class ScriptOptionsData {
        private boolean logArgs = true;
        private boolean logReturn = true;
        private boolean logThread = true;
        private boolean printStack = false;
        private boolean printThis = false;
        private boolean prettyPrint = true;

        public boolean isLogArgs() {
            return logArgs;
        }

        public void setLogArgs(boolean logArgs) {
            this.logArgs = logArgs;
        }

        public boolean isLogReturn() {
            return logReturn;
        }

        public void setLogReturn(boolean logReturn) {
            this.logReturn = logReturn;
        }

        public boolean isLogThread() {
            return logThread;
        }

        public void setLogThread(boolean logThread) {
            this.logThread = logThread;
        }

        public boolean isPrintStack() {
            return printStack;
        }

        public void setPrintStack(boolean printStack) {
            this.printStack = printStack;
        }

        public boolean isPrintThis() {
            return printThis;
        }

        public void setPrintThis(boolean printThis) {
            this.printThis = printThis;
        }

        public boolean isPrettyPrint() {
            return prettyPrint;
        }

        public void setPrettyPrint(boolean prettyPrint) {
            this.prettyPrint = prettyPrint;
        }

        public static ScriptOptionsData fromScriptOptions(ScriptOptions options) {
            if (options == null) {
                return new ScriptOptionsData();
            }
            ScriptOptionsData data = new ScriptOptionsData();
            data.setLogArgs(options.isLogArgs());
            data.setLogReturn(options.isLogReturn());
            data.setLogThread(options.isLogThread());
            data.setPrintStack(options.isPrintStack());
            data.setPrintThis(options.isPrintThis());
            data.setPrettyPrint(options.isPrettyPrint());
            return data;
        }

        public ScriptOptions toScriptOptions() {
            ScriptOptions options = new ScriptOptions();
            options.setLogArgs(logArgs);
            options.setLogReturn(logReturn);
            options.setLogThread(logThread);
            options.setPrintStack(printStack);
            options.setPrintThis(printThis);
            options.setPrettyPrint(prettyPrint);
            return options;
        }
    }

    /**
     * Serializable representation of ReturnPatchRule.
     */
    public static class ReturnPatchRuleData {
        private boolean enabled;
        private String mode = "CONSTANT";
        private String constantValue = "";
        private String expression = "";
        private String condition = "";
        private String thenValue = "";
        private String elseValue = "";
        private String scriptBody = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getConstantValue() {
            return constantValue;
        }

        public void setConstantValue(String constantValue) {
            this.constantValue = constantValue;
        }

        public String getExpression() {
            return expression;
        }

        public void setExpression(String expression) {
            this.expression = expression;
        }

        public String getCondition() {
            return condition;
        }

        public void setCondition(String condition) {
            this.condition = condition;
        }

        public String getThenValue() {
            return thenValue;
        }

        public void setThenValue(String thenValue) {
            this.thenValue = thenValue;
        }

        public String getElseValue() {
            return elseValue;
        }

        public void setElseValue(String elseValue) {
            this.elseValue = elseValue;
        }

        public String getScriptBody() {
            return scriptBody;
        }

        public void setScriptBody(String scriptBody) {
            this.scriptBody = scriptBody;
        }

        public static ReturnPatchRuleData fromReturnPatchRule(ReturnPatchRule rule) {
            if (rule == null) {
                return null;
            }
            ReturnPatchRuleData data = new ReturnPatchRuleData();
            data.setEnabled(rule.isEnabled());
            data.setMode(rule.getMode() != null ? rule.getMode().name() : "CONSTANT");
            data.setConstantValue(rule.getConstantValue());
            data.setExpression(rule.getExpression());
            data.setCondition(rule.getCondition());
            data.setThenValue(rule.getThenValue());
            data.setElseValue(rule.getElseValue());
            data.setScriptBody(rule.getScriptBody());
            return data;
        }

        public ReturnPatchRule toReturnPatchRule() {
            ReturnPatchRule rule = new ReturnPatchRule();
            rule.setEnabled(enabled);
            try {
                rule.setMode(ReturnPatchMode.valueOf(mode));
            } catch (IllegalArgumentException e) {
                rule.setMode(ReturnPatchMode.CONSTANT);
            }
            rule.setConstantValue(constantValue != null ? constantValue : "");
            rule.setExpression(expression != null ? expression : "");
            rule.setCondition(condition != null ? condition : "");
            rule.setThenValue(thenValue != null ? thenValue : "");
            rule.setElseValue(elseValue != null ? elseValue : "");
            rule.setScriptBody(scriptBody != null ? scriptBody : "");
            return rule;
        }
    }

    /**
     * Create a JaridaState from the current hooks and custom scripts.
     */
    public static JaridaState fromHooks(Map<String, HookSpec> hookSpecs, Map<String, Boolean> activeStates, String customScripts) {
        JaridaState state = new JaridaState();
        state.setCustomScriptPaths(customScripts != null ? customScripts : "");
        List<HookEntry> entries = new ArrayList<>();
        for (Map.Entry<String, HookSpec> entry : hookSpecs.entrySet()) {
            String key = entry.getKey();
            HookSpec spec = entry.getValue();
            boolean active = activeStates.getOrDefault(key, true);
            entries.add(HookEntry.fromHookSpec(spec, active));
        }
        state.setHooks(entries);
        return state;
    }

    /**
     * Extract hooks from this state. Returns a map of hookKey -> HookSpec.
     */
    public Map<String, HookSpec> toHookSpecs() {
        Map<String, HookSpec> specs = new LinkedHashMap<>();
        for (HookEntry entry : hooks) {
            if (entry.getHookKey() != null) {
                specs.put(entry.getHookKey(), entry.toHookSpec());
            }
        }
        return specs;
    }

    /**
     * Extract active states from this state. Returns a map of hookKey -> active.
     */
    public Map<String, Boolean> toActiveStates() {
        Map<String, Boolean> states = new LinkedHashMap<>();
        for (HookEntry entry : hooks) {
            if (entry.getHookKey() != null) {
                states.put(entry.getHookKey(), entry.isActive());
            }
        }
        return states;
    }
}
