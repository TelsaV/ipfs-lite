package io.ipfs.bitswap.peertask;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import io.ipfs.cid.Cid;
import io.libp2p.peer.PeerID;

public class PeerTracker {
    private final PeerID target;
    private final TaskMerger taskMerger;
    private final PriorityQueue<QueueTask> taskQueue;
    public Map<Cid, QueueTask> pendingTasks = new HashMap<>();
    public List<Task> activeTasks = new ArrayList<>();
    public int activeWork;
    public int freezeVal;


    public PeerTracker(@NonNull PeerID peer, @NonNull TaskMerger taskMerger) {
        this.target = peer;
        this.taskMerger = taskMerger;
        this.taskQueue = new PriorityQueue<>((o1, o2) -> {
            if (o1.Target == o2.Target) {
                return Integer.compare(o1.getTask().Priority, o2.getTask().Priority);
            }
            return Long.compare(o1.created, o2.created);
        });
    }

    // TODO check synchronized
    public synchronized void PushTasks(@NonNull List<Task> tasks) {

        long now = System.currentTimeMillis();

        // p.activelk.Lock()
        // defer p.activelk.Unlock()
        for (Task task : tasks) {

            // If the new task doesn't add any more information over what we
            // already have in the active queue, then we can skip the new task
            if (!taskHasMoreInfoThanActiveTasks(task)) {
                continue;
            }
            QueueTask existingTask = pendingTasks.get(task.Topic);
            if (existingTask != null) {
                // If the new task has a higher priority than the old task,
                if (task.Priority > existingTask.getTask().Priority) {
                    // Update the priority and the task's position in the queue
                    existingTask.getTask().Priority = task.Priority;
                    taskQueue.remove(existingTask);
                    taskQueue.offer(existingTask);
                }

                taskMerger.Merge(task, existingTask.getTask());

                // A task with the Topic exists, so we don't need to add
                // the new task to the queue
                continue;
            }

            // Push the new task onto the queue
            QueueTask qTask = new QueueTask(task, target, now);
            pendingTasks.put(task.Topic, qTask);
            taskQueue.offer(qTask);
        }
    }

    private boolean taskHasMoreInfoThanActiveTasks(@NonNull Task task) {
        // Indicates whether the new task adds any more information over tasks that are
        // already in the active task queue
        List<Task> tasksWithTopic = new ArrayList<>();
        for (Task at : activeTasks) {

            if (task.Topic == at.Topic) {
                tasksWithTopic.add(at);
            }
        }
        // No tasks with that topic, so the new task adds information
        if (tasksWithTopic.size() == 0) {
            return true;
        }
        return taskMerger.HasNewInfo(task, tasksWithTopic);

    }

    public boolean Remove(@NonNull Cid cid) {
        QueueTask t = pendingTasks.get(cid);
        if (t != null) {
            pendingTasks.remove(cid);
            taskQueue.remove(t);
            return true;
        }
        return false;
    }
}
