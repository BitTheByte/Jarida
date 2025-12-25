package com.jarida.jadxfrida.util;

import com.jarida.jadxfrida.model.ReturnPatchMode;
import com.jarida.jadxfrida.model.ReturnPatchRule;

public final class ReturnValueValidator {
    private ReturnValueValidator() {
    }

    public static String validate(String returnType, ReturnPatchRule rule) {
        if (rule == null || !rule.isEnabled()) {
            return null;
        }
        if (TypeUtil.isVoid(returnType)) {
            return "Cannot modify return value for void methods.";
        }
        if (rule.getMode() == ReturnPatchMode.CONSTANT) {
            String value = rule.getConstantValue();
            if (value == null || value.trim().isEmpty()) {
                return "Constant value is empty.";
            }
            String trimmed = value.trim();
            if (isRawLiteral(trimmed)) {
                return null;
            }
            if (TypeUtil.isBoolean(returnType)) {
                String v = trimmed.toLowerCase();
                if (!("true".equals(v) || "false".equals(v))) {
                    return "Boolean return type requires true/false.";
                }
            } else if (TypeUtil.isNumeric(returnType)) {
                try {
                    if ("char".equals(returnType)) {
                        String t = trimmed;
                        if (t.length() == 1) {
                            return null;
                        }
                        if ((t.startsWith("'") && t.endsWith("'")) || (t.startsWith("\"") && t.endsWith("\""))) {
                            String inner = t.substring(1, t.length() - 1);
                            if (inner.length() == 1) {
                                return null;
                            }
                        }
                    }
                    Double.parseDouble(trimmed);
                } catch (NumberFormatException e) {
                    return "Numeric return type requires a number.";
                }
            }
        } else if (rule.getMode() == ReturnPatchMode.EXPRESSION) {
            if (rule.getExpression() == null || rule.getExpression().trim().isEmpty()) {
                return "Expression is empty.";
            }
        } else if (rule.getMode() == ReturnPatchMode.CONDITIONAL) {
            if (rule.getCondition() == null || rule.getCondition().trim().isEmpty()) {
                return "Conditional rule requires a condition.";
            }
            if (rule.getThenValue() == null || rule.getThenValue().trim().isEmpty()) {
                return "Conditional rule requires a THEN value.";
            }
        } else if (rule.getMode() == ReturnPatchMode.SCRIPT) {
            if (rule.getScriptBody() == null || rule.getScriptBody().trim().isEmpty()) {
                return "Script body is empty.";
            }
        }
        return null;
    }

    private static boolean isRawLiteral(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase();
        return lower.startsWith("js:") || lower.startsWith("raw:");
    }
}
