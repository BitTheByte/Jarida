package com.jarida.jadxfrida;

import com.jarida.jadxfrida.model.DeviceMode;
import com.jarida.jadxfrida.model.FridaSessionConfig;
import com.jarida.jadxfrida.model.ScriptOptions;
import com.jarida.jadxfrida.model.TemplatePosition;
import jadx.api.plugins.options.impl.BasePluginOptionsBuilder;

public class FridaPluginOptions extends BasePluginOptionsBuilder {
    private static final String PREFIX = "jarida.";
    private DeviceMode deviceMode = DeviceMode.USB;
    private String deviceId = "";
    private String remoteHost = "127.0.0.1";
    private int remotePort = 27042;
    private boolean spawn = true;
    private String targetPackage = "";
    private String extraArgs = "";
    private String fridaPath = "frida";
    private String fridaPsPath = "frida-ps";
    private String adbPath = "adb";

    private boolean logArgs = true;
    private boolean logReturn = true;
    private boolean logThread = true;
    private boolean printStack = false;
    private boolean printThis = false;
    private boolean prettyPrint = true;

    private boolean templateAppend = false;
    private String templateName = "None";
    private String templateContent = "";
    private TemplatePosition templatePosition = TemplatePosition.APPEND;

    public FridaPluginOptions() {
        registerOptions();
    }

    @Override
    public void registerOptions() {
        if (deviceMode == null) {
            deviceMode = DeviceMode.USB;
        }
        enumOption(PREFIX + "deviceMode", DeviceMode.values(), DeviceMode::valueOf)
                .description("Frida device mode")
                .defaultValue(deviceMode)
                .setter(v -> deviceMode = v);
        strOption(PREFIX + "deviceId")
                .description("ADB device id")
                .defaultValue(deviceId)
                .setter(v -> deviceId = v);
        strOption(PREFIX + "remoteHost")
                .description("Remote frida-server host")
                .defaultValue(remoteHost)
                .setter(v -> remoteHost = v);
        intOption(PREFIX + "remotePort")
                .description("Remote frida-server port")
                .defaultValue(remotePort)
                .setter(v -> remotePort = v);
        boolOption(PREFIX + "spawn")
                .description("Spawn target app")
                .defaultValue(spawn)
                .setter(v -> spawn = v);
        strOption(PREFIX + "targetPackage")
                .description("Target package for spawn/attach")
                .defaultValue(targetPackage)
                .setter(v -> targetPackage = v);
        strOption(PREFIX + "extraArgs")
                .description("Extra frida CLI arguments")
                .defaultValue(extraArgs)
                .setter(v -> extraArgs = v);
        strOption(PREFIX + "fridaPath")
                .description("Path to frida executable")
                .defaultValue(fridaPath)
                .setter(v -> fridaPath = v);
        strOption(PREFIX + "fridaPsPath")
                .description("Path to frida-ps executable")
                .defaultValue(fridaPsPath)
                .setter(v -> fridaPsPath = v);
        strOption(PREFIX + "adbPath")
                .description("Path to adb executable")
                .defaultValue(adbPath)
                .setter(v -> adbPath = v);

        boolOption(PREFIX + "logArgs")
                .description("Log method arguments")
                .defaultValue(logArgs)
                .setter(v -> logArgs = v);
        boolOption(PREFIX + "logReturn")
                .description("Log return value")
                .defaultValue(logReturn)
                .setter(v -> logReturn = v);
        boolOption(PREFIX + "logThread")
                .description("Log thread name")
                .defaultValue(logThread)
                .setter(v -> logThread = v);
        boolOption(PREFIX + "printStack")
                .description("Print Java stack trace")
                .defaultValue(printStack)
                .setter(v -> printStack = v);
        boolOption(PREFIX + "printThis")
                .description("Print this object")
                .defaultValue(printThis)
                .setter(v -> printThis = v);
        boolOption(PREFIX + "prettyPrint")
                .description("Pretty print objects")
                .defaultValue(prettyPrint)
                .setter(v -> prettyPrint = v);

        boolOption(PREFIX + "templateAppend")
                .description("Append extra script")
                .defaultValue(templateAppend)
                .setter(v -> templateAppend = v);
        strOption(PREFIX + "templateName")
                .description("Selected script template")
                .defaultValue(templateName)
                .setter(v -> templateName = v);
        strOption(PREFIX + "templateContent")
                .description("Custom script content")
                .defaultValue(templateContent)
                .setter(v -> templateContent = v);
        if (templatePosition == null) {
            templatePosition = TemplatePosition.APPEND;
        }
        enumOption(PREFIX + "templatePosition", TemplatePosition.values(), TemplatePosition::valueOf)
                .description("Template position inside hook")
                .defaultValue(templatePosition)
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

}
