package threads.server.core.pages;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Room;


public class PAGES {

    private static PAGES INSTANCE = null;

    private final PageDatabase pageDatabase;


    private PAGES(final PAGES.Builder builder) {
        pageDatabase = builder.pageDatabase;
    }

    @NonNull
    private static PAGES createPages(@NonNull PageDatabase pageDatabase) {

        return new Builder()
                .pageDatabase(pageDatabase)
                .build();
    }

    public static PAGES getInstance(@NonNull Context context) {

        if (INSTANCE == null) {
            synchronized (PAGES.class) {
                if (INSTANCE == null) {
                    PageDatabase pageDatabase = Room.databaseBuilder(context,
                            PageDatabase.class,
                            PageDatabase.class.getSimpleName()).
                            allowMainThreadQueries().
                            fallbackToDestructiveMigration().
                            build();

                    INSTANCE = PAGES.createPages(pageDatabase);
                }
            }
        }
        return INSTANCE;
    }

    @NonNull
    public Bookmark createBookmark(@NonNull String uri, @NonNull String title) {
        return new Bookmark(uri, title, System.currentTimeMillis());
    }

    public void storeBookmark(@NonNull Bookmark bookmark) {
        pageDatabase.bookmarkDao().insertBookmark(bookmark);
    }

    @NonNull
    public Page createPage(@NonNull String hash) {
        return new Page(hash);
    }


    public void storePage(@NonNull Page page) {
        pageDatabase.pageDao().insertPage(page);
    }


    public void setPageOutdated(@NonNull String hash) {
        pageDatabase.pageDao().setOutdated(hash, true);
    }

    @Nullable
    public Page getPage(@NonNull String hash) {
        return pageDatabase.pageDao().getPage(hash);
    }

    @NonNull
    public PageDatabase getPageDatabase() {
        return pageDatabase;
    }

    public Bookmark getBookmark(@NonNull String uri) {
        return pageDatabase.bookmarkDao().getBookmark(uri);
    }

    public boolean hasBookmark(@NonNull String uri) {
        return getBookmark(uri) != null;
    }

    public void removeBookmark(@NonNull Bookmark bookmark) {
        pageDatabase.bookmarkDao().removeBookmark(bookmark);
    }


    @Nullable
    public String getPageContent(@NonNull String hash) {
        return pageDatabase.pageDao().getPageContent(hash);
    }


    static class Builder {

        PageDatabase pageDatabase = null;

        PAGES build() {

            return new PAGES(this);
        }

        PAGES.Builder pageDatabase(@NonNull PageDatabase pageDatabase) {

            this.pageDatabase = pageDatabase;
            return this;
        }
    }
}
