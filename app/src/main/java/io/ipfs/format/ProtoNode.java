package io.ipfs.format;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.protobuf.ByteString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.ipfs.LogUtils;
import io.ipfs.cid.Builder;
import io.ipfs.cid.Cid;
import merkledag.pb.MerkledagProtos;

public class ProtoNode implements Node {
    private static final String TAG = ProtoNode.class.getSimpleName();
    private final List<Link> links = Collections.synchronizedList(new ArrayList<>());
    public Cid cached;
    private byte[] data;
    private byte[] encoded;
    private Builder mBuilder;

    public ProtoNode() {

        LogUtils.error(TAG, "");
    }

    public ProtoNode(@NonNull byte[] data) {
        this.data = data;
    }

    public void SetCidBuilder(@Nullable Builder builder) {
        if (builder == null) {
            this.mBuilder = v0CidPrefix;
        } else {
            this.mBuilder = builder.WithCodec(Cid.DagProtobuf);
            this.cached = Cid.Undef();
        }
    }

    public void unmarshal(byte[] encoded) {

        try {

            MerkledagProtos.PBNode pbNode = MerkledagProtos.PBNode.parseFrom(encoded);
            List<MerkledagProtos.PBLink> pbLinks = pbNode.getLinksList();
            for (MerkledagProtos.PBLink pbLink : pbLinks) {
                links.add(Link.create(pbLink.getHash().toByteArray(), pbLink.getName(),
                        pbLink.getTsize()));
            }

            links.sort((o1, o2) -> o1.getName().compareTo(o2.getName()));

            this.data = pbNode.getData().toByteArray();

            this.encoded = encoded;

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

    }


    public long Size() {
        byte[] b = EncodeProtobuf(false);
        long size = b.length;
        for (Link link : links) {
            size += link.getSize();
        }
        return size;
    }

    @Override
    public List<Link> getLinks() {
        return new ArrayList<>(links);
    }

    @Override
    public Cid Cid() {
        if (encoded != null && cached.Defined()) {
            return cached;
        }
        byte[] data = RawData();

        if (encoded != null && cached.Defined()) {
            return cached;
        }
        cached = CidBuilder().Sum(data);
        return cached;
    }


    @Override
    public byte[] getData() {
        return data;
    }

    @Override
    public byte[] RawData() {
        return EncodeProtobuf(false);
    }


    // Marshal encodes a *Node instance into a new byte slice.
// The conversion uses an intermediate PBNode.
    private byte[] Marshal() {

        MerkledagProtos.PBNode.Builder pbn = MerkledagProtos.PBNode.newBuilder();

        links.sort((o1, o2) -> o1.getName().compareTo(o2.getName()));// keep links sorted

        synchronized (links) {
            for (Link link : links) {

                MerkledagProtos.PBLink.Builder lnb = MerkledagProtos.PBLink.newBuilder().setName(link.getName())
                        .setTsize(link.getSize());

                if (link.getCid().Defined()) {
                    ByteString hash = ByteString.copyFrom(link.getCid().Bytes());
                    lnb.setHash(hash);
                }

                pbn.addLinks(lnb.build());
            }
        }
        if (this.data.length > 0) {
            pbn.setData(ByteString.copyFrom(this.data));
        }

        return pbn.build().toByteArray();
    }

    private byte[] EncodeProtobuf(boolean force) {

        links.sort((o1, o2) -> o1.getName().compareTo(o2.getName()));// keep links sorted
        if (encoded == null || force) {
            cached = Cid.Undef();
            encoded = Marshal();
        }

        if (!cached.Defined()) {
            cached = CidBuilder().Sum(encoded);
        }

        return encoded;
    }

    private Builder CidBuilder() {
        if (mBuilder == null) {
            mBuilder = v0CidPrefix;
        }
        return mBuilder;
    }

    public Node Copy() {


// Copy returns a copy of the node.
// NOTE: Does not make copies of Node objects in the links.

        ProtoNode nnode = new ProtoNode();

        nnode.data = Arrays.copyOf(getData(), getData().length);


        synchronized (links) {
            if (links.size() > 0) {
                nnode.links.addAll(links);
            }
        }
        nnode.mBuilder = mBuilder;

        return nnode;


    }

    public void RemoveNodeLink(@NonNull String name) {
        encoded = null;
        synchronized (links) {
            for (Link link : links) {
                if (Objects.equals(link.getName(), name)) {
                    links.remove(link);
                    break;
                }
            }
        }
    }

    public void AddNodeLink(@NonNull String name, @NonNull Node link) {

        encoded = null;

        Link lnk = Link.MakeLink(link, name);

        AddRawLink(name, lnk);

    }

    private void AddRawLink(@NonNull String name, @NonNull Link link) {
        encoded = null;

        synchronized (links) {
            links.add(link);
        }
    }

}
