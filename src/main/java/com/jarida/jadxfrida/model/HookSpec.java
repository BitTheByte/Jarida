package com.jarida.jadxfrida.model;

public class HookSpec {
    private final MethodTarget target;
    private final ScriptOptions options;
    private final ReturnPatchRule returnPatchRule;
    private final String extraScript;
    private final TemplatePosition templatePosition;
    private final String hookId;
    private final boolean templateEnabled;
    private final String templateName;
    private final String templateContent;

    public HookSpec(MethodTarget target, ScriptOptions options, ReturnPatchRule returnPatchRule,
                    String extraScript, TemplatePosition templatePosition, String hookId,
                    boolean templateEnabled, String templateName, String templateContent) {
        this.target = target;
        this.options = options;
        this.returnPatchRule = returnPatchRule;
        this.extraScript = extraScript;
        this.templatePosition = templatePosition == null ? TemplatePosition.APPEND : templatePosition;
        this.hookId = hookId;
        this.templateEnabled = templateEnabled;
        this.templateName = templateName == null ? "" : templateName;
        this.templateContent = templateContent == null ? "" : templateContent;
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

    public boolean isTemplateEnabled() {
        return templateEnabled;
    }

    public String getTemplateName() {
        return templateName;
    }

    public String getTemplateContent() {
        return templateContent;
    }
}
