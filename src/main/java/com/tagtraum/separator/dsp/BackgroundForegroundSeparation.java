/*
 * =================================================
 * Copyright 2015 tagtraum industries incorporated
 * All rights reserved.
 * =================================================
 */
package com.tagtraum.separator.dsp;

import com.tagtraum.jipes.math.*;

import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Function that takes a channel as input argument and returns a (magnitude) mask.
 * The mask can be used to synthesize a signal from the original magnitude spectrum
 * that only contains a certain portion (background or foreground) of the original signal.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 * @see HarmonicPercussiveSeparation
 */
public class BackgroundForegroundSeparation implements Function<Channel, Matrix> {

    private static final Logger LOG = Logger.getLogger(BackgroundForegroundSeparation.class.getName());

    @Override
    public Matrix apply(final Channel channel) {
        final Song song = channel.getSong();

        final double bpm = 100.0;
        final double hopDurationMilliSecond = song.getHopSizeInFrames() * 1000.0 / song.getAudioFormat().getSampleRate();
        final double beatPerMilliSecond = bpm / 60.0 / 1000.0;
        final double beatsPerFrame = beatPerMilliSecond * hopDurationMilliSecond;
        final double framesPerBeat = 1.0 / beatsPerFrame;

        LOG.log(Level.FINE, "Frames per beat: " + framesPerBeat + " (assuming 100bpm)");

        final int minDistance = (int)(framesPerBeat * 2);
        final int maxDistance = minDistance * 10;
        final int maxSimilarRows = 10;

        LOG.log(Level.FINE, "MinDistance = " + minDistance);
        LOG.log(Level.FINE, "MaxDistance = " + maxDistance);

        final SelfSimilarityFunction selfSimilarityFunction = new SelfSimilarityFunction();
        selfSimilarityFunction.setBandwidth(maxDistance);

        final Matrix selfSimilarityMatrix = selfSimilarityFunction.apply(channel);
        final Matrix magnitudes = channel.getMagnitudes();

        final MutableMatrix mask = new FullMatrix(magnitudes.getNumberOfRows(), magnitudes.getNumberOfColumns(),
                new FloatBackingBuffer(true), false);
        final Set<Integer> maskSet = new HashSet<>();

        for (int row = 0; row < magnitudes.getNumberOfRows(); row++) {
            if (maskSet.contains(row)) continue;
            // find similar rows
            final List<SimilarRow> similarRows = new ArrayList<>();
            similarRows.add(new SimilarRow(row, 1));
            for (int otherRow = 0; otherRow < magnitudes.getNumberOfRows(); otherRow++) {
                final int distance = Math.abs(row - otherRow);
                if (distance < minDistance) continue;
                if (distance > maxDistance) continue;
                final float similarity = selfSimilarityMatrix.get(row, otherRow);
                if (similarity > 0) {
                    similarRows.add(new SimilarRow(otherRow, similarity));
                }
            }
            //Collections.sort(similarRows, (o1, o2) -> Integer.compare(o1.row, o2.row));
            //System.out.println("Similar: " + similarRows);

            Collections.sort(similarRows);
            Collections.reverse(similarRows);
            // trim similarities to maxSimilarRows
            while (similarRows.size() > maxSimilarRows) {
                similarRows.remove(similarRows.size() - 1);
            }
            //System.out.println("Top Sim: " + similarRows);
            // most similar is now top.
            // create a median magnitude frame from the top z magnitudes
            final float[] medians = new float[magnitudes.getNumberOfColumns()];
            for (int column = 0; column < magnitudes.getNumberOfColumns(); column++) {
                final float[] region = new float[similarRows.size()];
                for (int i = 0; i < region.length; i++) {
                    final int r = similarRows.get(i).row;
                    region[i] = magnitudes.get(r, column);
                }
                Arrays.sort(region);
                medians[column] = Floats.median(region);
            }
            // compute mask
            for (final SimilarRow similarRow : similarRows) {
                maskSet.add(similarRow.row);
                final float[] original = magnitudes.getRow(similarRow.row);
                float maskedPower = 0;
                float totalPower = 0;
                for (int column = 0; column < original.length; column++) {
                    //final float min = Math.min(medians[column], original[column]);
                    final float min = medians[column];
                    // TODO: Default to 0, if we don't have a magnitude? Does it matter at all?
                    float maskValue = original[column] == 0 ? 0 : min / original[column];
                    maskValue = Math.min(1, maskValue);
                    if (column < 5) maskValue = 1;
                /*
                if (maskValue < 0.25) maskValue = 0;
                if (maskValue > 0.75) maskValue = 1;
                */
                    mask.set(similarRow.row, column, maskValue);
                    final float power = original[column] * original[column];
                    maskedPower += maskValue * power;
                    totalPower += power;
                }
                similarRow.maskedPower = maskedPower / (totalPower == 0 ? 1 : totalPower);
            }
            // find lowest/highest maskedPower and make it
        /*
        Collections.sort(similarRows, (o1, o2) -> Float.compare(o2.maskedPower, o1.maskedPower));
        final SimilarRow leastMaskedPower = similarRows.get(0);
        final float[] leastMaskedPowerSamples = samples.getRow(leastMaskedPower.row);
        for (final SimilarRow similarRow : similarRows) {
            backForeMatrix.setRow(similarRow.row, leastMaskedPowerSamples);
        }
        */
        }
        return mask;
        /*
        final MutableMatrix mask2 = new FullMatrix(magnitudes.getNumberOfRows(), magnitudes.getNumberOfColumns(),
                new FloatBackingBuffer(true), false);
        mask2.fill(1f);
        return mask2;
        */
    }

    private static class SimilarRow implements Comparable<SimilarRow> {
        private int row;
        private float similarity;
        private float maskedPower;

        public SimilarRow(final int row, final float similarity) {
            this.row = row;
            this.similarity = similarity;
        }

        @Override
        public int compareTo(final SimilarRow o) {
            return Float.compare(similarity, o.similarity);
        }

        @Override
        public String toString() {
            return "SimilarRow{" +
                    "row=" + row +
                    ", sim=" + similarity +
                    ", maskedPow=" + maskedPower +
                    '}';
        }
    }

}
