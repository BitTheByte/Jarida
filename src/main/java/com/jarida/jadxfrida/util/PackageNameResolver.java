package com.jarida.jadxfrida.util;

import jadx.api.JadxDecompiler;

public final class PackageNameResolver {
    private PackageNameResolver() {
    }

    public static String resolvePackageName(JadxDecompiler decompiler) {
        if (decompiler == null) {
            return "";
        }
        try {
            String pkg = decompiler.getRoot().getAppPackage();
            return pkg == null ? "" : pkg;
        } catch (Exception e) {
            return "";
        }
    }
}
