package io.ipfs.bitswap.peertask;

import androidx.annotation.NonNull;

import java.util.List;

public interface TaskMerger {
    static TaskMerger getDefault() {
        return new TaskMerger() {
            @Override
            public boolean HasNewInfo(@NonNull Task task, @NonNull List<Task> existing) {
                return false;
            }

            @Override
            public void Merge(@NonNull Task task, @NonNull Task existing) {

            }
        };
    }

    // HasNewInfo indicates whether the given task has more information than
    // the existing group of tasks (which have the same Topic), and thus should
    // be merged.
    boolean HasNewInfo(@NonNull Task task, @NonNull List<Task> existing);

    // Merge copies relevant fields from a new task to an existing task.
    void Merge(@NonNull Task task, @NonNull Task existing);
}
