package com.jarida.jadxfrida.model;

public class HookSpec {
    private final MethodTarget target;
    private final ScriptOptions options;
    private final ReturnPatchRule returnPatchRule;
    private final String extraScript;
    private final TemplatePosition templatePosition;
    private final String hookId;

    public HookSpec(MethodTarget target, ScriptOptions options, ReturnPatchRule returnPatchRule,
                    String extraScript, TemplatePosition templatePosition, String hookId) {
        this.target = target;
        this.options = options;
        this.returnPatchRule = returnPatchRule;
        this.extraScript = extraScript;
        this.templatePosition = templatePosition == null ? TemplatePosition.APPEND : templatePosition;
        this.hookId = hookId;
    }

    public MethodTarget getTarget() {
        return target;
    }

    public ScriptOptions getOptions() {
        return options;
    }

    public ReturnPatchRule getReturnPatchRule() {
        return returnPatchRule;
    }

    public String getExtraScript() {
        return extraScript;
    }

    public TemplatePosition getTemplatePosition() {
        return templatePosition;
    }

    public String getHookId() {
        return hookId;
    }
}
