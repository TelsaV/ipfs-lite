package threads.lite;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.luminis.quic.QuicClientConnectionImpl;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import threads.lite.cid.Multiaddr;

import static junit.framework.TestCase.assertNotNull;

@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsQuicTest {


    private static final String TAG = IpfsQuicTest.class.getSimpleName();
    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void test_1() throws Exception {

        IPFS ipfs = TestEnv.getTestInstance(context);

        Multiaddr multiaddr = new Multiaddr("/ip4/147.75.109.213/udp/4001/quic/p2p/QmNnooDu7bfjPFoTZYxMNLWUQJyrVwtbZg5gBMjTezGAJN");

        QuicClientConnectionImpl conn = ipfs.getHost().dial(multiaddr);
        assertNotNull(conn);

        conn.connect(IPFS.CONNECT_TIMEOUT, IPFS.APRN, null, null);

        conn.close();

    }

}


