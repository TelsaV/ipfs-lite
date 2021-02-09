package threads.server.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import threads.LogUtils;
import threads.server.core.Content;
import threads.server.core.DOCS;
import threads.server.core.pages.Page;
import threads.server.core.peers.PEERS;
import threads.server.core.peers.User;
import threads.server.ipfs.IPFS;
import threads.server.ipfs.LinkInfo;
import threads.server.services.ConnectService;
import threads.server.services.LiteService;

public class PageWorker extends Worker {

    private static final String TAG = PageWorker.class.getSimpleName();

    private final IPFS ipfs;
    private final DOCS docs;

    @SuppressWarnings("WeakerAccess")
    public PageWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);

        ipfs = IPFS.getInstance(context);
        docs = DOCS.getInstance(context);
    }


    private static PeriodicWorkRequest getWork(@NonNull Context context) {

        int time = LiteService.getPublishServiceTime(context);

        return new PeriodicWorkRequest.Builder(PageWorker.class, time, TimeUnit.HOURS)
                .addTag(TAG)
                .build();

    }

    public static void publish(@NonNull Context context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    TAG, ExistingPeriodicWorkPolicy.REPLACE, getWork(context));
    }


    @NonNull
    @Override
    public Result doWork() {

        long start = System.currentTimeMillis();

        LogUtils.info(TAG, "Start " + getId().toString() + " ...");

        try {

            docs.updatePinsPage();


            if (!isStopped()) {
                try {

                    LiteService.bootstrap(getApplicationContext(), 20);

                    if (ipfs.isPrivateNetwork()) {
                        ConnectService.connect(getApplicationContext());
                    }
                } catch (Throwable e) {
                    LogUtils.error(TAG, e);
                }
            }

            if (!isStopped()) {
                publishPage();
            }

            if (isStopped()) {
                LogUtils.info(TAG, "Stopped " + getId().toString() +
                        " onStart [" + (System.currentTimeMillis() - start) + "]...");
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        } finally {
            LogUtils.info(TAG, "Finish " + getId().toString() +
                    " onStart [" + (System.currentTimeMillis() - start) + "]...");
        }
        return Result.success();
    }

    private void publishSequence(@NonNull String content, long sequence) {
        String host = IPFS.getPeerID(getApplicationContext());
        Objects.requireNonNull(host);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                List<User> users = PEERS.getInstance(getApplicationContext()).getUsers();
                if (!users.isEmpty()) {

                    for (User user : users) {
                        if (user.isLite()) {
                            boolean connected = ipfs.isConnected(user.getPid());
                            if (connected) {

                                HashMap<String, String> hashMap = new HashMap<>();
                                hashMap.put(Content.IPNS, content);
                                hashMap.put(Content.SEQ, "" + sequence);

                                String cid = ipfs.storeText(hashMap.toString());
                                Objects.requireNonNull(cid);
                                boolean success = ipfs.notify(user.getPid(), cid);

                                LogUtils.info(TAG, "success pushing [" + success + "]");
                            }
                        }
                    }
                }
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });
    }

    private void publishPage() {
        try {

            Page page = docs.getPinsPage();
            if (page != null) {

                String content = page.getContent();
                Objects.requireNonNull(content);

                if (!IPFS.isPrivateSharingEnabled(getApplicationContext())) {
                    Executors.newSingleThreadExecutor().execute(() -> publishContent(content));
                }

                LogUtils.error(TAG, "Start publish name " + content);

                int seq = IPFS.getSequence(getApplicationContext());
                seq++;
                IPFS.setSequence(getApplicationContext(), seq);

                publishSequence(content, seq);

                ipfs.publishName(content, this::isStopped, seq);


                LogUtils.error(TAG, "End publish name " + content);


            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    private void publishContent(@NonNull String content) {

        try {

            List<LinkInfo> links = ipfs.getLinks(content, this::isStopped);

            if (links != null) {
                for (LinkInfo linkInfo : links) {
                    if (linkInfo.isFile() || linkInfo.isDirectory()) {
                        LogUtils.error(TAG, "publishContent " + linkInfo.getName());
                        Executors.newSingleThreadExecutor().execute(() ->
                                publishContent(linkInfo.getContent()));
                    }
                }
            }

            ipfs.dhtPublish(content, this::isStopped);

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

}

