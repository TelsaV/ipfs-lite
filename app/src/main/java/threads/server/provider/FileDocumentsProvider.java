package threads.server.provider;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsProvider;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import threads.LogUtils;
import threads.server.BuildConfig;
import threads.server.InitApplication;
import threads.server.R;
import threads.server.core.Content;
import threads.server.core.DOCS;
import threads.server.core.threads.THREADS;
import threads.server.core.threads.Thread;
import threads.server.ipfs.IPFS;
import threads.server.utils.MimeType;

import static android.provider.DocumentsContract.QUERY_ARG_DISPLAY_NAME;
import static android.provider.DocumentsContract.QUERY_ARG_MIME_TYPES;

public class FileDocumentsProvider extends DocumentsProvider {
    public static final String SCHEME = "content";
    private static final String TAG = FileDocumentsProvider.class.getSimpleName();
    private static final String DOCUMENT = "document";
    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[]{
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE
    };

    private String appName;
    private String rootDir;
    private THREADS threads;
    private IPFS ipfs;
    private DOCS docs;


    public static boolean isLocalUri(@NonNull Uri uri) {
        try {
            return Objects.equals(uri.getScheme(), SCHEME) &&
                    Objects.equals(uri.getAuthority(), BuildConfig.DOCUMENTS_AUTHORITY);

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return false;
    }

    private static long getDocument(@NonNull String id) {
        try {
            return Long.parseLong(id);
        } catch (Throwable ignore) {
            // nothing to report here
        }
        return -1;
    }

    public static long getDocument(@NonNull Uri uri) {
        if (isLocalUri(uri)) {
            String pathSegment = uri.getLastPathSegment();
            Objects.requireNonNull(pathSegment);
            return getDocument(pathSegment);
        }
        return -1;
    }

    public static boolean isPartial(@NonNull Context context, @NonNull Uri uri) {
        ContentResolver contentResolver = context.getContentResolver();
        try (Cursor cursor = contentResolver.query(uri, new String[]{
                DocumentsContract.Document.COLUMN_FLAGS}, null, null, null)) {

            Objects.requireNonNull(cursor);
            cursor.moveToFirst();

            int docFlags = cursor.getInt(0);
            if ((docFlags & DocumentsContract.Document.FLAG_PARTIAL) != 0) {
                return true;
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
        }
        return false;
    }

    private static String[] resolveRootProjection(String[] projection) {
        String[] DEFAULT_ROOT_PROJECTION;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            DEFAULT_ROOT_PROJECTION = new String[]{
                    DocumentsContract.Root.COLUMN_ROOT_ID,
                    DocumentsContract.Root.COLUMN_ICON,
                    DocumentsContract.Root.COLUMN_TITLE,
                    DocumentsContract.Root.COLUMN_SUMMARY,
                    DocumentsContract.Root.COLUMN_FLAGS,
                    DocumentsContract.Root.COLUMN_QUERY_ARGS,
                    DocumentsContract.Root.COLUMN_DOCUMENT_ID
            };
        } else {
            DEFAULT_ROOT_PROJECTION = new String[]{
                    DocumentsContract.Root.COLUMN_ROOT_ID,
                    DocumentsContract.Root.COLUMN_ICON,
                    DocumentsContract.Root.COLUMN_TITLE,
                    DocumentsContract.Root.COLUMN_SUMMARY,
                    DocumentsContract.Root.COLUMN_FLAGS,
                    DocumentsContract.Root.COLUMN_DOCUMENT_ID
            };
        }
        return projection != null ? projection : DEFAULT_ROOT_PROJECTION;
    }

    private static String[] resolveDocumentProjection(String[] projection) {
        return projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION;
    }


    @SuppressWarnings("SameReturnValue")
    @NonNull
    private static String getRoot() {
        return "0";
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean hasReadPermission(@NonNull Context context, @NonNull Uri uri) {
        int perm = context.checkUriPermission(uri, Binder.getCallingPid(), Binder.getCallingUid(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return perm != PackageManager.PERMISSION_DENIED;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean hasWritePermission(@NonNull Context context, @NonNull Uri uri) {
        int perm = context.checkUriPermission(uri, Binder.getCallingPid(), Binder.getCallingUid(),
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        return perm != PackageManager.PERMISSION_DENIED;
    }


    @NonNull
    public static String getFileName(@NonNull Context context, @NonNull Uri uri) {
        String filename = null;

        ContentResolver contentResolver = context.getContentResolver();
        try (Cursor cursor = contentResolver.query(uri,
                null, null, null, null)) {

            Objects.requireNonNull(cursor);
            cursor.moveToFirst();
            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            filename = cursor.getString(nameIndex);
        } catch (Throwable e) {
            LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
        }

        if (filename == null) {
            filename = uri.getLastPathSegment();
        }

        if (filename == null) {
            filename = "file_name_not_detected";
        }

        return filename;
    }

    @NonNull
    public static String getMimeType(@NonNull Context context, @NonNull Uri uri) {
        String mimeType = context.getContentResolver().getType(uri);
        if (mimeType == null) {
            mimeType = MimeType.OCTET_MIME_TYPE;
        }
        return mimeType;
    }

    public static long getFileSize(@NonNull Context context, @NonNull Uri uri) {

        ContentResolver contentResolver = context.getContentResolver();

        try (Cursor cursor = contentResolver.query(uri,
                null, null, null, null)) {

            Objects.requireNonNull(cursor);
            cursor.moveToFirst();
            int nameIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
            return cursor.getLong(nameIndex);
        } catch (Throwable e) {
            LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
        }


        try (ParcelFileDescriptor fd = contentResolver.openFileDescriptor(uri, "r")) {
            Objects.requireNonNull(fd);
            return fd.getStatSize();
        } catch (Throwable e) {
            LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
        }
        return -1;
    }

    public static Uri getUriForThread(long idx) {
        Uri.Builder builder = new Uri.Builder();
        builder.scheme(SCHEME)
                .authority(BuildConfig.DOCUMENTS_AUTHORITY)
                .appendPath(DOCUMENT)
                .appendPath("" + idx);

        return builder.build();
    }

    @Nullable
    public static Uri getThumbnailUriForThread(Thread thread) {
        if (thread.isSeeding()) {

            String mimeType = thread.getMimeType();
            String uri = thread.getUri();
            if (uri != null) {
                if (mimeType.startsWith(MimeType.IMAGE) ||
                        mimeType.startsWith(MimeType.VIDEO)) {
                    Uri url = Uri.parse(uri);
                    String scheme = url.getScheme();
                    if (!Objects.equals(scheme, Content.IPNS) &&
                            !Objects.equals(scheme, Content.IPFS) &&
                            !Objects.equals(scheme, Content.HTTP) &&
                            !Objects.equals(scheme, Content.HTTPS)) {

                        return url;
                    }
                }
            } else {
                if (mimeType.startsWith(MimeType.IMAGE)) {
                    return getUriForThread(thread.getIdx());
                }
            }
        }
        return null;
    }

    public static Uri getUriForThread(Thread thread) {
        return getUriForThread(thread.getIdx());
    }


    public String renameDocument(String documentId, String displayName)
            throws FileNotFoundException {

        try {
            long idx = Long.parseLong(documentId);
            docs.renameDocument(idx, displayName);
            return null;
        } catch (Throwable throwable) {
            throw new FileNotFoundException("" + throwable.getLocalizedMessage());
        }
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        MatrixCursor result = new MatrixCursor(resolveRootProjection(projection));

        String rootId = BuildConfig.DOCUMENTS_AUTHORITY;
        MatrixCursor.RowBuilder row = result.newRow();
        row.add(DocumentsContract.Root.COLUMN_ROOT_ID, rootId);
        row.add(DocumentsContract.Root.COLUMN_ICON, R.drawable.app_icon);
        row.add(DocumentsContract.Root.COLUMN_TITLE, appName);
        row.add(DocumentsContract.Root.COLUMN_FLAGS,
                DocumentsContract.Root.FLAG_LOCAL_ONLY |
                        /* TODO activate again
                            DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD |
                             DocumentsContract.Root.FLAG_SUPPORTS_CREATE |*/
                        DocumentsContract.Root.FLAG_SUPPORTS_RECENTS |
                        DocumentsContract.Root.FLAG_SUPPORTS_SEARCH);
        row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, getRoot());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            row.add(DocumentsContract.Root.COLUMN_QUERY_ARGS,
                    "android:query-arg-mime-types\nandroid:query-arg-display-name");
        }
        row.add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*");
        return result;
    }

    @Override
    public boolean isChildDocument(String parentDocumentId,
                                   String documentId) {
        LogUtils.info(TAG, "isChildDocument : " + documentId + " " + parentDocumentId);
        try {
            List<String> paths = getPaths(documentId);
            return paths.contains(parentDocumentId);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        return false;
    }

    private void fillPaths(@NonNull Thread thread, @NonNull List<String> paths) {

        paths.add(0, "" + thread.getIdx());
        if (thread.getParent() > 0) {
            Thread parent = threads.getThreadByIdx(thread.getParent());
            Objects.requireNonNull(parent);
            fillPaths(parent, paths);
        }
    }

    private List<String> getPaths(@NonNull String childDocumentId) {
        List<String> paths = new ArrayList<>();
        long idx = Long.parseLong(childDocumentId);
        if (idx > 0) {
            Thread file = threads.getThreadByIdx(idx);
            Objects.requireNonNull(file);
            if (!file.isDeleting()) {
                fillPaths(file, paths);
            }
        }
        paths.add(0, getRoot());
        return paths;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public DocumentsContract.Path findDocumentPath(@Nullable String parentDocumentId, String childDocumentId)
            throws FileNotFoundException {
        LogUtils.info(TAG, "" + parentDocumentId + " " + childDocumentId);

        try {
            List<String> paths = getPaths(childDocumentId);
            return new DocumentsContract.Path(BuildConfig.DOCUMENTS_AUTHORITY, paths);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
            throw new FileNotFoundException(throwable.getLocalizedMessage());
        }
    }

    public String copyDocument(String sourceDocumentId, String targetParentDocumentId) throws FileNotFoundException {
        LogUtils.info(TAG, "copyDocument : " + sourceDocumentId);
        try {
            long idx = Long.parseLong(sourceDocumentId);
            long parent = Long.parseLong(targetParentDocumentId);
            long result = docs.copyDocument(parent, idx);

            return String.valueOf(result);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
            throw new FileNotFoundException("" + e.getLocalizedMessage());
        }
    }

    public String moveDocument(String sourceDocumentId, String sourceParentDocumentId, String targetParentDocumentId) throws FileNotFoundException {
        LogUtils.info(TAG, "copyDocument : " + sourceDocumentId);
        try {
            long idx = Long.parseLong(sourceDocumentId);
            long sourceIdx = Long.parseLong(sourceParentDocumentId);
            long targetIdx = Long.parseLong(targetParentDocumentId);

            docs.moveDocument(idx, sourceIdx, targetIdx);

            return String.valueOf(idx);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
            throw new FileNotFoundException("" + e.getLocalizedMessage());
        }
    }

    private void deleteDocument(long idx) {
        Context context = getContext();
        docs.deleteDocuments(context, idx);
    }

    public void removeDocument(String documentId, String parentDocumentId) throws FileNotFoundException {
        LogUtils.info(TAG, "removeDocument : " + documentId);
        try {
            long idx = Long.parseLong(documentId);
            deleteDocument(idx);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
            throw new FileNotFoundException("" + e.getLocalizedMessage());
        }
    }

    public void deleteDocument(String documentId) throws FileNotFoundException {
        LogUtils.info(TAG, "deleteDocument : " + documentId);
        try {
            long idx = Long.parseLong(documentId);
            deleteDocument(idx);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
            throw new FileNotFoundException("" + e.getLocalizedMessage());
        }
    }

    @Override
    public Cursor queryRecentDocuments(String rootId, String[] projection) {

        final MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));


        List<Thread> entries = threads.getNewestThreads(25);
        for (Thread thread : entries) {
            includeFile(result, thread);
        }

        return result;
    }

    @Override
    public Cursor querySearchDocuments(@NonNull String rootId,
                                       @Nullable String[] projection, @NonNull Bundle queryArgs)
            throws FileNotFoundException {

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            String query = queryArgs.getString(QUERY_ARG_DISPLAY_NAME, "");

            LogUtils.error(TAG, queryArgs.toString());
            List<String> mimeTypes = new ArrayList<>();

            String[] mimes = queryArgs.getStringArray(QUERY_ARG_MIME_TYPES);
            if (mimes != null) {
                for (String mimeType : mimes) {
                    if (mimeType.endsWith("/*")) {
                        mimeTypes.add(mimeType.replace("/*", ""));
                    } else {
                        mimeTypes.add(mimeType);
                    }
                }
            }

            return queryDocuments(rootId, query, projection, mimeTypes);
        } else {
            return super.querySearchDocuments(rootId, projection, queryArgs);
        }
    }

    @Override
    public Cursor querySearchDocuments(String rootId, String query, String[] projection) {
        LogUtils.info(TAG, "querySearchDocuments : " + query + " " + rootId);
        return queryDocuments(rootId, query, projection, new ArrayList<>());
    }

    private boolean isMimeType(@NonNull Thread thread, @NonNull List<String> mimeTypes) {

        if (mimeTypes.isEmpty()) {
            return true;
        }

        String mimeType = thread.getMimeType();
        if (mimeTypes.contains(mimeType)) {
            return true;
        }
        for (String mime : mimeTypes) {
            if (mimeType.startsWith(mime)) {
                return true;
            }
        }

        return false;

    }

    public Cursor queryDocuments(String rootId, String query,
                                 String[] projection, List<String> mimeTypes) {
        final MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));

        if (Objects.equals(BuildConfig.DOCUMENTS_AUTHORITY, rootId)) {
            List<Thread> entries = threads.getThreadsByQuery(query);
            for (Thread thread : entries) {
                if (isMimeType(thread, mimeTypes)) {
                    includeFile(result, thread);
                }
            }
        }
        return result;
    }


    @Override
    public Cursor queryDocument(String docId, String[] projection) throws FileNotFoundException {

        LogUtils.info(TAG, "queryDocument : " + docId);
        MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));

