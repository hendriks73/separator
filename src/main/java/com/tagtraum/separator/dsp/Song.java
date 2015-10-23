/*
 * =================================================
 * Copyright 2015 tagtraum industries incorporated
 * All rights reserved.
 * =================================================
 */
package com.tagtraum.separator.dsp;

import com.tagtraum.jipes.AbstractSignalProcessor;
import com.tagtraum.jipes.SignalPipeline;
import com.tagtraum.jipes.SignalPump;
import com.tagtraum.jipes.audio.*;
import com.tagtraum.jipes.math.*;
import com.tagtraum.jipes.universal.Mapping;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED;
import static javax.sound.sampled.AudioSystem.NOT_SPECIFIED;

/**
 * Song. Each song consists of multiple {@link Channel}s.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 * @see Channel
 */
public class Song {

    private static final Logger LOG = Logger.getLogger(Song.class.getName());

    private int sliceLengthInFrames = 2048;
    private int hopSizeInFrames = 512;
    private AudioFormat audioFormat;
    private final List<Channel> channels;
    private WindowFunction windowFunction;

    public Song() {
        this.channels = new ArrayList<>();
    }

    public void read(final File file) throws IOException, UnsupportedAudioFileException {
        try (final AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(file)) {
            final AudioFileFormat originalFileFormat = AudioSystem.getAudioFileFormat(file);
            final AudioInputStream actualStream = get44_1kHzStereo16BitStream(audioInputStream);
            this.audioFormat = actualStream.getFormat();
            final AudioSignalSource source = new AudioSignalSource(actualStream);
            source.setNormalize(false);
            final InterleavedChannelSplit channelSplit = new InterleavedChannelSplit();
            for (int channel = 0; channel < audioFormat.getChannels(); channel++) {
                final SignalPipeline<AudioBuffer, ?> collectorPipeline = new SignalPipeline<>(
                        new SlidingWindow(sliceLengthInFrames, hopSizeInFrames),
                        new SamplesCollector(getChannelSamplesId(channel), getSliceLengthInFrames())
                );
                channelSplit.connectTo(channel, collectorPipeline);
            }

            final SignalPump<AudioBuffer> pump = new SignalPump<>(source);
            pump.add(channelSplit);
            final Map<Object, Object> results = pump.pump();

            IntStream.range(0, audioFormat.getChannels())
                    .parallel()
                    .forEach((channel) -> {
                        final Matrix samples = (Matrix) results.get(getChannelSamplesId(channel));
                        final Matrix magnitudes = samplesToMagnitudes(samples);
                        addChannel(new Channel(this, magnitudes, samples));
                        LOG.log(Level.INFO, "Read channel " + channel + " of " + file + ". Original audioformat=" + originalFileFormat.getFormat() + ", magnitudes=" + magnitudes);
                    });
        }    }

    public Song(final AudioFormat audioFormat, final List<Channel> channels, final WindowFunction function) {
        this.audioFormat = audioFormat;
        this.channels = channels;
        this.windowFunction = function;
    }

    private Matrix samplesToMagnitudes(final Matrix samples) {
        //windowFunction = new WindowFunction.Hamming(sliceLengthInFrames);
        final MutableMatrix magnitudes = new FullMatrix(samples.getNumberOfRows(), samples.getNumberOfColumns() / 2, new FloatBackingBuffer(true), false);
        // create magnitudes matrix
        final Transform fft = FFTFactory.getInstance().create(getSliceLengthInFrames());
        for (int row = 0; row < samples.getNumberOfRows(); row++) {
            final float[] audioSamples = samples.getRow(row);
            //final float[] windowed = windowFunction.map(audioSamples);
            final float[][] spectrum = fft.transform(audioSamples);
            for (int column = 0; column < magnitudes.getNumberOfColumns(); column++) {
                final float m = (float) Math.sqrt(spectrum[0][column] * spectrum[0][column] + spectrum[1][column] * spectrum[1][column]);
                magnitudes.set(row, column, m);
            }
        }
        return magnitudes;
    }

