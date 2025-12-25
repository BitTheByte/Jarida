package com.jarida.jadxfrida.ui;

import com.jarida.jadxfrida.frida.FridaController;
import com.jarida.jadxfrida.frida.FridaServerChecker;
import com.jarida.jadxfrida.model.AdbDevice;
import com.jarida.jadxfrida.model.DeviceMode;
import com.jarida.jadxfrida.model.FridaProcessInfo;
import com.jarida.jadxfrida.model.FridaServerStatus;
import com.jarida.jadxfrida.model.FridaSessionConfig;
import com.jarida.jadxfrida.model.MethodTarget;
import com.jarida.jadxfrida.model.ReturnPatchRule;
import com.jarida.jadxfrida.model.ScriptTemplate;
import com.jarida.jadxfrida.model.ScriptTemplateStore;
import com.jarida.jadxfrida.model.ScriptOptions;
import com.jarida.jadxfrida.model.TemplatePosition;
import com.jarida.jadxfrida.util.AdbUtil;
import com.jarida.jadxfrida.util.ProcessResult;
import com.jarida.jadxfrida.util.ProcessUtils;
import com.jarida.jadxfrida.util.ReturnValueValidator;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.ButtonGroup;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.util.List;

public class FridaConfigDialog extends JDialog {
    private boolean confirmed = false;

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

    private final JCheckBox logArgs;
    private final JCheckBox logReturn;
    private final JCheckBox logThread;
    private final JCheckBox printStack;
    private final JCheckBox printThis;
    private final JCheckBox prettyPrint;

    private final ReturnValueRulePanel returnPanel;
    private final JTextArea statusArea;

    private final JComboBox<ScriptTemplate> templateList;
    private final JTextArea templateArea;
    private final JCheckBox templateAppend;
    private final JComboBox<TemplatePosition> templatePosition;
    private final JTabbedPane tabs;
    private final boolean showReturnTab;
    private final FridaSessionConfig fixedSessionConfig;
    private final FridaSessionConfig baseSessionConfig;

    private final FridaController fridaController;
    private final MethodTarget target;

    private FridaSessionConfig sessionConfig;
    private ScriptOptions scriptOptions;
    private ReturnPatchRule returnPatchRule;

    public FridaConfigDialog(JFrame owner, FridaController fridaController, MethodTarget target,
                             String defaultPackage, boolean patchDefault,
                             FridaSessionConfig initialConfig, ScriptOptions initialOptions,
                             ReturnPatchRule initialReturnPatch,
                             String templateName, String templateContent, boolean templateAppendDefault,
                             TemplatePosition templatePositionDefault,
                             FridaSessionConfig fixedSessionConfig, boolean allowConnectionEdit,
                             boolean showReturnTab, boolean focusReturnTab) {
        super(owner, "Jarida Configuration", true);
        this.fridaController = fridaController;
        this.target = target;
        this.fixedSessionConfig = fixedSessionConfig;
        this.baseSessionConfig = cloneConfig(initialConfig);
        this.showReturnTab = showReturnTab;

        deviceMode = new JComboBox<>(DeviceMode.values());
        deviceList = new JComboBox<>();
        remoteHost = new JTextField("127.0.0.1", 12);
        remotePort = new JTextField("27042", 6);
        targetPackage = new JTextField(defaultPackage, 30);
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
        logArgs = new JCheckBox("Log args", true);
        logReturn = new JCheckBox("Log return", true);
        logThread = new JCheckBox("Log thread name", true);
        printStack = new JCheckBox("Print stack", false);
        printThis = new JCheckBox("Print this", false);
        prettyPrint = new JCheckBox("Pretty print", true);

        returnPanel = new ReturnValueRulePanel();
        returnPanel.setEnabledDefault(patchDefault);

        statusArea = new JTextArea(4, 60);
        statusArea.setEditable(false);
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);

        templateList = new JComboBox<>();
        for (ScriptTemplate template : ScriptTemplateStore.defaults()) {
            templateList.addItem(template);
        }
        templateArea = new JTextArea(8, 60);
        templateAppend = new JCheckBox("Enable template for hook");
        templatePosition = new JComboBox<>(TemplatePosition.values());

