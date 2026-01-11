package com.jarida.jadxfrida;

import com.jarida.jadxfrida.model.DeviceMode;
import com.jarida.jadxfrida.model.FridaSessionConfig;
import com.jarida.jadxfrida.model.ScriptOptions;
import com.jarida.jadxfrida.model.TemplatePosition;
import com.jarida.jadxfrida.model.TraceHighlightColor;
import jadx.api.plugins.options.OptionFlag;
import jadx.api.plugins.options.impl.BasePluginOptionsBuilder;
import jadx.api.plugins.options.impl.OptionBuilder;

import java.awt.Color;

public class FridaPluginOptions extends BasePluginOptionsBuilder {
    private static final String PREFIX = "jarida.";
    private static final String HIGHLIGHT_COLOR_KEY = PREFIX + "traceHighlightColor";
    private static final TraceHighlightColor DEFAULT_HIGHLIGHT_COLOR = TraceHighlightColor.GREEN;
    private static final DeviceMode DEFAULT_DEVICE_MODE = DeviceMode.USB;
    private static final String DEFAULT_DEVICE_ID = "";
    private static final String DEFAULT_REMOTE_HOST = "127.0.0.1";
    private static final int DEFAULT_REMOTE_PORT = 27042;
    private static final boolean DEFAULT_SPAWN = true;
    private static final String DEFAULT_TARGET_PACKAGE = "";
    private static final String DEFAULT_EXTRA_ARGS = "";
    private static final String DEFAULT_FRIDA_PATH = "frida";
    private static final String DEFAULT_FRIDA_PS_PATH = "frida-ps";
    private static final String DEFAULT_ADB_PATH = "adb";
    private static final boolean DEFAULT_LOG_ARGS = true;
    private static final boolean DEFAULT_LOG_RETURN = true;
    private static final boolean DEFAULT_LOG_THREAD = true;
    private static final boolean DEFAULT_PRINT_STACK = false;
    private static final boolean DEFAULT_PRINT_THIS = false;
    private static final boolean DEFAULT_PRETTY_PRINT = true;
    private static final boolean DEFAULT_TEMPLATE_APPEND = false;
    private static final String DEFAULT_TEMPLATE_NAME = "None";
    private static final String DEFAULT_TEMPLATE_CONTENT = "";
    private static final TemplatePosition DEFAULT_TEMPLATE_POSITION = TemplatePosition.APPEND;

    private DeviceMode deviceMode = DEFAULT_DEVICE_MODE;
    private String deviceId = DEFAULT_DEVICE_ID;
    private String remoteHost = DEFAULT_REMOTE_HOST;
    private int remotePort = DEFAULT_REMOTE_PORT;
    private boolean spawn = DEFAULT_SPAWN;
    private String targetPackage = DEFAULT_TARGET_PACKAGE;
    private String extraArgs = DEFAULT_EXTRA_ARGS;
    private String fridaPath = DEFAULT_FRIDA_PATH;
    private String fridaPsPath = DEFAULT_FRIDA_PS_PATH;
    private String adbPath = DEFAULT_ADB_PATH;

    private boolean logArgs = DEFAULT_LOG_ARGS;
    private boolean logReturn = DEFAULT_LOG_RETURN;
    private boolean logThread = DEFAULT_LOG_THREAD;
    private boolean printStack = DEFAULT_PRINT_STACK;
    private boolean printThis = DEFAULT_PRINT_THIS;
    private boolean prettyPrint = DEFAULT_PRETTY_PRINT;

    private boolean templateAppend = DEFAULT_TEMPLATE_APPEND;
    private String templateName = DEFAULT_TEMPLATE_NAME;
    private String templateContent = DEFAULT_TEMPLATE_CONTENT;
    private TemplatePosition templatePosition = DEFAULT_TEMPLATE_POSITION;
    private TraceHighlightColor highlightColor = DEFAULT_HIGHLIGHT_COLOR;
    private Runnable highlightColorChangeListener;

