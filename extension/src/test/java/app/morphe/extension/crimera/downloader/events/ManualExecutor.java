package app.morphe.extension.crimera.downloader.events;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;

final class ManualExecutor implements Executor {
    private final Queue<Runnable> tasks = new ArrayDeque<>();

    @Override
    public synchronized void execute(Runnable command) {
        tasks.add(command);
    }

    public void runAll() {
        while (true) {
            Runnable task;
            synchronized (this) {
                task = tasks.poll();
            }
            if (task == null) {
                break;
            }
            task.run();
        }
    }

    public synchronized int size() {
        return tasks.size();
    }
}
