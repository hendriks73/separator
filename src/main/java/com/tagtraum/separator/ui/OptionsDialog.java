/*
 * =================================================
 * Copyright 2015 tagtraum industries incorporated
 * All rights reserved.
 * =================================================
 */
package com.tagtraum.separator.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;

/**
 * OptionsDialog.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class OptionsDialog extends JDialog {

    private static final ResourceBundle STRING_BUNDLE = ResourceBundle.getBundle("com/tagtraum/separator/ui/strings");
    private final JSpinner hopSize;
    private final JSpinner windowSize;
    private final JButton ok;
    private final JButton cancel;
    private final JSpinner harmonicWindow;
    private final JSpinner percussiveWindow;
    private final JSpinner k; // logistic curve k

    public OptionsDialog(final Frame frame) {
        super(frame, STRING_BUNDLE.getString("Separation_Options"));
        setModal(true);

        this.hopSize = new JSpinner(new Power2SpinnerModel(512, 64, 2048));
        this.windowSize = new JSpinner(new Power2SpinnerModel(2048, 512, 2048 * 4));

        this.hopSize.setEditor(new JSpinner.NumberEditor(this.hopSize));


        this.hopSize.addChangeListener(e -> {
            final int window = getWindowSize();
            final int hop = getHopSize();
            if (window < hop) {
                windowSize.setValue(hop);
            }
        });
        this.windowSize.addChangeListener(e -> {
            final int window = getWindowSize();
            final int hop = getHopSize();
            if (window < hop) {
                windowSize.setValue(hop);
            }
        });

        this.harmonicWindow = new JSpinner(new SpinnerNumberModel(325, 20, 1000, 1));
        this.percussiveWindow = new JSpinner(new SpinnerNumberModel(1292, 20, 5000, 1));
        this.k = new JSpinner(new SpinnerNumberModel(10, 5, 50, 1));

        this.ok = new JButton(STRING_BUNDLE.getString("OK"));
        this.ok.setDefaultCapable(true);
        getRootPane().setDefaultButton(ok);
        this.cancel = new JButton(STRING_BUNDLE.getString("Cancel"));
        this.ok.addActionListener((e) -> dispose());
        this.cancel.addActionListener((e) -> dispose());
        final Container contentPane = getContentPane();
        if (contentPane instanceof JComponent) {
            ((JComponent) contentPane).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        }
        contentPane.setLayout(new GridBagLayout());

        final GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        contentPane.add(new JLabel(STRING_BUNDLE.getString("Hop_Size")), gbc);
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(hopSize, gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        contentPane.add(new JLabel(STRING_BUNDLE.getString("Window_Size")), gbc);
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(windowSize, gbc);


        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        contentPane.add(new JLabel(STRING_BUNDLE.getString("Harmonic_Window")), gbc);
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(harmonicWindow, gbc);
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.NONE;
        contentPane.add(new JLabel(STRING_BUNDLE.getString("Percussive_Window")), gbc);
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(percussiveWindow, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.fill = GridBagConstraints.NONE;
        contentPane.add(new JLabel(STRING_BUNDLE.getString("HP_Separation_Harshness")), gbc);
        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(k, gbc);

        final JPanel okCancelPanel = new JPanel();
        okCancelPanel.setLayout(new BoxLayout(okCancelPanel, BoxLayout.LINE_AXIS));
        okCancelPanel.add(Box.createHorizontalGlue());
        okCancelPanel.add(cancel);
        okCancelPanel.add(ok);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        gbc.weightx = 2;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        contentPane.add(okCancelPanel, gbc);

        this.setLocationRelativeTo(getOwner());
        pack();
    }

    public void addActionListener(final ActionListener listener) {
        ok.addActionListener(listener);
    }

    public int getWindowSize() {
        return (Integer)windowSize.getValue();
    }

    public int getHopSize() {
        return (Integer)hopSize.getValue();
    }

    public int getK() {
        return (Integer)k.getValue();
    }

    public int getHarmonicWindow() {
        return (Integer)harmonicWindow.getValue();
    }

    public int getPercussiveWindow() {
        return (Integer)percussiveWindow.getValue();
    }

    private static class Power2SpinnerModel extends SpinnerNumberModel {

        private int max;
        private int min;
        private int value;

        public Power2SpinnerModel(final int value, final int min, final int max) {
            this.min = min;
            this.max = max;
            this.value = value;
        }

        @Override
        public Object getValue() {
            return value;
        }

        @Override
        public void setValue(final Object value) {
            if ((value == null) || !(value instanceof Number)) {
                throw new IllegalArgumentException("illegal value");
            }
            if (!value.equals(this.value)) {
                this.value = (int) value;
                fireStateChanged();
            }
        }

        @Override
        public Object getNextValue() {
            return value == max ? value = max : value * 2;
        }

        @Override
        public Object getPreviousValue() {
            return value == min ? value = min : value / 2;
        }
    }
}
