package com.jarida.jadxfrida.util;

import com.jarida.jadxfrida.model.MethodTarget;
import jadx.api.JadxDecompiler;
import jadx.api.JavaMethod;
import jadx.api.JavaNode;
import jadx.api.metadata.ICodeNodeRef;
import jadx.core.dex.info.MethodInfo;
import jadx.core.dex.instructions.args.ArgType;
import jadx.core.dex.nodes.MethodNode;

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
        MethodNode methodNode = method.getMethodNode();
        MethodInfo methodInfo = methodNode.getMethodInfo();

        List<String> args = new ArrayList<>();
        for (ArgType type : methodInfo.getArgumentsTypes()) {
            args.add(TypeUtil.toFridaType(type));
        }
        String returnType = TypeUtil.toFridaType(methodInfo.getReturnType());
        String className = TypeUtil.normalizeClassName(methodInfo.getDeclClass().makeRawFullName());
        boolean isStatic = method.getAccessFlags() != null && method.getAccessFlags().isStatic();
        boolean isConstructor = method.isConstructor();
        String methodName = methodInfo.getName();
        if (isConstructor) {
            methodName = "$init";
        }

        List<String> displayArgs = new ArrayList<>();
        for (ArgType type : method.getArguments()) {
            displayArgs.add(TypeUtil.toFridaType(type));
        }
        String displayReturn = TypeUtil.toFridaType(method.getReturnType());
        String displayClass = TypeUtil.normalizeClassName(method.getDeclaringClass().getFullName());
        String displayMethod = method.getName();
        if (isConstructor) {
            displayMethod = "$init";
        }
        String displaySignature = displayClass + "." + displayMethod + "(" + String.join(", ", displayArgs) + ")" + ":" + displayReturn;

        return new MethodTarget(className, methodName, returnType, args, isStatic, isConstructor, displaySignature);
    }
}
