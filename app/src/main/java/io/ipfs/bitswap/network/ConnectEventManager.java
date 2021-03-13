package io.ipfs.bitswap.network;

import androidx.annotation.NonNull;

import java.util.HashMap;

import io.libp2p.peer.ID;

public class ConnectEventManager {
    private final ConnectionListener connectionListener;
    private final HashMap<ID, ConnState> conns = new HashMap<>();

    public ConnectEventManager(@NonNull ConnectionListener connectionListener) {
        this.connectionListener = connectionListener;
    }

    public synchronized void Connected(@NonNull ID p) {

        ConnState state = conns.get(p);
        if (state == null) {
            state = new ConnState();
            state.responsive = true;
            conns.put(p, state);
        }
        state.refs++;

        if (state.refs == 1 && state.responsive) {
            connectionListener.PeerConnected(p);
        }
    }

    public synchronized void Disconnected(@NonNull ID p) {

        ConnState state = conns.get(p);
        if (state == null) {
            // Should never happen
            return;
        }
        state.refs--;

        if (state.refs == 0) {
            if (state.responsive) {
                connectionListener.PeerDisconnected(p);
            }
            conns.remove(p);
        }
    }

    public synchronized void MarkUnresponsive(@NonNull ID p) {
        ConnState state = conns.get(p);
        if (state == null || !state.responsive) {
            return;
        }

        state.responsive = false;

        connectionListener.PeerDisconnected(p);
    }

    public synchronized void OnMessage(@NonNull ID p) {
        // This is a frequent operation so to avoid different message arrivals
        // getting blocked by a write lock, first take a read lock to check if
        // we need to modify state
        ConnState state = conns.get(p);
        if (state == null || state.responsive) {
            return;
        }


        state.responsive = true;
        connectionListener.PeerConnected(p);
    }

    private static class ConnState {
        public int refs;
        public boolean responsive;
    }

}
