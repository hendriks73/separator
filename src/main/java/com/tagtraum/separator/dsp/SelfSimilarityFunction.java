/*
 * =================================================
 * Copyright 2015 tagtraum industries incorporated
 * All rights reserved.
 * =================================================
 */
package com.tagtraum.separator.dsp;

import com.tagtraum.jipes.math.*;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.ToDoubleBiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

/**
 * Function that takes a channel as input argument and returns a (already somewhat processed) self
 * similarity matrix.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 * @see BackgroundForegroundSeparation
 */
public class SelfSimilarityFunction implements Function<Channel, Matrix> {

    private static final Logger LOG = Logger.getLogger(SelfSimilarityFunction.class.getName());

    // only let the upper third through
    public static final ToDoubleBiFunction<float[], float[]> FULL_COSINE_SIMILARITY = (float[] a, float[] b) -> Math.max(0, ((float)Floats.cosineSimilarity(a, b)) * 2 - 1);
    public static final ToDoubleBiFunction<float[], float[]> NORM_FULL_COSINE_SIMILARITY = (float[] a, float[] b) -> Math.max(0, ((float)Floats.dotProduct(a, b)) * 2 - 1);
    //public static final ToDoubleBiFunction<float[], float[]> HALF_COSINE_SIMILARITY = (float[] a, float[] b) -> Math.max(0, Floats.cosineSimilarity(a, b, 0, a.length/2) * 2 - 1);

    private ToDoubleBiFunction<float[], float[]> similarityFunction = NORM_FULL_COSINE_SIMILARITY;

    private int bandwidth = -1;

    public int getBandwidth() {
        return bandwidth;
    }

    public void setBandwidth(final int bandwidth) {
        this.bandwidth = bandwidth;
    }

    @Override
    public Matrix apply(final Channel channel) {

        LOG.log(Level.FINE, "Creating self-similarity matrix...");
        final Matrix normMatrix = normalizeRows(channel.getMagnitudes());
        final Matrix selfSimilarity = selfSimilarity(normMatrix, similarityFunction);
        LOG.log(Level.FINE, "Got view...");

        final int distanceInRows = bandwidth < 0 ? selfSimilarity.getNumberOfRows() : bandwidth;
        final Matrix medianMatrix = diagonalMedianMatrix(selfSimilarity, 10, distanceInRows); // length is hop size dependent!!
        LOG.log(Level.FINE, "Created diagonal median matrix...");
        final Matrix sharpenedMatrix = sharpenDiagonally(medianMatrix);
        LOG.log(Level.FINE, "Sharpened...");
        //final Matrix longPaths = maskShortDiagonals(sharpenedMatrix, 60); // length is hop size dependent!!
        //System.out.println("Removed short paths...");
        return sharpenedMatrix;
    }

    private Matrix normalizeRows(final Matrix m) {
        final FullMatrix normMatrix = new FullMatrix(m.getNumberOfRows(), m.getNumberOfColumns());
        IntStream.range(0, m.getNumberOfRows())
                .parallel()
                .forEach(
                        (row) -> {
                            final float[] r = m.getRow(row);
                            final float norm = (float) Floats.euclideanNorm(r);
                            if (norm != 0) {
                                for (int column = 0; column < m.getNumberOfColumns(); column++) {
                                    normMatrix.set(row, column, r[column] / norm);
                                }
                            }
                        }
                );
        return normMatrix;
    }


    /**
     * Creates a self-similarity view of the given matrix, interpreting rows as features vectors.
     *
     * @param m source matrix
     * @param similarityFunction similarity function
     * @return self-similarity matrix
     */
    private Matrix selfSimilarity(final Matrix m, final ToDoubleBiFunction<float[], float[]> similarityFunction) {
        return new AbstractSelfSimilarityMatrix(m) {
            @Override
            public float get(final int row, final int column) {
                return (float)similarityFunction.applyAsDouble(m.getRow(row), m.getRow(column));
            }

        };
    }

