package threads.server.core.pages;

import androidx.room.RoomDatabase;

@androidx.room.Database(entities = {Page.class, Bookmark.class}, version = 12, exportSchema = false)
public abstract class PageDatabase extends RoomDatabase {

    public abstract PageDao pageDao();

    public abstract BookmarkDao bookmarkDao();

}
