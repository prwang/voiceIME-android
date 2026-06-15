package dev.local.a11yimeprobe;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Minimal 16-bit PCM WAV writer (spec section 5). No external dependencies.
 */
public final class WavWriter {

    private WavWriter() {
    }

    public static void writePcm16(File out, byte[] pcm, int sampleRate, int channels)
            throws IOException {
        int bitsPerSample = 16;
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        int dataLen = pcm.length;
        int totalLen = 36 + dataLen;

        byte[] header = new byte[44];
        putString(header, 0, "RIFF");
        putIntLe(header, 4, totalLen);
        putString(header, 8, "WAVE");
        putString(header, 12, "fmt ");
        putIntLe(header, 16, 16);                 // PCM fmt chunk size
        putShortLe(header, 20, (short) 1);        // audio format = PCM
        putShortLe(header, 22, (short) channels);
        putIntLe(header, 24, sampleRate);
        putIntLe(header, 28, byteRate);
        putShortLe(header, 32, (short) blockAlign);
        putShortLe(header, 34, (short) bitsPerSample);
        putString(header, 36, "data");
        putIntLe(header, 40, dataLen);

        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(header);
            fos.write(pcm);
        }
    }

    private static void putString(byte[] b, int off, String s) {
        for (int i = 0; i < s.length(); i++) {
            b[off + i] = (byte) s.charAt(i);
        }
    }

    private static void putIntLe(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xff);
        b[off + 1] = (byte) ((v >> 8) & 0xff);
        b[off + 2] = (byte) ((v >> 16) & 0xff);
        b[off + 3] = (byte) ((v >> 24) & 0xff);
    }

    private static void putShortLe(byte[] b, int off, short v) {
        b[off] = (byte) (v & 0xff);
        b[off + 1] = (byte) ((v >> 8) & 0xff);
    }
}
