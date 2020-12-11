package threads.server.core.threads;

import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.util.Objects;
import java.util.UUID;

import threads.server.core.Converter;
import threads.server.ipfs.CID;


@androidx.room.Entity
public class Thread {

    @ColumnInfo(name = "location")
    private final int location; // checked

    @ColumnInfo(name = "parent")
    private final long parent; // checked
    @PrimaryKey(autoGenerate = true)
    private long idx; // checked
    @ColumnInfo(name = "lastModified")
    private long lastModified; // checked

    @Deprecated
    @Nullable
    @ColumnInfo(name = "thumbnail")
    @TypeConverters(Converter.class)
    private CID thumbnail;  // checked

    @ColumnInfo(name = "progress")
    private int progress;  // checked
    @Nullable
    @TypeConverters(Converter.class)
    @ColumnInfo(name = "content")
    private CID content;  // checked
    @Deprecated
    @Nullable
    @ColumnInfo(name = "ipns")
    private String ipns;  // checked
    @ColumnInfo(name = "size")
    private long size;  // checked
    @NonNull
    @ColumnInfo(name = "mimeType")
    private String mimeType;  // checked
    @NonNull
    @ColumnInfo(name = "name")
    private String name = "";
    @Nullable
    @ColumnInfo(name = "uri")
    private String uri;
    @Nullable
    @ColumnInfo(name = "work")
    private String work;
    @ColumnInfo(name = "pinned")
    private boolean pinned; // checked
    @Deprecated
    @ColumnInfo(name = "publishing")
    private boolean publishing; // checked
    @ColumnInfo(name = "leaching")
    private boolean leaching; // checked
    @ColumnInfo(name = "seeding")
    private boolean seeding; // checked
    @ColumnInfo(name = "deleting")
    private boolean deleting; // checked
    @ColumnInfo(name = "error")
    private boolean error;
    @ColumnInfo(name = "init")
    private boolean init;
    @ColumnInfo(name = "position")
    private long position;
    @Deprecated
    @ColumnInfo(name = "hidden")
    private boolean hidden;
    @Deprecated
    @NonNull
    @TypeConverters(Status.class)
    @ColumnInfo(name = "status")
    private Status status;  // checked
    @NonNull
    @TypeConverters(SortOrder.class)
    @ColumnInfo(name = "sortOrder")
    private SortOrder sortOrder;  // checked

    Thread(int location, long parent) {
        this.location = location;
        this.parent = parent;
        this.lastModified = System.currentTimeMillis();
        this.mimeType = "";
        this.pinned = false;
        this.publishing = false;
        this.leaching = false;
        this.seeding = false;
        this.deleting = false;
        this.progress = 0;
        this.position = 0;
        this.status = Status.UNKNOWN;
        this.init = false;
        this.error = false;
        this.hidden = false;
        this.sortOrder = SortOrder.NAME;
    }

    static Thread createThread(int location, long parent) {
        return new Thread(location, parent);
    }

    @NonNull
    public SortOrder getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(@NonNull SortOrder sortOrder) {
        this.sortOrder = sortOrder;
    }

    @Nullable
    String getIpns() {
        return ipns;
    }

    void setIpns(@Nullable String ipns) {
        this.ipns = ipns;
    }

    public long getPosition() {
        return position;
    }

    public void setPosition(long position) {
        this.position = position;
    }

    @Nullable
    public String getUri() {
        return uri;
    }

    public void setUri(@Nullable String uri) {
        this.uri = uri;
    }

    public boolean isError() {
        return error;
    }

    public void setError(boolean error) {
        this.error = error;
    }

    public boolean isLeaching() {
        return leaching;
    }

    public void setLeaching(boolean leaching) {
        this.leaching = leaching;
    }


    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    boolean isPublishing() {
        return publishing;
    }

    void setPublishing(boolean publishing) {
        this.publishing = publishing;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public long getIdx() {
        return idx;
    }

    void setIdx(long idx) {
        this.idx = idx;
    }

    @NonNull
    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(@NonNull String mimeType) {
        this.mimeType = mimeType;
    }

    @Nullable
    CID getThumbnail() {
        return thumbnail;
    }

    void setThumbnail(@Nullable CID thumbnail) {
        this.thumbnail = thumbnail;
    }

    public boolean areItemsTheSame(@NonNull Thread thread) {
        return idx == thread.getIdx();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Thread thread = (Thread) o;
        return getIdx() == thread.getIdx();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getIdx());
    }

    @Nullable
    public CID getContent() {
        return content;
    }

    public void setContent(@Nullable CID content) {
        this.content = content;
    }

    public long getParent() {
        return parent;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    public boolean isDir() {
        return DocumentsContract.Document.MIME_TYPE_DIR.equals(getMimeType());
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public boolean isSeeding() {
        return seeding;
    }

    public void setSeeding(boolean seeding) {
        this.seeding = seeding;
    }

    public boolean isDeleting() {
        return deleting;
    }

    public void setDeleting(boolean deleting) {
        this.deleting = deleting;
    }

    @NonNull
    Status getStatus() {
        return status;
    }

    void setStatus(@NonNull Status status) {
        this.status = status;
    }

    @Nullable
    public String getWork() {
        return work;
    }

    public void setWork(@Nullable String work) {
        this.work = work;
    }

    @Nullable
    public UUID getWorkUUID() {
        if (work != null) {
            return UUID.fromString(work);
        }
        return null;
    }

    public boolean isInit() {
        return init;
    }

    public void setInit(boolean init) {
        this.init = init;
    }

    public int getLocation() {
        return location;
    }

    boolean isHidden() {
        return hidden;
    }

    void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public boolean hasContent() {
        return getContent() != null;
    }
}
