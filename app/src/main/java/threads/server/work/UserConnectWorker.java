package threads.server.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Objects;

import lite.Peer;
import lite.PeerInfo;
import threads.LogUtils;
import threads.server.InitApplication;
import threads.server.core.Content;
import threads.server.core.peers.PEERS;
import threads.server.core.peers.User;
import threads.server.ipfs.IPFS;

public class UserConnectWorker extends Worker {

    private static final String TAG = UserConnectWorker.class.getSimpleName();
    private final PEERS peers;
    private final IPFS ipfs;

    @SuppressWarnings("WeakerAccess")
    public UserConnectWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        peers = PEERS.getInstance(getApplicationContext());
        ipfs = IPFS.getInstance(getApplicationContext());
    }

    public static OneTimeWorkRequest getWork(@NonNull String pid) {

        Constraints.Builder builder = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED);


        Data.Builder data = new Data.Builder();
        data.putString(Content.PID, pid);

        return new OneTimeWorkRequest.Builder(UserConnectWorker.class)
                .setInputData(data.build())
                .addTag(TAG)
                .setConstraints(builder.build())
                .build();

    }

    @NonNull
    @Override
    public Result doWork() {

        String pid = getInputData().getString(Content.PID);
        Objects.requireNonNull(pid);
        long start = System.currentTimeMillis();

        LogUtils.info(TAG, " start connect [" + pid + "]...");


        try {

            connect(pid);


            if (!isStopped()) {
                PeerInfo pInfo = ipfs.pidInfo(pid);
                if (pInfo != null) {

                    if (!peers.getUserIsLite(pid)) {

                        String agent = pInfo.getAgentVersion();
                        if (!agent.isEmpty()) {
                            peers.setUserAgent(pid, agent);
                            if (agent.endsWith("lite")) {
                                peers.setUserLite(pid);
                            }
                        }
                    }
                }
            }


            if (!isStopped()) {
                Peer peerInfo = ipfs.swarmPeer(pid);
                String multiAddress = "";
                if (peerInfo != null) {
                    multiAddress = peerInfo.getAddress();
                }

                if (!multiAddress.isEmpty() && !multiAddress.contains(Content.CIRCUIT)) {
                    peers.setUserAddress(pid, multiAddress);
                }
            }


            ipfs.isConnected(pid);

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        } finally {
            LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
        }

        return Result.success();
    }


    private void connect(@NonNull String pid) {

        int timeout = InitApplication.getConnectionTimeout(getApplicationContext());

        ipfs.swarmEnhance(pid);

        if (!ipfs.isConnected(pid)) {
            if (!isStopped()) {
                // now check old addresses
                PEERS peers = PEERS.getInstance(getApplicationContext());
                User user = peers.getUserByPid(pid);
                Objects.requireNonNull(user);
                String address = user.getAddress();
                if (!address.isEmpty() && !address.contains("p2p-circuit")) {
                    if (ipfs.swarmConnect(pid, timeout)) {
                        return;
                    }
                }
            }

            if (!isStopped()) {
                ipfs.swarmConnect(pid, timeout);
            }
        }
    }
}

