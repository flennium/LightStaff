package org.flennn.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.function.Consumer;

public final class SchedulerAdapter {
    private final Plugin plugin;
    private final boolean folia;

    public SchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
        this.folia = detectFolia();
    }

    public boolean isFolia() {
        return folia;
    }

    public void runGlobal(Runnable runnable) {
        if (runnable == null) return;
        if (folia) {
            invokeGlobal("execute", new Class<?>[]{Plugin.class, Runnable.class}, plugin, runnable);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    public void runEntity(Entity entity, Runnable runnable) {
        if (runnable == null) return;
        if (entity == null || !folia) {
            runGlobal(runnable);
            return;
        }

        try {
            Object scheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
            Method execute = scheduler.getClass().getMethod("execute", Plugin.class, Runnable.class, Runnable.class, long.class);
            execute.invoke(scheduler, plugin, runnable, null, 1L);
        } catch (ReflectiveOperationException ex) {
            Console.warn("Folia entity scheduler failed, using global scheduler: " + ex.getMessage());
            runGlobal(runnable);
        }
    }

    public TaskHandle runGlobalRepeating(Runnable runnable, long initialDelayTicks, long periodTicks) {
        if (runnable == null) return TaskHandle.empty();
        long initialDelay = Math.max(1L, initialDelayTicks);
        long period = Math.max(1L, periodTicks);

        if (folia) {
            Object task = invokeGlobal("runAtFixedRate",
                    new Class<?>[]{Plugin.class, Consumer.class, long.class, long.class},
                    plugin,
                    (Consumer<Object>) ignored -> runnable.run(),
                    initialDelay,
                    period);
            return new ReflectiveTaskHandle(task);
        }

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, initialDelay, period);
        return task::cancel;
    }

    public void shutdown() {
    }

    private Object invokeGlobal(String method, Class<?>[] types, Object... args) {
        try {
            Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            return scheduler.getClass().getMethod(method, types).invoke(scheduler, args);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Could not use Folia global scheduler method " + method, ex);
        }
    }

    private boolean detectFolia() {
        try {
            Bukkit.class.getMethod("getGlobalRegionScheduler");
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public interface TaskHandle {
        void cancel();

        static TaskHandle empty() {
            return () -> {
            };
        }
    }

    private record ReflectiveTaskHandle(Object task) implements TaskHandle {
        @Override
        public void cancel() {
            if (task == null) return;
            try {
                task.getClass().getMethod("cancel").invoke(task);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }
}
