package com.jarida.jadxfrida;

import com.jarida.jadxfrida.frida.FridaController;
import com.jarida.jadxfrida.frida.HookScriptGenerator;
import com.jarida.jadxfrida.model.FridaSessionConfig;
import com.jarida.jadxfrida.model.HookRecord;
import com.jarida.jadxfrida.model.HookSpec;
import com.jarida.jadxfrida.model.MethodTarget;
import com.jarida.jadxfrida.model.ReturnPatchRule;
import com.jarida.jadxfrida.model.ScriptOptions;
import com.jarida.jadxfrida.model.TemplatePosition;
import com.jarida.jadxfrida.state.JaridaState;
import com.jarida.jadxfrida.state.JaridaStateManager;
import com.jarida.jadxfrida.ui.FridaConfigDialog;
import com.jarida.jadxfrida.ui.FridaConsolePanel;
import com.jarida.jadxfrida.ui.FridaConsoleNode;
import com.jarida.jadxfrida.ui.JaridaConnectionPanel;
import com.jarida.jadxfrida.util.MethodResolver;
import com.jarida.jadxfrida.util.PackageNameResolver;
import jadx.api.ICodeInfo;
import jadx.api.JavaClass;
import jadx.api.JavaMethod;
import jadx.api.JavaNode;
import jadx.api.JadxDecompiler;
import jadx.api.metadata.ICodeAnnotation;
import jadx.api.metadata.ICodeMetadata;
import jadx.api.metadata.ICodeNodeRef;
import jadx.api.metadata.annotations.NodeDeclareRef;
import jadx.api.plugins.JadxPlugin;
import jadx.api.plugins.JadxPluginContext;
import jadx.api.plugins.JadxPluginInfo;
import jadx.api.plugins.JadxPluginInfoBuilder;
import jadx.api.plugins.gui.JadxGuiContext;
import jadx.gui.ui.MainWindow;
import jadx.gui.ui.panel.ContentPanel;
import jadx.gui.ui.tab.TabbedPane;
import jadx.gui.treemodel.JNode;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.IOException;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.util.prefs.Preferences;

public class FridaTracePlugin implements JadxPlugin {
    private JadxPluginContext pluginContext;
    private JadxGuiContext guiContext;
    private JadxDecompiler decompiler;
    private FridaPluginOptions pluginOptions;

    private final FridaController fridaController = new FridaController();
    private FridaConsolePanel consolePanel;
    private FridaConsoleNode consoleNode;
    private JaridaConnectionPanel connectionPanel;
    private final java.util.List<String> pendingLogs = new java.util.ArrayList<>();
    private String pendingScript;
    private final java.util.Map<String, HookRecord> hooks = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, HookSpec> hookSpecs = new java.util.LinkedHashMap<>();
    private final Map<Object, List<Object>> highlightTags = new WeakHashMap<>();
    private java.lang.ref.WeakReference<TabbedPane> highlightListenerOwner;
    private int highlightRetries = 0;
    private static final int MAX_HIGHLIGHT_RETRIES = 6;
    private static final int HIGHLIGHT_RETRY_DELAY_MS = 250;
    private final AtomicBoolean suppressExitWarning = new AtomicBoolean(false);

    private FridaSessionConfig lastSessionConfig = new FridaSessionConfig();
    private ScriptOptions lastScriptOptions = new ScriptOptions();
    private FridaSessionConfig activeSessionConfig;
    private String customScriptPaths = "";
    private final java.util.Set<String> missingCustomScripts = new java.util.HashSet<>();
    private static final Preferences PREFS = Preferences.userRoot().node("com.jarida.jadxfrida");
    private JaridaStateManager stateManager;
    private boolean stateLoaded = false;

    @Override
    public JadxPluginInfo getPluginInfo() {
        return JadxPluginInfoBuilder.pluginId("jarida")
                .name("Jarida")
                .description("Jarida: Frida-backed runtime tracing and patching for Jadx")
                .homepage("https://github.com/BitTheByte/Jarida")
                .provides("gui")
                .requiredJadxVersion("1.5.0, r0")
                .build();
    }

    @Override
    public void init(JadxPluginContext context) {
        this.pluginContext = context;
        this.decompiler = context.getDecompiler();
        this.guiContext = context.getGuiContext();
        this.pluginOptions = new FridaPluginOptions();
        this.pluginOptions.setHighlightColorChangeListener(this::updateHighlights);
        context.registerOptions(pluginOptions);
        this.lastSessionConfig = pluginOptions.toSessionConfig();
        this.lastScriptOptions = pluginOptions.toScriptOptions();
        this.stateManager = new JaridaStateManager(this::appendLog);
        loadSavedPaths();
        loadSavedState();
        if (guiContext != null) {
            initGui();
            if (!hooks.isEmpty()) {
                javax.swing.Timer initial = new javax.swing.Timer(HIGHLIGHT_RETRY_DELAY_MS, e -> updateHighlights());
                initial.setRepeats(false);
                initial.start();
            }
        }
        fridaController.setOnExit(code -> {
            boolean expectedExit = suppressExitWarning.getAndSet(false);
            activeSessionConfig = null;
            if (consolePanel != null) {
                consolePanel.setSessionActive(false);
            }
            if (connectionPanel != null) {
                connectionPanel.setSessionActive(false);
            }
            if (!expectedExit) {
                showWarning("Jarida connection lost. The Frida session ended.");
            }
        });
    }

    @Override
    public void unload() {
        markExpectedExit();
        fridaController.stop();
    }