        applyInitial(initialConfig, initialOptions);
        applyTemplateInitial(templateName, templateContent, templateAppendDefault, templatePositionDefault);
        if (showReturnTab && initialReturnPatch != null) {
            returnPanel.setRule(initialReturnPatch);
        }

        deviceMode.addActionListener(e -> updateDeviceMode());
        spawnRadio.addActionListener(e -> updateAttachMode());
        attachRadio.addActionListener(e -> updateAttachMode());
        processList.addActionListener(e -> {
            FridaProcessInfo info = (FridaProcessInfo) processList.getSelectedItem();
            if (info != null && attachRadio.isSelected()) {
                pidField.setText(String.valueOf(info.getPid()));
            }
        });

        tabs = new JTabbedPane();
        // Connection is configured from the Jarida Console tab, not this dialog.
        tabs.addTab("Script Options", buildScriptPanel());
        if (showReturnTab) {
            tabs.addTab("Return Patch", returnPanel);
        }
        tabs.addTab("Templates", buildTemplatePanel());

        JPanel footer = new JPanel(new BorderLayout());
        footer.add(new JScrollPane(statusArea), BorderLayout.CENTER);
        footer.add(buildButtons(), BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(tabs, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(760, 500));
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int w = Math.min(960, screen.width - 160);
        int h = Math.min(620, screen.height - 160);
        setSize(new Dimension(w, h));
        setLocationRelativeTo(owner);
        if (showReturnTab && focusReturnTab) {
            tabs.setSelectedComponent(returnPanel);
        }
        updateAttachMode();
    }

    private void applyInitial(FridaSessionConfig config, ScriptOptions options) {
        if (config != null) {
            deviceMode.setSelectedItem(config.getDeviceMode());
            remoteHost.setText(config.getRemoteHost());
            remotePort.setText(String.valueOf(config.getRemotePort()));
            if (config.getTargetPackage() != null && !config.getTargetPackage().trim().isEmpty()) {
                targetPackage.setText(config.getTargetPackage());
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
        }
        if (options != null) {
            logArgs.setSelected(options.isLogArgs());
            logReturn.setSelected(options.isLogReturn());
            logThread.setSelected(options.isLogThread());
            printStack.setSelected(options.isPrintStack());
            printThis.setSelected(options.isPrintThis());
            prettyPrint.setSelected(options.isPrettyPrint());
        }
    }

    private void applyTemplateInitial(String name, String content, boolean append, TemplatePosition position) {
        templateAppend.setSelected(append);
        if (content != null) {
            templateArea.setText(content);
        }
        if (position != null) {
            templatePosition.setSelectedItem(position);
        }
        if (name != null) {
            for (int i = 0; i < templateList.getItemCount(); i++) {
                ScriptTemplate t = templateList.getItemAt(i);
                if (t.getName().equalsIgnoreCase(name)) {
                    templateList.setSelectedIndex(i);
                    return;
                }
            }
        }
    }

    private JPanel buildConnectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;

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
        refreshDevices.addActionListener(e -> refreshDevices());
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
        c.gridwidth = 3;
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
        refreshProc.addActionListener(e -> refreshProcesses());
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
        c.gridwidth = 3;
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
        c.gridwidth = 3;
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
        c.gridwidth = 3;
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
        c.gridwidth = 3;
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

        return panel;
    }

