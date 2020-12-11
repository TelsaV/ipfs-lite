package threads.server.utils;

import androidx.recyclerview.widget.DiffUtil;

import java.util.List;


@SuppressWarnings("WeakerAccess")
public class PeerDiffCallback extends DiffUtil.Callback {
    private final List<String> mOldList;
    private final List<String> mNewList;

    public PeerDiffCallback(List<String> oldUsers, List<String> newUsers) {
        this.mOldList = oldUsers;
        this.mNewList = newUsers;
    }

    @Override
    public int getOldListSize() {
        return mOldList.size();
    }

    @Override
    public int getNewListSize() {
        return mNewList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        return mOldList.get(oldItemPosition).equals(mNewList.get(
                newItemPosition));
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        return mOldList.get(oldItemPosition).equals(mNewList.get(newItemPosition));
    }


}
