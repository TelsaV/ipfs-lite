package threads.server.core.threads;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

public class PinsViewModel extends AndroidViewModel {

    private final ThreadsDatabase threadsDatabase;

    public PinsViewModel(@NonNull Application application) {
        super(application);
        threadsDatabase = THREADS.getInstance(
                application.getApplicationContext()).getThreadsDatabase();
    }

    // TODO delete

    public LiveData<List<Thread>> getVisiblePinnedThreads(int location) {
        return threadsDatabase.threadDao().getLiveDataVisibleChildrenByQuery(location, 0L, "");
    }
}