package threads.server.ipfs;

import android.content.Context;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Objects;

import io.ipfs.LogUtils;
import lite.PeerInfo;

import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;


@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsTest {
    private static final String TAG = IpfsTest.class.getSimpleName();
    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }


    @Test
    public void test_versionAndPID() {
        IPFS ipfs = TestEnv.getTestInstance(context);


        String pid = IPFS.getPeerID(context);
        LogUtils.error(TAG, Objects.requireNonNull(pid));


        PeerInfo info = ipfs.pidInfo(pid);
        assertNotNull(info);
        assertEquals(pid, info.getID());
    }

    @Test
    public void test_dns_addr() {

        if (TestEnv.isConnected(context)) {

            Pair<List<String>, List<String>> result = DnsAddrResolver.getMultiAddresses();
            List<String> first = result.first;
            List<String> second = result.second;
            assertNotNull(first);
            assertEquals(first.size(), 1);
            assertNotNull(second);
            assertEquals(second.size(), 12);


            for (String address : first) {
                LogUtils.error(TAG, address);
            }


            for (String address : second) {
                LogUtils.error(TAG, address);
            }
        }
    }

    @Test
    public void streamTest() {
        IPFS ipfs = TestEnv.getTestInstance(context);

        String test = "Moin";
        String cid = ipfs.storeText(test);
        assertNotNull(cid);
        byte[] bytes = ipfs.getData(cid);
        assertNotNull(bytes);
        assertEquals(test, new String(bytes));

        String fault = Objects.requireNonNull(IPFS.getPeerID(context));

        bytes = ipfs.loadData(fault, new TimeoutProgress(10));
        assertNull(bytes);


    }

    @Test
    public void test_timeout_cat() {

        String notValid = "QmaFuc7VmzwT5MAx3EANZiVXRtuWtTwALjgaPcSsZ2Jdip";
        IPFS ipfs = TestEnv.getTestInstance(context);

        byte[] bytes = ipfs.loadData(notValid, new TimeoutProgress(10));

        assertNull(bytes);

    }


    private byte[] getRandomBytes() {
        return RandomStringUtils.randomAlphabetic(400000).getBytes();
    }

    @Test
    public void test_add_cat() {
        IPFS ipfs = TestEnv.getTestInstance(context);

        byte[] content = getRandomBytes();

        String hash58Base = ipfs.storeData(content);
        assertNotNull(hash58Base);
        LogUtils.error(TAG, hash58Base);

        byte[] fileContents = ipfs.getData(hash58Base);
        assertNotNull(fileContents);
        assertEquals(content.length, fileContents.length);
        assertEquals(new String(content), new String(fileContents));

        ipfs.rm(hash58Base, true);

    }


    @Test
    public void test_ls_timeout() throws ClosedException {

        IPFS ipfs = TestEnv.getTestInstance(context);

        List<LinkInfo> links = ipfs.getLinks(
                "QmXm3f7uKuFKK3QUL1V1oJZnpJSYX8c3vdhd94evSQUPCH",
                new TimeoutProgress(20));
        assertNull(links);

    }

    @Test
    public void test_ls_small() throws ClosedException {

        IPFS ipfs = TestEnv.getTestInstance(context);


        String cid = ipfs.storeText("hallo");
        assertNotNull(cid);
        List<LinkInfo> links = ipfs.getLinks(cid, () -> false);
        assertNotNull(links);
        assertEquals(links.size(), 0);
        links = ipfs.getLinks(cid, new TimeoutProgress(20));
        assertNotNull(links);
        assertEquals(links.size(), 0);
    }
}
