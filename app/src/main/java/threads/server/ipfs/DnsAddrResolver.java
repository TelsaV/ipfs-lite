package threads.server.ipfs;

import android.util.Pair;

import androidx.annotation.NonNull;

import org.minidns.DnsClient;
import org.minidns.cache.LruCache;
import org.minidns.dnsmessage.DnsMessage;
import org.minidns.dnsqueryresult.DnsQueryResult;
import org.minidns.record.Data;
import org.minidns.record.Record;
import org.minidns.record.TXT;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import threads.LogUtils;

public class DnsAddrResolver {
    public static final String DNS_LINK = "dnslink=";
    private static final String LIB2P = "_dnsaddr.bootstrap.libp2p.io";
    private static final String DNS_ADDR = "dnsaddr=/dnsaddr/";
    private static final String IPv4 = "/ip4/";
    private static final String IPv6 = "/ip6/";
    private static final String TAG = DnsAddrResolver.class.getSimpleName();


    @NonNull
    private static final List<String> Bootstrap = new ArrayList<>(Arrays.asList(
            "/ip4/147.75.80.110/tcp/4001/p2p/QmbFgm5zan8P6eWWmeyfncR5feYEMPbht5b1FW1C37aQ7y", // default relay  libp2p
            "/ip4/147.75.195.153/tcp/4001/p2p/QmW9m57aiBDHAkKj9nmFSEn7ZqrcF1fZS4bipsTCHburei",// default relay  libp2p
            "/ip4/147.75.70.221/tcp/4001/p2p/Qme8g49gm3q4Acp7xWBKg3nAa9fxZ1YmyDJdyGgoG6LsXh",// default relay  libp2p

            "/ip4/104.131.131.82/tcp/4001/p2p/QmaCpDMGvV2BGHeYERUEnRQAwe3N8SzbUtfsmvsqQLuvuJ"// mars.i.ipfs.io

    ));

    static Pair<List<String>, List<String>> getBootstrap() {

        Pair<List<String>, List<String>> result = DnsAddrResolver.getMultiAddresses();

        List<String> bootstrap = new ArrayList<>(result.first);
        bootstrap.addAll(Bootstrap);
        return Pair.create(bootstrap, result.second);
    }


    @NonNull
    public static String getDNSLink(@NonNull String host) {

        List<String> txtRecords = getTxtRecords("_dnslink.".concat(host));
        for (String txtRecord : txtRecords) {
            try {
                if (txtRecord.startsWith(DNS_LINK)) {
                    return txtRecord.replaceFirst(DNS_LINK, "");
                }
            } catch (Throwable e) {
                LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
            }
        }
        return "";
    }

    @NonNull
    private static List<String> getTxtRecords(@NonNull String host) {
        List<String> txtRecords = new ArrayList<>();
        try {
            DnsClient client = new DnsClient(new LruCache(0));
            DnsQueryResult result = client.query(host, Record.TYPE.TXT);
            DnsMessage response = result.response;
            List<Record<? extends Data>> records = response.answerSection;
            for (Record<? extends Data> record : records) {
                TXT text = (TXT) record.getPayload();
                txtRecords.add(text.getText());
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
        }
        return txtRecords;
    }


    @NonNull
    static Pair<List<String>, List<String>> getMultiAddresses() {

        List<String> multiAddresses = new ArrayList<>();
        List<String> p2pAddresses = new ArrayList<>();
        Pair<List<String>, List<String>> result = new Pair<>(multiAddresses, p2pAddresses);

        List<String> txtRecords = getTxtRecords();
        for (String txtRecord : txtRecords) {
            try {
                if (txtRecord.startsWith(DNS_ADDR)) {
                    String multiAddress = txtRecord.replaceFirst(DNS_ADDR, "");
                    // now get IP of multiAddress
                    String host = multiAddress.substring(0, multiAddress.indexOf("/"));

                    if (!host.isEmpty()) {
                        String data = multiAddress.substring(host.length());
                        InetAddress address = InetAddress.getByName(host);
                        String ip = IPv4;
                        if (address instanceof Inet6Address) {
                            ip = IPv6;
                        }
                        String hostAddress = address.getHostAddress();

                        if (!data.startsWith("/p2p/")) {
                            String newAddress = hostAddress.concat(data);
                            multiAddresses.add(ip.concat(newAddress));
                        } else {
                            p2pAddresses.add(data);
                        }
                    }
                }
            } catch (Throwable e) {
                LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
            }
        }
        return result;
    }


    @NonNull
    private static List<String> getTxtRecords() {
        List<String> txtRecords = new ArrayList<>();
        try {
            DnsClient client = new DnsClient(new LruCache(0));
            DnsQueryResult result = client.query(LIB2P, Record.TYPE.TXT);
            DnsMessage response = result.response;
            List<Record<? extends Data>> records = response.answerSection;
            for (Record<? extends Data> record : records) {
                TXT text = (TXT) record.getPayload();
                txtRecords.add(text.getText());
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
        }
        return txtRecords;
    }
}
