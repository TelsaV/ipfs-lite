package threads.server.core.threads;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.TypeConverters;

import java.util.List;

import threads.server.core.Converter;
import threads.server.ipfs.CID;

@Dao
public interface ThreadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertThread(Thread thread);

    @Query("UPDATE Thread SET error = 1 WHERE idx = :idx")
    void setError(long idx);

    @Query("SELECT content FROM Thread WHERE idx = :idx")
    @TypeConverters({Converter.class})
    CID getContent(long idx);

    @Query("UPDATE Thread SET leaching = 1  WHERE idx = :idx")
    void setLeaching(long idx);

    @Query("UPDATE Thread SET leaching = 0  WHERE idx = :idx")
    void resetLeaching(long idx);

    @Query("SELECT leaching FROM Thread WHERE idx =:idx")
    boolean isLeaching(long idx);

    @Query("UPDATE Thread SET deleting = 1 WHERE idx = :idx")
    void setDeleting(long idx);

    @Query("UPDATE Thread SET deleting = 0 WHERE idx = :idx")
    void resetDeleting(long idx);

    @Query("SELECT * FROM Thread WHERE content = :cid AND parent = :parent AND location =:location")
    @TypeConverters({Converter.class})
    List<Thread> getThreadsByContentAndParent(int location, CID cid, long parent);

    @Query("SELECT * FROM Thread WHERE name = :name AND parent = :parent AND location =:location")
    List<Thread> getThreadsByNameAndParent(int location, String name, long parent);

    @Delete
    void removeThreads(List<Thread> threads);

    @Query("SELECT COUNT(idx) FROM Thread WHERE content =:cid OR thumbnail =:cid AND location =:location")
    @TypeConverters({Converter.class})
    int references(int location, CID cid);

    @Query("SELECT * FROM Thread WHERE parent =:thread AND location =:location")
    List<Thread> getChildren(int location, long thread);

    @Query("SELECT SUM(size) FROM Thread WHERE parent =:parent AND location =:location AND deleting = 0")
    long getChildrenSummarySize(int location, long parent);

    @Query("SELECT * FROM Thread WHERE idx =:idx")
    Thread getThreadByIdx(long idx);


    @Query("SELECT * FROM Thread WHERE parent =:parent AND location =:location AND deleting = 0  AND name LIKE :query")
    LiveData<List<Thread>> getLiveDataVisibleChildrenByQuery(int location, long parent, String query);

    @Query("UPDATE Thread SET content =:cid  WHERE idx = :idx")
    @TypeConverters({Converter.class})
    void setContent(long idx, CID cid);

    @Query("UPDATE Thread SET mimeType =:mimeType WHERE idx = :idx")
    void setMimeType(long idx, String mimeType);

    @Query("UPDATE Thread SET name =:name WHERE idx = :idx")
    void setName(long idx, String name);

    @Query("SELECT * FROM Thread WHERE parent =:parent AND deleting = 0 AND location =:location")
    List<Thread> getVisibleChildren(int location, long parent);

    @Query("SELECT * FROM Thread WHERE location =:location AND seeding = 1 AND deleting = 0 ORDER BY lastModified DESC LIMIT :limit")
    List<Thread> getNewestThreads(int location, int limit);

    @Query("SELECT * FROM Thread WHERE location =:location AND deleting = 0 AND name LIKE :query")
    List<Thread> getThreadsByQuery(int location, String query);

    @Query("UPDATE Thread SET progress = :progress WHERE idx = :idx")
    void setProgress(long idx, int progress);

    @Query("UPDATE Thread SET position = :position WHERE idx = :idx")
    void setPosition(long idx, long position);

    @Query("UPDATE Thread SET seeding = 1, init = 0, progress = 0, leaching = 0 WHERE idx = :idx")
    void setDone(long idx);

    @Query("UPDATE Thread SET content =:cid, seeding = 1, init = 0, progress = 0, leaching = 0 WHERE idx = :idx")
    @TypeConverters({Converter.class})
    void setDone(long idx, CID cid);

    @Query("UPDATE Thread SET size = :size WHERE idx = :idx")
    void setSize(long idx, long size);


    @Query("UPDATE Thread SET work = :work WHERE idx = :idx")
    void setWork(long idx, String work);

    @Query("UPDATE Thread SET work = null WHERE idx = :idx")
    void resetWork(long idx);

    @Query("SELECT init FROM Thread WHERE idx =:idx")
    boolean isInit(long idx);

    @Query("UPDATE Thread SET init = 0 WHERE idx = :idx")
    void resetInit(long idx);

    @Query("SELECT * FROM Thread WHERE deleting = 1 AND location =:location AND lastModified < :time")
    List<Thread> getDeletedThreads(int location, long time);

    @Query("UPDATE Thread SET lastModified =:lastModified WHERE idx = :idx")
    void setLastModified(long idx, long lastModified);

    @Query("DELETE FROM Thread WHERE idx =:idx")
    void removeThread(long idx);

    @Query("SELECT parent FROM Thread WHERE idx = :idx")
    long getParent(long idx);

    @Query("SELECT name FROM Thread WHERE idx = :idx")
    String getName(long idx);

    @Query("SELECT position FROM Thread WHERE idx = :idx")
    long getPosition(long idx);

    @Query("UPDATE Thread SET uri =:uri WHERE idx = :idx")
    void setUri(long idx, String uri);

    @Query("UPDATE Thread SET parent =:parent WHERE idx = :idx")
    void setParent(long idx, long parent);

    @Query("SELECT work FROM Thread WHERE idx = :idx")
    String getWork(long idx);

    @Query("UPDATE Thread SET sortOrder =:sortOrder WHERE idx = :idx")
    @TypeConverters({SortOrder.class})
    void setSortOrder(long idx, SortOrder sortOrder);

    @Query("SELECT sortOrder FROM Thread WHERE idx = :idx")
    @TypeConverters({SortOrder.class})
    SortOrder getSortOrder(long idx);
}
