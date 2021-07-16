package threads.lite;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import threads.lite.cid.Multiaddr;
import threads.lite.cid.PeerId;
import threads.lite.core.TimeoutCloseable;
import threads.lite.host.PeerInfo;

import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


@RunWith(AndroidJUnit4.class)
public class IpfsServerTest {

    private static final String TAG = IpfsServerTest.class.getSimpleName();
    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void server_test() throws Exception {

        IPFS ipfs = TestEnv.getTestInstance(context);
        ipfs.daemon();

        Multiaddr multiaddr = new Multiaddr("/ip4/127.0.0.1" + "/udp/" + ipfs.getPort() + "/quic");


        PeerId host = ipfs.getPeerID();
        assertNotNull(host);

        // make a connection to yourself
        ipfs.getHost().addToAddressBook(host, Collections.singletonList(multiaddr), true);

        PeerInfo info = ipfs.getPeerInfo(host, new TimeoutCloseable(IPFS.CONNECT_TIMEOUT));
        assertNotNull(info);
        assertEquals(info.getAgent(), IPFS.AGENT);

        String data = "moin";

        AtomicBoolean success = new AtomicBoolean(false);

        ipfs.setPusher((peerId, content) -> success.set(content.equals(data)));


        ipfs.notify(host, data);
        Thread.sleep(1000);

        assertTrue(success.get());

        ipfs.shutdown();

    }


}