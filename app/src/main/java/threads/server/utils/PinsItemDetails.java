package threads.server.utils;

import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemDetailsLookup;

@SuppressWarnings("WeakerAccess")
public class PinsItemDetails extends ItemDetailsLookup.ItemDetails<Long> {
    private final PinsItemPosition mItemPosition;
    public long idx;

    PinsItemDetails(@NonNull PinsItemPosition itemPosition) {
        this.mItemPosition = itemPosition;
    }

    @Override
    public int getPosition() {
        return mItemPosition.getPosition(idx);
    }

    @Nullable
    @Override
    public Long getSelectionKey() {
        return idx;
    }

    @Override
    public boolean inSelectionHotspot(@NonNull MotionEvent e) {
        return false;//don't consider taps as selections => Similar to google photos.
        // if true then consider click as selection
    }

    @Override
    public boolean inDragRegion(@NonNull MotionEvent e) {
        return true;
    }
}
