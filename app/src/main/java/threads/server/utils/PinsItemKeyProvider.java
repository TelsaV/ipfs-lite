package threads.server.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemKeyProvider;

public class PinsItemKeyProvider extends ItemKeyProvider<Long> {

    private final PinsViewAdapter mAdapter;


    public PinsItemKeyProvider(@NonNull PinsViewAdapter adapter) {
        super(SCOPE_CACHED);
        mAdapter = adapter;
    }

    @Nullable
    @Override
    public Long getKey(int position) {
        return mAdapter.getIdx(position);
    }

    @Override
    public int getPosition(@NonNull Long key) {
        return mAdapter.getPosition(key);
    }

}