    private Object getChannelSamplesId(final int channel) {
        return "Channel" + channel;
    }

    public AudioFormat getAudioFormat() {
        return audioFormat;
    }

    public List<Channel> getChannels() {
        return channels;
    }

    private synchronized void addChannel(final Channel channel) {
        channels.add(channel);
    }

    public int getHopSizeInFrames() {
        return hopSizeInFrames;
    }

    public int getSliceLengthInFrames() {
        return sliceLengthInFrames;
    }

    public void setHopSizeInFrames(final int hopSizeInFrames) {
        this.hopSizeInFrames = hopSizeInFrames;
    }

    public void setSliceLengthInFrames(final int sliceLengthInFrames) {
        this.sliceLengthInFrames = sliceLengthInFrames;
    }

    /**
     * Write this song asynchronously to a file in {@code WAV} format.
     *
     * @param file file to write to
     * @see #write(File)
     */
    public Future<Void> writeAsync(final File file) {
        return ForkJoinPool.commonPool().submit(() -> {
            this.write(file);
            return null;
        });
    }

    /**
     * Write this song to a file in {@code WAV} format.
     *
     * @param file file to write to
     * @throws IOException if something goes wrong.
     * @see #writeAsync(File)
     */
    public void write(final File file) throws IOException {
        final int rows = channels.get(0).getMagnitudes().getNumberOfRows();
        final InterleavedChannelJoin channelJoin = new InterleavedChannelJoin(channels.size());
        final Mapping<AudioBuffer> scaler = new Mapping<>(buffer -> {
            final float[] scaledReal = buffer.getRealData().clone();
            //final float factor = hopSizeInFrames / (float) sliceLengthInFrames;
            final float factor = 0.2f;
            Floats.multiply(scaledReal, factor);
            return new RealAudioBuffer(buffer.getFrameNumber(), scaledReal, buffer.getAudioFormat());
        });
        channelJoin.connectTo(scaler).connectTo(new WaveFileWriter(file, channels.size()));

        // connect olaProcessors for each channel to the channelJoin
        //final Mapping<AudioBuffer> mapping = new Mapping<>(AudioBufferFunctions.createMapFunction(windowFunction));
        channels.stream().forEach(channel -> channel.getOlaProcessor()
                //.connectTo((SignalProcessor<AudioBuffer, AudioBuffer>) mapping)
                .connectTo(channelJoin));

        // synthesize buffers for each row of each channel and push them into the OLA processor
        // and thus into the joiner and writer
        for (int row=0; row<rows; row++) {
            final int r = row;
            channels.stream().forEach(channel -> {
                try {
                    channel.getOlaProcessor().process(channel.synthesize(r));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

        // flush
        channels.stream().forEach(channel -> {
            try {
                channel.getOlaProcessor().flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Separates this song into multiple songs, using the provided masking function (i.e. a function
     * that <em>produces</em> a masking {@link Matrix}).
     *
     * @param maskingFunction masking function
     * @return two songs
     */
    public Song[] separate(final Function<Channel, Matrix> maskingFunction) {
        return separate(channels.stream().map(maskingFunction).collect(Collectors.toList()));
    }

    private Song[] separate(final List<Matrix> masks) {
        if (masks.size() != channels.size()) throw new IllegalArgumentException();
        final List<Channel> aChannels = new ArrayList<>();
        final List<Channel> bChannels = new ArrayList<>();
        for (int i=0; i<channels.size(); i++) {
            final Channel channel = channels.get(i);
            final Matrix mask = masks.get(i);
            final Channel[] separateChannels = channel.separate(mask);
            aChannels.add(separateChannels[0]);
            bChannels.add(separateChannels[1]);
        }
        return new Song[] {
                new Song(audioFormat, aChannels, windowFunction),
                new Song(audioFormat, bChannels, windowFunction),
        };
    }

    private static class SamplesCollector extends AbstractSignalProcessor<AudioBuffer, Matrix> {

        private final int columns;
        private List<float[]> rows = new ArrayList<>();

        public SamplesCollector(final Object id, final int columns) {
            super(id);
            this.columns = columns;
        }

        @Override
        protected Matrix processNext(final AudioBuffer buffer) throws IOException {
            rows.add(buffer.getRealData().clone());
            return lastOut;
        }

        @Override
        public void flush() throws IOException {
            if (!rows.isEmpty()) {
                lastOut = new FullMatrix(rows.size(), columns, new FloatBackingBuffer(true), false);
                for (int row = 0; row < rows.size(); row++) {
                    ((MutableMatrix) lastOut).setRow(row, rows.get(row));
                }
                rows.clear();
            }
            super.flush();
        }
    }

    /**
     * Attempts to convert the input stream into signed PCM, 44.1kHz, Stereo, 16-bit/sample format.
     *
     * @param in input stream
     * @return specially transformed stream
     */
    private static AudioInputStream get44_1kHzStereo16BitStream(final AudioInputStream in) {
        AudioInputStream stream = in;
        try {
            AudioFormat streamFormat = stream.getFormat();
            // ensure signed pcm
            if (!PCM_SIGNED.equals(streamFormat.getEncoding())) {
                stream = AudioSystem.getAudioInputStream(PCM_SIGNED, stream);
            }
            // ensure 44100 Hz
            streamFormat = stream.getFormat();
            if (streamFormat.getSampleRate() != 44100f && streamFormat.getSampleRate() != NOT_SPECIFIED) {
                stream = AudioSystem.getAudioInputStream(new AudioFormat(
                        streamFormat.getEncoding(),
                        44100,
                        streamFormat.getSampleSizeInBits(),
                        streamFormat.getChannels(),
                        streamFormat.getFrameSize(),
                        44100,
                        streamFormat.isBigEndian(),
                        streamFormat.properties()
                ), stream);
            }
            // ensure 16 bit
            streamFormat = stream.getFormat();
            if (streamFormat.getSampleSizeInBits() != 16 && streamFormat.getSampleRate() != NOT_SPECIFIED) {
                stream = AudioSystem.getAudioInputStream(new AudioFormat(
                        streamFormat.getEncoding(),
                        streamFormat.getSampleRate(),
                        16,
                        streamFormat.getChannels(),
                        2 * streamFormat.getChannels(),
                        streamFormat.getFrameRate(),
                        streamFormat.isBigEndian(),
                        streamFormat.properties()
                ), stream);
            }
            // ensure stereo
            streamFormat = stream.getFormat();
            if (streamFormat.getChannels() != 2 && streamFormat.getSampleRate() != NOT_SPECIFIED) {
                stream = AudioSystem.getAudioInputStream(new AudioFormat(
                        streamFormat.getEncoding(),
                        streamFormat.getSampleRate(),
                        streamFormat.getSampleSizeInBits(),
                        2,
                        4,
                        streamFormat.getFrameRate(),
                        streamFormat.isBigEndian(),
                        streamFormat.properties()
                ), stream);
            }
            return stream;
        } catch (RuntimeException e) {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e1) {
                    LOG.log(Level.SEVERE, e1.toString(), e1);
                }
            }
            throw e;
        }
    }


    public static void main(final String[] args) throws IOException, UnsupportedAudioFileException {
        for (final String arg : args) {
            final String prefix = arg.replace(".wav", "_");
            final Song song = new Song();
            song.read(new File(arg));

            final Function<Channel, Matrix> harmonicPercussive = new HarmonicPercussiveSeparation();
            final Function<Channel, Matrix> backgroundForeground = new BackgroundForegroundSeparation();

            final Song[] bf = song.separate(backgroundForeground);

            final Song background = bf[0];
            final Song foreground = bf[1];
            background.write(new File(prefix + "background.wav"));
            foreground.write(new File(prefix + "foreground.wav"));

            final Song[] hp = song.separate(harmonicPercussive);
            hp[0].write(new File(prefix + "percussive.wav"));
            hp[1].write(new File(prefix + "harmonic.wav"));
        }
    }
}
