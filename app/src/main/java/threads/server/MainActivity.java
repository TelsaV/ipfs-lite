package threads.server;


import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.URLUtil;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ShareCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.WorkManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;

import java.io.File;
import java.io.PrintStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import threads.LogUtils;
import threads.server.core.Content;
import threads.server.core.DOCS;
import threads.server.core.DeleteOperation;
import threads.server.core.books.BOOKS;
import threads.server.core.events.EVENTS;
import threads.server.core.events.EventViewModel;
import threads.server.core.peers.PEERS;
import threads.server.core.threads.SortOrder;
import threads.server.core.threads.THREADS;
import threads.server.fragments.AccountDialogFragment;
import threads.server.fragments.BookmarksDialogFragment;
import threads.server.fragments.BrowserFragment;
import threads.server.fragments.EditContentDialogFragment;
import threads.server.fragments.EditPeerDialogFragment;
import threads.server.fragments.NewFolderDialogFragment;
import threads.server.fragments.PeersFragment;
import threads.server.fragments.SettingsFragment;
import threads.server.fragments.SwarmFragment;
import threads.server.fragments.TextDialogFragment;
import threads.server.fragments.ThreadsFragment;
import threads.server.ipfs.IPFS;
import threads.server.provider.FileDocumentsProvider;
import threads.server.provider.FileProvider;
import threads.server.services.DiscoveryService;
import threads.server.services.LiteService;
import threads.server.services.QRCodeService;
import threads.server.services.RegistrationService;
import threads.server.services.UploadService;
import threads.server.services.UserService;
import threads.server.utils.CodecDecider;
import threads.server.utils.MimeType;
import threads.server.utils.PermissionAction;
import threads.server.utils.SelectionViewModel;
import threads.server.work.BackupWorker;
import threads.server.work.DeleteThreadsWorker;
import threads.server.work.LocalConnectWorker;
import threads.server.work.SwarmConnectWorker;
import threads.server.work.UploadFilesWorker;
import threads.server.work.UploadFolderWorker;