        try {
            long idx = Long.parseLong(docId);


            if (idx == 0) {

                int flags = 0;
                flags |= Document.FLAG_DIR_PREFERS_LAST_MODIFIED;
                flags |= Document.FLAG_DIR_PREFERS_GRID;
                flags |= Document.FLAG_DIR_SUPPORTS_CREATE;
                flags |= Document.FLAG_SUPPORTS_WRITE;
                //flags |= Document.FLAG_SUPPORTS_DELETE;
                //flags |= Document.FLAG_SUPPORTS_RENAME;
                //flags |= Document.FLAG_SUPPORTS_REMOVE;
                //flags |= Document.FLAG_SUPPORTS_MOVE;

                MatrixCursor.RowBuilder row = result.newRow();
                row.add(Document.COLUMN_DOCUMENT_ID, docId);
                row.add(Document.COLUMN_DISPLAY_NAME, rootDir);
                row.add(Document.COLUMN_SIZE, null);
                row.add(Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR);
                row.add(Document.COLUMN_LAST_MODIFIED, new Date());
                row.add(Document.COLUMN_FLAGS, flags);


            } else {
                Thread file = threads.getThreadByIdx(idx);
                if (file == null) {
                    throw new FileNotFoundException();
                }
                includeFile(result, file);
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
            throw new FileNotFoundException("" + throwable.getLocalizedMessage());
        }

        return result;

    }

