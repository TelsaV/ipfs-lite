package threads.server.provider;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.BaseDataSource;
import com.google.android.exoplayer2.upstream.DataSpec;

import java.io.EOFException;
import java.io.IOException;

import io.ipfs.Storage;
import io.ipfs.blockstore.Blockstore;
import io.ipfs.exchange.Interface;
import io.ipfs.offline.Exchange;
import io.ipfs.utils.Reader;

public class ProviderDataSource extends BaseDataSource {


    private final Storage storage;
    private final String cid;
    private Reader fileReader;
    @Nullable
    private Uri uri;
    private long bytesRemaining;
    private boolean opened;


    public ProviderDataSource(@NonNull Storage storage, @NonNull String cid) {
        super(false);
        this.cid = cid;
        this.storage = storage;
    }


    private int readIntern(byte[] buffer, int offset, int size) throws IOException {
        try {

            byte[] data = fileReader.load(size);
            System.arraycopy(data, 0, buffer, offset, data.length);

            return data.length;

        } catch (Throwable e) {
            throw new IOException(e);
        }

    }

    @Override
    public long open(@NonNull DataSpec dataSpec) throws IOException {
        try {
            this.uri = dataSpec.uri;

            transferInitializing(dataSpec);


            if (fileReader == null) {
                Blockstore blockstore = Blockstore.NewBlockstore(storage);
                Interface exchange = new Exchange(blockstore);
                fileReader = Reader.getReader(()-> false, blockstore, exchange, cid);
            }

            fileReader.seek(dataSpec.position);
            bytesRemaining = dataSpec.length == C.LENGTH_UNSET ? fileReader.getSize() - dataSpec.position
                    : dataSpec.length;
            if (bytesRemaining < 0) {
                throw new EOFException();
            }
        } catch (Exception e) {
            throw new IOException(e);
        }

        opened = true;
        transferStarted(dataSpec);

        return bytesRemaining;
    }


    @Override
    public int read(@NonNull byte[] buffer, int offset, int readLength) throws IOException {
        if (readLength == 0) {
            return 0;
        } else if (bytesRemaining == 0) {
            return C.RESULT_END_OF_INPUT;
        } else {
            int bytesRead;
            try {
                bytesRead = readIntern(buffer, offset, (int) Math.min(bytesRemaining, readLength));
            } catch (IOException e) {
                throw new IOException(e);
            }

            if (bytesRead > 0) {
                bytesRemaining -= bytesRead;
                bytesTransferred(bytesRead);
            }

            return bytesRead;
        }
    }

    @Override
    @Nullable
    public Uri getUri() {
        return uri;
    }

    @Override
    public void close() throws IOException {
        uri = null;
        try {
            if (fileReader != null) {
                fileReader.close();
            }
        } catch (Exception e) {
            throw new IOException(e);
        } finally {
            fileReader = null;
            if (opened) {
                opened = false;
                transferEnded();
            }
        }
    }

}
