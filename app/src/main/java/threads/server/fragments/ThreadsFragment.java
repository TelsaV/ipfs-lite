package threads.server.fragments;


import android.app.Activity;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.WorkManager;

import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import threads.LogUtils;
import threads.server.ExoPlayerActivity;
import threads.server.InitApplication;
import threads.server.MainActivity;
import threads.server.R;
import threads.server.core.Content;
import threads.server.core.DOCS;
import threads.server.core.events.EVENTS;
import threads.server.core.pages.PAGES;
import threads.server.core.threads.SortOrder;
import threads.server.core.threads.THREADS;
import threads.server.core.threads.Thread;
import threads.server.core.threads.ThreadViewModel;
import threads.server.ipfs.IPFS;
import threads.server.provider.FileDocumentsProvider;
import threads.server.services.LiteService;
import threads.server.services.QRCodeService;
import threads.server.services.ThreadsService;
import threads.server.services.UploadService;
import threads.server.utils.Folder;
import threads.server.utils.MimeType;
import threads.server.utils.SelectionViewModel;
import threads.server.utils.ThreadItemDetailsLookup;
import threads.server.utils.ThreadsItemKeyProvider;
import threads.server.utils.ThreadsViewAdapter;
import threads.server.work.BackupWorker;
import threads.server.work.PageWorker;
import threads.server.work.UploadDirectoryWorker;
import threads.server.work.UploadFileWorker;
import threads.server.work.UploadFolderWorker;
import threads.server.work.UploadThreadWorker;


