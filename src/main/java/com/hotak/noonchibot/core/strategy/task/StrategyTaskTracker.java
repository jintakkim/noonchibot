package com.hotak.noonchibot.core.strategy.task;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StrategyTaskTracker {
    private final Map<String, StrategyTask> tasksById = new HashMap<>();

    public void upsert(StrategyTask task) {
        tasksById.put(task.taskId(), task);
    }

    public List<StrategyTask> activeTasks(String strategyId) {
        return tasksById.values().stream()
                .filter(task -> task.strategyId().equals(strategyId))
                .filter(StrategyTask::isActive)
                .toList();
    }

    public Collection<StrategyTask> allTasks() {
        return List.copyOf(tasksById.values());
    }
}
