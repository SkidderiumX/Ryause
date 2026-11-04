package omg.depchaivl.ryause.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Executor {
    public static final ExecutorService POOL = Executors.newCachedThreadPool();

    private Executor() {
    }
}
