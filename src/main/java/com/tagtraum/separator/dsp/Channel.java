/*
 * =================================================
 * Copyright 2015 tagtraum industries incorporated
 * All rights reserved.
 * =================================================
 */
package com.tagtraum.separator.dsp;

import com.tagtraum.jipes.audio.AudioBuffer;
import com.tagtraum.jipes.audio.OLA;
import com.tagtraum.jipes.audio.RealAudioBuffer;
import com.tagtraum.jipes.math.AbstractMatrix;
import com.tagtraum.jipes.math.GriffinLim;
import com.tagtraum.jipes.math.Matrix;
import com.tagtraum.jipes.math.Transform;

import javax.sound.sampled.AudioFormat;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

/**
 * Channel. Multiple channels belong to one {@link Song}.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 * @see Song
 */
public class Channel {

    private final Matrix samples;
    private final Matrix magnitudes;
    private final Song song;
    private final OLA olaProcessor;

    public Channel(final Song song, final Matrix magnitudes, final Matrix samples) {
        this.magnitudes = magnitudes;
        this.samples = samples;
        this.song = song;
        this.olaProcessor = new OLA(song.getSliceLengthInFrames(), song.getHopSizeInFrames());
    }

    /**
     * Combines two matrices element-wise with the given operator.
     *
     * @param a Matrix a
     * @param b Matrix b
     * @param operator element-wise operator
     * @return result
     */
    public static Matrix apply(final Matrix a, final Matrix b, final DoubleBinaryOperator operator) {
        if (a.getNumberOfColumns() != b.getNumberOfColumns() || a.getNumberOfRows() != b.getNumberOfRows()) throw new IllegalArgumentException("Matrices must have same dimensions");
        return new AbstractMatrix() {

            @Override
            public float get(final int row, final int column) {
                return (float)operator.applyAsDouble(a.get(row, column), b.get(row, column));
            }

            @Override
            public int getNumberOfRows() {
                return a.getNumberOfRows();
            }

            @Override
            public int getNumberOfColumns() {
                return a.getNumberOfColumns();
            }

            @Override
            public boolean isZeroPadded() {
                return a.isZeroPadded();
            }

            @Override
            protected float get(final int index) {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * Apply the operator element-wise.
     *
     * @param m Matrix m
     * @param operator element-wise operator
     * @return result
     */
    private static Matrix apply(final Matrix m, final DoubleUnaryOperator operator) {
        return new AbstractMatrix() {

            @Override
            public float get(final int row, final int column) {
                return (float)operator.applyAsDouble(m.get(row, column));
            }

            @Override
            public int getNumberOfRows() {
                return m.getNumberOfRows();
            }

            @Override
            public int getNumberOfColumns() {
                return m.getNumberOfColumns();
            }

            @Override
            public boolean isZeroPadded() {
                return m.isZeroPadded();
            }

            @Override
            protected float get(final int index) {
                throw new UnsupportedOperationException();
            }
        };
    }

    public OLA getOlaProcessor() {
        return olaProcessor;
    }

    public Song getSong() {
        return song;
    }

    public Matrix getMagnitudes() {
        return magnitudes;
    }

    public Matrix getSamples() {
        return samples;
    }

    public AudioBuffer synthesize(final int row) {
        final float[] magBuffer = magnitudes.getRow(row);
        final AudioFormat songFormat = getSong().getAudioFormat();
        final AudioFormat audioFormat = new AudioFormat(
                songFormat.getEncoding(),
                songFormat.getSampleRate(),
                songFormat.getSampleSizeInBits(),
                1,
                songFormat.getFrameSize()/songFormat.getChannels(),
                songFormat.getFrameRate(),
                songFormat.isBigEndian()
        );
        final Transform griffinLim = new GriffinLim(samples.getRow(row), new float[magBuffer.length * 2], 5); // 5 griffin lim iterations?
        return new RealAudioBuffer(getSong().getHopSizeInFrames() * row, griffinLim.transform(magBuffer)[0], audioFormat);
        //return new RealAudioBuffer(getSong().getHopSizeInFrames() * row, samples.getRow(row), audioFormat);
    }

    /**
     * Separates this {@link Channel} into two channels using the given mask.
     *
     * @param mask mask as produced by {@link BackgroundForegroundSeparation} or {@link HarmonicPercussiveSeparation}
     * @return two channels - one is the inverse of the other
     */
    public Channel[] separate(final Matrix mask) {
        final Matrix inverseMask = apply(mask, (d) -> Math.abs(d - 1.0));
        return new Channel[]{
                new Channel(song, magnitudes.hadamardMultiply(mask), samples),
                new Channel(song, magnitudes.hadamardMultiply(inverseMask), samples)
        };
    }

}
