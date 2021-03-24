package threads.server.utils;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.LogUtils;
import threads.server.R;


public class PeersViewAdapter extends RecyclerView.Adapter<PeersViewAdapter.ViewHolder> {

    private static final String TAG = PeersViewAdapter.class.getSimpleName();
    private final List<String> peers = new ArrayList<>();

    private final PeersViewAdapterListener mListener;


    public PeersViewAdapter(@NonNull PeersViewAdapterListener listener) {
        this.mListener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return R.layout.peers;
    }

    @Override
    @NonNull
    public PeerViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                             int viewType) {

        View v = LayoutInflater.from(parent.getContext())
                .inflate(viewType, parent, false);
        return new PeerViewHolder(v);

    }


    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        final String peer = peers.get(position);

        if (holder instanceof PeerViewHolder) {
            PeerViewHolder peerViewHolder = (PeerViewHolder) holder;

            try {


                peerViewHolder.user_action.setVisibility(View.VISIBLE);
                peerViewHolder.user_action.setOnClickListener((v) ->
                        mListener.invokeAction(peer, v)
                );

                int res = R.drawable.server_network;
                int color = ColorGenerator.MATERIAL.getColor(peer);
                peerViewHolder.user_image.setImageResource(res);
                peerViewHolder.user_image.setColorFilter(color);


                peerViewHolder.view.setOnClickListener((v) -> {
                    try {
                        mListener.onClick(peer);
                    } catch (Throwable e) {
                        LogUtils.error(TAG, e);
                    }
                });


                peerViewHolder.user_alias.setText(peer);

            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }

        }

    }


    @Override
    public int getItemCount() {
        return peers.size();
    }


    public void updateData(@NonNull List<String> peers) {

        final PeerDiffCallback diffCallback = new PeerDiffCallback(this.peers, peers);
        final DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);

        this.peers.clear();
        this.peers.addAll(peers);
        diffResult.dispatchUpdatesTo(this);
    }


    public interface PeersViewAdapterListener {
        void invokeAction(@NonNull String peer, @NonNull View view);

        void onClick(@NonNull String peer);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        ViewHolder(View v) {
            super(v);
        }
    }


    static class PeerViewHolder extends ViewHolder {
        final View view;
        final TextView user_alias;
        final ImageView user_action;
        final ImageView user_image;

        PeerViewHolder(View v) {
            super(v);
            view = v;
            user_image = v.findViewById(R.id.user_image);
            user_alias = v.findViewById(R.id.user_alias);
            user_action = v.findViewById(R.id.user_action);

        }
    }
}
