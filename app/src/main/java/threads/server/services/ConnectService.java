package threads.server.services;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.cid.Multiaddr;
import threads.lite.cid.PeerId;
import threads.lite.core.TimeoutCloseable;
import threads.server.InitApplication;
import threads.server.Settings;
import threads.server.core.peers.PEERS;
import threads.server.core.peers.User;

public class ConnectService {
    private static final String TAG = ConnectService.class.getSimpleName();

    public static void connect(@NonNull Context context) throws InterruptedException {
        List<User> users = PEERS.getInstance(context).getUsers();
        if (!users.isEmpty()) {
            ExecutorService executor = Executors.newFixedThreadPool(users.size());

            List<Callable<Boolean>> tasks = new ArrayList<>();


            for (User user : users) {
                tasks.add(() -> connect(context, user.getPid()));
            }
            long start = System.currentTimeMillis();

            int timeout = Settings.getConnectionTimeout(context);
            List<Future<Boolean>> result = executor.invokeAll(tasks, timeout, TimeUnit.SECONDS);
            for (Future<Boolean> future : result) {
                LogUtils.error(TAG, "Success " + future.isDone());
            }
            LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
        }
    }

    private static boolean connect(@NonNull Context context, @NonNull String pid) {

        IPFS ipfs = IPFS.getInstance(context);


        // now check old addresses
        PEERS peers = PEERS.getInstance(context);
        User user = peers.getUserByPid(pid);
        Objects.requireNonNull(user);
        String address = user.getAddress();
        PeerId peerId = PeerId.fromBase58(pid);
        if (!address.isEmpty() && !address.contains("p2p-circuit")) {
            ipfs.addMultiAddress(peerId, new Multiaddr(address));
        }
        int timeout = Settings.getConnectionTimeout(context);

        return ipfs.swarmConnect(peerId, InitApplication.USER_GRACE_PERIOD,
                new TimeoutCloseable(timeout));


    }
}
