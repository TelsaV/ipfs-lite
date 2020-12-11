package threads.server.utils;

import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.widget.RecyclerView;

public class PinsItemDetailsLookup extends ItemDetailsLookup<Long> {
    @NonNull
    private final RecyclerView mRecyclerView;

    public PinsItemDetailsLookup(@NonNull RecyclerView recyclerView) {
        this.mRecyclerView = recyclerView;
    }

    @Nullable
    @Override
    public ItemDetails<Long> getItemDetails(@NonNull MotionEvent e) {
        View view = mRecyclerView.findChildViewUnder(e.getX(), e.getY());
        if (view != null) {
            RecyclerView.ViewHolder viewHolder = mRecyclerView.getChildViewHolder(view);
            if (viewHolder instanceof PinsViewAdapter.PinsViewHolder) {
                return ((PinsViewAdapter.PinsViewHolder) viewHolder).getPinsItemDetails();
            }
        }
        return null;
    }
}
