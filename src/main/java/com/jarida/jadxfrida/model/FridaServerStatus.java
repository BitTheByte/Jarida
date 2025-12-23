package com.jarida.jadxfrida.model;

public class FridaServerStatus {
    private final boolean present;
    private final boolean running;
    private final String details;

    public FridaServerStatus(boolean present, boolean running, String details) {
        this.present = present;
        this.running = running;
        this.details = details;
    }

    public boolean isPresent() {
        return present;
    }

    public boolean isRunning() {
        return running;
    }

    public String getDetails() {
        return details;
    }
}
