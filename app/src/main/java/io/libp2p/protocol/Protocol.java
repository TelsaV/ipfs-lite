package io.libp2p.protocol;

import androidx.annotation.NonNull;

import java.util.Objects;

import io.LogUtils;

public class Protocol {
    public static final String TAG = Protocol.class.getSimpleName();
    public static final Protocol ProtocolBitswapNoVers = new Protocol("/ipfs/bitswap", false);
    public static final Protocol ProtocolBitswapOneZero = new Protocol("/ipfs/bitswap/1.0.0", false);
    public static final Protocol ProtocolLite = new Protocol("/ipfs/lite/1.0.0", true);
    public static final Protocol ProtocolBitswap = new Protocol("/ipfs/bitswap/1.2.0", true);
    public static final Protocol ProtocolBitswapOneOne = new Protocol("/ipfs/bitswap/1.1.0", false);
    private final String id;

    public static Protocol create(@NonNull String protocol) {

        if(Objects.equals(protocol, ProtocolLite.id)){
            return ProtocolLite;
        } else  if(Objects.equals(protocol, ProtocolBitswap.id)) {
            LogUtils.error(TAG,  ProtocolBitswap.String());
            return ProtocolBitswap;
        } else  if(Objects.equals(protocol, ProtocolBitswapNoVers.id)) {
            LogUtils.error(TAG,  ProtocolBitswapNoVers.String());
            return ProtocolBitswapNoVers;
        } else  if(Objects.equals(protocol, ProtocolBitswapOneZero.id)) {
            LogUtils.error(TAG,  ProtocolBitswapOneZero.String());
            return ProtocolBitswapOneZero;
        } else  if(Objects.equals(protocol, ProtocolBitswapOneOne.id)) {
            LogUtils.error(TAG,  ProtocolBitswapOneOne.String());
            return ProtocolBitswapOneOne;
        } else {
            throw new RuntimeException();
        }

    }

    public boolean isSupportHas() {
        return supportHas;
    }

    private final boolean supportHas;

    public Protocol(@NonNull String id, boolean supportHas) {
        this.id = id;
        this.supportHas = supportHas;
    }

    public String String() {
        return id;
    }
}
