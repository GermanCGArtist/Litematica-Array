package dev.schemarray.shape.mc;

import dev.schemarray.shape.GeneratorSettings;
import dev.schemarray.shape.VoxelResult;
import java.util.function.Consumer;

/**
 * Generates off the render thread: request() waits 250 ms for more changes, cancels a job that is still running,
 * runs ShapeGenerator on one background thread and hands the result to onResult on the render thread. Work package B.
 */
public final class PreviewWorker {
    private PreviewWorker() {}

    /** Schedules a (re)generation; only the newest request's result is delivered. */
    public static void request(GeneratorSettings settings, Consumer<VoxelResult> onResult) {
        throw new UnsupportedOperationException("WP B");
    }

    /** Whether a job is waiting or running (the screen shows "Generating..."). */
    public static boolean busy() {
        throw new UnsupportedOperationException("WP B");
    }

    /** Drops pending and running jobs (screen closed). */
    public static void cancel() {
        throw new UnsupportedOperationException("WP B");
    }
}
