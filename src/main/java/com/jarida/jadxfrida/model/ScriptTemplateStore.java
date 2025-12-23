package com.jarida.jadxfrida.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ScriptTemplateStore {
    private ScriptTemplateStore() {
    }

    public static List<ScriptTemplate> defaults() {
        List<ScriptTemplate> list = new ArrayList<>();
        list.add(new ScriptTemplate("None", ""));
        list.add(new ScriptTemplate("Send JSON event", "send({type: 'trace', className: TARGET_CLASS, method: TARGET_METHOD});"));
        list.add(new ScriptTemplate("Log stack for every call", "console.log(getStackTrace());"));
        list.add(new ScriptTemplate("Dump this object", "try { console.log('this=' + safeToString(this)); } catch (e) {}"));
        return Collections.unmodifiableList(list);
    }
}