    @Override
    public void registerOptions() {
        enumOption(HIGHLIGHT_COLOR_KEY, TraceHighlightColor.values(), TraceHighlightColor::fromString)
                .description("Trace highlight color")
                .defaultValue(DEFAULT_HIGHLIGHT_COLOR)
                .setter(this::setHighlightColor);
        if (deviceMode == null) {
            deviceMode = DEFAULT_DEVICE_MODE;
        }
        hidden(enumOption(PREFIX + "deviceMode", DeviceMode.values(), DeviceMode::valueOf))
                .description("Frida device mode")
                .defaultValue(DEFAULT_DEVICE_MODE)
                .setter(v -> deviceMode = v);
        hidden(strOption(PREFIX + "deviceId"))
                .description("ADB device id")
                .defaultValue(DEFAULT_DEVICE_ID)
                .setter(v -> deviceId = v);
        hidden(strOption(PREFIX + "remoteHost"))
                .description("Remote frida-server host")
                .defaultValue(DEFAULT_REMOTE_HOST)
                .setter(v -> remoteHost = v);
        hidden(intOption(PREFIX + "remotePort"))
                .description("Remote frida-server port")
                .defaultValue(DEFAULT_REMOTE_PORT)
                .setter(v -> remotePort = v);
        hidden(boolOption(PREFIX + "spawn"))
                .description("Spawn target app")
                .defaultValue(DEFAULT_SPAWN)
                .setter(v -> spawn = v);
        hidden(strOption(PREFIX + "targetPackage"))
                .description("Target package for spawn/attach")
                .defaultValue(DEFAULT_TARGET_PACKAGE)
                .setter(v -> targetPackage = v);
        hidden(strOption(PREFIX + "extraArgs"))
                .description("Extra frida CLI arguments")
                .defaultValue(DEFAULT_EXTRA_ARGS)
                .setter(v -> extraArgs = v);
        hidden(strOption(PREFIX + "fridaPath"))
                .description("Path to frida executable")
                .defaultValue(DEFAULT_FRIDA_PATH)
                .setter(v -> fridaPath = v);
        hidden(strOption(PREFIX + "fridaPsPath"))
                .description("Path to frida-ps executable")
                .defaultValue(DEFAULT_FRIDA_PS_PATH)
                .setter(v -> fridaPsPath = v);
        hidden(strOption(PREFIX + "adbPath"))
                .description("Path to adb executable")
                .defaultValue(DEFAULT_ADB_PATH)
                .setter(v -> adbPath = v);

        hidden(boolOption(PREFIX + "logArgs"))
                .description("Log method arguments")
                .defaultValue(DEFAULT_LOG_ARGS)
                .setter(v -> logArgs = v);
        hidden(boolOption(PREFIX + "logReturn"))
                .description("Log return value")
                .defaultValue(DEFAULT_LOG_RETURN)
                .setter(v -> logReturn = v);
        hidden(boolOption(PREFIX + "logThread"))
                .description("Log thread name")
                .defaultValue(DEFAULT_LOG_THREAD)
                .setter(v -> logThread = v);
        hidden(boolOption(PREFIX + "printStack"))
                .description("Print Java stack trace")
                .defaultValue(DEFAULT_PRINT_STACK)
                .setter(v -> printStack = v);
        hidden(boolOption(PREFIX + "printThis"))
                .description("Print this object")
                .defaultValue(DEFAULT_PRINT_THIS)
                .setter(v -> printThis = v);
        hidden(boolOption(PREFIX + "prettyPrint"))
                .description("Pretty print objects")
                .defaultValue(DEFAULT_PRETTY_PRINT)
                .setter(v -> prettyPrint = v);

        hidden(boolOption(PREFIX + "templateAppend"))
                .description("Append extra script")
                .defaultValue(DEFAULT_TEMPLATE_APPEND)
                .setter(v -> templateAppend = v);
        hidden(strOption(PREFIX + "templateName"))
                .description("Selected script template")
                .defaultValue(DEFAULT_TEMPLATE_NAME)
                .setter(v -> templateName = v);
        hidden(strOption(PREFIX + "templateContent"))
                .description("Custom script content")
                .defaultValue(DEFAULT_TEMPLATE_CONTENT)
                .setter(v -> templateContent = v);
        if (templatePosition == null) {
            templatePosition = DEFAULT_TEMPLATE_POSITION;
        }
        hidden(enumOption(PREFIX + "templatePosition", TemplatePosition.values(), TemplatePosition::valueOf))
                .description("Template position inside hook")
                .defaultValue(DEFAULT_TEMPLATE_POSITION)
                .setter(v -> templatePosition = v);
    }

