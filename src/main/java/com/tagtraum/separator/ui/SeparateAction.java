/*
 * =================================================
 * Copyright 2015 tagtraum industries incorporated
 * All rights reserved.
 * =================================================
 */
package com.tagtraum.separator.ui;

import com.tagtraum.jipes.math.Matrix;
import com.tagtraum.separator.dsp.BackgroundForegroundSeparation;
import com.tagtraum.separator.dsp.Channel;
import com.tagtraum.separator.dsp.HarmonicPercussiveSeparation;
import com.tagtraum.separator.dsp.Song;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SeparateAction.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class SeparateAction extends AbstractAction {

    private static final Logger LOG = Logger.getLogger(SeparateAction.class.getName());

    private static final ResourceBundle STRING_BUNDLE = ResourceBundle.getBundle("com/tagtraum/separator/ui/strings");
    private static final boolean MAC = System.getProperty("os.name").toLowerCase().contains("mac");
    private final DropAreaWindow dropAreaWindow;

    public SeparateAction(final DropAreaWindow dropAreaWindow) {
        putValue(Action.NAME, STRING_BUNDLE.getString("Separate"));
        putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_O,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        this.dropAreaWindow = dropAreaWindow;
    }

    @Override
    public void actionPerformed(final ActionEvent e) {
        final File file = showOpenFileDialog();
        if (file != null) {
            separate(file);
        }
    }

    public void separate(final File file) {
        // show options dialog.

        SwingUtilities.invokeLater(() -> {
            try {
                final OptionsDialog optionsDialog = new OptionsDialog(dropAreaWindow);
                optionsDialog.addActionListener(e -> {
                    final int hopSize = optionsDialog.getHopSize();
                    final int windowSize = optionsDialog.getWindowSize();
                    final int k = optionsDialog.getK();
                    final int harmonicWindow = optionsDialog.getHarmonicWindow();
                    final int percussiveWindow = optionsDialog.getPercussiveWindow();

                    LOG.info("hopSize=" + hopSize);
                    LOG.info("windowSize=" + windowSize);
                    LOG.info("harmonicWindow=" + harmonicWindow);
                    LOG.info("percussiveWindow=" + percussiveWindow);
                    LOG.info("k=" + k);

                    final JProgressBar progressBar = new JProgressBar(0, 100);
                    progressBar.setIndeterminate(true);
                    final Object[] options = {STRING_BUNDLE.getString("Cancel")};
                    final JOptionPane pane = new JOptionPane(progressBar, JOptionPane.INFORMATION_MESSAGE, JOptionPane.DEFAULT_OPTION);
                    pane.setOptions(options);
                    final JDialog dialog = pane.createDialog(dropAreaWindow, "Separating...");
                    dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                    dialog.setModal(true);

                    final Thread processor = new Thread(new Runnable() {

                        final ExecutorService pool = Executors.newFixedThreadPool(2);

                        private Future<Object> separateAsync(final Song song, final Function<Channel, Matrix> separationFunction, final File file1, final File file2) throws InterruptedException, ExecutionException {
                            return pool.submit(() -> {
                                final Song[] songParts = song.separate(separationFunction);
                                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                                final Future<Void> future1 = songParts[0].writeAsync(file1);
                                final Future<Void> future2 = songParts[1].writeAsync(file2);
                                future1.get();
                                future2.get();
                                return null;
                            });
                        }

                        @Override
                        public void run() {
                            final Thread t = Thread.currentThread();
                            Future<Object> bfFuture = null;
                            Future<Object> hpFuture = null;

                            try {
                                SwingUtilities.invokeLater(() -> {
                                    dialog.show();
                                    dialog.dispose();
                                    if (options[0].equals(pane.getValue())) {
                                        LOG.info("Cancellation!");
                                        t.interrupt();
                                    }
                                });
                                final String prefix = file.toString().replace(getFileExtension(file), "_");
                                final Song song = new Song();
                                song.setHopSizeInFrames(hopSize);
                                song.setSliceLengthInFrames(windowSize);
                                song.read(file);

                                final BackgroundForegroundSeparation bfSeparationFunction = new BackgroundForegroundSeparation();
                                final File bfFile1 = new File(prefix + "background.wav");
                                final File bfFile2 = new File(prefix + "foreground.wav");
                                bfFuture = separateAsync(song, bfSeparationFunction, bfFile1, bfFile2);

                                final HarmonicPercussiveSeparation hpSeparationFunction = new HarmonicPercussiveSeparation();
                                hpSeparationFunction.setHarmonicWindow(harmonicWindow);
                                hpSeparationFunction.setPercussiveWindow(percussiveWindow);
                                hpSeparationFunction.setK(k);

                                final File hpFile1 = new File(prefix + "percussive.wav");
                                final File hpFile2 = new File(prefix + "harmonic.wav");
                                hpFuture = separateAsync(song, hpSeparationFunction, hpFile1, hpFile2);

                                hpFuture.get();
                                bfFuture.get();
                            } catch (InterruptedException e) {
                                if (hpFuture != null) hpFuture.cancel(true);
                                if (bfFuture != null) bfFuture.cancel(true);
                            } catch (ExecutionException e) {
                                if (e.getCause() instanceof InterruptedException) {
                                    if (hpFuture != null) hpFuture.cancel(true);
                                    if (bfFuture != null) bfFuture.cancel(true);
                                } else {
                                    LOG.log(Level.SEVERE, e.toString(), e);
                                    SwingUtilities.invokeLater(() -> dialog.setVisible(false));
                                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(dropAreaWindow, e.getCause().toString(), STRING_BUNDLE.getString("Error"), JOptionPane.ERROR_MESSAGE));
                                }
                            } catch (Exception e) {
                                LOG.log(Level.SEVERE, e.toString(), e);
                                SwingUtilities.invokeLater(() -> dialog.setVisible(false));
                                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(dropAreaWindow, e.toString(), STRING_BUNDLE.getString("Error"), JOptionPane.ERROR_MESSAGE));
                            } finally {
                                SwingUtilities.invokeLater(() -> dialog.setVisible(false));
                                pool.shutdown();
                            }
                        }
                    }, "Processor");
                    processor.start();
                });

                optionsDialog.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private File showOpenFileDialog() {

        final File chosenFile;
        final int result;
        final String initialPath = System.getProperty("user.home");

        if (MAC) {
            // the Swing file chooser really does not work all that well on OS X
            final FileDialog dialog = new FileDialog(dropAreaWindow, STRING_BUNDLE.getString("Select_an_audio_file_to_separate"));
            dialog.setFilenameFilter((dir, name) -> {
                final File f = new File(dir, name);
                return f.isFile();
            });
            dialog.setDirectory(initialPath);
            dialog.setMode(FileDialog.LOAD);
            dialog.setMultipleMode(false);
            dialog.setVisible(true);

            dialog.getDirectory();
            result = dialog.getFile() == null
                    ? JFileChooser.CANCEL_OPTION
                    : JFileChooser.APPROVE_OPTION;
            chosenFile = result == JFileChooser.APPROVE_OPTION ? new File(dialog.getDirectory(), dialog.getFile()) : null;
        } else {
            final JFileChooser fileChooser = new JFileChooser(initialPath);
            fileChooser.setDialogTitle(STRING_BUNDLE.getString("Select_an_audio_file_to_separate"));
            fileChooser.setDialogType(JFileChooser.OPEN_DIALOG);
            fileChooser.setMultiSelectionEnabled(false);
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fileChooser.setFileFilter(new FileFilter() {
                @Override
                public boolean accept(final File f) {
                    return f.isFile();
                }

                @Override
                public String getDescription() {
                    return STRING_BUNDLE.getString("Audio_File");
                }
            });
            result = fileChooser.showDialog(dropAreaWindow, STRING_BUNDLE.getString("OK"));
            chosenFile = result == JFileChooser.APPROVE_OPTION
                    ? fileChooser.getSelectedFile()
                    : null;
        }
        return chosenFile;
    }

    private static String getFileExtension(final File file) {
        final String filename = file.toString();
        final int i = filename.lastIndexOf('.');
        if (i<0) return "";
        return filename.substring(i);
    }
}


