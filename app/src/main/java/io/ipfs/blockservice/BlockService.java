package io.ipfs.blockservice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.ipfs.Closeable;
import io.ipfs.blocks.Block;
import io.ipfs.blockstore.Blockstore;
import io.ipfs.cid.Cid;
import io.ipfs.exchange.Fetcher;
import io.ipfs.exchange.Interface;

public interface BlockService extends BlockGetter {


    static BlockService New(@NonNull final Blockstore bs, @NonNull final Interface rem) {
        return new BlockService() {

            @Override
            @Nullable
            public Block GetBlock(@NonNull Closeable closeable, @NonNull Cid cid) {
                return getBlock(closeable, cid, bs, rem);
            }

            @Override
            public void AddBlock(@NonNull Block block) {

                Cid c = block.Cid();

                // hash security
                Cid.ValidateCid(c); // TODO

                /*
                if err != nil {
                    return err
                }
                if s.checkFirst {
                    if has, err := s.blockstore.Has(c); has || err != nil {
                        return err
                    }
                }*/

                bs.Put(block);

                /*
                if exchange != nil {
                    if err := s.exchange.HasBlock(o); err != nil {
                        log.Errorf("HasBlock: %s", err.Error())
                    }
                }*/
            }

            @Nullable
            private Block getBlock(@NonNull Closeable closeable, @NonNull Cid c,
                                   @NonNull Blockstore bs, @NonNull Fetcher fetcher) {
                // err := verifcid.ValidateCid(c) // hash security
        /*if err != nil {
            return nil, err
        }*/

                Block block = bs.Get(c);
                if (block != null) {
                    return block;
                }
        /*
        if err == blockstore.ErrNotFound && fget != nil {

            // TODO be careful checking ErrNotFound. If the underlying
            // implementation changes, this will break.
            log.Debug("Blockservice: Searching bitswap")
            blk, err := fetcher.GetBlock(closeable, c);
            if err != nil {
                if err == blockstore.ErrNotFound {
                    return nil, ErrNotFound
                }
                return nil, err
            }
            log.Event(ctx, "BlockService.BlockFetched", c)
            return blk, nil
        }

        log.Debug("Blockservice GetBlock: Not found")
        if err == blockstore.ErrNotFound {
            return nil, ErrNotFound
        }

        return nil, err*/
                return null;
            }
        };
    }


}
