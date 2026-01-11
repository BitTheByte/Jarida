package com.jarida.jadxfrida.ui;

import com.jarida.jadxfrida.model.HookRecord;
import jadx.gui.treemodel.JNode;
import jadx.gui.ui.panel.ContentPanel;
import jadx.gui.ui.tab.TabbedPane;

import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.JSplitPane;
import javax.swing.JLabel;
import javax.swing.JFileChooser;
import java.io.File;

public class FridaConsolePanel extends ContentPanel {
    private final JTextArea logArea;
    private final JTextArea scriptArea;
    private final Consumer<HookRecord> onRemoveHook;
    private final BiConsumer<HookRecord, Boolean> onSetHookActive;
    private final BiConsumer<List<HookRecord>, Boolean> onSetHooksActive;
    private final Consumer<HookRecord> onEditHook;
    private final Consumer<HookRecord> onJumpToHook;
    private final Runnable onRemoveAll;
    private final HookTableModel hooksModel = new HookTableModel();
    private final JTable hooksTable = new JTable(hooksModel);
    private final TableRowSorter<HookTableModel> hooksSorter = new TableRowSorter<>(hooksModel);
    private final JTextField hooksSearch = new JTextField(22);
    private JScrollPane hooksScroll;
    private final JaridaConnectionPanel connectionPanel;
    private final String version;
    private final JTabbedPane tabs;
    private boolean connectionVisible;
    private final Consumer<String> onCustomScriptsChanged;
    private final Consumer<String> onCustomScriptsSaved;
    private final CustomScriptsTableModel customScriptsModel = new CustomScriptsTableModel();
    private final JTable customScriptsTable = new JTable(customScriptsModel);

