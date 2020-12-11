package threads.server.core.contents;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;


@Dao
public interface ContentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertContent(Content... contents);

    @Query("DELETE FROM Content WHERE cid = :cid")
    void removeContentByCid(String cid);

    @Query("SELECT * FROM Content")
    List<Content> getContents();
}
