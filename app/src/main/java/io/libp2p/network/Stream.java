package io.libp2p.network;

import io.libp2p.mux.MuxedStream;
import io.libp2p.protocol.ID;

public interface Stream extends MuxedStream {
    void Reset();

    void Close();

    ID Protocol();

    Conn Conn();

    byte[] getData();
}
