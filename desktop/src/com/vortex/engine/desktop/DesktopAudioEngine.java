package com.vortex.engine.desktop;

import com.vortex.engine.core.audio.ChiptuneGenerator;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * Pure Java Sound API (javax.sound.sampled) Low-Latency 22.05kHz Procedural Audio Engine
 * for Windows .exe & Linux Desktop builds.
 *
 * v4.3.0 Zero-Lag / Zero-Leak Architecture:
 *  - Uses a small 3072-byte (~69ms) hardware ring buffer with 512-byte (~11ms) write slices.
 *  - Tracks active music & SFX SourceDataLines so stopMusicLoop() / stopAll() calls
 *    line.stop() + line.flush() + line.close() IMMEDIATELY (< 2ms cutoff, zero trailing audio!).
 *  - Uses daemon worker threads so background audio threads never leak or block JVM shutdown.
 */
public class DesktopAudioEngine {

    private static final int SAMPLE_RATE = 22050;
    private static final AtomicInteger THREAD_SEQ = new AtomicInteger(1);

    private final ExecutorService pool = Executors.newFixedThreadPool(3, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "Vortex-SFX-" + THREAD_SEQ.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    });

    private volatile boolean muted = false;
    private final AtomicLong musicGeneration = new AtomicLong(0);
    private volatile Thread musicThread = null;
    private volatile SourceDataLine activeMusicLine = null;
    private final Set<SourceDataLine> activeSfxLines = Collections.synchronizedSet(new HashSet<>());

    public void setMuted(boolean muted) {
        this.muted = muted;
        if (muted) {
            stopAll();
        }
    }

    public boolean isMuted() {
        return muted;
    }

    public synchronized void startMusicLoop(final String pattern, final int bpm) {
        stopMusicLoop();
        if (muted) return;
        final long myGen = musicGeneration.incrementAndGet();

        musicThread = new Thread(() -> {
            SourceDataLine line = null;
            try {
                short[] samples = ChiptuneGenerator.synthesizeLoop(pattern, bpm);
                if (samples == null || samples.length == 0 || musicGeneration.get() != myGen || muted) return;

                byte[] pcm = new byte[samples.length * 2];
                for (int i = 0; i < samples.length; i++) {
                    pcm[i * 2] = (byte) (samples[i] & 0xFF);
                    pcm[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
                }

                AudioFormat fmt = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
                line = AudioSystem.getSourceDataLine(fmt);
                // Small hardware buffer (~69ms) prevents multi-second OS audio queue lag
                line.open(fmt, 3072);
                synchronized (DesktopAudioEngine.this) {
                    if (musicGeneration.get() != myGen || muted) {
                        line.close();
                        return;
                    }
                    activeMusicLine = line;
                }
                line.start();

                int offset = 0;
                final int slice = 512; // ~11.6ms slices for responsive loop & instant abort
                while (musicGeneration.get() == myGen && !muted && !Thread.currentThread().isInterrupted()) {
                    int len = Math.min(slice, pcm.length - offset);
                    int written = line.write(pcm, offset, len);
                    if (written <= 0 && musicGeneration.get() != myGen) break;
                    offset += Math.max(len, written);
                    if (offset >= pcm.length) offset = 0;
                }
            } catch (Throwable ignored) {
                // Gracefully ignore line closure during instant stop or headless environments
            } finally {
                forceCloseLine(line);
                synchronized (DesktopAudioEngine.this) {
                    if (activeMusicLine == line) {
                        activeMusicLine = null;
                    }
                }
            }
        }, "Vortex-Chiptune-Loop");
        musicThread.setDaemon(true);
        musicThread.start();
    }

    /**
     * Immediately halts and flushes the hardware audio buffer so background music
     * cuts off in < 2ms with zero trailing notes.
     */
    public synchronized void stopMusicLoop() {
        musicGeneration.incrementAndGet();
        SourceDataLine line = activeMusicLine;
        activeMusicLine = null;
        forceCloseLine(line);
        if (musicThread != null) {
            musicThread.interrupt();
            musicThread = null;
        }
    }

    /**
     * Immediately silences both background soundtrack and any playing SFX clips.
     */
    public void stopAll() {
        stopMusicLoop();
        synchronized (activeSfxLines) {
            for (SourceDataLine sfxLine : activeSfxLines) {
                forceCloseLine(sfxLine);
            }
            activeSfxLines.clear();
        }
    }

    public boolean isMusicActive() {
        SourceDataLine line = activeMusicLine;
        return line != null && line.isOpen();
    }

    private static void forceCloseLine(SourceDataLine line) {
        if (line == null) return;
        try {
            line.stop();
        } catch (Throwable ignored) {
        }
        try {
            line.flush();
        } catch (Throwable ignored) {
        }
        try {
            line.close();
        } catch (Throwable ignored) {
        }
    }

    public void play(final String presetName) {
        if (muted || presetName == null || pool.isShutdown()) return;
        pool.execute(() -> {
            SourceDataLine line = null;
            try {
                short[] samples = synthesize(presetName.trim().toLowerCase());
                byte[] pcm = new byte[samples.length * 2];
                for (int i = 0; i < samples.length; i++) {
                    pcm[i * 2] = (byte) (samples[i] & 0xFF);
                    pcm[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
                }
                AudioFormat fmt = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
                line = AudioSystem.getSourceDataLine(fmt);
                line.open(fmt, Math.min(4096, Math.max(1024, pcm.length)));
                activeSfxLines.add(line);
                if (muted) return;
                line.start();
                int off = 0;
                while (off < pcm.length && !muted && line.isOpen()) {
                    int n = Math.min(1024, pcm.length - off);
                    int w = line.write(pcm, off, n);
                    if (w <= 0) break;
                    off += w;
                }
                if (!muted && line.isOpen()) {
                    line.drain();
                }
            } catch (Throwable ignored) {
                // Gracefully ignore on headless CI servers without an audio card
            } finally {
                if (line != null) {
                    activeSfxLines.remove(line);
                    forceCloseLine(line);
                }
            }
        });
    }

    public void shutdown() {
        stopAll();
        pool.shutdownNow();
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
            last = last * 0.68f + white * 0.32f;
            float env = (1f - t) * (1f - t) * gain;
            out[i] = (short) (last * env * Short.MAX_VALUE);
        }
        return out;
    }
}
