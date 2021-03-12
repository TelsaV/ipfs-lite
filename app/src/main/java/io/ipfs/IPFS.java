package io.ipfs;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
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

import io.ipfs.blockstore.Blockstore;
import io.ipfs.cid.Cid;
import io.ipfs.exchange.Interface;
import io.ipfs.routing.ContentRouting;
import io.ipfs.utils.Deleter;
import io.ipfs.utils.Link;
import io.ipfs.utils.LinkCloseable;
import io.ipfs.utils.Progress;
import io.ipfs.utils.ProgressStream;
import io.ipfs.utils.Reachable;
import io.ipfs.utils.ReaderProgress;
import io.ipfs.utils.ReaderStream;
import io.ipfs.utils.Resolver;
import io.ipfs.utils.Stream;
import ipns.pb.IpnsProtos;
import lite.Listener;
import lite.Node;
import lite.Peer;
import lite.PeerInfo;
import lite.Providers;
import lite.ResolveInfo;
import threads.server.Settings;
import threads.server.core.Content;
import threads.server.core.blocks.BLOCKS;
import threads.server.core.blocks.Block;
import threads.server.core.events.EVENTS;

public class IPFS implements Listener, Interface, ContentRouting {

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


    public void dhtFindProviders(@NonNull String cid, int numProviders,
                                 @NonNull io.ipfs.routing.Providers providers) throws ClosedException {
        if (!isDaemonRunning()) {
            return;
        }

        if (numProviders < 1) {
            throw new RuntimeException("number of providers must be greater than 0");
        }

        try {
            node.dhtFindProviders(cid, numProviders, new Providers() {
                @Override
                public boolean close() {
                    return providers.isClosed();
                }

                @Override
                public void peer(@NonNull String id) {
                    providers.Peer(id);
                }
            });
        } catch (Throwable ignore) {
        }
        if (providers.isClosed()) {
            throw new ClosedException();
        }
    }


