package dev.local.a11yimeprobe;

import android.os.CancellationSignal;

import java.io.File;

/**
 * ASR client behind an interface (spec section 9.4 / 19.1) so the product runs
 * with a fake (offline) or the real cloud client without changing the commit path.
 */
public interface AsrClient {

    AsrResult transcribe(File audioFile, AsrRequest request, CancellationSignal cancel) throws Exception;
}