    public FridaSessionConfig toSessionConfig() {
        FridaSessionConfig cfg = new FridaSessionConfig();
        cfg.setDeviceMode(deviceMode != null ? deviceMode : DeviceMode.USB);
        cfg.setDeviceId(deviceId);
        cfg.setRemoteHost(remoteHost);
        cfg.setRemotePort(remotePort);
        cfg.setSpawn(spawn);
        cfg.setTargetPackage(targetPackage);
        cfg.setExtraFridaArgs(extraArgs);
        cfg.setFridaPath(fridaPath);
        cfg.setFridaPsPath(fridaPsPath);
        cfg.setAdbPath(adbPath);
        return cfg;
    }

    public ScriptOptions toScriptOptions() {
        ScriptOptions opt = new ScriptOptions();
        opt.setLogArgs(logArgs);
        opt.setLogReturn(logReturn);
        opt.setLogThread(logThread);
        opt.setPrintStack(printStack);
        opt.setPrintThis(printThis);
        opt.setPrettyPrint(prettyPrint);
        return opt;
    }

    public void updateFrom(FridaSessionConfig cfg, ScriptOptions opt,
                           boolean append, String templateName, String templateContent) {
        if (cfg != null) {
            DeviceMode dm = cfg.getDeviceMode();
            if (dm != null) {
                deviceMode = dm;
            }
            deviceId = cfg.getDeviceId();
            remoteHost = cfg.getRemoteHost();
            remotePort = cfg.getRemotePort();
            spawn = cfg.isSpawn();
            targetPackage = cfg.getTargetPackage();
            extraArgs = cfg.getExtraFridaArgs();
            fridaPath = cfg.getFridaPath();
            fridaPsPath = cfg.getFridaPsPath();
            adbPath = cfg.getAdbPath();
        }
        if (opt != null) {
            logArgs = opt.isLogArgs();
            logReturn = opt.isLogReturn();
            logThread = opt.isLogThread();
            printStack = opt.isPrintStack();
            printThis = opt.isPrintThis();
            prettyPrint = opt.isPrettyPrint();
        }
        templateAppend = append;
        if (templateName != null) {
            this.templateName = templateName;
        }
        if (templateContent != null) {
            this.templateContent = templateContent;
        }
    }


    public boolean isTemplateAppend() {
        return templateAppend;
    }

    public String getTemplateName() {
        return templateName;
    }

    public String getTemplateContent() {
        return templateContent;
    }

    public TemplatePosition getTemplatePosition() {
        return templatePosition == null ? TemplatePosition.APPEND : templatePosition;
    }

    public void setTemplatePosition(TemplatePosition position) {
        if (position != null) {
            this.templatePosition = position;
        }
    }

    public Color getHighlightColor() {
        TraceHighlightColor preset = highlightColor != null ? highlightColor : DEFAULT_HIGHLIGHT_COLOR;
        return preset.getColor();
    }

    public void setHighlightColorChangeListener(Runnable listener) {
        this.highlightColorChangeListener = listener;
    }

    public static Color defaultHighlightColor() {
        return DEFAULT_HIGHLIGHT_COLOR.getColor();
    }

    private void setHighlightColor(TraceHighlightColor value) {
        if (value == null) {
            highlightColor = DEFAULT_HIGHLIGHT_COLOR;
            notifyHighlightColorChange();
            return;
        }
        highlightColor = value;
        notifyHighlightColorChange();
    }

    private <T> OptionBuilder<T> hidden(OptionBuilder<T> builder) {
        return builder.flags(OptionFlag.HIDE_IN_GUI);
    }

    private void notifyHighlightColorChange() {
        if (highlightColorChangeListener != null) {
            highlightColorChangeListener.run();
        }
    }
}
