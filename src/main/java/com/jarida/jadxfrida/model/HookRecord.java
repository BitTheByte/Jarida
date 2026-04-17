package com.jarida.jadxfrida.model;

import jadx.api.metadata.ICodeNodeRef;

public class HookRecord {
    private final String key;
    private final String display;
    private ICodeNodeRef nodeRef;
    private boolean active = true;

    public HookRecord(String key, String display, ICodeNodeRef nodeRef) {
        this.key = key;
        this.display = display;
        this.nodeRef = nodeRef;
    }

    public String getKey() {
        return key;
    }

    public String getDisplay() {
        return display;
    }

    public ICodeNodeRef getNodeRef() {
        return nodeRef;
    }

    public void setNodeRef(ICodeNodeRef nodeRef) {
        this.nodeRef = nodeRef;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public String toString() {
        return display + (active ? " [active]" : " [inactive]");
    }
}
