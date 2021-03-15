package io.ipfs.bitswap.peertask;

import androidx.annotation.NonNull;

import io.libp2p.peer.ID;

public class QueueTask {
    public final ID Target;
    public final long created; // created marks the time that the task was added to the queue
    private final Task task;

    public QueueTask(@NonNull Task task, @NonNull ID target, long time) {
        this.task = task;
        this.Target = target;
        this.created = time;
    }

    public Task getTask() {
        return task;
    }
}
