package threads.server.core;

import android.content.Context;
import android.net.Uri;
import android.util.Pair;
import android.webkit.WebResourceResponse;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.WorkManager;

import org.apache.commons.io.FilenameUtils;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import threads.LogUtils;
import threads.server.core.page.PAGES;
import threads.server.core.page.Page;
import threads.server.core.page.Resolver;
import threads.server.core.peers.PEERS;
import threads.server.core.peers.User;
import threads.server.core.threads.THREADS;
import threads.server.core.threads.Thread;
import threads.server.ipfs.Closeable;
import threads.server.ipfs.DnsAddrResolver;
import threads.server.ipfs.IPFS;
import threads.server.ipfs.Link;
import threads.server.ipfs.LinkInfo;
import threads.server.magic.ContentInfo;
import threads.server.magic.ContentInfoUtil;
import threads.server.services.MimeTypeService;
import threads.server.utils.MimeType;

public class DOCS {

    private static final String TAG = DOCS.class.getSimpleName();
    private static final String INDEX_HTML = "index.html";
    private static DOCS INSTANCE = null;
    private final IPFS ipfs;
    private final THREADS threads;
    private final PAGES pages;
    private final PEERS peers;
    private final String host;
    private final ContentInfoUtil util;


    private DOCS(@NonNull Context context) {
        ipfs = IPFS.getInstance(context);
        threads = THREADS.getInstance(context);
        pages = PAGES.getInstance(context);
        peers = PEERS.getInstance(context);
        host = ipfs.getHost();
        util = ContentInfoUtil.getInstance(context);

        initPinsPage();
    }

    public static DOCS getInstance(@NonNull Context context) {

        if (INSTANCE == null) {
            synchronized (DOCS.class) {
                if (INSTANCE == null) {
                    INSTANCE = new DOCS(context);
                }
            }
        }
        return INSTANCE;
    }


    @NonNull
    public String resolveName(@NonNull Uri uri,
                              @NonNull String name,
                              @NonNull Closeable closeable) throws ResolveNameException {


        if (Objects.equals(getHost(), name)) {
            String local = getLocalName();
            if (local != null) {
                return local;
            }
        }

        Resolver resolver = pages.getResolver(name);
        if (resolver != null) {
            return resolver.getContent();
        }


        long sequence = 0L;
        String ipns = null;
        String pid = ipfs.base58(name);
        User user = peers.getUserByPid(pid);
        if (user != null) {
            sequence = user.getSequence();
            ipns = user.getIpns();
        }

        IPFS.ResolvedName resolvedName = ipfs.resolveName(name, sequence, closeable);
        if (resolvedName == null) {

            if (ipns != null) {
                pages.storeResolver(name, ipns);
                return ipns;
            }

            throw new ResolveNameException(uri.toString());
        } else {
            pages.storeResolver(name, resolvedName.getHash());
            peers.setUserIpns(pid, resolvedName.getHash(), resolvedName.getSequence());
            return resolvedName.getHash();
        }
    }

    public String getHost() {
        return host;
    }

    @NonNull
    public Uri getPinsPageUri() {
        return Uri.parse(Content.IPNS + "://" + getHost());
    }

    @Nullable
    public String getLocalName() {
        return pages.getPageContent(getHost());
    }

