package io.ipfs.bitswap.network;

import java.time.Duration;

public class MessageSenderOpts {
    public int MaxRetries;
    public Duration SendTimeout;
    public Duration SendErrorBackoff;
}
