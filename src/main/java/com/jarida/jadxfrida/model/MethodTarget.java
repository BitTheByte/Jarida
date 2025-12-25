package com.jarida.jadxfrida.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MethodTarget {
    private final String className;
    private final String methodName;
    private final String returnType;
    private final List<String> argTypes;
    private final boolean isStatic;
    private final boolean isConstructor;
    private final String displaySignature;

    public MethodTarget(String className, String methodName, String returnType, List<String> argTypes,
                        boolean isStatic, boolean isConstructor, String displaySignature) {
        this.className = className;
        this.methodName = methodName;
        this.returnType = returnType;
        this.argTypes = new ArrayList<>(argTypes);
        this.isStatic = isStatic;
        this.isConstructor = isConstructor;
        this.displaySignature = displaySignature;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getReturnType() {
        return returnType;
    }

    public List<String> getArgTypes() {
        return Collections.unmodifiableList(argTypes);
    }

    public boolean isStatic() {
        return isStatic;
    }

    public boolean isConstructor() {
        return isConstructor;
    }

    public String getDisplaySignature() {
        if (displaySignature != null && !displaySignature.isEmpty()) {
            return displaySignature;
        }
        return className + "." + methodName + "(" + String.join(", ", argTypes) + ")" + ":" + returnType;
    }
}
