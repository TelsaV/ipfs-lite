package io.libp2p.peer;

import androidx.annotation.NonNull;

import java.util.Objects;

public class ID {
    private final String id;

    public ID(@NonNull String id) {
        this.id = id;
    }

    public String String() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ID id1 = (ID) o;
        return Objects.equals(id, id1.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
