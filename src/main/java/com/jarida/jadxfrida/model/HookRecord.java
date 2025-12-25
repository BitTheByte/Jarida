package com.jarida.jadxfrida.model;

import jadx.api.metadata.ICodeNodeRef;

public class HookRecord {
    private final String key;
    private final String display;
    private ICodeNodeRef nodeRef;
    private int scriptId = -1;
    private boolean active = true;
    private boolean pendingUnload = false;

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

    public int getScriptId() {
        return scriptId;
    }

    public void setScriptId(int scriptId) {
        this.scriptId = scriptId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isPendingUnload() {
        return pendingUnload;
    }

    public void setPendingUnload(boolean pendingUnload) {
        this.pendingUnload = pendingUnload;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(display);
        if (pendingUnload) {
            sb.append(" [pending remove]");
        } else if (active) {
            sb.append(" [active]");
        } else {
            sb.append(" [inactive]");
        }
        return sb.toString();
    }
}