    private void initGui() {
        guiContext.addMenuAction("Jarida Console", this::openConsole);

        guiContext.addPopupMenuAction(
                "Jarida: Start Tracing",
                ref -> resolveMethodForPopup(ref) != null,
                null,
                ref -> openSettings(ref, false, false, false, false)
        );

        guiContext.addPopupMenuAction(
                "Jarida: Patch Return Value",
                ref -> resolveMethodForPopup(ref) != null,
                null,
                ref -> openSettings(ref, true, true, true, false)
        );

        guiContext.addPopupMenuAction(
                "Jarida: Stop Tracing",
                ref -> isMethodTraced(ref),
                null,
                ref -> stopTracing(ref)
        );

        installHighlightListener();
    }

    private void openConsole() {
        openConsole(true);
    }

    private void openConsole(boolean focus) {
        guiContext.uiRun(() -> {
            JFrame frame = guiContext.getMainFrame();
            if (frame instanceof MainWindow) {
                MainWindow mainWindow = (MainWindow) frame;
                TabbedPane tabs = mainWindow.getTabbedPane();
                int prevIndex = tabs.getSelectedIndex();
                ContentPanel prevPanel = null;
                try {
                    prevPanel = tabs.getSelectedContentPanel();
                } catch (Exception ignored) {
                }
                if (consoleNode == null) {
                    String pkg = PackageNameResolver.resolvePackageName(decompiler);
                    String version = getVersionString();
                    connectionPanel = new JaridaConnectionPanel(fridaController, lastSessionConfig, pkg,
                            this::applyConnectionConfig, this::startSessionFromConnection, this::stopTrace, this::savePathsConfig);
                    consoleNode = new FridaConsoleNode(this::removeHook, this::setHookActive, this::setHooksActive,
                            this::editHook, this::removeAllHooks,
                            connectionPanel, version, this::applyCustomScriptsFromConsole, this::saveCustomScriptsFromConsole,
                            this::jumpToHook);
                }
                jadx.gui.ui.tab.TabsController controller = mainWindow.getTabsController();
                controller.openTab(consoleNode);
                ContentPanel consolePanelRef = tabs.getTabByNode(consoleNode);
                if (focus) {
                    selectConsoleTab(tabs, controller, consoleNode, consolePanelRef);
                } else {
                    if (prevPanel != null && tabs.indexOfComponent(prevPanel) >= 0) {
                        tabs.setSelectedComponent(prevPanel);
                    } else if (prevIndex >= 0 && prevIndex < tabs.getTabCount()) {
                        tabs.setSelectedIndex(prevIndex);
                    }
                }
                consolePanel = consoleNode.getPanel();
                if (connectionPanel != null) {
                    String pkg = PackageNameResolver.resolvePackageName(decompiler);
                    connectionPanel.applyInitial(lastSessionConfig, pkg);
                }
                if (consolePanel != null) {
                    consolePanel.setSessionActive(fridaController.isRunning());
                    consolePanel.setCustomScripts(customScriptPaths);
                }
                if (consolePanel != null && !pendingLogs.isEmpty()) {
                    for (String line : pendingLogs) {
                        consolePanel.appendLog(line);
                    }
                    pendingLogs.clear();
                }
                if (consolePanel != null && pendingScript != null) {
                    consolePanel.setScript(pendingScript);
                    pendingScript = null;
                }
                updateHooksUi();
            } else {
                JOptionPane.showMessageDialog(frame, "Unable to attach Jarida Console to Jadx UI.");
            }
        });
    }

    private void selectConsoleTab(TabbedPane tabs, Object controller, JNode node, ContentPanel panel) {
        if (tabs != null && panel != null) {
            try {
                tabs.setSelectedComponent(panel);
                return;
            } catch (Exception ignored) {
            }
        }
        if (controller == null || node == null) {
            return;
        }
        try {
            java.lang.reflect.Method method = controller.getClass().getMethod("selectTab", JNode.class, boolean.class);
            method.invoke(controller, node, true);
            return;
        } catch (Exception ignored) {
        }
        try {
            java.lang.reflect.Method method = controller.getClass().getMethod("selectTab", JNode.class);
            method.invoke(controller, node);
        } catch (Exception ignored) {
        }
    }

