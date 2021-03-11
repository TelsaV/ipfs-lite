package io.ipfs.utils;

import androidx.annotation.NonNull;

import java.io.InputStream;

public class WriterStream implements io.ipfs.format.Reader {

    private final InputStream mInputStream;
    private final Progress mProgress;
    private final long size;
    private int progress = 0;
    private long totalRead = 0;
    private boolean done;


    public WriterStream(@NonNull InputStream inputStream, @NonNull Progress progress, long size) {
        this.mInputStream = inputStream;
        this.mProgress = progress;
        this.size = size;
    }

    public boolean close() {
        done = true;
        return mProgress.isClosed();

    }

    @Override
    public int Read(byte[] bytes) {

        if (mProgress.isClosed()) {
            throw new RuntimeException("progress closed");
        }

        try {
            int read = mInputStream.read(bytes);
            if (read < 0) {
                done = true;
            } else {
                totalRead += read;
                if (mProgress.doProgress()) {
                    if (size > 0) {
                        int percent = (int) ((totalRead * 100.0f) / size);
                        if (progress < percent) {
                            progress = percent;
                            mProgress.setProgress(percent);
                        }
                    }
                }
            }
            return read;
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    public boolean Done() {
        return done;
    }
}