    public FridaConsolePanel(TabbedPane tabbedPane, JNode node, JaridaConnectionPanel connectionPanel,
                             String version,
                             Consumer<HookRecord> onRemoveHook,
                             BiConsumer<HookRecord, Boolean> onSetHookActive,
                             BiConsumer<List<HookRecord>, Boolean> onSetHooksActive,
                             Consumer<HookRecord> onEditHook, Runnable onRemoveAll,
                             Consumer<String> onCustomScriptsChanged, Consumer<String> onCustomScriptsSaved,
                             Consumer<HookRecord> onJumpToHook) {
        super(tabbedPane, node);
        this.connectionPanel = connectionPanel;
        this.version = version;
        this.onRemoveHook = onRemoveHook;
        this.onSetHookActive = onSetHookActive;
        this.onSetHooksActive = onSetHooksActive;
        this.onEditHook = onEditHook;
        this.onJumpToHook = onJumpToHook;
        this.onRemoveAll = onRemoveAll;
        this.hooksModel.setToggleHandler(onSetHookActive);
        this.onCustomScriptsChanged = onCustomScriptsChanged;
        this.onCustomScriptsSaved = onCustomScriptsSaved;
        this.customScriptsModel.addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                applyCustomScripts();
            }
        });
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
        hooksTable.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        hooksTable.setRowHeight(22);
        hooksTable.setFillsViewportHeight(true);
        hooksTable.setRowSorter(hooksSorter);
        hooksTable.getColumnModel().getColumn(0).setMaxWidth(90);
        hooksTable.getColumnModel().getColumn(0).setMinWidth(70);
        // Only two columns now: Enabled + Method

        JPopupMenu hooksMenu = new JPopupMenu();
        JMenuItem editItem = new JMenuItem("Edit");
        editItem.addActionListener(e -> editSelectedHook());
        JMenuItem navigateItem = new JMenuItem("Navigate");
        navigateItem.addActionListener(e -> jumpToSelectedHook());
        JMenuItem enableItem = new JMenuItem("Enable");
        enableItem.addActionListener(e -> setSelectedHooksActive(true));
        JMenuItem disableItem = new JMenuItem("Disable");
        disableItem.addActionListener(e -> setSelectedHooksActive(false));
        JMenuItem removeItem = new JMenuItem("Remove");
        removeItem.addActionListener(e -> removeSelectedHooks());

        hooksMenu.add(editItem);
        hooksMenu.add(navigateItem);
        hooksMenu.addSeparator();
        hooksMenu.add(enableItem);
        hooksMenu.add(disableItem);
        hooksMenu.add(removeItem);

        hooksTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowHookMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowHookMenu(e);
            }

            private void maybeShowHookMenu(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                hooksMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        });

        JButton enableAll = new JButton("Enable All");
        enableAll.addActionListener(e -> setAllHooksActive(true));
        JButton disableAll = new JButton("Disable All");
        disableAll.addActionListener(e -> setAllHooksActive(false));
        JButton removeAll = new JButton("Remove All");
        removeAll.addActionListener(e -> {
            if (onRemoveAll != null) {
                onRemoveAll.run();
            }
        });

        JPanel toolbar = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);
        c.gridy = 0;
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        toolbar.add(enableAll, c);
        c.gridx++;
        toolbar.add(disableAll, c);
        c.gridx++;
        toolbar.add(removeAll, c);
        c.gridx++;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        toolbar.add(new JPanel(), c);

        c.gridx++;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        toolbar.add(new javax.swing.JLabel("Search:"), c);
        c.gridx++;
        toolbar.add(hooksSearch, c);

        hooksSearch.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateHookFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateHookFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateHookFilter();
            }
        });

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(toolbar, BorderLayout.NORTH);
        hooksTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        hooksScroll = new JScrollPane(hooksTable);
        hooksScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        hooksScroll.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                updateHooksColumnWidth();
            }
        });
        panel.add(hooksScroll, BorderLayout.CENTER);
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

    public void setCustomScripts(String paths) {
        SwingUtilities.invokeLater(() -> customScriptsModel.setEntries(CustomScriptEntry.parse(paths)));
    }

    private void addCustomScriptFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File[] files = chooser.getSelectedFiles();
        if (files == null || files.length == 0) {
            return;
        }
        customScriptsModel.addFiles(files);
    }

    private void applyCustomScripts() {
        if (onCustomScriptsChanged == null) {
            return;
        }
        onCustomScriptsChanged.accept(customScriptsModel.serialize());
    }

    private void saveCustomScripts() {
        if (onCustomScriptsSaved == null) {
            return;
        }
        onCustomScriptsSaved.accept(customScriptsModel.serialize());
    }

    private List<CustomScriptEntry> getSelectedCustomScripts() {
        int[] rows = customScriptsTable.getSelectedRows();
        List<CustomScriptEntry> selected = new ArrayList<>();
        for (int row : rows) {
            int modelRow = customScriptsTable.convertRowIndexToModel(row);
            CustomScriptEntry entry = customScriptsModel.getEntryAt(modelRow);
            if (entry != null) {
                selected.add(entry);
            }
        }
        return selected;
    }

    private void setSelectedCustomScripts(boolean enabled) {
        List<CustomScriptEntry> selected = getSelectedCustomScripts();
        if (selected.isEmpty()) {
            return;
        }
        customScriptsModel.setEnabled(selected, enabled);
    }

    private void setAllCustomScripts(boolean enabled) {
        customScriptsModel.setEnabled(customScriptsModel.getEntries(), enabled);
    }

    private void removeSelectedCustomScripts() {
        List<CustomScriptEntry> selected = getSelectedCustomScripts();
        if (!selected.isEmpty()) {
            customScriptsModel.removeEntries(selected);
        }
    }

    private void updateHookFilter() {
        String text = hooksSearch.getText();
        if (text == null || text.trim().isEmpty()) {
            hooksSorter.setRowFilter(null);
            return;
        }
        String needle = text.trim().toLowerCase();
        hooksSorter.setRowFilter(new RowFilter<HookTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends HookTableModel, ? extends Integer> entry) {
                HookRecord record = hooksModel.getHookAt(entry.getIdentifier());
                if (record == null) {
                    return false;
                }
                String display = record.getDisplay();
                if (display != null && display.toLowerCase().contains(needle)) {
                    return true;
                }
                String status = record.isActive() ? "active" : "disabled";
                return status.contains(needle);
            }
        });
    }

    private List<HookRecord> getSelectedHooks() {
        int[] rows = hooksTable.getSelectedRows();
        List<HookRecord> selected = new ArrayList<>();
        for (int row : rows) {
            int modelRow = hooksTable.convertRowIndexToModel(row);
            HookRecord record = hooksModel.getHookAt(modelRow);
            if (record != null) {
                selected.add(record);
            }
        }
        return selected;
    }

    private void setSelectedHooksActive(boolean active) {
        List<HookRecord> selected = getSelectedHooks();
        if (selected.isEmpty()) {
            return;
        }
        applyHookActive(selected, active);
    }

    private void setAllHooksActive(boolean active) {
        applyHookActive(hooksModel.getHooks(), active);
    }

    private void applyHookActive(List<HookRecord> records, boolean active) {
        if (records == null || records.isEmpty()) {
            return;
        }
        if (onSetHooksActive != null) {
            onSetHooksActive.accept(records, active);
            return;
        }
        if (onSetHookActive == null) {
            return;
        }
        for (HookRecord record : records) {
            if (record != null && record.isActive() != active) {
                onSetHookActive.accept(record, active);
            }
        }
    }

    private void removeSelectedHooks() {
        List<HookRecord> selected = getSelectedHooks();
        for (HookRecord record : selected) {
            if (record != null && onRemoveHook != null) {
                onRemoveHook.accept(record);
            }
        }
    }

    private void editSelectedHook() {
        List<HookRecord> selected = getSelectedHooks();
        if (selected.isEmpty()) {
            showHookMessage("Select a hook to edit.");
            return;
        }
        if (selected.size() > 1) {
            showHookMessage("Select a single hook to edit.");
            return;
        }
        HookRecord record = selected.get(0);
        if (record != null && onEditHook != null) {
            onEditHook.accept(record);
        }
    }

    private void jumpToSelectedHook() {
        List<HookRecord> selected = getSelectedHooks();
        if (selected.isEmpty()) {
            showHookMessage("Select a hook to jump to.");
            return;
        }
        if (selected.size() > 1) {
            showHookMessage("Select a single hook to jump to.");
            return;
        }
        HookRecord record = selected.get(0);
        if (record != null && onJumpToHook != null) {
            onJumpToHook.accept(record);
        }
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
            hooksModel.setHooks(hooks);
            updateHooksColumnWidth();
        });
    }

    public void clearLog() {
        SwingUtilities.invokeLater(() -> logArea.setText(""));
    }

    public void clearAll() {
        SwingUtilities.invokeLater(() -> {
            logArea.setText("");
            scriptArea.setText("");
            hooksModel.setHooks(new java.util.ArrayList<>());
            updateHooksColumnWidth();
        });
    }

    public void setSessionActive(boolean active) {
        SwingUtilities.invokeLater(() -> {
            if (connectionPanel != null) {
                connectionPanel.setSessionActive(active);
            }
        });
    }

    private void showHookMessage(String message) {
        JOptionPane.showMessageDialog(this, message, "Jarida", JOptionPane.WARNING_MESSAGE);
    }

    private void updateHooksColumnWidth() {
        if (hooksTable.getColumnModel().getColumnCount() < 2) {
            return;
        }
        int viewportWidth = hooksScroll != null && hooksScroll.getViewport() != null
                ? hooksScroll.getViewport().getWidth()
                : hooksTable.getParent() != null ? hooksTable.getParent().getWidth() : 0;
        int enabledColWidth = hooksTable.getColumnModel().getColumn(0).getWidth();
        int minWidth = Math.max(0, viewportWidth - enabledColWidth);
        int maxTextWidth = 0;
        java.awt.FontMetrics fm = hooksTable.getFontMetrics(hooksTable.getFont());
        for (HookRecord record : hooksModel.getHooks()) {
            if (record == null) {
                continue;
            }
            String text = record.getDisplay();
            if (text == null) {
                continue;
            }
            maxTextWidth = Math.max(maxTextWidth, fm.stringWidth(text));
        }
        int desired = Math.max(minWidth, maxTextWidth);
        javax.swing.table.TableColumn methodCol = hooksTable.getColumnModel().getColumn(1);
        methodCol.setMinWidth(minWidth);
        methodCol.setPreferredWidth(desired);
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
        JPanel generatedPanel = new JPanel(new BorderLayout());
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        JButton copyScript = new JButton("Copy Script");
        copyScript.addActionListener(e -> copyToClipboard(scriptArea.getText()));
        toolBar.add(copyScript);
        generatedPanel.add(toolBar, BorderLayout.NORTH);
        generatedPanel.add(new JScrollPane(scriptArea), BorderLayout.CENTER);

        JPanel customPanel = new JPanel(new BorderLayout(6, 6));
        customPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        customScriptsTable.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        customScriptsTable.setRowHeight(22);
        customScriptsTable.setFillsViewportHeight(true);
        customScriptsTable.getColumnModel().getColumn(0).setMaxWidth(90);
        customScriptsTable.getColumnModel().getColumn(0).setMinWidth(70);

        JPanel header = new JPanel(new GridBagLayout());
        GridBagConstraints hc = new GridBagConstraints();
        hc.insets = new Insets(2, 2, 2, 2);
        hc.gridx = 0;
        hc.gridy = 0;
        hc.anchor = GridBagConstraints.WEST;
        header.add(new JLabel("Custom script files:"), hc);
        hc.gridx++;
        JButton addFiles = new JButton("Add Files");
        addFiles.addActionListener(e -> addCustomScriptFiles());
        header.add(addFiles, hc);
        hc.gridx++;
        JButton clearFiles = new JButton("Clear");
        clearFiles.addActionListener(e -> customScriptsModel.setEntries(new ArrayList<>()));
        header.add(clearFiles, hc);

        JButton removeSelected = new JButton("Remove Selected");
        removeSelected.addActionListener(e -> removeSelectedCustomScripts());
        JButton enableSelected = new JButton("Enable Selected");
        enableSelected.addActionListener(e -> setSelectedCustomScripts(true));
        JButton disableSelected = new JButton("Disable Selected");
        disableSelected.addActionListener(e -> setSelectedCustomScripts(false));
        JButton enableAll = new JButton("Enable All");
        enableAll.addActionListener(e -> setAllCustomScripts(true));
        JButton disableAll = new JButton("Disable All");
        disableAll.addActionListener(e -> setAllCustomScripts(false));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(enableSelected);
        controls.add(disableSelected);
        controls.add(removeSelected);
        controls.add(enableAll);
        controls.add(disableAll);

        JPanel pathsPanel = new JPanel(new BorderLayout());
        pathsPanel.add(header, BorderLayout.NORTH);
        pathsPanel.add(new JScrollPane(customScriptsTable), BorderLayout.CENTER);
        pathsPanel.add(controls, BorderLayout.SOUTH);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton saveCustom = new JButton("Save Custom Scripts");
        saveCustom.addActionListener(e -> saveCustomScripts());
        actions.add(saveCustom);

        customPanel.add(pathsPanel, BorderLayout.CENTER);
        customPanel.add(actions, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, generatedPanel, customPanel);
        split.setResizeWeight(0.6);
        split.setBorder(null);
        return split;
    }

    private java.awt.Component buildInfoPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(24, 24, 24, 24));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(6, 6, 6, 6);

        JLabel title = new JLabel("Jarida");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        panel.add(title, c);

        c.gridy++;
        JLabel versionLabel = new JLabel("Version:");
        versionLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        panel.add(versionLabel, c);
        c.gridx = 1;
        JLabel versionValue = new JLabel(version == null ? "unknown" : version);
        panel.add(versionValue, c);

        c.gridx = 0;
        c.gridy++;
        JLabel authorLabel = new JLabel("Author:");
        authorLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        panel.add(authorLabel, c);
        c.gridx = 1;
        panel.add(new JLabel("Ahmed Ezzat (BitTheByte)"), c);

        c.gridx = 0;
        c.gridy++;
        JLabel projectLabel = new JLabel("Project:");
        projectLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        panel.add(projectLabel, c);
        c.gridx = 1;
        panel.add(new JLabel("github.com/BitTheByte/Jarida"), c);

        return panel;
    }

    private static final class HookTableModel extends AbstractTableModel {
        private final String[] columns = {"Enabled", "Method"};
        private final List<HookRecord> hooks = new ArrayList<>();
        private BiConsumer<HookRecord, Boolean> toggleHandler;

        public void setToggleHandler(BiConsumer<HookRecord, Boolean> toggleHandler) {
            this.toggleHandler = toggleHandler;
        }

        public void setHooks(List<HookRecord> newHooks) {
            hooks.clear();
            if (newHooks != null) {
                hooks.addAll(newHooks);
            }
            fireTableDataChanged();
        }

        public List<HookRecord> getHooks() {
            return new ArrayList<>(hooks);
        }

        public HookRecord getHookAt(int row) {
            if (row < 0 || row >= hooks.size()) {
                return null;
            }
            return hooks.get(row);
        }

        @Override
        public int getRowCount() {
            return hooks.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0) {
                return Boolean.class;
            }
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            HookRecord record = getHookAt(rowIndex);
            if (record == null) {
                return "";
            }
            switch (columnIndex) {
                case 0:
                    return record.isActive();
                case 1:
                    return record.getDisplay();
                default:
                    return "";
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex != 0) {
                return;
            }
            HookRecord record = getHookAt(rowIndex);
            if (record == null) {
                return;
            }
            boolean desired = Boolean.TRUE.equals(aValue);
            if (record.isActive() != desired && toggleHandler != null) {
                toggleHandler.accept(record, desired);
            }
        }
    }

    private static final class CustomScriptEntry {
        final String path;
        boolean enabled;

        private CustomScriptEntry(String path, boolean enabled) {
            this.path = path == null ? "" : path.trim();
            this.enabled = enabled;
        }

        static List<CustomScriptEntry> parse(String raw) {
            List<CustomScriptEntry> entries = new ArrayList<>();
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
    }

    private static final class CustomScriptsTableModel extends AbstractTableModel {
        private final String[] columns = {"Enabled", "Path"};
        private final List<CustomScriptEntry> entries = new ArrayList<>();

        public void setEntries(List<CustomScriptEntry> items) {
            entries.clear();
            if (items != null) {
                entries.addAll(items);
            }
            fireTableDataChanged();
        }

        public List<CustomScriptEntry> getEntries() {
            return new ArrayList<>(entries);
        }

        public CustomScriptEntry getEntryAt(int row) {
            if (row < 0 || row >= entries.size()) {
                return null;
            }
            return entries.get(row);
        }

        public void addFiles(File[] files) {
            if (files == null || files.length == 0) {
                return;
            }
            java.util.Set<String> existing = new java.util.HashSet<>();
            for (CustomScriptEntry entry : entries) {
                existing.add(entry.path);
            }
            boolean changed = false;
            for (File file : files) {
                if (file == null) {
                    continue;
                }
                String path = file.getAbsolutePath();
                if (!existing.contains(path)) {
                    entries.add(new CustomScriptEntry(path, true));
                    existing.add(path);
                    changed = true;
                }
            }
            if (changed) {
                fireTableDataChanged();
            }
        }

        public void setEnabled(List<CustomScriptEntry> items, boolean enabled) {
            if (items == null || items.isEmpty()) {
                return;
            }
            boolean changed = false;
            for (CustomScriptEntry entry : items) {
                if (entry != null && entry.enabled != enabled) {
                    entry.enabled = enabled;
                    changed = true;
                }
            }
            if (changed) {
                fireTableDataChanged();
            }
        }

        public void removeEntries(List<CustomScriptEntry> items) {
            if (items == null || items.isEmpty()) {
                return;
            }
            if (entries.removeAll(items)) {
                fireTableDataChanged();
            }
        }

        public String serialize() {
            StringBuilder sb = new StringBuilder();
            for (CustomScriptEntry entry : entries) {
                if (entry == null || entry.path.isEmpty()) {
                    continue;
                }
                sb.append(entry.enabled ? "1|" : "0|")
                  .append(entry.path)
                  .append("\n");
            }
            return sb.toString().trim();
        }

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            CustomScriptEntry entry = getEntryAt(rowIndex);
            if (entry == null) {
                return "";
            }
            if (columnIndex == 0) {
                return entry.enabled;
            }
            return entry.path;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex != 0) {
                return;
            }
            CustomScriptEntry entry = getEntryAt(rowIndex);
            if (entry == null) {
                return;
            }
            boolean desired = Boolean.TRUE.equals(aValue);
            if (entry.enabled != desired) {
                entry.enabled = desired;
                fireTableRowsUpdated(rowIndex, rowIndex);
            }
        }
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