    private void openSettings(ICodeNodeRef ref, boolean patchDefault, boolean showReturnTab,
                              boolean focusReturnTab, boolean requireConnection) {
        MethodTarget target = resolveMethod(ref);
        if (target == null) {
            ICodeNodeRef caretRef = guiContext.getEnclosingNodeUnderCaret();
            target = resolveMethod(caretRef);
            if (ref == null) {
                ref = caretRef;
            }
        }
        if (target == null) {
            JOptionPane.showMessageDialog(guiContext.getMainFrame(), "No method selected.", "Jarida", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String pkg = PackageNameResolver.resolvePackageName(decompiler);
        if (pkg != null && !pkg.trim().isEmpty()) {
            lastSessionConfig.setTargetPackage(pkg);
        }
        FridaSessionConfig fixedConfig = activeSessionConfig != null ? activeSessionConfig : lastSessionConfig;
        FridaConfigDialog dialog = new FridaConfigDialog(guiContext.getMainFrame(), fridaController, target, pkg,
                patchDefault, lastSessionConfig, lastScriptOptions, null,
                pluginOptions.getTemplateName(), pluginOptions.getTemplateContent(), pluginOptions.isTemplateAppend(),
                pluginOptions.getTemplatePosition(),
                fixedConfig, false, showReturnTab, focusReturnTab);
        dialog.setVisible(true);
        if (!dialog.isConfirmed()) {
            return;
        }
        lastSessionConfig = dialog.getSessionConfig();
        lastScriptOptions = dialog.getScriptOptions();
        pluginOptions.updateFrom(lastSessionConfig, lastScriptOptions,
                dialog.isTemplateAppend(), dialog.getTemplateName(), dialog.getTemplateContent());
        pluginOptions.setTemplatePosition(dialog.getTemplatePosition());
        ReturnPatchRule patchRule = dialog.getReturnPatchRule();
        String hookKey = hookKey(target);
        HookRecord record = hooks.get(hookKey);
        if (record == null) {
            record = new HookRecord(hookKey, target.getDisplaySignature(), ref);
            hooks.put(hookKey, record);
        } else {
            appendLog("Updating hook: " + record.getDisplay());
            if (record.getNodeRef() == null && ref != null) {
                record.setNodeRef(ref);
            }
        }
        HookSpec spec = new HookSpec(target, lastScriptOptions, patchRule,
                dialog.getExtraScript(), dialog.getTemplatePosition(), hookKey,
                dialog.isTemplateAppend(), dialog.getTemplateName(), dialog.getTemplateContent());
        hookSpecs.put(hookKey, spec);
        String script = buildCombinedScript();
        boolean canReuseNow = fridaController.isRunning()
                && activeSessionConfig != null
                && fixedConfig != null
                && fixedConfig.isCompatibleForReuse(activeSessionConfig);

        if (consolePanel != null) {
            consolePanel.setScript(script);
        } else {
            pendingScript = script;
        }
        updateHooksUi();
        updateHighlights();
        saveCurrentState();

        if (!fridaController.isRunning()) {
            focusConnectionTab();
            if (consolePanel != null) {
                consolePanel.setSessionActive(false);
            }
            return;
        }
        try {
            if (canReuseNow) {
                fridaController.updateSessionScript(script, this::appendLog);
                if (consolePanel != null) {
                    consolePanel.setSessionActive(true);
                }
            } else {
                markExpectedExit();
                fridaController.startWithScript(lastSessionConfig, script, this::appendLog);
                activeSessionConfig = lastSessionConfig;
                if (consolePanel != null) {
                    consolePanel.setSessionActive(true);
                }
            }
        } catch (Exception e) {
            showError("Failed to start Jarida session: " + e.getMessage());
            if (consolePanel != null) {
                consolePanel.setSessionActive(false);
            }
        }
    }

    private MethodTarget resolveMethod(ICodeNodeRef ref) {
        MethodTarget target = MethodResolver.resolve(decompiler, ref);
        if (target != null) {
            return target;
        }
        if (guiContext != null) {
            ICodeNodeRef[] candidates = new ICodeNodeRef[]{
                    guiContext.getNodeUnderMouse(),
                    guiContext.getNodeUnderCaret(),
                    guiContext.getEnclosingNodeUnderMouse(),
                    guiContext.getEnclosingNodeUnderCaret()
            };
            for (ICodeNodeRef candidate : candidates) {
                if (candidate == null || candidate == ref) {
                    continue;
                }
                target = MethodResolver.resolve(decompiler, candidate);
                if (target != null) {
                    return target;
                }
            }
        }
        return resolveMethodFromEditor();
    }

    private MethodTarget resolveMethodFromEditor() {
        if (guiContext == null) {
            return null;
        }
        JFrame frame = guiContext.getMainFrame();
        if (!(frame instanceof MainWindow)) {
            return null;
        }
        TabbedPane tabs = ((MainWindow) frame).getTabbedPane();
        ContentPanel panel = tabs.getSelectedContentPanel();
        if (panel == null) {
            return null;
        }
        Object area = getCurrentCodeArea(panel);
        if (area == null) {
            return null;
        }
        Integer offset = getCaretPosition(area);
        if (offset == null) {
            return null;
        }
        ICodeInfo codeInfo = getCodeInfo(area);
        if (codeInfo == null || !codeInfo.hasMetadata()) {
            return null;
        }
        ICodeMetadata metadata = codeInfo.getCodeMetadata();
        if (metadata == null) {
            return null;
        }
        ICodeNodeRef nodeRef = null;
        ICodeAnnotation ann = metadata.searchUp(offset, ICodeAnnotation.AnnType.METHOD);
        nodeRef = extractNodeRef(ann);
        if (nodeRef == null) {
            ann = metadata.searchUp(offset, ICodeAnnotation.AnnType.DECLARATION);
            nodeRef = extractNodeRef(ann);
        }
        MethodTarget resolved = null;
        if (nodeRef != null) {
            resolved = MethodResolver.resolve(decompiler, nodeRef);
        }
        if (resolved == null) {
            nodeRef = findNearestMethodRef(metadata, area, offset);
            if (nodeRef != null) {
                resolved = MethodResolver.resolve(decompiler, nodeRef);
            }
        }
        if (resolved == null) {
            resolved = resolveLambdaFromContext(panel, area, codeInfo, offset);
        }
        return resolved;
    }

    private ICodeNodeRef extractNodeRef(ICodeAnnotation ann) {
        if (ann == null) {
            return null;
        }
        if (ann instanceof NodeDeclareRef) {
            return ((NodeDeclareRef) ann).getNode();
        }
        if (ann instanceof ICodeNodeRef) {
            return (ICodeNodeRef) ann;
        }
        return null;
    }

    private ICodeNodeRef findNearestMethodRef(ICodeMetadata metadata, Object area, int offset) {
        if (metadata == null) {
            return null;
        }
        Map<Integer, ICodeAnnotation> map = metadata.getAsMap();
        if (map == null || map.isEmpty()) {
            return null;
        }
        Integer caretLine = getLineOfOffset(area, offset);
        ICodeNodeRef best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Map.Entry<Integer, ICodeAnnotation> entry : map.entrySet()) {
            ICodeAnnotation ann = entry.getValue();
            if (ann == null) {
                continue;
            }
            ICodeAnnotation.AnnType type = ann.getAnnType();
            if (type != ICodeAnnotation.AnnType.METHOD && type != ICodeAnnotation.AnnType.DECLARATION) {
                continue;
            }
            ICodeNodeRef ref = extractNodeRef(ann);
            if (ref == null) {
                continue;
            }
            if (MethodResolver.resolve(decompiler, ref) == null) {
                continue;
            }
            if (caretLine != null) {
                Integer line = getLineOfOffset(area, entry.getKey());
                if (line != null && line.equals(caretLine)) {
                    return ref;
                }
            }
            int distance = Math.abs(entry.getKey() - offset);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = ref;
            }
        }
        if (bestDistance > 3000) {
            return null;
        }
        return best;
    }

