package threads.server.ipfs;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.ipfs.Closeable;
import io.ipfs.LogUtils;
import io.ipfs.cid.Cid;
import io.ipfs.utils.Deleter;
import io.ipfs.utils.LinkCloseable;
import io.ipfs.utils.ReaderStream;
import io.ipfs.utils.Stream;
import ipns.pb.IpnsProtos;
import lite.Listener;
import lite.Loader;
import lite.Node;
import lite.Peer;
import lite.PeerInfo;
import lite.Provider;
import lite.ResolveInfo;
import threads.server.Settings;
import threads.server.core.Content;
import threads.server.core.blocks.BLOCKS;
import threads.server.core.blocks.Block;
import threads.server.core.events.EVENTS;

public class IPFS implements Listener {

    private static final String PREF_KEY = "prefKey";
    private static final String PID_KEY = "pidKey";
    private static final String PRIVATE_NETWORK_KEY = "privateNetworkKey";
    private static final String PRIVATE_SHARING_KEY = "privateSharingKey";
    private static final String SWARM_KEY = "swarmKey";
    private static final String SWARM_PORT_KEY = "swarmPortKey";
    private static final String PUBLIC_KEY = "publicKey";
    private static final String PRIVATE_KEY = "privateKey";
    private static final String TAG = IPFS.class.getSimpleName();
    private static IPFS INSTANCE = null;
    private final EVENTS events;
    private final Node node;
    private final Object locker = new Object();
    private final HashSet<String> swarm = new HashSet<>();
    private final BLOCKS blocks;

    private long seeding = 0L;
    private long leeching = 0L;
    private Pusher pusher;

    @NonNull
    private Reachable reachable = Reachable.UNKNOWN;

    private IPFS(@NonNull Context context) throws Exception {

        events = EVENTS.getInstance(context);
        blocks = BLOCKS.getInstance(context);


        String peerID = getPeerID(context);

        boolean init = peerID == null;

        node = new Node(this);

        if (init) {
            node.identity();

            setPeerID(context, node.getPeerID());
            setPublicKey(context, node.getPublicKey());
            setPrivateKey(context, node.getPrivateKey());
        } else {
            node.setPeerID(peerID);
            node.setPrivateKey(IPFS.getPrivateKey(context));
            node.setPublicKey(IPFS.getPublicKey(context));
        }

        String swarmKey = getSwarmKey(context);
        if (!swarmKey.isEmpty()) {
            node.setSwarmKey(swarmKey.getBytes());
            node.setEnablePrivateNetwork(isPrivateNetworkEnabled(context));
        }

        node.setAgent(Settings.AGENT);
        node.setPushing(false);
        node.setPort(IPFS.getSwarmPort(context));

        node.setConcurrency(15);
        node.setGracePeriod(Settings.GRACE_PERIOD);
        node.setHighWater(Settings.HIGH_WATER);
        node.setLowWater(Settings.LOW_WATER);
        node.setResponsive(200);

    }

    public static int getSwarmPort(@NonNull Context context) {

        SharedPreferences sharedPref = context.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE);
        return sharedPref.getInt(SWARM_PORT_KEY, 5001);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static long copy(InputStream source, OutputStream sink) throws IOException {
        long nread = 0L;
        byte[] buf = new byte[4096];
        int n;
        while ((n = source.read(buf)) > 0) {
            sink.write(buf, 0, n);
            nread += n;
        }
        return nread;
    }

    public static void copy(@NonNull InputStream source, @NonNull OutputStream sink, @NonNull ReaderProgress progress) throws IOException {
        long nread = 0L;
        byte[] buf = new byte[4096];
        int remember = 0;
        int n;
        while ((n = source.read(buf)) > 0) {
            sink.write(buf, 0, n);
            nread += n;

            if (progress.doProgress()) {
                if (progress.getSize() > 0) {
                    int percent = (int) ((nread * 100.0f) / progress.getSize());
                    if (remember < percent) {
                        remember = percent;
                        progress.setProgress(percent);
                    }
                }
            }
        }
    }


