package com.jarida.jadxfrida.model;

public enum TemplatePosition {
    PREPEND("Prepend (before call)"),
    APPEND("Append (after call)");

    private final String label;

    TemplatePosition(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