public class MainActivity extends AppCompatActivity implements
        ThreadsFragment.ActionListener,
        BrowserFragment.ActionListener,
        PeersFragment.ActionListener,
        SwarmFragment.ActionListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String FRAG = "FRAG";
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            SwarmConnectWorker.dialing(getApplicationContext());
        }
    };
    private final AtomicInteger currentFragment = new AtomicInteger();


    private final ActivityResultLauncher<Intent> mFilesImportForResult =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            Intent data = result.getData();
                            try {
                                Objects.requireNonNull(data);


                                if (data.getClipData() != null) {
                                    ClipData mClipData = data.getClipData();
                                    long parent = getThread(getApplicationContext());
                                    LiteService.files(getApplicationContext(), mClipData, parent);

                                } else if (data.getData() != null) {
                                    Uri uri = data.getData();
                                    Objects.requireNonNull(uri);
                                    if (!FileDocumentsProvider.hasReadPermission(getApplicationContext(), uri)) {
                                        EVENTS.getInstance(getApplicationContext()).error(
                                                getString(R.string.file_has_no_read_permission));
                                        return;
                                    }

                                    if (FileDocumentsProvider.isPartial(getApplicationContext(), uri)) {
                                        EVENTS.getInstance(getApplicationContext()).error(
                                                getString(R.string.file_not_valid));
                                        return;
                                    }

                                    long parent = getThread(getApplicationContext());

                                    UploadService.uploadFile(getApplicationContext(), parent, uri);
                                }

                            } catch (Throwable throwable) {
                                LogUtils.error(TAG, throwable);
                            }
                        }
                    });
    private final ActivityResultLauncher<Intent> mFolderImportForResult =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            Intent data = result.getData();
                            try {
                                Objects.requireNonNull(data);
                                long parent = getThread(getApplicationContext());
                                if (data.getClipData() != null) {
                                    ClipData mClipData = data.getClipData();
                                    int items = mClipData.getItemCount();
                                    if (items > 0) {
                                        for (int i = 0; i < items; i++) {
                                            ClipData.Item item = mClipData.getItemAt(i);
                                            Uri uri = item.getUri();

                                            if (!FileDocumentsProvider.hasReadPermission(getApplicationContext(), uri)) {
                                                EVENTS.getInstance(getApplicationContext()).error(
                                                        getString(R.string.file_has_no_read_permission));
                                                return;
                                            }

                                            if (FileDocumentsProvider.isPartial(getApplicationContext(), uri)) {
                                                EVENTS.getInstance(getApplicationContext()).error(
                                                        getString(R.string.file_not_valid));
                                                return;
                                            }

                                            UploadFolderWorker.load(getApplicationContext(), parent, uri);
                                        }
                                    }
                                } else {
                                    Uri uri = data.getData();
                                    if (uri != null) {
                                        if (!FileDocumentsProvider.hasReadPermission(getApplicationContext(), uri)) {
                                            EVENTS.getInstance(getApplicationContext()).error(
                                                    getString(R.string.file_has_no_read_permission));
                                            return;
                                        }

                                        if (FileDocumentsProvider.isPartial(getApplicationContext(), uri)) {
                                            EVENTS.getInstance(getApplicationContext()).error(
                                                    getString(R.string.file_not_valid));
                                            return;
                                        }

                                        UploadFolderWorker.load(getApplicationContext(), parent, uri);
                                    }
                                }
                            } catch (Throwable throwable) {
                                LogUtils.error(TAG, throwable);
                            }
                        }
                    });
    private final ActivityResultLauncher<Intent> mBackupForResult =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            Intent data = result.getData();
                            try {
                                Objects.requireNonNull(data);
                                Uri uri = data.getData();
                                Objects.requireNonNull(uri);


                                if (!FileDocumentsProvider.hasWritePermission(getApplicationContext(), uri)) {
                                    EVENTS.getInstance(getApplicationContext()).error(
                                            getString(R.string.file_has_no_write_permission));
                                    return;
                                }
                                BackupWorker.backup(getApplicationContext(), uri);

                            } catch (Throwable throwable) {
                                LogUtils.error(TAG, throwable);
                            }
                        }
                    });
    private long mLastClickTime = 0;
    private CoordinatorLayout mDrawerLayout;
    private BottomNavigationView mNavigation;
    private NsdManager mNsdManager;
    private FloatingActionButton mFloatingActionButton;
    private SelectionViewModel mSelectionViewModel;
    private TextView mBrowserText;
    private ActionMode mActionMode;
    private ImageButton mActionBookmark;

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

    @Override
    public void onDestroy() {
        super.onDestroy();

        try {

            unregisterReceiver(broadcastReceiver);

            if (mNsdManager != null) {
                mNsdManager.unregisterService(RegistrationService.getInstance());
                mNsdManager.stopServiceDiscovery(DiscoveryService.getInstance());
            }

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    private void loadFragment(Fragment fragment, int value) {
        currentFragment.set(value);
        if (fragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuCompat.setGroupDividerEnabled(menu, true);
        return super.onCreateOptionsMenu(menu);
    }

    private void registerService(int port) {
        try {
            String peerID = IPFS.getPeerID(getApplicationContext());
            Objects.requireNonNull(peerID);
            String serviceType = "_ipfs-discovery._udp";
            NsdServiceInfo serviceInfo = new NsdServiceInfo();
            serviceInfo.setServiceName(peerID);
            serviceInfo.setServiceType(serviceType);
            serviceInfo.setPort(port);
            mNsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
            Objects.requireNonNull(mNsdManager);
            mNsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD,
                    RegistrationService.getInstance());


            DiscoveryService discovery = DiscoveryService.getInstance();
            discovery.setOnServiceFoundListener((info) -> mNsdManager.resolveService(info, new NsdManager.ResolveListener() {

                @Override
                public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {

                }


                @Override
                public void onServiceResolved(NsdServiceInfo serviceInfo) {

                    try {

                        String serviceName = serviceInfo.getServiceName();
                        boolean connect = !Objects.equals(peerID, serviceName);
                        if (connect) {
                            InetAddress inetAddress = serviceInfo.getHost();
                            LocalConnectWorker.connect(getApplicationContext(),
                                    serviceName, serviceInfo.getHost().toString(),
                                    serviceInfo.getPort(), inetAddress instanceof Inet6Address);
                        }

                    } catch (Throwable e) {
                        LogUtils.error(TAG, e);
                    }
                }
            }));
            mNsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discovery);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    public void setFabImage(@DrawableRes int resId) {
        mFloatingActionButton.setImageResource(resId);
    }

    public void showFab(boolean visible) {

        if (visible) {
            int value = currentFragment.intValue();
            if (value == R.id.navigation_files || value == R.id.navigation_peers) {
                mFloatingActionButton.show();
            } else {
                mFloatingActionButton.hide();
            }
        } else {
            mFloatingActionButton.hide();
        }

    }

    private final AtomicBoolean forwardActive = new AtomicBoolean(false);
    private final AtomicBoolean downloadActive = new AtomicBoolean(false);


    private void setSortOrder(@NonNull SortOrder sortOrder) {
        InitApplication.setSortOrder(getApplicationContext(), sortOrder);
    }

    private final AtomicReference<Uri> uriAtomicReference = new AtomicReference<>(null);

    private void updateDirectory(@Nullable Long parent, String query, @NonNull SortOrder sortOrder) {

        Fragment fragment = getSupportFragmentManager().findFragmentById(
                R.id.fragment_container);
        if (fragment instanceof ThreadsFragment) {
            ThreadsFragment threadsFragment = (ThreadsFragment) fragment;
            if (threadsFragment.isResumed()) {
                threadsFragment.updateDirectory(parent, query, sortOrder, true);
            }
        }
    }

    private void clickFilesAdd() {

        try {
            Long idx = mSelectionViewModel.getParentThread().getValue();
            Objects.requireNonNull(idx);

            setThread(getApplicationContext(), idx);

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType(MimeType.ALL);
            String[] mimeTypes = {MimeType.ALL};
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.putExtra(DocumentsContract.EXTRA_EXCLUDE_SELF, true);
            intent.addCategory(Intent.CATEGORY_OPENABLE);

            mFilesImportForResult.launch(intent);

        } catch (Throwable e) {
            EVENTS.getInstance(getApplicationContext()).warning(
                    getString(R.string.no_activity_found_to_handle_uri));
            LogUtils.error(TAG, e);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntents(intent);
    }

    @Override
    public void onBackPressed() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(
                R.id.fragment_container);
        if (fragment instanceof BrowserFragment) {
            BrowserFragment tabsFragment = (BrowserFragment) fragment;
            if (tabsFragment.isResumed()) {
                boolean result = tabsFragment.onBackPressed();
                if (result) {
                    return;
                }
            }
        }
        super.onBackPressed();
    }

    private void handleIntents(Intent intent) {

        final String action = intent.getAction();
        try {
            ShareCompat.IntentReader intentReader = new ShareCompat.IntentReader(this);
            if (Intent.ACTION_SEND.equals(action) ||
                    Intent.ACTION_SEND_MULTIPLE.equals(action)) {
                handleSend(intentReader);
            } else if (Intent.ACTION_VIEW.equals(action)) {
                Uri uri = intent.getData();
                if (uri != null) {
                    String scheme = uri.getScheme();
                    if (Objects.equals(scheme, Content.IPNS) ||
                            Objects.equals(scheme, Content.IPFS) ||
                            Objects.equals(scheme, Content.HTTP) ||
                            Objects.equals(scheme, Content.HTTPS)) {
                        openBrowserView(uri);
                    }
                }
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, "" + e.getLocalizedMessage());
        }
    }

    @Override
    public void openBrowserView(@NonNull Uri uri) {
        try {
            mSelectionViewModel.setUri(uri.toString());
            mNavigation.setSelectedItemId(R.id.navigation_browser);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }

    }

    private void handleSend(ShareCompat.IntentReader intentReader) {

        try {
            Objects.requireNonNull(intentReader);
            if (intentReader.isMultipleShare()) {
                int items = intentReader.getStreamCount();

                if (items > 0) {
                    FileProvider fileProvider =
                            FileProvider.getInstance(getApplicationContext());
                    File file = fileProvider.createTempDataFile();

                    try (PrintStream out = new PrintStream(file)) {
                        for (int i = 0; i < items; i++) {
                            Uri uri = intentReader.getStream(i);
                            if (uri != null) {
                                out.println(uri.toString());
                            }
                        }
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }

                    Uri uri = androidx.core.content.FileProvider.getUriForFile(
                            getApplicationContext(), BuildConfig.APPLICATION_ID, file);
                    Objects.requireNonNull(uri);
                    UploadFilesWorker.load(getApplicationContext(), 0L, uri);
                }
            } else {
                String type = intentReader.getType();
                if (Objects.equals(type, MimeType.PLAIN_MIME_TYPE)) {
                    CharSequence textObject = intentReader.getText();
                    Objects.requireNonNull(textObject);
                    String text = textObject.toString();
                    if (!text.isEmpty()) {

                        Uri uri = Uri.parse(text);
                        if (uri != null) {
                            if (Objects.equals(uri.getScheme(), Content.IPFS) ||
                                    Objects.equals(uri.getScheme(), Content.IPNS) ||
                                    Objects.equals(uri.getScheme(), Content.HTTP) ||
                                    Objects.equals(uri.getScheme(), Content.HTTPS)) {
                                openBrowserView(uri);
                                return;
                            }
                        }

                        CodecDecider result = CodecDecider.evaluate(getApplicationContext(), text);

                        if (result.getCodex() == CodecDecider.Codec.P2P_URI) {
                            EditPeerDialogFragment.newInstance(result.getMultihash(), null).
                                    show(getSupportFragmentManager(), EditPeerDialogFragment.TAG);

                        } else if (result.getCodex() == CodecDecider.Codec.MULTIHASH) {

                            EditContentDialogFragment.newInstance(result.getMultihash(),
                                    false).show(
                                    getSupportFragmentManager(), EditContentDialogFragment.TAG);

                        } else if (result.getCodex() == CodecDecider.Codec.IPFS_URI) {

                            EditContentDialogFragment.newInstance(result.getMultihash(),
                                    false).show(
                                    getSupportFragmentManager(), EditContentDialogFragment.TAG);

                        } else if (result.getCodex() == CodecDecider.Codec.IPNS_URI) {
                            openBrowserView(Uri.parse(text));
                        } else if (result.getCodex() == CodecDecider.Codec.MULTIADDRESS) {

                            String user = result.getPeerID();
                            String address = result.getPeerAddress();
                            String peerID = IPFS.getPeerID(getApplicationContext());
                            if (user.equals(peerID)) {
                                EVENTS.getInstance(getApplicationContext()).
                                        warning(getString(R.string.same_pid_like_host));
                                return;
                            }

                            if (address != null && address.contains(Content.CIRCUIT)) {
                                address = null;
                            }

                            EditPeerDialogFragment.newInstance(user, address).show(
                                    getSupportFragmentManager(), EditPeerDialogFragment.TAG);

                        } else {
                            if (URLUtil.isValidUrl(text)) {
                                openBrowserView(Uri.parse(text));
                            } else {
                                UploadService.storeText(
                                        getApplicationContext(), 0L, text, false);
                            }
                        }
                    }
                } else if (Objects.equals(type, MimeType.HTML_MIME_TYPE)) {
                    String html = intentReader.getHtmlText();
                    Objects.requireNonNull(html);
                    if (!html.isEmpty()) {
                        UploadService.storeText(
                                getApplicationContext(), 0L, html, false);
                    }
                } else {
                    Uri uri = intentReader.getStream();
                    Objects.requireNonNull(uri);

                    if (!FileDocumentsProvider.hasReadPermission(getApplicationContext(), uri)) {
                        EVENTS.getInstance(getApplicationContext()).error(
                                getString(R.string.file_has_no_read_permission));
                        return;
                    }

                    if (FileDocumentsProvider.isPartial(getApplicationContext(), uri)) {

                        EVENTS.getInstance(getApplicationContext()).error(
                                getString(R.string.file_not_found));

                        return;
                    }

                    UploadService.uploadFile(getApplicationContext(), 0L, uri);

                }
            }


        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }

    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(FRAG, currentFragment.intValue());
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        currentFragment.set(savedInstanceState.getInt(FRAG));
    }

    public void updateBookmark(@NonNull Uri uri) {
        try {

            BOOKS books = BOOKS.getInstance(getApplicationContext());
            if (books.hasBookmark(uri.toString())) {
                Drawable drawable = AppCompatResources.getDrawable(getApplicationContext(),
                        R.drawable.star);
                mActionBookmark.setImageDrawable(drawable);
            } else {
                Drawable drawable = AppCompatResources.getDrawable(getApplicationContext(),
                        R.drawable.star_outline);
                mActionBookmark.setImageDrawable(drawable);
            }

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }


    private ActionMode.Callback createSearchActionModeCallback(@NonNull String hint) {
        return new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.menu_search_action_mode, menu);


                MenuItem searchMenuItem = menu.findItem(R.id.search_view);

                SearchView mSearchView = (SearchView) searchMenuItem.getActionView();

                mSearchView.setQueryHint(hint);
                mSearchView.setIconifiedByDefault(false);
                mSearchView.setFocusable(true);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mSearchView.setFocusedByDefault(true);
                }

                mSearchView.setQuery("", true);
                mSearchView.setIconified(false);
                mSearchView.requestFocus();


                mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {

                        try {
                            if (mActionMode != null) {
                                mActionMode.finish();
                            }
                            if (query != null && !query.isEmpty()) {
                                Uri uri = Uri.parse(query);
                                String scheme = uri.getScheme();
                                if (Objects.equals(scheme, Content.IPNS) ||
                                        Objects.equals(scheme, Content.IPFS) ||
                                        Objects.equals(scheme, Content.HTTP) ||
                                        Objects.equals(scheme, Content.HTTPS)) {
                                    openBrowserView(uri);
                                } else {

                                    IPFS ipfs = IPFS.getInstance(getApplicationContext());
                                    if (ipfs.isValidCID(query)) {
                                        openBrowserView(Uri.parse(Content.IPFS + "://" + query));
                                    } else {
                                        EVENTS.getInstance(getApplicationContext()).error(
                                                getString(R.string.cid_not_valid)
                                        );
                                    }
                                }
                            }
                        } catch (Throwable throwable) {
                            LogUtils.error(TAG, throwable);
                        }
                        return false;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
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
                mActionMode = null;
            }
        };

    }

    private int dpToPx(int dp) {
        float density = getApplicationContext().getResources()
                .getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.ThreadsTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DOCS docs = DOCS.getInstance(getApplicationContext());

        mActionBookmark = findViewById(R.id.action_bookmark);
        mActionBookmark.setOnClickListener(v -> {
            try {

                Fragment fragment = getSupportFragmentManager().findFragmentById(
                        R.id.fragment_container);
                if (fragment instanceof BrowserFragment) {
                    BrowserFragment brow = (BrowserFragment) fragment;
                    if (brow.isResumed()) {
                        brow.bookmark(getApplicationContext(), mActionBookmark);
                    }
                }

            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });

        ImageButton mActionHome = findViewById(R.id.action_home);
        if (docs.isPrivateNetwork(getApplicationContext())) {
            mActionHome.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                    android.R.color.holo_orange_dark), android.graphics.PorterDuff.Mode.SRC_IN);
        } else {
            mActionHome.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                    R.color.colorAccent), android.graphics.PorterDuff.Mode.SRC_IN);
        }

        ImageView mActionBookmarks = findViewById(R.id.action_bookmarks);
        mActionBookmarks.setOnClickListener(v -> {
            try {
                BookmarksDialogFragment dialogFragment = new BookmarksDialogFragment();
                dialogFragment.show(getSupportFragmentManager(), BookmarksDialogFragment.TAG);
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });

        ImageView mActionEditCid = findViewById(R.id.action_edit_cid);
        mActionEditCid.setOnClickListener(v -> {
            try {
                EditContentDialogFragment.newInstance(null, false).show(
                        getSupportFragmentManager(), EditContentDialogFragment.TAG);
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });


        ImageView mActionSorting = findViewById(R.id.action_sorting);
        mActionSorting.setOnClickListener(v -> {
            try {
                SortOrder sortOrder = InitApplication.getSortOrder(getApplicationContext());
                PopupMenu popup = new PopupMenu(MainActivity.this, v);
                popup.inflate(R.menu.popup_sorting);

                popup.getMenu().getItem(0).setChecked(sortOrder == SortOrder.NAME);
                popup.getMenu().getItem(1).setChecked(sortOrder == SortOrder.NAME_INVERSE);
                popup.getMenu().getItem(2).setChecked(sortOrder == SortOrder.DATE);
                popup.getMenu().getItem(3).setChecked(sortOrder == SortOrder.DATE_INVERSE);
                popup.getMenu().getItem(4).setChecked(sortOrder == SortOrder.SIZE);
                popup.getMenu().getItem(5).setChecked(sortOrder == SortOrder.SIZE_INVERSE);

                popup.setOnMenuItemClickListener(item -> {
                    try {
                        int itemId = item.getItemId();
                        if (itemId == R.id.sort_date) {

                            setSortOrder(SortOrder.DATE);

                            updateDirectory(mSelectionViewModel.getParentThread().getValue(),
                                    mSelectionViewModel.getQuery().getValue(), SortOrder.DATE);
                            return true;
                        } else if (itemId == R.id.sort_date_inverse) {

                            setSortOrder(SortOrder.DATE_INVERSE);

                            updateDirectory(mSelectionViewModel.getParentThread().getValue(),
                                    mSelectionViewModel.getQuery().getValue(), SortOrder.DATE_INVERSE);
                            return true;
                        } else if (itemId == R.id.sort_name) {

                            setSortOrder(SortOrder.NAME);

                            updateDirectory(mSelectionViewModel.getParentThread().getValue(),
                                    mSelectionViewModel.getQuery().getValue(), SortOrder.NAME);
                            return true;
                        } else if (itemId == R.id.sort_name_inverse) {

                            setSortOrder(SortOrder.NAME_INVERSE);

                            updateDirectory(mSelectionViewModel.getParentThread().getValue(),
                                    mSelectionViewModel.getQuery().getValue(), SortOrder.NAME_INVERSE);
                            return true;
                        } else if (itemId == R.id.sort_size) {

                            setSortOrder(SortOrder.SIZE);

                            updateDirectory(mSelectionViewModel.getParentThread().getValue(),
                                    mSelectionViewModel.getQuery().getValue(), SortOrder.SIZE);
                            return true;
                        } else if (itemId == R.id.sort_size_inverse) {

                            setSortOrder(SortOrder.SIZE_INVERSE);

                            updateDirectory(mSelectionViewModel.getParentThread().getValue(),
                                    mSelectionViewModel.getQuery().getValue(), SortOrder.SIZE_INVERSE);
                            return true;
                        }
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }
                    return false;
                });
                popup.show();

            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });

        ImageView mActionId = findViewById(R.id.action_id);
        mActionId.setOnClickListener(v -> {
            try {

                String peerID = IPFS.getPeerID(getApplicationContext());
                Objects.requireNonNull(peerID);

                Uri uri = QRCodeService.getImage(getApplicationContext(), peerID);

                AccountDialogFragment.newInstance(uri).show(
                        getSupportFragmentManager(), AccountDialogFragment.TAG);
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });


        ImageView mActionOverflow = findViewById(R.id.action_overflow);
        mActionOverflow.setOnClickListener(v -> {

            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);


            View menuOverflow = inflater.inflate(
                    R.layout.menu_overflow, mDrawerLayout, false);

            PopupWindow mPopupWindow = new PopupWindow(
                    MainActivity.this, null, R.attr.popupMenuStyle);
            mPopupWindow.setContentView(menuOverflow);
            mPopupWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
            mPopupWindow.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
            mPopupWindow.setOutsideTouchable(true);
            mPopupWindow.setFocusable(true);

            mPopupWindow.showAsDropDown(mActionOverflow, 0, -dpToPx(48),
                    Gravity.TOP | Gravity.END);


            int frag = currentFragment.get();
            ImageButton actionNextPage = menuOverflow.findViewById(R.id.action_next_page);
            if (frag == R.id.navigation_browser && forwardActive.get()) {
                actionNextPage.setEnabled(true);
                actionNextPage.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                        R.color.colorActiveImage), android.graphics.PorterDuff.Mode.SRC_IN);
            } else {
                actionNextPage.setEnabled(false);
                actionNextPage.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                        R.color.colorPassiveImage), android.graphics.PorterDuff.Mode.SRC_IN);
            }
            actionNextPage.setOnClickListener(v1 -> {
                try {
                    Fragment fragment = getSupportFragmentManager().findFragmentById(
                            R.id.fragment_container);
                    if (fragment instanceof BrowserFragment) {
                        BrowserFragment browserFragment = (BrowserFragment) fragment;
                        if (browserFragment.isResumed()) {
                            browserFragment.goForward();
                        }
                    }
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    mPopupWindow.dismiss();
                }

            });


            ImageButton actionFindPage = menuOverflow.findViewById(R.id.action_find_page);
            if (frag == R.id.navigation_browser || frag == R.id.navigation_files) {
                actionFindPage.setEnabled(true);
                actionFindPage.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                        R.color.colorActiveImage), android.graphics.PorterDuff.Mode.SRC_IN);
            } else {
                actionFindPage.setEnabled(false);
                actionFindPage.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                        R.color.colorPassiveImage), android.graphics.PorterDuff.Mode.SRC_IN);
            }
            actionFindPage.setOnClickListener(v12 -> {
                try {
                    if (frag == R.id.navigation_browser) {
                        Fragment fragment = getSupportFragmentManager().findFragmentById(
                                R.id.fragment_container);
                        if (fragment instanceof BrowserFragment) {
                            BrowserFragment browserFragment = (BrowserFragment) fragment;
                            if (browserFragment.isResumed()) {
                                browserFragment.findInPage();
                            }
                        }
                    } else if (frag == R.id.navigation_files) {
                        Fragment fragment = getSupportFragmentManager().findFragmentById(
                                R.id.fragment_container);
                        if (fragment instanceof ThreadsFragment) {
                            ThreadsFragment threadsFragment = (ThreadsFragment) fragment;
                            if (threadsFragment.isResumed()) {
                                threadsFragment.findInPage();
                            }
                        }
                    }

                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    mPopupWindow.dismiss();
                }

            });

            ImageButton actionDownload = menuOverflow.findViewById(R.id.action_download);

            if (downloadActive.get()) {
                actionDownload.setEnabled(true);
                actionDownload.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                        R.color.colorActiveImage), android.graphics.PorterDuff.Mode.SRC_IN);
            } else {
                actionDownload.setEnabled(false);
                actionDownload.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                        R.color.colorPassiveImage), android.graphics.PorterDuff.Mode.SRC_IN);
            }

            actionDownload.setOnClickListener(v13 -> {
                try {
                    if (frag == R.id.navigation_browser) {
                        Fragment fragment = getSupportFragmentManager().findFragmentById(
                                R.id.fragment_container);
                        if (fragment instanceof BrowserFragment) {
                            BrowserFragment browserFragment = (BrowserFragment) fragment;
                            if (browserFragment.isResumed()) {
                                browserFragment.download();
                            }
                        }
                    }
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    mPopupWindow.dismiss();
                }

            });

            ImageButton actionShare = menuOverflow.findViewById(R.id.action_share);
            if (uriAtomicReference.get() != null) {
                actionShare.setEnabled(true);
                actionShare.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                        R.color.colorActiveImage), android.graphics.PorterDuff.Mode.SRC_IN);
            } else {
                actionShare.setEnabled(false);
                actionShare.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                        R.color.colorPassiveImage), android.graphics.PorterDuff.Mode.SRC_IN);
            }
            actionShare.setOnClickListener(v14 -> {
                try {

                    Uri uri = uriAtomicReference.get();
                    Objects.requireNonNull(uri);

                    ComponentName[] names = {new ComponentName(getApplicationContext(), MainActivity.class)};

                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_link));
                    intent.putExtra(Intent.EXTRA_TEXT, uri.toString());
                    intent.setType(MimeType.PLAIN_MIME_TYPE);
                    intent.putExtra(DocumentsContract.EXTRA_EXCLUDE_SELF, true);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);


                    Intent chooser = Intent.createChooser(intent, getText(R.string.share));
                    chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, names);
                    startActivity(chooser);
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    mPopupWindow.dismiss();
                }

            });

            ImageButton actionReload = menuOverflow.findViewById(R.id.action_reload);

            if (frag == R.id.navigation_browser) {
                actionReload.setEnabled(true);
                actionReload.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                        R.color.colorActiveImage), android.graphics.PorterDuff.Mode.SRC_IN);
            } else {
                actionReload.setEnabled(false);
                actionReload.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                        R.color.colorPassiveImage), android.graphics.PorterDuff.Mode.SRC_IN);
            }
            actionReload.setOnClickListener(v15 -> {
                try {
                    if (frag == R.id.navigation_browser) {
                        Fragment fragment = getSupportFragmentManager().findFragmentById(
                                R.id.fragment_container);
                        if (fragment instanceof BrowserFragment) {
                            BrowserFragment browserFragment = (BrowserFragment) fragment;
                            if (browserFragment.isResumed()) {
                                browserFragment.reload();
                            }
                        }
                    }
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    mPopupWindow.dismiss();
                }

            });

            TextView actionClearCache = menuOverflow.findViewById(R.id.action_clear_cache);
            actionClearCache.setVisibility(View.GONE);
            actionClearCache.setOnClickListener(v19 -> {
                try {
                    Fragment fragment = getSupportFragmentManager().findFragmentById(
                            R.id.fragment_container);
                    if (fragment instanceof BrowserFragment) {
                        BrowserFragment browserFragment = (BrowserFragment) fragment;
                        if (browserFragment.isResumed()) {
                            browserFragment.clearCache();
                        }
                    }
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    mPopupWindow.dismiss();
                }

            });

            TextView actionNewFolder = menuOverflow.findViewById(R.id.action_new_folder);
            if (frag == R.id.navigation_files) {
                actionNewFolder.setVisibility(View.VISIBLE);
            } else {
                actionNewFolder.setVisibility(View.GONE);
            }
            actionNewFolder.setOnClickListener(v19 -> {
                try {

                    long parent = 0L;
                    Long thread = mSelectionViewModel.getParentThread().getValue();
                    if (thread != null) {
                        parent = thread;
                    }

                    NewFolderDialogFragment.newInstance(parent).
                            show(getSupportFragmentManager(), NewFolderDialogFragment.TAG);

                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    mPopupWindow.dismiss();
                }

            });


            TextView actionImportFolder = menuOverflow.findViewById(R.id.action_import_folder);
            if (frag == R.id.navigation_files) {
                actionImportFolder.setVisibility(View.VISIBLE);
            } else {
                actionImportFolder.setVisibility(View.GONE);
            }
            actionImportFolder.setOnClickListener(v19 -> {
                try {
                    long parent = 0L;
                    Long thread = mSelectionViewModel.getParentThread().getValue();
                    if (thread != null) {
                        parent = thread;
                    }
                    setThread(getApplicationContext(), parent);

                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    intent.putExtra(DocumentsContract.EXTRA_EXCLUDE_SELF, true);
                    mFolderImportForResult.launch(intent);

                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    mPopupWindow.dismiss();
                }

            });


            TextView actionNewText = menuOverflow.findViewById(R.id.action_new_text);
            if (frag == R.id.navigation_files) {
                actionNewText.setVisibility(View.VISIBLE);
            } else {
                actionNewText.setVisibility(View.GONE);
            }
            actionNewText.setOnClickListener(v19 -> {
                try {

                    long parent = 0L;
                    Long thread = mSelectionViewModel.getParentThread().getValue();
                    if (thread != null) {
                        parent = thread;
                    }

                    TextDialogFragment.newInstance(parent).
                            show(getSupportFragmentManager(), TextDialogFragment.TAG);

                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    mPopupWindow.dismiss();
                }

            });

            TextView actionBackup = menuOverflow.findViewById(R.id.action_backup);
            actionBackup.setOnClickListener(v19 -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    intent.putExtra(DocumentsContract.EXTRA_EXCLUDE_SELF, true);
                    mBackupForResult.launch(intent);

                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    mPopupWindow.dismiss();
                }

            });

            TextView actionDocumentation = menuOverflow.findViewById(R.id.action_documentation);
            actionDocumentation.setOnClickListener(v19 -> {
                try {
                    String uri = "https://gitlab.com/remmer.wilts/ipfs-lite";

                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri),
                            getApplicationContext(), MainActivity.class);
                    startActivity(intent);
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    mPopupWindow.dismiss();
                }

            });


        });

        mBrowserText = findViewById(R.id.action_browser);


        mBrowserText.setOnClickListener(view -> {
            try {
                try {
                    String hint = getString(R.string.ipfs) + "://";
                    mActionMode = startSupportActionMode(
                            createSearchActionModeCallback(hint));
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }

            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });

        mSelectionViewModel = new ViewModelProvider(this).get(SelectionViewModel.class);


        mSelectionViewModel.getParentThread().observe(this, (threadIdx) -> {

            if (threadIdx != null) {
                if (threadIdx > 0L) {
                    mActionEditCid.setVisibility(View.GONE);
                } else {
                    if (currentFragment.get() == R.id.navigation_files) {
                        mActionEditCid.setVisibility(View.VISIBLE);
                    } else {
                        mActionEditCid.setVisibility(View.GONE);
                    }
                }
            }

        });
        Uri uri = docs.getPinsPageUri();
        mSelectionViewModel.setUri(uri.toString());
        updateUri(uri, false);


        mFloatingActionButton = findViewById(R.id.floating_action_button);

        mFloatingActionButton.setOnClickListener((v) -> {

            if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
                return;
            }
            mLastClickTime = SystemClock.elapsedRealtime();

            int value = currentFragment.intValue();
            if (value == R.id.navigation_files) {
                clickFilesAdd();
            } else if (value == R.id.navigation_peers) {
                EditPeerDialogFragment.newInstance().show(
                        getSupportFragmentManager(), EditPeerDialogFragment.TAG);
            }
        });


        mNavigation = findViewById(R.id.navigation);
        mNavigation.refreshDrawableState();
        mNavigation.setOnNavigationItemSelectedListener((item) -> {

            int itemId = item.getItemId();
            if (itemId == R.id.navigation_files) {

                loadFragment(new ThreadsFragment(), R.id.navigation_files);
                showFab(true);
                mSelectionViewModel.setParentThread(0L);
                mActionBookmark.setVisibility(View.GONE);
                mActionBookmarks.setVisibility(View.GONE);
                mActionId.setVisibility(View.GONE);
                mActionEditCid.setVisibility(View.VISIBLE);
                mActionSorting.setVisibility(View.VISIBLE);
                setFabImage(R.drawable.plus_thick);
                return true;
            } else if (itemId == R.id.navigation_peers) {

                loadFragment(new PeersFragment(), R.id.navigation_peers);
                showFab(true);
                mSelectionViewModel.setParentThread(0L);
                mActionBookmark.setVisibility(View.GONE);
                mActionBookmarks.setVisibility(View.GONE);
                mActionId.setVisibility(View.VISIBLE);
                mActionEditCid.setVisibility(View.GONE);
                mActionSorting.setVisibility(View.GONE);
                setFabImage(R.drawable.account_plus);
                return true;
            } else if (itemId == R.id.navigation_browser) {

                loadFragment(new BrowserFragment(), R.id.navigation_browser);
                showFab(false);
                mSelectionViewModel.setParentThread(0L);
                mActionBookmark.setVisibility(View.VISIBLE);
                mActionBookmarks.setVisibility(View.VISIBLE);
                mActionId.setVisibility(View.GONE);
                mActionEditCid.setVisibility(View.GONE);
                mActionSorting.setVisibility(View.GONE);
                return true;
            } else if (itemId == R.id.navigation_swarm) {

                loadFragment(new SwarmFragment(), R.id.navigation_swarm);
                showFab(false);
                mSelectionViewModel.setParentThread(0L);
                mActionBookmark.setVisibility(View.GONE);
                mActionBookmarks.setVisibility(View.GONE);
                mActionId.setVisibility(View.GONE);
                mActionEditCid.setVisibility(View.GONE);
                mActionSorting.setVisibility(View.GONE);
                return true;
            } else if (itemId == R.id.navigation_settings) {

                loadFragment(new SettingsFragment(), R.id.navigation_settings);
                showFab(false);
                mSelectionViewModel.setParentThread(0L);
                mActionBookmark.setVisibility(View.GONE);
                mActionBookmarks.setVisibility(View.GONE);
                mActionId.setVisibility(View.VISIBLE);
                mActionEditCid.setVisibility(View.GONE);
                mActionSorting.setVisibility(View.GONE);
                return true;
            }
            return false;

        });

        mActionHome.setOnClickListener(view -> openBrowserView(docs.getPinsPageUri()));


        if (savedInstanceState != null) {
            mNavigation.setSelectedItemId(savedInstanceState.getInt(FRAG));
        } else {
            mNavigation.setSelectedItemId(R.id.navigation_files);
        }


        mDrawerLayout = findViewById(R.id.drawer_layout);


        EventViewModel eventViewModel =
                new ViewModelProvider(this).get(EventViewModel.class);

        eventViewModel.getExit().observe(this, (event) -> {
            try {
                if (event != null) {
                    String content = event.getContent();
                    if (!content.isEmpty()) {
                        Snackbar snackbar = Snackbar.make(mDrawerLayout, content,
                                Snackbar.LENGTH_INDEFINITE);
                        snackbar.setAction(android.R.string.ok, (view) -> {

                            try {

                                WorkManager.getInstance(getApplicationContext()).cancelAllWork();

                                IPFS.getInstance(getApplicationContext()).shutdown();
                                finishAffinity();
                                java.lang.Thread.sleep(500);
                                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                                startActivity(intent);
                            } catch (Throwable e) {
                                LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
                            } finally {
                                snackbar.dismiss();
                            }

                        });
                        snackbar.setAnchorView(mNavigation);
                        snackbar.addCallback(new Snackbar.Callback() {

                            @Override
                            public void onDismissed(Snackbar snackbar, int event) {
                                showFab(true);
                            }
                        });
                        showFab(false);
                        snackbar.show();

                    }
                    eventViewModel.removeEvent(event);
                }
            } catch (Throwable e) {
                LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
            }

        });
        eventViewModel.getDelete().observe(this, (event) -> {
            try {
                if (event != null) {
                    String content = event.getContent();
                    if (!content.isEmpty()) {
                        Gson gson = new Gson();
                        DeleteOperation deleteOperation = gson.fromJson(content, DeleteOperation.class);
                        if (Objects.equals(deleteOperation.getKind(), DeleteOperation.THREADS)) {
                            long[] idxs = deleteOperation.indices;


                            String message;
                            if (idxs.length == 1) {
                                message = getString(R.string.delete_file);
                            } else {
                                message = getString(
                                        R.string.delete_files, "" + idxs.length);
                            }
                            AtomicBoolean deleteThreads = new AtomicBoolean(true);
                            Snackbar snackbar = Snackbar.make(mDrawerLayout, message, Snackbar.LENGTH_LONG);
                            snackbar.setAction(getString(R.string.revert_operation), (view) -> {

                                try {
                                    deleteThreads.set(false);
                                    ExecutorService executor = Executors.newSingleThreadExecutor();
                                    executor.submit(() -> THREADS.getInstance(
                                            getApplicationContext()).resetThreadsDeleting(idxs));
                                } catch (Throwable e) {
                                    LogUtils.error(TAG, e);
                                } finally {
                                    snackbar.dismiss();
                                }

                            });
                            snackbar.setAnchorView(mNavigation);
                            snackbar.addCallback(new Snackbar.Callback() {

                                @Override
                                public void onDismissed(Snackbar snackbar, int event) {
                                    if (deleteThreads.get()) {
                                        DeleteThreadsWorker.cleanup(getApplicationContext());
                                    }
                                    showFab(true);

                                }
                            });
                            showFab(false);
                            snackbar.show();
                        } else if (Objects.equals(deleteOperation.getKind(), DeleteOperation.PEERS)) {
                            String[] pids = deleteOperation.pids;


                            String message;
                            if (pids.length == 1) {
                                message = getString(R.string.delete_user);
                            } else {
                                message = getString(
                                        R.string.delete_users, "" + pids.length);
                            }

                            AtomicBoolean deleteUsers = new AtomicBoolean(true);
                            Snackbar snackbar = Snackbar.make(mDrawerLayout, message, Snackbar.LENGTH_LONG);
                            snackbar.setAction(getString(R.string.revert_operation), (view) -> {

                                try {
                                    deleteUsers.set(false);
                                    ExecutorService executor = Executors.newSingleThreadExecutor();
                                    executor.submit(() -> PEERS.getInstance(
                                            getApplicationContext()).setUsersVisible(pids));
                                } catch (Throwable e) {
                                    LogUtils.error(TAG, e);
                                } finally {
                                    snackbar.dismiss();
                                }

                            });
                            snackbar.setAnchorView(mNavigation);
                            snackbar.addCallback(new Snackbar.Callback() {

                                @Override
                                public void onDismissed(Snackbar snackbar, int event) {
                                    if (deleteUsers.get()) {
                                        UserService.deleteUsers(getApplicationContext(), pids);
                                    }
                                    showFab(true);

                                }
                            });
                            showFab(false);
                            snackbar.show();
                        }
                    }
                    eventViewModel.removeEvent(event);

                }
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }

        });
        eventViewModel.getError().observe(this, (event) -> {
            try {
                if (event != null) {
                    String content = event.getContent();
                    if (!content.isEmpty()) {
                        Snackbar snackbar = Snackbar.make(mDrawerLayout, content,
                                Snackbar.LENGTH_INDEFINITE);
                        snackbar.setAction(android.R.string.ok, (view) -> snackbar.dismiss());
                        snackbar.setAnchorView(mNavigation);
                        snackbar.addCallback(new Snackbar.Callback() {

                            @Override
                            public void onDismissed(Snackbar snackbar, int event) {
                                showFab(true);

                            }
                        });
                        showFab(false);
                        snackbar.show();
                    }
                    eventViewModel.removeEvent(event);

                }
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }

        });

        eventViewModel.getPermission().observe(this, (event) -> {
            try {
                if (event != null) {
                    String content = event.getContent();
                    if (!content.isEmpty()) {
                        Snackbar snackbar = Snackbar.make(mDrawerLayout, content,
                                Snackbar.LENGTH_INDEFINITE);
                        snackbar.setAction(R.string.app_settings, new PermissionAction());
                        snackbar.setAnchorView(mNavigation);
                        snackbar.addCallback(new Snackbar.Callback() {

                            @Override
                            public void onDismissed(Snackbar snackbar, int event) {
                                showFab(true);

                            }
                        });
                        showFab(false);
                        snackbar.show();

                    }
                    eventViewModel.removeEvent(event);
                }
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }

        });


        eventViewModel.getWarning().observe(this, (event) -> {
            try {
                if (event != null) {
                    String content = event.getContent();
                    if (!content.isEmpty()) {
                        Snackbar snackbar = Snackbar.make(mDrawerLayout, content,
                                Snackbar.LENGTH_SHORT);
                        snackbar.setAnchorView(mNavigation);
                        snackbar.addCallback(new Snackbar.Callback() {

                            @Override
                            public void onDismissed(Snackbar snackbar, int event) {
                                showFab(true);

                            }
                        });
                        showFab(false);
                        snackbar.show();
                    }
                    eventViewModel.removeEvent(event);
                }
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }

        });
        eventViewModel.getInfo().observe(this, (event) -> {
            try {
                if (event != null) {
                    String content = event.getContent();
                    if (!content.isEmpty()) {
                        Toast.makeText(getApplicationContext(), content, Toast.LENGTH_SHORT).show();
                    }
                    eventViewModel.removeEvent(event);
                }
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }

        });

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(broadcastReceiver, intentFilter);


        try {
            if (InitApplication.isAutoDiscovery(getApplicationContext())) {
                IPFS ipfs = IPFS.getInstance(getApplicationContext());
                registerService((int) ipfs.getSwarmPort());
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }

        Intent intent = getIntent();
        handleIntents(intent);

    }

    public void updateTitle(@NonNull Uri uri) {


        boolean http = Objects.equals(uri.getScheme(), Content.HTTP);

        mBrowserText.setTextAppearance(R.style.TextAppearance_AppCompat_Small);
        mBrowserText.setClickable(true);
        if (!http) {
            mBrowserText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.lock, 0, 0, 0
            );
        } else {
            mBrowserText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.lock_open, 0, 0, 0
            );
        }
        mBrowserText.setCompoundDrawablePadding(8);
        mBrowserText.setBackgroundResource(R.drawable.browser);
        mBrowserText.getBackground().setAlpha(30);

        mBrowserText.setText(uri.toString());

    }


    private void updateDownload(@NonNull Uri uri) {
        downloadActive.set(Objects.equals(uri.getScheme(), Content.IPFS) ||
                Objects.equals(uri.getScheme(), Content.IPNS));
    }

    @Override
    public void updateUri(@NonNull Uri uri, boolean forward) {
        forwardActive.set(forward);
        uriAtomicReference.set(uri);
        updateTitle(uri);
        updateBookmark(uri);
        updateDownload(uri);
    }
}