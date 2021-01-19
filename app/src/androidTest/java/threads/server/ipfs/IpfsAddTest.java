package threads.server.ipfs;


import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import threads.LogUtils;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertNotEquals;

@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsAddTest {

    private static final String TAG = IpfsAddTest.class.getSimpleName();
    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }

    private byte[] getRandomBytes(int number) {
        return RandomStringUtils.randomAlphabetic(number).getBytes();
    }

    @NonNull
    public File createCacheFile() throws IOException {
        return File.createTempFile("temp", ".cid", context.getCacheDir());
    }

    @Test
    public void add_wrap_test() throws Exception {

        IPFS ipfs = TestEnv.getTestInstance(context);

        int packetSize = 1000;
        long maxData = 1000;
        File inputFile = createCacheFile();
        for (int i = 0; i < maxData; i++) {
            byte[] randomBytes = getRandomBytes(packetSize);
            FileServer.insertRecord(inputFile, i, packetSize, randomBytes);
        }
        long size = inputFile.length();

        LogUtils.error(TAG, "Bytes : " + inputFile.length() / 1000 + "[kb]");

        CID hash58Base = ipfs.storeFile(inputFile);
        assertNotNull(hash58Base);

        List<LinkInfo> links = ipfs.ls(hash58Base, () -> false);
        assertNotNull(links);
        assertEquals(links.size(), 4);

        byte[] bytes = ipfs.getData(hash58Base);
        assertNotNull(bytes);
        assertEquals(bytes.length, size);

        IOUtils.contentEquals(new ByteArrayInputStream(bytes), new FileInputStream(inputFile));


    }

    @Test
    public void add_dir_test() throws Exception {
        IPFS ipfs = TestEnv.getTestInstance(context);


        CID ciddir = ipfs.createEmptyDir();
        assertNotNull(ciddir);

        File cacheDir = new File(context.getCacheDir(), UUID.randomUUID().toString());
        assertTrue(cacheDir.mkdir());

        File inputFile = new File(cacheDir, UUID.randomUUID().toString());
        assertTrue(inputFile.createNewFile());
        for (int i = 0; i < 10; i++) {
            byte[] randomBytes = getRandomBytes(1000);
            FileServer.insertRecord(inputFile, i, 1000, randomBytes);
        }
        long size = inputFile.length();

        CID hash58Base = ipfs.storeFile(cacheDir);
        assertNotNull(hash58Base);

        List<LinkInfo> links = ipfs.ls(hash58Base, () -> false);
        assertNotNull(links);


        assertEquals(links.size(), 1);
        LinkInfo link = links.get(0);
        assertTrue(link.isFile());
        assertEquals(link.getSize(), size);
        CID content = link.getContent();

        ciddir = ipfs.addLinkToDir(ciddir, link.getName(), content);
        assertNotNull(ciddir);

        byte[] bytes = ipfs.getData(content);
        assertNotNull(bytes);
        assertEquals(bytes.length, size);

        IOUtils.contentEquals(new ByteArrayInputStream(bytes), new FileInputStream(inputFile));
        assertEquals(ciddir, hash58Base);

        List<LinkInfo> artLinks = ipfs.ls(hash58Base, () -> false);
        assertNotNull(artLinks);
        assertEquals(artLinks.size(), 1);
        assertEquals(artLinks.get(0), links.get(0));


        // delete parent
        ipfs.rm(ciddir.getCid(), false);

        // check if child is still available
        bytes = ipfs.getData(content);
        assertNotNull(bytes);
        assertEquals(bytes.length, size);

        ipfs.rm(content.getCid(), true);

        bytes = ipfs.getData(content);
        assertNull(bytes);
    }


    @Test
    public void add_test() throws Exception {

        int packetSize = 1000;
        long maxData = 1000;
        IPFS ipfs = TestEnv.getTestInstance(context);

        File inputFile = createCacheFile();
        for (int i = 0; i < maxData; i++) {
            byte[] randomBytes = getRandomBytes(packetSize);
            FileServer.insertRecord(inputFile, i, packetSize, randomBytes);
        }
        long size = inputFile.length();

        LogUtils.error(TAG, "Bytes : " + inputFile.length() / 1000 + "[kb]");

        CID hash58Base = ipfs.storeFile(inputFile);
        assertNotNull(hash58Base);

        List<LinkInfo> links = ipfs.ls(hash58Base, () -> false);
        assertNotNull(links);
        assertEquals(links.size(), 4);
        assertNotEquals(links.get(0).getContent(), hash58Base);

        byte[] bytes = ipfs.getData(hash58Base);
        assertNotNull(bytes);
        assertEquals(bytes.length, size);

        IOUtils.contentEquals(new ByteArrayInputStream(bytes), new FileInputStream(inputFile));

    }


    @Test
    public void add_wrap_small_test() throws Exception {

        int packetSize = 200;
        long maxData = 1000;
        IPFS ipfs = TestEnv.getTestInstance(context);

        File inputFile = createCacheFile();
        for (int i = 0; i < maxData; i++) {
            byte[] randomBytes = getRandomBytes(packetSize);
            FileServer.insertRecord(inputFile, i, packetSize, randomBytes);
        }
        long size = inputFile.length();


        LogUtils.error(TAG, "Bytes : " + inputFile.length() / 1000 + "[kb]");

        CID hash58Base = ipfs.storeFile(inputFile);
        assertNotNull(hash58Base);

        List<LinkInfo> links = ipfs.ls(hash58Base, () -> false);
        assertNotNull(links);
        assertEquals(links.size(), 0);

        byte[] bytes = ipfs.getData(hash58Base);
        assertNotNull(bytes);
        assertEquals(bytes.length, size);


        IOUtils.contentEquals(new ByteArrayInputStream(bytes), new FileInputStream(inputFile));


    }

    //@Test
    public void add_small_test() throws Exception {

        int packetSize = 200;
        long maxData = 1000;
        IPFS ipfs = TestEnv.getTestInstance(context);

        File inputFile = createCacheFile();
        for (int i = 0; i < maxData; i++) {
            byte[] randomBytes = getRandomBytes(packetSize);
            FileServer.insertRecord(inputFile, i, packetSize, randomBytes);
        }
        long size = inputFile.length();

        LogUtils.error(TAG, "Bytes : " + inputFile.length() / 1000 + "[kb]");

        CID hash58Base = ipfs.storeFile(inputFile);
        assertNotNull(hash58Base);

        List<LinkInfo> links = ipfs.ls(hash58Base, () -> false);
        assertNotNull(links);
        assertEquals(links.size(), 0);

        byte[] bytes = ipfs.getData(hash58Base);
        assertNotNull(bytes);
        assertEquals(bytes.length, size);

        IOUtils.contentEquals(new ByteArrayInputStream(bytes), new FileInputStream(inputFile));


    }
}
