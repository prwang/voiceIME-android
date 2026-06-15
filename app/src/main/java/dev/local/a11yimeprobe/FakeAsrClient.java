package dev.local.a11yimeprobe;

import android.os.CancellationSignal;
import android.os.SystemClock;

import java.io.File;

/**
 * Offline fake (spec section 9.4). Reuses the same Recorder/SessionState/Committer
 * path with no network, for UX testing and as a regression harness.
 */
public final class FakeAsrClient implements AsrClient {

    @Override
    public AsrResult transcribe(File audioFile, AsrRequest request, CancellationSignal cancel) {
        SystemClock.sleep(300);
        if (cancel != null && cancel.isCanceled()) {
            return new AsrResult("", false, null);
        }
        return new AsrResult("[fake cloud transcript] 你好，世界！", false, "fake-0");
    }
}
