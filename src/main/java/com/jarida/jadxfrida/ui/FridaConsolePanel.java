package com.jarida.jadxfrida.ui;

import com.jarida.jadxfrida.model.HookRecord;
import jadx.gui.treemodel.JNode;
import jadx.gui.ui.panel.ContentPanel;
import jadx.gui.ui.tab.TabbedPane;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.function.Consumer;

public class FridaConsolePanel extends ContentPanel {
    private final JTextArea logArea;
    private final JTextArea scriptArea;
    private final Consumer<HookRecord> onRemoveHook;
    private final Consumer<HookRecord> onToggleHook;
    private final Runnable onRemoveAll;
    private final DefaultListModel<HookRecord> hooksModel = new DefaultListModel<>();
    private final JList<HookRecord> hooksList = new JList<>(hooksModel);
    private final JaridaConnectionPanel connectionPanel;
    private final String version;
    private final JTabbedPane tabs;
    private boolean connectionVisible;

    public FridaConsolePanel(TabbedPane tabbedPane, JNode node, JaridaConnectionPanel connectionPanel,
                             String version,
                             Consumer<HookRecord> onRemoveHook, Consumer<HookRecord> onToggleHook, Runnable onRemoveAll) {
        super(tabbedPane, node);
        this.connectionPanel = connectionPanel;
        this.version = version;
        this.onRemoveHook = onRemoveHook;
        this.onToggleHook = onToggleHook;
        this.onRemoveAll = onRemoveAll;
        setLayout(new BorderLayout());
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        scriptArea = new JTextArea();
        scriptArea.setEditable(false);
        scriptArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        tabs = new JTabbedPane();
        if (connectionPanel != null) {
            tabs.addTab("Connection", connectionPanel);
            connectionVisible = true;
        }
        tabs.addTab("Console", buildConsolePanel());
        tabs.addTab("Hooks", buildHooksPanel());
        tabs.addTab("Script", buildScriptPanel());
        tabs.addTab("Info", buildInfoPanel());
        add(tabs, BorderLayout.CENTER);
    }

    private java.awt.Component buildHooksPanel() {
        hooksList.setVisibleRowCount(8);
        hooksList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JButton toggleSelected = new JButton("Enable/Disable");
        toggleSelected.addActionListener(e -> {
            HookRecord selected = hooksList.getSelectedValue();
            if (selected != null && onToggleHook != null) {
                onToggleHook.accept(selected);
            }
        });
        JButton removeSelected = new JButton("Remove Selected");
        removeSelected.addActionListener(e -> {
            HookRecord selected = hooksList.getSelectedValue();
            if (selected != null && onRemoveHook != null) {
                onRemoveHook.accept(selected);
            }
        });
        JButton removeAll = new JButton("Remove All");
        removeAll.addActionListener(e -> {
            if (onRemoveAll != null) {
                onRemoveAll.run();
            }
        });
        JToolBar hookBar = new JToolBar();
        hookBar.setFloatable(false);
        hookBar.add(toggleSelected);
        hookBar.add(removeSelected);
        hookBar.add(removeAll);

        java.awt.Panel panel = new java.awt.Panel(new BorderLayout());
        panel.add(hookBar, BorderLayout.NORTH);
        panel.add(new JScrollPane(hooksList), BorderLayout.CENTER);
        return panel;
    }

