package threads.server.core.threads;

import androidx.annotation.NonNull;
import androidx.room.TypeConverter;


public enum Status {
    UNKNOWN(0),
    SUCCESS(1),
    FAILURE(2);
    @NonNull
    private final Integer code;

    Status(@NonNull Integer code) {

        this.code = code;
    }

    @TypeConverter
    public static Status toStatus(Integer status) {

        if (status.equals(Status.SUCCESS.getCode())) {
            return Status.SUCCESS;
        } else if (status.equals(Status.FAILURE.getCode())) {
            return Status.FAILURE;
        } else if (status.equals(Status.UNKNOWN.getCode())) {
            return Status.UNKNOWN;
        } else {
            throw new IllegalArgumentException("Could not recognize status");
        }
    }

    @TypeConverter
    public static Integer toInteger(Status status) {

        return status.getCode();
    }

    @NonNull
    private Integer getCode() {
        return code;
    }
}
