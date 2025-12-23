package com.jarida.jadxfrida.frida;

import com.jarida.jadxfrida.model.FridaProcessInfo;
import com.jarida.jadxfrida.model.FridaSessionConfig;
import com.jarida.jadxfrida.model.HookSpec;
import com.jarida.jadxfrida.model.DeviceMode;
import com.jarida.jadxfrida.util.ProcessResult;
import com.jarida.jadxfrida.util.ProcessUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class FridaController {
    private Process process;
    private Thread stdoutThread;
    private Thread stderrThread;
    private final List<Path> tempScripts = new CopyOnWriteArrayList<>();
    private volatile Boolean noPauseSupported;
    private volatile Boolean autoReloadSupported;
    private volatile String lastFridaPath;
    private Path sessionScriptPath;
    private volatile String lastScriptContent;
    private java.util.function.Consumer<Integer> onExit;
    private final AtomicBoolean exitNotified = new AtomicBoolean(false);
    private volatile boolean sessionAutoReloadEnabled = false;

    public synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    public synchronized void stop() {
        if (process != null) {
            process.destroy();
        }
        sessionScriptPath = null;
        lastScriptContent = null;
        sessionAutoReloadEnabled = false;
        cleanupTempScripts();
    }

    public void setOnExit(java.util.function.Consumer<Integer> onExit) {
        this.onExit = onExit;
    }

    public String getFridaVersion() {
        return getFridaVersion("frida");
    }

    public String getFridaVersion(String fridaPath) {
        try {
            ProcessResult result = ProcessUtils.run(Arrays.asList(fridaPath, "--version"), 3000);
            if (result.getExitCode() == 0) {
                return result.getStdout().trim();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    public List<FridaProcessInfo> listProcesses(FridaSessionConfig config) {
        List<FridaProcessInfo> list = new ArrayList<>();
        List<String> cmd = new ArrayList<>();
        String fridaPsPath = config.getFridaPsPath();
        if (fridaPsPath == null || fridaPsPath.trim().isEmpty()) {
            fridaPsPath = "frida-ps";
        }
        if (!isExecutableAvailable(fridaPsPath)) {
            return list;
        }
        cmd.add(fridaPsPath);
        if (config.getDeviceMode() == DeviceMode.USB) {
            if (config.getDeviceId() != null && !config.getDeviceId().isEmpty()) {
                cmd.add("-D");
                cmd.add(config.getDeviceId());
            } else {
                cmd.add("-U");
            }
        } else {
            cmd.add("-H");
            cmd.add(config.getRemoteHost() + ":" + config.getRemotePort());
        }
        cmd.add("-a");
        try {
            ProcessResult res = ProcessUtils.run(cmd, 5000);
            if (res.getExitCode() != 0) {
                return list;
            }
            String[] lines = res.getStdout().split("\\R");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty() || line.startsWith("PID")) {
                    continue;
                }
                String[] parts = line.split("\\s+", 2);
                if (parts.length < 2) {
                    continue;
                }
                try {
                    int pid = Integer.parseInt(parts[0]);
                    list.add(new FridaProcessInfo(pid, parts[1].trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public synchronized void start(FridaSessionConfig config, HookSpec spec, Consumer<String> log) throws IOException {
        String script = HookScriptGenerator.generate(spec);
        startWithScript(config, script, log);
    }

    public synchronized void startWithScript(FridaSessionConfig config, String script, Consumer<String> log) throws IOException {
        stop();
        exitNotified.set(false);
        sessionScriptPath = Files.createTempFile("jarida-session-", ".js");
        Files.write(sessionScriptPath, script.getBytes(StandardCharsets.UTF_8));
        lastScriptContent = script;
        tempScripts.add(sessionScriptPath);

        String fridaPath = config.getFridaPath();
        if (fridaPath == null || fridaPath.trim().isEmpty()) {
            fridaPath = "frida";
        }
        if (!isExecutableAvailable(fridaPath)) {
            throw new IOException("Frida executable not found: " + fridaPath);
        }
        boolean useNoPause = config.isSpawn() && isNoPauseSupported(fridaPath, log);
        boolean useAutoReload = isAutoReloadSupported(fridaPath, log);
        List<String> command = buildCommand(config, sessionScriptPath.toFile(), useNoPause, useAutoReload);
        sessionAutoReloadEnabled = command.contains("--auto-reload");
        log.accept("Starting Jarida (frida): " + String.join(" ", command));

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(false);
        process = builder.start();
        final Process started = process;
        stdoutThread = startReader(process.getInputStream(), log::accept, false);
        stderrThread = startReader(process.getErrorStream(), log::accept, true);

        if (config.isSpawn() && !useNoPause) {
            scheduleResume(process, log);
        }
        Thread waiter = new Thread(() -> {
            try {
                int code = started.waitFor();
                notifyExit(code);
            } catch (InterruptedException ignored) {
            }
        }, "frida-exit");
        waiter.setDaemon(true);
        waiter.start();
    }

    public synchronized void updateSessionScript(String script, Consumer<String> log) throws IOException {
        if (sessionScriptPath == null) {
            throw new IOException("No session script to update");
        }
        if (script != null && script.equals(lastScriptContent)) {
            return;
        }
        Files.write(sessionScriptPath, script.getBytes(StandardCharsets.UTF_8));
        lastScriptContent = script;
        // Do not warn here; some Frida builds reload scripts without requiring a flag.
    }

    private Thread startReader(java.io.InputStream stream, Consumer<String> out, boolean stderr) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (stderr && shouldSuppressStderr(line)) {
                        if (line.startsWith("Fatal Python error")) {
                            emit(out, true, "Frida exited (Python runtime shutdown).");
                            handleSessionTerminated(-1);
                        }
                        continue;
                    }
                    boolean terminated = shouldNotifyExit(line);
                    String marker = "]-> ";
                    int idx = line.indexOf(marker);
                    if (idx >= 0 && idx + marker.length() < line.length()) {
                        String after = line.substring(idx + marker.length());
                        if (after.startsWith("[JARIDA]")) {
                            emit(out, stderr, line.substring(0, idx + marker.length() - 1));
                            emit(out, stderr, after);
                            if (terminated) {
                                handleSessionTerminated(0);
                            }
                            continue;
                        }
                    }
                    emit(out, stderr, line);
                    if (terminated) {
                        handleSessionTerminated(0);
                    }
                }
            } catch (IOException ignored) {
            }
        }, "frida-reader");
        t.setDaemon(true);
        t.start();
        return t;
    }

    private void emit(Consumer<String> out, boolean stderr, String line) {
        if (out == null || line == null) {
            return;
        }
        if (stderr) {
            if (line.startsWith("[stderr]")) {
                out.accept(line);
            } else {
                out.accept("[stderr] " + line);
            }
        } else {
            out.accept(line);
        }
    }

    private boolean shouldNotifyExit(String line) {
        if (line == null) {
            return false;
        }
        String trimmed = line.trim().toLowerCase();
        return trimmed.contains("process terminated")
                || trimmed.contains("process crashed")
                || trimmed.contains("thank you for using frida");
    }

    private boolean shouldSuppressStderr(String line) {
        if (line == null) {
            return false;
        }
        String lower = line.toLowerCase();
        return lower.contains("fatal python error")
                || lower.contains("python runtime state")
                || lower.contains("interpreter shutdown")
                || lower.contains("_enter_buffered_busy")
                || lower.contains("current thread")
                || lower.contains("most recent call first")
                || lower.contains("<no python frame>");
    }

    private void notifyExit(int code) {
        if (!exitNotified.compareAndSet(false, true)) {
            return;
        }
        if (onExit != null) {
            onExit.accept(code);
        }
    }

    private void handleSessionTerminated(int code) {
        if (!exitNotified.compareAndSet(false, true)) {
            return;
        }
        if (onExit != null) {
            onExit.accept(code);
        }
        stop();
    }


    private List<String> buildCommand(FridaSessionConfig config, File scriptFile, boolean useNoPause, boolean useAutoReload) {
        List<String> cmd = new ArrayList<>();
        String fridaPath = config.getFridaPath();
        if (fridaPath == null || fridaPath.trim().isEmpty()) {
            fridaPath = "frida";
        }
        cmd.add(fridaPath);
        if (config.getDeviceMode() == DeviceMode.USB) {
            if (config.getDeviceId() != null && !config.getDeviceId().isEmpty()) {
                cmd.add("-D");
                cmd.add(config.getDeviceId());
            } else {
                cmd.add("-U");
            }
        } else {
            cmd.add("-H");
            cmd.add(config.getRemoteHost() + ":" + config.getRemotePort());
        }
        if (config.isSpawn()) {
            cmd.add("-f");
            cmd.add(config.getTargetPackage());
            if (useNoPause) {
                cmd.add("--no-pause");
            }
        } else {
            if (config.getTargetPid() > 0) {
                cmd.add("-p");
                cmd.add(String.valueOf(config.getTargetPid()));
            } else if (config.getTargetProcess() != null && !config.getTargetProcess().isEmpty()) {
                cmd.add("-n");
                cmd.add(config.getTargetProcess());
            } else {
                cmd.add("-n");
                cmd.add(config.getTargetPackage());
            }
        }
        cmd.add("-l");
        cmd.add(scriptFile.getAbsolutePath());
        if (useAutoReload) {
            cmd.add("--auto-reload");
        }
        if (config.getExtraFridaArgs() != null && !config.getExtraFridaArgs().trim().isEmpty()) {
            cmd.addAll(splitArgs(config.getExtraFridaArgs()));
        }
        return cmd;
    }

    private boolean isNoPauseSupported(String fridaPath, Consumer<String> log) {
        if (lastFridaPath == null || !lastFridaPath.equals(fridaPath)) {
            noPauseSupported = null;
            lastFridaPath = fridaPath;
        }
        if (noPauseSupported != null) {
            return noPauseSupported;
        }
        synchronized (this) {
            if (noPauseSupported != null) {
                return noPauseSupported;
            }
            boolean supported = false;
            try {
                ProcessResult result = ProcessUtils.run(Arrays.asList(fridaPath, "--help"), 3000);
                String output = (result.getStdout() + "\n" + result.getStderr());
                supported = output.contains("--no-pause");
            } catch (Exception e) {
                log.accept("Unable to detect frida --no-pause support: " + e.getMessage());
            }
            noPauseSupported = supported;
            return supported;
        }
    }

    private boolean isAutoReloadSupported(String fridaPath, Consumer<String> log) {
        if (lastFridaPath == null || !lastFridaPath.equals(fridaPath)) {
            autoReloadSupported = null;
            lastFridaPath = fridaPath;
        }
        if (autoReloadSupported != null) {
            return autoReloadSupported;
        }
        synchronized (this) {
            if (autoReloadSupported != null) {
                return autoReloadSupported;
            }
            boolean supported = false;
            try {
                ProcessResult result = ProcessUtils.run(Arrays.asList(fridaPath, "--help"), 3000);
                String output = (result.getStdout() + "\n" + result.getStderr());
                supported = output.contains("--auto-reload");
            } catch (Exception e) {
                log.accept("Unable to detect frida --auto-reload support: " + e.getMessage());
            }
            if (!supported) {
                String version = getFridaVersion(fridaPath);
                int major = parseMajorVersion(version);
                if (major >= 16) {
                    supported = true;
                }
            }
            autoReloadSupported = supported;
            return supported;
        }
    }

    private int parseMajorVersion(String version) {
        if (version == null || version.trim().isEmpty()) {
            return -1;
        }
        String v = version.trim();
        if (v.toLowerCase().startsWith("frida")) {
            v = v.replaceAll("[^0-9.]", "");
        }
        String[] parts = v.split("\\.");
        if (parts.length == 0) {
            return -1;
        }
        try {
            return Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void scheduleResume(Process proc, Consumer<String> log) {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(500);
                sendCommand("%resume", log);
                log.accept("Sent %resume to Frida (no --no-pause support detected).");
            } catch (Exception e) {
                log.accept("Failed to send %resume: " + e.getMessage());
            }
        }, "frida-resume");
        t.setDaemon(true);
        t.start();
    }

    private List<String> splitArgs(String extra) {
        List<String> args = new ArrayList<>();
        if (extra == null || extra.trim().isEmpty()) {
            return args;
        }
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;
        for (int i = 0; i < extra.length(); i++) {
            char c = extra.charAt(i);
            if (inQuotes) {
                if (c == quoteChar) {
                    inQuotes = false;
                } else if (c == '\\' && i + 1 < extra.length()) {
                    char next = extra.charAt(i + 1);
                    if (next == quoteChar || next == '\\') {
                        current.append(next);
                        i++;
                    } else {
                        current.append(c);
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"' || c == '\'') {
                    inQuotes = true;
                    quoteChar = c;
                } else if (Character.isWhitespace(c)) {
                    if (current.length() > 0) {
                        args.add(current.toString());
                        current.setLength(0);
                    }
                } else {
                    current.append(c);
                }
            }
        }
        if (current.length() > 0) {
            args.add(current.toString());
        }
        return args;
    }

    private boolean isNoPauseUnsupported(String line) {
        if (line == null) {
            return false;
        }
        String lower = line.toLowerCase();
        return (lower.contains("unrecognized arguments") || lower.contains("no such option") || lower.contains("unknown option"))
                && lower.contains("--no-pause");
    }

    private void cleanupTempScripts() {
        for (Path path : tempScripts) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
        }
        tempScripts.clear();
    }

    private void sendCommand(String command, Consumer<String> log) throws IOException {
        if (process == null) {
            throw new IOException("Frida process not running");
        }
        OutputStream os = process.getOutputStream();
        os.write((command + "\n").getBytes(StandardCharsets.UTF_8));
        os.flush();
        if (log != null) {
            log.accept("Frida cmd: " + command);
        }
    }


    private String formatPathForFrida(File scriptFile) {
        String path = scriptFile.getAbsolutePath();
        if (File.separatorChar == '\\') {
            path = path.replace('\\', '/');
        }
        if (path.contains(" ")) {
            return "\"" + path + "\"";
        }
        return path;
    }

    private boolean isExecutableAvailable(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        String trimmed = path.trim();
        if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.endsWith(".exe")) {
            File file = new File(trimmed);
            if (file.exists()) {
                return true;
            }
            if (!trimmed.toLowerCase().endsWith(".exe")) {
                return new File(trimmed + ".exe").exists();
            }
            return false;
        }
        return true;
    }
}
