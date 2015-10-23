# Separator

*Separator* is a simple demo project to illustrate different kinds of
[source separation](https://en.wikipedia.org/wiki/Source_separation).

In its initial form it supports a harmonic/percussive and a
foreground/background separation.

Harmonic/percussive separation can be used to extract drums and other
highly percussive sounds. It can also be used as a first processing step
towards extracting harmonic features like melody.

Foreground/background separation is interesting for extracting the
singing voice (e.g. of pop songs).

The algorithms used are similar to:

- [Music/Voice separation using the similarity matrix](http://ismir2012.ismir.net/event/papers/583_ISMIR_2012.pdf)
by Zafar Rafii and Bryan Pardo
- [Harmonic/percussive separation using median filtering](http://dafx10.iem.at/papers/DerryFitzGerald_DAFx10_P15.pdf)
by Derry FitzGerald

*Separator* itself is built based on the audio feature extraction library
[jipes](http://www.tagtraum.com/jipes/).


## How to Build

*Separator* can be build using [Maven](https://maven.apache.org).
Simply run

    mvn clean install

and you will find multiple build results in the `target` directory.


## How to Run

*Separator* requires Java 8 to run.

Once you've built the project, you can run it with

    java -Xmx2G -jar target/separator-1.0.0-jar-with-dependencies.jar

The version number may need to be changed.
Note, that *Separator* is not really optimized. Therefore, lots of RAM/heap are useful.

To separate a file, simply drag it onto the running app, select options, and wait.
Results are placed in the same directory as the source file with suitable file name modifications.

In its original form, *Separator* only works with audio formats supported by the Java
platform (e.g. `WAV`). To use this with other audio formats, you need to add libraries
that support those formats to the class/library path
(see e.g. [Sampled SP libraries](http://www.tagtraum.com/sampledsp.html)).


## Separation Options

When running *Separator* you are presented a bunch of options:

- **Hop Size**: distance between windows in frames
- **Window Size**: analysis window
- **Harmonic Median Window**: Length of windows to include in the median computation for the h/p separation in ms
- **Percussive Median Window**: Frequency range of a window to include in the median computation for the h/p separation in Hz
- **Harm./Perc. Separation Harshness**: How is h/p median ratio mapped to h/p magnitudes? Extremes are binary and proportional.
  The implementation uses the [logistic function](https://en.wikipedia.org/wiki/Logistic_function) with a configurable `k`.
