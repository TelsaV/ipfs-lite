package threads.server;


import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.URLUtil;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ShareCompat;
import androidx.core.view.MenuCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import threads.LogUtils;
import threads.server.core.DOCS;
import threads.server.core.DeleteOperation;
import threads.server.core.events.EVENTS;
import threads.server.core.events.EventViewModel;
import threads.server.core.page.PageViewModel;
import threads.server.core.peers.Content;
import threads.server.core.peers.PEERS;
import threads.server.core.threads.THREADS;
import threads.server.fragments.ActionListener;
import threads.server.fragments.BrowserFragment;
import threads.server.fragments.EditContentDialogFragment;
import threads.server.fragments.EditPeerDialogFragment;
import threads.server.fragments.PeersFragment;
import threads.server.fragments.PinsFragment;
import threads.server.fragments.SettingsFragment;
import threads.server.fragments.ThreadsFragment;
import threads.server.ipfs.IPFS;
import threads.server.provider.FileDocumentsProvider;
import threads.server.services.DiscoveryService;
import threads.server.services.LiteService;
import threads.server.services.RegistrationService;
import threads.server.services.UploadService;
import threads.server.services.UserService;
import threads.server.utils.CodecDecider;
import threads.server.utils.MimeType;
import threads.server.utils.PermissionAction;
import threads.server.utils.SelectionViewModel;
import threads.server.work.LocalConnectWorker;
import threads.server.work.PageWorker;
import threads.server.work.SwarmConnectWorker;
import threads.server.work.UploadThreadWorker;
import threads.server.work.UploadUriWorker;


