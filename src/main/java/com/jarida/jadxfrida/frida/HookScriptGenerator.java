package com.jarida.jadxfrida.frida;

import com.jarida.jadxfrida.model.HookSpec;
import com.jarida.jadxfrida.model.MethodTarget;
import com.jarida.jadxfrida.model.ReturnPatchRule;
import com.jarida.jadxfrida.model.ScriptOptions;
import com.jarida.jadxfrida.model.TemplatePosition;
import com.jarida.jadxfrida.util.JsEscaper;
import com.jarida.jadxfrida.util.TypeUtil;

import java.util.List;

public final class HookScriptGenerator {
    private HookScriptGenerator() {
    }

    public static String generate(HookSpec spec) {
        return generateCombined(java.util.Collections.singletonList(spec));
    }

    public static String generateCombined(java.util.Collection<HookSpec> specs) {
        return generateCombined(specs, null);
    }

    public static String generateCombined(java.util.Collection<HookSpec> specs, java.util.List<String> globalScripts) {
        StringBuilder sb = new StringBuilder();
        sb.append("'use strict';\n");
        appendGlobalScripts(sb, globalScripts);
        sb.append("setImmediate(function() {\n");
        sb.append("  Java.perform(function() {\n");
        appendHelpers(sb);
        int idx = 0;
        if (specs == null || specs.isEmpty()) {
            sb.append("    // no hooks\n");
        } else {
            for (HookSpec spec : specs) {
                if (spec == null || spec.getTarget() == null) {
                    continue;
                }
                idx++;
                appendHook(sb, spec, idx);
            }
        }
        sb.append("  });\n");
        sb.append("});\n");
        return sb.toString();
    }

