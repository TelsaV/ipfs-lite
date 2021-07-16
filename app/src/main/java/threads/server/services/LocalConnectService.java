package threads.server.services;

import android.content.Context;

import androidx.annotation.NonNull;

import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.cid.PeerId;
import threads.lite.core.TimeoutCloseable;
import threads.lite.host.PeerInfo;
import threads.server.core.peers.PEERS;
import threads.server.core.peers.User;

public class LocalConnectService {

    private static final String TAG = LocalConnectService.class.getSimpleName();

    public static void connect(@NonNull Context context, @NonNull String pid,
                               @NonNull String host, int port, boolean inet6) {

        try {
            IPFS ipfs = IPFS.getInstance(context);

            ipfs.swarmEnhance(PeerId.fromBase58(pid));

            String pre = "/ip4";
            if (inet6) {
                pre = "/ip6";
            }
            String multiAddress = pre + host + "/udp/" + port + "/quic/p2p/" + pid;

            boolean connect = ipfs.swarmConnect(multiAddress, IPFS.CONNECT_TIMEOUT);


            LogUtils.error(TAG, "Success " + connect + " " + pid);

            PEERS peers = PEERS.getInstance(context);

            User user = peers.getUserByPid(pid);
            if (user == null) {
                user = peers.createUser(PeerId.fromBase58(pid).toBase58(), pid);
                peers.storeUser(user);


                PeerInfo pInfo = ipfs.getPeerInfo(PeerId.fromBase58(pid), new TimeoutCloseable(5));


                if (!peers.getUserIsLite(pid)) {

                    String agent = pInfo.getAgent();
                    if (!agent.isEmpty()) {
                        peers.setUserAgent(pid, agent);
                        if (agent.endsWith("lite")) {
                            peers.setUserLite(pid);
                        }
                    }
                }
            }

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

}

