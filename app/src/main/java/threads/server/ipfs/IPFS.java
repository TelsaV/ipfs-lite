package threads.server.ipfs;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.Base64;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;

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
import java.util.HashMap;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import lite.DhtClose;
import lite.Listener;
import lite.Loader;
import lite.LsInfoClose;
import lite.Node;
import lite.Peer;
import lite.PeerInfo;
import lite.Reader;
import lite.ResolveInfo;
import lite.Sequence;
import threads.LogUtils;
import threads.server.core.events.EVENTS;

public class IPFS implements Listener {
    private static final String EMPTY_DIR_58 = "QmUNLLsPACCz1vLxQVkXqqLX5R1X345qqfHbsf67hvA3Nn";
    private static final String EMPTY_DIR_32 = "bafybeiczsscdsbs7ffqz55asqdf3smv6klcw3gofszvwlyarci47bgf354";
    private static final String PREF_KEY = "prefKey";
    private static final String PID_KEY = "pidKey";
    private static final String PRIVATE_NETWORK_KEY = "privateNetworkKey";
    private static final String PRIVATE_SHARING_KEY = "privateSharingKey";
    private static final String HIGH_WATER_KEY = "highWaterKey";
    private static final String LOW_WATER_KEY = "lowWaterKey";
    private static final String GRACE_PERIOD_KEY = "gracePeriodKey";
    private static final String STORAGE_DIRECTORY = "storageDirectoryKey";
    private static final String SWARM_KEY = "swarmKey";
    private static final String SWARM_PORT_KEY = "swarmPortKey";
    private static final String PUBLIC_KEY = "publicKey";
    private static final String AGENT_KEY = "agentKey";
    private static final String PRIVATE_KEY = "privateKey";
    private static final String TAG = IPFS.class.getSimpleName();
    private static IPFS INSTANCE = null;
    private final File baseDir;
    private final EVENTS events;
    private final Node node;
    private final Object locker = new Object();
    private final int location;
    private final HashSet<String> swarm = new HashSet<>();
    private final Gson gson = new Gson();
    private long seeding = 0L;
    private long leeching = 0L;
    private Pusher pusher;

    @NonNull
    private Reachable reachable = Reachable.UNKNOWN;

    private IPFS(@NonNull Context context) throws Exception {

        File dir = getExternalStorageDirectory(context);
        events = EVENTS.getInstance(context);
        if (dir == null) {
            this.baseDir = context.getFilesDir();
            this.location = 0;
        } else {
            StorageManager manager = (StorageManager)
                    context.getSystemService(Activity.STORAGE_SERVICE);
            Objects.requireNonNull(manager);
            StorageVolume volume = manager.getStorageVolume(dir);
            Objects.requireNonNull(volume);
            this.location = volume.hashCode();
            this.baseDir = dir;
        }


        String host = getPeerID(context);

        boolean init = host == null;

        node = new Node(this, this.baseDir.getAbsolutePath());

        if (init) {
            node.identity();

            setPeerID(context, node.getPeerID());
            setPublicKey(context, node.getPublicKey());
            setPrivateKey(context, node.getPrivateKey());
        } else {
            node.setPeerID(host);
            node.setPrivateKey(IPFS.getPrivateKey(context));
            node.setPublicKey(IPFS.getPublicKey(context));
        }

        /* addNoAnnounce
         "/ip4/10.0.0.0/ipcidr/8",
                "/ip4/100.64.0.0/ipcidr/10",
                "/ip4/169.254.0.0/ipcidr/16",
                "/ip4/172.16.0.0/ipcidr/12",
                "/ip4/192.0.0.0/ipcidr/24",
                "/ip4/192.0.0.0/ipcidr/29",
                "/ip4/192.0.0.8/ipcidr/32",
                "/ip4/192.0.0.170/ipcidr/32",
                "/ip4/192.0.0.171/ipcidr/32",
                "/ip4/192.0.2.0/ipcidr/24",
                "/ip4/192.168.0.0/ipcidr/16",
                "/ip4/198.18.0.0/ipcidr/15",
                "/ip4/198.51.100.0/ipcidr/24",
                "/ip4/203.0.113.0/ipcidr/24",
                "/ip4/240.0.0.0/ipcidr/4"
         */


        String swarmKey = getSwarmKey(context);
        if (!swarmKey.isEmpty()) {
            node.setSwarmKey(swarmKey.getBytes());
            node.setEnablePrivateNetwork(isPrivateNetworkEnabled(context));
        }

        node.setAgent(IPFS.getStoredAgent(context));
        node.setPushing(false);
        node.setPort(IPFS.getSwarmPort(context));

        node.setGracePeriod(getGracePeriod(context));
        node.setHighWater(getHighWater(context));
        node.setLowWater(getLowWater(context));

        node.openDatabase();
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

    @Nullable
    public static File getExternalStorageDirectory(@NonNull Context context) {

        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        String dir = sharedPref.getString(STORAGE_DIRECTORY, "");
        Objects.requireNonNull(dir);
        if (dir.isEmpty()) {
            return null;
        }
        return new File(dir);
    }

    public static void setExternalStorageDirectory(@NonNull Context context, @Nullable File file) {

        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        if (file == null) {
            editor.putString(STORAGE_DIRECTORY, "");
        } else {
            editor.putString(STORAGE_DIRECTORY, file.getAbsolutePath());
        }
        editor.apply();

    }

    private static void setPublicKey(@NonNull Context context, @NonNull String key) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(PUBLIC_KEY, key);
        editor.apply();
    }

