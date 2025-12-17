/*
 * Copyright 2024-2024 the original author or authors.
 */

package com.taobao.arthas.mcp.server.task;

import com.taobao.arthas.mcp.server.protocol.spec.McpSchema;

import com.taobao.arthas.mcp.server.task.context.TaskContext;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import com.taobao.arthas.mcp.server.protocol.server.McpNettyServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-memory implementation of TaskManager.
 *
 * @author Yeaury
 */
public class InMemoryTaskManager implements TaskManager {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryTaskManager.class);

    private final Map<String, McpSchema.Task> tasks = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<McpSchema.GetTaskResult>> taskFutures = new ConcurrentHashMap<>();
    private final Map<String, TaskContext> taskContexts = new ConcurrentHashMap<>();
    private static final long DEFAULT_TTL = 3600 * 1000L; // 1 hour
    private static final long DEFAULT_POLL_INTERVAL = 1000L; // 1 second
    
    private final ScheduledExecutorService cleanupExecutor;
    
    private McpNettyServerExchange sessionExchange;

    public InMemoryTaskManager() {
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mcp-task-cleanup");
            t.setDaemon(true);
            return t;
        });
        
        // Schedule periodic cleanup
        this.cleanupExecutor.scheduleAtFixedRate(this::cleanupTasks, 1, 1, TimeUnit.MINUTES);
    }

    @Override
    public void setSessionExchange(McpNettyServerExchange exchange) {
        this.sessionExchange = exchange;
    }

    @Override
    public McpSchema.Task createTask(Map<String, Object> meta) {
        String taskId = UUID.randomUUID().toString();
        long now = Instant.now().toEpochMilli();
        
        McpSchema.Task task = new McpSchema.Task(
                taskId,
                "Task created",
                McpSchema.TaskStatus.PENDING,
                now,
                now,
                DEFAULT_TTL,
                DEFAULT_POLL_INTERVAL,
                new HashMap<>(),
                meta
        );
        
        tasks.put(taskId, task);
        
        notifyTaskListChanged();
        
        return task;
    }

    @Override
    public McpSchema.Task getTask(String taskId) {
        return tasks.get(taskId);
    }

    @Override
    public McpSchema.ListTasksResult listTasks(String cursor) {
        // Simple implementation without real pagination for now
        List<McpSchema.Task> allTasks = new ArrayList<>(tasks.values());
        // Sort by created time desc
        allTasks.sort((t1, t2) -> Long.compare(t2.getCreatedAt(), t1.getCreatedAt()));
        
        return new McpSchema.ListTasksResult(allTasks, null, null);
    }

    @Override
    public McpSchema.Task cancelTask(String taskId, String reason) {
        McpSchema.Task task = tasks.get(taskId);
        if (task == null) {
            return null;
        }
        
        // If already completed or cancelled, do nothing
        if (task.getStatus() == McpSchema.TaskStatus.COMPLETED ||
            task.getStatus() == McpSchema.TaskStatus.ERROR ||
            task.getStatus() == McpSchema.TaskStatus.CANCELLED) {
            return task;
        }
        
        // Trigger cancellation handler in context
        TaskContext context = taskContexts.get(taskId);
        if (context != null) {
            try {
                context.cancel();
            } catch (Exception e) {
                logger.warn("Error executing cancellation handler for task {}", taskId, e);
            }
        }

        return updateTask(taskId, McpSchema.TaskStatus.CANCELLED, "Cancelled: " + reason, task.getOutput());
    }
    
    public void registerTaskContext(String taskId, TaskContext context) {
        taskContexts.put(taskId, context);
    }

    @Override
    public McpSchema.Task updateTask(String taskId, McpSchema.TaskStatus status, String statusMessage, Map<String, Object> output) {
        McpSchema.Task currentTask = tasks.get(taskId);
        if (currentTask == null) {
            return null;
        }

        long now = Instant.now().toEpochMilli();
        
        McpSchema.Task updatedTask = new McpSchema.Task(
                currentTask.getTaskId(),
                statusMessage,
                status,
                currentTask.getCreatedAt(),
                now,
                currentTask.getTtl(),
                currentTask.getPollInterval(),
                output != null ? output : currentTask.getOutput(),
                currentTask.getMeta()
        );
        
        tasks.put(taskId, updatedTask);
        
        // Check if task is finished
        if (status == McpSchema.TaskStatus.COMPLETED || status == McpSchema.TaskStatus.ERROR || status == McpSchema.TaskStatus.CANCELLED) {
            completeFuture(updatedTask);
        }
        
        // Send status notification if status changed
        if (currentTask.getStatus() != status) {
            notifyTaskStatusChanged(updatedTask);
        }
        
        return updatedTask;
    }
    
    @Override
    public CompletableFuture<McpSchema.GetTaskResult> waitForTask(String taskId) {
        McpSchema.Task task = tasks.get(taskId);
        if (task == null) {
            CompletableFuture<McpSchema.GetTaskResult> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Task not found: " + taskId));
            return future;
        }

        // If already finished, return immediately
        if (isFinished(task)) {
            return CompletableFuture.completedFuture(toGetTaskResult(task));
        }

        // Return existing future or create new one
        return taskFutures.computeIfAbsent(taskId, k -> new CompletableFuture<>());
    }

    private boolean isFinished(McpSchema.Task task) {
        return task.getStatus() == McpSchema.TaskStatus.COMPLETED ||
               task.getStatus() == McpSchema.TaskStatus.ERROR ||
               task.getStatus() == McpSchema.TaskStatus.CANCELLED;
    }

    private void completeFuture(McpSchema.Task task) {
        CompletableFuture<McpSchema.GetTaskResult> future = taskFutures.remove(task.getTaskId());
        if (future != null) {
            future.complete(toGetTaskResult(task));
        }
    }

    private McpSchema.GetTaskResult toGetTaskResult(McpSchema.Task task) {
        return new McpSchema.GetTaskResult(
                task.getTaskId(), task.getStatusMessage(), task.getStatus(),
                task.getCreatedAt(), task.getLastUpdatedAt(), task.getTtl(),
                task.getPollInterval(), task.getOutput(), task.getMeta()
        );
    }
    
    private void notifyTaskListChanged() {
        if (sessionExchange != null) {
            sessionExchange.sendNotification(McpSchema.METHOD_NOTIFICATION_TASKS_LIST_CHANGED, null);
        }
    }
    
    private void notifyTaskStatusChanged(McpSchema.Task task) {
        if (sessionExchange != null) {
            McpSchema.TaskNotification notification = new McpSchema.TaskNotification(
                    task.getTaskId(),
                    task.getStatus(),
                    task.getStatusMessage(),
                    task.getLastUpdatedAt(),
                    task.getOutput()
            );
            sessionExchange.sendNotification(McpSchema.METHOD_NOTIFICATION_TASKS_STATUS, notification);
        }
    }
    
    private void cleanupTasks() {
        long now = Instant.now().toEpochMilli();
        boolean changed = false;
        
        Iterator<Map.Entry<String, McpSchema.Task>> it = tasks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, McpSchema.Task> entry = it.next();
            McpSchema.Task task = entry.getValue();
            
            // Check TTL
            long expirationTime = task.getCreatedAt() + task.getTtl();
            if (now > expirationTime) {
                // Task expired
                // If it's still running, try to cancel it first to release resources
                if (task.getStatus() == McpSchema.TaskStatus.WORKING || task.getStatus() == McpSchema.TaskStatus.PENDING) {
                    TaskContext context = taskContexts.get(task.getTaskId());
                    if (context != null) {
                        try {
                            context.cancel();
                            logger.info("Cancelled expired task: {}", task.getTaskId());
                        } catch (Exception e) {
                            logger.warn("Error cancelling expired task {}", task.getTaskId(), e);
                        }
                    }
                }
                
                // Remove task
                it.remove();
                
                // Also clean up future if exists
                CompletableFuture<McpSchema.GetTaskResult> future = taskFutures.remove(task.getTaskId());
                if (future != null) {
                    future.completeExceptionally(new TimeoutException("Task expired"));
                }
                
                // Clean up context
                taskContexts.remove(task.getTaskId());
                
                changed = true;
                logger.debug("Removed expired task: {}", task.getTaskId());
            }
        }
        
        if (changed) {
            notifyTaskListChanged();
        }
    }
}