    private static String buildConstantLiteral(ReturnPatchRule patch, String returnType) {
        if (patch == null) {
            return "null";
        }
        String val = patch.getConstantValue();
        if (val == null) {
            return "null";
        }
        String trimmed = val.trim();
        if (trimmed.isEmpty()) {
            return "null";
        }
        if (trimmed.startsWith("js:") || trimmed.startsWith("raw:")) {
            return trimmed.substring(trimmed.indexOf(':') + 1).trim();
        }
        if ("null".equals(trimmed)) {
            return "null";
        }
        if (TypeUtil.isBoolean(returnType)) {
            return trimmed.toLowerCase();
        }
        if (TypeUtil.isNumeric(returnType)) {
            if ("char".equals(returnType)) {
                if (trimmed.length() == 1) {
                    return Integer.toString(trimmed.charAt(0));
                }
                if ((trimmed.startsWith("'") && trimmed.endsWith("'")) || (trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
                    String inner = trimmed.substring(1, trimmed.length() - 1);
                    if (inner.length() == 1) {
                        return Integer.toString(inner.charAt(0));
                    }
                }
            }
            return trimmed;
        }
        return JsEscaper.quote(trimmed);
    }

    private static void appendExtraScriptInline(StringBuilder sb, String extraScript, String indent) {
        if (extraScript == null || extraScript.trim().isEmpty()) {
            return;
        }
        sb.append(indent).append("try {\n");
        String[] lines = extraScript.split("\\R");
        for (String line : lines) {
            sb.append(indent).append("  ").append(line).append("\n");
        }
        sb.append(indent).append("} catch (e) { console.log('[JARIDA] Template error: ' + e); }\n");
    }

    private static void appendGlobalScripts(StringBuilder sb, java.util.List<String> scripts) {
        if (scripts == null || scripts.isEmpty()) {
            return;
        }
        for (String script : scripts) {
            if (script == null || script.trim().isEmpty()) {
                continue;
            }
            sb.append("\n// ---- Jarida custom script ----\n");
            sb.append(script).append("\n");
        }
        sb.append("\n");
    }

    private static void appendHelpers(StringBuilder sb) {
        sb.append("    function safeToString(obj, opts) {\n");
        sb.append("      try {\n");
        sb.append("        if (obj === null || obj === undefined) return 'null';\n");
        sb.append("        if (typeof obj === 'string') return obj;\n");
        sb.append("        if (typeof obj === 'number' || typeof obj === 'boolean') return String(obj);\n");
        sb.append("        if (obj.$className) {\n");
        sb.append("          var cn = obj.$className;\n");
        sb.append("          if (cn === '[B' || cn === 'byte[]') return byteArrayToString(obj);\n");
        sb.append("          if (cn.indexOf('[]') !== -1 || cn.charAt(0) === '[') return arrayToString(obj);\n");
        sb.append("          if (isBundle(obj)) return bundleToString(obj);\n");
        sb.append("          if (isIntent(obj)) return intentToString(obj);\n");
        sb.append("          if (isMap(obj)) return mapToString(obj);\n");
        sb.append("          if (isCollection(obj)) return collectionToString(obj);\n");
        sb.append("          return objectToString(obj, opts);\n");
        sb.append("        }\n");
        sb.append("        return String(obj);\n");
        sb.append("      } catch (e) {\n");
        sb.append("        return '<error:' + e + '>';\n");
        sb.append("      }\n");
        sb.append("    }\n");

        sb.append("    function formatArgs(args, opts) {\n");
        sb.append("      if (!opts || !opts.logArgs) return '';\n");
        sb.append("      var out = [];\n");
        sb.append("      for (var i = 0; i < args.length; i++) {\n");
        sb.append("        out.push('arg' + i + '=' + safeToString(args[i], opts));\n");
        sb.append("      }\n");
        sb.append("      return out.join(', ');\n");
        sb.append("    }\n");

        sb.append("    function formatCall(methodSig, args, opts, threadName) {\n");
        sb.append("      var argsText = formatArgs(args, opts);\n");
        sb.append("      var call = methodSig;\n");
        sb.append("      if (argsText) { call += ' { ' + argsText + ' }'; }\n");
        sb.append("      if (threadName) { call += ' [thread=' + threadName + ']'; }\n");
        sb.append("      return call;\n");
        sb.append("    }\n");

        sb.append("    function isCollection(obj) {\n");
        sb.append("      try {\n");
        sb.append("        var Collection = Java.use('java.util.Collection');\n");
        sb.append("        return Collection.class.isInstance(obj);\n");
        sb.append("      } catch (e) { return false; }\n");
        sb.append("    }\n");

        sb.append("    function isBundle(obj) {\n");
        sb.append("      try {\n");
        sb.append("        var Bundle = Java.use('android.os.Bundle');\n");
        sb.append("        return Bundle.class.isInstance(obj);\n");
        sb.append("      } catch (e) { return false; }\n");
        sb.append("    }\n");
        sb.append("    function isIntent(obj) {\n");
        sb.append("      try {\n");
        sb.append("        var Intent = Java.use('android.content.Intent');\n");
        sb.append("        return Intent.class.isInstance(obj);\n");
        sb.append("      } catch (e) { return false; }\n");
        sb.append("    }\n");

        sb.append("    function isMap(obj) {\n");
        sb.append("      try {\n");
        sb.append("        var Map = Java.use('java.util.Map');\n");
        sb.append("        return Map.class.isInstance(obj);\n");
        sb.append("      } catch (e) { return false; }\n");
        sb.append("    }\n");
        sb.append("    function collectionToString(obj) {\n");
        sb.append("      try { return obj.toString(); } catch (e) { return '<collection>'; }\n");
        sb.append("    }\n");

        sb.append("    function mapToString(obj) {\n");
        sb.append("      try {\n");
        sb.append("        var entries = obj.entrySet().toArray();\n");
        sb.append("        var out = [];\n");
        sb.append("        var limit = Math.min(entries.length, 50);\n");
        sb.append("        for (var i = 0; i < limit; i++) {\n");
        sb.append("          var e = entries[i];\n");
        sb.append("          out.push(safeToString(e.getKey(), null) + '=' + safeToString(e.getValue(), null));\n");
        sb.append("        }\n");
        sb.append("        if (entries.length > limit) { out.push('...'); }\n");
        sb.append("        return '{' + out.join(', ') + '}';\n");
        sb.append("      } catch (e) { return '<map>'; }\n");
        sb.append("    }\n");
        sb.append("    function bundleToString(obj) {\n");
        sb.append("      try {\n");
        sb.append("        var keys = obj.keySet().toArray();\n");
        sb.append("        var out = [];\n");
        sb.append("        var limit = Math.min(keys.length, 50);\n");
        sb.append("        for (var i = 0; i < limit; i++) {\n");
        sb.append("          var k = keys[i];\n");
        sb.append("          out.push(k + '=' + safeToString(obj.get(k), null));\n");
        sb.append("        }\n");
        sb.append("        if (keys.length > limit) { out.push('...'); }\n");
        sb.append("        return 'Bundle{' + out.join(', ') + '}';\n");
        sb.append("      } catch (e) { return '<bundle>'; }\n");
        sb.append("    }\n");
        sb.append("    function intentToString(obj) {\n");
        sb.append("      try {\n");
        sb.append("        var out = [];\n");
        sb.append("        out.push('action=' + obj.getAction());\n");
        sb.append("        out.push('data=' + obj.getDataString());\n");
        sb.append("        try { var extras = obj.getExtras(); if (extras) { out.push('extras=' + bundleToString(extras)); } } catch (e) {}\n");
        sb.append("        return 'Intent{' + out.join(', ') + '}';\n");
        sb.append("      } catch (e) { return '<intent>'; }\n");
        sb.append("    }\n");
        sb.append("    function arrayToString(obj) {\n");
        sb.append("      try {\n");
        sb.append("        var Arrays = Java.use('java.util.Arrays');\n");
        sb.append("        var cls = obj.getClass();\n");
        sb.append("        if (cls.isArray()) {\n");
        sb.append("          var comp = cls.getComponentType();\n");
        sb.append("          if (!comp.isPrimitive()) { return Arrays.deepToString(obj); }\n");
        sb.append("        }\n");
        sb.append("        return Arrays.toString(obj);\n");
        sb.append("      } catch (e) { return '<array>'; }\n");
        sb.append("    }\n");

        sb.append("    function byteArrayToString(obj) {\n");
        sb.append("      try {\n");
        sb.append("        var Base64 = Java.use('android.util.Base64');\n");
        sb.append("        return Base64.encodeToString(obj, 0);\n");
        sb.append("      } catch (e) {\n");
        sb.append("        try {\n");
        sb.append("          var Arrays = Java.use('java.util.Arrays');\n");
        sb.append("          return Arrays.toString(obj);\n");
        sb.append("        } catch (e2) { return '<byte[]>'; }\n");
        sb.append("      }\n");
        sb.append("    }\n");

        sb.append("    function getStackTrace() {\n");
        sb.append("      try {\n");
        sb.append("        var Exception = Java.use('java.lang.Exception');\n");
        sb.append("        var Log = Java.use('android.util.Log');\n");
        sb.append("        return Log.getStackTraceString(Exception.$new());\n");
        sb.append("      } catch (e) { return '<stack unavailable: ' + e + '>'; }\n");
        sb.append("    }\n");
        sb.append("    function objectToString(obj, opts) {\n");
        sb.append("      try {\n");
        sb.append("        var cls = obj.getClass();\n");
        sb.append("        var name = cls.getName();\n");
        sb.append("        var id = 0;\n");
        sb.append("        try {\n");
        sb.append("          var System = Java.use('java.lang.System');\n");
        sb.append("          id = System.identityHashCode(obj);\n");
        sb.append("        } catch (e) {}\n");
        sb.append("        if (!opts || !opts.prettyPrint) {\n");
        sb.append("          return id ? (name + '@' + id) : name;\n");
        sb.append("        }\n");
        sb.append("        try {\n");
        sb.append("          var s = obj.toString();\n");
        sb.append("          if (s && s !== '[object Object]') return s;\n");
        sb.append("        } catch (e2) {}\n");
        sb.append("        try {\n");
        sb.append("          var fields = cls.getDeclaredFields();\n");
        sb.append("          var out = [];\n");
        sb.append("          var limit = Math.min(fields.length, 20);\n");
        sb.append("          for (var i = 0; i < limit; i++) {\n");
        sb.append("            var f = fields[i];\n");
        sb.append("            try {\n");
        sb.append("              f.setAccessible(true);\n");
        sb.append("              out.push(f.getName() + '=' + safeToString(f.get(obj), null));\n");
        sb.append("            } catch (e3) {}\n");
        sb.append("          }\n");
        sb.append("          if (fields.length > limit) { out.push('...'); }\n");
        sb.append("          if (out.length > 0) return name + '{' + out.join(', ') + '}';\n");
        sb.append("        } catch (e4) {}\n");
        sb.append("        return id ? (name + '@' + id) : name;\n");
        sb.append("      } catch (e) {\n");
        sb.append("        try { return obj.toString(); } catch (e2) { return '<object>'; }\n");
        sb.append("      }\n");
        sb.append("    }\n");

        sb.append("    function mapFromObject(obj) {\n");
        sb.append("      try {\n");
        sb.append("        var HashMap = Java.use('java.util.HashMap');\n");
        sb.append("        var map = HashMap.$new();\n");
        sb.append("        for (var k in obj) {\n");
        sb.append("          if (!obj.hasOwnProperty(k)) continue;\n");
        sb.append("          map.put(k, obj[k]);\n");
        sb.append("        }\n");
        sb.append("        return map;\n");
        sb.append("      } catch (e) { return obj; }\n");
        sb.append("    }\n");
        sb.append("    function listFromArray(arr) {\n");
        sb.append("      try {\n");
        sb.append("        var ArrayList = Java.use('java.util.ArrayList');\n");
        sb.append("        var list = ArrayList.$new();\n");
        sb.append("        for (var i = 0; i < arr.length; i++) { list.add(arr[i]); }\n");
        sb.append("        return list;\n");
        sb.append("      } catch (e) { return arr; }\n");
        sb.append("    }\n");

        sb.append("    function castReturn(val, returnType) {\n");
        sb.append("      try {\n");
        sb.append("        if (returnType === 'void') return;\n");
        sb.append("        if (val === null || val === undefined) return null;\n");
        sb.append("        if (returnType.endsWith('[]') && Array.isArray(val)) {\n");
        sb.append("          var comp = returnType.substring(0, returnType.length - 2);\n");
        sb.append("          try { return Java.array(comp, val); } catch (e) { return val; }\n");
        sb.append("        }\n");
        sb.append("        switch (returnType) {\n");
        sb.append("          case 'boolean': return !!val;\n");
        sb.append("          case 'byte':\n");
        sb.append("          case 'short':\n");
        sb.append("          case 'int': return parseInt(val);\n");
        sb.append("          case 'char':\n");
        sb.append("            if (typeof val === 'string' && val.length > 0) return val.charCodeAt(0);\n");
        sb.append("            return parseInt(val);\n");
        sb.append("          case 'long': return Java.use('java.lang.Long').valueOf(String(val));\n");
        sb.append("          case 'float': return parseFloat(val);\n");
        sb.append("          case 'double': return parseFloat(val);\n");
        sb.append("          case 'java.lang.String':\n");
        sb.append("          case 'String': return String(val);\n");
        sb.append("          case 'java.lang.Boolean': return Java.use('java.lang.Boolean').valueOf(String(val));\n");
        sb.append("          case 'java.lang.Integer': return Java.use('java.lang.Integer').valueOf(String(val));\n");
        sb.append("          case 'java.lang.Long': return Java.use('java.lang.Long').valueOf(String(val));\n");
        sb.append("          case 'java.lang.Short': return Java.use('java.lang.Short').valueOf(String(val));\n");
        sb.append("          case 'java.lang.Byte': return Java.use('java.lang.Byte').valueOf(String(val));\n");
        sb.append("          case 'java.lang.Float': return Java.use('java.lang.Float').valueOf(String(val));\n");
        sb.append("          case 'java.lang.Double': return Java.use('java.lang.Double').valueOf(String(val));\n");
        sb.append("          case 'java.lang.Character': return Java.use('java.lang.Character').valueOf(String(val));\n");
        sb.append("          default:\n");
        sb.append("            if (returnType.indexOf('java.util.Map') === 0 && typeof val === 'object' && !Array.isArray(val)) {\n");
        sb.append("              return mapFromObject(val);\n");
        sb.append("            }\n");
        sb.append("            if ((returnType.indexOf('java.util.List') === 0 || returnType.indexOf('java.util.Collection') === 0 || returnType.indexOf('java.util.Set') === 0)\n");
        sb.append("                && Array.isArray(val)) {\n");
        sb.append("              return listFromArray(val);\n");
        sb.append("            }\n");
        sb.append("            return val;\n");
        sb.append("        }\n");
        sb.append("      } catch (e) { return val; }\n");
        sb.append("    }\n");

        sb.append("    function applyPatch(ret, args, thiz, patch) {\n");
        sb.append("      if (!patch || !patch.enabled) return ret;\n");
        sb.append("      try {\n");
        sb.append("        if (patch.mode === 'CONSTANT') {\n");
        sb.append("          return patch.constValue;\n");
        sb.append("        }\n");
        sb.append("        if (patch.mode === 'EXPRESSION') {\n");
        sb.append("          var fn = new Function('ret', 'args', 'thiz', 'Java', 'send', 'console', patch.expr);\n");
        sb.append("          return fn(ret, args, thiz, Java, send, console);\n");
        sb.append("        }\n");
        sb.append("        if (patch.mode === 'CONDITIONAL') {\n");
        sb.append("          var cfn = new Function('ret', 'args', 'thiz', 'Java', 'send', 'console', 'return (' + patch.cond + ');');\n");
        sb.append("          var cond = cfn(ret, args, thiz, Java, send, console);\n");
        sb.append("          if (cond) {\n");
        sb.append("            var tfn = new Function('ret', 'args', 'thiz', 'Java', 'send', 'console', 'return (' + patch.thenValue + ');');\n");
        sb.append("            return tfn(ret, args, thiz, Java, send, console);\n");
        sb.append("          }\n");
        sb.append("          if (patch.elseValue && patch.elseValue.trim() !== '') {\n");
        sb.append("            var efn = new Function('ret', 'args', 'thiz', 'Java', 'send', 'console', 'return (' + patch.elseValue + ');');\n");
        sb.append("            return efn(ret, args, thiz, Java, send, console);\n");
        sb.append("          }\n");
        sb.append("          return ret;\n");
        sb.append("        }\n");
        sb.append("        if (patch.mode === 'SCRIPT') {\n");
        sb.append("          var sfn = new Function('ret', 'args', 'thiz', 'Java', 'send', 'console', patch.script);\n");
        sb.append("          var out = sfn(ret, args, thiz, Java, send, console);\n");
        sb.append("          if (out === undefined) return ret;\n");
        sb.append("          return out;\n");
        sb.append("        }\n");
        sb.append("        return ret;\n");
        sb.append("      } catch (e) {\n");
        sb.append("        console.log('[JARIDA] Patch error: ' + e);\n");
        sb.append("        return ret;\n");
        sb.append("      }\n");
        sb.append("    }\n");
    }

    private static void appendHook(StringBuilder sb, HookSpec spec, int idx) {
        MethodTarget target = spec.getTarget();
        ScriptOptions opt = spec.getOptions();
        ReturnPatchRule patch = spec.getReturnPatchRule();
        List<String> argTypes = target.getArgTypes();
        String suffix = "_" + idx;
        String extraScript = spec.getExtraScript();
        boolean hasExtra = extraScript != null && !extraScript.trim().isEmpty();
        TemplatePosition position = spec.getTemplatePosition();

        sb.append("    try {\n");
        sb.append("    // Hook: ").append(target.getDisplaySignature()).append("\n");
        sb.append("    var TARGET_CLASS").append(suffix).append(" = ").append(JsEscaper.quote(target.getClassName())).append(";\n");
        sb.append("    var TARGET_METHOD").append(suffix).append(" = ").append(JsEscaper.quote(target.getMethodName())).append(";\n");
        sb.append("    var RETURN_TYPE").append(suffix).append(" = ").append(JsEscaper.quote(target.getReturnType())).append(";\n");
        sb.append("    var ARG_TYPES").append(suffix).append(" = [");
        for (int i = 0; i < argTypes.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(JsEscaper.quote(argTypes.get(i)));
        }
        sb.append("];\n");
        sb.append("    var METHOD_SIG").append(suffix)
                .append(" = ").append(JsEscaper.quote(target.getDisplaySignature())).append(";\n");
        sb.append("    var OPTIONS").append(suffix).append(" = {\n");
        sb.append("      logArgs: ").append(opt.isLogArgs()).append(",\n");
        sb.append("      logReturn: ").append(opt.isLogReturn()).append(",\n");
        sb.append("      logThread: ").append(opt.isLogThread()).append(",\n");
        sb.append("      printStack: ").append(opt.isPrintStack()).append(",\n");
        sb.append("      printThis: ").append(opt.isPrintThis()).append(",\n");
        sb.append("      prettyPrint: ").append(opt.isPrettyPrint()).append("\n");
        sb.append("    };\n");
        boolean enabled = patch != null && patch.isEnabled() && !TypeUtil.isVoid(target.getReturnType());
        sb.append("    var PATCH").append(suffix).append(" = {\n");
        sb.append("      enabled: ").append(enabled).append(",\n");
        sb.append("      mode: ").append(JsEscaper.quote(patch == null ? "" : patch.getMode().name())).append(",\n");
        sb.append("      constValue: ").append(buildConstantLiteral(patch, target.getReturnType())).append(",\n");
        sb.append("      expr: ").append(JsEscaper.quote(safe(patch == null ? null : patch.getExpression()))).append(",\n");
        sb.append("      cond: ").append(JsEscaper.quote(safe(patch == null ? null : patch.getCondition()))).append(",\n");
        sb.append("      thenValue: ").append(JsEscaper.quote(safe(patch == null ? null : patch.getThenValue()))).append(",\n");
        sb.append("      elseValue: ").append(JsEscaper.quote(safe(patch == null ? null : patch.getElseValue()))).append(",\n");
        sb.append("      script: ").append(JsEscaper.quote(safe(patch == null ? null : patch.getScriptBody()))).append("\n");
        sb.append("    };\n");

        sb.append("    var clazz").append(suffix).append(" = Java.use(TARGET_CLASS").append(suffix).append(");\n");
        sb.append("    var CALL_COUNT").append(suffix).append(" = 0;\n");
        sb.append("    var overload").append(suffix).append(" = null;\n");
        if (target.isConstructor()) {
            sb.append("    var ctor").append(suffix).append(" = clazz").append(suffix).append(".$init;\n");
            sb.append("    if (!ctor").append(suffix).append(") {\n");
            sb.append("      console.log('[JARIDA] Hook skipped, constructor not found: ' + TARGET_CLASS").append(suffix).append(");\n");
            sb.append("    } else {\n");
            sb.append("      try {\n");
            sb.append("        overload").append(suffix).append(" = ctor").append(suffix);
        } else {
            sb.append("    var methodRef").append(suffix).append(" = clazz").append(suffix).append("[TARGET_METHOD").append(suffix).append("];\n");
            sb.append("    if (!methodRef").append(suffix).append(") {\n");
            sb.append("      console.log('[JARIDA] Hook skipped, method not found: ' + METHOD_SIG").append(suffix).append(");\n");
            sb.append("    } else {\n");
            sb.append("      try {\n");
            sb.append("        overload").append(suffix).append(" = methodRef").append(suffix);
        }
        if (argTypes.isEmpty()) {
            sb.append(".overload();\n");
        } else {
            sb.append(".overload(");
            for (int i = 0; i < argTypes.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(JsEscaper.quote(argTypes.get(i)));
            }
            sb.append(");\n");
        }
        sb.append("      } catch (e) {\n");
        sb.append("        console.log('[JARIDA] Hook skipped (no overload): ' + METHOD_SIG").append(suffix).append(" + ' => ' + e);\n");
        sb.append("      }\n");
        sb.append("    }\n");

        sb.append("    if (overload").append(suffix).append(") {\n");
        sb.append("    overload").append(suffix).append(".implementation = function() {\n");
        sb.append("      var args = [].slice.call(arguments);\n");
        sb.append("      var threadName = null;\n");
        sb.append("      if (OPTIONS").append(suffix).append(".logThread) {\n");
        sb.append("        try {\n");
        sb.append("          var Thread = Java.use('java.lang.Thread');\n");
        sb.append("          threadName = Thread.currentThread().getName();\n");
        sb.append("        } catch (e) {}\n");
        sb.append("      }\n");
        sb.append("      var callId = ++CALL_COUNT").append(suffix).append(";\n");
        sb.append("      var callLine = formatCall(METHOD_SIG").append(suffix).append(", args, OPTIONS").append(suffix).append(", threadName);\n");
        sb.append("      var prefix = '[JARIDA] #' + callId + ' ';\n");
        sb.append("      console.log(prefix + 'CALL ' + callLine);\n");
        sb.append("      var TARGET_CLASS = TARGET_CLASS").append(suffix).append(";\n");
        sb.append("      var TARGET_METHOD = TARGET_METHOD").append(suffix).append(";\n");
        sb.append("      var METHOD_SIG = METHOD_SIG").append(suffix).append(";\n");
        sb.append("      if (OPTIONS").append(suffix).append(".printThis) {\n");
        sb.append("        try { console.log(prefix + 'THIS ' + safeToString(this, OPTIONS").append(suffix).append(")); } catch (e) {}\n");
        sb.append("      }\n");
        sb.append("      if (OPTIONS").append(suffix).append(".printStack) {\n");
        sb.append("        console.log(prefix + 'STACK\\n' + getStackTrace());\n");
        sb.append("      }\n");
        if (hasExtra && position == TemplatePosition.PREPEND) {
            appendExtraScriptInline(sb, extraScript, "      ");
        }

        sb.append("      var ret = overload").append(suffix).append(".call(this");
        if (!argTypes.isEmpty()) {
            for (int i = 0; i < argTypes.size(); i++) {
                sb.append(", args[").append(i).append("]");
            }
        }
        sb.append(");\n");

        if (hasExtra && position == TemplatePosition.APPEND) {
            appendExtraScriptInline(sb, extraScript, "      ");
        }
        sb.append("      if (OPTIONS").append(suffix).append(".logReturn) {\n");
        sb.append("        if (RETURN_TYPE").append(suffix).append(" === 'void') {\n");
        sb.append("          console.log(prefix + 'RET  ' + METHOD_SIG").append(suffix).append(" + ' => void');\n");
        sb.append("        } else {\n");
        sb.append("          console.log(prefix + 'RET  ' + METHOD_SIG").append(suffix).append(" + ' => ' + safeToString(ret, OPTIONS").append(suffix).append("));\n");
        sb.append("        }\n");
        sb.append("      }\n");
        sb.append("      var patched = applyPatch(ret, args, this, PATCH").append(suffix).append(");\n");
        sb.append("      if (patched !== ret && RETURN_TYPE").append(suffix).append(" !== 'void') {\n");
        sb.append("        console.log(prefix + 'RET  ' + METHOD_SIG").append(suffix).append(" + ' => ' + safeToString(patched, OPTIONS").append(suffix).append(") + ' (patched)');\n");
        sb.append("      }\n");
        sb.append("      if (RETURN_TYPE").append(suffix).append(" === 'void') { return; }\n");
        sb.append("      return castReturn(patched, RETURN_TYPE").append(suffix).append(");\n");
        sb.append("    };\n");
        sb.append("    }\n");
        // avoid noisy "Hooked" logs on auto-reload; extra scripts are injected per-call
        sb.append("    } catch (e) { console.log('[JARIDA] Hook error: ' + e); }\n");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
