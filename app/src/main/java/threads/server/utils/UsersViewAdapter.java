package threads.server.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.amulyakhare.textdrawable.util.ColorGenerator;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import threads.LogUtils;
import threads.server.R;
import threads.server.core.peers.User;
import threads.server.services.MimeTypeService;

public class UsersViewAdapter extends
        RecyclerView.Adapter<UsersViewAdapter.ViewHolder> implements UserItemPosition {

    private static final String TAG = UsersViewAdapter.class.getSimpleName();
    private final List<User> users = new ArrayList<>();
    private final UsersViewAdapterListener mListener;
    private final Context mContext;
    @Nullable
    private SelectionTracker<String> mSelectionTracker;

    public UsersViewAdapter(@NonNull Context context,
                            @NonNull UsersViewAdapter.UsersViewAdapterListener listener) {
        this.mContext = context;
        this.mListener = listener;
    }


    public void setSelectionTracker(SelectionTracker<String> selectionTracker) {
        this.mSelectionTracker = selectionTracker;
    }

    private boolean hasSelection() {
        if (mSelectionTracker != null) {
            return mSelectionTracker.hasSelection();
        }
        return false;
    }

    @Override
    public int getItemViewType(int position) {
        return R.layout.users;
    }

    @Override
    @NonNull
    public UsersViewAdapter.UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                                              int viewType) {

        View v = LayoutInflater.from(parent.getContext())
                .inflate(viewType, parent, false);
        return new UsersViewAdapter.UserViewHolder(this, v);

    }


    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        final User user = users.get(position);

        if (holder instanceof UsersViewAdapter.UserViewHolder) {
            UsersViewAdapter.UserViewHolder userViewHolder = (UsersViewAdapter.UserViewHolder) holder;

            boolean isSelected = false;
            if (mSelectionTracker != null) {
                if (mSelectionTracker.isSelected(user.getPid())) {
                    isSelected = true;
                }
            }

            userViewHolder.bind(isSelected, user);

            try {
                if (isSelected) {
                    userViewHolder.view.setBackgroundResource(R.color.colorSelectedItem);
                } else {
                    userViewHolder.view.setBackgroundColor(
                            android.R.drawable.list_selector_background);
                }

                if (hasSelection()) {
                    if (isSelected) {
                        userViewHolder.user_action.setVisibility(View.VISIBLE);
                        userViewHolder.user_action.setImageResource(R.drawable.check_circle_outline);
                    } else {
                        userViewHolder.user_action.setVisibility(View.VISIBLE);
                        userViewHolder.user_action.setImageResource(R.drawable.checkbox_blank_circle_outline);
                    }
                } else {
                    if (user.isDialing()) {
                        userViewHolder.user_action.setImageResource(R.drawable.pause);
                        userViewHolder.user_action.setVisibility(View.VISIBLE);
                        userViewHolder.user_action.setOnClickListener((v) ->
                                mListener.invokeAbortDialing(user)
                        );
                    } else {
                        userViewHolder.user_action.setImageResource(R.drawable.dots);
                        userViewHolder.user_action.setVisibility(View.VISIBLE);
                        userViewHolder.user_action.setOnClickListener((v) ->
                                mListener.invokeAction(user, v)
                        );
                    }
                }


                {
                    if (user.hasName()) {
                        Bitmap bitmap = MimeTypeService.getNameImage(mContext, user.getAlias());
                        Glide.with(mContext).
                                load(bitmap).
                                apply(RequestOptions.circleCropTransform()).
                                into(userViewHolder.user_image);
                    } else {
                        String name = user.getAlias();
                        int color = ColorGenerator.MATERIAL.getColor(name);

                        int res = R.drawable.server_network;
                        if (user.isLite()) {
                            res = R.drawable.account;
                        }

                        userViewHolder.user_image.setImageResource(res);
                        userViewHolder.user_image.setColorFilter(color);
                    }
                }

                if (user.isConnected()) {
                    int color = ContextCompat.getColor(mContext, R.color.colorAccent);
                    userViewHolder.user_date.setTextColor(color);
                    userViewHolder.user_date.setText(mContext.getString(R.string.online));
                } else {
                    long timestamp = user.getTimestamp();
                    if (timestamp > 0L) {
                        String date = getDate(new Date(timestamp));
                        userViewHolder.user_date.setText(date);
                    } else {
                        userViewHolder.user_date.setText("");
                    }
                }


                userViewHolder.view.setOnClickListener((v) -> {
                    try {
                        mListener.onClick(user);
                    } catch (Throwable e) {
                        LogUtils.error(TAG, e);
                    }
                });

                userViewHolder.user_alias.setText(user.getAlias());

            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }

        }

    }


    @NonNull
    private String getDate(@NonNull Date date) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        Date today = c.getTime();
        c.set(Calendar.MONTH, 0);
        c.set(Calendar.DAY_OF_MONTH, 0);
        Date lastYear = c.getTime();

        if (date.before(today)) {
            if (date.before(lastYear)) {
                return android.text.format.DateFormat.format("dd.MM.yyyy", date).toString();
            } else {
                return android.text.format.DateFormat.format("dd.MMMM", date).toString();
            }
        } else {
            return android.text.format.DateFormat.format("HH:mm", date).toString();
        }
    }


    @Override
    public int getItemCount() {
        return users.size();
    }


    public void updateData(@NonNull List<User> users) {

        final UserDiffCallback diffCallback = new UserDiffCallback(this.users, users);
        final DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);

        this.users.clear();
        this.users.addAll(users);
        diffResult.dispatchUpdatesTo(this);
    }

    @Override
    public synchronized int getPosition(String pid) {
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getPid().equals(pid)) {
                return i;
            }
        }
        return 0;
    }

    String getPid(int position) {
        return users.get(position).getPid();
    }

    public void selectAllUsers() {
        try {
            for (User user : users) {
                if (mSelectionTracker != null) {
                    mSelectionTracker.select(user.getPid());
                }
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }


    public interface UsersViewAdapterListener {

        void invokeAction(@NonNull User user, @NonNull View view);

        void invokeAbortDialing(@NonNull User user);

        void onClick(@NonNull User user);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View view;

        ViewHolder(View v) {
            super(v);
            view = v;
        }
    }


    static class UserViewHolder extends ViewHolder {

        final TextView user_date;
        final TextView user_alias;
        final ImageView user_action;
        final ImageView user_image;
        final UserItemDetails userItemDetails;


        UserViewHolder(UserItemPosition pos, View v) {
            super(v);
            user_date = v.findViewById(R.id.user_date);
            user_image = v.findViewById(R.id.user_image);
            user_alias = v.findViewById(R.id.user_alias);
            user_action = v.findViewById(R.id.user_action);
            userItemDetails = new UserItemDetails(pos);
        }

        void bind(boolean isSelected, User user) {

            userItemDetails.pid = user.getPid();

            itemView.setActivated(isSelected);


        }

        ItemDetailsLookup.ItemDetails<String> getUserItemDetails() {

            return userItemDetails;
        }
    }
}
