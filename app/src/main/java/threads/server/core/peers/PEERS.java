package threads.server.core.peers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.List;
import java.util.UUID;

public class PEERS {
    private static final Migration MIGRATION_112_113 = new Migration(112, 113) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE User "
                    + " ADD COLUMN work TEXT");
        }
    };
    private static final Migration MIGRATION_113_114 = new Migration(113, 114) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE User "
                    + " ADD COLUMN timestamp INTEGER DEFAULT 0 NOT NULL");
        }
    };
    private static final Migration MIGRATION_114_115 = new Migration(114, 115) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE User "
                    + " ADD COLUMN visible INTEGER DEFAULT 1 NOT NULL");
        }
    };
    private static final Migration MIGRATION_115_116 = new Migration(115, 116) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE User "
                    + " ADD COLUMN sequence INTEGER DEFAULT 0 NOT NULL");
            database.execSQL("ALTER TABLE User "
                    + " ADD COLUMN ipns TEXT");
        }
    };
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
                            addMigrations(MIGRATION_112_113, MIGRATION_113_114, MIGRATION_114_115,
                                    MIGRATION_115_116).
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

    public void setUserWork(@NonNull String pid, @NonNull UUID id) {
        getUsersDatabase().userDao().setWork(pid, id.toString());
    }

    public void resetUserWork(@NonNull String pid) {
        getUsersDatabase().userDao().resetWork(pid);
    }

    public void setUserDialing(@NonNull String pid) {

        getUsersDatabase().userDao().setDialing(pid, true);
    }

    public void resetUserDialing(@NonNull String pid) {

        getUsersDatabase().userDao().setDialing(pid, false);
    }

    public boolean isUserConnected(@NonNull String pid) {

        return getUsersDatabase().userDao().isConnected(pid);
    }


    @Nullable
    public String getUserPublicKey(@NonNull String pid) {

        return getUsersDatabase().userDao().getPublicKey(pid);
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

    public void setUserPublicKey(@NonNull String pid, @NonNull String pkey) {
        getUsersDatabase().userDao().setPublicKey(pid, pkey);
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

    public void setUserIpns(@NonNull String pid, String ipns, long sequence) {
        getUsersDatabase().userDao().setIpns(pid, ipns, sequence);
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