    private static void setPublicKey(@NonNull Context context, @NonNull String key) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(PUBLIC_KEY, key);
        editor.apply();
    }

    private static void setPrivateKey(@NonNull Context context, @NonNull String key) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(PRIVATE_KEY, key);
        editor.apply();
    }

    @NonNull
    public static String getSwarmKey(@NonNull Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        return Objects.requireNonNull(sharedPref.getString(SWARM_KEY, ""));

    }

    public static void setSwarmKey(@NonNull Context context, @NonNull String key) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(SWARM_KEY, key);
        editor.apply();
    }

    @NonNull
    private static String getPublicKey(@NonNull Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        return Objects.requireNonNull(sharedPref.getString(PUBLIC_KEY, ""));

    }

    @NonNull
    private static String getPrivateKey(@NonNull Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        return Objects.requireNonNull(sharedPref.getString(PRIVATE_KEY, ""));

    }

    private static void setPeerID(@NonNull Context context, @NonNull String peerID) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(PID_KEY, peerID);
        editor.apply();
    }

    @Nullable
    public static String getPeerID(@NonNull Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        return sharedPref.getString(PID_KEY, null);
    }

    public static void setPrivateNetworkEnabled(@NonNull Context context, boolean privateNetwork) {

        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(PRIVATE_NETWORK_KEY, privateNetwork);
        editor.apply();
    }

    public static void setPrivateSharingEnabled(@NonNull Context context, boolean privateSharing) {

        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(PRIVATE_SHARING_KEY, privateSharing);
        editor.apply();
    }

    public static boolean isPrivateNetworkEnabled(@NonNull Context context) {

        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        return sharedPref.getBoolean(PRIVATE_NETWORK_KEY, false);
    }

    public static boolean isPrivateSharingEnabled(@NonNull Context context) {

        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        return sharedPref.getBoolean(PRIVATE_SHARING_KEY, false);
    }

    @NonNull
    public static IPFS getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (IPFS.class) {
                if (INSTANCE == null) {
                    try {
                        INSTANCE = new IPFS(context);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return INSTANCE;
    }

    private static int nextFreePort() {
        int port = ThreadLocalRandom.current().nextInt(4001, 65535);
        while (true) {
            if (isLocalPortFree(port)) {
                return port;
            } else {
                port = ThreadLocalRandom.current().nextInt(4001, 65535);
            }
        }
    }

    private static boolean isLocalPortFree(int port) {
        try {
            new ServerSocket(port).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }


    public void setPusher(@Nullable Pusher pusher) {
        node.setPushing(pusher != null);
        this.pusher = pusher;
    }


    public boolean notify(@NonNull String pid, @NonNull String cid) {
        if (!isDaemonRunning()) {
            return false;
        }
        try {
            synchronized (pid.intern()) {
                return node.push(pid, cid.getBytes()) == cid.length();
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return false;
    }


    @NonNull
    public String decodeName(@NonNull String name) {
        try {
            return node.decodeName(name);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return "";
    }


    @Override
    public void push(String cid, String pid) {
        try {
            // CID and PID are both valid objects (code done in go)
            Objects.requireNonNull(cid);
            Objects.requireNonNull(pid);
            if (pusher != null) {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.submit(() -> pusher.push(pid, cid));
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

    }

    @Override
    public void blockPut(String key, byte[] bytes) {
        blocks.insertBlock(key, bytes);
    }


    public void swarmReduce(@NonNull String pid) {
        swarm.remove(pid);
    }

    @Override
    public boolean shouldConnect(String pid) {
        return swarm.contains(pid);
    }

    @Override
    public boolean shouldGate(String pid) {
        return !swarm.contains(pid);
    }

    @Override
    public long blockSize(String key) {
        //LogUtils.error(TAG, "size " + key);
        Block block = blocks.getBlock(key);
        if (block != null) {
            return block.getSize();
        }
        return -1;
    }


    public void swarmEnhance(@NonNull String pid) {
        swarm.add(pid);
    }

    public void swarmEnhance(@NonNull List<String> users) {
        swarm.addAll(users);
    }

    @NonNull
    public String getPeerID() {
        return node.getPeerID();
    }

    @NonNull
    public String getHost() {
        return base32(node.getPeerID());
    }

    public void checkSwarmKey(@NonNull String key) throws Exception {
        node.checkSwarmKey(key);
    }

    public void shutdown() {
        try {
            setPusher(null);
            node.setShutdown(true);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public void startDaemon(boolean privateSharing) {
        if (!node.getRunning()) {
            synchronized (locker) {
                if (!node.getRunning()) {
                    AtomicBoolean failure = new AtomicBoolean(false);
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    AtomicReference<String> exception = new AtomicReference<>("");
                    executor.submit(() -> {
                        try {

                            long port = node.getPort();
                            if (!isLocalPortFree((int) port)) {
                                node.setPort(nextFreePort());
                            }
                            LogUtils.error(TAG, "start daemon...");
                            node.daemon(privateSharing);
                            LogUtils.error(TAG, "stop daemon...");
                        } catch (Throwable e) {
                            failure.set(true);
                            exception.set("" + e.getLocalizedMessage());
                            LogUtils.error(TAG, e);
                        }
                    });

                    while (!node.getRunning()) {
                        if (failure.get()) {
                            break;
                        }
                    }
                    if (failure.get()) {
                        throw new RuntimeException(exception.get());
                    }
                }
            }
        }
    }

    @NonNull
    public Reachable getReachable() {
        return reachable;
    }

    private void setReachable(@NonNull Reachable reachable) {
        this.reachable = reachable;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> events.reachable(this.reachable.name()));
    }

    public void bootstrap() {
        if (isDaemonRunning()) {
            if (numSwarmPeers() < Settings.MIN_PEERS) {
                try {
                    Pair<List<String>, List<String>> result = DnsAddrResolver.getBootstrap();

                    List<String> bootstrap = result.first;
                    List<Callable<Boolean>> tasks = new ArrayList<>();
                    ExecutorService executor = Executors.newFixedThreadPool(bootstrap.size());
                    for (String address : bootstrap) {
                        tasks.add(() -> swarmConnect(address, null, Settings.TIMEOUT_BOOTSTRAP));
                    }

                    List<Future<Boolean>> futures = executor.invokeAll(tasks, Settings.TIMEOUT_BOOTSTRAP, TimeUnit.SECONDS);
                    for (Future<Boolean> future : futures) {
                        LogUtils.info(TAG, "\nBootstrap done " + future.isDone());
                    }

                    List<String> second = result.second;
                    tasks.clear();
                    if (!second.isEmpty()) {
                        executor = Executors.newFixedThreadPool(second.size());
                        for (String address : second) {
                            tasks.add(() -> swarmConnect(address, null, Settings.TIMEOUT_BOOTSTRAP));
                        }
                        futures.clear();
                        futures = executor.invokeAll(tasks, Settings.TIMEOUT_BOOTSTRAP, TimeUnit.SECONDS);
                        for (Future<Boolean> future : futures) {
                            LogUtils.info(TAG, "\nConnect done " + future.isDone());
                        }
                    }
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }
            }
        }
    }


    public void dhtFindProviders(@NonNull String cid, @NonNull Provider provider, int numProvs,
                                 @NonNull Closeable closeable) throws ClosedException {
        if (!isDaemonRunning()) {
            return;
        }
        try {
            node.dhtFindProvs(cid, provider, numProvs, closeable::isClosed);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        if (closeable.isClosed()) {
            throw new ClosedException();
        }
    }

    public List<String> dhtFindProviders(@NonNull String cid, int numProvs, int timeout) {

        if (!isDaemonRunning()) {
            return Collections.emptyList();
        }

        List<String> providers = new ArrayList<>();

        try {

            node.dhtFindProvsTimeout(cid, providers::add
                    , numProvs, timeout);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        return providers;
    }

    public void dhtPublish(@NonNull String cid, @NonNull lite.Closeable closable) {

        if (!isDaemonRunning()) {
            return;
        }

        try {
            node.dhtProvide(cid, closable);
        } catch (Throwable ignore) {
        }
    }

    @Nullable
    public PeerInfo pidInfo(@NonNull String pid) {

        if (!isDaemonRunning()) {
            return null;
        }
        try {
            return node.pidInfo(pid);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

        return null;

    }

    @Nullable
    public PeerInfo id() {
        try {
            return node.id();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public boolean swarmConnect(@NonNull String multiAddress, @Nullable String pid,
                                @NonNull Closeable closeable) throws ClosedException {
        if (!isDaemonRunning()) {
            return false;
        }

        if (pid != null) {
            swarmEnhance(pid);
        }
        try {
            return node.swarmConnect(multiAddress, closeable::isClosed);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, multiAddress + " connection failed");
        }
        if (closeable.isClosed()) {
            throw new ClosedException();
        }
        return false;
    }

    public boolean swarmConnect(@NonNull String multiAddress, @Nullable String pid, int timeout) {
        if (!isDaemonRunning()) {
            return false;
        }
        try {
            if (pid != null) {
                swarmEnhance(pid);
            }
            return node.swarmConnectTimeout(multiAddress, timeout);
        } catch (Throwable e) {
            LogUtils.error(TAG, multiAddress + " " + e.getLocalizedMessage());
        }
        return false;
    }

    public boolean isPrivateNetwork() {
        return node.getPrivateNetwork();
    }

    public boolean isConnected(@NonNull String pid) {

        if (!isDaemonRunning()) {
            return false;
        }
        try {
            return node.isConnected(pid);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        return false;
    }

    @Nullable
    public Peer swarmPeer(@NonNull String pid) {
        if (!isDaemonRunning()) {
            return null;
        }
        try {
            return node.swarmPeer(pid);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        return null;
    }

    @NonNull
    public List<String> swarmPeers() {
        if (!isDaemonRunning()) {
            return Collections.emptyList();
        }
        return swarm_peers();
    }

    @NonNull
    private List<String> swarm_peers() {

        List<String> peers = new ArrayList<>();
        if (isDaemonRunning()) {
            try {
                node.swarmPeers(peers::add);
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }
        }
        return peers;
    }

    public void publishName(@NonNull String cid, @NonNull Closeable closeable, int sequence) throws ClosedException {
        if (!isDaemonRunning()) {
            return;
        }
        try {
            node.publishName(cid, closeable::isClosed, sequence);
        } catch (Throwable ignore) {
        }

        if (closeable.isClosed()) {
            throw new ClosedException();
        }
    }

    @NonNull
    public String base32(@NonNull String pid) {
        try {
            return node.base32(pid);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    public String base58(@NonNull String pid) {
        try {
            return node.base58(pid);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }


    @Nullable
    public ResolvedName resolveName(@NonNull String name, long last,
                                    @NonNull Closeable closeable) throws ClosedException {
        if (!isDaemonRunning()) {
            return null;
        }


        long time = System.currentTimeMillis();

        AtomicReference<ResolvedName> resolvedName = new AtomicReference<>(null);
        try {
            AtomicLong timeout = new AtomicLong(System.currentTimeMillis() + Settings.RESOLVE_MAX_TIME);
            AtomicBoolean abort = new AtomicBoolean(false);
            node.resolveName(new ResolveInfo() {
                @Override
                public boolean close() {
                    return (timeout.get() < System.currentTimeMillis()) || abort.get() || closeable.isClosed();
                }

                private void setName(@NonNull String hash, long sequence) {
                    resolvedName.set(new ResolvedName(sequence,
                            hash.replaceFirst(Content.IPFS_PATH, "")));
                }

                @Override
                public void resolved(byte[] data) {

                    try {
                        IpnsProtos.IpnsEntry entry = IpnsProtos.IpnsEntry.parseFrom(data);
                        Objects.requireNonNull(entry);
                        String hash = entry.getValue().toStringUtf8();
                        long seq = entry.getSequence();

                        LogUtils.error(TAG, "IpnsEntry : " + seq + " " + hash + " " +
                                (System.currentTimeMillis() - time));

                        if (seq < last) {
                            abort.set(true);
                            return; // newest value already available
                        }
                        if (!abort.get()) {
                            if (hash.startsWith(Content.IPFS_PATH)) {
                                timeout.set(System.currentTimeMillis() + Settings.RESOLVE_TIMEOUT);
                                setName(hash, seq);
                            } else {
                                LogUtils.error(TAG, "invalid hash " + hash);
                            }
                        }
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }

                }
            }, name, false, 8);

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        LogUtils.error(TAG, "Finished resolve name " + name + " " +
                (System.currentTimeMillis() - time));

        if (closeable.isClosed()) {
            throw new ClosedException();
        }
        return resolvedName.get();
    }


    public void rm(@NonNull String cid, boolean recursively) {

        try {
            Deleter.rm(blocks, cid, recursively);
            // node.rm(cid, recursively);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    public long getSwarmPort() {
        return node.getPort();
    }

    @Nullable
    public String storeData(@NonNull byte[] data) {

        try (InputStream inputStream = new ByteArrayInputStream(data)) {
            return storeInputStream(inputStream);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        return null;
    }

    @Nullable
    public String storeText(@NonNull String content) {

        try (InputStream inputStream = new ByteArrayInputStream(content.getBytes())) {
            return storeInputStream(inputStream);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        return null;
    }

    @Nullable
    public String rmLinkFromDir(String dir, String name) {
        try {
            return Stream.RemoveLinkFromDir(blocks, () -> false, dir, name);
            //return node.removeLinkFromDir(dir, name);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return null;
    }

    @Nullable
    public String addLinkToDir(@NonNull String dir, @NonNull String name, @NonNull String link) {
        try {
            return Stream.AddLinkToDir(blocks, () -> false, dir, name, link);
            //return node.addLinkToDir(dir, name, link);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        return null;
    }

    @Nullable
    public String createEmptyDir() {
        try {
            return Stream.CreateEmptyDir(blocks, () -> false);
            //return node.createEmptyDir();
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        return null;
    }


    @NonNull
    public String resolve(@NonNull String root, @NonNull List<String> path, @NonNull Closeable closeable) throws ClosedException {

        String resultPath = Content.IPFS_PATH + root;
        for (String name : path) {
            resultPath = resultPath.concat("/").concat(name);
        }

        return resolve(resultPath, closeable);

    }

    public String resolve(@NonNull String path, @NonNull Closeable closeable) throws ClosedException {
        if (!isDaemonRunning()) {
            return "";
        }
        String result = "";

        try {
            result = node.resolve(path, closeable::isClosed);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        if (closeable.isClosed()) {
            throw new ClosedException();
        }
        return result;
    }

    public boolean resolve(@NonNull String cid, @NonNull String name,
                           @NonNull Closeable closeable) throws ClosedException {
        String res = resolve("/" + Content.IPFS + "/" + cid + "/" + name, closeable);
        return !res.isEmpty();
    }


    public boolean isDir(@NonNull String cid, @NonNull Closeable closeable) throws ClosedException {

        if (!isDaemonRunning()) {
            return false;
        }
        boolean result;
        try {
            result = Stream.IsDir(blocks, () -> false, cid);
            ;
            //result = node.isDir(cid, closeable::isClosed);

        } catch (Throwable e) {
            result = false;
        }
        if (closeable.isClosed()) {
            throw new ClosedException();
        }
        return result;
    }


    public long getSize(@NonNull String cid, @NonNull Closeable closeable) throws ClosedException {
        List<LinkInfo> links = ls(cid, closeable);
        int size = -1;
        if (links != null) {
            for (LinkInfo info : links) {
                size += info.getSize();
            }
        }
        return size;
    }


    @Nullable
    public List<Link> links(@NonNull String cid, @NonNull Closeable closeable) throws ClosedException {

        LogUtils.info(TAG, "Lookup CID : " + cid);

        List<Link> links = lss(cid, closeable);
        if (links == null) {
            LogUtils.info(TAG, "no links");
            return null;
        }

        List<Link> result = new ArrayList<>();
        for (Link link : links) {
            LogUtils.info(TAG, "Link : " + link.toString());
            if (!link.getName().isEmpty()) {
                result.add(link);
            }
        }
        return result;
    }

    @Nullable
    public List<LinkInfo> getLinks(@NonNull String cid, @NonNull Closeable closeable) throws ClosedException {

        LogUtils.info(TAG, "Lookup CID : " + cid);

        List<LinkInfo> links = ls(cid, closeable);
        if (links == null) {
            LogUtils.info(TAG, "no links");
            return null;
        }

        List<LinkInfo> result = new ArrayList<>();
        for (LinkInfo link : links) {
            LogUtils.info(TAG, "Link : " + link.toString());
            if (!link.getName().isEmpty()) {
                result.add(link);
            }
        }
        return result;
    }


    @Nullable
    private List<Link> lss(@NonNull String cid, @NonNull Closeable closeable) throws ClosedException {
        if (!isDaemonRunning()) {
            return Collections.emptyList();
        }
        List<Link> infoList = new ArrayList<>();
        try {
            Stream.Ls(blocks, new LinkCloseable() {
                @Override
                public void info(@NonNull String name, @NonNull String hash, long size, int type) {
                    Link info = Link.create(name, hash);
                    infoList.add(info);
                    LogUtils.error(TAG, info.toString());
                }

                @Override
                public boolean isClosed() {
                    return closeable.isClosed();
                }
            }, cid, false);

        } catch (Throwable e) {
            if (closeable.isClosed()) {
                throw new ClosedException();
            }
            return null;
        }
        if (closeable.isClosed()) {
            throw new ClosedException();
        }
        return infoList;
    }


    @Nullable
    List<LinkInfo> ls(@NonNull String cid, @NonNull Closeable closeable) throws ClosedException {
        if (!isDaemonRunning()) {
            return Collections.emptyList();
        }
        List<LinkInfo> infoList = new ArrayList<>();
        try {
            Stream.Ls(blocks, new LinkCloseable() {

                @Override
                public boolean isClosed() {
                    return closeable.isClosed();
                }

                @Override
                public void info(@NonNull String name, @NonNull String hash, long size, int type) {
                    LinkInfo info = LinkInfo.create(name, hash, size, type);
                    infoList.add(info);
                }
            }, cid, true);

        } catch (Throwable e) {
            if (closeable.isClosed()) {
                throw new ClosedException();
            }
            return null;
        }
        if (closeable.isClosed()) {
            throw new ClosedException();
        }
        return infoList;
    }

    @Nullable
    public String storeFile(@NonNull File target) {
        try {
            return node.addFile(target.getAbsolutePath());
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        return null;
    }

    @NonNull
    public io.ipfs.utils.Reader getReader(@NonNull String cid) {
        return io.ipfs.utils.Reader.getReader(blocks, cid);
        // return node.getReader(cid);
    }

    private boolean loadToOutputStream(@NonNull OutputStream outputStream, @NonNull String cid,
                                       @NonNull Progress progress) {

        try (InputStream inputStream = getLoaderStream(cid, progress)) {
            IPFS.copy(inputStream, outputStream);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
            return false;
        }
        return true;

    }

    private void getToOutputStream(@NonNull OutputStream outputStream, @NonNull String cid) throws Exception {
        try (InputStream inputStream = getInputStream(cid)) {
            IPFS.copy(inputStream, outputStream);
        }
    }

    public boolean loadToFile(@NonNull File file, @NonNull String cid, @NonNull Progress progress) {
        if (!isDaemonRunning()) {
            return false;
        }

        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            return loadToOutputStream(outputStream, cid, progress);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
            return false;
        }
    }

    public void storeToOutputStream(@NonNull OutputStream os, @NonNull Progress progress,
                                    @NonNull String cid, long size) throws Exception {

        long totalRead = 0L;
        int remember = 0;

        try (io.ipfs.utils.Reader reader = getReader(cid)) {
            byte[] buf = reader.loadNextData();
            while (buf != null && buf.length > 0) {

                if (progress.isClosed()) {
                    throw new RuntimeException("Progress closed");
                }

                // calculate progress
                totalRead += buf.length;
                if (progress.doProgress()) {
                    if (size > 0) {
                        int percent = (int) ((totalRead * 100.0f) / size);
                        if (remember < percent) {
                            remember = percent;
                            progress.setProgress(percent);
                        }
                    }
                }

                os.write(buf, 0, buf.length);

                buf = reader.loadNextData();

            }
        }

    }

    public void storeToOutputStream(@NonNull OutputStream os, @NonNull String cid, int blockSize) throws Exception {

        try (io.ipfs.utils.Reader reader = getReader(cid)) {
            byte[] buf = reader.loadNextData();
            while (buf != null && buf.length > 0) {

                os.write(buf, 0, buf.length);
                buf = reader.loadNextData();
            }
        }

    }

    @NonNull
    private Loader getLoader(@NonNull String cid, @NonNull Closeable closeable) throws Exception {
        return node.getLoader(cid, closeable::isClosed);
    }

    @NonNull
    public InputStream getLoaderStream(@NonNull String cid, @NonNull Closeable closeable) throws Exception {

        Loader loader = getLoader(cid, closeable);

        return new CloseableInputStream(loader, closeable);

    }

    @NonNull
    public InputStream getLoaderStream(@NonNull String cid, @NonNull Progress progress) throws Exception {

        Loader loader = getLoader(cid, progress);

        return new LoaderInputStream(loader, progress);

    }

    public void storeToFile(@NonNull File file, @NonNull String cid, int blockSize) throws Exception {

        try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            storeToOutputStream(fileOutputStream, cid, blockSize);
        }

    }

    @Nullable
    public String storeInputStream(@NonNull InputStream inputStream,
                                   @NonNull Progress progress, long size) {


        String res = "";
        try {
            res = Stream.Write(blocks, ()-> false, new ReaderStream(inputStream, progress, size));
            //res = node.stream(new WriterStream(inputStream, progress, size));
        } catch (Throwable e) {
            if (!progress.isClosed()) {
                LogUtils.error(TAG, e);
            }
        }

        if (!res.isEmpty()) {
            return res;
        }
        return null;
    }

    @Nullable
    public String storeInputStream(@NonNull InputStream inputStream) {

        return storeInputStream(inputStream, new Progress() {
            @Override
            public boolean isClosed() {
                return false;
            }

            @Override
            public void setProgress(int progress) {
            }

            @Override
            public boolean doProgress() {
                return false;
            }


        }, 0);
    }

    @Nullable
    public String getText(@NonNull String cid) {

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            getToOutputStream(outputStream, cid);
            return new String(outputStream.toByteArray());
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
            return null;
        }
    }

    @Nullable
    public byte[] getData(@NonNull String cid) {

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            getToOutputStream(outputStream, cid);
            return outputStream.toByteArray();
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
            return null;
        }
    }

    @Nullable
    public byte[] loadData(@NonNull String cid, @NonNull Progress progress) {
        if (!isDaemonRunning()) {
            return null;
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            boolean success = loadToOutputStream(outputStream, cid, progress);
            if (success) {
                return outputStream.toByteArray();
            } else {
                return null;
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
            return null;
        }

    }

    @Override
    public void blockDelete(String key) {
        // LogUtils.error(TAG, "del " + key);

        blocks.deleteBlock(key);

    }

    @Override
    public void error(String message) {
        if (message != null && !message.isEmpty()) {
            LogUtils.error(TAG, "error " + message);
        }
    }

    @Override
    public byte[] blockGet(String key) {
        //LogUtils.error(TAG, "get " + key);
        Block block = blocks.getBlock(key);
        if (block != null) {
            return block.getData();
        }
        return null;
    }

    @Override
    public boolean blockHas(String key) {
        //LogUtils.error(TAG, "has " + key);
        return blocks.hasBlock(key);
    }

    @Override
    public void info(String message) {
        if (message != null && !message.isEmpty()) {
            LogUtils.info(TAG, "" + message);
        }
    }

    @Override
    public void reachablePrivate() {
        setReachable(Reachable.PRIVATE);
    }

    @Override
    public void reachablePublic() {
        setReachable(Reachable.PUBLIC);
    }

    @Override
    public void reachableUnknown() {
        setReachable(Reachable.UNKNOWN);
    }

    @Override
    public void verbose(String s) {
        LogUtils.verbose(TAG, "" + s);
    }

    @NonNull
    public InputStream getInputStream(@NonNull String cid) throws Exception {
        io.ipfs.utils.Reader reader = getReader(cid);
        return new io.ipfs.utils.ReaderInputStream(reader);

    }

    public boolean isDaemonRunning() {
        return node.getRunning();
    }

    public boolean isValidCID(@NonNull String cid) {
        try {
            return !Cid.Decode(cid).String().isEmpty();
            //this.node.cidCheck(cid);
            //return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public long getSeeding() {
        return seeding;
    }

    @Override
    public void seeding(long amount) {
        seeding += amount;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            events.seeding(seeding);
            //LogUtils.info(TAG, "Seeding Amount : " + amount);
        });
    }

    @Override
    public void leeching(long amount) {
        leeching += amount;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            events.leeching(leeching);
            //LogUtils.info(TAG, "Leeching Amount : " + amount);
        });
    }


    public long numSwarmPeers() {
        if (!isDaemonRunning()) {
            return 0;
        }
        return node.numSwarmPeers();
    }

    public long getLeeching() {
        return leeching;
    }

    public interface Pusher {
        void push(@NonNull String pid, @NonNull String cid);
    }

    public static class ResolvedName {
        private final long sequence;
        @NonNull
        private final String hash;

        public ResolvedName(long sequence, @NonNull String hash) {
            this.sequence = sequence;
            this.hash = hash;
        }

        public long getSequence() {
            return sequence;
        }

        @NonNull
        public String getHash() {
            return hash;
        }
    }

    private static class LoaderInputStream extends InputStream implements AutoCloseable {
        private final Loader mLoader;
        private final Progress mProgress;
        private final long size;
        private int position = 0;
        private byte[] data = null;
        private int remember = 0;
        private long totalRead = 0L;

        LoaderInputStream(@NonNull Loader loader, @NonNull Progress progress) {
            mLoader = loader;
            mProgress = progress;
            size = mLoader.getSize();
        }

        @Override
        public int available() {
            long size = mLoader.getSize();
            return (int) size;
        }

        @Override
        public int read() throws IOException {


            try {
                if (data == null) {
                    invalidate();
                    preLoad();
                }
                if (data == null) {
                    return -1;
                }
                if (position < data.length) {
                    byte value = data[position];
                    position++;
                    return (value & 0xff);
                } else {
                    invalidate();
                    if (preLoad()) {
                        byte value = data[position];
                        position++;
                        return (value & 0xff);
                    } else {
                        return -1;
                    }
                }

            } catch (Throwable e) {
                throw new IOException(e);
            }
        }


        private void invalidate() {
            position = 0;
            data = null;
        }


        private boolean preLoad() throws Exception {

            mLoader.load(4096, mProgress::isClosed);
            int read = (int) mLoader.getRead();
            if (read > 0) {
                data = new byte[read];
                byte[] values = mLoader.getData();
                System.arraycopy(values, 0, data, 0, read);

                totalRead += read;
                if (mProgress.doProgress()) {
                    if (size > 0) {
                        int percent = (int) ((totalRead * 100.0f) / size);
                        if (remember < percent) {
                            remember = percent;
                            mProgress.setProgress(percent);
                        }
                    }
                }
                return true;
            }
            return false;
        }

        public void close() {
            try {
                mLoader.close();
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }
        }
    }


    private static class CloseableInputStream extends InputStream implements AutoCloseable {
        private final Loader loader;
        private final Closeable closeable;
        private int position = 0;
        private byte[] data = null;

        CloseableInputStream(@NonNull Loader loader, @NonNull Closeable closeable) {
            this.loader = loader;
            this.closeable = closeable;
        }

        @Override
        public int available() {
            long size = loader.getSize();
            return (int) size;
        }

        @Override
        public int read() throws IOException {


            try {
                if (data == null) {
                    invalidate();
                    preLoad();
                }
                if (data == null) {
                    return -1;
                }
                if (position < data.length) {
                    byte value = data[position];
                    position++;
                    return (value & 0xff);
                } else {
                    invalidate();
                    if (preLoad()) {
                        byte value = data[position];
                        position++;
                        return (value & 0xff);
                    } else {
                        return -1;
                    }
                }

            } catch (Throwable e) {
                throw new IOException(e);
            }
        }


        private void invalidate() {
            position = 0;
            data = null;
        }


        private boolean preLoad() throws Exception {
            loader.load(4096, closeable::isClosed);
            int read = (int) loader.getRead();
            if (read > 0) {
                data = new byte[read];
                byte[] values = loader.getData();
                System.arraycopy(values, 0, data, 0, read);
                return true;
            }
            return false;
        }

        public void close() {
            try {
                loader.close();
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }
        }
    }


    private static class WriterStream implements lite.WriterStream {
        private final InputStream mInputStream;
        private final Progress mProgress;
        private final int SIZE = 262144;
        private final long size;
        private final byte[] data = new byte[SIZE];
        private int progress = 0;
        private long totalRead = 0;


        WriterStream(@NonNull InputStream inputStream, @NonNull Progress progress, long size) {
            this.mInputStream = inputStream;
            this.mProgress = progress;
            this.size = size;
        }


        @Override
        public byte[] data() {
            return data;
        }

        @Override
        public long read() throws Exception {


            if (mProgress.isClosed()) {
                throw new Exception("progress closed");
            }


            int read = mInputStream.read(data);

            totalRead += read;

            if (mProgress.doProgress()) {
                if (size > 0) {
                    int percent = (int) ((totalRead * 100.0f) / size);
                    if (progress < percent) {
                        progress = percent;
                        mProgress.setProgress(percent);
                    }
                }
            }
            return read;
        }

        @Override
        public boolean close() {
            return mProgress.isClosed();

        }
    }
}
