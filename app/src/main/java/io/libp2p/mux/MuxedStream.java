package io.libp2p.mux;

import androidx.annotation.Nullable;

import java.time.Duration;

public interface MuxedStream {
    void SetWriteDeadline(@Nullable Duration timeout);
}
