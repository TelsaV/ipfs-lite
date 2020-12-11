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

import threads.LogUtils;
import threads.server.InitApplication;
import threads.server.core.peers.PEERS;
import threads.server.core.peers.User;
import threads.server.ipfs.IPFS;

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

            int timeout = InitApplication.getConnectionTimeout(context);
            List<Future<Boolean>> result = executor.invokeAll(tasks, timeout, TimeUnit.SECONDS);
            for (Future<Boolean> future : result) {
                LogUtils.error(TAG, "Success " + future.isDone());
            }
            LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
        }
    }

    private static boolean connect(@NonNull Context context, @NonNull String pid) {

        IPFS ipfs = IPFS.getInstance(context);


        if (ipfs.isConnected(pid)) {
            return true;
        }

        // now check old addresses
        PEERS peers = PEERS.getInstance(context);
        User user = peers.getUserByPid(pid);
        Objects.requireNonNull(user);
        String address = user.getAddress();
        if (!address.isEmpty() && !address.contains("p2p-circuit")) {
            String multiAddress = address.concat("/p2p/" + pid);

            if (ipfs.swarmConnect(multiAddress, pid, 5)) {
                return true;
            }
        }
        int timeout = InitApplication.getConnectionTimeout(context);

        return ipfs.swarmConnect(pid, timeout);


    }
}
