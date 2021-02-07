package threads.server.utils;

import android.content.Context;
import android.net.Uri;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.URI;
import java.util.Objects;

import threads.LogUtils;
import threads.server.core.Content;
import threads.server.ipfs.IPFS;


public class CodecDecider {

    private static final String TAG = CodecDecider.class.getSimpleName();

    private String multihash = null;
    private String peerID = null;
    private Codec codex = Codec.UNKNOWN;
    private String peerAddress = null;

    private CodecDecider() {
    }

    public static CodecDecider evaluate(@NonNull Context context, @NonNull String code) {
        CodecDecider codecDecider = new CodecDecider();

        IPFS ipfs = IPFS.getInstance(context);

        try {
            Uri uri = Uri.parse(code);
            if (uri != null) {
                if (Objects.equals(uri.getScheme(), Content.IPFS)) {
                    String multihash = uri.getHost();
                    if (ipfs.isValidCID(multihash)) {
                        codecDecider.setMultihash(multihash);
                        codecDecider.setCodex(Codec.IPFS_URI);
                        return codecDecider;
                    }
                } else if (Objects.equals(uri.getScheme(), Content.IPNS)) {
                    String multihash = uri.getHost();

                    codecDecider.setMultihash(multihash);
                        codecDecider.setCodex(Codec.IPNS_URI);
                        return codecDecider;

                }
            }
        } catch (Throwable e) {
            // ignore exception
        }

        try {


            try {
                code = code.trim();
                if (code.startsWith("\"") && code.endsWith("\"")) {
                    code = code.substring(1, code.length() - 1);
                }


                if (code.startsWith("/ip4/") || code.startsWith("/ip6/")) {
                    String peerID = getValidPeerID(ipfs, code);
                    if (peerID != null && !peerID.isEmpty()) {
                        codecDecider.setPeerID(peerID);


                        String address = getMultiAddress(code);
                        if (address != null) {
                            codecDecider.setPeerAddress(address);
                        }

                        codecDecider.setCodex(Codec.MULTIADDRESS);
                        return codecDecider;
                    }
                }
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }

            // check if multihash is valid

            if (ipfs.isValidCID(code)) {
                codecDecider.setMultihash(code);
                codecDecider.setCodex(Codec.MULTIHASH);
                return codecDecider;
            }

            if (URLUtil.isValidUrl(code)) {
                // ok now it is a URI, but is the content is an ipfs multihash

                URI uri = new URI(code);
                String path = uri.getPath();
                if (path.startsWith("/" + Content.IPFS + "/")) {
                    String multihash = path.replaceFirst("/" + Content.IPFS + "/", "");
                    multihash = trim(multihash);
                    if (ipfs.isValidCID(multihash)) {
                        codecDecider.setMultihash(multihash);
                        codecDecider.setCodex(Codec.IPFS_URI);
                        return codecDecider;
                    }
                } else if (path.startsWith("/" + Content.IPNS + "/")) {
                    String multihash = path.replaceFirst("/" + Content.IPNS + "/", "");
                    multihash = trim(multihash);
                    if (ipfs.isValidCID(multihash)) {
                        codecDecider.setMultihash(multihash);
                        codecDecider.setCodex(Codec.IPNS_URI);
                        return codecDecider;
                    }
                }

            }
        } catch (Throwable e) {
            // ignore exception
        }


        codecDecider.setCodex(Codec.UNKNOWN);
        return codecDecider;
    }

    @Nullable
    private static String getMultiAddress(@NonNull String code) {

        try {

            if (!code.isEmpty()) {
                String multiAddress = code;


                if (multiAddress.startsWith("/ip4/") || multiAddress.startsWith("/ip6/")) {

                    if (multiAddress.contains(Content.CIRCUIT)) {
                        return null;
                    }

                    String style = "/p2p/";
                    if (multiAddress.contains(style)) {
                        int index = multiAddress.indexOf(style);
                        return multiAddress.substring(0, index);
                    }


                    if (multiAddress.endsWith("/")) {
                        multiAddress = multiAddress.substring(0, multiAddress.length() - 1);
                    }
                    return multiAddress;
                }
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        return null;
    }

    @Nullable
    private static String getValidPeerID(@NonNull IPFS ipfs, @Nullable String code) {

        if (code != null && !code.isEmpty()) {

            String pid = code;
            try {
                String style = "/p2p/";
                if (code.contains(style)) {
                    int index = code.indexOf(style);
                    pid = code.substring(index + style.length());
                }
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }

            return ipfs.decodeName(pid);

        }

        return null;

    }

    private static String trim(@NonNull String data) {
        int index = data.indexOf("/");
        if (index > 0) {
            return data.substring(0, index);
        }
        return data;
    }

    public String getPeerID() {
        return peerID;
    }

    private void setPeerID(@NonNull String peerID) {
        this.peerID = peerID;
    }

    public String getMultihash() {
        return multihash;
    }

    private void setMultihash(String multihash) {
        this.multihash = multihash;
    }

    public Codec getCodex() {
        return codex;
    }

    private void setCodex(Codec codex) {
        this.codex = codex;
    }

    public String getPeerAddress() {
        return peerAddress;
    }

    private void setPeerAddress(@NonNull String address) {
        this.peerAddress = address;
    }


    public enum Codec {
        UNKNOWN, MULTIHASH, IPFS_URI, IPNS_URI, MULTIADDRESS, P2P_URI
    }
}