    /**
     * Creates a new matrix that contains diagonal medians.
     *
     * @param m source matrix, must be square
     * @param length the length of the median region is {@code length * 2 + 1}
     * @return new matrix with median values
     */
    public static Matrix diagonalMedianMatrix(final Matrix m, final int length, final int maxDistanceFromCenter) {
        final int size = m.getNumberOfColumns();
        if (m.getNumberOfColumns() != m.getNumberOfRows()) throw new IllegalArgumentException("Matrix must be square");
        final SymmetricBandMatrix medianMatrix = new SymmetricBandMatrix(m.getNumberOfColumns(), maxDistanceFromCenter*2+1, new FloatBackingBuffer(true), 0f, false);

        //for (int row=0; row<Math.min(size, maxDistanceFromCenter); row++) {
        //}
        IntStream.range(0, Math.min(size, maxDistanceFromCenter))
                .parallel()
                .forEach(
                        (row) -> {
                            // create a diagonal array, padded on each side
                            final int unpaddedLength = size - row;
                            final int paddedLength = unpaddedLength + 2 * length;
                            final float[] diagonal = new float[paddedLength];
                            for (int d = 0; d < unpaddedLength; d++) {
                                diagonal[d + length] = m.get(row + d, d);
                            }
                            // add padding by repeating first and last value
                            Arrays.fill(diagonal, 0, length, diagonal[length]);
                            Arrays.fill(diagonal, unpaddedLength + length, diagonal.length, diagonal[unpaddedLength + length - 1]);

                            for (int d = 0; d < unpaddedLength; d++) {
                                final float median = Floats.median(diagonal, d, 2 * length + 1);
                                medianMatrix.set(row + d, d, median);
                            }
                        }
                );

        return medianMatrix;
    }

    /**
     * Creates a view with sharpened diagonal.
     * This is done by setting all values to zero that are horizontally
     * or vertically adjacent to higher values.
     *
     * @param m square matrix
     * @return diagonally sharpened matrix
     */
    public static Matrix sharpenDiagonally(final Matrix m) {
        if (m.getNumberOfColumns() != m.getNumberOfRows()) throw new IllegalArgumentException("Matrix must be square");
        return new AbstractMatrix() {

            @Override
            public float get(final int row, final int column) {
                final int size = m.getNumberOfColumns();
                final float v = m.get(row, column);

                final float right = m.get(Math.min(size - 1, row + 1), column);
                if (right > v) return 0;
                final float left = m.get(Math.max(0, row - 1), column);
                if (left > v) return 0;
                final float top = m.get(row, Math.min(size - 1, column+1));
                if (top > v) return 0;
                final float bottom = m.get(row, Math.max(0, column-1));
                if (bottom > v) return 0;
                return m.get(row, column);
            }

            @Override
            public int getNumberOfRows() {
                return m.getNumberOfRows();
            }

            @Override
            public int getNumberOfColumns() {
                return m.getNumberOfRows();
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

    /**
     * Creates a view of the given matrix that masks diagonal paths that are shorter than the
     * given min length.
     *
     * @param m symmetric, square matrix
     * @param minLength min length of paths to "shine" through
     * @return masked view with long paths
     */
    public static Matrix maskShortDiagonals(final Matrix m, final int minLength) {
        if (m.getNumberOfColumns() != m.getNumberOfRows()) throw new IllegalArgumentException("Matrix must be square");
        // create a mask covering short path path with a sparse matrix
        // i.e. wherever the mask is 1, we return a zero.
        // otherwise we return the original value
        final SymmetricMatrix mask = new SymmetricMatrix(m.getNumberOfColumns(), new SparseBackingBuffer(0f), false);
        final int size = m.getNumberOfColumns();
        for (int y=0; y<size; y++) {
            int start = -1;
            for (int x = 0; x < size-y; x++) {
                final float v = m.get(x, y+x);
                if (start == -1 && v > 0) {
                    start = x;
                } else if (start != -1 && v == 0) {
                    final int length = x-start;
                    if (length < minLength) {
                        for (int x2=start; x2<=x; x2++) {
                            mask.set(x2, y+x2, 1);
                        }
                    }
                    start = -1;
                }
            }
        }
        return new AbstractMatrix() {

            @Override
            public float get(final int row, final int column) {
                return mask.get(row, column) == 1 ? 0 : m.get(row, column);
            }

            @Override
            public int getNumberOfRows() {
                return m.getNumberOfRows();
            }

            @Override
            public int getNumberOfColumns() {
                return m.getNumberOfRows();
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

    private abstract static class AbstractSelfSimilarityMatrix extends AbstractMatrix {

        private final Matrix m;

        public AbstractSelfSimilarityMatrix(final Matrix m) {
            this.m = m;
        }

        @Override
        public int getNumberOfRows() {
            return m.getNumberOfRows();
        }

        @Override
        public int getNumberOfColumns() {
            return m.getNumberOfRows();
        }

        @Override
        public boolean isZeroPadded() {
            return m.isZeroPadded();
        }

        @Override
        protected float get(final int index) {
            throw new UnsupportedOperationException();
        }
    }
}
