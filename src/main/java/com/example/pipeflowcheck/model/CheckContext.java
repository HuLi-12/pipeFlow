package com.example.pipeflowcheck.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
public class CheckContext {
    private CheckTask task;
    private List<String> path = new ArrayList<>();
    private Set<String> visited = new HashSet<>();
    private boolean enteredSpecialChannel = false;

    public CheckContext copy() {
        CheckContext copy = new CheckContext();
        copy.setTask(this.task);
        copy.setPath(new ArrayList<>(this.path));
        copy.setVisited(new HashSet<>(this.visited));
        copy.setEnteredSpecialChannel(this.enteredSpecialChannel);
        return copy;
    }
}
