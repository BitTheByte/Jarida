package com.jarida.jadxfrida.model;

public class AdbDevice {
    private final String id;
    private final String status;
    private final String description;

    public AdbDevice(String id, String status, String description) {
        this.id = id;
        this.status = status;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        if (id == null || id.trim().isEmpty()) {
            if (description != null && !description.trim().isEmpty()) {
                return description.trim();
            }
            return status == null ? "" : status.trim();
        }
        return id.trim();
    }
}