    public void appendLog(String line) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(line + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public void setScript(String script) {
        SwingUtilities.invokeLater(() -> {
            scriptArea.setText(script == null ? "" : script);
            scriptArea.setCaretPosition(0);
        });
    }

    public void appendScript(String script) {
        SwingUtilities.invokeLater(() -> {
            String current = scriptArea.getText();
            if (current == null || current.isEmpty()) {
                scriptArea.setText(script == null ? "" : script);
            } else {
                scriptArea.append("\n\n// ---- Additional hook ----\n\n");
                scriptArea.append(script == null ? "" : script);
            }
            scriptArea.setCaretPosition(scriptArea.getDocument().getLength());
        });
    }

    public void updateHooks(List<HookRecord> hooks) {
        SwingUtilities.invokeLater(() -> {
            hooksModel.clear();
            if (hooks != null) {
                for (HookRecord hook : hooks) {
                    hooksModel.addElement(hook);
                }
            }
        });
    }

    public void clearLog() {
        SwingUtilities.invokeLater(() -> logArea.setText(""));
    }

    public void clearAll() {
        SwingUtilities.invokeLater(() -> {
            logArea.setText("");
            scriptArea.setText("");
            hooksModel.clear();
        });
    }

    public void setSessionActive(boolean active) {
        SwingUtilities.invokeLater(() -> {
            if (connectionPanel != null) {
                connectionPanel.setSessionActive(active);
            }
        });
    }

    private java.awt.Component buildConsolePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> clearLog());
        JButton copyLog = new JButton("Copy Log");
        copyLog.addActionListener(e -> copyToClipboard(logArea.getText()));
        toolBar.add(clearButton);
        toolBar.add(copyLog);
        panel.add(toolBar, BorderLayout.NORTH);
        panel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        return panel;
    }

    private java.awt.Component buildScriptPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        JButton copyScript = new JButton("Copy Script");
        copyScript.addActionListener(e -> copyToClipboard(scriptArea.getText()));
        toolBar.add(copyScript);
        panel.add(toolBar, BorderLayout.NORTH);
        panel.add(new JScrollPane(scriptArea), BorderLayout.CENTER);
        return panel;
    }

    private java.awt.Component buildInfoPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(24, 24, 24, 24));
        DefaultTableModel model = new DefaultTableModel(new Object[]{"Field", "Value"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        model.addRow(new Object[]{"Author", "Ahmed Ezzat (BitTheByte)"});
        model.addRow(new Object[]{"Project", "https://github.com/BitTheByte/Jarida"});
        model.addRow(new Object[]{"Version", version == null ? "unknown" : version});
        javax.swing.JTable table = new javax.swing.JTable(model);
        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        table.setEnabled(false);
        table.setFocusable(false);
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        table.getColumnModel().getColumn(0).setCellRenderer(center);
        table.getColumnModel().getColumn(1).setCellRenderer(center);
        DefaultTableCellRenderer header = (DefaultTableCellRenderer) table.getTableHeader().getDefaultRenderer();
        header.setHorizontalAlignment(SwingConstants.CENTER);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JPanel centerPanel = new JPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.weighty = 1;
        c.anchor = java.awt.GridBagConstraints.CENTER;
        c.fill = java.awt.GridBagConstraints.NONE;
        scroll.setPreferredSize(new java.awt.Dimension(520, 140));
        centerPanel.add(scroll, c);
        panel.add(centerPanel, BorderLayout.CENTER);
        return panel;
    }

    public void setConnectionVisible(boolean visible) {
        // Connection tab is always visible; indicator is updated via setSessionActive().
        if (connectionPanel == null) {
            return;
        }
        if (!connectionVisible) {
            SwingUtilities.invokeLater(() -> {
                if (!connectionVisible) {
                    tabs.insertTab("Connection", null, connectionPanel, null, 0);
                    connectionVisible = true;
                }
            });
        }
    }

    public void selectConnectionTab() {
        if (connectionPanel == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (tabs.indexOfComponent(connectionPanel) >= 0) {
                tabs.setSelectedComponent(connectionPanel);
            }
        });
    }

    private void copyToClipboard(String text) {
        try {
            StringSelection selection = new StringSelection(text == null ? "" : text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void loadSettings() {
        // No persistent UI settings required.
    }

    @Override
    public boolean supportsQuickTabs() {
        return false;
    }

    @Override
    public void dispose() {
        super.dispose();
    }
}
