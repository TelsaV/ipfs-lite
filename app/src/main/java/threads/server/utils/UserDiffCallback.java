package threads.server.utils;

import androidx.recyclerview.widget.DiffUtil;

import java.util.List;

import threads.server.core.peers.User;

@SuppressWarnings("WeakerAccess")
public class UserDiffCallback extends DiffUtil.Callback {
    private final List<User> mOldList;
    private final List<User> mNewList;

    public UserDiffCallback(List<User> oldUsers, List<User> newUsers) {
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
        return mOldList.get(oldItemPosition).areItemsTheSame(mNewList.get(
                newItemPosition));
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        return mOldList.get(oldItemPosition).sameContent(mNewList.get(newItemPosition));
    }


}
