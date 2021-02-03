package threads.server.fragments;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.HttpAuthHandler;
import android.webkit.URLUtil;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ProgressBar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.ByteArrayInputStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import threads.LogUtils;
import threads.server.InitApplication;
import threads.server.MainActivity;
import threads.server.R;
import threads.server.core.Content;
import threads.server.core.DOCS;
import threads.server.core.events.EVENTS;
import threads.server.core.page.Bookmark;
import threads.server.core.page.PAGES;
import threads.server.ipfs.Closeable;
import threads.server.provider.FileDocumentsProvider;
import threads.server.services.LiteService;
import threads.server.utils.CustomWebChromeClient;
import threads.server.utils.MimeType;
import threads.server.utils.SelectionViewModel;
import threads.server.work.ClearCacheWorker;
import threads.server.work.DownloadContentWorker;
import threads.server.work.DownloadFileWorker;

public class BrowserFragment extends Fragment implements
        SwipeRefreshLayout.OnRefreshListener {


    private static final String TAG = BrowserFragment.class.getSimpleName();
    private static final String DOWNLOADS = "content://com.android.externalstorage.documents/document/primary:Download";
    private static final long CLICK_OFFSET = 500;
    private Context mContext;
    private final ActivityResultLauncher<Intent> mFileForResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
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
                        LiteService.FileInfo fileInfo = LiteService.getFileInfo(mContext);
                        Objects.requireNonNull(fileInfo);
                        DownloadFileWorker.download(mContext, uri, fileInfo.getUri(),
                                fileInfo.getFilename(), fileInfo.getMimeType(), fileInfo.getSize());


                    } catch (Throwable e) {
                        LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
                    }
                }
            });
    private final ActivityResultLauncher<Intent> mContentForResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
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
                        Uri contentUri = LiteService.getContentUri(mContext);
                        Objects.requireNonNull(contentUri);
                        DownloadContentWorker.download(mContext, uri, contentUri);

                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }
                }
            });
    private WebView mWebView;
    private FragmentActivity mActivity;
    private BrowserFragment.ActionListener mListener;
    private ProgressBar mProgressBar;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private long mLastClickTime = 0;
    private MenuItem mActionBookmark;
    private CustomWebChromeClient mCustomWebChromeClient;

    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
        mActivity = null;

    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
        mActivity = getActivity();
        mListener = (BrowserFragment.ActionListener) getActivity();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        super.onCreateOptionsMenu(menu, menuInflater);
        menuInflater.inflate(R.menu.menu_browser_fragment, menu);

        mActionBookmark = menu.findItem(R.id.action_bookmark);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.browser_view, container, false);
    }

    public boolean onBackPressed() {

        if (mWebView.canGoBack()) {
            mWebView.goBack();
            return true;
        }

        return false;
    }


    private void loadUrl(@NonNull String uri) {

        mSwipeRefreshLayout.setDistanceToTriggerSync(999999);
        preload(Uri.parse(uri));
        checkBookmark(uri);
        mListener.updateTitle(uri);
        mWebView.loadUrl(uri);


    }

    private void preload(@NonNull Uri uri) {
        if (Objects.equals(uri.getScheme(), Content.IPFS) ||
                Objects.equals(uri.getScheme(), Content.IPNS)) {
            try {
                if (Objects.equals(uri.getScheme(), Content.IPNS)) {
                    PAGES pages = PAGES.getInstance(mContext);
                    String name = uri.getHost();
                    if (name != null) {
                        pages.removeResolver(name);
                    }
                }

                mProgressBar.setVisibility(View.VISIBLE);

            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }
        }
    }

    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        int itemId = item.getItemId();
        if (itemId == R.id.action_share) {

            try {
                if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                    return true;
                }
                mLastClickTime = SystemClock.elapsedRealtime();

                String uri = mWebView.getUrl();


                ComponentName[] names = {new ComponentName(mContext, MainActivity.class)};

                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_link));
                intent.putExtra(Intent.EXTRA_TEXT, uri);
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
        } else if (itemId == R.id.action_bookmark) {

            if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                return true;
            }
            mLastClickTime = SystemClock.elapsedRealtime();

            try {
                String uri = mWebView.getUrl();

                Objects.requireNonNull(uri);

                PAGES pages = PAGES.getInstance(mContext);

                Bookmark bookmark = pages.getBookmark(uri);
                if (bookmark != null) {
                    String name = bookmark.getTitle();
                    pages.removeBookmark(bookmark);
                    if (mActionBookmark != null) {
                        mActionBookmark.setIcon(R.drawable.star_outline);
                    }
                    EVENTS.getInstance(mContext).warning(
                            getString(R.string.bookmark_removed, name));
                } else {
                    Bitmap bitmap = mCustomWebChromeClient.getFavicon(uri);

                    String title = mCustomWebChromeClient.getTitle(uri);
                    if (title == null) {
                        title = "" + mWebView.getTitle();
                    }

                    bookmark = pages.createBookmark(uri, title);
                    if (bitmap != null) {
                        bookmark.setBitmapIcon(bitmap);
                    }

                    pages.storeBookmark(bookmark);

                    if (mActionBookmark != null) {
                        mActionBookmark.setIcon(R.drawable.star);
                    }
                    EVENTS.getInstance(mContext).warning(
                            getString(R.string.bookmark_added, title));
                }


            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
            return true;
        } else if (itemId == R.id.action_clear_cache) {

            try {

                mWebView.clearHistory();
                mWebView.clearCache(true);

                ClearCacheWorker.clearCache(mContext);

                EVENTS.getInstance(mContext).warning(
                        getString(R.string.clear_cache));

            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }
            return true;
        } else if (itemId == R.id.action_bookmarks) {


            if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                return true;
            }
            mLastClickTime = SystemClock.elapsedRealtime();

            BookmarksDialogFragment dialogFragment = new BookmarksDialogFragment();

            dialogFragment.show(getChildFragmentManager(), BookmarksDialogFragment.TAG);

            return true;
        } else if (itemId == R.id.action_find_page) {


            if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                return true;
            }
            mLastClickTime = SystemClock.elapsedRealtime();

            try {
                ((AppCompatActivity)
                        mActivity).startSupportActionMode(
                        createFindActionModeCallback());
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final DOCS docs = DOCS.getInstance(mContext);

        mSwipeRefreshLayout = view.findViewById(R.id.swipe_container);
        mSwipeRefreshLayout.setOnRefreshListener(this);
        mSwipeRefreshLayout.setColorSchemeResources(R.color.colorAccent,
                android.R.color.holo_green_dark,
                android.R.color.holo_orange_dark,
                android.R.color.holo_blue_dark);

        mProgressBar = view.findViewById(R.id.progress_bar);
        mProgressBar.setVisibility(View.GONE);


        mWebView = view.findViewById(R.id.web_view);


        mCustomWebChromeClient = new CustomWebChromeClient(mActivity) {

            public void onProgressChanged(WebView view, int newProgress) {

                mProgressBar.setVisibility(View.VISIBLE);

            }
        };
        mWebView.setWebChromeClient(mCustomWebChromeClient);

        InitApplication.setWebSettings(mWebView);

        SelectionViewModel mSelectionViewModel = new ViewModelProvider(mActivity).get(SelectionViewModel.class);


        mSelectionViewModel.getUri().observe(getViewLifecycleOwner(), (uri) -> {
            if (uri != null) {
                loadUrl(uri);
            }
        });


        mWebView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {

            try {
                LogUtils.error(TAG, "downloadUrl : " + url);

                Uri uri = Uri.parse(url);
                if (Objects.equals(uri.getScheme(), Content.IPFS) ||
                        Objects.equals(uri.getScheme(), Content.IPNS)) {
                    contentDownloader(uri);
                } else {
                    String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
                    fileDownloader(uri, filename, mimeType, contentLength);
                }


            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });

        mWebView.setWebViewClient(new WebViewClient() {


            @Override
            public void onPageCommitVisible(WebView view, String url) {
                super.onPageCommitVisible(view, url);
                LogUtils.error(TAG, "onPageCommitVisible " + url);

                mProgressBar.setVisibility(View.GONE);

            }


            @Override
            public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {

                try {
                    EVENTS.getInstance(mContext).warning(
                            "Login mechanism not yet implemented");
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }
                super.onReceivedHttpAuthRequest(view, handler, host, realm);
            }


            @Override
            public void onLoadResource(WebView view, String url) {
                LogUtils.error(TAG, "onLoadResource : " + url);
                super.onLoadResource(view, url);
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                LogUtils.error(TAG, "doUpdateVisitedHistory : " + url + " " + isReload);

                mSwipeRefreshLayout.setDistanceToTriggerSync(100);
                checkBookmark(url);
                mListener.updateTitle(url);
                super.doUpdateVisitedHistory(view, url, isReload);
            }

            @Override
            public void onPageStarted(WebView view, String uri, Bitmap favicon) {
                LogUtils.error(TAG, "onPageStarted : " + uri);

                mSwipeRefreshLayout.setDistanceToTriggerSync(999999);
                checkBookmark(uri);
                mListener.updateTitle(uri);
            }


            @Override
            public void onPageFinished(WebView view, String url) {

                LogUtils.error(TAG, "onPageFinished : " + url);
                mProgressBar.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                LogUtils.error(TAG, "" + error.getDescription());
            }


            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {

                try {
                    Uri uri = request.getUrl();
                    LogUtils.error(TAG, "shouldOverrideUrlLoading : " + uri);

                    if (Objects.equals(uri.getScheme(), Content.ABOUT)) {
                        return true;
                    } else if (Objects.equals(uri.getScheme(), Content.HTTP) ||
                            Objects.equals(uri.getScheme(), Content.HTTPS)) {
                        return false;
                    } else if (Objects.equals(uri.getScheme(), Content.IPNS) ||
                            Objects.equals(uri.getScheme(), Content.IPFS)) {

                        String res = uri.getQueryParameter("download");
                        if (Objects.equals(res, "1")) {
                            contentDownloader(uri);
                            return true;
                        }
                        return false;

                    } else if (Objects.equals(uri.getScheme(), Content.MAGNET)) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

                            startActivity(intent);

                        } catch (Throwable ignore) {
                            EVENTS.getInstance(mContext).warning(
                                    getString(R.string.no_activity_found_to_handle_uri));
                        }
                        return true;
                    } else {
                        try {
                            // all other stuff
                            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

                            startActivity(intent);

                        } catch (Throwable ignore) {
                            EVENTS.getInstance(mContext).warning(
                                    getString(R.string.no_activity_found_to_handle_uri));
                        }
                        return true;
                    }

                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }
                return false;

            }


            public WebResourceResponse createEmptyResource() {
                return new WebResourceResponse("text/plain", Content.UTF8,
                        new ByteArrayInputStream("".getBytes()));
            }

            public WebResourceResponse createErrorMessage(@NonNull Throwable throwable) {
                String message = docs.generateErrorHtml(throwable);
                return new WebResourceResponse("text/html", Content.UTF8,
                        new ByteArrayInputStream(message.getBytes()));
            }


            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {

                try {
                    Uri uri = request.getUrl();
                    LogUtils.error(TAG, "shouldInterceptRequest : " + uri.toString());

                    if (Objects.equals(uri.getScheme(), Content.IPNS) ||
                            Objects.equals(uri.getScheme(), Content.IPFS)) {

                        String host = getHost(uri.toString());
                        if (host != null) {
                            try {
                                String pid = docs.decodeName(host);
                                if (pid != null) {
                                    LiteService.connect(mContext, pid);
                                }
                            } catch (Throwable throwable) {
                                LogUtils.error(TAG, throwable);
                            }
                        }

                        final AtomicLong time = new AtomicLong(System.currentTimeMillis());
                        long timeout = 100000; // BROWSER TIMEOUT


                        Closeable closeable = () -> System.currentTimeMillis() - time.get() > timeout;
                        {
                            Pair<Uri, Boolean> result = docs.redirectUri(uri, closeable);
                            if (result.second) {
                                mActivity.runOnUiThread(() -> {
                                    mWebView.stopLoading();
                                    mWebView.loadUrl(result.first.toString());
                                });
                                return null;
                            }
                            uri = result.first;
                        }
                        if (closeable.isClosed()) {
                            throw new DOCS.TimeoutException(uri.toString());
                        }

                        return docs.getResponse(uri, closeable);
                    }
                } catch (Throwable throwable) {
                    return createErrorMessage(throwable);
                }
                return null;
            }
        });

    }



    private void checkBookmark(@Nullable String uri) {
        try {
            if (uri != null) {
                PAGES pages = PAGES.getInstance(mContext);
                if (pages.hasBookmark(uri)) {
                    if (mActionBookmark != null) {
                        mActionBookmark.setIcon(R.drawable.star);
                    }
                } else {
                    if (mActionBookmark != null) {
                        mActionBookmark.setIcon(R.drawable.star_outline);
                    }
                }
            } else {
                if (mActionBookmark != null) {
                    mActionBookmark.setVisible(false);
                }
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }


    private void fileDownloader(@NonNull Uri uri, @NonNull String filename, @NonNull String mimeType,
                                long size) {

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(R.string.download_title);
        builder.setMessage(filename);

        builder.setPositiveButton(getString(android.R.string.yes), (dialog, which) -> {


            LiteService.setFileInfo(mContext, uri, filename, mimeType, size);

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(DOWNLOADS));
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            mFileForResult.launch(intent);
            mProgressBar.setVisibility(View.GONE);
            EVENTS.getInstance(mContext).warning(filename);

        });
        builder.setNeutralButton(getString(android.R.string.cancel), (dialog, which) -> {
            mProgressBar.setVisibility(View.GONE);
            dialog.cancel();
        });
        builder.show();

    }


    private void contentDownloader(@NonNull Uri uri) {

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(R.string.download_title);
        String filename = LiteService.getFileName(uri);
        builder.setMessage(filename);

        builder.setPositiveButton(getString(android.R.string.yes), (dialog, which) -> {

            LiteService.setContentUri(mContext, uri);

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(DOWNLOADS));
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            mContentForResult.launch(intent);

            mProgressBar.setVisibility(View.GONE);

            EVENTS.getInstance(mContext).warning(filename);

        });
        builder.setNeutralButton(getString(android.R.string.cancel), (dialog, which) -> {
            mProgressBar.setVisibility(View.GONE);
            dialog.cancel();
        });
        builder.show();

    }


    @Override
    public void onRefresh() {
        mSwipeRefreshLayout.setRefreshing(true);

        try {
            preload(Uri.parse(mWebView.getUrl()));
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        try {
            mWebView.reload();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        } finally {
            mSwipeRefreshLayout.setRefreshing(false);
        }

    }

    private ActionMode.Callback createFindActionModeCallback() {
        return new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.menu_find_action_mode, menu);


                MenuItem action_mode_find = menu.findItem(R.id.action_mode_find);
                EditText mFindText = (EditText) action_mode_find.getActionView();
                mFindText.setMinWidth(200);
                mFindText.setMaxWidth(400);
                mFindText.setSingleLine();
                mFindText.setBackgroundResource(android.R.color.transparent);
                mFindText.setHint(R.string.find_page);
                mFindText.setFocusable(true);
                mFindText.requestFocus();

                mFindText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                    }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                    }

                    @Override
                    public void afterTextChanged(Editable editable) {
                        mWebView.findAllAsync(mFindText.getText().toString());
                    }
                });


                mode.setTitle("0/0");

                mWebView.setFindListener((activeMatchOrdinal, numberOfMatches, isDoneCounting) -> {
                    try {
                        String result = "" + activeMatchOrdinal + "/" + numberOfMatches;
                        mode.setTitle(result);
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
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
                int itemId = item.getItemId();
                if (itemId == R.id.action_mode_previous) {


                    if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                        return true;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    try {
                        mWebView.findNext(false);
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }

                    return true;
                } else if (itemId == R.id.action_mode_next) {


                    if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                        return true;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();
                    try {
                        mWebView.findNext(true);
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }

                    return true;

                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                try {
                    mWebView.clearMatches();
                    mWebView.setFindListener(null);
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }
            }
        };

    }



    @Nullable
    private String getHost(@NonNull String url) {
        try {
            Uri uri = Uri.parse(url);
            if (Objects.equals(uri.getScheme(), Content.IPNS)) {
                return uri.getHost();
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return null;
    }

    public interface ActionListener {

        void updateTitle(@Nullable String uri);

    }
}
