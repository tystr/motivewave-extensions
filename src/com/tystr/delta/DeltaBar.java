package com.tystr.delta;

import com.motivewave.platform.sdk.common.Tick;

import java.util.*;

/**
 * This class provides convenience methods for interacting with delta over a series of prices, such as a candlestick or
 * price bar.
 *
 * @author Tyler Stroud
 */
public class DeltaBar {
    private final Map<Float, Integer> deltasByPrice;
    private int volume = 0;

    public DeltaBar(Map<Float, Integer> deltasByPrice) {
        this.deltasByPrice = deltasByPrice;
    }

    public DeltaBar() {
        this.deltasByPrice = new TreeMap<>();
        volume = 0;
    }

    /**
     *
     * @param price price is a float representing the price at which the volume was traded. Trades executed at the bid
     *              are market sell orders.
     * @param volumeAtBid volumeAtBid is an integer representing the volume to be added
     */
    public void addVolumeAtBid(float price, int volumeAtBid) {
        int deltaAtPrice = deltasByPrice.getOrDefault(price, 0);
        deltaAtPrice -= volumeAtBid;
        deltasByPrice.put(price, deltaAtPrice);
        volume += volumeAtBid;
    }

    /**
     *
     * @param price price is a float representing the price at which the volume was traded. Trades executed at the ask
     *              are market buy orders.
     * @param volumeAtAsk volumeAtAsk is an integer representing the volume to be added
     */
    public void addVolumeAtAsk(float price, int volumeAtAsk) {
        int deltaAtPrice = deltasByPrice.getOrDefault(price, 0);
        deltaAtPrice += volumeAtAsk;
        deltasByPrice.put(price, deltaAtPrice);
        volume += volumeAtAsk;
    }

    /**
     * @return integer representing the total volume
     */
    public int getVolume() {
        return volume;
    }

    /**
     * @return A float representing the delta percent
     */
    public float getDeltaPercent() {
        return (float) getDelta() / (float) getVolume();
    }

    /**
     * @return true if deltasByPrice has no mapped entries
     */
    public boolean isEmpty() {
        return deltasByPrice.isEmpty();
    }

    /**
     *
     * @return A float representing the price where the max delta occurred
     */
    public float getMaxDelta() {
        return Collections.max(deltasByPrice.entrySet(), Map.Entry.comparingByValue()).getKey();
    }

    /**
     * @return A float representing the price where the min (or max negative) delta occurred
     */
    public float getMinDelta() {
        return Collections.min(deltasByPrice.entrySet(), Map.Entry.comparingByValue()).getKey();
    }

    /**
     *
     * @return A float representing the price where the max absolute delta occurred
     */
    public float getMaxAbsoluteDelta() {
        return Collections.max(deltasByPrice.entrySet(), Map.Entry.comparingByValue((a, b) -> (Math.max(Math.abs(a), Math.abs(b))))).getKey();
    }

    /**
     * @return An integer representing the sum of the total delta for this bar
     */
    public int getDelta() {
        return deltasByPrice.values().stream().mapToInt(Integer::valueOf).sum();
    }

    /**
     *
     * @return A float representing the price that contains the highest delta
     */
    public float getDeltaPOC() {
        return Collections.max(deltasByPrice.entrySet(), Map.Entry.comparingByValue()).getKey();
    }
}