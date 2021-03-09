package io.ipfs.path;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import io.ipfs.cid.Cid;

public class Path {
    private final String path;
    public Path(@NonNull String path){
        this.path = path;
    }

    public static Path New(@NonNull String p){
        return ParsePath(p);
    }


    // ParseCidToPath takes a CID in string form and returns a valid ipfs Path.
    public static Path ParseCidToPath(String txt) {
        if( txt.isEmpty()) {
            throw new RuntimeException("path is empty");
        }

        Cid c = Cid.Decode(txt);

        return FromCid(c);
    }


    // FromCid safely converts a cid.Cid type to a Path type.
    public static Path FromCid(@NonNull Cid cid) {
        return new Path("/ipfs/" + cid.String());
    }

    public static Path ParsePath(@NonNull String txt){
        String[] parts = txt.split("/");

        if( parts.length == 1 ) {
            return ParseCidToPath(txt);
        }
        /*
        // if the path doesnt begin with a '/'
        // we expect this to start with a hash, and be an 'ipfs' path
        if parts[0] != "" {
            if _, err := decodeCid(parts[0]); err != nil {
                return "", &pathError{error: err, path: txt}
            }
            // The case when the path starts with hash without a protocol prefix
            return Path("/ipfs/" + txt), nil
        }

        if len(parts) < 3 {
            return "", &pathError{error: fmt.Errorf("path does not begin with '/'"), path: txt}
        }

        //TODO: make this smarter
        switch parts[1] {
            case "ipfs", "ipld":
                if parts[2] == "" {
                return "", &pathError{error: fmt.Errorf("not enough path components"), path: txt}
            }
            // Validate Cid.
            _, err := decodeCid(parts[2])
            if err != nil {
                return "", &pathError{error: fmt.Errorf("invalid CID: %s", err), path: txt}
            }
            case "ipns":
                if parts[2] == "" {
                return "", &pathError{error: fmt.Errorf("not enough path components"), path: txt}
            }
            default:
                return "", &pathError{error: fmt.Errorf("unknown namespace %q", parts[1]), path: txt}
        }
        */
        return new Path(txt);
    }


    public List<String> Segments(){
        String[] spits = path.split("/");
        List<String> result = new ArrayList<>();
        for (String split:spits) {
            if(!split.isEmpty()){
                result.add(split);
            }
        }
        return result;
    }
    public String String() {
        return path;
    }
}
