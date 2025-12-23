package com.jarida.jadxfrida.model;

public class ReturnPatchRule {
    private boolean enabled;
    private ReturnPatchMode mode = ReturnPatchMode.CONSTANT;
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

    public ReturnPatchMode getMode() {
        return mode;
    }

    public void setMode(ReturnPatchMode mode) {
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
}
