package com.jarida.jadxfrida.ui;

import com.jarida.jadxfrida.frida.FridaController;
import com.jarida.jadxfrida.frida.FridaServerChecker;
import com.jarida.jadxfrida.model.AdbDevice;
import com.jarida.jadxfrida.model.DeviceMode;
import com.jarida.jadxfrida.model.FridaProcessInfo;
import com.jarida.jadxfrida.model.FridaServerStatus;
import com.jarida.jadxfrida.model.FridaSessionConfig;
import com.jarida.jadxfrida.util.AdbUtil;
import com.jarida.jadxfrida.util.ProcessResult;
import com.jarida.jadxfrida.util.ProcessUtils;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.ButtonGroup;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

public class JaridaConnectionPanel extends JPanel {
    private final JComboBox<DeviceMode> deviceMode;
    private final JComboBox<AdbDevice> deviceList;
    private final JTextField remoteHost;
    private final JTextField remotePort;
    private final JTextField targetPackage;
    private final JComboBox<FridaProcessInfo> processList;
    private final JTextField pidField;
    private final JRadioButton spawnRadio;
    private final JRadioButton attachRadio;
    private final JTextField extraArgs;
    private final JTextField adbPath;
    private final JTextField fridaPath;
    private final JTextField fridaPsPath;
    private final JTextArea statusArea;
    private final JButton stopButton;
    private final JLabel statusLabel;
    private final JButton startButton;
    private final JButton savePathsButton;

    private final FridaController fridaController;
    private final Consumer<FridaSessionConfig> onApply;
    private final Consumer<FridaSessionConfig> onStart;
    private final Runnable onStop;
    private final Consumer<FridaSessionConfig> onSavePaths;

    public JaridaConnectionPanel(FridaController fridaController,
                                 FridaSessionConfig initialConfig,
                                 String defaultPackage,
                                 Consumer<FridaSessionConfig> onApply,
                                 Consumer<FridaSessionConfig> onStart,
                                 Runnable onStop,
                                 Consumer<FridaSessionConfig> onSavePaths) {
        super(new BorderLayout());
        this.fridaController = fridaController;
        this.onApply = onApply;
        this.onStart = onStart;
        this.onStop = onStop;
        this.onSavePaths = onSavePaths;

        deviceMode = new JComboBox<>(DeviceMode.values());
        deviceList = new JComboBox<>();
        remoteHost = new JTextField("127.0.0.1", 12);
        remotePort = new JTextField("27042", 6);
        targetPackage = new JTextField(defaultPackage == null ? "" : defaultPackage, 30);
        processList = new JComboBox<>();
        pidField = new JTextField("", 8);
        spawnRadio = new JRadioButton("Spawn (start app)", true);
        attachRadio = new JRadioButton("Attach (existing process)");
        ButtonGroup group = new ButtonGroup();
        group.add(spawnRadio);
        group.add(attachRadio);
        extraArgs = new JTextField("", 30);
        adbPath = new JTextField("adb", 30);
        fridaPath = new JTextField("frida", 30);
        fridaPsPath = new JTextField("frida-ps", 30);

        statusArea = new JTextArea(4, 60);
        statusArea.setEditable(false);
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);