    public void deleteDocument(long idx) {

        try {
            removeFromParentDocument(idx);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

        threads.setThreadsDeleting(idx);

        try {
            updateParentSize(idx);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

        deleteThread(idx);

    }

    public void deleteDocuments(@Nullable Context context, long... idxs) {

        if (context != null) {
            for (long idx : idxs) {
                UUID uuid = threads.getThreadWork(idx);
                if (uuid != null) {
                    WorkManager.getInstance(context).cancelWorkById(uuid);
                }
            }
        }

        for (long idx : idxs) {
            try {
                removeFromParentDocument(idx);
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        }


        threads.setThreadsDeleting(idxs);
        for (long idx : idxs) {
            try {
                updateParentSize(idx);
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        }


        for (long idx : idxs) {
            deleteThread(idx);
        }


        setPinsPageOutdated();
    }

    private void deleteThread(long idx) {
        long start = System.currentTimeMillis();

        try {
            Thread thread = threads.getThreadByIdx(idx);
            if (thread != null) {
                if (thread.isDeleting()) {

                    List<Thread> entries = threads.getSelfAndAllChildren(thread);
                    threads.removeThreads(entries);

                    for (Thread entry : entries) {
                        String cid = entry.getContent();
                        if (cid != null) {
                            if (!threads.isReferenced(cid)) {
                                ipfs.rm(cid, !entry.isDir());
                            }
                        }
                    }

                }
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        } finally {
            LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
        }

    }

    public void moveDocument(long idx, long sourceIdx, long targetIdx) {

        Thread thread = threads.getThreadByIdx(idx);
        Objects.requireNonNull(thread);
        Thread source = threads.getThreadByIdx(sourceIdx);
        Objects.requireNonNull(source);
        Thread target = threads.getThreadByIdx(targetIdx);
        Objects.requireNonNull(target);

        if (!Objects.equals(thread.getParent(), sourceIdx)) {
            throw new RuntimeException("Parent ");
        }

        try {
            removeFromParentDocument(idx);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }


        String name = getUniqueName(source.getName(), targetIdx);
        threads.setThreadName(idx, name);

        threads.setThreadParent(idx, targetIdx);

        updateParentDocument(idx);

        try {
            updateDirectorySize(sourceIdx);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }


        try {
            updateDirectorySize(targetIdx);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }


    public String getUniqueName(@NonNull String name, long parent) {
        return getName(name, parent, 0);
    }

    private String getName(@NonNull String name, long parent, int index) {
        String searchName = name;
        if (index > 0) {
            try {
                String base = FilenameUtils.getBaseName(name);
                String extension = FilenameUtils.getExtension(name);
                if (extension.isEmpty()) {
                    searchName = searchName.concat(" (" + index + ")");
                } else {
                    String end = " (" + index + ")";
                    if (base.endsWith(end)) {
                        String realBase = base.substring(0, base.length() - end.length());
                        searchName = realBase.concat(" (" + index + ")").concat(".").concat(extension);
                    } else {
                        searchName = base.concat(" (" + index + ")").concat(".").concat(extension);
                    }
                }
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
                searchName = searchName.concat(" (" + index + ")"); // just backup
            }
        }
        List<Thread> names = threads.getThreadsByNameAndParent(
                searchName, parent);
        if (!names.isEmpty()) {
            return getName(name, parent, ++index);
        }
        return searchName;
    }


    public void finishDocument(long idx, boolean pinsOutdated) {
        updateParentDocument(idx);
        updateParentSize(idx);
        if (pinsOutdated) {
            setPinsPageOutdated();
        }
    }

    private void updateParentSize(long idx) {
        long parent = threads.getThreadParent(idx);
        updateDirectorySize(parent);
    }

    private void updateDirectorySize(long parent) {
        if (parent > 0) {
            long parentSize = threads.getChildrenSummarySize(parent);
            threads.setThreadSize(parent, parentSize);
            updateParentSize(parent);
        }
    }

    private void updateParentDocument(long idx, @NonNull String oldName) {
        long parent = threads.getThreadParent(idx);

        if (parent > 0) {
            String cid = threads.getThreadContent(idx);
            Objects.requireNonNull(cid);
            String name = threads.getThreadName(idx);
            String dirCid = threads.getThreadContent(parent);
            Objects.requireNonNull(dirCid);
            if (!oldName.isEmpty()) {
                dirCid = ipfs.rmLinkFromDir(dirCid, oldName);
            } else {
                dirCid = ipfs.rmLinkFromDir(dirCid, name);
            }
            Objects.requireNonNull(dirCid);
            String newDir = ipfs.addLinkToDir(dirCid, name, cid);
            Objects.requireNonNull(newDir);
            threads.setThreadContent(parent, newDir);
            threads.setThreadLastModified(parent, System.currentTimeMillis());
            updateParentDocument(parent, "");
        }
    }


    private void removeFromParentDocument(long idx) {

        Thread child = threads.getThreadByIdx(idx);
        Objects.requireNonNull(child);

        long parent = child.getParent();
        if (parent > 0) {
            String name = child.getName();
            String dirCid = threads.getThreadContent(parent);
            Objects.requireNonNull(dirCid);
            String newDir = ipfs.rmLinkFromDir(dirCid, name);
            Objects.requireNonNull(newDir);
            threads.setThreadContent(parent, newDir);
            threads.setThreadLastModified(parent, System.currentTimeMillis());
            updateParentDocument(parent);
        }
    }


    private void updateParentDocument(long idx) {
        try {
            long parent = threads.getThreadParent(idx);
            updateParentDocument(idx, parent);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    private void updateParentDocument(long idx, long parent) {
        try {
            if (parent > 0) {
                String cid = threads.getThreadContent(idx);
                Objects.requireNonNull(cid);
                String name = threads.getThreadName(idx);
                String dirCid = threads.getThreadContent(parent);
                Objects.requireNonNull(dirCid);
                String newDir = ipfs.addLinkToDir(dirCid, name, cid);
                Objects.requireNonNull(newDir);
                threads.setThreadContent(parent, newDir);
                threads.setThreadLastModified(parent, System.currentTimeMillis());
                updateParentDocument(parent);
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public long copyDocument(long parent, long idx) {
        Thread source = threads.getThreadByIdx(idx);
        Objects.requireNonNull(source);

        if (parent > 0) {
            Thread target = threads.getThreadByIdx(parent);
            Objects.requireNonNull(target);
        }

        String name = getUniqueName(source.getName(), parent);

        Thread thread = threads.createThread(parent);
        thread.setInit(source.isInit());
        thread.setName(name);
        thread.setContent(source.getContent());
        thread.setLeaching(source.isLeaching());
        thread.setError(source.isError());
        thread.setDeleting(source.isDeleting());
        thread.setMimeType(source.getMimeType());
        thread.setSize(source.getSize());
        thread.setUri(source.getUri());
        thread.setSeeding(source.isSeeding());
        thread.setPosition(0L);
        thread.setProgress(0);
        thread.setWork(null);


        long result = threads.storeThread(thread);

        finishDocument(result, true);
        return result;
    }

    private String checkMimeType(@Nullable String mimeType, @NonNull String name) {
        boolean evalDisplayName = false;
        if (mimeType == null) {
            evalDisplayName = true;
        } else {
            if (mimeType.isEmpty()) {
                evalDisplayName = true;
            } else {
                if (Objects.equals(mimeType, MimeType.OCTET_MIME_TYPE)) {
                    evalDisplayName = true;
                }
            }
        }
        if (evalDisplayName) {
            mimeType = MimeTypeService.getMimeType(name);
        }
        return mimeType;
    }

    public long createDocument(long parent, @Nullable String type, @Nullable String content,
                               @Nullable Uri uri, String displayName, long size,
                               boolean seeding, boolean init) {
        String mimeType = checkMimeType(type, displayName);
        Thread thread = threads.createThread(parent);
        if (Objects.equals(mimeType, MimeType.DIR_MIME_TYPE)) {
            thread.setMimeType(MimeType.DIR_MIME_TYPE);
        } else {
            thread.setMimeType(mimeType);
        }

        String name = getUniqueName(displayName, parent);
        thread.setContent(content);
        thread.setInit(init);
        thread.setName(name);
        thread.setSize(size);
        thread.setSeeding(seeding);
        if (uri != null) {
            thread.setUri(uri.toString());
        }
        return threads.storeThread(thread);
    }

    public void renameDocument(long idx, String displayName) {
        String oldName = threads.getThreadName(idx);
        if (!Objects.equals(oldName, displayName)) {
            threads.setThreadName(idx, displayName);

            setPinsPageOutdated();

            updateParentDocument(idx, oldName);
        }

    }


    private void initPinsPage() {
        try {
            Page page = getPinsPage();
            if (page == null) {
                page = pages.createPage(getHost());
                String dir = ipfs.createEmptyDir();
                Objects.requireNonNull(dir);
                page.setOutdated(false);
                page.setContent(dir);
                pages.storePage(page);
            } else {
                updatePinsPage();
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }


    public void setPinsPageOutdated() {
        pages.setPageOutdated(getHost());
    }

    public boolean isPinsPageOutdated() {
        return pages.isPageOutdated(getHost());
    }

    public void updatePinsPage() {
        try {

            Page page = getPinsPage();
            Objects.requireNonNull(page);

            String dir = ipfs.createEmptyDir();
            Objects.requireNonNull(dir);

            List<Thread> pins = threads.getPins();

            boolean isEmpty = pins.isEmpty();
            if (!isEmpty) {
                for (Thread pin : pins) {
                    String link = pin.getContent();
                    Objects.requireNonNull(link);
                    String name = pin.getName();
                    dir = ipfs.addLinkToDir(dir, name, link);
                    Objects.requireNonNull(dir);
                }
            }
            page.setOutdated(false);
            page.setContent(dir);
            pages.storePage(page);

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }


    @NonNull
    private String getMimeType(@NonNull String doc, @NonNull Closeable closeable) {

        if (ipfs.isEmptyDir(doc) || ipfs.isDir(doc, closeable)) {
            return MimeType.DIR_MIME_TYPE;
        }

        String mimeType = MimeType.OCTET_MIME_TYPE;
        if (!closeable.isClosed()) {
            try (InputStream in = ipfs.getLoaderStream(doc, closeable, 5000)) {
                ContentInfo info = util.findMatch(in);

                if (info != null) {
                    mimeType = info.getMimeType();
                }

            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }
        }
        return mimeType;
    }

    @NonNull
    public String getPath(@NonNull Thread thread) {
        StringBuilder builder = new StringBuilder();
        List<Thread> ancestors = threads.getAncestors(thread.getIdx());
        builder.append(Content.IPFS + "://");
        boolean first = true;
        for (Thread ancestor : ancestors) {
            if (first) {
                String cid = ancestor.getContent();
                Objects.requireNonNull(cid);
                builder.append(cid);
                first = false;
            } else {
                builder.append("/").append(ancestor.getName());
            }
        }

        return builder.toString();

    }

    public String generateErrorHtml(@NonNull Throwable throwable) {

        return "<html>" +
                "<head>" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=2, user-scalable=yes\">" +
                "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">" +
                "<title>" + "Error" + "</title>" + "</head><body><div <div style=\"padding: 16px; word-break:break-word; background-color: #696969; color: white;\">" +
                throwable.getMessage() +
                "</div></body></html>";
    }



    public String generateDirectoryHtml(@NonNull Uri uri, @NonNull String root, List<String> paths, @Nullable List<LinkInfo> links) {
        String title = root;

        if (!paths.isEmpty()) {
            title = paths.get(paths.size() - 1);
        }


        StringBuilder answer = new StringBuilder("<html>" +
                "<head>" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=2, user-scalable=yes\">" +
                "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">" +
                "<title>" + title + "</title>");

        answer.append("</head><body>");


        answer.append("<div style=\"padding: 16px; word-break:break-word; background-color: #696969; color: white;\">Index of ").append(uri.toString()).append("</div>");

        if (links != null) {
            if (!links.isEmpty()) {
                answer.append("<form><table  width=\"100%\" style=\"border-spacing: 4px;\">");
                for (LinkInfo linkInfo : links) {

                    String mimeType = MimeType.DIR_MIME_TYPE;
                    if (!linkInfo.isDirectory()) {
                        mimeType = MimeTypeService.getMimeType(linkInfo.getName());
                    }
                    String linkUri = uri + "/" + linkInfo.getName();

                    answer.append("<tr>");

                    answer.append("<td>");
                    answer.append(MimeTypeService.getSvgResource(mimeType));
                    answer.append("</td>");

                    answer.append("<td width=\"100%\" style=\"word-break:break-word\">");
                    answer.append("<a href=\"");
                    answer.append(linkUri);
                    answer.append("\">");
                    answer.append(linkInfo.getName());
                    answer.append("</a>");
                    answer.append("</td>");

                    answer.append("<td>");
                    if (!linkInfo.isDirectory()) {
                        answer.append(getFileSize(linkInfo.getSize()));
                    }
                    answer.append("</td>");
                    answer.append("<td align=\"center\">");
                    String text = "<button style=\"float:none!important;display:inline;\" name=\"download\" value=\"1\" formenctype=\"text/plain\" formmethod=\"get\" type=\"submit\" formaction=\"" +
                            linkUri + "\">" + MimeTypeService.getSvgDownload() + "</button>";
                    answer.append(text);
                    answer.append("</td>");
                    answer.append("</tr>");
                }
                answer.append("</table></form>");
            }

        }
        answer.append("</body></html>");


        return answer.toString();
    }

    private String getFileSize(long size) {

        String fileSize;

        if (size < 1000) {
            fileSize = String.valueOf(size);
            return fileSize.concat(" B");
        } else if (size < 1000 * 1000) {
            fileSize = String.valueOf((double) (size / 1000));
            return fileSize.concat(" KB");
        } else {
            fileSize = String.valueOf((double) (size / (1000 * 1000)));
            return fileSize.concat(" MB");
        }
    }


    @Nullable
    public Link getLink(@NonNull Uri uri, @NonNull String root, @NonNull Closeable progress) {
        List<String> paths = uri.getPathSegments();
        String host = uri.getHost();
        Objects.requireNonNull(host);
        return ipfs.link(root, paths, progress);
    }


    @Nullable
    public String getRoot(@NonNull Uri uri, @NonNull Closeable closeable) throws ResolveNameException, InvalidNameException {
        String host = uri.getHost();
        Objects.requireNonNull(host);

        String root;
        if (Objects.equals(uri.getScheme(), Content.IPNS)) {

            if (!ipfs.isValidCID(host)) {
                throw new InvalidNameException(uri.toString());
            }
            root = resolveName(uri, host, closeable);

        } else {
            if (!ipfs.isValidCID(host)) {
                throw new InvalidNameException(uri.toString());
            }
            root = host;
        }

        return root;

    }

    @NonNull
    private FileInfo getDataInfo(@NonNull Uri uri, @NonNull String root, @NonNull Closeable closeable) throws TimeoutException {
        String host = uri.getHost();
        Objects.requireNonNull(host);

        try {
            String mimeType = getMimeType(root, closeable);

            if (closeable.isClosed()) {
                throw new TimeoutException(uri.toString());
            }

            return new FileInfo(root, mimeType, root);
        } catch (Throwable throwable) {
            if (closeable.isClosed()) {
                throw new TimeoutException(uri.toString());
            }
            throw new RuntimeException(throwable);
        }
    }



    @NonNull
    public WebResourceResponse getResponse(@NonNull Uri uri, @NonNull String root,
                                           @NonNull List<String> paths,
                                           @NonNull Closeable closeable) throws Exception {

        if (paths.isEmpty()) {
            if (ipfs.isEmptyDir(root)) {
                String answer = generateDirectoryHtml(uri, root, paths, new ArrayList<>());
                return new WebResourceResponse(MimeType.HTML_MIME_TYPE, Content.UTF8,
                        new ByteArrayInputStream(answer.getBytes()));
            } else if (ipfs.isDir(root, closeable)) {
                List<LinkInfo> links = ipfs.getLinks(root, closeable);
                String answer = generateDirectoryHtml(uri, root, paths, links);
                return new WebResourceResponse(MimeType.HTML_MIME_TYPE, Content.UTF8,
                        new ByteArrayInputStream(answer.getBytes()));
            } else {
                String mimeType = getMimeType(root, closeable);
                if (closeable.isClosed()) {
                    throw new TimeoutException(uri.toString());
                }

                long size = ipfs.getSize(root, closeable);
                if (closeable.isClosed()) {
                    throw new TimeoutException(uri.toString());
                }
                return getContentResponse(uri, root, mimeType, size, closeable);
            }


        } else {
            String cid = ipfs.resolve(root, paths, closeable);
            if (closeable.isClosed()) {
                throw new TimeoutException(uri.toString());
            }
            Objects.requireNonNull(cid);

            if (ipfs.isEmptyDir(cid)) {
                String answer = generateDirectoryHtml(uri, root, paths, new ArrayList<>());
                return new WebResourceResponse(MimeType.HTML_MIME_TYPE, Content.UTF8,
                        new ByteArrayInputStream(answer.getBytes()));
            } else if (ipfs.isDir(cid, closeable)) {
                List<LinkInfo> links = ipfs.getLinks(cid, closeable);
                String answer = generateDirectoryHtml(uri, root, paths, links);
                return new WebResourceResponse(MimeType.HTML_MIME_TYPE, Content.UTF8,
                        new ByteArrayInputStream(answer.getBytes()));

            } else {
                String mimeType = getMimeType(uri, cid, closeable);
                if (closeable.isClosed()) {
                    throw new TimeoutException(uri.toString());
                }
                long size = ipfs.getSize(cid, closeable);
                if (closeable.isClosed()) {
                    throw new TimeoutException(uri.toString());
                }
                return getContentResponse(uri, cid, mimeType,
                        size, closeable);
            }


        }
    }

    @NonNull
    private WebResourceResponse getContentResponse(@NonNull Uri uri,
                                                   @NonNull String content,
                                                   @NonNull String mimeType, long size,
                                                   @NonNull Closeable closeable) throws TimeoutException {

        try {

            InputStream in = ipfs.getLoaderStream(content, closeable, 5000);


            Map<String, String> responseHeaders = new HashMap<>();
            if (size > 0) {
                responseHeaders.put("Content-Length", "" + size);
            }
            responseHeaders.put("Content-Type", mimeType);

            return new WebResourceResponse(mimeType, Content.UTF8, 200,
                    "OK", responseHeaders, new BufferedInputStream(in));
        } catch (Throwable throwable) {
            if (closeable.isClosed()) {
                throw new TimeoutException(uri.toString());
            }
            throw new RuntimeException(throwable);
        }


    }



    @NonNull
    private String getMimeType(@NonNull Uri uri,
                               @NonNull String element,
                               @NonNull Closeable closeable) {

        List<String> paths = uri.getPathSegments();
        if (!paths.isEmpty()) {
            String name = paths.get(paths.size() - 1);
            String mimeType = MimeTypeService.getMimeType(name);
            if (!mimeType.equals(MimeType.OCTET_MIME_TYPE)) {
                return mimeType;
            } else {
                return getMimeType(element, closeable);
            }
        } else {
            return getMimeType(element, closeable);
        }

    }


    public boolean isLocalUri(@NonNull String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            Objects.requireNonNull(host);


            if (Objects.equals(uri.getScheme(), Content.IPNS) ||
                    Objects.equals(uri.getScheme(), Content.IPNS)) {
                if (Objects.equals(host, getHost())) {
                    return true;
                }
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return false;
    }




    @NonNull
    public FileInfo getFileInfo(@NonNull Uri uri, @NonNull Closeable closeable)
            throws InvalidNameException, ResolveNameException, TimeoutException {

        String host = uri.getHost();
        Objects.requireNonNull(host);

        String root = getRoot(uri, closeable);
        Objects.requireNonNull(root);


        Link linkInfo = getLink(uri, root, closeable);
        if (linkInfo != null) {
            String filename = linkInfo.getName();
            if (ipfs.isDir(linkInfo.getContent(), closeable)) {
                return new FileInfo(filename, MimeType.DIR_MIME_TYPE, linkInfo.getContent());
            } else {

                String mimeType = getMimeType(uri, linkInfo.getContent(), closeable);

                return new FileInfo(filename, mimeType, linkInfo.getContent());
            }

        } else {
            return getDataInfo(uri, root, closeable);
        }
    }


    @NonNull
    public WebResourceResponse getResponse(@NonNull Uri uri,
                                           @NonNull Closeable closeable) throws Exception {

        String host = uri.getHost();
        Objects.requireNonNull(host);
        List<String> paths = uri.getPathSegments();

        if (!ipfs.isValidCID(host)) {
            throw new InvalidNameException(uri.toString());
        }

        String root;
        if (Objects.equals(uri.getScheme(), Content.IPNS)) {
            root = resolveName(uri, host, closeable);
        } else {
            root = host;
        }
        Objects.requireNonNull(root);

        if (closeable.isClosed()) {
            throw new TimeoutException(uri.toString());
        }

        return getResponse(uri, root, paths, closeable);

    }

    @Nullable
    public Page getPinsPage() {
        return pages.getPage(getHost());
    }

    @Nullable
    public String decodeName(@NonNull String name) {
        return ipfs.decodeName(name);
    }

    public static class FileInfo {

        @NonNull
        private final String filename;
        @NonNull
        private final String mimeType;
        @NonNull
        private final String content;


        public FileInfo(@NonNull String filename, @NonNull String mimeType, @NonNull String content) {
            this.filename = filename;
            this.mimeType = mimeType;
            this.content = content;
        }

        @NonNull
        public String getFilename() {
            return filename;
        }

        @NonNull
        public String getMimeType() {
            return mimeType;
        }

        @NonNull
        public String getContent() {
            return content;
        }

    }

    public static class TimeoutException extends Exception {

        public TimeoutException(@NonNull String name) {
            super("Timeout for " + name);
        }

    }

    public static class ResolveNameException extends Exception {


        public ResolveNameException(@NonNull String name) {
            super("Resolve name failed for " + name);
        }

    }

    public static class InvalidNameException extends Exception {


        public InvalidNameException(@NonNull String name) {
            super("Invalid name " + name);
        }

    }


    @NonNull
    public Pair<Uri,Boolean> redirectUri(@NonNull Uri uri, @NonNull Closeable closeable) {


        if (Objects.equals(uri.getScheme(), Content.IPNS) ||
                Objects.equals(uri.getScheme(), Content.IPFS)) {
            List<String> paths = uri.getPathSegments();
            String host = uri.getHost();
            Objects.requireNonNull(host);
            if (!ipfs.isValidCID(host)) {
                String cid = DnsAddrResolver.getDNSLink(host);
                if (cid == null) {
                    Uri.Builder builder = new Uri.Builder();
                    builder.scheme(Content.HTTPS)
                            .authority(host);
                    for (String path : paths) {
                        builder.appendPath(path);
                    }

                    return Pair.create(builder.build(), false);
                } else {
                    Uri.Builder builder = new Uri.Builder();
                    builder.scheme(Content.IPFS)
                            .authority(cid);
                    for (String path : paths) {
                        builder.appendPath(path);
                    }
                    return redirect(builder.build(), cid, paths, closeable);
                }

            } else {
                String root;
                try {
                    if (Objects.equals(uri.getScheme(), Content.IPNS)) {
                        root = resolveName(uri, host, closeable);
                    } else {
                        root = host;
                    }
                    Objects.requireNonNull(root);
                    return redirect(uri, root, paths, closeable);
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }
            }
        }
        return Pair.create(uri, false);
    }


    @NonNull
    public Pair<Uri, Boolean> redirect(@NonNull Uri uri, @NonNull String root,
                                       @NonNull List<String> paths, @NonNull Closeable closeable) {

        if (paths.isEmpty()) {

            if (!ipfs.isEmptyDir(root)) {
                boolean exists = ipfs.resolve(root, INDEX_HTML, closeable);
                if (exists) {
                    Uri.Builder builder = new Uri.Builder();
                    builder.scheme(uri.getScheme())
                            .authority(uri.getAuthority());
                    for (String path : paths) {
                        builder.appendPath(path);
                    }
                    builder.appendPath(INDEX_HTML);
                    return Pair.create(builder.build(), true);
                }
            }


        } else {

            String cid = ipfs.resolve(root, paths, closeable);

            if (cid != null) {
                if (!ipfs.isEmptyDir(cid)) {
                    boolean exists = ipfs.resolve(cid, INDEX_HTML, closeable);
                    if (exists) {
                        Uri.Builder builder = new Uri.Builder();
                        builder.scheme(uri.getScheme())
                                .authority(uri.getAuthority());
                        for (String path : paths) {
                            builder.appendPath(path);
                        }
                        builder.appendPath(INDEX_HTML);
                        return Pair.create(builder.build(), true);
                    }
                }
            }

        }
        return Pair.create(uri, false);
    }
}
