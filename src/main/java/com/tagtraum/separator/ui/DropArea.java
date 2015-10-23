/*
 * =================================================
 * Copyright 2015 tagtraum industries incorporated
 * All rights reserved.
 * =================================================
 */
package com.tagtraum.separator.ui;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import java.util.ResourceBundle;
import java.util.TooManyListenersException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DropArea.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class DropArea extends JLabel {

    private static final Logger LOG = Logger.getLogger(DropArea.class.getName());

    private static final ResourceBundle STRING_BUNDLE = ResourceBundle.getBundle("com/tagtraum/separator/ui/strings");

    public DropArea() {
        super(STRING_BUNDLE.getString("Drop_audio_file_here"));
        setFont(getFont().deriveFont(getFont().getSize2D() * 1.5f));
        setVerticalAlignment(SwingConstants.CENTER);
        setVerticalTextPosition(SwingConstants.BOTTOM);
        setHorizontalAlignment(SwingConstants.CENTER);
        setHorizontalTextPosition(SwingConstants.CENTER);

        setIcon(new ImageIcon(Toolkit.getDefaultToolkit().getImage(getClass().getResource("separator_64.png"))));
        setBorder(createAlphaBorder(0));
        setTransferHandler(new TransferHandler() {

            @Override
            public boolean canImport(final TransferSupport support) {
                support.setDropAction(COPY);
                support.setShowDropLocation(true);
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(final TransferSupport support) {
                final File file;
                try {
                    final List<File> fileList = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (fileList.size() != 1) return false;
                    file = fileList.get(0);
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, e.toString(), e);
                    return false;
                }

                final SeparateAction action = new SeparateAction((DropAreaWindow) SwingUtilities.windowForComponent(DropArea.this));
                action.separate(file);

                return true;
            }
        });
        setupDragIndicator();
    }

    private void setupDragIndicator() {
        try {
            getDropTarget().addDropTargetListener(new DropTargetListener() {
                @Override
                public void dragEnter(final DropTargetDragEvent dtde) {
                    // let's be a little bit fancy...
                    fadeBorder(0, 8);
                }

                @Override
                public void dragOver(final DropTargetDragEvent dtde) {

                }

                @Override
                public void dropActionChanged(final DropTargetDragEvent dtde) {

                }

                @Override
                public void dragExit(final DropTargetEvent dte) {
                    fadeBorder(255, -8);
                }

                @Override
                public void drop(final DropTargetDropEvent dtde) {
                    fadeBorder(255, -8);
                }

                private void fadeBorder(final int init, final int increment) {
                    final Timer timer = new Timer(10, new ActionListener() {
                        private int alpha = init;

                        @Override
                        public void actionPerformed(final ActionEvent e) {
                            DropArea.this.setBorder(createAlphaBorder(alpha));
                            alpha+=increment;
                            final Timer t = (Timer) e.getSource();
                            if (alpha>=255) {
                                DropArea.this.setBorder(createAlphaBorder(255));
                                t.stop();
                            }
                            if (alpha<=0) {
                                DropArea.this.setBorder(createAlphaBorder(0));
                                t.stop();
                            }
                        }
                    });
                    timer.setRepeats(true);
                    timer.setCoalesce(true);
                    timer.start();
                }
            });
        } catch (TooManyListenersException e) {
            LOG.log(Level.SEVERE, e.toString(), e);
        }
    }

    private Border createAlphaBorder(final int alpha) {
        return BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3),
                BorderFactory.createDashedBorder(new Color(getForeground().getRed(), getForeground().getGreen(), getForeground().getBlue(), alpha), 3f, 3, 3, true));
    }


}