    public void dhtPublish(@NonNull Closeable closable, @NonNull String cid) throws ClosedException {

        if (!isDaemonRunning()) {
            return;
        }

        try {
            node.dhtProvide(cid, closable::isClosed);
        } catch (Throwable ignore) {
        }
        if (closable.isClosed()) {
            throw new ClosedException();
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

    public void publishName(@NonNull String cid, @NonNull Closeable closeable, int sequence)
            throws ClosedException {
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
            Deleter.rm(() -> false, blocks, cid, recursively);
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
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return null;
    }

    @Nullable
    public String addLinkToDir(@NonNull String dir, @NonNull String name, @NonNull String link) {
        try {
            return Stream.AddLinkToDir(blocks, () -> false, dir, name, link);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        return null;
    }

    @Nullable
    public String createEmptyDir() {
        try {
            return Stream.CreateEmptyDir(blocks);
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
            result = Resolver.resolve(closeable, blocks, this, path);
        } catch (Throwable ignore) {
            // common use case not resolve a a path
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
            Blockstore blockstore = Blockstore.NewBlockstore(blocks);
            result = Stream.IsDir(closeable, blockstore, this, cid);

        } catch (Throwable e) {
            result = false;
        }
        if (closeable.isClosed()) {
            throw new ClosedException();
        }
        return result;
    }


    public long getSize(@NonNull String cid, @NonNull Closeable closeable) throws ClosedException {
        List<Link> links = ls(cid, closeable, true);
        int size = -1;
        if (links != null) {
            for (Link info : links) {
                size += info.getSize();
            }
        }
        return size;
    }


    @Nullable
    public List<Link> links(@NonNull String cid, @NonNull Closeable closeable) throws ClosedException {

        LogUtils.info(TAG, "Lookup CID : " + cid);

        List<Link> links = ls(cid, closeable, false);
        if (links == null) {
            LogUtils.info(TAG, "no links");
            return null;
        }

        List<Link> result = new ArrayList<>();
        for (Link link : links) {
            LogUtils.info(TAG, "Link : " + link.toString());
            if (!link.isRaw()) {
                result.add(link);
            }
        }
        return result;
    }

    @Nullable
    public List<Link> getLinks(@NonNull String cid, @NonNull Closeable closeable) throws ClosedException {

        List<Link> links = ls(cid, closeable, true);
        if (links == null) {
            LogUtils.info(TAG, "no links");
            return null;
        }

        List<Link> result = new ArrayList<>();
        for (Link link : links) {

            if (!link.getName().isEmpty()) {
                result.add(link);
            }
        }
        return result;
    }



    @Nullable
    public List<Link> ls(@NonNull String cid, @NonNull Closeable closeable,
                         boolean resolveChildren) throws ClosedException {
        if (!isDaemonRunning()) {
            return Collections.emptyList();
        }
        List<Link> infoList = new ArrayList<>();
        try {
            Blockstore blockstore = Blockstore.NewBlockstore(blocks);
            Stream.Ls(new LinkCloseable() {

                @Override
                public boolean isClosed() {
                    return closeable.isClosed();
                }

                @Override
                public void info(@NonNull Link link) {
                    infoList.add(link);
                    LogUtils.error(TAG, link.toString());
                }
            }, blockstore, this, cid, resolveChildren);

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
        try(FileInputStream inputStream = new FileInputStream(target)) {
            return storeInputStream(inputStream);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return null;
    }

    @NonNull
    public io.ipfs.utils.Reader getReader(@NonNull String cid, @NonNull Closeable closeable) {
        Blockstore blockstore = Blockstore.NewBlockstore(blocks);
        return io.ipfs.utils.Reader.getReader(closeable, blockstore, this, cid);
    }

    private void getToOutputStream(@NonNull OutputStream outputStream, @NonNull String cid,
                                   @NonNull Closeable closeable) throws Exception {
        try (InputStream inputStream = getInputStream(cid, closeable)) {
            IPFS.copy(inputStream, outputStream);
        }
    }

    public void storeToFile(@NonNull File file, @NonNull String cid, @NonNull Progress progress) {
        if (!isDaemonRunning()) {
            return;
        }
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            storeToOutputStream(outputStream, progress, cid);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    public void storeToOutputStream(@NonNull OutputStream os, @NonNull Progress progress,
                                    @NonNull String cid) throws Exception {

        long totalRead = 0L;
        int remember = 0;

        try (io.ipfs.utils.Reader reader = getReader(cid, progress)) {

            long size = reader.getSize();
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

    public void storeToOutputStream(@NonNull OutputStream os, @NonNull String cid, @NonNull Closeable closeable) throws Exception {

        try (io.ipfs.utils.Reader reader = getReader(cid, closeable)) {
            byte[] buf = reader.loadNextData();
            while (buf != null && buf.length > 0) {

                os.write(buf, 0, buf.length);
                buf = reader.loadNextData();
            }
        }

    }

    @NonNull
    public InputStream getLoaderStream(@NonNull String cid, @NonNull Closeable closeable) {
        io.ipfs.utils.Reader loader = getReader(cid, closeable);
        return new ReaderStream(loader);
    }

    @NonNull
    public InputStream getLoaderStream(@NonNull String cid, @NonNull Progress progress) {
        io.ipfs.utils.Reader loader = getReader(cid, progress);
        return new ProgressStream(loader, progress);

    }

    public void storeToFile(@NonNull File file, @NonNull String cid, @NonNull Closeable closeable) throws Exception {

        try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            storeToOutputStream(fileOutputStream, cid, closeable);
        }
    }

    @Nullable
    public String storeInputStream(@NonNull InputStream inputStream,
                                   @NonNull Progress progress, long size) {


        String res = "";
        try {
            res = Stream.Write(blocks, new io.ipfs.utils.WriterStream(inputStream, progress, size));
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
    public String getText(@NonNull String cid, @NonNull Closeable closeable) {

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            getToOutputStream(outputStream, cid, closeable);
            return new String(outputStream.toByteArray());
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
            return null;
        }
    }

    @Nullable
    public byte[] getData(@NonNull String cid, @NonNull Closeable closeable) {

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            getToOutputStream(outputStream, cid, closeable);
            return outputStream.toByteArray();
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
            return null;
        }
    }

    @Nullable
    public byte[] loadData(@NonNull String cid, @NonNull Progress progress) throws Exception {
        if (!isDaemonRunning()) {
            return null;
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            storeToOutputStream(outputStream, progress, cid);
            return outputStream.toByteArray();
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
    public InputStream getInputStream(@NonNull String cid, @NonNull Closeable closeable) {
        io.ipfs.utils.Reader reader = getReader(cid, closeable);
        return new ReaderStream(reader);

    }

    public boolean isDaemonRunning() {
        return node.getRunning();
    }

    public boolean isValidCID(@NonNull String cid) {
        try {
            return !Cid.Decode(cid).String().isEmpty();
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

    @Override
    public io.ipfs.blocks.Block getBlock(@NonNull Closeable closeable, @NonNull Cid cid) {

        if(!isDaemonRunning()){
            return null;
        }

        try {

            String result = node.getBlock(closeable::isClosed, cid.String());
            if(!result.isEmpty()){
                Blockstore bs = Blockstore.NewBlockstore(blocks);
                return bs.Get(Cid.Decode(result));
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        if (closeable.isClosed()) {
            throw new RuntimeException(new ClosedException());
        }
        return null;
    }

    @Override
    public void FindProvidersAsync(@NonNull io.ipfs.routing.Providers providers, @NonNull Cid cid, int number) {

        try {
            dhtFindProviders(cid.String(), number, providers);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    @Override
    public void Provide(@NonNull Closeable closeable, @NonNull Cid cid) {
        try {
            dhtPublish(closeable, cid.String());
        } catch (Throwable throwable){
            throw new RuntimeException(throwable);
        }

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

}
