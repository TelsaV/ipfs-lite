package threads.server.fragments;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.DocumentsContract;
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
import androidx.work.WorkManager;

import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import threads.LogUtils;
import threads.server.MainActivity;
import threads.server.R;
import threads.server.core.DOCS;
import threads.server.core.events.EVENTS;
import threads.server.core.peers.Content;
import threads.server.core.threads.PinsViewModel;
import threads.server.core.threads.THREADS;
import threads.server.core.threads.Thread;
import threads.server.ipfs.CID;
import threads.server.ipfs.IPFS;
import threads.server.services.LiteService;
import threads.server.services.QRCodeService;
import threads.server.services.ThreadsService;
import threads.server.utils.MimeType;
import threads.server.utils.PinsItemDetailsLookup;
import threads.server.utils.PinsItemKeyProvider;
import threads.server.utils.PinsViewAdapter;
import threads.server.work.PageWorker;

public class PinsFragment extends Fragment implements
        SwipeRefreshLayout.OnRefreshListener, PinsViewAdapter.PinsViewAdapterListener {

    private static final String TAG = PinsFragment.class.getSimpleName();

    private static final int CLICK_OFFSET = 500;

    @NonNull
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private PinsViewAdapter mPinsViewAdapter;
    private long mLastClickTime = 0;
    private Context mContext;
    private FragmentActivity mActivity;
    private PinsFragment.ActionListener mListener;
    private RecyclerView mRecyclerView;
    private ActionMode mActionMode;
    private SelectionTracker<Long> mSelectionTracker;
    private SwipeRefreshLayout mSwipeRefreshLayout;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
        mActivity = getActivity();
        mListener = (PinsFragment.ActionListener) mActivity;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
        mActivity = null;
        mListener = null;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mSelectionTracker != null) {
            mSelectionTracker.onSaveInstanceState(outState);
        }

    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        super.onCreateOptionsMenu(menu, menuInflater);
        menuInflater.inflate(R.menu.menu_pins_fragment, menu);
    }


    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        int itemId = item.getItemId();
        if (itemId == R.id.action_share) {

            try {

                if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                    return true;
                }
                mLastClickTime = SystemClock.elapsedRealtime();

                DOCS docs = DOCS.getInstance(mContext);
                Uri uri = docs.getPinsPageUri();


                ComponentName[] names = {new ComponentName(mContext, MainActivity.class)};

                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_link));
                intent.putExtra(Intent.EXTRA_TEXT, uri.toString());
                intent.setType(MimeType.PLAIN_MIME_TYPE);
                intent.putExtra(DocumentsContract.EXTRA_EXCLUDE_SELF, true);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);


                Intent chooser = Intent.createChooser(intent, getText(R.string.share));
                chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, names);
                chooser.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(chooser);

            } catch (Throwable ignore) {
                EVENTS.getInstance(mContext).warning(
                        getString(R.string.no_activity_found_to_handle_uri));
            }
            return true;
        } else if (itemId == R.id.action_select_all) {


            if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                return true;
            }

            mLastClickTime = SystemClock.elapsedRealtime();

            mPinsViewAdapter.selectAllThreads();

            return true;

        } else if (itemId == R.id.action_view) {


            if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                return true;
            }

            mLastClickTime = SystemClock.elapsedRealtime();


            try {
                DOCS docs = DOCS.getInstance(mContext);
                String content = docs.getHost();
                String gateway = LiteService.getGateway(mContext);
                String url = gateway + "/" + Content.IPNS + "/" + content;

                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);

            } catch (Throwable e) {
                EVENTS.getInstance(mContext).warning(
                        getString(R.string.no_activity_found_to_handle_uri));
            }

            return true;

        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.pins_view, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        IPFS ipfs = IPFS.getInstance(mContext);
        PinsViewModel mPinsViewModel = new ViewModelProvider(this).get(PinsViewModel.class);
        mPinsViewModel.getVisiblePinnedThreads(ipfs.getLocation()).observe(getViewLifecycleOwner(), (threads) -> {
            try {
                if (threads != null) {

                    threads.sort(Comparator.comparing(Thread::getLastModified).reversed());

                    int size = mPinsViewAdapter.getItemCount();
                    boolean scrollToTop = size < threads.size();

                    mPinsViewAdapter.updateData(threads);

                    if (scrollToTop) {
                        mRecyclerView.scrollToPosition(0);
                    }
                }
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });


        mRecyclerView = view.findViewById(R.id.recycler_view_pins);

        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(mContext);
        mRecyclerView.setLayoutManager(linearLayoutManager);

        mPinsViewAdapter = new PinsViewAdapter(mContext, this);
        mRecyclerView.setAdapter(mPinsViewAdapter);


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

        mSwipeRefreshLayout = view.findViewById(R.id.swipe_container);
        mSwipeRefreshLayout.setOnRefreshListener(this);
        mSwipeRefreshLayout.setColorSchemeResources(R.color.colorAccent,
                android.R.color.holo_green_dark,
                android.R.color.holo_orange_dark,
                android.R.color.holo_blue_dark);


        mSelectionTracker = new SelectionTracker.Builder<>(TAG, mRecyclerView,
                new PinsItemKeyProvider(mPinsViewAdapter),
                new PinsItemDetailsLookup(mRecyclerView),
                StorageStrategy.createLongStorage())
                .build();


        mSelectionTracker.addObserver(new SelectionTracker.SelectionObserver<Long>() {
            @Override
            public void onSelectionChanged() {
                if (!mSelectionTracker.hasSelection()) {
                    if (mActionMode != null) {
                        mActionMode.finish();
                    }
                } else {
                    if (mActionMode == null) {
                        mActionMode = ((AppCompatActivity)
                                mActivity).startSupportActionMode(
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
                        mActionMode = ((AppCompatActivity)
                                mActivity).startSupportActionMode(
                                createActionModeCallback());
                    }
                }
                if (mActionMode != null) {
                    mActionMode.setTitle("" + mSelectionTracker.getSelection().size());
                }
                super.onSelectionRestored();
            }
        });

        mPinsViewAdapter.setSelectionTracker(mSelectionTracker);


        if (savedInstanceState != null) {
            mSelectionTracker.onRestoreInstanceState(savedInstanceState);
        }

    }

    @Override
    public void onRefresh() {
        mSwipeRefreshLayout.setRefreshing(true);

        try {
            EVENTS.getInstance(mContext).warning(getString(R.string.publish_pins));

            PageWorker.publish(mContext, true);

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        } finally {
            mSwipeRefreshLayout.setRefreshing(false);
        }

    }


    private void unpinAction() {

        Selection<Long> selection = mSelectionTracker.getSelection();
        if (selection.size() == 0) {
            EVENTS.getInstance(mContext).warning(getString(R.string.no_marked_file_unpin));
            return;
        }

        try {

            long[] entries = convert(selection);

            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit(() -> {
                try {
                    THREADS threads = THREADS.getInstance(mContext);
                    DOCS docs = DOCS.getInstance(mContext);

                    docs.removePagePins(entries);

                    for (long idx : entries) {
                        Thread thread = threads.getThreadByIdx(idx);
                        if (thread != null) {
                            UUID uuid = thread.getWorkUUID();
                            if (uuid != null) {
                                WorkManager.getInstance(mContext).cancelWorkById(uuid);
                            }
                        }
                    }

                } catch (Throwable e) {
                    LogUtils.error(TAG, e);
                }
            });


            mSelectionTracker.clearSelection();
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    private long[] convert(Selection<Long> entries) {
        int i = 0;

        long[] basic = new long[entries.size()];
        for (Long entry : entries) {
            basic[i] = entry;
            i++;
        }

        return basic;
    }

    private void deleteAction() {


        if (!mSelectionTracker.hasSelection()) {
            EVENTS.getInstance(mContext).warning(getString(R.string.no_marked_file_delete));
            return;
        }


        try {
            long[] entries = convert(mSelectionTracker.getSelection());

            ThreadsService.removeThreads(mContext, entries);

            mSelectionTracker.clearSelection();

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }

    }

    @Override
    public void invokeAction(@NonNull Thread thread, @NonNull View view) {

        if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
            return;
        }
        mLastClickTime = SystemClock.elapsedRealtime();

        try {


            PopupMenu menu = new PopupMenu(mContext, view);
            menu.inflate(R.menu.popup_pins_menu);
            menu.getMenu().findItem(R.id.popup_rename).setVisible(true);
            menu.getMenu().findItem(R.id.popup_unpin).setVisible(true);

            menu.setOnMenuItemClickListener((item) -> {

                if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                    return true;
                }
                mLastClickTime = SystemClock.elapsedRealtime();

                if (item.getItemId() == R.id.popup_info) {
                    clickThreadInfo(thread);
                    return true;
                } else if (item.getItemId() == R.id.popup_view) {
                    viewGateway(thread);
                    return true;
                } else if (item.getItemId() == R.id.popup_delete) {
                    clickThreadDelete(thread.getIdx());
                    return true;
                } else if (item.getItemId() == R.id.popup_unpin) {
                    clickUnpin(thread);
                    return true;
                } else if (item.getItemId() == R.id.popup_rename) {
                    clickThreadRename(thread);
                    return true;
                }
                return false;

            });

            menu.show();


        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }


    }


    private void clickThreadRename(@NonNull Thread thread) {
        try {
            RenameFileDialogFragment.newInstance(thread.getIdx(), thread.getName()).
                    show(getChildFragmentManager(), RenameFileDialogFragment.TAG);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }


    private void clickUnpin(@NonNull Thread thread) {

        final DOCS docs = DOCS.getInstance(mContext);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                docs.removePagePins(thread.getIdx());
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }
        });


        UUID uuid = thread.getWorkUUID();
        if (uuid != null) {
            WorkManager.getInstance(mContext).cancelWorkById(uuid);
        }
    }

    private ActionMode.Callback createActionModeCallback() {
        return new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.menu_pins_action_mode, menu);


                mListener.showFab(false);

                mHandler.post(() -> mPinsViewAdapter.notifyDataSetChanged());

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

                    mPinsViewAdapter.selectAllThreads();

                    return true;
                } else if (itemId == R.id.action_mode_delete) {


                    if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                        return true;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    deleteAction();

                    return true;
                } else if (itemId == R.id.action_mode_unpin) {


                    if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                        return true;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    unpinAction();

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
                mHandler.post(() -> mPinsViewAdapter.notifyDataSetChanged());

            }
        };

    }


    private void clickThreadInfo(@NonNull Thread thread) {
        try {
            String uri = "";

            CID cid = thread.getContent();
            Objects.requireNonNull(cid);
            if (thread.isPinned()) {
                uri = Content.IPFS + "://" + cid.getCid();
            }
            String multihash = cid.getCid();
            Uri uriImage = QRCodeService.getImage(mContext, multihash);
            ContentDialogFragment.newInstance(uriImage, multihash,
                    getString(R.string.multi_hash_access, thread.getName()), uri)
                    .show(getChildFragmentManager(), ContentDialogFragment.TAG);


        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }

    }


    private void viewGateway(@NonNull Thread thread) {

        try {
            CID cid = thread.getContent();
            Objects.requireNonNull(cid);

            String gateway = LiteService.getGateway(mContext);
            String uri = gateway + "/" + Content.IPFS + "/" + cid.getCid();
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Throwable e) {
            EVENTS.getInstance(mContext).warning(
                    getString(R.string.no_activity_found_to_handle_uri));
        }
    }

    private void clickThreadDelete(long idx) {
        ThreadsService.removeThreads(mContext, idx);
    }


    public interface ActionListener {


        void showFab(boolean b);
    }
}
