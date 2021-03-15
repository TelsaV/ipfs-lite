package io.libp2p.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.libp2p.peer.ID;

public interface Stream {


    @NonNull
    ID RemotePeer();

    byte[] GetData();

    @Nullable
    String GetError();

}
