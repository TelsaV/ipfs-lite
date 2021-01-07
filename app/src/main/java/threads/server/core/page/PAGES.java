package threads.server.core.page;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Room;

import java.util.List;

import threads.server.ipfs.CID;


public class PAGES {

    private static PAGES INSTANCE = null;

    private final PageDatabase pageDatabase;
    private final ResolverDatabase resolverDatabase;

    private PAGES(final PAGES.Builder builder) {
        pageDatabase = builder.pageDatabase;
        resolverDatabase = builder.resolverDatabase;
    }

    @NonNull
    private static PAGES createPages(@NonNull PageDatabase threadsDatabase,
                                     @NonNull ResolverDatabase resolverDatabase) {

        return new Builder()
                .pageDatabase(threadsDatabase)
                .resolverDatabase(resolverDatabase)
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


                    ResolverDatabase resolverDatabase = Room.inMemoryDatabaseBuilder(context,
                            ResolverDatabase.class).allowMainThreadQueries().
                            fallbackToDestructiveMigration().build();
                    INSTANCE = PAGES.createPages(pageDatabase, resolverDatabase);
                }
            }
        }
        return INSTANCE;
    }

    @NonNull
    public Bookmark createBookmark(@NonNull String uri, @NonNull String title) {
        return new Bookmark(uri, title);
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


    public void resetPageOutdated(@NonNull String hash) {
        pageDatabase.pageDao().setOutdated(hash, false);
    }

    public boolean isPageOutdated(@NonNull String hash) {
        return pageDatabase.pageDao().isOutdated(hash);
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

    public List<Bookmark> getBookmarksByQuery(@NonNull String query) {

        String searchQuery = query.trim();
        if (!searchQuery.startsWith("%")) {
            searchQuery = "%" + searchQuery;
        }
        if (!searchQuery.endsWith("%")) {
            searchQuery = searchQuery + "%";
        }
        return pageDatabase.bookmarkDao().getBookmarksByQuery(searchQuery);
    }

    public void storeResolver(@NonNull String name, @NonNull String content) {
        storeResolver(createResolver(name, content));
    }

    @NonNull
    private Resolver createResolver(@NonNull String name, @NonNull String content) {
        return new Resolver(name, content);
    }

    private void storeResolver(@NonNull Resolver resolver) {
        resolverDatabase.resolverDao().insertResolver(resolver);
    }


    @Nullable
    public Resolver getResolver(@NonNull String name) {
        return resolverDatabase.resolverDao().getResolver(name);
    }

    public void removeResolver(@NonNull String name) {
        resolverDatabase.resolverDao().removeResolver(name);
    }

    @Nullable
    public CID getPageContent(@NonNull String hash) {
        return pageDatabase.pageDao().getPageContent(hash);
    }


    static class Builder {

        PageDatabase pageDatabase = null;
        ResolverDatabase resolverDatabase = null;

        PAGES build() {

            return new PAGES(this);
        }

        PAGES.Builder pageDatabase(@NonNull PageDatabase pageDatabase) {

            this.pageDatabase = pageDatabase;
            return this;
        }

        public Builder resolverDatabase(ResolverDatabase resolverDatabase) {
            this.resolverDatabase = resolverDatabase;
            return this;
        }
    }
}
