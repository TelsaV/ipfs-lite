package threads.server.core.peers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Room;

import java.util.List;

public class PEERS {

    private static PEERS INSTANCE = null;
    private final UsersDatabase usersDatabase;

    private PEERS(final PEERS.Builder builder) {
        this.usersDatabase = builder.usersDatabase;
    }

    @NonNull
    private static PEERS createPeers(@NonNull UsersDatabase usersDatabase) {

        return new Builder()
                .usersDatabase(usersDatabase)
                .build();
    }

    public static PEERS getInstance(@NonNull Context context) {

        if (INSTANCE == null) {
            synchronized (PEERS.class) {
                if (INSTANCE == null) {
                    UsersDatabase usersDatabase = Room.databaseBuilder(context, UsersDatabase.class,
                            UsersDatabase.class.getSimpleName()).
                            allowMainThreadQueries().
                            fallbackToDestructiveMigration().build();


                    INSTANCE = PEERS.createPeers(usersDatabase);
                }
            }
        }
        return INSTANCE;
    }

    @NonNull
    public UsersDatabase getUsersDatabase() {
        return usersDatabase;
    }

    public void storeUser(@NonNull User user) {

        getUsersDatabase().userDao().insertUsers(user);
    }


    @NonNull
    public User createUser(@NonNull String pid, @NonNull String name) {

        return User.createUser(name, pid);
    }


    public void removeUsers(@NonNull String... pids) {
        getUsersDatabase().userDao().removeUserByPids(pids);
    }

    public boolean hasUser(@NonNull String pid) {
        return getUsersDatabase().userDao().hasUser(pid) > 0;
    }


    @Nullable
    public User getUserByPid(@NonNull String pid) {
        return getUsersDatabase().userDao().getUserByPid(pid);
    }

    @NonNull
    public List<User> getUsers() {
        return getUsersDatabase().userDao().getUsers();
    }


    public void setUserConnected(@NonNull String user) {

        getUsersDatabase().userDao().setConnected(user, System.currentTimeMillis());

    }

    public void setUserDisconnected(@NonNull String user) {

        getUsersDatabase().userDao().setDisconnected(user);
    }

    @NonNull
    public List<String> getSwarm() {
        return getUsersDatabase().userDao().getSwarm();
    }


    public boolean isUserConnected(@NonNull String pid) {

        return getUsersDatabase().userDao().isConnected(pid);
    }


    public void setUserAlias(@NonNull String pid, @NonNull String alias) {

        getUsersDatabase().userDao().setAlias(pid, alias);
    }


    @NonNull
    public List<String> getUsersPids() {
        return getUsersDatabase().userDao().getUserPids();
    }


    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean getUserIsLite(@NonNull String pid) {

        return getUsersDatabase().userDao().isLite(pid);
    }

    public void setUserAgent(@NonNull String pid, @NonNull String agent) {
        getUsersDatabase().userDao().setAgent(pid, agent);
    }

    public void setUserAddress(@NonNull String pid, @NonNull String address) {
        getUsersDatabase().userDao().setAddress(pid, address);
    }

    public void setUserLite(@NonNull String pid) {
        getUsersDatabase().userDao().setLite(pid);
    }


    public void setUsersInvisible(String... pids) {
        getUsersDatabase().userDao().setInvisible(pids);
    }

    public void setUsersVisible(String... pids) {
        getUsersDatabase().userDao().setVisible(pids);
    }


    static class Builder {
        UsersDatabase usersDatabase = null;

        PEERS build() {

            return new PEERS(this);
        }


        Builder usersDatabase(@NonNull UsersDatabase usersDatabase) {

            this.usersDatabase = usersDatabase;
            return this;
        }
    }
}
