package threads.server.utils;

import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemDetailsLookup;

@SuppressWarnings("WeakerAccess")
public class UserItemDetails extends ItemDetailsLookup.ItemDetails<String> {
    private final UserItemPosition mUserItemPosition;
    public String pid;

    UserItemDetails(@NonNull UserItemPosition userItemPosition) {
        this.mUserItemPosition = userItemPosition;
    }

    @Override
    public int getPosition() {
        return mUserItemPosition.getPosition(pid);
    }

    @Nullable
    @Override
    public String getSelectionKey() {
        return pid;
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