        stopButton = new JButton("Close Connection");
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> {
            if (this.onStop != null) {
                this.onStop.run();
            }
        });
        statusLabel = new JLabel("Status: Disconnected");
        startButton = new JButton("Open Connection");
        startButton.addActionListener(e -> startTracing());
        savePathsButton = new JButton("Save Paths");
        savePathsButton.addActionListener(e -> savePaths());

        applyInitial(initialConfig, defaultPackage);

        deviceMode.addActionListener(e -> updateDeviceMode());
        spawnRadio.addActionListener(e -> updateAttachMode());
        attachRadio.addActionListener(e -> updateAttachMode());
        processList.addActionListener(e -> {
            FridaProcessInfo info = (FridaProcessInfo) processList.getSelectedItem();
            if (info != null && attachRadio.isSelected() && info.getPid() > 0) {
                pidField.setText(String.valueOf(info.getPid()));
            }
        });

        add(buildForm(), BorderLayout.CENTER);
        add(new JScrollPane(statusArea), BorderLayout.SOUTH);

        updateDeviceMode();
        updateAttachMode();
        SwingUtilities.invokeLater(this::refreshDevicesAsync);
    }

    public void applyInitial(FridaSessionConfig config, String defaultPackage) {
        if (config != null) {
            deviceMode.setSelectedItem(config.getDeviceMode());
            remoteHost.setText(config.getRemoteHost());
            remotePort.setText(String.valueOf(config.getRemotePort()));
            if (config.getTargetPackage() != null && !config.getTargetPackage().trim().isEmpty()) {
                targetPackage.setText(config.getTargetPackage());
            } else if (defaultPackage != null && !defaultPackage.trim().isEmpty()) {
                targetPackage.setText(defaultPackage);
            }
            spawnRadio.setSelected(config.isSpawn());
            attachRadio.setSelected(!config.isSpawn());
            if (config.getTargetPid() > 0) {
                pidField.setText(String.valueOf(config.getTargetPid()));
            }
            extraArgs.setText(config.getExtraFridaArgs());
            adbPath.setText(config.getAdbPath());
            fridaPath.setText(config.getFridaPath());
            fridaPsPath.setText(config.getFridaPsPath());
        } else if (defaultPackage != null && !defaultPackage.trim().isEmpty()) {
            targetPackage.setText(defaultPackage);
        }
    }

    private JPanel buildForm() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;

        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Connection:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridwidth = 3;
        panel.add(statusLabel, c);
        c.gridwidth = 1;

        c.gridx = 0;
        c.gridy++;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Device mode:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridwidth = 3;
        panel.add(deviceMode, c);
        c.gridwidth = 1;

        c.gridx = 0;
        c.gridy++;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("USB device:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(deviceList, c);
        JButton refreshDevices = new JButton("Refresh devices");
        refreshDevices.addActionListener(e -> refreshDevicesAsync());
        c.gridx = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        panel.add(refreshDevices, c);

        c.gridx = 0;
        c.gridy++;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Remote host:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(remoteHost, c);
        c.gridx = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Port:"), c);
        c.gridx = 3;
        c.weightx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(remotePort, c);

        c.gridx = 0;
        c.gridy++;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Target package:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(targetPackage, c);
        c.gridwidth = 1;

        c.gridx = 0;
        c.gridy++;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Attach/Spawn:"), c);
        c.gridx = 1;
        panel.add(spawnRadio, c);
        c.gridx = 2;
        panel.add(attachRadio, c);

        c.gridx = 0;
        c.gridy++;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Process (attach):"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(processList, c);
        JButton refreshProc = new JButton("Refresh processes");
        refreshProc.addActionListener(e -> refreshProcessesAsync());
        c.gridx = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        panel.add(refreshProc, c);

        c.gridx = 0;
        c.gridy++;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("PID (attach):"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(pidField, c);
        JButton detectPid = new JButton("Find PID (ADB)");
        detectPid.addActionListener(e -> detectPidFromAdb());
        c.gridx = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        panel.add(detectPid, c);

        c.gridx = 0;
        c.gridy++;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Extra frida args:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(extraArgs, c);
        c.gridwidth = 1;

        c.gridx = 0;
        c.gridy++;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("ADB path:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(adbPath, c);
        c.gridwidth = 1;

        c.gridx = 0;
        c.gridy++;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Frida path:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(fridaPath, c);
        c.gridwidth = 1;

        c.gridx = 0;
        c.gridy++;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Frida-ps path:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(fridaPsPath, c);
        c.gridwidth = 1;

        JButton checkBtn = new JButton("Check connectivity");
        checkBtn.addActionListener(e -> checkConnectivity());
        c.gridx = 0;
        c.gridy++;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        panel.add(checkBtn, c);

        c.gridx = 1;
        c.weightx = 0;
        panel.add(startButton, c);
        c.gridx = 2;
        panel.add(stopButton, c);
        c.gridx = 3;
        panel.add(savePathsButton, c);

        return panel;
    }

    private void updateDeviceMode() {
        boolean usb = deviceMode.getSelectedItem() == DeviceMode.USB;
        deviceList.setEnabled(usb);
        remoteHost.setEnabled(!usb);
        remotePort.setEnabled(!usb);
    }

    private void updateAttachMode() {
        boolean attach = attachRadio.isSelected();
        processList.setEnabled(attach);
        pidField.setEnabled(attach);
    }

    public void setSessionActive(boolean active) {
        SwingUtilities.invokeLater(() -> {
            stopButton.setEnabled(active);
            startButton.setEnabled(!active);
            if (active) {
                statusLabel.setText("Status: Connected");
                statusLabel.setForeground(new java.awt.Color(80, 190, 110));
            } else {
                statusLabel.setText("Status: Disconnected");
                statusLabel.setForeground(new java.awt.Color(220, 120, 120));
            }
        });
    }

    private void applyConnection() {
        FridaSessionConfig cfg = buildConfig();
        if (cfg == null) {
            return;
        }
        if (onApply != null) {
            onApply.accept(cfg);
        }
        statusArea.setText("Connection settings saved.");
    }

    private void startTracing() {
        FridaSessionConfig cfg = buildConfig();
        if (cfg == null) {
            return;
        }
        if (onApply != null) {
            onApply.accept(cfg);
        }
        if (onStart != null) {
            onStart.accept(cfg);
        } else {
            statusArea.setText("Connection settings saved.");
        }
    }

    private void savePaths() {
        FridaSessionConfig cfg = new FridaSessionConfig();
        cfg.setAdbPath(adbPath.getText().trim());
        cfg.setFridaPath(fridaPath.getText().trim());
        cfg.setFridaPsPath(fridaPsPath.getText().trim());
        if (onSavePaths != null) {
            onSavePaths.accept(cfg);
        }
        statusArea.setText("Path configuration saved.");
    }

    private FridaSessionConfig buildConfig() {
        FridaSessionConfig cfg = new FridaSessionConfig();
        cfg.setDeviceMode((DeviceMode) deviceMode.getSelectedItem());
        AdbDevice dev = (AdbDevice) deviceList.getSelectedItem();
        if (dev != null && isValidDeviceId(dev.getId())) {
            cfg.setDeviceId(dev.getId());
        }
        cfg.setRemoteHost(remoteHost.getText().trim());
        try {
            cfg.setRemotePort(Integer.parseInt(remotePort.getText().trim()));
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid remote port", "Invalid", JOptionPane.ERROR_MESSAGE);
            return null;
        }
        cfg.setSpawn(spawnRadio.isSelected());
        cfg.setTargetPackage(targetPackage.getText().trim());
        FridaProcessInfo proc = (FridaProcessInfo) processList.getSelectedItem();
        if (attachRadio.isSelected()) {
            String pidText = pidField.getText().trim();
            if (!pidText.isEmpty()) {
                try {
                    cfg.setTargetPid(Integer.parseInt(pidText));
                    cfg.setTargetProcess("");
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(this, "Invalid PID value", "Invalid", JOptionPane.ERROR_MESSAGE);
                    return null;
                }
            } else if (proc != null && proc.getPid() > 0) {
                cfg.setTargetPid(proc.getPid());
                cfg.setTargetProcess(proc.getName());
            } else {
                cfg.setTargetPid(-1);
                cfg.setTargetProcess("");
            }
        } else {
            cfg.setTargetPid(-1);
            cfg.setTargetProcess("");
        }
        cfg.setExtraFridaArgs(extraArgs.getText().trim());
        cfg.setAdbPath(adbPath.getText().trim());
        cfg.setFridaPath(fridaPath.getText().trim());
        cfg.setFridaPsPath(fridaPsPath.getText().trim());
        if (cfg.getTargetPackage().isEmpty() && cfg.isSpawn()) {
            JOptionPane.showMessageDialog(this, "Target package is required for spawn", "Missing data", JOptionPane.ERROR_MESSAGE);
            return null;
        }
        if (attachRadio.isSelected()) {
            boolean hasPid = cfg.getTargetPid() > 0;
            boolean hasProcess = proc != null && proc.getPid() > 0;
            boolean hasPackage = cfg.getTargetPackage() != null && !cfg.getTargetPackage().isEmpty();
            if (!hasPid && !hasProcess && !hasPackage) {
                JOptionPane.showMessageDialog(this, "Provide a PID, select a process, or enter a package name for attach.", "Missing data", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        }
        if (cfg.getDeviceMode() == DeviceMode.USB && !isValidDeviceId(cfg.getDeviceId())) {
            JOptionPane.showMessageDialog(this, "Select a valid USB device.", "Missing data", JOptionPane.ERROR_MESSAGE);
            return null;
        }
        return cfg;
    }

    private void refreshDevicesAsync() {
        deviceList.removeAllItems();
        deviceList.addItem(new AdbDevice("loading", "loading", "loading..."));
        new Thread(() -> {
            List<AdbDevice> devices = AdbUtil.listDevices(adbPath.getText().trim());
            SwingUtilities.invokeLater(() -> {
                deviceList.removeAllItems();
                for (AdbDevice device : devices) {
                    deviceList.addItem(device);
                }
                if (devices.isEmpty()) {
                    deviceList.addItem(new AdbDevice("none", "none", "No devices"));
                }
                if (attachRadio.isSelected()) {
                    refreshProcessesAsync();
                }
            });
        }, "jarida-devices").start();
    }

    private void refreshProcessesAsync() {
        processList.removeAllItems();
        processList.addItem(new FridaProcessInfo(-1, "loading..."));
        new Thread(() -> {
            FridaSessionConfig cfg = new FridaSessionConfig();
            cfg.setDeviceMode((DeviceMode) deviceMode.getSelectedItem());
            AdbDevice dev = (AdbDevice) deviceList.getSelectedItem();
            if (dev != null) {
                cfg.setDeviceId(dev.getId());
            }
            cfg.setRemoteHost(remoteHost.getText().trim());
            try {
                cfg.setRemotePort(Integer.parseInt(remotePort.getText().trim()));
            } catch (NumberFormatException ignored) {
            }
            cfg.setFridaPsPath(fridaPsPath.getText().trim());
            List<FridaProcessInfo> processes = fridaController.listProcesses(cfg);
            if (cfg.getDeviceMode() == DeviceMode.USB && dev != null) {
                String pkg = targetPackage.getText().trim();
                if (!pkg.isEmpty()) {
                    List<FridaProcessInfo> adbProcs = AdbUtil.findProcessesByPackage(adbPath.getText().trim(), dev.getId(), pkg);
                    for (FridaProcessInfo info : adbProcs) {
                        boolean exists = false;
                        for (FridaProcessInfo p : processes) {
                            if (p.getPid() == info.getPid()) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            processes.add(info);
                        }
                    }
                    if (pidField.getText().trim().isEmpty() && !adbProcs.isEmpty()) {
                        pidField.setText(String.valueOf(adbProcs.get(0).getPid()));
                    }
                }
            }
            SwingUtilities.invokeLater(() -> {
                processList.removeAllItems();
                for (FridaProcessInfo info : processes) {
                    processList.addItem(info);
                }
                if (processes.isEmpty()) {
                    processList.addItem(new FridaProcessInfo(-1, "No processes"));
                }
                String pkg = targetPackage.getText().trim();
                if (!pkg.isEmpty()) {
                    for (int i = 0; i < processList.getItemCount(); i++) {
                        FridaProcessInfo info = processList.getItemAt(i);
                        if (info != null && info.getName().contains(pkg)) {
                            processList.setSelectedIndex(i);
                            break;
                        }
                    }
                }
            });
        }, "jarida-processes").start();
    }

    private void detectPidFromAdb() {
        statusArea.setText("");
        new Thread(() -> {
            String pkg = targetPackage.getText().trim();
            if (pkg.isEmpty()) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Target package is empty.", "Missing data", JOptionPane.WARNING_MESSAGE));
                return;
            }
            DeviceMode mode = (DeviceMode) deviceMode.getSelectedItem();
            if (mode != DeviceMode.USB) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "ADB PID detection is only available in USB mode.", "Not available", JOptionPane.WARNING_MESSAGE));
                return;
            }
            AdbDevice dev = (AdbDevice) deviceList.getSelectedItem();
            if (dev == null) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "No USB device selected.", "Missing data", JOptionPane.WARNING_MESSAGE));
                return;
            }
            List<FridaProcessInfo> procs = AdbUtil.findProcessesByPackage(adbPath.getText().trim(), dev.getId(), pkg);
            if (procs.isEmpty()) {
                SwingUtilities.invokeLater(() -> statusArea.setText("ADB: no running process found for " + pkg));
                return;
            }
            FridaProcessInfo first = procs.get(0);
            SwingUtilities.invokeLater(() -> {
                pidField.setText(String.valueOf(first.getPid()));
                statusArea.setText("ADB: found PID " + first.getPid() + " (" + first.getName() + ")");
            });
        }, "jarida-adb-pid").start();
    }

    private void checkConnectivity() {
        statusArea.setText("");
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            String fridaExe = fridaPath.getText().trim();
            if (fridaExe.isEmpty()) {
                fridaExe = "frida";
            }
            if (looksLikePath(fridaExe) && !isExecutablePath(fridaExe)) {
                sb.append("frida path not found: ").append(fridaExe).append("\n");
            } else {
                try {
                    ProcessResult fridaRes = ProcessUtils.run(java.util.Arrays.asList(fridaExe, "--version"), 5000);
                    if (fridaRes.getExitCode() == 0) {
                        String version = fridaRes.getStdout().trim();
                        if (version.isEmpty()) {
                            version = fridaRes.getStderr().trim();
                        }
                        if (version.isEmpty()) {
                            sb.append("frida version: (unknown)\n");
                        } else {
                            sb.append("frida version: ").append(version).append("\n");
                        }
                    } else if (fridaRes.getExitCode() == -1) {
                        sb.append("frida check timed out after 5000 ms\n");
                    } else {
                        String err = fridaRes.getStderr().trim();
                        if (err.isEmpty()) {
                            err = fridaRes.getStdout().trim();
                        }
                        sb.append("frida error: ").append(err.isEmpty() ? "unknown error" : err).append("\n");
                    }
                } catch (Exception e) {
                    sb.append("frida error: ").append(e.getMessage()).append("\n");
                }
            }
            try {
                String fridaPs = fridaPsPath.getText().trim();
                if (fridaPs.isEmpty()) {
                    fridaPs = "frida-ps";
                }
                if (looksLikePath(fridaPs) && !isExecutablePath(fridaPs)) {
                    sb.append("frida-ps path not found: ").append(fridaPs).append("\n");
                } else {
                    ProcessResult psRes = ProcessUtils.run(java.util.Arrays.asList(fridaPs, "--version"), 7000);
                    if (psRes.getExitCode() == 0) {
                        sb.append("frida-ps version: ").append(psRes.getStdout().trim()).append("\n");
                    } else {
                        sb.append("frida-ps check failed: ").append(psRes.getStderr().trim()).append("\n");
                    }
                }
            } catch (Exception e) {
                sb.append("frida-ps not found: ").append(e.getMessage()).append("\n");
            }
            DeviceMode mode = (DeviceMode) deviceMode.getSelectedItem();
            if (mode == DeviceMode.USB) {
                AdbDevice dev = (AdbDevice) deviceList.getSelectedItem();
                if (dev == null) {
                    sb.append("No USB device selected.\n");
                } else {
                    FridaServerStatus status = FridaServerChecker.check(adbPath.getText().trim(), dev.getId());
                    sb.append("frida-server present: ").append(status.isPresent()).append("\n");
                    sb.append("frida-server running: ").append(status.isRunning()).append("\n");
                    if (!status.getDetails().isEmpty()) {
                        sb.append(status.getDetails()).append("\n");
                    }
                }
            } else {
                sb.append("Remote mode: ensure frida-server is reachable at ")
                        .append(remoteHost.getText().trim()).append(":").append(remotePort.getText().trim()).append("\n");
            }
            SwingUtilities.invokeLater(() -> statusArea.setText(sb.toString()));
        }, "jarida-check").start();
    }

    private boolean looksLikePath(String value) {
        if (value == null) {
            return false;
        }
        return value.contains("/") || value.contains("\\") || value.toLowerCase().endsWith(".exe");
    }

    private boolean isExecutablePath(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        File file = new File(trimmed);
        if (file.exists()) {
            return true;
        }
        if (!trimmed.toLowerCase().endsWith(".exe")) {
            File exe = new File(trimmed + ".exe");
            return exe.exists();
        }
        return false;
    }

    private static boolean isValidDeviceId(String deviceId) {
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
}