public class ThreadsFragment extends Fragment implements
        SwipeRefreshLayout.OnRefreshListener, ThreadsViewAdapter.ThreadsViewAdapterListener {

    private static final String TAG = ThreadsFragment.class.getSimpleName();


    private static final int CLICK_OFFSET = 500;
    @NonNull
    private final AtomicReference<LiveData<List<Thread>>> observer = new AtomicReference<>(null);
    @NonNull
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private SelectionViewModel mSelectionViewModel;
    private ThreadsViewAdapter mThreadsViewAdapter;
    private ThreadViewModel mThreadViewModel;
    private long mLastClickTime = 0;
    private Context mContext;


    private final ActivityResultLauncher<Intent> mDirExportForResult =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            if (result.getResultCode() == Activity.RESULT_OK) {
                                Intent data = result.getData();
                                try {
                                    Objects.requireNonNull(data);
                                    Uri uri = data.getData();
                                    Objects.requireNonNull(uri);

                                    if (!FileDocumentsProvider.hasWritePermission(mContext, uri)) {
                                        EVENTS.getInstance(mContext).error(
                                                getString(R.string.file_has_no_write_permission));
                                        return;
                                    }
                                    long parent = getThread(mContext);
                                    UploadDirectoryWorker.load(mContext, uri, parent);

                                } catch (Throwable throwable) {
                                    LogUtils.error(TAG, throwable);
                                }
                            }
                        }
                    });


    private final ActivityResultLauncher<Intent> mFolderImportForResult =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            if (result.getResultCode() == Activity.RESULT_OK) {
                                Intent data = result.getData();
                                try {
                                    Objects.requireNonNull(data);
                                    long parent = getThread(mContext);
                                    if (data.getClipData() != null) {
                                        ClipData mClipData = data.getClipData();
                                        int items = mClipData.getItemCount();
                                        if (items > 0) {
                                            for (int i = 0; i < items; i++) {
                                                ClipData.Item item = mClipData.getItemAt(i);
                                                Uri uri = item.getUri();

                                                if (!FileDocumentsProvider.hasReadPermission(mContext, uri)) {
                                                    EVENTS.getInstance(mContext).error(
                                                            getString(R.string.file_has_no_read_permission));
                                                    return;
                                                }

                                                if (FileDocumentsProvider.isPartial(mContext, uri)) {
                                                    EVENTS.getInstance(mContext).error(
                                                            getString(R.string.file_not_valid));
                                                    return;
                                                }

                                                UploadFolderWorker.load(mContext, parent, uri);
                                            }
                                        }
                                    } else {
                                        Uri uri = data.getData();
                                        if (uri != null) {
                                            if (!FileDocumentsProvider.hasReadPermission(mContext, uri)) {
                                                EVENTS.getInstance(mContext).error(
                                                        getString(R.string.file_has_no_read_permission));
                                                return;
                                            }

                                            if (FileDocumentsProvider.isPartial(mContext, uri)) {
                                                EVENTS.getInstance(mContext).error(
                                                        getString(R.string.file_not_valid));
                                                return;
                                            }

                                            UploadFolderWorker.load(mContext, parent, uri);
                                        }
                                    }
                                } catch (Throwable throwable) {
                                    LogUtils.error(TAG, throwable);
                                }
                            }
                        }
                    });


    private final ActivityResultLauncher<Intent> mFilesImportForResult =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            if (result.getResultCode() == Activity.RESULT_OK) {
                                Intent data = result.getData();
                                try {
                                    Objects.requireNonNull(data);


                                    if (data.getClipData() != null) {
                                        ClipData mClipData = data.getClipData();
                                        long parent = getThread(mContext);
                                        LiteService.files(mContext, mClipData, parent);

                                    } else if (data.getData() != null) {
                                        Uri uri = data.getData();
                                        Objects.requireNonNull(uri);
                                        if (!FileDocumentsProvider.hasReadPermission(mContext, uri)) {
                                            EVENTS.getInstance(mContext).error(
                                                    getString(R.string.file_has_no_read_permission));
                                            return;
                                        }

                                        if (FileDocumentsProvider.isPartial(mContext, uri)) {
                                            EVENTS.getInstance(mContext).error(
                                                    getString(R.string.file_not_valid));
                                            return;
                                        }

                                        long parent = getThread(mContext);

                                        UploadService.uploadFile(mContext, parent, uri);
                                    }

                                } catch (Throwable throwable) {
                                    LogUtils.error(TAG, throwable);
                                }
                            }
                        }
                    });


    private final ActivityResultLauncher<Intent> mBackupForResult =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            if (result.getResultCode() == Activity.RESULT_OK) {
                                Intent data = result.getData();
                                try {
                                    Objects.requireNonNull(data);
                                    Uri uri = data.getData();
                                    Objects.requireNonNull(uri);


                                    if (!FileDocumentsProvider.hasWritePermission(mContext, uri)) {
                                        EVENTS.getInstance(mContext).error(
                                                getString(R.string.file_has_no_write_permission));
                                        return;
                                    }
                                    BackupWorker.backup(mContext, uri);

                                } catch (Throwable throwable) {
                                    LogUtils.error(TAG, throwable);
                                }
                            }
                        }
                    });


    private final ActivityResultLauncher<Intent> mFileExportForResult =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            if (result.getResultCode() == Activity.RESULT_OK) {
                                Intent data = result.getData();
                                try {
                                    Objects.requireNonNull(data);
                                    Uri uri = data.getData();
                                    Objects.requireNonNull(uri);
                                    if (!FileDocumentsProvider.hasWritePermission(mContext, uri)) {
                                        EVENTS.getInstance(mContext).error(
                                                getString(R.string.file_has_no_write_permission));
                                        return;
                                    }
                                    long threadIdx = getThread(mContext);
                                    UploadFileWorker.load(mContext, uri, threadIdx);

                                } catch (Throwable throwable) {
                                    LogUtils.error(TAG, throwable);
                                }
                            }
                        }
                    });
    private FragmentActivity mActivity;
    private ThreadsFragment.ActionListener mListener;
    private RecyclerView mRecyclerView;

    private ActionMode mActionMode;
    private ActionMode mSearchActionMode;
    private SelectionTracker<Long> mSelectionTracker;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private ThreadItemDetailsLookup mThreadItemDetailsLookup;

    private SortOrder sortOrder = SortOrder.DATE;

    private static long getThread(@NonNull Context context) {

        SharedPreferences sharedPref = context.getSharedPreferences(
                TAG, Context.MODE_PRIVATE);
        return sharedPref.getLong(Content.IDX, -1);
    }

    private static void setThread(@NonNull Context context, long idx) {

        SharedPreferences sharedPref = context.getSharedPreferences(
                TAG, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putLong(Content.IDX, idx);
        editor.apply();
    }

    public static String left(String str, final int len) {
        if (str == null) {
            return "";
        }
        if (len < 0) {
            return "";
        }
        str = str.trim();
        if (str.length() <= len) {
            return str;
        }
        return str.substring(0, len).concat("...");
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
        mActivity = getActivity();
        mListener = (ThreadsFragment.ActionListener) getActivity();
        sortOrder = InitApplication.getSortOrder(context);
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
        menuInflater.inflate(R.menu.menu_threads_fragment, menu);

        Long value = mSelectionViewModel.getParentThread().getValue();
        boolean topLevel = Objects.equals(0L, value);


        MenuItem actionEditCode = menu.findItem(R.id.action_edit_cid);
        actionEditCode.setVisible(topLevel);

        MenuItem actionAddDir = menu.findItem(R.id.action_add_folder);
        actionAddDir.setVisible(true);

        MenuItem actionNewFolder = menu.findItem(R.id.action_new_folder);
        actionNewFolder.setVisible(true);

        MenuItem actionNewText = menu.findItem(R.id.action_text);
        actionNewText.setVisible(true);


        MenuItem actionBackup = menu.findItem(R.id.action_backup);
        actionBackup.setVisible(topLevel);


    }

    private void setSortOrder(@NonNull SortOrder sortOrder) {
        this.sortOrder = sortOrder;

        long parent = 0L;
        Long thread = mSelectionViewModel.getParentThread().getValue();
        if (thread != null) {
            parent = thread;
        }
        if (parent == 0L) {
            InitApplication.setSortOrder(mContext, sortOrder);
        } else {
            final long idx = parent;
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit(() -> {
                try {
                    THREADS threads = THREADS.getInstance(mContext);
                    threads.setThreadSortOrder(idx, sortOrder);
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }
            });
        }
    }

    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        int itemId = item.getItemId();
        if (itemId == R.id.action_threads_search) {

            if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                return true;
            }
            mLastClickTime = SystemClock.elapsedRealtime();

            try {
                mSearchActionMode = ((AppCompatActivity)
                        mActivity).startSupportActionMode(
                        createSearchActionModeCallback());

            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
            return true;
        } else if (itemId == R.id.action_sort) {


            if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                return true;
            }

            mLastClickTime = SystemClock.elapsedRealtime();

            SubMenu subMenu = item.getSubMenu();


            subMenu.getItem(0).setChecked(sortOrder == SortOrder.NAME);
            subMenu.getItem(1).setChecked(sortOrder == SortOrder.NAME_INVERSE);
            subMenu.getItem(2).setChecked(sortOrder == SortOrder.DATE);
            subMenu.getItem(3).setChecked(sortOrder == SortOrder.DATE_INVERSE);
            subMenu.getItem(4).setChecked(sortOrder == SortOrder.SIZE);
            subMenu.getItem(5).setChecked(sortOrder == SortOrder.SIZE_INVERSE);

            return true;

        } else if (itemId == R.id.sort_date) {

            setSortOrder(SortOrder.DATE);

            updateDirectory(mSelectionViewModel.getParentThread().getValue(),
                    mSelectionViewModel.getQuery().getValue(), true);
            return true;
        } else if (itemId == R.id.sort_date_inverse) {

            setSortOrder(SortOrder.DATE_INVERSE);

            updateDirectory(mSelectionViewModel.getParentThread().getValue(),
                    mSelectionViewModel.getQuery().getValue(), true);
            return true;
        } else if (itemId == R.id.sort_name) {

            setSortOrder(SortOrder.NAME);

            updateDirectory(mSelectionViewModel.getParentThread().getValue(),
                    mSelectionViewModel.getQuery().getValue(), true);
            return true;
        } else if (itemId == R.id.sort_name_inverse) {

            setSortOrder(SortOrder.NAME_INVERSE);

            updateDirectory(mSelectionViewModel.getParentThread().getValue(),
                    mSelectionViewModel.getQuery().getValue(), true);
            return true;
        } else if (itemId == R.id.sort_size) {

            setSortOrder(SortOrder.SIZE);

            updateDirectory(mSelectionViewModel.getParentThread().getValue(),
                    mSelectionViewModel.getQuery().getValue(), true);
            return true;
        } else if (itemId == R.id.sort_size_inverse) {

            setSortOrder(SortOrder.SIZE_INVERSE);

            updateDirectory(mSelectionViewModel.getParentThread().getValue(),
                    mSelectionViewModel.getQuery().getValue(), true);
            return true;
        } else if (itemId == R.id.action_share) {

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

        } else if (itemId == R.id.action_backup) {


            if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                return true;
            }

            mLastClickTime = SystemClock.elapsedRealtime();

            clickBackup();

            return true;

        } else if (itemId == R.id.action_text) {

            if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                return true;
            }

            mLastClickTime = SystemClock.elapsedRealtime();

            try {
                long parent = 0L;
                Long thread = mSelectionViewModel.getParentThread().getValue();
                if (thread != null) {
                    parent = thread;
                }

                TextDialogFragment.newInstance(parent).
                        show(getChildFragmentManager(), TextDialogFragment.TAG);
            } catch (Throwable e) {
                EVENTS.getInstance(mContext).warning(
                        getString(R.string.no_activity_found_to_handle_uri));
            }

            return true;

        } else if (itemId == R.id.action_edit_cid) {

            if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                return true;
            }

            mLastClickTime = SystemClock.elapsedRealtime();

            clickEditContent();
            return true;

        } else if (itemId == R.id.action_add_folder) {


            if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                return true;
            }

            mLastClickTime = SystemClock.elapsedRealtime();

            Long idx = mSelectionViewModel.getParentThread().getValue();
            Objects.requireNonNull(idx);

            clickImportFolder(idx);
            return true;

        } else if (itemId == R.id.action_new_folder) {


            if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                return true;
            }

            mLastClickTime = SystemClock.elapsedRealtime();

            clickNewFolder();
            return true;


        }
        return super.onOptionsItemSelected(item);
    }

    private void clickImportFolder(long idx) {
        try {
            setThread(mContext, idx);

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.putExtra(DocumentsContract.EXTRA_EXCLUDE_SELF, true);
            mFolderImportForResult.launch(intent);
        } catch (Throwable e) {
            EVENTS.getInstance(mContext).warning(
                    getString(R.string.no_activity_found_to_handle_uri));
        }
    }


    private void clickNewFolder() {
        try {
            long parent = 0L;
            Long thread = mSelectionViewModel.getParentThread().getValue();
            if (thread != null) {
                parent = thread;
            }

            NewFolderDialogFragment.newInstance(parent).
                    show(getChildFragmentManager(), NewFolderDialogFragment.TAG);
        } catch (Throwable e) {
            EVENTS.getInstance(mContext).warning(
                    getString(R.string.no_activity_found_to_handle_uri));
        }
    }

    private void clickBackup() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.putExtra(DocumentsContract.EXTRA_EXCLUDE_SELF, true);
            mBackupForResult.launch(intent);
        } catch (Throwable e) {
            EVENTS.getInstance(mContext).warning(
                    getString(R.string.no_activity_found_to_handle_uri));
        }
    }

    private void createFolderChips(@NonNull ChipGroup group, @NonNull List<Folder> folders) {

        for (int i = folders.size(); i < group.getChildCount(); i++) {
            group.removeViewAt(i);
        }

        int size = folders.size();
        for (int i = 0; i < size; i++) {
            Folder folder = folders.get(i);

            Chip mChip = (Chip) group.getChildAt(i);

            if (mChip == null) {

                mChip = (Chip) getLayoutInflater().inflate(R.layout.item_chip_folder,
                        null, false);
                group.addView(mChip);
            }

            mChip.setText(folder.getName());
            mChip.setChipBackgroundColorResource(R.color.colorChips);

            mChip.setOnCheckedChangeListener((compoundButton, b) -> {

                mSelectionTracker.clearSelection();
                mSelectionViewModel.setParentThread(folder.getIdx());
                mHandler.post(() -> mActivity.invalidateOptionsMenu());

            });

        }

    }

    private List<Folder> createFolders(long idx) {
        THREADS threads = THREADS.getInstance(mContext);
        List<Folder> folders = new ArrayList<>();
        List<Thread> ancestors = threads.getAncestors(idx);
        for (Thread thread : ancestors) {

            String name = left(thread.getName(), 20);

            folders.add(new Folder(name, thread.getIdx()));
        }
        return folders;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.threads_view, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        ChipGroup chipGroup = view.findViewById(R.id.folder_group);
        HorizontalScrollView scrollView = view.findViewById(R.id.folder_scroll_view);

        ImageView homeAction = view.findViewById(R.id.home_action);
        homeAction.setOnClickListener((v) -> {

            mSelectionTracker.clearSelection();
            mSelectionViewModel.setParentThread(0L);
            mHandler.post(() -> mActivity.invalidateOptionsMenu());

        });

        scrollView.setVisibility(View.GONE);


        mSelectionViewModel = new ViewModelProvider(mActivity).get(SelectionViewModel.class);


        mSelectionViewModel.getParentThread().observe(getViewLifecycleOwner(), (threadIdx) -> {

            if (threadIdx != null) {

                createFolderChips(chipGroup, createFolders(threadIdx));

                if (threadIdx == 0L) {
                    scrollView.setVisibility(View.GONE);
                } else {
                    scrollView.setVisibility(View.VISIBLE);
                }
                setSortOrder(threadIdx);

                updateDirectory(threadIdx,
                        mSelectionViewModel.getQuery().getValue(), false);


                scrollView.post(() -> scrollView.scrollTo(chipGroup.getRight(), chipGroup.getTop()));
            }

        });

        mSelectionViewModel.getQuery().observe(getViewLifecycleOwner(), (query) -> {

            if (query != null) {
                Long parent = mSelectionViewModel.getParentThread().getValue();
                updateDirectory(parent, query, false);
            }

        });

        mThreadViewModel = new ViewModelProvider(this).get(ThreadViewModel.class);

        mRecyclerView = view.findViewById(R.id.recycler_view_message_list);


        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(mContext);
        mRecyclerView.setLayoutManager(linearLayoutManager);

        mRecyclerView.setItemAnimator(null);

        mThreadsViewAdapter = new ThreadsViewAdapter(mContext, this);
        mRecyclerView.setAdapter(mThreadsViewAdapter);


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

        mThreadItemDetailsLookup = new ThreadItemDetailsLookup(mRecyclerView);


        mSwipeRefreshLayout = view.findViewById(R.id.swipe_container);
        mSwipeRefreshLayout.setOnRefreshListener(this);
        mSwipeRefreshLayout.setColorSchemeResources(R.color.colorAccent,
                android.R.color.holo_green_dark,
                android.R.color.holo_orange_dark,
                android.R.color.holo_blue_dark);

        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        mSwipeRefreshLayout.setDistanceToTriggerSync((int) metrics.density * 128);


        mSelectionTracker = new SelectionTracker.Builder<>(TAG, mRecyclerView,
                new ThreadsItemKeyProvider(mThreadsViewAdapter),
                mThreadItemDetailsLookup,
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

        mThreadsViewAdapter.setSelectionTracker(mSelectionTracker);


        if (savedInstanceState != null) {
            mSelectionTracker.onRestoreInstanceState(savedInstanceState);
        }

    }


    private void setSortOrder(long idx) {

        if (idx == 0L) {
            sortOrder = InitApplication.getSortOrder(mContext);
        } else {
            THREADS threads = THREADS.getInstance(mContext);
            SortOrder order = threads.getThreadSortOrder(idx);
            if (order != null) {
                sortOrder = order;
            } else {
                sortOrder = InitApplication.getSortOrder(mContext);
            }
        }
    }

    private void updateDirectory(@Nullable Long parent, String query, boolean forceScrollToTop) {
        try {

            LiveData<List<Thread>> obs = observer.get();
            if (obs != null) {
                obs.removeObservers(getViewLifecycleOwner());
            }

            if (parent == null) {
                parent = 0L;
            }

            LiveData<List<Thread>> liveData =
                    mThreadViewModel.getVisibleChildrenByQuery(parent, query);
            observer.set(liveData);


            liveData.observe(getViewLifecycleOwner(), (threads) -> {

                if (threads != null) {

                    switch (sortOrder) {
                        case DATE: {
                            threads.sort(Comparator.comparing(Thread::getLastModified).reversed());
                            break;
                        }
                        case DATE_INVERSE: {
                            threads.sort(Comparator.comparing(Thread::getLastModified));
                            break;
                        }
                        case SIZE: {
                            threads.sort(Comparator.comparing(Thread::getSize));
                            break;
                        }
                        case SIZE_INVERSE: {
                            threads.sort(Comparator.comparing(Thread::getSize).reversed());
                            break;
                        }
                        case NAME: {
                            threads.sort(Comparator.comparing(Thread::getName));
                            break;
                        }
                        case NAME_INVERSE: {
                            threads.sort(Comparator.comparing(Thread::getName).reversed());
                            break;
                        }
                        default:
                            threads.sort(Comparator.comparing(Thread::getLastModified).reversed());
                    }


                    int size = mThreadsViewAdapter.getItemCount();
                    boolean scrollToTop = size < threads.size();


                    mThreadsViewAdapter.updateData(threads);

                    if (scrollToTop || forceScrollToTop) {
                        try {
                            mRecyclerView.scrollToPosition(0);
                        } catch (Throwable e) {
                            LogUtils.error(TAG, e);
                        }
                    }
                }
            });
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

        final EVENTS events = EVENTS.getInstance(mContext);

        if (!mSelectionTracker.hasSelection()) {
            events.warning(getString(R.string.no_marked_file_delete));
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
            boolean isSeeding = thread.isSeeding();
            boolean isOpenActive = isSeeding && !thread.isDir();


            PopupMenu menu = new PopupMenu(mContext, view);
            menu.inflate(R.menu.popup_threads_menu);
            menu.getMenu().findItem(R.id.popup_rename).setVisible(true);
            menu.getMenu().findItem(R.id.popup_share).setVisible(true);
            menu.getMenu().findItem(R.id.popup_delete).setVisible(true);
            menu.getMenu().findItem(R.id.popup_copy_to).setVisible(isSeeding);
            menu.getMenu().findItem(R.id.popup_open_with).setVisible(isOpenActive);

            menu.setOnMenuItemClickListener((item) -> {

                if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                    return true;
                }
                mLastClickTime = SystemClock.elapsedRealtime();

                if (item.getItemId() == R.id.popup_info) {
                    clickThreadInfo(thread);
                    return true;
                } else if (item.getItemId() == R.id.popup_delete) {
                    clickThreadDelete(thread.getIdx());
                    return true;
                } else if (item.getItemId() == R.id.popup_share) {
                    clickThreadShare(thread.getIdx());
                    return true;
                } else if (item.getItemId() == R.id.popup_copy_to) {
                    clickThreadCopy(thread);
                    return true;
                } else if (item.getItemId() == R.id.popup_rename) {
                    clickThreadRename(thread);
                    return true;
                } else if (item.getItemId() == R.id.popup_open_with) {
                    clickThreadOpen(thread);
                    return true;
                } else {
                    return false;
                }
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

    private void clickThreadShare(long idx) {
        final EVENTS events = EVENTS.getInstance(mContext);
        final THREADS threads = THREADS.getInstance(mContext);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                Thread thread = threads.getThreadByIdx(idx);
                Objects.requireNonNull(thread);
                ComponentName[] names = {new ComponentName(
                        mContext.getApplicationContext(), MainActivity.class)};
                Uri uri = DOCS.getInstance(mContext).getPath(thread);

                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_TEXT, uri.toString());
                intent.setType(MimeType.PLAIN_MIME_TYPE);
                intent.putExtra(Intent.EXTRA_SUBJECT, thread.getName());
                intent.putExtra(Intent.EXTRA_TITLE, thread.getName());


                Intent chooser = Intent.createChooser(intent, getText(R.string.share));
                chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, names);
                chooser.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(chooser);


            } catch (Throwable ignore) {
                events.warning(getString(R.string.no_activity_found_to_handle_uri));
            }
        });


    }

    private void clickThreadCopy(@NonNull Thread thread) {

        setThread(mContext, thread.getIdx());
        try {
            if (thread.isDir()) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                intent.putExtra(DocumentsContract.EXTRA_EXCLUDE_SELF, true);
                mDirExportForResult.launch(intent);
            } else {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.setType(thread.getMimeType());
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                intent.putExtra(Intent.EXTRA_TITLE, thread.getName());
                intent.putExtra(DocumentsContract.EXTRA_EXCLUDE_SELF, true);
                mFileExportForResult.launch(intent);
            }
        } catch (Throwable e) {
            EVENTS.getInstance(mContext).warning(
                    getString(R.string.no_activity_found_to_handle_uri));
        }

    }

    private ActionMode.Callback createActionModeCallback() {
        return new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.menu_threads_action_mode, menu);

                MenuItem action_delete = menu.findItem(R.id.action_mode_delete);
                action_delete.setVisible(true);

                mListener.showFab(false);

                mHandler.post(() -> mThreadsViewAdapter.notifyDataSetChanged());

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

                    mThreadsViewAdapter.selectAllThreads();

                    return true;
                } else if (itemId == R.id.action_mode_delete) {

                    if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                        return true;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    deleteAction();

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
                mHandler.post(() -> mThreadsViewAdapter.notifyDataSetChanged());

            }
        };

    }

    @Override
    public void onClick(@NonNull Thread thread) {

        if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
            return;
        }
        mLastClickTime = SystemClock.elapsedRealtime();

        try {
            if (!mSelectionTracker.hasSelection()) {

                mListener.showFab(true);

                if (mSearchActionMode != null) {
                    mSearchActionMode.finish();
                }

                if (thread.isDir()) {
                    mSelectionViewModel.setParentThread(thread.getIdx());
                    mActivity.invalidateOptionsMenu();
                } else {
                    clickThreadPlay(thread);
                }

            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }

    }


    private void clickThreadInfo(@NonNull Thread thread) {
        try {
            String cid = thread.getContent();
            Objects.requireNonNull(cid);
            String uri = Content.IPFS + "://" + cid;

            Uri uriImage = QRCodeService.getImage(mContext, cid);
            ContentDialogFragment.newInstance(uriImage, cid,
                    getString(R.string.multi_hash_access, thread.getName()), uri)
                    .show(getChildFragmentManager(), ContentDialogFragment.TAG);


        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }

    }

    private void clickThreadOpen(@NonNull Thread thread) {


        try {

            if (thread.isSeeding()) {

                String cid = thread.getContent();
                Objects.requireNonNull(cid);


                String mimeType = thread.getMimeType();


                // special case
                if (Objects.equals(mimeType, MimeType.URL_MIME_TYPE)) {

                    Uri uri = Uri.parse(IPFS.getInstance(mContext).getText(cid));
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);


                    Intent chooser = Intent.createChooser(intent,
                            getString(R.string.open_with));
                    chooser.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(chooser);


                    return;
                }


                Uri uri = FileDocumentsProvider.getUriForThread(thread);

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setAction(Intent.ACTION_VIEW);
                intent.putExtra(Intent.EXTRA_TITLE, thread.getName());
                intent.setDataAndType(uri, mimeType);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);


                Intent chooser = Intent.createChooser(intent, getString(R.string.open_with));
                chooser.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(chooser);


            }
        } catch (Throwable ignore) {
            EVENTS.getInstance(mContext).warning(getString(R.string.no_activity_found_to_handle_uri));
        }

    }

    private void clickThreadPlay(@NonNull Thread thread) {


        try {

            if (thread.isSeeding()) {

                String cid = thread.getContent();
                Objects.requireNonNull(cid);


                String mimeType = thread.getMimeType();


                // special case
                if (Objects.equals(mimeType, MimeType.URL_MIME_TYPE)) {

                    IPFS ipfs = IPFS.getInstance(mContext);
                    Uri uri = Uri.parse(ipfs.getText(cid));
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(intent);

                    return;
                } else if (Objects.equals(mimeType, MimeType.HTML_MIME_TYPE)) {
                    Uri uri = DOCS.getInstance(mContext).getPath(thread);
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);

                    return;
                }


                Uri uri = FileDocumentsProvider.getUriForThread(thread);


                Intent intent;
                if (MimeTypes.isVideo(mimeType)) {
                    intent = new Intent(Intent.ACTION_VIEW, uri, mContext, ExoPlayerActivity.class);
                } else {
                    intent = new Intent(Intent.ACTION_VIEW);

                }
                intent.putExtra(Intent.EXTRA_TITLE, thread.getName());
                intent.setDataAndType(uri, mimeType);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            }
        } catch (Throwable ignore) {
            EVENTS.getInstance(mContext).warning(getString(R.string.no_activity_found_to_handle_uri));
        }

    }

    @Override
    public void invokePauseAction(@NonNull Thread thread) {

        if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
            return;
        }
        mLastClickTime = SystemClock.elapsedRealtime();


        UUID uuid = thread.getWorkUUID();
        if (uuid != null) {
            WorkManager.getInstance(mContext).cancelWorkById(uuid);
        }


        Executors.newSingleThreadExecutor().submit(() -> {
            THREADS threads = THREADS.getInstance(mContext);
            threads.resetThreadLeaching(thread.getIdx());
        });

    }

    @Override
    public void invokeDeleteAction(@NonNull Thread thread) {
        ThreadsService.removeThreads(mContext, thread.getIdx());
    }

    @Override
    public void invokeLoadAction(@NonNull Thread thread) {


        UUID uuid = UploadThreadWorker.load(mContext, thread.getIdx(), true);

        Executors.newSingleThreadExecutor().submit(() -> {
            THREADS threads = THREADS.getInstance(mContext);
            threads.setThreadLeaching(thread.getIdx());
            threads.setThreadWork(thread.getIdx(), uuid);
        });


    }


    private void clickThreadDelete(long idx) {
        ThreadsService.removeThreads(mContext, idx);
    }


    private void clickEditContent() {
        try {
            EditContentDialogFragment.newInstance(null, false).show(
                    getChildFragmentManager(), EditContentDialogFragment.TAG);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }


    private void clickImportFiles(long idx) {

        try {

            setThread(mContext, idx);

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType(MimeType.ALL);
            String[] mimeTypes = {MimeType.ALL};
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.putExtra(DocumentsContract.EXTRA_EXCLUDE_SELF, true);
            intent.addCategory(Intent.CATEGORY_OPENABLE);

            mFilesImportForResult.launch(intent);

        } catch (Throwable e) {
            EVENTS.getInstance(mContext).warning(
                    getString(R.string.no_activity_found_to_handle_uri));
            LogUtils.error(TAG, e);
        }
    }


    public void clickFilesAdd() {
        Long idx = mSelectionViewModel.getParentThread().getValue();
        Objects.requireNonNull(idx);
        clickImportFiles(idx);
    }


    private ActionMode.Callback createSearchActionModeCallback() {
        return new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.menu_search_action_mode, menu);

                mThreadItemDetailsLookup.setActive(false);

                MenuItem searchMenuItem = menu.findItem(R.id.search_view);

                SearchView mSearchView = (SearchView) searchMenuItem.getActionView();

                mSearchView.setIconifiedByDefault(false);
                mSearchView.setFocusable(true);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mSearchView.setFocusedByDefault(true);
                }
                String query = mSelectionViewModel.getQuery().getValue();
                Objects.requireNonNull(query);
                mSearchView.setQuery(query, true);
                mSearchView.setIconified(false);
                mSearchView.requestFocus();


                mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {

                        mSelectionViewModel.getQuery().setValue(query);
                        return false;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {

                        mSelectionViewModel.getQuery().setValue(newText);
                        return false;
                    }
                });

                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {

                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                try {
                    mThreadItemDetailsLookup.setActive(true);
                    mSelectionViewModel.setQuery("");

                    if (mSearchActionMode != null) {
                        mSearchActionMode = null;
                    }
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }
            }
        };

    }

    @Override
    public void onRefresh() {
        mSwipeRefreshLayout.setRefreshing(true);

        try {
            EVENTS.getInstance(mContext).warning(getString(R.string.publish_files));

            PageWorker.publish(mContext, true);

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        } finally {
            mSwipeRefreshLayout.setRefreshing(false);
        }

    }

    public interface ActionListener {

        void showFab(boolean b);
    }
}
