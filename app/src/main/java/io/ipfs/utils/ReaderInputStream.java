package io.ipfs.utils;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;

import io.ipfs.LogUtils;

public class ReaderInputStream extends InputStream implements AutoCloseable {
    private static final String TAG = ReaderInputStream.class.getSimpleName();
    private final Reader mReader;
    private int position = 0;
    private byte[] data = null;

    ReaderInputStream(@NonNull Reader reader) {
        mReader = reader;
    }

    @Override
    public int available() {
        long size = mReader.getSize();
        return (int) size;
    }


    @Override
    public int read() throws IOException {

        try {
            if (data == null) {
                invalidate();
                preLoad();
            }
            if (data == null) {
                return -1;
            }
            if (position < data.length) {
                byte value = data[position];
                position++;
                return (value & 0xff);
            } else {
                invalidate();
                if (preLoad()) {
                    byte value = data[position];
                    position++;
                    return (value & 0xff);
                } else {
                    return -1;
                }
            }


        } catch (Throwable e) {
            throw new IOException(e);
        }
    }

    private void invalidate() {
        position = 0;
        data = null;
    }


    private boolean preLoad() {

        data = mReader.loadNextData();

        return data != null;
    }

    public void close() {
        try {
            mReader.close();
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }
}
