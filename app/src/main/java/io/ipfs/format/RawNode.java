package io.ipfs.format;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import io.ipfs.blocks.Block;
import io.ipfs.cid.Builder;
import io.ipfs.cid.Cid;

public class RawNode implements Node {

    private final Block block;


    public RawNode(@NonNull Block block) {
        this.block = block;
    }

    @Override
    public void SetCidBuilder(@Nullable Builder builder) {
        // TODO
    }

    @Override
    public List<Link> getLinks() {
        return new ArrayList<>();
    }

    @Override
    public Cid Cid() {
        return block.Cid();
    }

    @Override
    public byte[] getData() {
        return block.RawData();
    }

    @Override
    public byte[] RawData() {
        return block.RawData();
    }

}