public class MainActivity extends AppCompatActivity implements
        ThreadsFragment.ActionListener,
        BrowserFragment.ActionListener,
        PinsFragment.ActionListener,
        PeersFragment.ActionListener,
        ActionListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String FRAG = "FRAG";
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            SwarmConnectWorker.connect(getApplicationContext());
        }
    };
    private final AtomicInteger currentFragment = new AtomicInteger();
    private long mLastClickTime = 0;
    private CoordinatorLayout mDrawerLayout;
    private BottomNavigationView mNavigation;
    private NsdManager mNsdManager;
    private FloatingActionButton mFloatingActionButton;
    private SelectionViewModel mSelectionViewModel;
    private TextView mBrowserText;
    private ActionMode mActionMode;

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
            String host = IPFS.getPeerID(getApplicationContext());
            Objects.requireNonNull(host);
            String serviceType = "_ipfs-discovery._udp";
            NsdServiceInfo serviceInfo = new NsdServiceInfo();
            serviceInfo.setServiceName(host);
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
                        boolean connect = !Objects.equals(host, serviceName);
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.ThreadsTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle(null);
        Toolbar mToolbar = findViewById(R.id.toolbar);

        setSupportActionBar(mToolbar);


        ImageButton mActionHome = findViewById(R.id.action_home);


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

        final IPFS ipfs = IPFS.getInstance(getApplicationContext());
        try {
            ipfs.startDaemon(IPFS.isPrivateSharingEnabled(getApplicationContext()));
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

        final DOCS docs = DOCS.getInstance(getApplicationContext());
        Uri uri = docs.getPinsPageUri();
        mSelectionViewModel.setUri(uri.toString());
        updateTitle(uri.toString());


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
                setFabImage(R.drawable.plus_thick);
                return true;
            } else if (itemId == R.id.navigation_peers) {

                loadFragment(new PeersFragment(), R.id.navigation_peers);
                showFab(true);
                mSelectionViewModel.setParentThread(0L);
                setFabImage(R.drawable.account_plus);
                return true;
            } else if (itemId == R.id.navigation_browser) {

                loadFragment(new BrowserFragment(), R.id.navigation_browser);
                showFab(false);
                mSelectionViewModel.setParentThread(0L);
                return true;
            } else if (itemId == R.id.navigation_settings) {

                loadFragment(new SettingsFragment(), R.id.navigation_settings);
                showFab(false);
                mSelectionViewModel.setParentThread(0L);
                return true;
            }
            return false;

        });

        mActionHome.setOnClickListener(view -> {

            try {
                if (docs.isPinsPageOutdated()) {
                    PageWorker.publish(getApplicationContext(), true);
                }

                openBrowserView(docs.getPinsPageUri());

            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }
        });


        if (savedInstanceState != null) {
            mNavigation.setSelectedItemId(savedInstanceState.getInt(FRAG));
        } else {
            mNavigation.setSelectedItemId(R.id.navigation_files);
        }


        mDrawerLayout = findViewById(R.id.drawer_layout);


        PageViewModel pageViewModel =
                new ViewModelProvider(this).get(PageViewModel.class);

        pageViewModel.getPage(docs.getHost()).observe(this, (page) -> {
            try {
                if (page != null) {
                    if (page.isOutdated()) {
                        PageWorker.publish(getApplicationContext(), true);
                    }
                }
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });

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
                                        LiteService.threads(getApplicationContext(), idxs);
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
                registerService((int) ipfs.getSwarmPort());
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }

        Intent intent = getIntent();
        handleIntents(intent);

    }

    private void clickFilesAdd() {

        Fragment fragment = getSupportFragmentManager().findFragmentById(
                R.id.fragment_container);
        if (fragment instanceof ThreadsFragment) {
            ThreadsFragment threadsFragment = (ThreadsFragment) fragment;
            if (threadsFragment.isResumed()) {
                threadsFragment.clickFilesAdd();
            }
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

            ShareCompat.IntentReader intentReader = ShareCompat.IntentReader.from(this);
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
            } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
                String query =
                        intent.getStringExtra(SearchManager.QUERY);
                if (query == null) {
                    query = intent.getDataString();
                }
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

                            String search = "https://duckduckgo.com/?q=" + query + "&kp=-1";
                            if (ipfs.isValidCID(query)) {
                                search = Content.IPFS + "://" + query;
                            }
                            openBrowserView(Uri.parse(search));
                        }
                    }
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }
            }

        } catch (Throwable e) {
            LogUtils.error(TAG, "" + e.getLocalizedMessage());
        }
    }

    @Override
    public void openBrowserView(@NonNull Uri uri) {
        mSelectionViewModel.setUri(uri.toString());
        mNavigation.setSelectedItemId(R.id.navigation_browser);
    }

    private void handleSend(ShareCompat.IntentReader intentReader) {

        try {
            Objects.requireNonNull(intentReader);
            if (intentReader.isMultipleShare()) {

                int items = intentReader.getStreamCount();
                if (items > 0) {
                    List<WorkContinuation> continuations = new ArrayList<>();

                    for (int i = 0; i < items; i++) {
                        Uri uri = intentReader.getStream(i);
                        if (uri != null) {

                            if (!FileDocumentsProvider.hasReadPermission(getApplicationContext(), uri)) {
                                EVENTS.getInstance(getApplicationContext()).error(
                                        getString(R.string.file_has_no_read_permission));
                                continue;
                            }

                            if (FileDocumentsProvider.isPartial(getApplicationContext(), uri)) {

                                EVENTS.getInstance(getApplicationContext()).error(
                                        getString(R.string.file_not_found));

                                continue;
                            }


                            continuations.add(WorkManager.getInstance(getApplicationContext())
                                    .beginWith(UploadUriWorker.getWork(uri, i * 250))
                                    .then(UploadThreadWorker.getSharedWork()));
                        }
                    }
                    WorkContinuation.combine(continuations).enqueue();


                }
            } else {
                String type = intentReader.getType();
                if (Objects.equals(type, MimeType.PLAIN_MIME_TYPE)) {
                    CharSequence textObject = intentReader.getText();
                    Objects.requireNonNull(textObject);
                    String text = textObject.toString();
                    if (!text.isEmpty()) {
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
                            Uri uri = Uri.parse(text);
                            openBrowserView(uri);
                        } else if (result.getCodex() == CodecDecider.Codec.MULTIADDRESS) {

                            String user = result.getPeerID();
                            String address = result.getPeerAddress();
                            String host = IPFS.getPeerID(getApplicationContext());
                            if (user.equals(host)) {
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
                                Uri uri = Uri.parse(text);
                                openBrowserView(uri);
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

                    WorkManager.getInstance(getApplicationContext()).beginWith(
                            UploadUriWorker.getWork(uri, 0))
                            .then(UploadThreadWorker.getSharedWork())
                            .enqueue();
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

    @Override
    public void updateTitle(@Nullable String uri) {

        if (uri != null) {
            boolean http = Objects.equals(Uri.parse(uri).getScheme(), Content.HTTP);

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

            mBrowserText.setText(uri);
        }
    }


    private ActionMode.Callback createSearchActionModeCallback(@NonNull String hint) {
        return new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.menu_searchable, menu);

                mode.setCustomView(null);
                mode.setTitle("");
                mode.setTitleOptionalHint(true);

                MenuItem searchMenuItem = menu.findItem(R.id.action_search);
                SearchView mSearchView = (SearchView) searchMenuItem.getActionView();
                mSearchView.setMaxWidth(Integer.MAX_VALUE);
                SearchManager searchManager = (SearchManager) getSystemService(SEARCH_SERVICE);
                mSearchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));


                mSearchView.requestFocus();
                mSearchView.setIconifiedByDefault(false);
                mSearchView.setIconified(false);
                mSearchView.setSubmitButtonEnabled(false);
                mSearchView.setQueryHint(hint);
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

    @Override
    public void openUri(@NonNull Uri uri) {
        openBrowserView(uri);
    }

    @Override
    public WebView getWebView() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(
                R.id.fragment_container);
        if (fragment instanceof BrowserFragment) {
            BrowserFragment threadsFragment = (BrowserFragment) fragment;
            if (threadsFragment.isResumed()) {
                return threadsFragment.getWebView();
            }
        }
        return null;
    }
}