    private JPanel buildScriptPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(logArgs);
        panel.add(logReturn);
        panel.add(logThread);
        panel.add(printStack);
        panel.add(printThis);
        panel.add(prettyPrint);
        return panel;
    }

    private JPanel buildTemplatePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Template:"));
        top.add(templateList);
        JButton load = new JButton("Load");
        load.addActionListener(e -> loadTemplate());
        top.add(load);
        top.add(templateAppend);
        top.add(new JLabel("Placement:"));
        top.add(templatePosition);

        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(templateArea), BorderLayout.CENTER);
        return panel;
    }


    private void loadTemplate() {
        ScriptTemplate template = (ScriptTemplate) templateList.getSelectedItem();
        if (template != null) {
            templateArea.setText(template.getContent());
        }
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

    private JPanel buildButtons() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(e -> onOk());
        cancel.addActionListener(e -> onCancel());
        panel.add(cancel);
        panel.add(ok);
        return panel;
    }

    private void onOk() {
        FridaSessionConfig cfg = cloneConfig(fixedSessionConfig != null ? fixedSessionConfig : baseSessionConfig);
        if (cfg == null) {
            cfg = new FridaSessionConfig();
        }

        ScriptOptions opt = new ScriptOptions();
        opt.setLogArgs(logArgs.isSelected());
        opt.setLogReturn(logReturn.isSelected());
        opt.setLogThread(logThread.isSelected());
        opt.setPrintStack(printStack.isSelected());
        opt.setPrintThis(printThis.isSelected());
        opt.setPrettyPrint(prettyPrint.isSelected());

        ReturnPatchRule rule = null;
        if (showReturnTab) {
            rule = returnPanel.toRule();
            String error = ReturnValueValidator.validate(target.getReturnType(), rule);
            if (error != null) {
                JOptionPane.showMessageDialog(this, error, "Invalid return patch", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } else {
            rule = new ReturnPatchRule();
            rule.setEnabled(false);
        }

        this.sessionConfig = cfg;
        this.scriptOptions = opt;
        this.returnPatchRule = rule;
        this.confirmed = true;
        setVisible(false);
    }

    private void onCancel() {
        confirmed = false;
        setVisible(false);
    }

    private void refreshDevices() {
        refreshDevicesAsync();
    }

    private void refreshProcesses() {
        refreshProcessesAsync();
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
        }, "frida-devices").start();
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
        }, "frida-processes").start();
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
        }, "frida-adb-pid").start();
    }

    private void checkConnectivity() {
        statusArea.setText("");
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            String fridaVersion = fridaController.getFridaVersion(fridaPath.getText().trim());
            if (fridaVersion.isEmpty()) {
                sb.append("frida CLI not found.\n");
            } else {
                sb.append("frida version: ").append(fridaVersion).append("\n");
            }
            try {
                String fridaPs = fridaPsPath.getText().trim();
                if (fridaPs.isEmpty()) {
                    fridaPs = "frida-ps";
                }
                ProcessResult psRes = ProcessUtils.run(java.util.Arrays.asList(fridaPs, "--version"), 3000);
                if (psRes.getExitCode() == 0) {
                    sb.append("frida-ps version: ").append(psRes.getStdout().trim()).append("\n");
                } else {
                    sb.append("frida-ps check failed: ").append(psRes.getStderr().trim()).append("\n");
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
        }, "frida-check").start();
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public FridaSessionConfig getSessionConfig() {
        return sessionConfig;
    }

    public ScriptOptions getScriptOptions() {
        return scriptOptions;
    }

    public ReturnPatchRule getReturnPatchRule() {
        return returnPatchRule;
    }

    public String getExtraScript() {
        if (templateAppend.isSelected()) {
            return templateArea.getText();
        }
        return "";
    }


    public boolean isTemplateAppend() {
        return templateAppend.isSelected();
    }

    public TemplatePosition getTemplatePosition() {
        TemplatePosition pos = (TemplatePosition) templatePosition.getSelectedItem();
        return pos == null ? TemplatePosition.APPEND : pos;
    }

    public String getTemplateName() {
        ScriptTemplate template = (ScriptTemplate) templateList.getSelectedItem();
        return template == null ? "" : template.getName();
    }

    public String getTemplateContent() {
        return templateArea.getText();
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

    private static FridaSessionConfig cloneConfig(FridaSessionConfig src) {
        FridaSessionConfig cfg = new FridaSessionConfig();
        cfg.setDeviceMode(src.getDeviceMode());
        cfg.setDeviceId(src.getDeviceId());
        cfg.setRemoteHost(src.getRemoteHost());
        cfg.setRemotePort(src.getRemotePort());
        cfg.setSpawn(src.isSpawn());
        cfg.setTargetPackage(src.getTargetPackage());
        cfg.setTargetProcess(src.getTargetProcess());
        cfg.setTargetPid(src.getTargetPid());
        cfg.setExtraFridaArgs(src.getExtraFridaArgs());
        cfg.setFridaPath(src.getFridaPath());
        cfg.setFridaPsPath(src.getFridaPsPath());
        cfg.setAdbPath(src.getAdbPath());
        return cfg;
    }
}
