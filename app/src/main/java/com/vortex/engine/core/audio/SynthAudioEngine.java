package com.vortex.engine.core.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Procedural 16-bit PCM synthesizer for zero-asset arcade & 3D game sound effects on Android.
 * v4.3.0: Instantaneous pause+flush+stop+release cutoff on stopMusicLoop()/stopAll() and daemon threads.
 */
public class SynthAudioEngine {
    private static final int SAMPLE_RATE = 22050;
    private static final AtomicInteger THREAD_SEQ = new AtomicInteger(1);
    private final Map<String, short[]> clips = new HashMap<>();
    private final ExecutorService audioPool = Executors.newFixedThreadPool(3, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "Vortex-Android-SFX-" + THREAD_SEQ.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    });
    private volatile boolean muted = false;
    private AudioTrack musicTrack = null;
    private final Set<AudioTrack> activeSfxTracks = Collections.synchronizedSet(new HashSet<>());

    public SynthAudioEngine() {
        generatePresets();
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
        if (muted) {
            stopAll();
        }
    }

    public boolean isMuted() {
        return muted;
    }

    private void generatePresets() {
        clips.put("jump", synthSweep(210.0, 560.0, 0.16, 0.45, false));
        clips.put("coin", synthCoin(0.20));
        clips.put("laser", synthSweep(880.0, 170.0, 0.14, 0.40, true));
        clips.put("explosion", synthNoise(0.34, 0.55));
        clips.put("hit", synthSweep(180.0, 65.0, 0.12, 0.50, true));
        clips.put("powerup", synthArpeggio(new double[]{523.25, 659.25, 783.99, 1046.50}, 0.28));
    }

    private short[] synthSweep(double startHz, double endHz, double durationSec, double amp, boolean square) {
        int n = (int) (SAMPLE_RATE * durationSec);
        short[] pcm = new short[n];
        double phase = 0.0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            double hz = startHz + (endHz - startHz) * t;
            phase += 2.0 * Math.PI * hz / SAMPLE_RATE;
            double env = 1.0 - t;
            double wave = square ? (Math.sin(phase) >= 0 ? 0.8 : -0.8) : Math.sin(phase);
            pcm[i] = (short) (wave * env * amp * Short.MAX_VALUE);
        }
        return pcm;
    }

    private short[] synthCoin(double durationSec) {
        int n = (int) (SAMPLE_RATE * durationSec);
        short[] pcm = new short[n];
        double phase = 0.0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            double hz = t < 0.35 ? 987.77 : 1318.51;
            phase += 2.0 * Math.PI * hz / SAMPLE_RATE;
            double env = Math.pow(1.0 - t, 1.3);
            pcm[i] = (short) (Math.sin(phase) * env * 0.45 * Short.MAX_VALUE);
        }
        return pcm;
    }

    private short[] synthArpeggio(double[] notes, double durationSec) {
        int n = (int) (SAMPLE_RATE * durationSec);
        short[] pcm = new short[n];
        double phase = 0.0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            int idx = Math.min(notes.length - 1, (int) (t * notes.length));
            phase += 2.0 * Math.PI * notes[idx] / SAMPLE_RATE;
            double env = 1.0 - t * 0.75;
            pcm[i] = (short) (Math.sin(phase) * env * 0.42 * Short.MAX_VALUE);
        }
        return pcm;
    }

    private short[] synthNoise(double durationSec, double amp) {
        int n = (int) (SAMPLE_RATE * durationSec);
        short[] pcm = new short[n];
        Random rng = new Random(42L);
        double lp = 0.0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            double white = rng.nextDouble() * 2.0 - 1.0;
            lp = lp * 0.82 + white * 0.18;
            double env = Math.pow(1.0 - t, 1.8);
            pcm[i] = (short) (lp * env * amp * Short.MAX_VALUE);
        }
        return pcm;
    }

    private static void forceReleaseTrack(AudioTrack t) {
        if (t == null) return;
        try {
            t.pause();
            t.flush();
            t.stop();
        } catch (Throwable ignored) {
        }
        try {
            t.release();
        } catch (Throwable ignored) {
        }
    }

    public void play(String soundName) {
        if (muted || soundName == null || audioPool.isShutdown()) return;
        final short[] pcm = clips.get(soundName.toLowerCase().trim());
        if (pcm == null) return;

        audioPool.execute(() -> {
            AudioTrack track = null;
            try {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                AudioFormat format = new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build();
                track = new AudioTrack(
                        attrs,
                        format,
                        pcm.length * 2,
                        AudioTrack.MODE_STATIC,
                        AudioManager.AUDIO_SESSION_ID_GENERATE
                );
                activeSfxTracks.add(track);
                if (muted) return;
                track.write(pcm, 0, pcm.length);
                track.play();
                long sleepMs = (pcm.length * 1000L) / SAMPLE_RATE + 35L;
                Thread.sleep(sleepMs);
            } catch (Throwable ignored) {
            } finally {
                if (track != null) {
                    activeSfxTracks.remove(track);
                    forceReleaseTrack(track);
                }
            }
        });
    }

    public synchronized void startMusicLoop(String pattern, int bpm) {
        stopMusicLoop();
        if (muted) return;
        try {
            short[] pcm = ChiptuneGenerator.synthesizeLoop(pattern, bpm);
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();
            musicTrack = new AudioTrack(
                    attrs, format, pcm.length * 2,
                    AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE);
            musicTrack.write(pcm, 0, pcm.length);
            musicTrack.setLoopPoints(0, pcm.length, -1);
            musicTrack.play();
        } catch (Throwable ignored) {
        }
    }

    public synchronized void stopMusicLoop() {
        AudioTrack t = musicTrack;
        musicTrack = null;
        if (t != null) {
            try {
                t.setLoopPoints(0, 0, 0);
            } catch (Throwable ignored) {
            }
            forceReleaseTrack(t);
        }
    }

    public void stopAll() {
        stopMusicLoop();
        synchronized (activeSfxTracks) {
            for (AudioTrack t : activeSfxTracks) {
                forceReleaseTrack(t);
            }
            activeSfxTracks.clear();
        }
    }

    public void shutdown() {
        stopAll();
        audioPool.shutdownNow();
    }
}
