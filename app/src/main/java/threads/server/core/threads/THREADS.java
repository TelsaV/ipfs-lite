package threads.server.core.threads;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class THREADS extends ThreadsAPI {

    private static final Migration MIGRATION_112_113 = new Migration(112, 113) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Thread "
                    + " ADD COLUMN error INTEGER DEFAULT 0 NOT NULL");
            database.execSQL("ALTER TABLE Thread "
                    + " ADD COLUMN init INTEGER DEFAULT 0 NOT NULL");
            database.execSQL("ALTER TABLE Thread "
                    + " ADD COLUMN uri TEXT");
            database.execSQL("ALTER TABLE Thread "
                    + " ADD COLUMN work TEXT");
        }
    };

    private static final Migration MIGRATION_113_114 = new Migration(113, 114) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Thread "
                    + " ADD COLUMN location INTEGER DEFAULT 0 NOT NULL");
        }
    };
    private static final Migration MIGRATION_114_115 = new Migration(114, 115) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Thread "
                    + " ADD COLUMN hidden INTEGER DEFAULT 0 NOT NULL");
        }
    };
    private static final Migration MIGRATION_115_116 = new Migration(115, 116) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Thread "
                    + " ADD COLUMN position INTEGER DEFAULT 0 NOT NULL");
        }
    };
    private static final Migration MIGRATION_116_117 = new Migration(116, 117) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Thread "
                    + " ADD COLUMN ipns TEXT");
        }
    };

    private static final Migration MIGRATION_117_118 = new Migration(117, 118) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Thread "
                    + " ADD COLUMN sortOrder INTEGER DEFAULT 0 NOT NULL");
        }
    };
    private static THREADS INSTANCE = null;

    private THREADS(final THREADS.Builder builder) {
        super(builder.threadsDatabase);
    }

    @NonNull
    private static THREADS createThreads(@NonNull ThreadsDatabase threadsDatabase) {


        return new THREADS.Builder()
                .threadsDatabase(threadsDatabase)
                .build();
    }

    public static THREADS getInstance(@NonNull Context context) {

        if (INSTANCE == null) {
            synchronized (THREADS.class) {
                if (INSTANCE == null) {
                    ThreadsDatabase threadsDatabase = Room.databaseBuilder(context,
                            ThreadsDatabase.class,
                            ThreadsDatabase.class.getSimpleName()).
                            allowMainThreadQueries().
                            addMigrations(MIGRATION_112_113, MIGRATION_113_114,
                                    MIGRATION_114_115, MIGRATION_115_116, MIGRATION_116_117,
                                    MIGRATION_117_118).
                            fallbackToDestructiveMigration().
                            build();
                    INSTANCE = THREADS.createThreads(threadsDatabase);
                }
            }
        }
        return INSTANCE;
    }


    static class Builder {

        ThreadsDatabase threadsDatabase = null;

        THREADS build() {

            return new THREADS(this);
        }

        Builder threadsDatabase(@NonNull ThreadsDatabase threadsDatabase) {

            this.threadsDatabase = threadsDatabase;
            return this;
        }


    }
}
