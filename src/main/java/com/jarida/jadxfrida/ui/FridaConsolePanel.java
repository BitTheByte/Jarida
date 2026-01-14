package com.jarida.jadxfrida.ui;

import com.jarida.jadxfrida.model.HookRecord;
import jadx.gui.treemodel.JNode;
import jadx.gui.ui.panel.ContentPanel;
import jadx.gui.ui.tab.TabbedPane;

import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextPane;
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
import javax.swing.JScrollBar;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.text.DefaultCaret;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.JSplitPane;
import javax.swing.JLabel;
import javax.swing.JFileChooser;
import java.io.File;

public class FridaConsolePanel extends ContentPanel {
    private final JTextPane logArea;
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
    private JScrollPane logScroll;
    private volatile boolean autoScrollEnabled = true;
    private final AnsiState ansiState = new AnsiState();
    private final Map<AnsiStyleKey, AttributeSet> styleCache = new HashMap<>();
    private static final Color[] ANSI_COLORS = {
            new Color(0, 0, 0),
            new Color(170, 0, 0),
            new Color(0, 170, 0),
            new Color(170, 85, 0),
            new Color(0, 0, 170),
            new Color(170, 0, 170),
            new Color(0, 170, 170),
            new Color(170, 170, 170),
            new Color(85, 85, 85),
            new Color(255, 85, 85),
            new Color(85, 255, 85),
            new Color(255, 255, 85),
            new Color(85, 85, 255),
            new Color(255, 85, 255),
            new Color(85, 255, 255),
            new Color(255, 255, 255)
    };

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
        logArea = new JTextPane();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        DefaultCaret caret = (DefaultCaret) logArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);

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

    private static final int MAX_LOG_LENGTH = 2000000;

    public void appendLog(String line) {
        SwingUtilities.invokeLater(() -> {
            trimLogIfNeeded();
            appendAnsi(line + "\n");
            if (autoScrollEnabled) {
                SwingUtilities.invokeLater(this::scrollLogToBottom);
            }
        });
    }

    private void trimLogIfNeeded() {
        StyledDocument doc = logArea.getStyledDocument();
        int len = doc.getLength();
        if (len > MAX_LOG_LENGTH) {
            try {
                int removeLen = len - MAX_LOG_LENGTH + MAX_LOG_LENGTH / 4;
                doc.remove(0, removeLen);
            } catch (BadLocationException ignored) {
            }
        }
    }

    private void scrollLogToBottom() {
        JScrollBar bar = logScroll.getVerticalScrollBar();
        if (bar != null) {
            bar.setValue(bar.getMaximum());
        }
    }

    private void updateAutoScroll() {
        JScrollBar bar = logScroll.getVerticalScrollBar();
        if (bar == null || !bar.isVisible()) {
            autoScrollEnabled = true;
            return;
        }
        int value = bar.getValue();
        int extent = bar.getModel().getExtent();
        int max = bar.getMaximum();
        autoScrollEnabled = (value + extent >= max - 50);
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
        SwingUtilities.invokeLater(() -> {
            ansiState.reset();
            styleCache.clear();
            autoScrollEnabled = true;
            logArea.setText("");
        });
    }

    public void clearAll() {
        SwingUtilities.invokeLater(() -> {
            ansiState.reset();
            styleCache.clear();
            autoScrollEnabled = true;
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
        logScroll = new JScrollPane(logArea);
        // Detect user scrolling via mouse wheel
        logScroll.addMouseWheelListener(e -> {
            if (e.getWheelRotation() < 0) {
                // Scrolling up - disable auto-scroll
                autoScrollEnabled = false;
            } else {
                // Scrolling down - check if at bottom
                updateAutoScroll();
            }
        });
        // Detect user dragging scrollbar
        logScroll.getVerticalScrollBar().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                updateAutoScroll();
            }
        });
        panel.add(logScroll, BorderLayout.CENTER);
        return panel;
    }

    private void appendAnsi(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        StyledDocument doc = logArea.getStyledDocument();
        StringBuilder buffer = new StringBuilder();
        int idx = 0;
        while (idx < text.length()) {
            char ch = text.charAt(idx);
            if (ch == '\u001B' && idx + 1 < text.length() && text.charAt(idx + 1) == '[') {
                int end = text.indexOf('m', idx + 2);
                if (end == -1) {
                    buffer.append(ch);
                    idx++;
                    continue;
                }
                if (buffer.length() > 0) {
                    insertStyled(doc, buffer.toString());
                    buffer.setLength(0);
                }
                String codeStr = text.substring(idx + 2, end);
                applyAnsiCodes(codeStr);
                idx = end + 1;
            } else {
                buffer.append(ch);
                idx++;
            }
        }
        if (buffer.length() > 0) {
            insertStyled(doc, buffer.toString());
        }
    }

    private void insertStyled(StyledDocument doc, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        AttributeSet attrs = getAttributesForState();
        try {
            doc.insertString(doc.getLength(), text, attrs);
        } catch (BadLocationException ignored) {
        }
    }

    private AttributeSet getAttributesForState() {
        // Handle inverse mode by swapping fg and bg
        Color fg = ansiState.inverse ? ansiState.bg : ansiState.fg;
        Color bg = ansiState.inverse ? ansiState.fg : ansiState.bg;
        AnsiStyleKey key = new AnsiStyleKey(fg, bg, ansiState.bold, ansiState.italic,
                ansiState.underline, ansiState.strikethrough);
        AttributeSet cached = styleCache.get(key);
        if (cached != null) {
            return cached;
        }
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        Font font = logArea.getFont();
        if (font != null) {
            StyleConstants.setFontFamily(attrs, font.getFamily());
            StyleConstants.setFontSize(attrs, font.getSize());
        }
        StyleConstants.setBold(attrs, key.bold);
        StyleConstants.setItalic(attrs, key.italic);
        StyleConstants.setUnderline(attrs, key.underline);
        StyleConstants.setStrikeThrough(attrs, key.strikethrough);
        if (key.fg != null) {
            StyleConstants.setForeground(attrs, key.fg);
        }
        if (key.bg != null) {
            StyleConstants.setBackground(attrs, key.bg);
        }
        styleCache.put(key, attrs);
        return attrs;
    }

    private void applyAnsiCodes(String codeStr) {
        int[] codes = parseAnsiCodes(codeStr);
        applyAnsiCodes(codes);
    }

    private int[] parseAnsiCodes(String codeStr) {
        if (codeStr == null || codeStr.isEmpty()) {
            return new int[]{0};
        }
        String[] parts = codeStr.split(";");
        List<Integer> codes = new ArrayList<>();
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            try {
                codes.add(Integer.parseInt(part));
            } catch (NumberFormatException ignored) {
            }
        }
        if (codes.isEmpty()) {
            return new int[]{0};
        }
        int[] out = new int[codes.size()];
        for (int i = 0; i < codes.size(); i++) {
            out[i] = codes.get(i);
        }
        return out;
    }

    private void applyAnsiCodes(int[] codes) {
        int i = 0;
        if (codes == null || codes.length == 0) {
            ansiState.reset();
            return;
        }
        while (i < codes.length) {
            int code = codes[i];
            if (code == 0) {
                ansiState.reset();
            } else if (code == 1) {
                ansiState.bold = true;
            } else if (code == 22) {
                ansiState.bold = false;
            } else if (code == 3) {
                ansiState.italic = true;
            } else if (code == 23) {
                ansiState.italic = false;
            } else if (code == 4) {
                ansiState.underline = true;
            } else if (code == 24) {
                ansiState.underline = false;
            } else if (code == 7) {
                ansiState.inverse = true;
            } else if (code == 27) {
                ansiState.inverse = false;
            } else if (code == 9) {
                ansiState.strikethrough = true;
            } else if (code == 29) {
                ansiState.strikethrough = false;
            } else if (code == 39) {
                ansiState.fg = null;
            } else if (code == 49) {
                ansiState.bg = null;
            } else if (code == 38) {
                if (i + 1 < codes.length && codes[i + 1] == 5 && i + 2 < codes.length) {
                    ansiState.fg = xtermColor(codes[i + 2]);
                    i += 2;
                } else if (i + 1 < codes.length && codes[i + 1] == 2 && i + 4 < codes.length) {
                    ansiState.fg = new Color(clampColor(codes[i + 2]), clampColor(codes[i + 3]), clampColor(codes[i + 4]));
                    i += 4;
                }
            } else if (code == 48) {
                if (i + 1 < codes.length && codes[i + 1] == 5 && i + 2 < codes.length) {
                    ansiState.bg = xtermColor(codes[i + 2]);
                    i += 2;
                } else if (i + 1 < codes.length && codes[i + 1] == 2 && i + 4 < codes.length) {
                    ansiState.bg = new Color(clampColor(codes[i + 2]), clampColor(codes[i + 3]), clampColor(codes[i + 4]));
                    i += 4;
                }
            } else if (code >= 30 && code <= 37) {
                ansiState.fg = ANSI_COLORS[code - 30];
            } else if (code >= 90 && code <= 97) {
                ansiState.fg = ANSI_COLORS[code - 90 + 8];
            } else if (code >= 40 && code <= 47) {
                ansiState.bg = ANSI_COLORS[code - 40];
            } else if (code >= 100 && code <= 107) {
                ansiState.bg = ANSI_COLORS[code - 100 + 8];
            }
            i++;
        }
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static Color xtermColor(int value) {
        int idx = Math.max(0, Math.min(255, value));
        if (idx < 16) {
            return ANSI_COLORS[idx];
        }
        if (idx <= 231) {
            int c = idx - 16;
            int r = c / 36;
            int g = (c / 6) % 6;
            int b = c % 6;
            return new Color(toXtermComponent(r), toXtermComponent(g), toXtermComponent(b));
        }
        int gray = 8 + (idx - 232) * 10;
        return new Color(gray, gray, gray);
    }

    private static int toXtermComponent(int value) {
        if (value <= 0) {
            return 0;
        }
        return 55 + value * 40;
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

    private static final class AnsiState {
        private Color fg;
        private Color bg;
        private boolean bold;
        private boolean italic;
        private boolean underline;
        private boolean strikethrough;
        private boolean inverse;

        private void reset() {
            fg = null;
            bg = null;
            bold = false;
            italic = false;
            underline = false;
            strikethrough = false;
            inverse = false;
        }
    }

    private static final class AnsiStyleKey {
        private final Color fg;
        private final Color bg;
        private final boolean bold;
        private final boolean italic;
        private final boolean underline;
        private final boolean strikethrough;

        private AnsiStyleKey(Color fg, Color bg, boolean bold, boolean italic, boolean underline, boolean strikethrough) {
            this.fg = fg;
            this.bg = bg;
            this.bold = bold;
            this.italic = italic;
            this.underline = underline;
            this.strikethrough = strikethrough;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof AnsiStyleKey)) {
                return false;
            }
            AnsiStyleKey other = (AnsiStyleKey) obj;
            return bold == other.bold
                    && italic == other.italic
                    && underline == other.underline
                    && strikethrough == other.strikethrough
                    && Objects.equals(fg, other.fg)
                    && Objects.equals(bg, other.bg);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fg, bg, bold, italic, underline, strikethrough);
        }
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
