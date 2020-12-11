package threads.server.fragments;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import lite.Peer;
import threads.LogUtils;
import threads.server.MainActivity;
import threads.server.R;
import threads.server.core.events.EVENTS;
import threads.server.core.peers.Content;
import threads.server.core.peers.PEERS;
import threads.server.core.peers.User;
import threads.server.core.peers.UsersViewModel;
import threads.server.ipfs.IPFS;
import threads.server.services.QRCodeService;
import threads.server.services.UserService;
import threads.server.utils.MimeType;
import threads.server.utils.Network;
import threads.server.utils.UserItemDetailsLookup;
import threads.server.utils.UsersItemKeyProvider;
import threads.server.utils.UsersViewAdapter;
import threads.server.work.UserConnectWorker;

public class PeersFragment extends Fragment implements
        SwipeRefreshLayout.OnRefreshListener, UsersViewAdapter.UsersViewAdapterListener {

    private static final String TAG = PeersFragment.class.getSimpleName();
    private static final int CLICK_OFFSET = 500;
    @NonNull
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean run = new AtomicBoolean(false);
    private long mLastClickTime = 0;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private UsersViewAdapter mUsersViewAdapter;
    private Context mContext;
    private SelectionTracker<String> mSelectionTracker;
    private ActionMode mActionMode;
    private FragmentActivity mActivity;
    private PeersFragment.ActionListener mListener;

    private static void checkUsers(@NonNull Context context) {

        try {

            PEERS peers = PEERS.getInstance(context);

            IPFS ipfs = IPFS.getInstance(context);

            List<String> users = peers.getUsersPids();

            for (String pid : users) {
                try {
                    boolean value = ipfs.isConnected(pid);
                    boolean preValue = peers.isUserConnected(pid);

                    if (preValue != value) {
                        if (value) {
                            peers.setUserConnected(pid);
                        } else {
                            peers.setUserDisconnected(pid);
                        }
                    }

                } catch (Throwable e) {
                    LogUtils.error(TAG, e);
                }

            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
        mListener = null;
        mActivity = null;
        run.set(false);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
        mActivity = getActivity();
        mListener = (PeersFragment.ActionListener) mActivity;
        run.set(true);
        peersOnlineStatus();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        mSelectionTracker.onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        super.onCreateOptionsMenu(menu, menuInflater);
        menuInflater.inflate(R.menu.menu_peers_fragment, menu);
    }

    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        int itemId = item.getItemId();
        if (itemId == R.id.action_id) {

            if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                return true;
            }

            mLastClickTime = SystemClock.elapsedRealtime();


            String host = IPFS.getPeerID(mContext);
            Objects.requireNonNull(host);

            Uri uri = QRCodeService.getImage(mContext, host);

            AccountDialogFragment.newInstance(uri).show(
                    getChildFragmentManager(), AccountDialogFragment.TAG);


            return true;
        } else if (itemId == R.id.action_select_all) {


            if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                return true;
            }

            mLastClickTime = SystemClock.elapsedRealtime();

            mUsersViewAdapter.selectAllUsers();

            return true;

        } else if (itemId == R.id.action_swarm) {

            if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                return true;
            }
            mLastClickTime = SystemClock.elapsedRealtime();

            SwarmDialogFragment dialogFragment = SwarmDialogFragment.newInstance();
            dialogFragment.show(getChildFragmentManager(), SwarmDialogFragment.TAG);

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.peers_view, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView mRecyclerView = view.findViewById(R.id.recycler_users);

        mSwipeRefreshLayout = view.findViewById(R.id.swipe_container);
        mSwipeRefreshLayout.setOnRefreshListener(this);
        mSwipeRefreshLayout.setColorSchemeResources(R.color.colorAccent,
                android.R.color.holo_green_dark,
                android.R.color.holo_orange_dark,
                android.R.color.holo_blue_dark);


        LinearLayoutManager linearLayout = new LinearLayoutManager(getContext());
        mRecyclerView.setLayoutManager(linearLayout);
        mUsersViewAdapter = new UsersViewAdapter(mContext, this);
        mRecyclerView.setAdapter(mUsersViewAdapter);

        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                boolean hasSelection = mSelectionTracker.hasSelection();
                if (dy > 0 && !hasSelection) {
                    mListener.showFab(false);
                } else if (dy < 0 && !hasSelection) {
                    mListener.showFab(true);
                }

            }
        });

        mSelectionTracker = new SelectionTracker.Builder<>(
                "user-selection",//unique id
                mRecyclerView,
                new UsersItemKeyProvider(mUsersViewAdapter),
                new UserItemDetailsLookup(mRecyclerView),
                StorageStrategy.createStringStorage())
                .build();


        mSelectionTracker.addObserver(new SelectionTracker.SelectionObserver<String>() {
            @Override
            public void onSelectionChanged() {
                if (!mSelectionTracker.hasSelection()) {
                    if (mActionMode != null) {
                        mActionMode.finish();
                    }
                } else {
                    if (mActionMode == null) {
                        mActionMode = ((AppCompatActivity) mActivity).startSupportActionMode(
                                createActionModeCallback());
                    }
                }
                if (mActionMode != null) {
                    mActionMode.setTitle("" + mSelectionTracker.getSelection().size());
                }
                super.onSelectionChanged();
            }

            @Override
            public void onSelectionRestored() {
                if (!mSelectionTracker.hasSelection()) {
                    if (mActionMode != null) {
                        mActionMode.finish();
                    }
                } else {
                    if (mActionMode == null) {
                        mActionMode = ((AppCompatActivity) mActivity).startSupportActionMode(
                                createActionModeCallback());
                    }
                }
                if (mActionMode != null) {
                    mActionMode.setTitle("" + mSelectionTracker.getSelection().size());
                }
                super.onSelectionRestored();
            }
        });

        mUsersViewAdapter.setSelectionTracker(mSelectionTracker);


        if (savedInstanceState != null) {
            mSelectionTracker.onRestoreInstanceState(savedInstanceState);
        }


        UsersViewModel messagesViewModel = new ViewModelProvider(this).get(UsersViewModel.class);
        messagesViewModel.getUsers().observe(getViewLifecycleOwner(), (peers) -> {

            try {
                if (peers != null) {
                    mUsersViewAdapter.updateData(peers);
                }
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }

        });

    }

    private ActionMode.Callback createActionModeCallback() {
        return new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.menu_users_action_mode, menu);

                mListener.showFab(false);
                mHandler.post(() -> mUsersViewAdapter.notifyDataSetChanged());

                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {

                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.action_mode_mark_all) {

                    if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                        return true;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    mUsersViewAdapter.selectAllUsers();

                    return true;
                } else if (itemId == R.id.action_mode_connect) {

                    if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                        return true;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    if (!Network.isConnected(mContext)) {
                        EVENTS.getInstance(mContext).warning(getString(R.string.offline_mode));
                    }

                    try {
                        Selection<String> entries = mSelectionTracker.getSelection();
                        String[] pids = new String[entries.size()];
                        int i = 0;
                        for (String pid : entries) {
                            pids[i] = pid;
                            i++;
                        }
                        connectUsers(pids);
                        mSelectionTracker.clearSelection();

                    } catch (Throwable e) {
                        LogUtils.error(TAG, e);
                    }
                } else if (itemId == R.id.action_mode_delete) {

                    if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                        return true;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    try {
                        String[] entries = convert(mSelectionTracker.getSelection());

                        UserService.removeUsers(mContext, entries);

                        mSelectionTracker.clearSelection();

                    } catch (Throwable e) {
                        LogUtils.error(TAG, e);
                    }

                    return true;


                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {

                mSelectionTracker.clearSelection();
                mListener.showFab(true);

                if (mActionMode != null) {
                    mActionMode = null;
                }
                mHandler.post(() -> mUsersViewAdapter.notifyDataSetChanged());

            }
        };

    }

    private String[] convert(Selection<String> entries) {
        int i = 0;

        String[] basic = new String[entries.size()];
        for (String entry : entries) {
            basic[i] = entry;
            i++;
        }

        return basic;
    }

    @Override
    public void invokeAction(@NonNull User user, @NonNull View view) {
        if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
            return;
        }
        mLastClickTime = SystemClock.elapsedRealtime();
        try {

            boolean isConnected = user.isConnected();
            PopupMenu menu = new PopupMenu(mContext, view);
            menu.inflate(R.menu.popup_peers_menu);
            menu.getMenu().findItem(R.id.popup_connect).setVisible(!isConnected);
            menu.getMenu().findItem(R.id.popup_share).setVisible(true);
            menu.getMenu().findItem(R.id.popup_view).setVisible(user.isLite());
            menu.setOnMenuItemClickListener((item) -> {


                if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                    return true;
                }

                mLastClickTime = SystemClock.elapsedRealtime();


                if (item.getItemId() == R.id.popup_connect) {
                    if (!Network.isConnected(mContext)) {
                        EVENTS.getInstance(mContext).warning(getString(R.string.offline_mode));
                    }
                    connectUser(user);
                    return true;
                } else if (item.getItemId() == R.id.popup_delete) {
                    clickUserDelete(user.getPid());
                    return true;
                } else if (item.getItemId() == R.id.popup_info) {
                    clickUserInfo(user);
                    return true;
                } else if (item.getItemId() == R.id.popup_share) {
                    clickUserShare(user);
                    return true;
                } else if (item.getItemId() == R.id.popup_rename) {
                    clickUserRename(user.getPid());
                    return true;
                } else if (item.getItemId() == R.id.popup_view) {
                    clickUserView(user.getPid());
                    return true;
                }
                return false;

            });

            menu.show();


        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    private void clickUserView(@NonNull String pid) {
        try {
            IPFS ipfs = IPFS.getInstance(mContext);
            String uri = Content.IPNS + "://" + ipfs.base32(pid);
            mListener.openBrowserView(Uri.parse(uri));
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    @Override
    public void invokeAbortDialing(@NonNull User user) {

        if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
            return;
        }
        mLastClickTime = SystemClock.elapsedRealtime();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() ->
                PEERS.getInstance(mContext).resetUserDialing(user.getPid())
        );
        UUID uuid = user.getWorkUUID();
        if (uuid != null) {
            WorkManager.getInstance(mContext).cancelWorkById(uuid);
        }

    }

    @Override
    public void onClick(@NonNull User user) {
        if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
            return;
        }
        mLastClickTime = SystemClock.elapsedRealtime();

        try {
            if (!mSelectionTracker.hasSelection()) {


                if (mActionMode != null) {
                    mActionMode.finish();
                }

                if (user.isLite()) {
                    clickUserView(user.getPid());
                }

            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    @Override
    public void onRefresh() {
        mSwipeRefreshLayout.setRefreshing(true);

        try {

            if (!Network.isConnected(mContext)) {
                EVENTS.getInstance(mContext).warning(getString(R.string.offline_mode));
            }


            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit(() -> {
                PEERS peers = PEERS.getInstance(mContext);
                List<User> users = peers.getUsers();
                List<OneTimeWorkRequest> works = new ArrayList<>();
                for (User user : users) {

                    if (Network.isConnected(mContext)) {
                        peers.setUserDialing(user.getPid());
                    }
                    OneTimeWorkRequest work = UserConnectWorker.getWork(user.getPid());
                    peers.setUserWork(user.getPid(), work.getId());
                    works.add(work);

                }
                WorkManager.getInstance(mContext).enqueue(works);
            });


        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        } finally {
            mSwipeRefreshLayout.setRefreshing(false);
        }

    }

    private void clickUserDelete(@NonNull String pid) {
        UserService.removeUsers(mContext, pid);
    }


    private void clickUserShare(@NonNull User user) {

        try {

            ComponentName[] names = {new ComponentName(
                    mContext.getApplicationContext(), MainActivity.class)};

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_TEXT, user.getPid());
            intent.setType(MimeType.PLAIN_MIME_TYPE);
            intent.putExtra(Intent.EXTRA_SUBJECT, user.getAlias());
            intent.putExtra(Intent.EXTRA_TITLE, user.getAlias());


            Intent chooser = Intent.createChooser(intent, getText(R.string.share));
            chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, names);
            chooser.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(chooser);


        } catch (Throwable ignore) {
            EVENTS.getInstance(mContext).
                    warning(getString(R.string.no_activity_found_to_handle_uri));
        }
    }

    private void clickUserInfo(@NonNull User user) {

        try {
            IPFS ipfs = IPFS.getInstance(mContext);
            String address = "";
            Peer info = ipfs.swarmPeer(user.getPid());
            if (info != null) {
                address = info.getAddress();
            }
            Uri uri = QRCodeService.getImage(mContext, user.getPid());
            InfoDialogFragment.newInstance(uri, user.getPid(),
                    getString(R.string.peer_access, user.getAlias()),
                    address)
                    .show(getChildFragmentManager(), InfoDialogFragment.TAG);

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    private void connectUsers(@NonNull String... pids) {

        PEERS peers = PEERS.getInstance(mContext);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            List<OneTimeWorkRequest> works = new ArrayList<>();

            for (String pid : pids) {


                OneTimeWorkRequest work = UserConnectWorker.getWork(pid);

                if (Network.isConnected(mContext)) {
                    peers.setUserDialing(pid);
                }
                peers.setUserWork(pid, work.getId());


                works.add(work);


            }

            WorkManager.getInstance(mContext).enqueue(works);
        });
    }

    private void connectUser(@NonNull User user) {

        OneTimeWorkRequest work = UserConnectWorker.getWork(user.getPid());
        WorkManager.getInstance(mContext).enqueue(work);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            PEERS peers = PEERS.getInstance(mContext);
            if (Network.isConnected(mContext)) {
                peers.setUserDialing(user.getPid());
            }
            peers.setUserWork(user.getPid(), work.getId());
        });

    }

    private void clickUserRename(@NonNull String pid) {

        try {
            NameDialogFragment.newInstance(pid, getString(R.string.peer_rename_title))
                    .show(getChildFragmentManager(), NameDialogFragment.TAG);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    private void peersOnlineStatus() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                while (run.get()) {
                    checkUsers(mContext);
                    java.lang.Thread.sleep(1000);
                }
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }
        });
    }


    public interface ActionListener {

        void showFab(boolean b);

        void openBrowserView(@NonNull Uri uri);
    }
}
