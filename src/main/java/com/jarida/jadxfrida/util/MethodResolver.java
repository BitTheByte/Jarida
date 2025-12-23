package com.jarida.jadxfrida.util;

import com.jarida.jadxfrida.model.MethodTarget;
import jadx.api.JadxDecompiler;
import jadx.api.JavaMethod;
import jadx.api.JavaNode;
import jadx.api.metadata.ICodeNodeRef;
import jadx.core.dex.instructions.args.ArgType;

import java.util.ArrayList;
import java.util.List;

public final class MethodResolver {
    private MethodResolver() {
    }

    public static MethodTarget resolve(JadxDecompiler decompiler, ICodeNodeRef ref) {
        if (decompiler == null || ref == null) {
            return null;
        }
        JavaNode node = decompiler.getJavaNodeByRef(ref);
        if (node instanceof JavaMethod) {
            return fromJavaMethod((JavaMethod) node);
        }
        return null;
    }

    public static MethodTarget fromJavaMethod(JavaMethod method) {
        List<String> args = new ArrayList<>();
        for (ArgType type : method.getArguments()) {
            args.add(TypeUtil.toFridaType(type));
        }
        String returnType = TypeUtil.toFridaType(method.getReturnType());
        String className = TypeUtil.normalizeClassName(method.getDeclaringClass().getFullName());
        boolean isStatic = method.getAccessFlags() != null && method.getAccessFlags().isStatic();
        boolean isConstructor = method.isConstructor();
        String methodName = method.getName();
        if (isConstructor) {
            methodName = "$init";
        }
        return new MethodTarget(className, methodName, returnType, args, isStatic, isConstructor);
    }
}
