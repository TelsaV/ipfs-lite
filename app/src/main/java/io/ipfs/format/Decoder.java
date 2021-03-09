package io.ipfs.format;

import androidx.annotation.NonNull;

import io.ipfs.blocks.Block;
import io.ipfs.cid.Cid;

public class Decoder {
    @NonNull
    public static Node Decode(@NonNull Block block) {

        if (block instanceof Node) {
            return (Node) block;
        }

        long type = block.Cid().Type();

        if (type == Cid.DagProtobuf) {
            return DecodeProtobufBlock(block);
        } else if (type == Cid.Raw) {
            return DecodeRawBlock(block);
        } else if (type == Cid.DagCBOR) {
            throw new RuntimeException("Not supported decoder");
        } else {
            throw new RuntimeException("Not supported decoder");
        }
    }


    // DecodeRawBlock is a block decoder for raw IPLD nodes conforming to `node.DecodeBlockFunc`.
    public static Node DecodeRawBlock(@NonNull Block block) {
        if (block.Cid().Type() != Cid.Raw) {
            throw new RuntimeException("raw nodes cannot be decoded from non-raw blocks");
        }
        // Once you "share" a block, it should be immutable. Therefore, we can just use this block as-is.
        return new RawNode(block);
    }


    // DecodeProtobufBlock is a block decoder for protobuf IPLD nodes conforming to
// node.DecodeBlockFunc
    public static Node DecodeProtobufBlock(@NonNull Block b) {
        Cid c = b.Cid();
        if (c.Type() != Cid.DagProtobuf) {
            throw new RuntimeException("this function can only decode protobuf nodes");
        }

        ProtoNode decnd = DecodeProtobuf(b.RawData());
        decnd.cached = c;
        // TODO decnd.builder = c.Prefix();
        return decnd;
    }


    // DecodeProtobuf decodes raw data and returns a new Node instance.
    public static ProtoNode DecodeProtobuf(byte[] encoded) {
        ProtoNode n = new ProtoNode();
        n.unmarshal(encoded);
        return n;
    }

    /*

    func (d *safeBlockDecoder) Decode(block blocks.Block) (Node, error) {
        // Short-circuit by cast if we already have a Node.
        if node, ok := block.(Node); ok {
            return node, nil
        }

        ty := block.Cid().Type()

        d.lock.RLock()
        decoder, ok := d.decoders[ty]
        d.lock.RUnlock()

        if ok {
            return decoder(block)
        } else {
            // TODO: get the *long* name for this format
            return nil, fmt.Errorf("unrecognized object type: %d", ty)
        }
    }*/
}
