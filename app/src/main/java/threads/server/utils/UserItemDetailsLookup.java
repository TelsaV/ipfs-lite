package threads.server.utils;

import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.widget.RecyclerView;

public class UserItemDetailsLookup extends ItemDetailsLookup<String> {
    @NonNull
    private final RecyclerView mRecyclerView;

    public UserItemDetailsLookup(@NonNull RecyclerView recyclerView) {
        this.mRecyclerView = recyclerView;
    }

    @Nullable
    @Override
    public ItemDetails<String> getItemDetails(@NonNull MotionEvent e) {
        View view = mRecyclerView.findChildViewUnder(e.getX(), e.getY());
        if (view != null) {
            RecyclerView.ViewHolder viewHolder = mRecyclerView.getChildViewHolder(view);
            if (viewHolder instanceof UsersViewAdapter.UserViewHolder) {
                return ((UsersViewAdapter.UserViewHolder) viewHolder).getUserItemDetails();
            }
        }
        return null;
    }
}
