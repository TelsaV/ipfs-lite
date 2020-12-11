package threads.server.core.page;


import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.TypeConverters;

import threads.server.core.Converter;
import threads.server.ipfs.CID;


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
    @TypeConverters({Converter.class})
    CID getPageContent(String hash);
}
