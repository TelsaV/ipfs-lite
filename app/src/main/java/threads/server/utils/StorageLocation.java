package threads.server.utils;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.Objects;

public class StorageLocation {
    private final String location;
    private final File file;
    private final int hashCode;
    private final boolean primary;

    public StorageLocation(@NonNull String location, @NonNull File file, int hashCode, boolean primary) {
        this.location = location;
        this.file = file;
        this.hashCode = hashCode;
        this.primary = primary;
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof StorageLocation) {
            return Objects.equals(this.hashCode, ((StorageLocation) object).hashCode);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @NonNull
    @Override
    public String toString() {
        return location;
    }

    public File getFile() {
        return file;
    }

    public boolean isPrimary() {
        return primary;
    }
}
