package threads.server.ipfs;


import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.LogUtils;
import io.ipfs.IPFS;
import lite.PeerInfo;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;


@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsFindPeer {
    private static final String TAG = IpfsFindPeer.class.getSimpleName();

    private static final String DUMMY_PID = "QmVLnkyetpt7JNpjLmZX21q9F8ZMnhBts3Q53RcAGxWH6V";

    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }


    @Test
    public void dummy() {
        assertNotNull(context);
    }

    @Test
    public void test_id() {

        IPFS ipfs = TestEnv.getTestInstance(context);

        PeerInfo info = ipfs.id();
        assertNotNull(info);
        assertEquals(info.getProtocolVersion(), "ipfs/0.1.0");
        LogUtils.error(TAG, info.getID());

    }

    @Test
    public void swarm_connect() {

        IPFS ipfs = TestEnv.getTestInstance(context);
        String pc = "QmRxoQNy1gNGMM1746Tw8UBNBF8axuyGkzcqb2LYFzwuXd";

        // TIMEOUT not working
        boolean result = ipfs.swarmConnect(IPFS.P2P_PATH + pc, null, 6);
        assertFalse(result);

    }


    //@Test
    public void test_local_peer() throws Exception {
        IPFS ipfs = TestEnv.getTestInstance(context);

        String local = "Qmf5TsSK8dVm3btzuUrnvS8wfUW6e2vMxMRkzV9rsG6eDa";

        PeerInfo peerInfo = ipfs.pidInfo(local);
        assertNotNull(peerInfo);

        boolean result = ipfs.swarmConnect(IPFS.P2P_PATH + local, null, 60);

        assertTrue(result);

        while (ipfs.isConnected(local)) {
            LogUtils.error(TAG, "Peer conntected with : " + local);
            Thread.sleep(1000);
        }
    }

    @Test
    public void test_swarm_connect() {
        IPFS ipfs = TestEnv.getTestInstance(context);

        String relay = "QmchgNzyUFyf2wpfDMmpGxMKHA3PkC1f3H2wUgbs21vXoz";


        boolean connected = ipfs.isConnected(relay);
        assertFalse(connected);


        boolean result = ipfs.swarmConnect(IPFS.P2P_PATH + relay, null, 1);
        assertFalse(result);


        relay = "QmchgNzyUFyf2wpfDMmpGxMKHA3PkC1f3H2wUgbs21vXoz";
        result = ipfs.swarmConnect(IPFS.P2P_PATH + relay, null, 10);
        assertFalse(result);


        relay = DUMMY_PID;
        result = ipfs.swarmConnect(IPFS.P2P_PATH + relay, null, 10);
        assertFalse(result);

    }


    @Test
    public void test_find_swarm_peers() {
        IPFS ipfs = TestEnv.getTestInstance(context);


        AtomicInteger atomicInteger = new AtomicInteger(0);


        while (atomicInteger.incrementAndGet() < 5) {
            List<String> peers = ipfs.swarmPeers();

            assertNotNull(peers);
            LogUtils.error(TAG, "Peers : " + peers.size());
            for (String peer : peers) {

                long time = System.currentTimeMillis();
                LogUtils.error(TAG, "isConnected : " + ipfs.isConnected(peer)
                        + " " + (System.currentTimeMillis() - time));

                LogUtils.error(TAG, peer);

                PeerInfo peerInfo = ipfs.pidInfo(peer);
                if (peerInfo != null) {
                    String publicKey = peerInfo.getPublicKey();
                    assertNotNull(publicKey);
                    LogUtils.error(TAG, peerInfo.toString());
                }

            }

        }

    }

}