    @Override
    public String getDocumentType(String documentId) throws FileNotFoundException {

        LogUtils.info(TAG, "getDocumentType : " + documentId);
        try {
            long idx = Long.parseLong(documentId);
            if (idx == 0) {
                return Document.MIME_TYPE_DIR;
            } else {
                Thread file = threads.getThreadByIdx(idx);
                if (file == null) {
                    throw new FileNotFoundException();
                }
                return file.getMimeType();
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
            throw new FileNotFoundException("" + throwable.getLocalizedMessage());
        }
    }

    private void includeFile(MatrixCursor result, Thread file) {
        int flags = Document.FLAG_SUPPORTS_DELETE;
        flags |= Document.FLAG_SUPPORTS_RENAME;
        flags |= Document.FLAG_SUPPORTS_COPY;
        flags |= Document.FLAG_SUPPORTS_MOVE;
        flags |= Document.FLAG_SUPPORTS_REMOVE;

        final String displayName = file.getName();
        final String mimeType = file.getMimeType();


        if (!file.hasContent()) {
            flags |= Document.FLAG_SUPPORTS_WRITE;
        }

        if (!file.isSeeding()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                flags |= Document.FLAG_PARTIAL;
            }
        }

        if (file.isDir()) {
            flags |= Document.FLAG_DIR_PREFERS_LAST_MODIFIED;
            flags |= Document.FLAG_DIR_PREFERS_GRID;
            flags |= Document.FLAG_DIR_SUPPORTS_CREATE;
        }

        final MatrixCursor.RowBuilder row = result.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, "" + file.getIdx());
        row.add(Document.COLUMN_DISPLAY_NAME, displayName);
        row.add(Document.COLUMN_SIZE, file.getSize());
        if (file.isDir()) {
            row.add(Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR);
        } else {
            row.add(Document.COLUMN_MIME_TYPE, mimeType);
        }
        row.add(Document.COLUMN_LAST_MODIFIED, file.getLastModified());
        row.add(Document.COLUMN_FLAGS, flags);

    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder) throws FileNotFoundException {

        try {
            long idx = Long.parseLong(parentDocumentId);

            List<Thread> entries = threads.getVisibleChildren(idx);

            final MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));

            for (Thread file : entries) {
                includeFile(result, file);
            }
            return result;
        } catch (Throwable e) {
            throw new FileNotFoundException("" + e.getLocalizedMessage());
        }
    }


    @Override
    public String createDocument(String parentDocumentId, String type, String displayName)
            throws FileNotFoundException {
        try {
            long parent = Long.parseLong(parentDocumentId);
            boolean seeding = false;
            boolean init = true;
            String content = null;
            if (Objects.equals(type, MimeType.DIR_MIME_TYPE)) {
                seeding = true;
                init = false;
                content = ipfs.createEmptyDir();
            }
            long idx = docs.createDocument(parent, type, content, null, displayName,
                    0L, seeding, init);
            if (content != null) {
                docs.finishDocument(idx, true);
            }
            return String.valueOf(idx);
        } catch (Throwable throwable) {
            throw new FileNotFoundException("" + throwable.getLocalizedMessage());
        }
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode,
                                             @Nullable CancellationSignal signal) throws FileNotFoundException {

        LogUtils.info(TAG, "openDocument : " + documentId);


        try {
            long idx = Long.parseLong(documentId);


            final boolean isWrite = (mode.indexOf('w') != -1);
            if (isWrite) {

                final int accessMode = ParcelFileDescriptor.parseMode(mode);
                Handler handler = new Handler(getContext().getMainLooper());
                File temp = FileProvider.getFile(getContext(), idx);
                return ParcelFileDescriptor.open(temp, accessMode, handler,
                        e -> {
                            String cid = ipfs.storeFile(temp);
                            Objects.requireNonNull(cid);

                            long size = temp.length();
                            threads.setThreadSize(idx, size);
                            threads.setThreadDone(idx, cid);

                            docs.finishDocument(idx, true);

                        });
            } else {

                Thread file = threads.getThreadByIdx(idx);
                Objects.requireNonNull(file);

                String cid = file.getContent();
                Objects.requireNonNull(cid);

                return ParcelFileDescriptor.open(
                        FileProvider.getFile(Objects.requireNonNull(getContext()), cid, idx),
                        ParcelFileDescriptor.MODE_READ_ONLY);

            }
        } catch (Throwable throwable) {
            throw new FileNotFoundException("" + throwable.getLocalizedMessage());
        }

    }

    @Override
    public boolean onCreate() {
        Context context = getContext();
        Objects.requireNonNull(context);
        appName = context.getString(R.string.app_name);
        rootDir = context.getString(R.string.ipfs);
        InitApplication.runUpdatesIfNecessary(context);
        threads = THREADS.getInstance(context);
        ipfs = IPFS.getInstance(context);
        docs = DOCS.getInstance(context);
        InitApplication.syncData(context);
        return true;
    }


}