    private MethodTarget resolveLambdaFromContext(ContentPanel panel, Object area, ICodeInfo codeInfo, int offset) {
        if (panel == null || codeInfo == null) {
            return null;
        }
        String lineText = getLineAtOffset(codeInfo, area, offset);
        if (lineText == null) {
            return null;
        }
        String trimmed = lineText.trim();
        if (!(trimmed.contains("->") || trimmed.contains("lambda$") || trimmed.contains("Lambda"))) {
            return null;
        }
        JavaClass javaClass = getJavaClass(panel);
        if (javaClass == null) {
            return null;
        }
        List<JavaMethod> lambdaMethods = new ArrayList<>();
        for (JavaMethod method : javaClass.getMethods()) {
            if (method == null) {
                continue;
            }
            String name = method.getName();
            if (name == null) {
                continue;
            }
            if (name.contains("lambda$") || name.contains("$lambda$") || name.startsWith("lambda$")) {
                lambdaMethods.add(method);
            }
        }
        if (lambdaMethods.isEmpty()) {
            return null;
        }
        if (lambdaMethods.size() == 1) {
            return MethodResolver.fromJavaMethod(lambdaMethods.get(0));
        }
        List<MethodTarget> targets = new ArrayList<>();
        for (JavaMethod method : lambdaMethods) {
            MethodTarget target = MethodResolver.fromJavaMethod(method);
            if (target != null) {
                targets.add(target);
            }
        }
        if (targets.isEmpty()) {
            return null;
        }
        String[] options = new String[targets.size()];
        for (int i = 0; i < targets.size(); i++) {
            options[i] = targets.get(i).getDisplaySignature();
        }
        Object choice = JOptionPane.showInputDialog(guiContext.getMainFrame(),
                "Select lambda method to hook:", "Jarida",
                JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (choice == null) {
            return null;
        }
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(choice.toString())) {
                return targets.get(i);
            }
        }
        return null;
    }

    private String getLineAtOffset(ICodeInfo codeInfo, Object area, int offset) {
        if (codeInfo == null) {
            return null;
        }
        String code = codeInfo.getCodeStr();
        if (code == null) {
            return null;
        }
        Integer line = getLineOfOffset(area, offset);
        if (line == null) {
            return null;
        }
        String[] lines = code.split("\\R", -1);
        int idx = line;
        if (idx < 0 || idx >= lines.length) {
            idx = line - 1;
        }
        if (idx < 0 || idx >= lines.length) {
            return null;
        }
        return lines[idx];
    }

    private JavaClass getJavaClass(ContentPanel panel) {
        if (panel == null) {
            return null;
        }
        JNode node = panel.getNode();
        if (node == null) {
            return null;
        }
        JavaNode javaNode = node.getJavaNode();
        if (javaNode instanceof JavaClass) {
            return (JavaClass) javaNode;
        }
        if (javaNode instanceof JavaMethod) {
            return ((JavaMethod) javaNode).getDeclaringClass();
        }
        return null;
    }

    private Integer getCaretPosition(Object area) {
        if (area == null) {
            return null;
        }
        java.lang.reflect.Method method = lookupMethod(area.getClass(), "getCaretPosition");
        if (method == null) {
            return null;
        }
        try {
            Object out = method.invoke(area);
            if (out instanceof Integer) {
                return (Integer) out;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void appendLog(String line) {
        if (consolePanel == null) {
            pendingLogs.add(line);
            return;
        }
        consolePanel.appendLog(line);
    }

    private String getVersionString() {
        try {
            Package pkg = getClass().getPackage();
            if (pkg != null && pkg.getImplementationVersion() != null) {
                return pkg.getImplementationVersion();
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    private void showError(String message) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(guiContext.getMainFrame(), message, "Jarida", JOptionPane.ERROR_MESSAGE));
        appendLog(message);
    }

    private void showWarning(String message) {
        if (guiContext != null && guiContext.getMainFrame() != null) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(guiContext.getMainFrame(), message, "Jarida", JOptionPane.WARNING_MESSAGE));
        }
        appendLog(message);
    }

    private void markExpectedExit() {
        if (fridaController.isRunning()) {
            suppressExitWarning.set(true);
        }
    }

    private void stopTrace() {
        markExpectedExit();
        fridaController.stop();
        activeSessionConfig = null;
        appendLog("Jarida trace stopped.");
        if (consolePanel != null) {
            consolePanel.setSessionActive(false);
        }
    }

    private void applyConnectionConfig(FridaSessionConfig cfg) {
        if (cfg == null) {
            return;
        }
        lastSessionConfig = cfg;
        pluginOptions.updateFrom(cfg, lastScriptOptions,
                pluginOptions.isTemplateAppend(), pluginOptions.getTemplateName(), pluginOptions.getTemplateContent());
    }

    private void applyCustomScriptsFromConsole(String paths) {
        customScriptPaths = paths == null ? "" : paths;
        String script = buildCombinedScript();
        if (consolePanel != null) {
            consolePanel.setScript(script);
        } else {
            pendingScript = script;
        }
        if (fridaController.isRunning()) {
            try {
                fridaController.updateSessionScript(script, this::appendLog);
            } catch (IOException e) {
                appendLog("Failed to update Jarida session: " + e.getMessage());
            }
        }
    }

    private void saveCustomScriptsFromConsole(String paths) {
        customScriptPaths = paths == null ? "" : paths;
        try {
            PREFS.put("customScripts", customScriptPaths);
        } catch (Exception ignored) {
        }
        appendLog("Custom scripts saved.");
    }

    private void savePathsConfig(FridaSessionConfig cfg) {
        if (cfg == null) {
            return;
        }
        String adb = cfg.getAdbPath();
        String frida = cfg.getFridaPath();
        String fridaPs = cfg.getFridaPsPath();
        try {
            if (adb != null && !adb.trim().isEmpty()) {
                PREFS.put("path.adb", adb.trim());
                lastSessionConfig.setAdbPath(adb.trim());
            }
            if (frida != null && !frida.trim().isEmpty()) {
                PREFS.put("path.frida", frida.trim());
                lastSessionConfig.setFridaPath(frida.trim());
            }
            if (fridaPs != null && !fridaPs.trim().isEmpty()) {
                PREFS.put("path.fridaPs", fridaPs.trim());
                lastSessionConfig.setFridaPsPath(fridaPs.trim());
            }
        } catch (Exception ignored) {
        }
        pluginOptions.updateFrom(lastSessionConfig, lastScriptOptions,
                pluginOptions.isTemplateAppend(), pluginOptions.getTemplateName(), pluginOptions.getTemplateContent());
    }

    private void loadSavedPaths() {
        try {
            String adb = PREFS.get("path.adb", null);
            String frida = PREFS.get("path.frida", null);
            String fridaPs = PREFS.get("path.fridaPs", null);
            String savedScripts = PREFS.get("customScripts", null);
            if (adb != null && !adb.trim().isEmpty()) {
                lastSessionConfig.setAdbPath(adb.trim());
            }
            if (frida != null && !frida.trim().isEmpty()) {
                lastSessionConfig.setFridaPath(frida.trim());
            }
            if (fridaPs != null && !fridaPs.trim().isEmpty()) {
                lastSessionConfig.setFridaPsPath(fridaPs.trim());
            }
            if (savedScripts != null) {
                customScriptPaths = savedScripts;
            }
            if (pluginOptions != null) {
                pluginOptions.updateFrom(lastSessionConfig, lastScriptOptions,
                        pluginOptions.isTemplateAppend(), pluginOptions.getTemplateName(), pluginOptions.getTemplateContent());
            }
        } catch (Exception ignored) {
        }
    }

    private void startSessionFromConnection(FridaSessionConfig cfg) {
        if (cfg == null) {
            return;
        }
        lastSessionConfig = cfg;
        openConsole();
        if (fridaController.isRunning()) {
            appendLog("Jarida session already active.");
            if (consolePanel != null) {
                consolePanel.setSessionActive(true);
            }
            return;
        }
        try {
            String script = buildCombinedScript();
            fridaController.startWithScript(cfg, script, this::appendLog);
            activeSessionConfig = cfg;
            if (consolePanel != null) {
                consolePanel.setScript(script);
                consolePanel.setSessionActive(true);
            } else {
                pendingScript = script;
            }
        } catch (Exception e) {
            showError("Failed to start Jarida session: " + e.getMessage());
            if (consolePanel != null) {
                consolePanel.setSessionActive(false);
            }
        }
    }

    private void focusConnectionTab() {
        if (guiContext == null) {
            return;
        }
        guiContext.uiRun(() -> {
            openConsole(true);
            if (consolePanel != null) {
                consolePanel.selectConnectionTab();
            }
        });
    }

    private String hookKey(MethodTarget target) {
        return target.getDisplaySignature();
    }

    private void updateHooksUi() {
        if (consolePanel != null) {
            consolePanel.updateHooks(new java.util.ArrayList<>(hooks.values()));
        }
        highlightRetries = 0;
        updateHighlights();
    }

    private MethodTarget resolveMethodForPopup(ICodeNodeRef ref) {
        return MethodResolver.resolve(decompiler, ref);
    }

    private boolean isMethodTraced(ICodeNodeRef ref) {
        MethodTarget target = resolveMethodForPopup(ref);
        if (target == null) {
            return false;
        }
        HookRecord record = hooks.get(hookKey(target));
        return record != null && record.isActive();
    }

    private void stopTracing(ICodeNodeRef ref) {
        MethodTarget target = resolveMethodForPopup(ref);
        if (target == null) {
            return;
        }
        HookRecord record = hooks.get(hookKey(target));
        if (record != null) {
            removeHook(record);
        }
    }

    private void removeHook(HookRecord record) {
        if (record == null) {
            return;
        }
        hooks.remove(record.getKey());
        hookSpecs.remove(record.getKey());
        record.setActive(false);
        reloadCombinedHooks();
        updateHooksUi();
        saveCurrentState();
    }

    private void editHook(HookRecord record) {
        if (record == null) {
            return;
        }
        HookSpec spec = hookSpecs.get(record.getKey());
        if (spec == null) {
            appendLog("Hook not found for editing.");
            return;
        }
        MethodTarget target = spec.getTarget();
        if (target == null) {
            appendLog("Hook target not available.");
            return;
        }
        String pkg = PackageNameResolver.resolvePackageName(decompiler);
        if (pkg != null && !pkg.trim().isEmpty()) {
            lastSessionConfig.setTargetPackage(pkg);
        }
        ReturnPatchRule initialRule = spec.getReturnPatchRule();
        boolean patchDefault = initialRule != null && initialRule.isEnabled();
        TemplatePosition templatePosition = spec.getTemplatePosition();
        FridaSessionConfig fixedConfig = activeSessionConfig != null ? activeSessionConfig : lastSessionConfig;

        FridaConfigDialog dialog = new FridaConfigDialog(guiContext.getMainFrame(), fridaController, target, pkg,
                patchDefault, lastSessionConfig, spec.getOptions(), initialRule,
                spec.getTemplateName(), spec.getTemplateContent(), spec.isTemplateEnabled(),
                templatePosition, fixedConfig, false, true, false);
        dialog.setVisible(true);
        if (!dialog.isConfirmed()) {
            return;
        }

        lastSessionConfig = dialog.getSessionConfig();
        lastScriptOptions = dialog.getScriptOptions();
        pluginOptions.updateFrom(lastSessionConfig, lastScriptOptions,
                dialog.isTemplateAppend(), dialog.getTemplateName(), dialog.getTemplateContent());
        pluginOptions.setTemplatePosition(dialog.getTemplatePosition());

        HookSpec updated = new HookSpec(target, lastScriptOptions, dialog.getReturnPatchRule(),
                dialog.getExtraScript(), dialog.getTemplatePosition(), record.getKey(),
                dialog.isTemplateAppend(), dialog.getTemplateName(), dialog.getTemplateContent());
        hookSpecs.put(record.getKey(), updated);

        String script = buildCombinedScript();
        if (consolePanel != null) {
            consolePanel.setScript(script);
        } else {
            pendingScript = script;
        }
        if (fridaController.isRunning()) {
            try {
                fridaController.updateSessionScript(script, this::appendLog);
            } catch (Exception e) {
                appendLog("Failed to reload hooks: " + e.getMessage());
            }
        }
        updateHighlights();
        saveCurrentState();
    }

    private void jumpToHook(HookRecord record) {
        if (record == null || guiContext == null) {
            return;
        }
        ICodeNodeRef ref = record.getNodeRef();
        if (ref == null) {
            ref = resolveNodeRefFromSpec(record);
            if (ref != null) {
                record.setNodeRef(ref);
            }
        }
        if (ref == null) {
            showWarning("No source location available for this hook. Open the declaring class to let Jarida resolve it.");
            return;
        }
        final ICodeNodeRef finalRef = ref;
        guiContext.uiRun(() -> {
            boolean opened = guiContext.open(finalRef);
            if (!opened) {
                showWarning("Unable to open hook location in Jadx.");
                return;
            }
            highlightRetries = 0;
            javax.swing.Timer refresh = new javax.swing.Timer(HIGHLIGHT_RETRY_DELAY_MS, e -> updateHighlights());
            refresh.setRepeats(false);
            refresh.start();
        });
    }

    private ICodeNodeRef resolveNodeRefFromSpec(HookRecord record) {
        if (record == null || decompiler == null) {
            return null;
        }
        HookSpec spec = hookSpecs.get(record.getKey());
        if (spec == null || spec.getTarget() == null) {
            return null;
        }
        String className = spec.getTarget().getClassName();
        if (className == null || className.isEmpty()) {
            return null;
        }
        String signature = spec.getTarget().getDisplaySignature();
        JavaClass cls = lookupJavaClass(className);
        if (cls == null) {
            return null;
        }
        for (JavaMethod method : cls.getMethods()) {
            if (method == null) {
                continue;
            }
            MethodTarget target = MethodResolver.fromJavaMethod(method);
            if (target != null && signature.equals(target.getDisplaySignature())) {
                try {
                    return method.getMethodNode();
                } catch (Exception ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private JavaClass lookupJavaClass(String className) {
        if (className == null || className.isEmpty() || decompiler == null) {
            return null;
        }
        try {
            JavaClass cls = decompiler.searchJavaClassByOrigFullName(className);
            if (cls != null) {
                return cls;
            }
        } catch (Exception ignored) {
        }
        try {
            JavaClass cls = decompiler.searchJavaClassByAliasFullName(className);
            if (cls != null) {
                return cls;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void removeAllHooks() {
        for (HookRecord record : new java.util.ArrayList<>(hooks.values())) {
            hooks.remove(record.getKey());
            hookSpecs.remove(record.getKey());
        }
        reloadCombinedHooks();
        updateHooksUi();
        saveCurrentState();
    }

    private void reloadCombinedHooks() {
        String script = buildCombinedScript();
        if (consolePanel != null) {
            consolePanel.setScript(script);
        } else {
            pendingScript = script;
        }
        if (!fridaController.isRunning()) {
            return;
        }
        try {
            fridaController.updateSessionScript(script, this::appendLog);
        } catch (Exception e) {
            appendLog("Failed to reload hooks: " + e.getMessage());
        }
    }

    private java.util.List<HookSpec> getActiveSpecs() {
        java.util.List<HookSpec> active = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, HookSpec> entry : hookSpecs.entrySet()) {
            HookRecord record = hooks.get(entry.getKey());
            if (record != null && record.isActive()) {
                active.add(entry.getValue());
            }
        }
        return active;
    }

    private String buildCombinedScript() {
        java.util.List<String> globals = new java.util.ArrayList<>();
        java.util.List<CustomScriptEntry> scripts = parseCustomScriptPaths(customScriptPaths);
        for (CustomScriptEntry entry : scripts) {
            if (!entry.enabled || entry.path.isEmpty()) {
                continue;
            }
            try {
                java.nio.file.Path p = java.nio.file.Paths.get(entry.path);
                String content = java.nio.file.Files.readString(p);
                globals.add("// -- " + entry.path + "\n" + content);
            } catch (Exception e) {
                if (missingCustomScripts.add(entry.path)) {
                    appendLog("Custom script not found: " + entry.path);
                }
            }
        }
        return HookScriptGenerator.generateCombined(getActiveSpecs(), globals);
    }

    private static final class CustomScriptEntry {
        final String path;
        final boolean enabled;

        private CustomScriptEntry(String path, boolean enabled) {
            this.path = path == null ? "" : path.trim();
            this.enabled = enabled;
        }
    }

    private java.util.List<CustomScriptEntry> parseCustomScriptPaths(String raw) {
        java.util.List<CustomScriptEntry> entries = new java.util.ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return entries;
        }
        String[] lines = raw.split("\\R");
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            boolean enabled = true;
            String path = trimmed;
            if (trimmed.startsWith("1|") || trimmed.startsWith("0|")) {
                enabled = trimmed.startsWith("1|");
                path = trimmed.substring(2).trim();
            } else if (trimmed.startsWith("[x]") || trimmed.startsWith("[ ]")) {
                enabled = trimmed.startsWith("[x]");
                path = trimmed.substring(3).trim();
            }
            if (!path.isEmpty()) {
                entries.add(new CustomScriptEntry(path, enabled));
            }
        }
        return entries;
    }

    private void setHookActive(HookRecord record, boolean active) {
        if (record == null) {
            return;
        }
        if (record.isActive() == active) {
            return;
        }
        record.setActive(active);
        reloadCombinedHooks();
        updateHooksUi();
        saveCurrentState();
    }

    private void setHooksActive(java.util.List<HookRecord> records, boolean active) {
        if (records == null || records.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (HookRecord record : records) {
            if (record != null && record.isActive() != active) {
                record.setActive(active);
                changed = true;
            }
        }
        if (changed) {
            reloadCombinedHooks();
            updateHooksUi();
            saveCurrentState();
        }
    }

    private void installHighlightListener() {
        if (guiContext == null) {
            return;
        }
        guiContext.uiRun(() -> {
            JFrame frame = guiContext.getMainFrame();
            if (!(frame instanceof MainWindow)) {
                return;
            }
            TabbedPane tabs = ((MainWindow) frame).getTabbedPane();
            if (tabs == null) {
                return;
            }
            TabbedPane existing = highlightListenerOwner != null ? highlightListenerOwner.get() : null;
            if (existing == tabs) {
                return;
            }
            tabs.addChangeListener(e -> updateHighlights());
            highlightListenerOwner = new java.lang.ref.WeakReference<>(tabs);
        });
    }

    private void updateHighlights() {
        if (guiContext == null) {
            return;
        }
        Runnable task = () -> {
            installHighlightListener();
            clearAllHighlights();
            JFrame frame = guiContext.getMainFrame();
            if (!(frame instanceof MainWindow)) {
                return;
            }
            TabbedPane tabs = ((MainWindow) frame).getTabbedPane();
            if (tabs == null) {
                return;
            }
            List<ContentPanel> panels = tabs.getTabs();
            if (panels == null) {
                return;
            }
            Set<ICodeNodeRef> activeRefs = new HashSet<>();
            Map<String, HookRecord> activeBySignature = new java.util.HashMap<>();
            for (HookRecord record : hooks.values()) {
                if (!record.isActive()) {
                    continue;
                }
                if (record.getNodeRef() != null) {
                    activeRefs.add(record.getNodeRef());
                }
                activeBySignature.put(record.getKey(), record);
            }
            if (activeBySignature.isEmpty()) {
                highlightRetries = 0;
                return;
            }
            boolean highlightAllInstances = pluginOptions != null && pluginOptions.isHighlightAllInstances();
            boolean metadataPending = false;
            boolean anyPanelSeen = false;
            for (ContentPanel panel : panels) {
                Object area = getCurrentCodeArea(panel);
                if (area == null) {
                    continue;
                }
                anyPanelSeen = true;
                ICodeInfo codeInfo = getCodeInfo(area);
                if (codeInfo == null || !codeInfo.hasMetadata()) {
                    metadataPending = true;
                    continue;
                }
                ICodeMetadata metadata = codeInfo.getCodeMetadata();
                if (metadata == null) {
                    metadataPending = true;
                    continue;
                }
                Map<Integer, ICodeAnnotation> map = metadata.getAsMap();
                if (map == null || map.isEmpty()) {
                    continue;
                }
                Color highlight = getHookHighlightColor();
                List<Object> tags = new ArrayList<>();
                Set<Integer> highlightedLines = new HashSet<>();
                Map<ICodeNodeRef, Boolean> resolvedCache = new java.util.HashMap<>();
                for (Map.Entry<Integer, ICodeAnnotation> entry : map.entrySet()) {
                    ICodeAnnotation ann = entry.getValue();
                    if (ann == null) {
                        continue;
                    }
                    ICodeNodeRef nodeRef = extractNodeRef(ann);
                    if (nodeRef == null) {
                        continue;
                    }
                    boolean isDeclaration = ann instanceof NodeDeclareRef;
                    if (!highlightAllInstances && !isDeclaration) {
                        continue;
                    }
                    boolean matches = activeRefs.contains(nodeRef);
                    if (!matches) {
                        Boolean cached = resolvedCache.get(nodeRef);
                        if (Boolean.TRUE.equals(cached)) {
                            matches = true;
                        } else if (cached == null) {
                            MethodTarget resolved = MethodResolver.resolve(decompiler, nodeRef);
                            boolean matched = false;
                            if (resolved != null) {
                                HookRecord pending = activeBySignature.get(resolved.getDisplaySignature());
                                if (pending != null) {
                                    if (pending.getNodeRef() != nodeRef) {
                                        pending.setNodeRef(nodeRef);
                                    }
                                    activeRefs.add(nodeRef);
                                    matched = true;
                                }
                            }
                            resolvedCache.put(nodeRef, matched);
                            matches = matched;
                        }
                    }
                    if (!matches) {
                        continue;
                    }
                    Integer line = getLineOfOffset(area, entry.getKey());
                    if (line == null || !highlightedLines.add(line)) {
                        continue;
                    }
                    Object tag = addLineHighlight(area, line, highlight);
                    if (tag != null) {
                        tags.add(tag);
                    }
                }
                if (!tags.isEmpty()) {
                    highlightTags.put(area, tags);
                }
            }
            if ((metadataPending || !anyPanelSeen) && highlightRetries < MAX_HIGHLIGHT_RETRIES) {
                highlightRetries++;
                javax.swing.Timer timer = new javax.swing.Timer(HIGHLIGHT_RETRY_DELAY_MS, e -> updateHighlights());
                timer.setRepeats(false);
                timer.start();
            } else {
                highlightRetries = 0;
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            guiContext.uiRun(task);
        }
    }

    private void clearAllHighlights() {
        if (highlightTags.isEmpty()) {
            return;
        }
        Map<Object, List<Object>> snapshot = new java.util.HashMap<>(highlightTags);
        highlightTags.clear();
        for (Map.Entry<Object, List<Object>> entry : snapshot.entrySet()) {
            Object area = entry.getKey();
            List<Object> tags = entry.getValue();
            if (area == null || tags == null) {
                continue;
            }
            for (Object tag : tags) {
                removeLineHighlight(area, tag);
            }
        }
    }

    private final java.util.concurrent.ConcurrentMap<String, java.lang.reflect.Method> reflectionCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<String> reflectionMissReported = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    private java.lang.reflect.Method lookupMethod(Class<?> cls, String name, Class<?>... params) {
        return lookupMethod(cls, name, true, params);
    }

    private java.lang.reflect.Method lookupMethod(Class<?> cls, String name, boolean reportMissing, Class<?>... params) {
        if (cls == null) {
            return null;
        }
        String key = cls.getName() + "#" + name;
        java.lang.reflect.Method cached = reflectionCache.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            java.lang.reflect.Method m = cls.getMethod(name, params);
            reflectionCache.put(key, m);
            return m;
        } catch (NoSuchMethodException e) {
            if (reportMissing && reflectionMissReported.add(key)) {
                appendLog("Jarida: jadx API method missing: " + cls.getSimpleName() + "." + name
                        + " — highlight/code-area features may be degraded.");
            }
            return null;
        }
    }

    private Object getCurrentCodeArea(ContentPanel panel) {
        if (panel == null) {
            return null;
        }
        // Not all ContentPanel subclasses (e.g. our own FridaConsolePanel) expose
        // getCurrentCodeArea — absence here is expected, not a jadx API drift signal.
        java.lang.reflect.Method method = lookupMethod(panel.getClass(), "getCurrentCodeArea", false);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(panel);
        } catch (Exception ignored) {
            return null;
        }
    }

    private ICodeInfo getCodeInfo(Object area) {
        if (area == null) {
            return null;
        }
        java.lang.reflect.Method method = lookupMethod(area.getClass(), "getCodeInfo");
        if (method == null) {
            return null;
        }
        try {
            Object out = method.invoke(area);
            if (out instanceof ICodeInfo) {
                return (ICodeInfo) out;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Integer getLineOfOffset(Object area, int offset) {
        if (area == null) {
            return null;
        }
        java.lang.reflect.Method method = lookupMethod(area.getClass(), "getLineOfOffset", int.class);
        if (method == null) {
            return null;
        }
        try {
            Object out = method.invoke(area, offset);
            if (out instanceof Integer) {
                return (Integer) out;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Object addLineHighlight(Object area, int line, Color color) {
        if (area == null) {
            return null;
        }
        java.lang.reflect.Method method = lookupMethod(area.getClass(), "addLineHighlight", int.class, Color.class);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(area, line, color);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void removeLineHighlight(Object area, Object tag) {
        if (area == null) {
            return;
        }
        java.lang.reflect.Method method = lookupMethod(area.getClass(), "removeLineHighlight", Object.class);
        if (method == null) {
            return;
        }
        try {
            method.invoke(area, tag);
        } catch (Exception ignored) {
        }
    }

    private Color getHookHighlightColor() {
        if (pluginOptions == null) {
            return FridaPluginOptions.defaultHighlightColor();
        }
        return pluginOptions.getHighlightColor();
    }

    // ========== State Persistence Methods ==========

    /**
     * Load saved Jarida state from the JADX project file.
     */
    private void loadSavedState() {
        if (stateLoaded || stateManager == null || guiContext == null) {
            return;
        }
        JFrame mainFrame = guiContext.getMainFrame();
        if (mainFrame == null) {
            return;
        }
        JaridaState state = stateManager.loadState(mainFrame);
        if (state == null) {
            return;
        }
        stateLoaded = true;
        // Restore custom script paths
        if (state.getCustomScriptPaths() != null && !state.getCustomScriptPaths().isEmpty()) {
            customScriptPaths = state.getCustomScriptPaths();
        }
        // Restore hooks
        Map<String, HookSpec> loadedSpecs = state.toHookSpecs();
        Map<String, Boolean> activeStates = state.toActiveStates();
        for (Map.Entry<String, HookSpec> entry : loadedSpecs.entrySet()) {
            String hookKey = entry.getKey();
            HookSpec spec = entry.getValue();
            hookSpecs.put(hookKey, spec);
            MethodTarget target = spec.getTarget();
            String display = target != null ? target.getDisplaySignature() : hookKey;
            HookRecord record = new HookRecord(hookKey, display, null);
            record.setActive(activeStates.getOrDefault(hookKey, true));
            hooks.put(hookKey, record);
        }
        if (!hooks.isEmpty()) {
            appendLog("Restored " + hooks.size() + " hook(s) from project.");
        }
    }

    /**
     * Save the current Jarida state to the JADX project file.
     */
    private void saveCurrentState() {
        if (stateManager == null || guiContext == null) {
            return;
        }
        JFrame mainFrame = guiContext.getMainFrame();
        if (mainFrame == null) {
            return;
        }
        // Build active states map from hooks
        Map<String, Boolean> activeStates = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, HookRecord> entry : hooks.entrySet()) {
            activeStates.put(entry.getKey(), entry.getValue().isActive());
        }
        stateManager.saveState(mainFrame, hookSpecs, activeStates, customScriptPaths);
    }

}
