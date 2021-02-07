package threads.server.core.pages;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;

import java.io.ByteArrayOutputStream;

@androidx.room.Entity
public class Bookmark {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "uri")
    private final String uri;
    @NonNull
    @ColumnInfo(name = "title")
    private final String title;

    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    private byte[] icon;
    @ColumnInfo(name = "timestamp")
    private final long timestamp; // checked

    public Bookmark(@NonNull String uri, @NonNull String title, long timestamp) {
        this.uri = uri;
        this.title = title;
        this.timestamp = timestamp;
    }

    private static byte[] getBytes(@NonNull Bitmap bitmap) {
        Bitmap copy = bitmap.copy(bitmap.getConfig(), true);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        copy.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] byteArray = stream.toByteArray();
        copy.recycle();
        return byteArray;
    }

    public long getTimestamp() {
        return timestamp;
    }


    public byte[] getIcon() {
        return icon;
    }


    void setIcon(byte[] icon) {
        this.icon = icon;
    }


    @Nullable
    public Bitmap getBitmapIcon() {
        if (icon != null) {
            return BitmapFactory.decodeByteArray(icon, 0, icon.length);
        }
        return null;
    }

    public void setBitmapIcon(@NonNull Bitmap bitmap) {
        setIcon(Bookmark.getBytes(bitmap));
    }

    @NonNull
    public String getUri() {
        return uri;
    }

    @NonNull
    public String getTitle() {
        return title;
    }
}
