package com.jarida.jadxfrida.model;

public class FridaSessionConfig {
    private DeviceMode deviceMode = DeviceMode.USB;
    private String deviceId = "";
    private String remoteHost = "127.0.0.1";
    private int remotePort = 27042;
    private boolean spawn = true;
    private String targetPackage = "";
    private String targetProcess = "";
    private int targetPid = -1;
    private String extraFridaArgs = "";
    private String fridaPath = "frida";
    private String fridaPsPath = "frida-ps";
    private String adbPath = "adb";

    public DeviceMode getDeviceMode() {
        return deviceMode;
    }

    public void setDeviceMode(DeviceMode deviceMode) {
        this.deviceMode = deviceMode;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }

    public int getRemotePort() {
        return remotePort;
    }

    public void setRemotePort(int remotePort) {
        this.remotePort = remotePort;
    }

    public boolean isSpawn() {
        return spawn;
    }

    public void setSpawn(boolean spawn) {
        this.spawn = spawn;
    }

    public String getTargetPackage() {
        return targetPackage;
    }

    public void setTargetPackage(String targetPackage) {
        this.targetPackage = targetPackage;
    }

    public String getTargetProcess() {
        return targetProcess;
    }

    public void setTargetProcess(String targetProcess) {
        this.targetProcess = targetProcess;
    }

    public int getTargetPid() {
        return targetPid;
    }

    public void setTargetPid(int targetPid) {
        this.targetPid = targetPid;
    }

    public String getExtraFridaArgs() {
        return extraFridaArgs;
    }

    public void setExtraFridaArgs(String extraFridaArgs) {
        this.extraFridaArgs = extraFridaArgs;
    }

    public String getFridaPath() {
        return fridaPath;
    }

    public void setFridaPath(String fridaPath) {
        this.fridaPath = fridaPath;
    }

    public String getFridaPsPath() {
        return fridaPsPath;
    }

    public void setFridaPsPath(String fridaPsPath) {
        this.fridaPsPath = fridaPsPath;
    }

    public String getAdbPath() {
        return adbPath;
    }

    public void setAdbPath(String adbPath) {
        this.adbPath = adbPath;
    }

    public boolean isCompatibleForReuse(FridaSessionConfig other) {
        if (other == null) {
            return false;
        }
        if (deviceMode != other.deviceMode) {
            return false;
        }
        if (!eq(deviceId, other.deviceId)) {
            return false;
        }
        if (deviceMode == DeviceMode.REMOTE) {
            if (!eq(remoteHost, other.remoteHost) || remotePort != other.remotePort) {
                return false;
            }
        }
        if (targetPid > 0 || other.targetPid > 0) {
            return targetPid > 0 && other.targetPid == targetPid;
        }
        if (!isEmpty(targetProcess) || !isEmpty(other.targetProcess)) {
            return !isEmpty(targetProcess) && eq(targetProcess, other.targetProcess);
        }
        return eq(targetPackage, other.targetPackage);
    }

    private static boolean eq(String a, String b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
