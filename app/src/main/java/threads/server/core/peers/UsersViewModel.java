package threads.server.core.peers;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

public class UsersViewModel extends AndroidViewModel {

    @NonNull
    private final UsersDatabase usersDatabase;

    public UsersViewModel(@NonNull Application application) {
        super(application);
        usersDatabase = PEERS.getInstance(
                application.getApplicationContext()).getUsersDatabase();
    }

    @NonNull
    public LiveData<List<User>> getUsers() {
        return usersDatabase.userDao().getLiveDataUsers();
    }
}