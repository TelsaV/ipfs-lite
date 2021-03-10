package io.ipfs.blockstore;

import androidx.annotation.NonNull;

import java.util.List;

import io.ipfs.Storage;
import io.ipfs.blocks.BasicBlock;
import io.ipfs.blocks.Block;
import io.ipfs.cid.Cid;
import io.ipfs.datastore.Dshelp;

public interface Blockstore {
    String TAG = Blockstore.class.getSimpleName();
    Block Get(@NonNull Cid cid);

    static Blockstore NewBlockstore(@NonNull final Storage storage){
        return new Blockstore() {
            @Override
            public Block Get(@NonNull Cid cid)  {

                String key = Dshelp.CidToDsKey(cid).String();
                threads.server.core.blocks.Block bdata = storage.getBlock(key);
                if(bdata == null) {
                    return null;
                }
                /*
                if bs.rehash {
                    rbcid, err := k.Prefix().Sum(bdata)
                    if err != nil {
                        return nil, err
                    }

                    if !rbcid.Equals(k) {
                        return nil, bstore.ErrHashMismatch
                    }

                    return blocks.NewBlockWithCid(bdata, rbcid)
                }*/
                return BasicBlock.NewBlockWithCid(cid, bdata.getData());
            }

            @Override
            public void Put(@NonNull Block block) {
                String key = Dshelp.CidToDsKey(block.Cid()).String();
                storage.insertBlock(key, block.RawData());
            }

            public void DeleteBlock(@NonNull Cid cid) {
                String key = Dshelp.CidToDsKey(cid).String();
                storage.deleteBlock(key);
            }

            @Override
            public void DeleteBlocks(@NonNull List<Cid> cids) {
                for (Cid cid : cids) {
                    DeleteBlock(cid);
                }
            }

        };
    }

    void DeleteBlock(@NonNull Cid cid);

    void DeleteBlocks(@NonNull List<Cid> cids);

    void Put(@NonNull Block block);

}


