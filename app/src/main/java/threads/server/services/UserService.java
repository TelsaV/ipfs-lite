package threads.server.services;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.WorkManager;

import com.google.gson.Gson;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import threads.LogUtils;
import threads.server.core.DeleteOperation;
import threads.server.core.events.EVENTS;
import threads.server.core.peers.PEERS;
import threads.server.core.peers.User;
import threads.server.ipfs.IPFS;

public class UserService {


    private static final String TAG = UserService.class.getSimpleName();


    public static void deleteUsers(@NonNull Context context, String... pids) {


        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            long start = System.currentTimeMillis();
            try {
                PEERS.getInstance(context).removeUsers(pids);
                IPFS ipfs = IPFS.getInstance(context);
                for (String pid : pids) {
                    ipfs.swarmReduce(pid);
                }
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            } finally {
                LogUtils.info(TAG, " deleteUsers finish onStart [" +
                        (System.currentTimeMillis() - start) + "]...");
            }

        });
    }

    public static void removeUsers(@NonNull Context context, String... pids) {


        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            long start = System.currentTimeMillis();

            try {
                PEERS peers = PEERS.getInstance(context);
                EVENTS events = EVENTS.getInstance(context);
                peers.setUsersInvisible(pids);

                Gson gson = new Gson();
                DeleteOperation deleteOperation = new DeleteOperation();
                deleteOperation.kind = DeleteOperation.PEERS;
                deleteOperation.pids = pids;

                String content = gson.toJson(deleteOperation, DeleteOperation.class);
                events.delete(content);


                for (String pid : pids) {
                    User user = peers.getUserByPid(pid);
                    if (user != null) {
                        UUID uuid = user.getWorkUUID();
                        if (uuid != null) {
                            WorkManager.getInstance(context).cancelWorkById(uuid);
                        }
                    }
                }


            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            } finally {
                LogUtils.info(TAG, " removeUsers finish onStart [" +
                        (System.currentTimeMillis() - start) + "]...");
            }

        });
    }


}

