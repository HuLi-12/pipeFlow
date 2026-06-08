package com.example.pipeflowcheck.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
public class CheckContext {
    private CheckTask task;
    private List<String> nodePath = new ArrayList<>();
    private List<String> channelPath = new ArrayList<>();
    private Set<String> visited = new HashSet<>();
    private boolean enteredSpecialChannel = false;
    /** shared counter for max path limit across all DFS branches */
    private int[] pathCounter;

    public CheckContext copy() {
        CheckContext copy = new CheckContext();
        copy.setTask(this.task);
        copy.setNodePath(new ArrayList<>(this.nodePath));
        copy.setChannelPath(new ArrayList<>(this.channelPath));
        copy.setVisited(new HashSet<>(this.visited));
        copy.setEnteredSpecialChannel(this.enteredSpecialChannel);
        copy.setPathCounter(this.pathCounter);
        return copy;
    }
}
