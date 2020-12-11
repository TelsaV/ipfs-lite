package threads.server.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemKeyProvider;

public class UsersItemKeyProvider extends ItemKeyProvider<String> {

    private final UsersViewAdapter mUsersViewAdapter;


    public UsersItemKeyProvider(@NonNull UsersViewAdapter adapter) {
        super(SCOPE_CACHED);
        mUsersViewAdapter = adapter;
    }

    @Nullable
    @Override
    public String getKey(int position) {
        return mUsersViewAdapter.getPid(position);
    }

    @Override
    public int getPosition(@NonNull String key) {
        return mUsersViewAdapter.getPosition(key);
    }

}