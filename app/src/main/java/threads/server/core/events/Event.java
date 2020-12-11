package threads.server.core.events;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverter;
import androidx.room.TypeConverters;

import java.util.Date;

@Entity
public class Event {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "identifier")
    private final String identifier;
    @NonNull
    @ColumnInfo(name = "content")
    private final String content;
    @NonNull
    @TypeConverters(Event.class)
    @ColumnInfo(name = "date")
    private final Date date;

    Event(@NonNull String identifier, @NonNull String content, @NonNull Date date) {

        this.identifier = identifier;
        this.date = date;
        this.content = content;
    }

    public static Event createEvent(@NonNull String identifier, @NonNull String content) {
        return new Event(identifier, content, new Date());
    }

    @TypeConverter
    public static Date toDate(Long value) {
        return value == null ? null : new Date(value);
    }

    @TypeConverter
    public static Long toLong(Date date) {
        if (date == null) {
            return null;
        } else {
            return date.getTime();
        }
    }

    @NonNull
    public String getContent() {
        return content;
    }

    @NonNull
    public Date getDate() {
        return date;
    }

    @NonNull
    public String getIdentifier() {
        return identifier;
    }


}
