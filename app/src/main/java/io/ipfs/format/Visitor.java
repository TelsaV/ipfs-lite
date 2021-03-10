package io.ipfs.format;

import androidx.annotation.NonNull;

import java.util.Stack;

public class Visitor {
    private final Stack<Stage> stack = new Stack<>();
    private boolean rootVisited = false;

    public Visitor(@NonNull NavigableNode root) {
        rootVisited = false;
        pushActiveNode(root);
    }

    public void reset(@NonNull Stack<Stage> stages) {
        stack.clear();
        stack.addAll(stages);
        rootVisited = true;
    }

    @NonNull
    NavigableNode getActiveNode() {
        return stack.peek().getNode();
    }

    public void pushActiveNode(@NonNull NavigableNode node) {
        stack.push(new Stage(node));
    }


    public void popStage() {
        stack.pop();
    }

    public Stage peekStage() {
        return stack.peek();
    }

    public boolean isRootVisited(boolean visited) {
        boolean temp = rootVisited;
        rootVisited = visited;
        return temp;
    }


    public boolean isEmpty() {
        return stack.isEmpty();
    }
}
