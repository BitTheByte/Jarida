package com.jarida.jadxfrida.util;

import com.jarida.jadxfrida.model.AdbDevice;
import com.jarida.jadxfrida.model.FridaProcessInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AdbUtil {
    private static final long DEFAULT_TIMEOUT_MS = 5000;

    private AdbUtil() {
    }

    public static List<AdbDevice> listDevices(String adbPath) {
        if (adbPath == null || adbPath.trim().isEmpty()) {
            adbPath = "adb";
        }
        List<AdbDevice> result = new ArrayList<>();
        try {
            ProcessResult output = ProcessUtils.run(Arrays.asList(adbPath, "devices", "-l"), DEFAULT_TIMEOUT_MS);
            if (output.getExitCode() != 0) {
                return result;
            }
            String[] lines = output.getStdout().split("\\R");
            for (String line : lines) {
                if (line == null || line.trim().isEmpty()) {
                    continue;
                }
                if (line.startsWith("List of devices")) {
                    continue;
                }
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 2) {
                    continue;
                }
                String id = parts[0];
                String status = parts[1];
                String desc = "";
                if (parts.length > 2) {
                    desc = String.join(" ", Arrays.asList(parts).subList(2, parts.length));
                }
                result.add(new AdbDevice(id, status, desc));
            }
        } catch (IOException | InterruptedException e) {
            return result;
        }
        return result;
    }

    public static ProcessResult shell(String adbPath, String deviceId, String command) throws IOException, InterruptedException {
        if (adbPath == null || adbPath.trim().isEmpty()) {
            adbPath = "adb";
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(adbPath);
        if (deviceId != null && !deviceId.isEmpty()) {
            cmd.add("-s");
            cmd.add(deviceId);
        }
        cmd.add("shell");
        cmd.add(command);
        return ProcessUtils.run(cmd, DEFAULT_TIMEOUT_MS);
    }

    public static boolean isValidDeviceId(String deviceId) {
        if (deviceId == null) {
            return false;
        }
        String trimmed = deviceId.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        String lower = trimmed.toLowerCase();
        return !("loading".equals(lower) || "none".equals(lower));
    }

    public static List<FridaProcessInfo> findProcessesByPackage(String adbPath, String deviceId, String packageName) {
        List<FridaProcessInfo> result = new ArrayList<>();
        if (packageName == null || packageName.trim().isEmpty()) {
            return result;
        }
        String pkg = packageName.trim();
        try {
            Map<Integer, String> pidNames = parsePsTable(adbPath, deviceId);
            ProcessResult pidof = shell(adbPath, deviceId, "pidof " + pkg);
            if (pidof.getExitCode() == 0 && !pidof.getStdout().trim().isEmpty()) {
                String[] parts = pidof.getStdout().trim().split("\\s+");
                for (String part : parts) {
                    try {
                        int pid = Integer.parseInt(part.trim());
                        String name = pidNames.get(pid);
                        if (name == null || name.isEmpty()) {
                            name = pkg;
                        }
                        result.add(new FridaProcessInfo(pid, name));
                    } catch (NumberFormatException ignored) {
                    }
                }
            } else {
                ProcessResult ps = shell(adbPath, deviceId, "ps -A | grep " + pkg);
                if (ps.getExitCode() == 0 && !ps.getStdout().trim().isEmpty()) {
                    String[] lines = ps.getStdout().split("\\R");
                    for (String line : lines) {
                        FridaProcessInfo info = parsePsLine(line);
                        if (info != null) {
                            result.add(info);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private static Map<Integer, String> parsePsTable(String adbPath, String deviceId) {
        Map<Integer, String> map = new HashMap<>();
        try {
            ProcessResult ps = shell(adbPath, deviceId, "ps -A");
            if (ps.getExitCode() != 0) {
                return map;
            }
            String[] lines = ps.getStdout().split("\\R");
            for (String line : lines) {
                FridaProcessInfo info = parsePsLine(line);
                if (info != null) {
                    map.put(info.getPid(), info.getName());
                }
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    private static FridaProcessInfo parsePsLine(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("USER") || trimmed.startsWith("UID")) {
            return null;
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length < 2) {
            return null;
        }
        Integer pid = null;
        String name = parts[parts.length - 1];
        for (int i = 0; i < parts.length; i++) {
            try {
                pid = Integer.parseInt(parts[i]);
                break;
            } catch (NumberFormatException ignored) {
            }
        }
        if (pid == null) {
            return null;
        }
        return new FridaProcessInfo(pid, name);
    }
}
