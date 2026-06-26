package com.intelli.webrunner.ui;

import com.intelli.webrunner.util.JwtTokenService;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public final class JwtDecoderPanel {
    private final JPanel root = new JPanel(new BorderLayout(0, 8));
    private final JLabel expiryLabel = new JLabel("Not exp field");
    private final JTextArea jwtField = createTextArea();
    private final JTextArea decodedField = createTextArea();
    private final JPasswordField secretField = new JPasswordField();
    private final JButton updateButton = new JButton("Update");

    public JwtDecoderPanel(Project project) {
        buildUi();
        attachListeners();
    }

    public JComponent getComponent() {
        return root;
    }

    private void buildUi() {
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(expiryLabel, BorderLayout.NORTH);

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(new JLabel("JWT"), BorderLayout.NORTH);
        inputPanel.add(new JBScrollPane(jwtField), BorderLayout.CENTER);

        JPanel outputPanel = new JPanel(new BorderLayout());
        outputPanel.add(new JLabel("Decoded JSON"), BorderLayout.NORTH);
        outputPanel.add(new JBScrollPane(decodedField), BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inputPanel, outputPanel);
        splitPane.setResizeWeight(0.5);
        root.add(splitPane, BorderLayout.CENTER);
        root.add(createSigningPanel(), BorderLayout.SOUTH);
    }

    private JPanel createSigningPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints label = new GridBagConstraints();
        label.gridx = 0;
        label.insets = new Insets(0, 0, 0, 8);
        panel.add(new JLabel("Secret"), label);

        GridBagConstraints field = new GridBagConstraints();
        field.gridx = 1;
        field.weightx = 1;
        field.fill = GridBagConstraints.HORIZONTAL;
        field.insets = new Insets(0, 0, 0, 8);
        panel.add(secretField, field);

        GridBagConstraints button = new GridBagConstraints();
        button.gridx = 2;
        panel.add(updateButton, button);
        return panel;
    }

    private void attachListeners() {
        jwtField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { updateDecoded(); }
            @Override public void removeUpdate(DocumentEvent event) { updateDecoded(); }
            @Override public void changedUpdate(DocumentEvent event) { updateDecoded(); }
        });
        updateButton.addActionListener(event -> updateToken());
    }

    private void updateDecoded() {
        decodedField.setText(JwtTokenService.decode(jwtField.getText()));
        expiryLabel.setText(JwtTokenService.expiryStatus(jwtField.getText()));
    }

    private void updateToken() {
        try {
            jwtField.setText(JwtTokenService.update(decodedField.getText(), new String(secretField.getPassword())));
        } catch (Exception e) {
            decodedField.setText("Unable to update JWT: " + e.getMessage());
        }
    }

    private static JTextArea createTextArea() {
        JTextArea field = new JTextArea();
        field.setLineWrap(true);
        field.setWrapStyleWord(false);
        field.setFont(new Font(Font.MONOSPACED, Font.PLAIN, field.getFont().getSize()));
        return field;
    }
}
