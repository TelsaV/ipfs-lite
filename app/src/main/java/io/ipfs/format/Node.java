package io.ipfs.format;

import java.util.List;

import io.ipfs.cid.Cid;

public interface Node {
    List<Link> getLinks();

    Cid Cid();

    byte[] getData();

    byte[] RawData();
}
