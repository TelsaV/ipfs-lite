package threads.server.core.contents;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Room;

import java.util.List;


public class CDS {

    private static CDS INSTANCE = null;
    @NonNull
    private final ContentDatabase contentDatabase;

    private CDS(@NonNull Context context) {

        contentDatabase = Room.databaseBuilder(context,
                ContentDatabase.class,
                ContentDatabase.class.getSimpleName()).
                allowMainThreadQueries().
                fallbackToDestructiveMigration().build();
    }

    @NonNull
    public static CDS getInstance(@NonNull Context context) {

        if (INSTANCE == null) {
            synchronized (CDS.class) {
                if (INSTANCE == null) {
                    INSTANCE = new CDS(context);
                }
            }
        }
        return INSTANCE;
    }

    @NonNull
    private ContentDatabase getContentDatabase() {
        return contentDatabase;
    }

    public List<Content> getContents() {
        return getContentDatabase().contentDao().getContents();
    }


    public void insertContent(@NonNull String cid, long expired, boolean recursively) {
        Content content = Content.create(cid, expired, recursively);
        getContentDatabase().contentDao().insertContent(content);
    }

    public void removeContent(@NonNull String cid) {
        getContentDatabase().contentDao().removeContentByCid(cid);
    }
}
