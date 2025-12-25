package com.jarida.jadxfrida.model;

public class ScriptTemplate {
    private final String name;
    private final String content;

    public ScriptTemplate(String name, String content) {
        this.name = name;
        this.content = content;
    }

    public String getName() {
        return name;
    }

    public String getContent() {
        return content;
    }

    @Override
    public String toString() {
        return name;
    }
}
