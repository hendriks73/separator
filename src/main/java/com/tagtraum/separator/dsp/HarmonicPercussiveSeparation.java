/*
 * =================================================
 * Copyright 2015 tagtraum industries incorporated
 * All rights reserved.
 * =================================================
 */
package com.tagtraum.separator.dsp;

import com.tagtraum.jipes.math.*;

import java.util.Arrays;
import java.util.function.DoubleBinaryOperator;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.IntStream;

/**
 * Function that takes a channel as input argument and returns a (magnitude) mask.
 * The mask can be used to synthesize a signal from the original magnitude spectrum
 * that only contains a certain portion (harmonic or percussive) of the original signal.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 * @see BackgroundForegroundSeparation
 */
public class HarmonicPercussiveSeparation implements Function<Channel, Matrix> {


    /*
    public static final DoubleBinaryOperator BINARY = (a, b) -> a > b ? 0 : 1;
    public static final DoubleBinaryOperator PROPORTIONALLY = (a, b) -> b/(a+b);
    public static final DoubleBinaryOperator TWO_THIRDS = (a, b) -> b/(a+b) > 0.66f ? 1f : 0f;
    public static final DoubleBinaryOperator LOGISTIC = (a, b) -> 1f / (1f + (float) Math.exp(-10f * (b/(a+b)-0.5f)));
    */

    private static final Logger LOG = Logger.getLogger(HarmonicPercussiveSeparation.class.getName());

    private int k = 10;
    private DoubleBinaryOperator magnitudesToMask = (a, b) -> 1f / (1f + (float) Math.exp(-k * (b/(a+b)-0.5f))); // Logistic function
    private long harmonicWindow = 325; // in milliseconds
    private long percussiveWindow = 1292; // in Hertz

    public DoubleBinaryOperator getMagnitudesToMask() {
        return magnitudesToMask;
    }

    public void setMagnitudesToMask(final DoubleBinaryOperator magnitudesToMask) {
        this.magnitudesToMask = magnitudesToMask;
    }

    public long getHarmonicWindow() {
        return harmonicWindow;
    }

    public void setHarmonicWindow(final long harmonicWindow) {
        this.harmonicWindow = harmonicWindow;
    }

    public long getPercussiveWindow() {
        return percussiveWindow;
    }

    public void setPercussiveWindow(final long percussiveWindow) {
        this.percussiveWindow = percussiveWindow;
    }

    public int getK() {
        return k;
    }

    public void setK(final int k) {
        this.k = k;
    }

    @Override
    public Matrix apply(final Channel channel) {
        final Song song = channel.getSong();
        final float sampleRate = song.getAudioFormat().getSampleRate();
        final int hopSizeInFrames = song.getHopSizeInFrames();
        final int sliceLengthInFrames = song.getSliceLengthInFrames();

        final float hopSizeInMilliseconds = hopSizeInFrames / sampleRate * 1000;
        final float harmonicRegionLength = harmonicWindow/hopSizeInMilliseconds;
        final float percussiveRegionLength = sliceLengthInFrames * percussiveWindow / sampleRate;
        final int percussiveL = toMedianL(percussiveRegionLength);
        final int harmonicL = toMedianL(harmonicRegionLength);
        LOG.info("Percussive l=" + percussiveL + ", harmonic l=" + harmonicL);

        final Matrix percussiveMedians = rowMedians(channel.getMagnitudes(), percussiveL);
        final Matrix harmonicMedians = columnMedians(channel.getMagnitudes(), harmonicL);

        return Channel.apply(harmonicMedians, percussiveMedians, magnitudesToMask);
    }

    private static int toMedianL(final float f) {
        return Math.round((f-1f)/2f);
    }

    /**
     * Creates a new matrix which contains row-wise medians from the source matrix.
     *
     * @param m source matrix
     * @param length median region is {@code length*2+1}
     * @return matrix with medians
     */
    private static Matrix rowMedians(final Matrix m, final int length) {
        final MutableMatrix medians = new FullMatrix(m.getNumberOfRows(), m.getNumberOfColumns(), new FloatBackingBuffer(true), false);
        final float[] paddedValues = new float[m.getNumberOfColumns()+ 2*length];

        IntStream.range(0, m.getNumberOfRows())
                .parallel()
                .forEach(
                        (row) -> {
                            final float[] values = m.getRow(row);
                            // values
                            System.arraycopy(values, 0, paddedValues, length, values.length);
                            // padding
                            Arrays.fill(paddedValues, 0, length, values[0]);
                            Arrays.fill(paddedValues, values.length+length, paddedValues.length, values[values.length-1]);
                            for (int column=0; column<m.getNumberOfColumns(); column++) {
                                medians.set(row, column, Floats.median(paddedValues, column, length * 2 + 1));
                            }
                        });
        return medians;
    }

    /**
     * Creates a new matrix which contains column-wise medians from the source matrix.
     *
     * @param m source matrix
     * @param length median region is {@code length*2+1}
     * @return matrix with medians
     */
    private static Matrix columnMedians(final Matrix m, final int length) {
        return rowMedians(m.transpose(), length).transpose();
    }

}
