package com.jarida.jadxfrida.util;

import jadx.core.dex.instructions.args.ArgType;

public final class TypeUtil {
    private TypeUtil() {
    }

    public static String toFridaType(ArgType type) {
        if (type == null) {
            return "java.lang.Object";
        }
        if (type.isPrimitive()) {
            return type.toString();
        }
        if (type.isArray()) {
            String base = toFridaType(type.getArrayRootElement());
            int dim = type.getArrayDimension();
            StringBuilder sb = new StringBuilder(base);
            for (int i = 0; i < dim; i++) {
                sb.append("[]");
            }
            return sb.toString();
        }
        if (type.isObject()) {
            String obj = type.getObject();
            if (obj != null && !obj.isEmpty()) {
                return normalizeClassName(stripGenerics(obj));
            }
        }
        return normalizeClassName(stripGenerics(type.toString()));
    }

    private static String stripGenerics(String typeName) {
        if (typeName == null) {
            return null;
        }
        int idx = typeName.indexOf('<');
        if (idx >= 0) {
            return typeName.substring(0, idx);
        }
        return typeName;
    }

    public static String normalizeClassName(String name) {
        if (name == null) {
            return null;
        }
        String out = name;
        out = out.replaceAll("\\.AnonymousClass(\\d+)", "\\$$1");
        return out;
    }

    public static boolean isVoid(String returnType) {
        return "void".equals(returnType);
    }

    public static boolean isPrimitive(String returnType) {
        return "boolean".equals(returnType)
                || "byte".equals(returnType)
                || "short".equals(returnType)
                || "char".equals(returnType)
                || "int".equals(returnType)
                || "long".equals(returnType)
                || "float".equals(returnType)
                || "double".equals(returnType);
    }

    public static boolean isBoolean(String returnType) {
        return "boolean".equals(returnType);
    }

    public static boolean isNumeric(String returnType) {
        return "byte".equals(returnType)
                || "short".equals(returnType)
                || "char".equals(returnType)
                || "int".equals(returnType)
                || "long".equals(returnType)
                || "float".equals(returnType)
                || "double".equals(returnType);
    }

    public static boolean isString(String returnType) {
        return "java.lang.String".equals(returnType) || "String".equals(returnType);
    }

    public static String toOverloadType(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return "java.lang.Object";
        }
        String normalized = stripGenerics(typeName).trim();
        if (normalized.isEmpty()) {
            return "java.lang.Object";
        }
        if (normalized.charAt(0) == '[') {
            // Already a JVM descriptor form.
            return normalized;
        }

        int dimensions = 0;
        while (normalized.endsWith("[]")) {
            dimensions++;
            normalized = normalized.substring(0, normalized.length() - 2);
        }
        if (dimensions == 0) {
            return normalized;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dimensions; i++) {
            sb.append('[');
        }
        String primitiveDescriptor = primitiveArrayDescriptor(normalized);
        if (primitiveDescriptor != null) {
            sb.append(primitiveDescriptor);
        } else {
            sb.append('L').append(normalizeClassName(normalized)).append(';');
        }
        return sb.toString();
    }

    private static String primitiveArrayDescriptor(String primitive) {
        switch (primitive) {
            case "boolean":
                return "Z";
            case "byte":
                return "B";
            case "char":
                return "C";
            case "short":
                return "S";
            case "int":
                return "I";
            case "long":
                return "J";
            case "float":
                return "F";
            case "double":
                return "D";
            default:
                return null;
        }
    }
}
