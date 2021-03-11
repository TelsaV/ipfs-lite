package io.ipfs.utils;

import io.ipfs.Closeable;

public interface Progress extends Closeable {

    void setProgress(int progress);

    boolean doProgress();

}
