package threads.server.core.threads;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import threads.server.ipfs.CID;

public class ThreadsAPI {

    private final ThreadsDatabase threadsDatabase;

    ThreadsAPI(@NonNull ThreadsDatabase threadsDatabase) {

        this.threadsDatabase = threadsDatabase;
    }


    @NonNull
    public ThreadsDatabase getThreadsDatabase() {
        return threadsDatabase;
    }


    public void setThreadsDeleting(long... idxs) {
        for (long idx : idxs) {
            getThreadsDatabase().threadDao().setDeleting(idx);
        }
    }


    public void resetThreadsDeleting(long... idxs) {
        for (long idx : idxs) {
            getThreadsDatabase().threadDao().resetDeleting(idx);
        }
    }

    public void setThreadLeaching(long idx) {
        getThreadsDatabase().threadDao().setLeaching(idx);
    }

    public void resetThreadLeaching(long idx) {
        getThreadsDatabase().threadDao().resetLeaching(idx);
    }

    public boolean isThreadLeaching(long idx) {
        return getThreadsDatabase().threadDao().isLeaching(idx);
    }


    public void setThreadDone(long idx) {
        getThreadsDatabase().threadDao().setDone(idx);
    }

    public void setThreadDone(long idx, @NonNull CID cid) {
        getThreadsDatabase().threadDao().setDone(idx, cid);
    }


    public List<Thread> getAncestors(long idx) {
        List<Thread> path = new ArrayList<>();
        if (idx > 0) {
            Thread thread = getThreadByIdx(idx);
            if (thread != null) {
                path.addAll(getAncestors(thread.getParent()));
                path.add(thread);
            }
        }
        return path;
    }


    public void setMimeType(@NonNull Thread thread, @NonNull String mimeType) {

        getThreadsDatabase().threadDao().setMimeType(thread.getIdx(), mimeType);
    }

    public void setThreadMimeType(long idx, @NonNull String mimeType) {

        getThreadsDatabase().threadDao().setMimeType(idx, mimeType);
    }

    @NonNull
    public Thread createThread(int location, long parent) {
        return Thread.createThread(location, parent);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isReferenced(int location, @NonNull CID cid) {
        return getThreadsDatabase().threadDao().references(location, cid) > 0;
    }

    public List<Thread> getNewestThreads(int location, int limit) {
        return getThreadsDatabase().threadDao().getNewestThreads(location, limit);
    }

    public void removeThread(long idx) {
        getThreadsDatabase().threadDao().removeThread(idx);
    }

    public void removeThreads(@NonNull List<Thread> threads) {
        getThreadsDatabase().threadDao().removeThreads(threads);
    }

    public long storeThread(@NonNull Thread thread) {
        return getThreadsDatabase().threadDao().insertThread(thread);
    }

    public void setThreadName(long idx, @NonNull String name) {
        getThreadsDatabase().threadDao().setName(idx, name);
    }

    public void setThreadContent(long idx, @NonNull CID cid) {
        getThreadsDatabase().threadDao().setContent(idx, cid);
    }

    public long getThreadParent(long idx) {
        return getThreadsDatabase().threadDao().getParent(idx);
    }

    public String getThreadName(long idx) {
        return getThreadsDatabase().threadDao().getName(idx);
    }


    @NonNull
    public List<Thread> getPins(int location) {
        return getChildren(location, 0L);
    }

    @NonNull
    public List<Thread> getChildren(int location, long parent) {
        return getThreadsDatabase().threadDao().getChildren(location, parent);
    }

    public long getChildrenSummarySize(int location, long parent) {
        return getThreadsDatabase().threadDao().getChildrenSummarySize(location, parent);
    }

    @NonNull
    public List<Thread> getVisibleChildren(int location, long thread) {
        return getThreadsDatabase().threadDao().getVisibleChildren(location, thread);
    }


    @Nullable
    public Thread getThreadByIdx(long idx) {
        return getThreadsDatabase().threadDao().getThreadByIdx(idx);
    }


    @Nullable
    public CID getThreadContent(long idx) {
        return getThreadsDatabase().threadDao().getContent(idx);
    }


    public long getThreadPosition(long idx) {
        return getThreadsDatabase().threadDao().getPosition(idx);
    }

    @NonNull
    public List<Thread> getThreadsByContentAndParent(int location, @NonNull CID cid, long thread) {

        return getThreadsDatabase().threadDao().getThreadsByContentAndParent(location, cid, thread);
    }


    @NonNull
    public List<Thread> getThreadsByNameAndParent(int location, @NonNull String name, long thread) {

        return getThreadsDatabase().threadDao().getThreadsByNameAndParent(location, name, thread);
    }


    public List<Thread> getThreadsByQuery(int location, String query) {

        String searchQuery = query.trim();
        if (!searchQuery.startsWith("%")) {
            searchQuery = "%" + searchQuery;
        }
        if (!searchQuery.endsWith("%")) {
            searchQuery = searchQuery + "%";
        }
        return getThreadsDatabase().threadDao().getThreadsByQuery(location, searchQuery);
    }

    public void setThreadProgress(long idx, int progress) {

        getThreadsDatabase().threadDao().setProgress(idx, progress);
    }


    public void setThreadPosition(long idx, long position) {
        getThreadsDatabase().threadDao().setPosition(idx, position);
    }


    public void setThreadSize(long idx, long size) {
        getThreadsDatabase().threadDao().setSize(idx, size);
    }

    public void setThreadError(long idx) {
        getThreadsDatabase().threadDao().setError(idx);
    }

    public void setThreadWork(long idx, @NonNull UUID id) {
        getThreadsDatabase().threadDao().setWork(idx, id.toString());
    }

    public void resetThreadWork(long idx) {
        getThreadsDatabase().threadDao().resetWork(idx);
    }

    public boolean isThreadInit(long idx) {
        return getThreadsDatabase().threadDao().isInit(idx);
    }

    public void resetThreadInit(long idx) {
        getThreadsDatabase().threadDao().resetInit(idx);
    }

    public List<Thread> getDeletedThreads(int location) {
        return getThreadsDatabase().threadDao().getDeletedThreads(location, System.currentTimeMillis());
    }

    public void setThreadLastModified(long idx, long time) {
        getThreadsDatabase().threadDao().setLastModified(idx, time);
    }

    public List<Thread> getSelfAndAllChildren(int location, @NonNull Thread thread) {

        List<Thread> children = new ArrayList<>();
        children.add(thread);
        List<Thread> entries = getChildren(location, thread.getIdx());
        for (Thread entry : entries) {
            children.addAll(getSelfAndAllChildren(location, entry));
        }
        return children;
    }

    public void setThreadUri(long idx, @NonNull String uri) {
        getThreadsDatabase().threadDao().setUri(idx, uri);
    }

    public void setThreadParent(long idx, long targetIdx) {
        getThreadsDatabase().threadDao().setParent(idx, targetIdx);
    }


    @Nullable
    public UUID getThreadWork(long idx) {
        String work = getThreadsDatabase().threadDao().getWork(idx);
        if (work != null) {
            return UUID.fromString(work);
        }
        return null;
    }


    public void setThreadSortOrder(long idx, @NonNull SortOrder sortOrder) {
        getThreadsDatabase().threadDao().setSortOrder(idx, sortOrder);
    }

    @Nullable
    public SortOrder getThreadSortOrder(long idx) {
        return getThreadsDatabase().threadDao().getSortOrder(idx);
    }

}
