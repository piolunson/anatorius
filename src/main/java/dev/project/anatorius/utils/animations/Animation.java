package dev.project.anatorius.utils.animations;

public class Animation {
    private float from, to;
    private long startNanos;
    private int duration;
    private Easing.Method easing;

    public Animation(int duration, Easing.Method easing) {
        this(0.0f, 0.0f, duration, easing);
    }

    public Animation(float prev, float current, int duration, Easing.Method easing) {
        this.from = prev;
        this.to = current;
        this.duration = duration;
        this.easing = easing;
        this.startNanos = System.nanoTime();
    }

    private float progress() {
        if (duration <= 0) return 1.0f;
        float t = (System.nanoTime() - startNanos) / (duration * 1_000_000.0f);
        return t <= 0.0f ? 0.0f : Math.min(t, 1.0f);
    }

    public float get() {
        float t = progress();
        if (t >= 1.0f) return to;
        return from + (to - from) * Easing.ease(t, easing);
    }

    public float get(float target) {
        if (this.to != target) animateTo(target);
        return get();
    }

    public void animateTo(float target) {
        from = get();
        to = target;
        startNanos = System.nanoTime();
    }

    public void set(float value) {
        from = value;
        to = value;
        startNanos = System.nanoTime();
    }

    public boolean isFinished() {
        return progress() >= 1.0f;
    }

    public float getCurrent() { return to; }

    public float getPrev() { return from; }

    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }

    public Easing.Method getEasing() { return easing; }
    public void setEasing(Easing.Method easing) { this.easing = easing; }
}
