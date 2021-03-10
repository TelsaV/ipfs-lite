package io.ipfs.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import io.ipfs.Closeable;
import io.ipfs.cid.Cid;
import io.ipfs.format.NodeGetter;

public class Resolver {


    public static Cid ResolvePath(@NonNull Closeable ctx, @NonNull NodeGetter dag, @NonNull Path p) {
        /*
        if _, ok := p.(path.Resolved); ok {
            return p.(path.Resolved), nil
        }
        if err := p.IsValid(); err != nil {
            return nil, err
        }*/

        Path ipath = new Path(p.String());

        //var resolveOnce resolver.ResolveOnce
        List<String> paths = ipath.Segments();
        String pre = paths.get(0);
        String content = paths.get(1);
        /*
        switch (pre) {
            case "ipfs":
                resolveOnce = uio.ResolveUnixfsOnce;
            case "ipld":
                resolveOnce = resolver.ResolveSingle;
            default:
                throw new RuntimeException("not resolved");
        }

        r := &resolver.Resolver{
            DAG:         dag,
                    ResolveOnce: resolveOnce,
        }

        node, rest, err := r.ResolveToLastNode(ctx, ipath)
        if err != nil {
            return nil, err
        }

        root, err := cid.Parse(ipath.Segments()[1])
        if err != nil {
            return nil, err
        }

         */

        // return path.NewResolvedPath(ipath, node, root, gopath.Join(rest...))
        return Cid.Decode(content);
    }

    @Nullable
    public static io.ipfs.format.Node ResolveNode(@NonNull Closeable closeable,
                                                  @NonNull NodeGetter nodeGetter,
                                                  @NonNull Path path) {
        Cid cid = ResolvePath(closeable, nodeGetter, path);

        return nodeGetter.Get(closeable, cid);
    }

}
