package threads.server.core.page;


import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;


@Dao
public interface BookmarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertBookmark(Bookmark bookmark);

    @Query("SELECT * FROM Bookmark WHERE uri = :uri")
    Bookmark getBookmark(String uri);

    @Query("SELECT * FROM Bookmark")
    LiveData<List<Bookmark>> getLiveDataBookmarks();

    @Delete
    void removeBookmark(Bookmark bookmark);
}
