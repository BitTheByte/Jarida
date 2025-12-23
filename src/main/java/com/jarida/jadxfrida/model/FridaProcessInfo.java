package com.jarida.jadxfrida.model;

public class FridaProcessInfo {
    private final int pid;
    private final String name;

    public FridaProcessInfo(int pid, String name) {
        this.pid = pid;
        this.name = name;
    }

    public int getPid() {
        return pid;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return pid + "  " + name;
    }
}
