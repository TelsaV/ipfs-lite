package threads.server.ipfs;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import threads.LogUtils;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertNull;


@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsCatTest {

    private static final String TAG = IpfsCatTest.class.getSimpleName();
    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void cat_test() {

        IPFS ipfs = TestEnv.getTestInstance(context);
        String cid = "Qmaisz6NMhDB51cCvNWa1GMS7LU1pAxdF4Ld6Ft9kZEP2a";
        long time = System.currentTimeMillis();
        List<String> provs = ipfs.dhtFindProviders(cid, 10, 45);
        for (String prov : provs) {
            LogUtils.error(TAG, "Provider " + prov);
        }
        LogUtils.error(TAG, "Time Providers : " + (System.currentTimeMillis() - time) + " [ms]");

        time = System.currentTimeMillis();
        List<LinkInfo> res = ipfs.ls(cid, new TimeoutProgress(10));
        LogUtils.error(TAG, "Time : " + (System.currentTimeMillis() - time) + " [ms]");
        assertNotNull(res);
        assertTrue(res.isEmpty());

        time = System.currentTimeMillis();
        byte[] content = ipfs.loadData(cid, new TimeoutProgress(10));

        LogUtils.error(TAG, "Time : " + (System.currentTimeMillis() - time) + " [ms]");

        assertNotNull(content);


        time = System.currentTimeMillis();
        ipfs.rm(cid, true);
        LogUtils.error(TAG, "Time : " + (System.currentTimeMillis() - time) + " [ms]");

    }


    @Test
    public void cat_not_exist() {


        IPFS ipfs = TestEnv.getTestInstance(context);
        String cid = "QmUNLLsPACCz1vLxQVkXqqLX5R1X345qqfHbsf67hvA3Nt";


        byte[] content = ipfs.loadData(cid, new TimeoutProgress(10));

        assertNull(content);

    }


    //@Test
    public void cat_test_local() {


        IPFS ipfs = TestEnv.getTestInstance(context);
        //noinspection SpellCheckingInspection
        String cid = "Qme6rRsAb8YCfmQpvDsobZAiWNRefcJw8eFw3WV4pME82V";

        String local = ipfs.storeText("Moin Moin Moin");
        assertNotNull(local);


        byte[] content = ipfs.getData(cid);

        assertNotNull(content);

    }


    @Test
    public void cat_empty() {

        IPFS ipfs = TestEnv.getTestInstance(context);
        String cid = "QmUNLLsPACCz1vLxQVkXqqLX5R1X345qqfHbsf67hvA3Nn";
        List<LinkInfo> res = ipfs.ls(cid, new TimeoutProgress(10));
        assertNotNull(res);

        assertTrue(res.isEmpty());
        byte[] content = ipfs.loadData(cid, new TimeoutProgress(10));

        assertNull(content);

        ipfs.rm(cid, true);

    }
}