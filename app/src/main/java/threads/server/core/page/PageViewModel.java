package threads.server.core.page;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

public class PageViewModel extends AndroidViewModel {
    private final PageDatabase pageDatabase;

    public PageViewModel(@NonNull Application application) {
        super(application);
        pageDatabase = PAGES.getInstance(
                application.getApplicationContext()).getPageDatabase();
    }

    public LiveData<Page> getPage(@NonNull String hash) {
        return pageDatabase.pageDao().getLiveDataPage(hash);
    }
}
