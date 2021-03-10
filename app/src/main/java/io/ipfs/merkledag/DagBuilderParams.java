package io.ipfs.merkledag;

import androidx.annotation.NonNull;

import io.ipfs.cid.Builder;
import io.ipfs.utils.Splitter;

public class DagBuilderParams {
    private final DagService dagService;
    private final Builder builder;
    private final boolean rawLeaves;


    public DagBuilderParams(@NonNull DagService dagService,
                            @NonNull Builder builder,
                            boolean rawLeaves) {
        this.dagService = dagService;
        this.builder = builder;
        this.rawLeaves = rawLeaves;

    }

    public DagBuilderHelper New(@NonNull Splitter splitter) {

        return new DagBuilderHelper(dagService, builder,
                splitter, rawLeaves);
    }

}
