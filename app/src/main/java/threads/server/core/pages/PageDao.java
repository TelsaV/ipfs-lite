package threads.server.core.pages;


import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;


@Dao
public interface PageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPage(Page page);

    @Query("SELECT * FROM Page WHERE hash = :hash")
    Page getPage(String hash);

    @Query("UPDATE Page SET outdated = :outdated WHERE hash = :hash")
    void setOutdated(String hash, boolean outdated);

    @Query("SELECT outdated FROM Page WHERE hash =:hash")
    boolean isOutdated(String hash);

    @Query("SELECT * FROM Page WHERE hash = :hash")
    LiveData<Page> getLiveDataPage(String hash);

    @Query("SELECT content FROM Page WHERE hash = :hash")
    String getPageContent(String hash);
}