    private static String getStoredAgent(@NonNull Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        return sharedPref.getString(AGENT_KEY, "go-ipfs/0.8.0-dev/lite");

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

    public static void setLowWater(@NonNull Context context, int lowWater) {

        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt(LOW_WATER_KEY, lowWater);
        editor.apply();
    }

    private static int getLowWater(@NonNull Context context) {

        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        return sharedPref.getInt(LOW_WATER_KEY, 20);
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
    private static String getGracePeriod(@NonNull Context context) {

        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        return Objects.requireNonNull(sharedPref.getString(GRACE_PERIOD_KEY, "30s"));
    }

    public static void setGracePeriod(@NonNull Context context, @NonNull String gracePeriod) {

        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(GRACE_PERIOD_KEY, gracePeriod);
        editor.apply();

    }

    public static void setHighWater(@NonNull Context context, int highWater) {

        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt(HIGH_WATER_KEY, highWater);
        editor.apply();
    }

    private static int getHighWater(@NonNull Context context) {

        SharedPreferences sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE);
        return sharedPref.getInt(HIGH_WATER_KEY, 40);
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

    public static void logCacheDir(@NonNull Context context) {
        try {
            File[] files = context.getCacheDir().listFiles();
            if (files != null) {
                for (File file : files) {
                    LogUtils.error(TAG, "" + file.length() + " " + file.getAbsolutePath());
                    if (file.isDirectory()) {
                        File[] children = file.listFiles();
                        if (children != null) {
                            for (File child : children) {
                                LogUtils.error(TAG, "" + child.length() + " " + child.getAbsolutePath());
                            }
                        }
                    }
                }
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    public static void logBaseDir(@NonNull Context context) {
        try {
            File[] files = context.getFilesDir().listFiles();
            if (files != null) {
                for (File file : files) {
                    LogUtils.warning(TAG, "" + file.length() + " " + file.getAbsolutePath());
                    if (file.isDirectory()) {
                        logDir(file);
                    }
                }
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    public static void logDir(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                LogUtils.warning(TAG, "" + child.length() + " " + child.getAbsolutePath());
                if (child.isDirectory()) {
                    logDir(child);
                }
            }
        }
    }

    private static void deleteFile(@NonNull File root) {
        try {
            if (root.isDirectory()) {
                File[] files = root.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isDirectory()) {
                            deleteFile(file);
                            boolean result = file.delete();
                            if (!result) {
                                LogUtils.error(TAG, "File " + file.getName() + " not deleted");
                            }
                        } else {
                            boolean result = file.delete();
                            if (!result) {
                                LogUtils.error(TAG, "File " + file.getName() + " not deleted");
                            }
                        }
                    }
                }
                boolean result = root.delete();
                if (!result) {
                    LogUtils.error(TAG, "File " + root.getName() + " not deleted");
                }
            } else {
                boolean result = root.delete();
                if (!result) {
                    LogUtils.error(TAG, "File " + root.getName() + " not deleted");
                }
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    static void cleanCacheDir(@NonNull Context context) {

        try {
            File[] files = context.getCacheDir().listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteFile(file);
                        boolean result = file.delete();
                        if (!result) {
                            LogUtils.error(TAG, "File not deleted.");
                        }
                    } else {
                        boolean result = file.delete();
                        if (!result) {
                            LogUtils.error(TAG, "File not deleted.");
                        }
                    }
                }
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }


    public void setPusher(@Nullable Pusher pusher) {
        node.setPushing(pusher != null);
        this.pusher = pusher;
    }

    @Override
    public void push(byte[] data, @NonNull String pid) {
        String text = new String(Base64.decode(data, Base64.DEFAULT));

        if (pusher != null) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit(() -> pusher.push(text, pid));
        }
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

    public boolean push(@NonNull String pid, @NonNull HashMap<String, String> map) {
        return push(pid, gson.toJson(map));
    }

    private boolean push(@NonNull String pid, @NonNull String push) {
        if (!isDaemonRunning()) {
            return false;
        }
        try {
            byte[] bytes = Base64.encode(push.getBytes(), Base64.DEFAULT);
            return node.push(pid, bytes) == bytes.length;
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return false;
    }

    public void swarmEnhance(@NonNull String pid) {
        swarm.add(pid);
    }

    public void swarmEnhance(@NonNull List<String> users) {
        swarm.addAll(users);
    }

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

    public synchronized void startDaemon(boolean privateSharing) {
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

    public int getLocation() {
        return location;
    }

    public long getTotalSpace() {
        return this.baseDir.getTotalSpace();
    }

    public long getFreeSpace() {
        return this.baseDir.getFreeSpace();
    }

    public void bootstrap(int minPeers, int timeout) {
        if (isDaemonRunning()) {
            if (numSwarmPeers() < minPeers) {
                try {
                    Pair<List<String>, List<String>> result = DnsAddrResolver.getBootstrap();

                    List<String> bootstrap = result.first;
                    List<Callable<Boolean>> tasks = new ArrayList<>();
                    ExecutorService executor = Executors.newFixedThreadPool(bootstrap.size());
                    for (String address : bootstrap) {
                        tasks.add(() -> swarmConnect(address, null, timeout));
                    }

                    List<Future<Boolean>> futures = executor.invokeAll(tasks, timeout, TimeUnit.SECONDS);
                    for (Future<Boolean> future : futures) {
                        LogUtils.info(TAG, "\nBootstrap done " + future.isDone());
                    }


                    List<String> second = result.second;
                    tasks.clear();
                    executor = Executors.newFixedThreadPool(second.size());
                    for (String address : second) {
                        tasks.add(() -> swarmConnect(address, null, timeout));
                    }
                    futures.clear();
                    futures = executor.invokeAll(tasks, timeout, TimeUnit.SECONDS);
                    for (Future<Boolean> future : futures) {
                        LogUtils.info(TAG, "\nConnect done " + future.isDone());
                    }
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }
            }
        }
    }

    public List<String> dhtFindProviders(@NonNull CID cid, int numProvs, int timeout) {

        if (!isDaemonRunning()) {
            return Collections.emptyList();
        }

        List<String> providers = new ArrayList<>();

        try {

            node.dhtFindProvsTimeout(cid.getCid(), providers::add
                    , numProvs, timeout);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        return providers;
    }

    public void dhtPublish(@NonNull CID cid, @NonNull DhtClose closable) {

        if (!isDaemonRunning()) {
            return;
        }

        try {
            node.dhtProvide(cid.getCid(), closable);
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

    public boolean swarmConnect(@NonNull String pid, int timeout) {
        if (!isDaemonRunning()) {
            return false;
        }
        return swarmConnect("/p2p/" + pid, pid, timeout);
    }

    public boolean swarmConnect(@NonNull String multiAddress, @Nullable String pid, int timeout) {
        if (!isDaemonRunning()) {
            return false;
        }
        try {
            if (pid != null) {
                swarmEnhance(pid);
            }
            return node.swarmConnect(multiAddress, timeout);
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

    public void publishName(@NonNull CID cid, @NonNull Closeable closeable, @NonNull Sequence sequence) {
        if (!isDaemonRunning()) {
            return;
        }
        try {
            node.publishName(cid.getCid(), closeable::isClosed, sequence);
        } catch (Throwable ignore) {
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
    public ResolvedName resolveName(@NonNull String name, final long sequence,
                                    @NonNull Closeable closeable) {
        if (!isDaemonRunning()) {
            return null;
        }


        long time = System.currentTimeMillis();

        AtomicReference<ResolvedName> resolvedName = new AtomicReference<>(null);
        try {
            AtomicInteger counter = new AtomicInteger(0);
            AtomicBoolean close = new AtomicBoolean(false);
            node.resolveName(new ResolveInfo() {
                @Override
                public boolean close() {
                    return close.get() || closeable.isClosed();
                }

                @Override
                public void resolved(String hash, long seq) {


                    LogUtils.error(TAG, "" + seq + " " + hash);

                    if (seq < sequence) {
                        close.set(true);
                        return; // newest value already available
                    }

                    if (hash.startsWith("/ipfs/")) {
                        if (seq > sequence || counter.get() < 2) {
                            close.set(true);
                        } else {
                            counter.incrementAndGet();
                        }
                        resolvedName.set(new ResolvedName(
                                seq, hash.replaceFirst("/ipfs/", "")));
                    }

                }
            }, name, false, 8);

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        LogUtils.error(TAG, "Finished resolve name " + name + " " +
                (System.currentTimeMillis() - time));

        return resolvedName.get();
    }

    public void rm(@NonNull String cid, boolean recursively) {

        try {
            node.rm(cid, recursively);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    public long getSwarmPort() {
        return node.getPort();
    }

    @Nullable
    public CID storeData(@NonNull byte[] data) {

        try (InputStream inputStream = new ByteArrayInputStream(data)) {
            return storeInputStream(inputStream);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        return null;
    }

    @Nullable
    public CID storeText(@NonNull String content) {

        try (InputStream inputStream = new ByteArrayInputStream(content.getBytes())) {
            return storeInputStream(inputStream);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        return null;
    }

    @Nullable
    public CID rmLinkFromDir(CID dir, String name) {
        try {
            return CID.create(node.removeLinkFromDir(dir.getCid(), name));
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        return null;
    }

    @Nullable
    public CID addLinkToDir(@NonNull CID dir, @NonNull String name, @NonNull CID link) {
        try {
            return CID.create(node.addLinkToDir(dir.getCid(), name, link.getCid()));
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        return null;
    }

    @Nullable
    public CID createEmptyDir() {
        try {
            return CID.create(node.createEmptyDir());
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        return null;
    }

    @Nullable
    public LinkInfo getLinkInfo(@NonNull CID dir, @NonNull List<String> path, @NonNull Closeable closeable) {

        LinkInfo linkInfo = null;
        CID root = dir;

        for (String name : path) {
            linkInfo = getLinkInfo(root, name, closeable);
            if (linkInfo != null) {
                root = linkInfo.getCid();
            } else {
                break;
            }
        }

        return linkInfo;
    }

    @Nullable
    public LinkInfo getLinkInfo(@NonNull CID dir, @NonNull String name, @NonNull Closeable closeable) {
        List<LinkInfo> links = ls(dir, closeable);
        if (links != null) {
            for (LinkInfo info : links) {
                if (Objects.equals(info.getName(), name)) {
                    return info;
                }
            }
        }
        return null;
    }

    @Nullable
    public List<LinkInfo> getLinks(@NonNull CID cid, @NonNull Closeable closeable) {

        LogUtils.info(TAG, "Lookup CID : " + cid.getCid());

        List<LinkInfo> links = ls(cid, closeable);
        if (links == null) {
            LogUtils.info(TAG, "no links or stopped");
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
    public List<LinkInfo> ls(@NonNull CID cid, @NonNull Closeable closeable) {
        if (!isDaemonRunning()) {
            return Collections.emptyList();
        }
        List<LinkInfo> infoList = new ArrayList<>();
        try {

            node.ls(cid.getCid(), new LsInfoClose() {
                @Override
                public boolean close() {
                    return closeable.isClosed();
                }

                @Override
                public void lsInfo(String name, String hash, long size, int type) {
                    LinkInfo info = LinkInfo.create(name, hash, size, type);
                    infoList.add(info);
                }
            });

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
            return null;
        }
        if (closeable.isClosed()) {
            return null;
        }
        return infoList;
    }

    @Nullable
    public CID storeFile(@NonNull File target) {

        try {
            return CID.create(node.addFile(target.getAbsolutePath()));
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        return null;
    }

    @NonNull
    public Reader getReader(@NonNull CID cid) throws Exception {
        return node.getReader(cid.getCid());
    }

    private boolean loadToOutputStream(@NonNull OutputStream outputStream, @NonNull CID cid,
                                       @NonNull Progress progress) {

        try (InputStream inputStream = getLoaderStream(cid, progress)) {
            IPFS.copy(inputStream, outputStream);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
            return false;
        }
        return true;

    }

    private void getToOutputStream(@NonNull OutputStream outputStream, @NonNull CID cid) throws Exception {
        try (InputStream inputStream = getInputStream(cid)) {
            IPFS.copy(inputStream, outputStream);
        }
    }

    public boolean loadToFile(@NonNull File file, @NonNull CID cid, @NonNull Progress progress) {
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
                                    @NonNull CID cid, long size) throws Exception {

        long totalRead = 0L;
        int remember = 0;
        Reader reader = getReader(cid);
        try {

            reader.load(4096);
            long read = reader.getRead();
            while (read > 0) {

                if (progress.isClosed()) {
                    throw new RuntimeException("Progress closed");
                }

                // calculate progress
                totalRead += read;
                if (progress.doProgress()) {
                    if (size > 0) {
                        int percent = (int) ((totalRead * 100.0f) / size);
                        if (remember < percent) {
                            remember = percent;
                            progress.setProgress(percent);
                        }
                    }
                }

                byte[] bytes = reader.getData();
                os.write(bytes, 0, bytes.length);

                reader.load(4096);
                read = reader.getRead();
            }
        } finally {
            reader.close();
        }

    }

    public void storeToOutputStream(@NonNull OutputStream os, @NonNull CID cid, int blockSize) throws Exception {

        Reader reader = getReader(cid);
        try {

            reader.load(blockSize);
            long read = reader.getRead();
            while (read > 0) {
                byte[] bytes = reader.getData();
                os.write(bytes, 0, bytes.length);

                reader.load(blockSize);
                read = reader.getRead();
            }
        } finally {
            reader.close();
        }

    }

    @NonNull
    private Loader getLoader(@NonNull CID cid, @NonNull Closeable closeable) throws Exception {
        return node.getLoader(cid.getCid(), closeable::isClosed);

    }

    @NonNull
    public InputStream getLoaderStream(@NonNull CID cid, @NonNull Closeable closeable, long readTimeoutMillis) throws Exception {

        Loader loader = getLoader(cid, closeable);

        return new CloseableInputStream(loader, readTimeoutMillis);

    }

    @NonNull
    private InputStream getLoaderStream(@NonNull CID cid, @NonNull Progress progress) throws Exception {

        Loader loader = getLoader(cid, progress);

        return new LoaderInputStream(loader, progress);

    }

    public void storeToFile(@NonNull File file, @NonNull CID cid, int blockSize) throws Exception {

        try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            storeToOutputStream(fileOutputStream, cid, blockSize);
        }

    }

    @Nullable
    public CID storeInputStream(@NonNull InputStream inputStream,
                                @NonNull Progress progress, long size) {


        String res = "";
        try {
            res = node.stream(new WriterStream(inputStream, progress, size));
        } catch (Throwable e) {
            if (!progress.isClosed()) {
                LogUtils.error(TAG, e);
            }
        }

        if (!res.isEmpty()) {
            return CID.create(res);
        }
        return null;
    }

    @Nullable
    public CID storeInputStream(@NonNull InputStream inputStream) {

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
    public String getText(@NonNull CID cid) {

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            getToOutputStream(outputStream, cid);
            return new String(outputStream.toByteArray());
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
            return null;
        }
    }

    @Nullable
    public byte[] getData(@NonNull CID cid) {

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            getToOutputStream(outputStream, cid);
            return outputStream.toByteArray();
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
            return null;
        }
    }

    @Nullable
    public byte[] loadData(@NonNull CID cid, @NonNull Progress progress) {
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

    public void gc() {
        try {
            node.repoGC();
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }

    }

    @Override
    public void error(String message) {
        if (message != null && !message.isEmpty()) {
            LogUtils.error(TAG, message);
        }
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
    public InputStream getInputStream(@NonNull CID cid) throws Exception {
        Reader reader = getReader(cid);
        return new ReaderInputStream(reader);

    }

    public boolean isDaemonRunning() {
        return node.getRunning();
    }

    public boolean isValidCID(String multihash) {
        try {
            this.node.cidCheck(multihash);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public boolean isValidPID(String multihash) {
        try {
            this.node.pidCheck(multihash);
            return true;
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
            LogUtils.info(TAG, "Seeding Amount : " + amount);
        });
    }

    @Override
    public void leeching(long amount) {
        leeching += amount;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            events.leeching(leeching);
            LogUtils.info(TAG, "Leeching Amount : " + amount);
        });
    }

    public boolean isEmptyDir(@NonNull CID cid) {
        return Objects.equals(cid.getCid(), EMPTY_DIR_32) || Objects.equals(cid.getCid(), EMPTY_DIR_58);
    }

    public boolean isDir(@NonNull CID doc, @NonNull Closeable closeable) {
        List<LinkInfo> links = getLinks(doc, closeable);
        return links != null && !links.isEmpty();
    }

    public long getSize(@NonNull CID cid, @NonNull Closeable closeable) {
        List<LinkInfo> links = ls(cid, closeable);
        int size = -1;
        if (links != null) {
            for (LinkInfo info : links) {
                size += info.getSize();
            }
        }
        return size;
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
        void push(@NonNull String text, @NonNull String pid);
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

    private static class ReaderInputStream extends InputStream implements AutoCloseable {
        private final Reader mReader;
        private int position = 0;
        private byte[] data = null;

        ReaderInputStream(@NonNull Reader reader) {
            mReader = reader;
        }

        @Override
        public int available() {
            long size = mReader.getSize();
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
            mReader.load(4096);
            int read = (int) mReader.getRead();
            if (read > 0) {
                data = new byte[read];
                byte[] values = mReader.getData();
                System.arraycopy(values, 0, data, 0, read);
                return true;
            }
            return false;
        }

        public void close() {
            try {
                mReader.close();
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }
        }
    }


    private static class CloseableInputStream extends InputStream implements AutoCloseable {
        private final Loader mLoader;
        private final long readTimeoutMillis;
        private int position = 0;
        private byte[] data = null;

        CloseableInputStream(@NonNull Loader loader, long readTimeoutMillis) {
            this.mLoader = loader;
            this.readTimeoutMillis = readTimeoutMillis;
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
            long start = System.currentTimeMillis();
            mLoader.load(4096, () -> (System.currentTimeMillis() - start) > (readTimeoutMillis));
            int read = (int) mLoader.getRead();
            if (read > 0) {
                data = new byte[read];
                byte[] values = mLoader.getData();
                System.arraycopy(values, 0, data, 0, read);
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
