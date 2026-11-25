package dev.project.anatorius.utils.system;

public class ThreadExecutor {
    public static void execute(Runnable runnable) {
        new OneTimeThread(runnable).start();
    }

    private static class OneTimeThread extends Thread {
        private final Runnable runnable;

        public OneTimeThread(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public void run() {
            try {
                runnable.run();
            } catch (Throwable t) {
                System.err.println("[Anatorius] Background task failed on thread '" + getName() + "':");
                t.printStackTrace();
            }
        }
    }
}
