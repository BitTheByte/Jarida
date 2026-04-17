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
        sb.append("Java.perform(function() {\n");
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
        sb.append("    var JARIDA_MAX_DEPTH = 3;\n");
        sb.append("    var JARIDA_MAX_STR = 2000;\n");
        sb.append("    var JARIDA_MAX_COLLECTION = 50;\n");
        sb.append("    var JARIDA_MAX_FIELDS = 24;\n");
        sb.append("    function JARIDA_tryUse(name) {\n");
        sb.append("      try { return Java.use(name); } catch (e) { return null; }\n");
        sb.append("    }\n");
        sb.append("    var J_Collection = JARIDA_tryUse('java.util.Collection');\n");
        sb.append("    var J_Map = JARIDA_tryUse('java.util.Map');\n");
        sb.append("    var J_Bundle = JARIDA_tryUse('android.os.Bundle');\n");
        sb.append("    var J_Intent = JARIDA_tryUse('android.content.Intent');\n");
        sb.append("    var J_Arrays = JARIDA_tryUse('java.util.Arrays');\n");
        sb.append("    var J_Base64 = JARIDA_tryUse('android.util.Base64');\n");
        sb.append("    var J_System = JARIDA_tryUse('java.lang.System');\n");
        sb.append("    var J_Exception = JARIDA_tryUse('java.lang.Exception');\n");
        sb.append("    var J_Log = JARIDA_tryUse('android.util.Log');\n");
        sb.append("    var J_Thread = JARIDA_tryUse('java.lang.Thread');\n");
        sb.append("    var J_HashMap = JARIDA_tryUse('java.util.HashMap');\n");
        sb.append("    var J_ArrayList = JARIDA_tryUse('java.util.ArrayList');\n");
        sb.append("    var J_Long = JARIDA_tryUse('java.lang.Long');\n");
        sb.append("    function JARIDA_truncate(s) {\n");
        sb.append("      if (typeof s !== 'string') return s;\n");
        sb.append("      if (s.length > JARIDA_MAX_STR) return s.substring(0, JARIDA_MAX_STR) + '...<' + s.length + 'B>';\n");
        sb.append("      return s;\n");
        sb.append("    }\n");
        sb.append("    function safeToString(obj, opts, depth, seen) {\n");
        sb.append("      if (depth === undefined) depth = 0;\n");
        sb.append("      if (!seen) seen = [];\n");
        sb.append("      try {\n");
        sb.append("        if (obj === null || obj === undefined) return 'null';\n");
        sb.append("        if (typeof obj === 'string') return JARIDA_truncate(obj);\n");
        sb.append("        if (typeof obj === 'number' || typeof obj === 'boolean') return String(obj);\n");
        sb.append("        if (depth > JARIDA_MAX_DEPTH) return '<max-depth>';\n");
        sb.append("        if (obj.$className) {\n");
        sb.append("          for (var si = 0; si < seen.length; si++) { if (seen[si] === obj) return '<cycle>'; }\n");
        sb.append("          seen.push(obj);\n");
        sb.append("          try {\n");
        sb.append("            var cn = obj.$className;\n");
        sb.append("            if (cn === '[B' || cn === 'byte[]') return byteArrayToString(obj);\n");
        sb.append("            if (cn.charAt(0) === '[') return arrayToString(obj);\n");
        sb.append("            if (isBundle(obj)) return bundleToString(obj, opts, depth, seen);\n");
        sb.append("            if (isIntent(obj)) return intentToString(obj, opts, depth, seen);\n");
        sb.append("            if (isMap(obj)) return mapToString(obj, opts, depth, seen);\n");
        sb.append("            if (isCollection(obj)) return collectionToString(obj, opts, depth, seen);\n");
        sb.append("            return objectToString(obj, opts, depth, seen);\n");
        sb.append("          } finally { seen.pop(); }\n");
        sb.append("        }\n");
        sb.append("        return JARIDA_truncate(String(obj));\n");
        sb.append("      } catch (e) {\n");
        sb.append("        return '<error:' + e + '>';\n");
        sb.append("      }\n");
        sb.append("    }\n");

        sb.append("    function formatArgs(args, opts) {\n");
        sb.append("      if (!opts || !opts.logArgs) return '';\n");
        sb.append("      var out = [];\n");
        sb.append("      for (var i = 0; i < args.length; i++) {\n");
        sb.append("        out.push('arg' + i + '=' + safeToString(args[i], opts, 0, []));\n");
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
        sb.append("      if (!J_Collection) return false;\n");
        sb.append("      try { return J_Collection.class.isInstance(obj); } catch (e) { return false; }\n");
        sb.append("    }\n");
        sb.append("    function isBundle(obj) {\n");
        sb.append("      if (!J_Bundle) return false;\n");
        sb.append("      try { return J_Bundle.class.isInstance(obj); } catch (e) { return false; }\n");
        sb.append("    }\n");
        sb.append("    function isIntent(obj) {\n");
        sb.append("      if (!J_Intent) return false;\n");
        sb.append("      try { return J_Intent.class.isInstance(obj); } catch (e) { return false; }\n");
        sb.append("    }\n");
        sb.append("    function isMap(obj) {\n");
        sb.append("      if (!J_Map) return false;\n");
        sb.append("      try { return J_Map.class.isInstance(obj); } catch (e) { return false; }\n");
        sb.append("    }\n");

        sb.append("    function collectionToString(obj, opts, depth, seen) {\n");
        sb.append("      try {\n");
        sb.append("        var arr = obj.toArray();\n");
        sb.append("        var out = [];\n");
        sb.append("        var limit = Math.min(arr.length, JARIDA_MAX_COLLECTION);\n");
        sb.append("        for (var i = 0; i < limit; i++) {\n");
        sb.append("          out.push(safeToString(arr[i], opts, depth + 1, seen));\n");
        sb.append("        }\n");
        sb.append("        if (arr.length > limit) out.push('...+' + (arr.length - limit));\n");
        sb.append("        return '[' + out.join(', ') + ']';\n");
        sb.append("      } catch (e) {\n");
        sb.append("        try { return JARIDA_truncate(obj.toString()); } catch (e2) { return '<collection>'; }\n");
        sb.append("      }\n");
        sb.append("    }\n");

        sb.append("    function mapToString(obj, opts, depth, seen) {\n");
        sb.append("      try {\n");
        sb.append("        var entries = obj.entrySet().toArray();\n");
        sb.append("        var out = [];\n");
        sb.append("        var limit = Math.min(entries.length, JARIDA_MAX_COLLECTION);\n");
        sb.append("        for (var i = 0; i < limit; i++) {\n");
        sb.append("          var e = entries[i];\n");
        sb.append("          out.push(safeToString(e.getKey(), opts, depth + 1, seen) + '=' + safeToString(e.getValue(), opts, depth + 1, seen));\n");
        sb.append("        }\n");
        sb.append("        if (entries.length > limit) out.push('...+' + (entries.length - limit));\n");
        sb.append("        return '{' + out.join(', ') + '}';\n");
        sb.append("      } catch (e) { return '<map>'; }\n");
        sb.append("    }\n");
        sb.append("    function bundleToString(obj, opts, depth, seen) {\n");
        sb.append("      try {\n");
        sb.append("        var keys = obj.keySet().toArray();\n");
        sb.append("        var out = [];\n");
        sb.append("        var limit = Math.min(keys.length, JARIDA_MAX_COLLECTION);\n");
        sb.append("        for (var i = 0; i < limit; i++) {\n");
        sb.append("          var k = keys[i];\n");
        sb.append("          out.push(k + '=' + safeToString(obj.get(k), opts, depth + 1, seen));\n");
        sb.append("        }\n");
        sb.append("        if (keys.length > limit) out.push('...+' + (keys.length - limit));\n");
        sb.append("        return 'Bundle{' + out.join(', ') + '}';\n");
        sb.append("      } catch (e) { return '<bundle>'; }\n");
        sb.append("    }\n");
        sb.append("    function intentToString(obj, opts, depth, seen) {\n");
        sb.append("      try {\n");
        sb.append("        var out = [];\n");
        sb.append("        try { out.push('action=' + obj.getAction()); } catch (e) {}\n");
        sb.append("        try { out.push('data=' + obj.getDataString()); } catch (e) {}\n");
        sb.append("        try { var extras = obj.getExtras(); if (extras) { out.push('extras=' + bundleToString(extras, opts, depth + 1, seen)); } } catch (e) {}\n");
        sb.append("        return 'Intent{' + out.join(', ') + '}';\n");
        sb.append("      } catch (e) { return '<intent>'; }\n");
        sb.append("    }\n");
        sb.append("    function arrayToString(obj) {\n");
        sb.append("      if (!J_Arrays) { try { return JARIDA_truncate(String(obj)); } catch (e) { return '<array>'; } }\n");
        sb.append("      try {\n");
        sb.append("        var cls = obj.getClass();\n");
        sb.append("        if (cls.isArray()) {\n");
        sb.append("          var comp = cls.getComponentType();\n");
        sb.append("          if (!comp.isPrimitive()) { return JARIDA_truncate(J_Arrays.deepToString(obj)); }\n");
        sb.append("        }\n");
        sb.append("        return JARIDA_truncate(J_Arrays.toString(obj));\n");
        sb.append("      } catch (e) { return '<array>'; }\n");
        sb.append("    }\n");

        sb.append("    function byteArrayToString(obj) {\n");
        sb.append("      if (J_Base64) {\n");
        sb.append("        try { return JARIDA_truncate(J_Base64.encodeToString(obj, 0)); } catch (e) {}\n");
        sb.append("      }\n");
        sb.append("      if (J_Arrays) {\n");
        sb.append("        try { return JARIDA_truncate(J_Arrays.toString(obj)); } catch (e) {}\n");
        sb.append("      }\n");
        sb.append("      return '<byte[]>';\n");
        sb.append("    }\n");

        sb.append("    function getStackTrace() {\n");
        sb.append("      if (!J_Exception || !J_Log) return '<stack unavailable>';\n");
        sb.append("      try { return J_Log.getStackTraceString(J_Exception.$new()); } catch (e) { return '<stack unavailable: ' + e + '>'; }\n");
        sb.append("    }\n");
        sb.append("    function objectToString(obj, opts, depth, seen) {\n");
        sb.append("      try {\n");
        sb.append("        var cls = obj.getClass();\n");
        sb.append("        var name = cls.getName();\n");
        sb.append("        var id = 0;\n");
        sb.append("        if (J_System) { try { id = J_System.identityHashCode(obj); } catch (e) {} }\n");
        sb.append("        if (!opts || !opts.prettyPrint) {\n");
        sb.append("          return id ? (name + '@' + id) : name;\n");
        sb.append("        }\n");
        sb.append("        try {\n");
        sb.append("          var s = obj.toString();\n");
        sb.append("          if (s && s !== '[object Object]') return JARIDA_truncate(s);\n");
        sb.append("        } catch (e2) {}\n");
        sb.append("        try {\n");
        sb.append("          var fields = cls.getDeclaredFields();\n");
        sb.append("          var out = [];\n");
        sb.append("          var limit = Math.min(fields.length, JARIDA_MAX_FIELDS);\n");
        sb.append("          for (var i = 0; i < limit; i++) {\n");
        sb.append("            var f = fields[i];\n");
        sb.append("            try {\n");
        sb.append("              f.setAccessible(true);\n");
        sb.append("              out.push(f.getName() + '=' + safeToString(f.get(obj), opts, depth + 1, seen));\n");
        sb.append("            } catch (e3) {}\n");
        sb.append("          }\n");
        sb.append("          if (fields.length > limit) out.push('...+' + (fields.length - limit));\n");
        sb.append("          if (out.length > 0) return name + '{' + out.join(', ') + '}';\n");
        sb.append("        } catch (e4) {}\n");
        sb.append("        return id ? (name + '@' + id) : name;\n");
        sb.append("      } catch (e) {\n");
        sb.append("        try { return JARIDA_truncate(obj.toString()); } catch (e2) { return '<object>'; }\n");
        sb.append("      }\n");
        sb.append("    }\n");

        sb.append("    function mapFromObject(obj) {\n");
        sb.append("      if (!J_HashMap) return obj;\n");
        sb.append("      try {\n");
        sb.append("        var map = J_HashMap.$new();\n");
        sb.append("        for (var k in obj) {\n");
        sb.append("          if (!obj.hasOwnProperty(k)) continue;\n");
        sb.append("          map.put(k, obj[k]);\n");
        sb.append("        }\n");
        sb.append("        return map;\n");
        sb.append("      } catch (e) { return obj; }\n");
        sb.append("    }\n");
        sb.append("    function listFromArray(arr) {\n");
        sb.append("      if (!J_ArrayList) return arr;\n");
        sb.append("      try {\n");
        sb.append("        var list = J_ArrayList.$new();\n");
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
        sb.append("          case 'long': return J_Long ? J_Long.valueOf(String(val)) : val;\n");
        sb.append("          case 'float': return parseFloat(val);\n");
        sb.append("          case 'double': return parseFloat(val);\n");
        sb.append("          case 'java.lang.String':\n");
        sb.append("          case 'String': return String(val);\n");
        sb.append("          case 'java.lang.Boolean': { var B = JARIDA_tryUse('java.lang.Boolean'); return B ? B.valueOf(String(val)) : val; }\n");
        sb.append("          case 'java.lang.Integer': { var I = JARIDA_tryUse('java.lang.Integer'); return I ? I.valueOf(String(val)) : val; }\n");
        sb.append("          case 'java.lang.Long': return J_Long ? J_Long.valueOf(String(val)) : val;\n");
        sb.append("          case 'java.lang.Short': { var S = JARIDA_tryUse('java.lang.Short'); return S ? S.valueOf(String(val)) : val; }\n");
        sb.append("          case 'java.lang.Byte': { var BY = JARIDA_tryUse('java.lang.Byte'); return BY ? BY.valueOf(String(val)) : val; }\n");
        sb.append("          case 'java.lang.Float': { var F = JARIDA_tryUse('java.lang.Float'); return F ? F.valueOf(String(val)) : val; }\n");
        sb.append("          case 'java.lang.Double': { var D = JARIDA_tryUse('java.lang.Double'); return D ? D.valueOf(String(val)) : val; }\n");
        sb.append("          case 'java.lang.Character': { var C = JARIDA_tryUse('java.lang.Character'); return C ? C.valueOf(String(val)) : val; }\n");
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
        List<String> overloadArgTypes = new java.util.ArrayList<>();
        for (String argType : argTypes) {
            overloadArgTypes.add(TypeUtil.toOverloadType(argType));
        }
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
            for (int i = 0; i < overloadArgTypes.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(JsEscaper.quote(overloadArgTypes.get(i)));
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
        sb.append("      if (OPTIONS").append(suffix).append(".logThread && J_Thread) {\n");
        sb.append("        try { threadName = J_Thread.currentThread().getName(); } catch (e) {}\n");
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
