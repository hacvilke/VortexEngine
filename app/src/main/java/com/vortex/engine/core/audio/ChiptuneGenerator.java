package com.vortex.engine.core.audio;

/**
 * Pure-Java 16-Step Polyphonic Chiptune & Synthwave Loop Synthesizer (22.05 kHz 16-bit PCM).
 * Shared identically by Android (AudioTrack) and Windows/Linux Desktop (javax.sound.sampled).
 *
 * Pattern encoding: 16 comma-separated integers (0..8), where:
 *   0 = rest, 1..8 = C Minor Pentatonic / Dorian scale notes across 2 octaves:
 *   [C4, Eb4, F4, G4, Bb4, C5, Eb5, G5]
 * Automatically synthesizes Lead Pulse Wave + Sub-Octave Triangle Bass + Kick/HiHat Drums!
 */
public class ChiptuneGenerator {

    public static final int SAMPLE_RATE = 22050;

    // C4, Eb4, F4, G4, Bb4, C5, Eb5, G5 (in Hz)
    public static final float[] SCALE_HZ = {
            0f,
            261.63f, 311.13f, 349.23f, 392.00f,
            466.16f, 523.25f, 622.25f, 783.99f
    };

    public static final String[] NOTE_LABELS = {
            "REST", "C4", "Eb4", "F4", "G4", "Bb4", "C5", "Eb5", "G5"
    };

    public static final String PRESET_CYBERPUNK = "1,0,6,4,3,0,5,4,1,0,8,6,5,4,3,2";
    public static final String PRESET_DUNGEON   = "1,2,4,1,0,5,4,2,1,2,6,5,4,0,2,1";
    public static final String PRESET_BOSS      = "6,6,0,5,6,8,0,6,4,4,0,3,4,5,3,2";
    public static final String PRESET_SKYCHILL  = "6,0,4,0,5,0,8,0,6,0,3,0,4,0,2,0";

    public static String resolvePresetOrCustom(String nameOrPattern) {
        if (nameOrPattern == null || nameOrPattern.trim().isEmpty()) return PRESET_CYBERPUNK;
        String u = nameOrPattern.trim().toUpperCase();
        switch (u) {
            case "CYBER":
            case "CYBERPUNK": return PRESET_CYBERPUNK;
            case "DUNGEON":   return PRESET_DUNGEON;
            case "BOSS":      return PRESET_BOSS;
            case "SKY":
            case "SKYCHILL":  return PRESET_SKYCHILL;
            default:
                return nameOrPattern.contains(",") ? nameOrPattern : PRESET_CYBERPUNK;
        }
    }

    public static int[] parseSteps(String pattern) {
        int[] steps = new int[16];
        String resolved = resolvePresetOrCustom(pattern);
        String[] parts = resolved.split(",");
        for (int i = 0; i < 16; i++) {
            if (i < parts.length) {
                try {
                    steps[i] = Math.max(0, Math.min(8, Integer.parseInt(parts[i].trim())));
                } catch (Exception ignored) {
                    steps[i] = 0;
                }
            }
        }
        return steps;
    }

    public static String encodeSteps(int[] steps) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            if (i > 0) sb.append(',');
            sb.append(i < steps.length ? Math.max(0, Math.min(8, steps[i])) : 0);
        }
        return sb.toString();
    }

    /**
     * Synthesizes a seamless 16-step polyphonic PCM loop (Lead + Bass + Kick/Hat).
     */
    public static short[] synthesizeLoop(String pattern, int bpm) {
        int clampedBpm = Math.max(70, Math.min(200, bpm));
        int[] steps = parseSteps(pattern);

        // 16 sixteenth-note steps = 4 beats
        double stepSec = (60.0 / clampedBpm) * 0.25;
        int samplesPerStep = Math.max(400, (int) (SAMPLE_RATE * stepSec));
        short[] pcm = new short[samplesPerStep * 16];

        double leadPhase = 0.0;
        double bassPhase = 0.0;
        long noiseSeed = 0x1234ABCDL;

        for (int s = 0; s < 16; s++) {
            int noteIdx = steps[s];
            float leadFreq = SCALE_HZ[noteIdx];
            // Driving synthwave root bass on every 2nd step
            int bassIdx = (steps[(s / 4) * 4] > 0) ? steps[(s / 4) * 4] : 1;
            float bassFreq = SCALE_HZ[bassIdx] * 0.5f;

            boolean isKick = (s % 4 == 0);
            boolean isHat = (s % 2 == 1);

            int baseOffset = s * samplesPerStep;
            for (int i = 0; i < samplesPerStep; i++) {
                float t = (float) i / samplesPerStep;
                float mix = 0f;

                // 1. Lead 25% Duty Pulse Wave with plucky envelope
                if (leadFreq > 1f) {
                    leadPhase += leadFreq / SAMPLE_RATE;
                    if (leadPhase >= 1.0) leadPhase -= 1.0;
                    float pulse = (leadPhase < 0.28) ? 0.55f : -0.35f;
                    float env = (1.0f - t * 0.72f) * 0.22f;
                    mix += pulse * env;
                }

                // 2. Sub-Octave Triangle Bass
                bassPhase += bassFreq / SAMPLE_RATE;
                if (bassPhase >= 1.0) bassPhase -= 1.0;
                float tri = (float) (4.0 * Math.abs(bassPhase - 0.5) - 1.0);
                float bassEnv = ((s % 2 == 0) ? (1.0f - t * 0.5f) : (0.6f - t * 0.4f)) * 0.20f;
                mix += tri * Math.max(0f, bassEnv);

                // 3. Punchy Synth Kick & Crisp Hi-Hat
                if (isKick && t < 0.45f) {
                    float kt = t / 0.45f;
                    float kf = 135f * (1.0f - kt * 0.72f);
                    mix += (float) Math.sin(2.0 * Math.PI * kf * (i / (double) SAMPLE_RATE)) * (1.0f - kt) * 0.24f;
                }
                if (isHat && t < 0.18f) {
                    noiseSeed = (noiseSeed * 1664525L + 1013904223L) & 0xFFFFFFFFL;
                    float wn = ((noiseSeed / (float) 0x80000000L) - 1.0f);
                    mix += wn * (1.0f - t / 0.18f) * 0.08f;
                }

                if (mix > 0.92f) mix = 0.92f;
                if (mix < -0.92f) mix = -0.92f;
                pcm[baseOffset + i] = (short) (mix * Short.MAX_VALUE);
            }
        }
        return pcm;
    }
}
