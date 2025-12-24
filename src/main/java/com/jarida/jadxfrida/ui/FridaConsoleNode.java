package com.jarida.jadxfrida.ui;

import com.jarida.jadxfrida.model.HookRecord;
import jadx.gui.treemodel.JClass;
import jadx.gui.treemodel.JNode;
import jadx.gui.ui.panel.ContentPanel;
import jadx.gui.ui.tab.TabbedPane;
import jadx.gui.utils.Icons;

import javax.swing.Icon;
import java.util.function.Consumer;

public class FridaConsoleNode extends JNode {
    private FridaConsolePanel panel;
    private final JaridaConnectionPanel connectionPanel;
    private final Consumer<HookRecord> onRemoveHook;
    private final Consumer<HookRecord> onToggleHook;
    private final Consumer<HookRecord> onEditHook;
    private final Runnable onRemoveAll;
    private final String version;
    private final Consumer<String> onCustomScriptsChanged;

    public FridaConsoleNode(Consumer<HookRecord> onRemoveHook,
                            Consumer<HookRecord> onToggleHook,
                            Consumer<HookRecord> onEditHook,
                            Runnable onRemoveAll,
                            JaridaConnectionPanel connectionPanel, String version,
                            Consumer<String> onCustomScriptsChanged) {
        this.onRemoveHook = onRemoveHook;
        this.onToggleHook = onToggleHook;
        this.onEditHook = onEditHook;
        this.onRemoveAll = onRemoveAll;
        this.connectionPanel = connectionPanel;
        this.version = version;
        this.onCustomScriptsChanged = onCustomScriptsChanged;
    }

    @Override
    public JClass getJParent() {
        return null;
    }

    @Override
    public String makeString() {
        return "Jarida Console";
    }

    @Override
    public String getName() {
        return "Jarida Console";
    }

    @Override
    public Icon getIcon() {
        return Icons.RUN;
    }

    @Override
    public ContentPanel getContentPanel(TabbedPane tabbedPane) {
        if (panel == null || panel.getTabbedPane() != tabbedPane) {
            panel = new FridaConsolePanel(tabbedPane, this, connectionPanel, version,
                    onRemoveHook, onToggleHook, onEditHook, onRemoveAll, onCustomScriptsChanged);
        }
        return panel;
    }

    @Override
    public boolean supportsQuickTabs() {
        return false;
    }

    public FridaConsolePanel getPanel() {
        return panel;
    }
}
