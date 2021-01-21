package threads.server.core.page;

import androidx.room.RoomDatabase;

@androidx.room.Database(entities = {Page.class, Bookmark.class}, version = 11, exportSchema = false)
public abstract class PageDatabase extends RoomDatabase {

    public abstract PageDao pageDao();

    public abstract BookmarkDao bookmarkDao();

}
