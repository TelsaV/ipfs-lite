package io.ipfs.bitswap.message;

import androidx.annotation.NonNull;

import com.google.protobuf.InvalidProtocolBufferException;

import io.libp2p.network.Stream;
import io.protos.bitswap.BitswapProtos;

public interface BitSwapMessage {
    static BitSwapMessage fromData(byte[] data) throws InvalidProtocolBufferException {
        BitswapProtos.Message message = BitswapProtos.Message.parseFrom(data);
        return new BitSwapMessage() {
            @Override
            public void ToNetV1(@NonNull Stream stream) {

            }
        };
    }

    void ToNetV1(@NonNull Stream stream);
}
