package threads.server.core.peers;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUsers(User... users);

    @Query("SELECT * FROM User")
    List<User> getUsers();

    @Query("SELECT pid FROM User")
    List<String> getUserPids();

    @Query("UPDATE User SET alias = :alias WHERE pid = :pid")
    void setAlias(String pid, String alias);

    @Query("SELECT publicKey FROM User WHERE pid = :pid ")
    String getPublicKey(String pid);

    @Query("SELECT lite FROM User WHERE pid = :pid ")
    boolean isLite(String pid);


    @Query("SELECT * FROM User WHERE pid = :pid")
    User getUserByPid(String pid);

    @Query("DELETE FROM User WHERE pid IN(:pids)")
    void removeUserByPids(String... pids);

    @Query("SELECT * FROM User WHERE visible = 1 ORDER BY timestamp DESC")
    LiveData<List<User>> getLiveDataUsers();


    @Query("UPDATE User SET work = :work WHERE pid = :pid")
    void setWork(String pid, String work);

    @Query("UPDATE User SET work = null WHERE pid = :pid")
    void resetWork(String pid);

    @Query("SELECT COUNT(*) FROM User WHERE pid = :pid")
    long hasUser(String pid);

    @Query("UPDATE User SET connected = 1, timestamp = :timestamp WHERE pid = :pid")
    void setConnected(String pid, long timestamp);

    @Query("UPDATE User SET connected = 0 WHERE pid = :pid")
    void setDisconnected(String pid);

    @Query("SELECT connected FROM User WHERE pid = :pid ")
    boolean isConnected(String pid);

    @Query("UPDATE User SET publicKey = :pkey WHERE pid = :pid")
    void setPublicKey(String pid, String pkey);

    @Query("UPDATE User SET agent = :agent WHERE pid = :pid")
    void setAgent(String pid, String agent);

    @Query("UPDATE User SET address = :address WHERE pid = :pid")
    void setAddress(String pid, String address);

    @Query("UPDATE User SET ipns = :ipns, sequence = :sequence WHERE pid = :pid")
    void setIpns(String pid, String ipns, long sequence);

    @Query("UPDATE User SET lite = 1 WHERE pid = :pid")
    void setLite(String pid);

    @Query("UPDATE User SET visible = 1 WHERE pid IN(:pids)")
    void setVisible(String... pids);

    @Query("UPDATE User SET visible = 0 WHERE pid IN(:pids)")
    void setInvisible(String... pids);


    @Query("SELECT pid FROM User WHERE visible = 1 AND blocked = 0")
    List<String> getSwarm();
}
