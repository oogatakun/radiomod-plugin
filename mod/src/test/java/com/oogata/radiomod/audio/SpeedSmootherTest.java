package com.oogata.radiomod.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpeedSmootherTest {

    @Test
    void clampsTargetToTwoPercent() {
        SpeedSmoother s = new SpeedSmoother();
        s.setTarget(0.5);
        assertEquals(0.02, s.target(), 1e-12);
        s.setTarget(-0.5);
        assertEquals(-0.02, s.target(), 1e-12);
    }

    @Test
    void movesGraduallyAndNeverJumps() {
        SpeedSmoother s = new SpeedSmoother();
        s.setTarget(0.02);
        double prev = s.rate();
        assertEquals(1.0, prev, 1e-12);
        double maxStep = 0;
        for (int i = 0; i < 400; i++) { // 10ms × 400 = 4秒
            double r = s.step(0.01);
            maxStep = Math.max(maxStep, Math.abs(r - prev));
            assertTrue(r >= prev && r <= 1.02 + 1e-12);
            prev = r;
        }
        assertEquals(1.02, prev, 1e-12);
        assertTrue(maxStep <= SpeedSmoother.MAX_SLEW_PER_SECOND * 0.01 + 1e-12, "step=" + maxStep);
    }

    @Test
    void takesAboutTwoSecondsForTwoPercent() {
        SpeedSmoother s = new SpeedSmoother();
        s.setTarget(0.02);
        s.step(1.0);
        assertEquals(1.01, s.rate(), 1e-12);
        s.step(1.0);
        assertEquals(1.02, s.rate(), 1e-12);
    }

    @Test
    void canSlowDownAndReturnToNormal() {
        SpeedSmoother s = new SpeedSmoother();
        s.setTarget(-0.02);
        s.step(5.0);
        assertEquals(0.98, s.rate(), 1e-12);
        s.setTarget(0);
        s.step(5.0);
        assertEquals(1.0, s.rate(), 1e-12);
    }

    @Test
    void resetReturnsToNormalImmediately() {
        SpeedSmoother s = new SpeedSmoother();
        s.setTarget(0.02);
        s.step(5.0);
        s.reset();
        assertEquals(1.0, s.rate(), 1e-12);
        assertEquals(0.0, s.target(), 1e-12);
    }
}
