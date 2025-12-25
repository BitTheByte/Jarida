package com.jarida.jadxfrida.frida;

import com.jarida.jadxfrida.model.FridaServerStatus;
import com.jarida.jadxfrida.util.AdbUtil;
import com.jarida.jadxfrida.util.ProcessResult;

public final class FridaServerChecker {
    private FridaServerChecker() {
    }

    public static FridaServerStatus check(String adbPath, String deviceId) {
        boolean running = false;
        boolean present = false;
        StringBuilder details = new StringBuilder();
        try {
            ProcessResult pidof = AdbUtil.shell(adbPath, deviceId, "pidof frida-server");
            if (pidof.getExitCode() == 0 && !pidof.getStdout().trim().isEmpty()) {
                running = true;
                details.append("frida-server pid: ").append(pidof.getStdout().trim()).append('\n');
            }
            if (!running) {
                ProcessResult ps = AdbUtil.shell(adbPath, deviceId, "ps | grep frida-server");
                if (ps.getExitCode() == 0 && !ps.getStdout().trim().isEmpty()) {
                    running = true;
                    details.append("frida-server process: ").append(ps.getStdout().trim()).append('\n');
                }
            }
            ProcessResult lsExact = AdbUtil.shell(adbPath, deviceId, "ls /data/local/tmp/frida-server");
            if (lsExact.getExitCode() == 0 && !lsExact.getStdout().trim().isEmpty()) {
                present = true;
            }
            if (!present) {
                ProcessResult lsAny = AdbUtil.shell(adbPath, deviceId, "ls /data/local/tmp | grep -E '^frida-server'");
                if (lsAny.getExitCode() == 0 && !lsAny.getStdout().trim().isEmpty()) {
                    present = true;
                    details.append("frida-server variants: ").append(lsAny.getStdout().trim()).append('\n');
                }
            }
            if (!present) {
                details.append("frida-server not found in /data/local/tmp (expected frida-server*).\n");
            }
        } catch (Exception e) {
            details.append("Failed to query device: ").append(e.getMessage());
        }
        return new FridaServerStatus(present, running, details.toString().trim());
    }
}
