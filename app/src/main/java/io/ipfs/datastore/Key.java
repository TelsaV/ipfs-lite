package io.ipfs.datastore;

import androidx.annotation.NonNull;

public class Key {
    private String key;

    public Key(@NonNull String key) {
        this.key = key;
        clean();
    }


    // RawKey creates a new Key without safety checking the input. Use with care.
    public static Key RawKey(@NonNull String s) {
        // accept an empty string and fix it to avoid special cases
        // elsewhere
        if (s.length() == 0) {
            return new Key("/");
        }

        // perform a quick sanity check that the key is in the correct
        // format, if it is not then it is a programmer error and it is
        // okay to panic
        if (s.getBytes()[0] != '/') {
            throw new RuntimeException("invalid datastore key: " + s);
        }

        return new Key(s);
    }

    private void clean() {
        if (key.length() == 0) {
            key = "/";
        }
        // TODO maybe
    }

    public String String() {
        return key;
    }
}
