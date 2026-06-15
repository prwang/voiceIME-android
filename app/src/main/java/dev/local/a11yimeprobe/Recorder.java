package dev.local.a11yimeprobe;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Locale;

/**
 * Hold-to-record helper (spec section 9.2). PCM 16 kHz mono 16-bit, recorded on
 * a background thread, written to an app-private WAV. Computes duration, byte
 * count, RMS and peak. No audio data is logged.
 */
public final class Recorder {

    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private final Context context;

    private AudioRecord record;
    private Thread thread;
    private volatile boolean running = false;
    private ByteArrayOutputStream buffer;
    private long startMs;
    private int minBufferSize;
    private int usedSource;

    public Recorder(Context context) {
        this.context = context;
    }

    public static final class Result {
        public long durationMs;
        public int bytes;
        public int sampleRate;
        public int minBufferSize;
        public double rms;
        public int peakAbs;
        public int source;
        public String wavPath;

        public String describe() {
            return "AUDIO_RESULT duration_ms=" + durationMs
                    + " bytes=" + bytes
                    + " sample_rate=" + sampleRate
                    + " min_buffer_size=" + minBufferSize
                    + " rms=" + String.format(Locale.US, "%.1f", rms)
                    + " peak_abs=" + peakAbs
                    + " source=" + source
                    + " wav_path=" + wavPath;
        }
    }

    public synchronized void start(SessionState session) {
        if (running) {
            return;
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ProbeLog.w("Audio", "AUDIO_NO_PERMISSION session=" + session.sessionId);
            return;
        }

        minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING);
        if (minBufferSize <= 0) {
            minBufferSize = SAMPLE_RATE * 2;
        }
        int bufSize = minBufferSize * 2;

        record = newRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, bufSize);
        usedSource = MediaRecorder.AudioSource.VOICE_RECOGNITION;
        if (record == null || record.getState() != AudioRecord.STATE_INITIALIZED) {
            if (record != null) {
                record.release();
            }
            record = newRecord(MediaRecorder.AudioSource.MIC, bufSize);
            usedSource = MediaRecorder.AudioSource.MIC;
        }
        if (record == null || record.getState() != AudioRecord.STATE_INITIALIZED) {
            ProbeLog.e("Audio", "AUDIO_INIT_FAILED session=" + session.sessionId);
            if (record != null) {
                record.release();
                record = null;
            }
            return;
        }

        buffer = new ByteArrayOutputStream();
        running = true;
        startMs = SystemClock.elapsedRealtime();
        record.startRecording();
        ProbeLog.i("Audio", "AUDIO_START session=" + session.sessionId
                + " source=" + usedSource + " min_buffer_size=" + minBufferSize);

        thread = new Thread(this::loop, "probe-recorder");
        thread.start();
    }

    private AudioRecord newRecord(int source, int bufSize) {
        try {
            return new AudioRecord(source, SAMPLE_RATE, CHANNEL, ENCODING, bufSize);
        } catch (Exception e) {
            ProbeLog.e("Audio", "AUDIO_CREATE_FAIL source=" + source);
            return null;
        }
    }

    private void loop() {
        byte[] buf = new byte[minBufferSize];
        while (running) {
            int n = record.read(buf, 0, buf.length);
            if (n > 0) {
                buffer.write(buf, 0, n);
            }
        }
    }

    public synchronized Result stop() {
        if (!running) {
            return null;
        }
        running = false;
        long endMs = SystemClock.elapsedRealtime();
        joinThread();
        try {
            record.stop();
        } catch (Exception ignored) {
        }
        record.release();
        record = null;

        byte[] pcm = buffer.toByteArray();
        Result r = new Result();
        r.bytes = pcm.length;
        r.sampleRate = SAMPLE_RATE;
        r.minBufferSize = minBufferSize;
        r.durationMs = endMs - startMs;
        r.source = usedSource;
        computeLevels(pcm, r);

        File out = new File(context.getCacheDir(), "probe-last.wav");
        try {
            WavWriter.writePcm16(out, pcm, SAMPLE_RATE, 1);
            r.wavPath = out.getAbsolutePath();
        } catch (Exception e) {
            ProbeLog.e("Audio", "WAV_WRITE_FAIL");
            r.wavPath = null;
        }
        return r;
    }

    public synchronized void cancel() {
        if (!running) {
            return;
        }
        running = false;
        joinThread();
        try {
            if (record != null) {
                record.stop();
            }
        } catch (Exception ignored) {
        }
        if (record != null) {
            record.release();
            record = null;
        }
        ProbeLog.i("Audio", "AUDIO_CANCELLED");
    }

    private void joinThread() {
        try {
            if (thread != null) {
                thread.join(500);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        thread = null;
    }

    private void computeLevels(byte[] pcm, Result r) {
        long sumSq = 0;
        int peak = 0;
        int samples = pcm.length / 2;
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            int s = (short) ((pcm[i] & 0xff) | (pcm[i + 1] << 8));
            sumSq += (long) s * s;
            int a = Math.abs(s);
            if (a > peak) {
                peak = a;
            }
        }
        r.peakAbs = peak;
        r.rms = samples > 0 ? Math.sqrt((double) sumSq / samples) : 0.0;
    }
}
