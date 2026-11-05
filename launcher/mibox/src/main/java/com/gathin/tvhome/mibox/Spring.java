package com.gathin.tvhome.mibox;

/**
 * A damped spring of unit mass, stepped by the frame clock: the volume bar's motion (how it opens, fills,
 * stretches and pulses). Plain Java, so its feel is tested on the JVM. An underdamped spring (damping below
 * 2 * sqrt(stiffness)) overshoots its target and settles back, which is the bounce the bar is built on.
 */
final class Spring {
    /** Integration step: small enough to stay stable at the stiffest spring used here. */
    private static final float STEP_S = 0.004f;
    private static final float REST_DISTANCE = 5e-4f;
    private static final float REST_SPEED = 5e-3f;

    final float stiffness;
    final float damping;
    float value;
    float velocity;
    float target;

    Spring(float stiffness, float damping, float value) {
        this.stiffness = stiffness;
        this.damping = damping;
        this.value = value;
        this.target = value;
    }

    /** Advances by dt seconds; true while it still moves. At rest it sits exactly on its target. */
    boolean step(float dt) {
        if (dt <= 0) return !atRest();
        int n = Math.max(1, (int) Math.ceil(dt / STEP_S));
        float h = dt / n;
        for (int i = 0; i < n; i++) {
            float a = -stiffness * (value - target) - damping * velocity;
            velocity += a * h;
            value += velocity * h;
        }
        if (atRest()) {
            snap();
            return false;
        }
        return true;
    }

    boolean atRest() {
        return Math.abs(value - target) < REST_DISTANCE && Math.abs(velocity) < REST_SPEED;
    }

    /** Jumps to the target: used when animations are off in Android's settings. */
    void snap() {
        value = target;
        velocity = 0;
    }
}
