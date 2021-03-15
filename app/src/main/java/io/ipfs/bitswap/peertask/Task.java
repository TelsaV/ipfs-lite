package io.ipfs.bitswap.peertask;

import androidx.annotation.NonNull;

import io.ipfs.cid.Cid;

public class Task {
    // Topic for the task
    public Cid Topic;
    // Priority of the task
    public int Priority;
    // The size of the task
    // - peers with most active work are deprioritized
    // - peers with most pending work are prioritized
    public int Work;
    // Arbitrary data associated with this Task by the client
    public Object Data;


    public Task(@NonNull Cid topic, int priority, int work, @NonNull Object data) {
        this.Topic = topic;
        this.Priority = priority;
        this.Work = work;
        this.Data = data;
    }

}

