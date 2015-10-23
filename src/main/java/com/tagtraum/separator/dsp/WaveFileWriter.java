/*
 * =================================================
 * Copyright 2015 tagtraum industries incorporated
 * All rights reserved.
 * =================================================
 */
package com.tagtraum.separator.dsp;

import com.tagtraum.jipes.SignalProcessor;
import com.tagtraum.jipes.SignalProcessorSupport;
import com.tagtraum.jipes.audio.AudioBuffer;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * WaveFileWriter.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class WaveFileWriter implements SignalProcessor<AudioBuffer, AudioBuffer> {

    private static final Logger LOG = Logger.getLogger(WaveFileWriter.class.getName());

    private final SignalProcessorSupport<AudioBuffer> support = new SignalProcessorSupport<>();
    private final File file;
    private final File tempFile;
    private final DataOutputStream out;
    private final int channels;
    private AudioFormat audioFormat;

    public WaveFileWriter(final File file, final int channels) throws IOException {
        this.channels = channels;
        this.file = file;
        this.tempFile = File.createTempFile("wave", ".raw");
        this.tempFile.deleteOnExit();
        this.out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tempFile)));
    }

    @Override
    public void process(final AudioBuffer audioBuffer) throws IOException {
        final float[] data = audioBuffer.getRealData();
        if (audioFormat == null) {
            audioFormat = audioBuffer.getAudioFormat();
        }
        if (audioFormat.isBigEndian()) {
            for (final float s : data) {
                final short v = (short)Math.max(Short.MIN_VALUE+5, Math.min(s, Short.MAX_VALUE-5));
                out.write((v >>> 8) & 0xFF);
                out.write((v >>> 0) & 0xFF);
            }
        } else {
            for (final float s : data) {
                final short v = (short)Math.max(Short.MIN_VALUE+5, Math.min(s, Short.MAX_VALUE-5));
                out.write((v >>> 0) & 0xFF);
                out.write((v >>> 8) & 0xFF);
            }
        }
        support.process(audioBuffer);
    }

    @Override
    public void flush() throws IOException {
        this.out.flush();
        this.out.close();
        final AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                this.audioFormat.getSampleRate(),
                this.audioFormat.getSampleSizeInBits(),
                channels,
                this.audioFormat.getFrameSize(),
                this.audioFormat.getFrameRate(),
                this.audioFormat.isBigEndian());
        LOG.log(Level.INFO, "Writing " + file + " in format: " + format);
        final AudioInputStream in = new AudioInputStream(new FileInputStream(tempFile), format, tempFile.length()/format.getFrameSize());
        AudioSystem.write(in, AudioFileFormat.Type.WAVE, file);
        this.tempFile.delete();
        this.support.flush();
    }

    @Override
    public AudioBuffer getOutput() throws IOException {
        return null;
    }

    @Override
    public Object getId() {
        return "WaveFileWriter";
    }

    @Override
    public <O2> SignalProcessor<AudioBuffer, O2> connectTo(final SignalProcessor<AudioBuffer, O2> signalProcessor) {
        return support.connectTo(signalProcessor);
    }

    @Override
    public <O2> SignalProcessor<AudioBuffer, O2> disconnectFrom(final SignalProcessor<AudioBuffer, O2> signalProcessor) {
        return support.disconnectFrom(signalProcessor);
    }

    @Override
    public SignalProcessor<AudioBuffer, ?>[] getConnectedProcessors() {
        return support.getConnectedProcessors();
    }
}
