package com.jarida.jadxfrida.ui;

import com.jarida.jadxfrida.model.ReturnPatchMode;
import com.jarida.jadxfrida.model.ReturnPatchRule;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public class ReturnValueRulePanel extends JPanel {
    private final JCheckBox enablePatch;
    private final JComboBox<ReturnPatchMode> mode;
    private final JTextField constantField;
    private final JTextArea expressionArea;
    private final JTextField conditionField;
    private final JTextField thenField;
    private final JTextField elseField;
    private final JTextArea scriptArea;
    private final CardLayout cardLayout;
    private final JPanel cardPanel;

    public ReturnValueRulePanel() {
        super(new BorderLayout());
        enablePatch = new JCheckBox("Enable patch", true);
        mode = new JComboBox<>(ReturnPatchMode.values());
        constantField = new JTextField();
        expressionArea = new JTextArea(5, 40);
        conditionField = new JTextField();
        thenField = new JTextField();
        elseField = new JTextField();
        scriptArea = new JTextArea(8, 40);

        JPanel header = new JPanel(new GridBagLayout());
        GridBagConstraints hc = new GridBagConstraints();
        hc.insets = new Insets(4, 4, 4, 4);
        hc.gridx = 0;
        hc.gridy = 0;
        hc.anchor = GridBagConstraints.WEST;
        header.add(new JLabel("Mode:"), hc);
        hc.gridx = 1;
        header.add(mode, hc);
        hc.gridx = 2;
        header.add(enablePatch, hc);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.add(buildConstantPanel(), ReturnPatchMode.CONSTANT.name());
        cardPanel.add(buildExpressionPanel(), ReturnPatchMode.EXPRESSION.name());
        cardPanel.add(buildConditionalPanel(), ReturnPatchMode.CONDITIONAL.name());
        cardPanel.add(buildScriptPanel(), ReturnPatchMode.SCRIPT.name());

        mode.addActionListener(e -> updateMode());
        enablePatch.addActionListener(e -> updateEnabledState());
        updateMode();
        updateEnabledState();

        add(header, BorderLayout.NORTH);
        add(cardPanel, BorderLayout.CENTER);
        setBorder(BorderFactory.createTitledBorder("Return Value Patching"));
    }

    private JPanel buildConstantPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel("Constant value (e.g. true, 0, \"abc\", null) or raw JS: prefix with js:/raw:"), BorderLayout.NORTH);
        panel.add(constantField, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildExpressionPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel("JavaScript snippet (use ret, args, thiz, Java, send, console). Example: return ret + 1;"), BorderLayout.NORTH);
        panel.add(new JScrollPane(expressionArea), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildConditionalPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("Condition (JS, use ret/args/thiz/Java):"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        panel.add(conditionField, c);
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Then value (JS expression):"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        panel.add(thenField, c);
        c.gridx = 0;
        c.gridy = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Else value (optional, JS expression):"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        panel.add(elseField, c);
        return panel;
    }

    private JPanel buildScriptPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel("Full JS function body. You can use ret, args, thiz, Java, send, console. Return undefined to keep original."), BorderLayout.NORTH);
        panel.add(new JScrollPane(scriptArea), BorderLayout.CENTER);
        return panel;
    }

    private void updateMode() {
        ReturnPatchMode selected = (ReturnPatchMode) mode.getSelectedItem();
        if (selected != null) {
            cardLayout.show(cardPanel, selected.name());
        }
    }

    private void updateEnabledState() {
        boolean enabled = enablePatch.isSelected();
        mode.setEnabled(enabled);
        constantField.setEnabled(enabled);
        expressionArea.setEnabled(enabled);
        conditionField.setEnabled(enabled);
        thenField.setEnabled(enabled);
        elseField.setEnabled(enabled);
        scriptArea.setEnabled(enabled);
    }

    public ReturnPatchRule toRule() {
        ReturnPatchRule rule = new ReturnPatchRule();
        rule.setEnabled(enablePatch.isSelected());
        ReturnPatchMode selected = (ReturnPatchMode) mode.getSelectedItem();
        if (selected != null) {
            rule.setMode(selected);
        }
        rule.setConstantValue(constantField.getText());
        rule.setExpression(expressionArea.getText());
        rule.setCondition(conditionField.getText());
        rule.setThenValue(thenField.getText());
        rule.setElseValue(elseField.getText());
        rule.setScriptBody(scriptArea.getText());
        return rule;
    }

    public void setEnabledDefault(boolean enable) {
        enablePatch.setSelected(enable);
        updateEnabledState();
    }

    public void setRule(ReturnPatchRule rule) {
        if (rule == null) {
            return;
        }
        enablePatch.setSelected(rule.isEnabled());
        ReturnPatchMode selected = rule.getMode();
        if (selected != null) {
            mode.setSelectedItem(selected);
        }
        constantField.setText(safe(rule.getConstantValue()));
        expressionArea.setText(safe(rule.getExpression()));
        conditionField.setText(safe(rule.getCondition()));
        thenField.setText(safe(rule.getThenValue()));
        elseField.setText(safe(rule.getElseValue()));
        scriptArea.setText(safe(rule.getScriptBody()));
        updateMode();
        updateEnabledState();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
