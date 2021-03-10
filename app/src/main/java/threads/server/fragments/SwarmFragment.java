package threads.server.fragments;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.List;
import java.util.Objects;

import io.ipfs.LogUtils;
import lite.Peer;
import threads.server.R;
import threads.server.core.Content;
import threads.server.core.events.EVENTS;
import threads.server.ipfs.IPFS;
import threads.server.services.QRCodeService;
import threads.server.utils.PeersViewAdapter;

public class SwarmFragment extends Fragment implements
        SwipeRefreshLayout.OnRefreshListener,
        PeersViewAdapter.PeersViewAdapterListener {

    public static final String TAG = SwarmFragment.class.getSimpleName();
    private static final int CLICK_OFFSET = 500;
    private Context mContext;
    private long mLastClickTime = 0;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private PeersViewAdapter mSwarmAdapter;
    private SwarmFragment.ActionListener mListener;


    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
        mListener = null;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
        mListener = (SwarmFragment.ActionListener) getActivity();
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.swarm_view, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        RecyclerView mRecyclerView = view.findViewById(R.id.recycler_peers);
        Objects.requireNonNull(mRecyclerView);
        mRecyclerView.setItemAnimator(null); // no animation of the item when something changed


        mSwipeRefreshLayout = view.findViewById(R.id.swipe_container);
        mSwipeRefreshLayout.setOnRefreshListener(this);
        mSwipeRefreshLayout.setColorSchemeResources(R.color.colorPrimary);

        LinearLayoutManager linearLayout = new LinearLayoutManager(getContext());
        mRecyclerView.setLayoutManager(linearLayout);
        mSwarmAdapter = new PeersViewAdapter(this);
        mRecyclerView.setAdapter(mSwarmAdapter);

        updateData();

    }

    public void updateData() {
        if (isResumed()) {
            try {
                IPFS ipfs = IPFS.getInstance(mContext);
                List<String> peers = ipfs.swarmPeers();
                peers.sort(String::compareTo);
                mSwarmAdapter.updateData(peers);
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }
        }
    }

    @Override
    public void onClick(@NonNull String peer) {
        if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
            return;
        }
        mLastClickTime = SystemClock.elapsedRealtime();

        try {
            IPFS ipfs = IPFS.getInstance(mContext);
            String uri = Content.IPNS + "://" + ipfs.base32(peer);
            mListener.openBrowserView(Uri.parse(uri));
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
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

    private void clickPeerAdd(@NonNull String pid) {

        try {
            IPFS ipfs = IPFS.getInstance(mContext);
            // CHECKED if pid is valid
            if (ipfs.decodeName(pid).isEmpty()) {
                EVENTS.getInstance(mContext).error(getString(R.string.pid_not_valid));
                return;
            }

            // CHECKED
            String peerID = ipfs.getPeerID();

            if (pid.equals(peerID)) {
                EVENTS.getInstance(mContext).warning(getString(R.string.same_pid_like_host));
                return;
            }

            Peer info = ipfs.swarmPeer(pid);
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
        }
    }

    @Override
    public void onRefresh() {
        mSwipeRefreshLayout.setRefreshing(true);

        try {
            updateData();
            EVENTS.getInstance(mContext).warning(getString(R.string.refreshing));
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        } finally {
            mSwipeRefreshLayout.setRefreshing(false);
        }

    }

    public void enableSwipeRefresh(boolean enable) {
        try {
            if (isResumed()) {
                mSwipeRefreshLayout.setEnabled(enable);
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }


    public interface ActionListener {

        void openBrowserView(@NonNull Uri uri);
    }
}
