package com.vortex.engine.desktop;

import com.vortex.engine.core.audio.ChiptuneGenerator;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * Pure Java Sound API (javax.sound.sampled) 22.05kHz Procedural Retro SFX Synthesizer
 * for Windows .exe & Linux Desktop builds. Zero external audio files required!
 */
public class DesktopAudioEngine {

    private static final int SAMPLE_RATE = 22050;
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private volatile boolean muted = false;
    private volatile Thread musicThread = null;
    private volatile boolean musicRunning = false;

    public void setMuted(boolean muted) {
        this.muted = muted;
        if (muted) stopMusicLoop();
    }

    public synchronized void startMusicLoop(final String pattern, final int bpm) {
        stopMusicLoop();
        if (muted) return;
        musicRunning = true;
        musicThread = new Thread(() -> {
            try {
                short[] samples = ChiptuneGenerator.synthesizeLoop(pattern, bpm);
                byte[] pcm = new byte[samples.length * 2];
                for (int i = 0; i < samples.length; i++) {
                    pcm[i * 2] = (byte) (samples[i] & 0xFF);
                    pcm[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
                }
                AudioFormat fmt = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
                try (SourceDataLine line = AudioSystem.getSourceDataLine(fmt)) {
                    line.open(fmt, 8192);
                    line.start();
                    int offset = 0;
                    int chunk = 2048;
                    while (musicRunning && !muted) {
                        int len = Math.min(chunk, pcm.length - offset);
                        line.write(pcm, offset, len);
                        offset += len;
                        if (offset >= pcm.length) offset = 0;
                    }
                    line.stop();
                }
            } catch (Throwable ignored) {
            }
        }, "Vortex-Chiptune-Thread");
        musicThread.setDaemon(true);
        musicThread.start();
    }

    public synchronized void stopMusicLoop() {
        musicRunning = false;
        if (musicThread != null) {
            musicThread.interrupt();
            musicThread = null;
        }
    }

    public void play(final String presetName) {
        if (muted || presetName == null) return;
        pool.execute(() -> {
            try {
                short[] samples = synthesize(presetName.trim().toLowerCase());
                byte[] pcm = new byte[samples.length * 2];
                for (int i = 0; i < samples.length; i++) {
                    pcm[i * 2] = (byte) (samples[i] & 0xFF);
                    pcm[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
                }
                AudioFormat fmt = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
                try (SourceDataLine line = AudioSystem.getSourceDataLine(fmt)) {
                    line.open(fmt, Math.max(4096, pcm.length));
                    line.start();
                    line.write(pcm, 0, pcm.length);
                    line.drain();
                }
            } catch (Throwable ignored) {
                // Gracefully ignore on headless CI servers without an audio card
            }
        });
    }

    public static short[] synthesize(String type) {
        switch (type) {
            case "jump":      return sweepWave(220f, 640f, 0.16f, true);
            case "coin":      return coinChime();
            case "laser":     return sweepWave(920f, 170f, 0.13f, true);
            case "explosion": return noiseBurst(0.32f, 0.85f);
            case "hit":       return noiseBurst(0.11f, 0.65f);
            case "powerup":   return arpeggio(new float[]{330f, 440f, 554f, 660f}, 0.28f);
            default:          return sweepWave(440f, 520f, 0.09f, false);
        }
    }

    private static short[] sweepWave(float f0, float f1, float dur, boolean square) {
        int n = (int) (SAMPLE_RATE * dur);
        short[] out = new short[n];
        double phase = 0.0;
        for (int i = 0; i < n; i++) {
            float t = (float) i / n;
            float f = f0 + (f1 - f0) * t;
            phase += 2.0 * Math.PI * f / SAMPLE_RATE;
            float env = (1.0f - t) * 0.40f;
            double w = Math.sin(phase);
            if (square) w = w >= 0 ? 0.8 : -0.8;
            out[i] = (short) (w * env * Short.MAX_VALUE);
        }
        return out;
    }

    private static short[] coinChime() {
        int n = (int) (SAMPLE_RATE * 0.22f);
        short[] out = new short[n];
        int split = n / 3;
        double phase = 0.0;
        for (int i = 0; i < n; i++) {
            float f = (i < split) ? 987.77f : 1318.51f;
            phase += 2.0 * Math.PI * f / SAMPLE_RATE;
            float env = (1.0f - (float) i / n) * 0.42f;
            out[i] = (short) (Math.sin(phase) * env * Short.MAX_VALUE);
        }
        return out;
    }

    private static short[] arpeggio(float[] notes, float dur) {
        int n = (int) (SAMPLE_RATE * dur);
        short[] out = new short[n];
        double phase = 0.0;
        for (int i = 0; i < n; i++) {
            int idx = Math.min(notes.length - 1, (i * notes.length) / n);
            phase += 2.0 * Math.PI * notes[idx] / SAMPLE_RATE;
            float env = (1.0f - (float) i / n * 0.7f) * 0.40f;
            out[i] = (short) (Math.sin(phase) * env * Short.MAX_VALUE);
        }
        return out;
    }

    private static short[] noiseBurst(float dur, float gain) {
        int n = (int) (SAMPLE_RATE * dur);
        short[] out = new short[n];
        Random rng = new Random(99L);
        float last = 0f;
        for (int i = 0; i < n; i++) {
            float t = (float) i / n;
            float white = rng.nextFloat() * 2f - 1f;
            last = last * 0.72f + white * 0.28f;
            float env = (1.0f - t) * (1.0f - t) * gain * 0.55f;
            out[i] = (short) (last * env * Short.MAX_VALUE);
        }
        return out;
    }

    public void shutdown() {
        stopMusicLoop();
        pool.shutdownNow();
    }
}
