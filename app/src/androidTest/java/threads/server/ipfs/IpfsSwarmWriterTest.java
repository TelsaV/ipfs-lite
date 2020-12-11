package threads.server.ipfs;


import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import lite.PeerInfo;
import threads.LogUtils;

import static junit.framework.TestCase.assertNotNull;

@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsSwarmWriterTest {

    private static final String TAG = IpfsSwarmWriterTest.class.getSimpleName();

    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }


    private static String getRandomString(int number) {
        return "" + RandomStringUtils.randomAscii(number);
    }


    @Test
    public void dummy() {
        assertNotNull(context);
    }

    //@Test
    public void write() throws Exception {
        String RELAY_PID = "QmWFhiem9PnRAm9pBHQYvRqQcGAeJ2VfSFhD3JKdytiWKG";

        IPFS ipfs = TestEnv.getTestInstance(context);
        LogUtils.error(TAG, "Connecting to RELAY ...");
        boolean success = ipfs.swarmConnect(RELAY_PID, 10);
        LogUtils.error(TAG, "Connecting to RELAY done " + success);


        PeerInfo info = ipfs.id();
        LogUtils.error(TAG, "PID : " + Objects.requireNonNull(info).getID());


        File file = createRandomFile();

        CID hash58Base = ipfs.storeFile(file);
        LogUtils.error(TAG, "CID : " + Objects.requireNonNull(hash58Base).getCid());


        try {

            info = ipfs.id();
            assertNotNull(info);
            LogUtils.error(TAG, info.toString());


        } catch (Throwable e) {
            LogUtils.error(TAG, "" + e.getLocalizedMessage());
        }
    }

    public File createCacheFile() throws IOException {
        return File.createTempFile("temp", ".cid", context.getCacheDir());
    }

    private File createRandomFile() throws Exception {


        int packetSize = 1000;
        long maxData = 10;


        File inputFile = createCacheFile();
        String randomString = getRandomString(packetSize);
        for (int i = 0; i < maxData; i++) {
            FileServer.insertRecord(inputFile, i, packetSize, randomString.getBytes());
        }

        return inputFile;
    }
}
