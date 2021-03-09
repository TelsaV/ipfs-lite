package io.ipfs.format;

import com.google.protobuf.ByteString;

import java.util.ArrayList;
import java.util.List;

import merkledag.pb.MerkledagProtos;
import io.ipfs.LogUtils;
import io.ipfs.cid.Cid;

public class ProtoNode implements Node {
    private static final String TAG = ProtoNode.class.getSimpleName();
    private final List<Link> links = new ArrayList<>();
    public Cid cached;
    private ByteString data;

    public void unmarshal(byte[] encoded) {

        try {

            MerkledagProtos.PBNode pbNode = MerkledagProtos.PBNode.parseFrom(encoded);
            List<MerkledagProtos.PBLink> pbLinks = pbNode.getLinksList();
            for (MerkledagProtos.PBLink pbLink : pbLinks) {
                links.add(Link.create(pbLink.getHash().toByteArray(), pbLink.getName(),
                        pbLink.getTsize()));
            }

            links.sort((o1, o2) -> o1.getName().compareTo(o2.getName()));

            data = pbNode.getData();

            //n.encoded = encoded
            //return nil
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

    }

    @Override
    public List<Link> getLinks() {
        return links;
    }

    @Override
    public Cid Cid() {
        return cached;
    }

    @Override
    public byte[] getData() {
        return data.toByteArray();
    }

    @Override
    public byte[] RawData() {

        // TODO
        return new byte[0];
    }
}
