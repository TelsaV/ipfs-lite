package io.ipfs.cid;

import android.util.Pair;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;

import io.ipfs.multihash.Multihash;

public class Encoder {


    // Encode encodes the cid using the parameters of the Encoder
    public static Cid encode(Cid c) {

        if (c.Version() == 0) {
            try {
                byte[] raw = c.Bytes();

                ByteArrayOutputStream res = new ByteArrayOutputStream();
                Multihash.putUvarint(res, 1);
                Multihash.putUvarint(res, Cid.DagProtobuf);

                res.write(raw);
                byte[] data = res.toByteArray();

                return new Cid(data);
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        } else {

            try (InputStream din = new ByteArrayInputStream(c.Bytes())) {

                int type = (int) Multihash.readVarint(din);
                if (type != 1) {
                    throw new RuntimeException("Value 1 expected");
                }
                int len = (int) Multihash.readVarint(din);

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                int read;
                while ((read = din.read()) > 0) {
                    outputStream.write(read);
                }
                byte[] data = outputStream.toByteArray();
                new Cid(data);
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
            return c;
        }
    }


    // FromUvarint reads an unsigned varint from the beginning of buf, returns the
// varint, and the number of bytes read.
    public static Pair<Long, Integer> FromUvarint(byte[] buf) {
        // Modified from the go standard library. Copyright the Go Authors and
        // released under the BSD License.
        long x = 0;
        int s = 0;


        for (int i = 0; i < buf.length; i++) {
            int b = buf[i];
            if ((i == 8 && b >= 0x80) || i >= 9) {
                // this is the 9th and last byte we're willing to read, but it
                // signals there's more (1 in MSB).
                // or this is the >= 10th byte, and for some reason we're still here.
                throw new RuntimeException("overflow");
            }
            if (b < 0x80) {
                if (b == 0 && s > 0) {
                    throw new RuntimeException("overflow");
                }
                return Pair.create((x | (b) << s), i + 1);
            }
            x |= (b & 0x7f) << s;
            s += 7;
        }
        throw new RuntimeException("overflow");
    }

    public static Cid CidFromBytes(byte[] data) {

        if (data.length > 2 && data[0] == Multihash.Type.sha2_256.index && data[1] == Multihash.Type.sha2_256.length) {
            if (data.length != 34) {
                throw new RuntimeException("not enough bytes for cid v0");
            }

            return new Cid(null); // TODO
        }


        try {

            Pair<Long, Integer> firstRun = FromUvarint(data);


            if (firstRun.first != 1) {
                throw new RuntimeException("expected 1 as the cid version number, got: " + firstRun.first);
            }


            byte[] slice = Arrays.copyOfRange(data, firstRun.second, data.length);


            Pair<Long, Integer> secondRun = FromUvarint(slice);

            slice = Arrays.copyOfRange(data, firstRun.second + secondRun.second, data.length);

            Multihash v = Multihash.deserialize(slice);

        /*
        mhnr, _, err := mh.MHFromBytes(slice)
        if err != nil {
            return 0, Undef, err
        }

        l := n + cn + mhnr*/

            return new Cid(v.getHash());
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }

    }


    public static Cid NewCidV1(long codecType, Multihash mhash) {
        try {
            byte[] raw = mhash.getHash();

            ByteArrayOutputStream res = new ByteArrayOutputStream();
            Multihash.putUvarint(res, 1);
            Multihash.putUvarint(res, codecType);
            res.write(raw);
            byte[] data = res.toByteArray();

            return new Cid(data);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }

    }
}
