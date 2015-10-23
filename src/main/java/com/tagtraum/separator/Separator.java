/*
 * =================================================
 * Copyright 2015 tagtraum industries incorporated
 * All rights reserved.
 * =================================================
 */
package com.tagtraum.separator;

import com.tagtraum.separator.ui.DropAreaWindow;

import javax.swing.*;

/**
 * Main class. Creates a {@link DropAreaWindow}.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class Separator {

    private static final boolean MAC = System.getProperty("os.name").toLowerCase().contains("mac");

    public static void main(final String[] args) {
        SwingUtilities.invokeLater(() -> {
            if (MAC) {
                System.setProperty("apple.laf.useScreenMenuBar", "true");
            } else {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            final DropAreaWindow appWindow = new DropAreaWindow();
            appWindow.setVisible(true);
        });
    }
}
