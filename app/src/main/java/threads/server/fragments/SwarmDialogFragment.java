package threads.server.fragments;

import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;
import java.util.Objects;

import lite.Peer;
import threads.LogUtils;
import threads.server.R;
import threads.server.core.events.EVENTS;
import threads.server.core.peers.Content;
import threads.server.ipfs.IPFS;
import threads.server.services.QRCodeService;
import threads.server.utils.PeersViewAdapter;

public class SwarmDialogFragment extends BottomSheetDialogFragment implements
        PeersViewAdapter.PeersViewAdapterListener {

    public static final String TAG = SwarmDialogFragment.class.getSimpleName();
    private static final int CLICK_OFFSET = 500;
    private Context mContext;
    private long mLastClickTime = 0;

    public static SwarmDialogFragment newInstance() {
        return new SwarmDialogFragment();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        Dialog dialog = new Dialog(mContext, R.style.ThreadsThemeDialog);
        dialog.setContentView(R.layout.swarm_view);


        Toolbar mToolbar = dialog.findViewById(R.id.toolbar);
        Objects.requireNonNull(mToolbar);
        mToolbar.setTitle(R.string.swarm);
        mToolbar.setNavigationIcon(R.drawable.arrow_left);
        mToolbar.setNavigationOnClickListener(v -> {
            if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                return;
            }
            mLastClickTime = SystemClock.elapsedRealtime();

            dismiss();
        });


        RecyclerView mRecyclerView = dialog.findViewById(R.id.recycler_peers);
        Objects.requireNonNull(mRecyclerView);
        mRecyclerView.setItemAnimator(null); // no animation of the item when something changed


        LinearLayoutManager linearLayout = new LinearLayoutManager(getContext());
        mRecyclerView.setLayoutManager(linearLayout);
        PeersViewAdapter peersViewAdapter = new PeersViewAdapter(this);
        mRecyclerView.setAdapter(peersViewAdapter);

        IPFS ipfs = IPFS.getInstance(mContext);

        List<String> peers = ipfs.swarmPeers();
        try {
            peersViewAdapter.updateData(peers);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }

        Window window = dialog.getWindow();
        if (window != null) {
            window.getAttributes().windowAnimations = R.style.DialogAnimation;
        }
        return dialog;
    }


    @Override
    public void invokeAction(@NonNull String peer, @NonNull View view) {

        if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
            return;
        }
        mLastClickTime = SystemClock.elapsedRealtime();
        try {

            PopupMenu menu = new PopupMenu(mContext, view);
            menu.inflate(R.menu.popup_swarm_menu);


            menu.setOnMenuItemClickListener((item) -> {


                if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                    return true;
                }

                mLastClickTime = SystemClock.elapsedRealtime();
                if (item.getItemId() == R.id.popup_info) {
                    clickPeerInfo(peer);
                    return true;
                } else if (item.getItemId() == R.id.popup_add) {
                    clickPeerAdd(peer);
                    return true;
                }
                return false;

            });

            menu.show();


        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }

    }


    private void clickPeerInfo(@NonNull String pid) {

        try {
            IPFS ipfs = IPFS.getInstance(mContext);
            String address = "";
            Peer info = ipfs.swarmPeer(pid);
            if (info != null) {
                address = info.getAddress();
            }
            Uri uri = QRCodeService.getImage(mContext, pid);
            InfoDialogFragment.newInstance(uri, pid,
                    getString(R.string.peer_access, pid),
                    address)
                    .show(getParentFragmentManager(), InfoDialogFragment.TAG);

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        } finally {
            dismiss();
        }
    }


    private void clickPeerAdd(@NonNull String pid) {

        try {
            // CHECKED if pid is valid
            if (!IPFS.getInstance(mContext).isValidPID(pid)) {
                EVENTS.getInstance(mContext).error(getString(R.string.pid_not_valid));
                return;
            }

            // CHECKED
            String host = IPFS.getPeerID(mContext);

            if (pid.equals(host)) {
                EVENTS.getInstance(mContext).warning(getString(R.string.same_pid_like_host));
                return;
            }

            Peer info = IPFS.getInstance(mContext).swarmPeer(pid);
            String address = null;
            if (info != null) {
                address = info.getAddress();
            }

            if (address != null && address.contains(Content.CIRCUIT)) {
                address = null;
            }

            EditPeerDialogFragment.newInstance(pid, address).show(
                    getParentFragmentManager(), EditPeerDialogFragment.TAG);

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        } finally {
            dismiss();
        }


    }

}
