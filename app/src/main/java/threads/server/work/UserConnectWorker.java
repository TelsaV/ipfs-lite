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

import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.cid.Multiaddr;
import threads.lite.cid.PeerId;
import threads.lite.core.TimeoutCloseable;
import threads.lite.host.PeerInfo;
import threads.server.InitApplication;
import threads.server.Settings;
import threads.server.core.Content;
import threads.server.core.peers.PEERS;
import threads.server.core.peers.User;

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

            connect(PeerId.fromBase58(pid));


            if (!isStopped()) {
                PeerInfo pInfo = ipfs.getPeerInfo(PeerId.fromBase58(pid), new TimeoutCloseable(5));

                if (!peers.getUserIsLite(pid)) {

                    String agent = pInfo.getAgent();
                    if (!agent.isEmpty()) {
                        peers.setUserAgent(pid, agent);
                        if (agent.contains(IPFS.AGENT_PREFIX)) {
                            peers.setUserLite(pid);
                        }
                    }
                }
            }


            if (!isStopped()) {

                String multiAddress = ipfs.remoteAddress(
                        PeerId.fromBase58(pid), new TimeoutCloseable(5)).toString();

                if (!multiAddress.isEmpty() && !multiAddress.contains(Content.CIRCUIT)) {
                    peers.setUserAddress(pid, multiAddress);
                }
            }


        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        } finally {
            LogUtils.info(TAG, " finish onStart [" + (System.currentTimeMillis() - start) + "]...");
        }

        return Result.success();
    }


    private void connect(@NonNull PeerId pid) {

        int timeout = Settings.getConnectionTimeout(getApplicationContext());

        ipfs.swarmEnhance(pid);


        if (!isStopped()) {
            // now check old addresses
            PEERS peers = PEERS.getInstance(getApplicationContext());
            User user = peers.getUserByPid(pid.toBase58());
            Objects.requireNonNull(user);
            String address = user.getAddress();
            if (!address.isEmpty() && !address.contains("p2p-circuit")) {
                ipfs.addMultiAddress(pid, new Multiaddr(address));
            }
        }

        if (!isStopped()) {
            ipfs.swarmConnect(pid, InitApplication.USER_GRACE_PERIOD,
                    new TimeoutCloseable(timeout));
        }

    }
}

