package com.tystr.delta;

import com.motivewave.platform.sdk.common.Tick;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeltaBar {
    private final Map<Float, Integer> deltasByPrice;
    private final int delta;

    public DeltaBar(Map<Float, Integer> deltasByPrice) {
        this.deltasByPrice = deltasByPrice;
        this.delta = calcDelta();
    }

    public Map<Float, Integer> getDeltasByPrice() {
        return deltasByPrice;
    }
    public int getDelta() {
        return delta;
    }
    public float getDeltaPOC() {
        return Collections.max(deltasByPrice.entrySet(), Map.Entry.comparingByValue()).getKey();
    }
    private int calcDelta() {
        return deltasByPrice.values().stream().mapToInt(Integer::valueOf).sum();
    }

    public static DeltaBar getDeltaForTicksAsDeltaBar(List<Tick> ticks) {
        Map<Float, Integer> deltasByPrice = new HashMap<Float, Integer>();
        for (Tick tick : ticks) {
            if (tick.isAskTick()) {
                int deltaAtPrice = deltasByPrice.getOrDefault(tick.getAskPrice(), 0);
                deltaAtPrice += tick.getVolume();
                deltasByPrice.put(tick.getAskPrice(), deltaAtPrice);
            } else {
                int deltaAtPrice = deltasByPrice.getOrDefault(tick.getBidPrice(), 0);
                deltaAtPrice -= tick.getVolume();
                deltasByPrice.put(tick.getBidPrice(), deltaAtPrice);
            }
        }

        return new DeltaBar(deltasByPrice);
    }
}