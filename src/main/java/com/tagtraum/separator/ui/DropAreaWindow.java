/*
 * =================================================
 * Copyright 2015 tagtraum industries incorporated
 * All rights reserved.
 * =================================================
 */
package com.tagtraum.separator.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

import static java.util.Arrays.asList;

/**
 * DropAreaWindow.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class DropAreaWindow extends JFrame {

    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(DropAreaWindow.class);
    private static final ResourceBundle STRING_BUNDLE = ResourceBundle.getBundle("com/tagtraum/separator/ui/strings");
    private static final int DEFAULT_WIDTH = 400;
    private static final int DEFAULT_HEIGHT = 300;

    public DropAreaWindow() throws HeadlessException {
        super("Separator");
        this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setIconImages(asList(
                getImage("separator_16.png"),
                getImage("separator_32.png"),
                getImage("separator_64.png"),
                getImage("separator_128.png"),
                getImage("separator_256.png"),
                getImage("separator_512.png"),
                getImage("separator_1024.png")
        ));

        setupMenuBar();
        setupBounds();
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(new DropArea(), BorderLayout.CENTER);
    }

    private void setupMenuBar() {
        final JMenuBar menubar = new JMenuBar();
        setJMenuBar(menubar);
        final JMenu fileMenu = new JMenu(STRING_BUNDLE.getString("File"));
        fileMenu.add(new JMenuItem(new SeparateAction(this)));
        menubar.add(fileMenu);
    }

    private Image getImage(final String name) {
        return Toolkit.getDefaultToolkit().getImage(getClass().getResource(name));
    }

    private void setupBounds() {
        setMinimumSize(new Dimension(250, 200));
        final Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        final int defaultX = (screen.width - DEFAULT_WIDTH) / 2;
        final int defaultY = (screen.height - DEFAULT_HEIGHT) / 2;

        final int x = PREFERENCES.getInt("bounds.x", defaultX);
        final int y = PREFERENCES.getInt("bounds.y", defaultY);
        final int width = PREFERENCES.getInt("bounds.width", DEFAULT_WIDTH);
        final int height = PREFERENCES.getInt("bounds.height", DEFAULT_HEIGHT);
        setBounds(x, y, width, height);
        addComponentListener(new ComponentListener() {
            @Override
            public void componentResized(final ComponentEvent e) {
                PREFERENCES.putInt("bounds.width", e.getComponent().getWidth());
                PREFERENCES.putInt("bounds.height", e.getComponent().getHeight());
            }

            @Override
            public void componentMoved(final ComponentEvent e) {
                PREFERENCES.putInt("bounds.x", e.getComponent().getX());
                PREFERENCES.putInt("bounds.y", e.getComponent().getY());
            }

            @Override
            public void componentShown(final ComponentEvent e) {

            }

            @Override
            public void componentHidden(final ComponentEvent e) {

            }
        });
    }
}
