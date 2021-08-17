package com.tystr;

import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * This class provides a data structure and convenience methods for interacting with Volume Profile data. A volume
 * profile is essentially a structure of volume by price within which a value area can be calculated.
 *
 * @author Tyler Stroud
 */
public class VolumeProfile {
    private static final float defaultTickSize = 0.25f;
    private static final float defaultValueAreaPercent = 0.682f;

    private final SortedMap<Float, Integer> volumeByPrice;
    private final SortedMap<Float, Integer> valueArea;
    private float valueAreaPercent;
    private float tickSize;
    private boolean isValueAreaCalculated = false;

    public VolumeProfile() {
        volumeByPrice = new TreeMap<>();
        valueArea = new TreeMap<>();
        valueAreaPercent = defaultValueAreaPercent;
        this.tickSize = defaultTickSize;
    }

    /**
     *
     * @param valueAreaPercent valueAreaPercent is a float representing the percent of volume to measure as the value
     *                         area
     */
    public VolumeProfile(float valueAreaPercent) {
        volumeByPrice = new TreeMap<>();
        valueArea = new TreeMap<>();
        this.valueAreaPercent = valueAreaPercent;
        this.tickSize = defaultTickSize;
    }

    /**
     * This method allows configuring the percent of total volume to use when calculating the value area.
     *
     * @param valueAreaPercent valueAreaPercent is a float representing the percent of volume to measure as the value
     *                         area
     */
    public void setValueAreaPercent(float valueAreaPercent) {
        if (this.valueAreaPercent == valueAreaPercent) return;
        this.valueAreaPercent = valueAreaPercent;
        clearValueArea();
    }

    /**
     *
     * @param tickSize tickSize is used as the interval to increment up and down when constructing the value area.
     */
    public void setTickSize(float tickSize) {
        this.tickSize = tickSize;
    }

    /**
     * This method adds the given volume to the volume already mapped at the specified price
     *
     * @param price price at which to add volume
     * @param volume volume to be added at the specified price
     */
    public void addVolumeAtPrice(float price, int volume) {
        int volumeAtPrice = volumeByPrice.getOrDefault(price, 0);
        volumeAtPrice += volume;
        volumeByPrice.put(price, volumeAtPrice);
        isValueAreaCalculated = false;
    }

    /**
     * Removes all volume by price and value area data
     */
    public void clear() {
        volumeByPrice.clear();
        valueArea.clear();
        isValueAreaCalculated = false;
    }

    /**
     * Removes value area data
     */
    public void clearValueArea() {
        valueArea.clear();
        isValueAreaCalculated = false;
    }

    /**
     * @return {@code true} if volume profile contains no volume by price mappings
     */
    public boolean isEmpty() {
        return volumeByPrice.isEmpty();
    }

    /**
     * This method calculates the value area.
     */
    public void calculateValueArea() {
        valueArea.clear();
        if (volumeByPrice.isEmpty()) return;


        // Add volume POC to value area
        float volumePOC = getPointOfControl();
        valueArea.put(volumePOC, volumeByPrice.get(volumePOC));

        int runningVolume = 0;

        float abovePrice1 = volumePOC + tickSize;
        float abovePrice2 = volumePOC + (tickSize * 2);
        float belowPrice1 = volumePOC; //- interval;
        float belowPrice2 = volumePOC; // - (interval * 2);
        boolean incrementAbove = false;
        int aboveIncrements = -2;
        int belowIncrements = -2;
        int totalVolume = getVolume();

        for (int i = 1; i <= volumeByPrice.size(); i++) {
            if (incrementAbove) {
                aboveIncrements = aboveIncrements + 2;
                abovePrice1 = volumePOC + (tickSize * (aboveIncrements + 1));
                abovePrice2 = volumePOC + (tickSize * (aboveIncrements + 2));
            } else {
                belowIncrements = belowIncrements + 2;
                belowPrice1 = volumePOC - (tickSize * (belowIncrements + 1));
                belowPrice2 = volumePOC - (tickSize * (belowIncrements + 2));
            }

            int abovePrice1Volume = volumeByPrice.getOrDefault(abovePrice1, 0);
            int abovePrice2Volume = volumeByPrice.getOrDefault(abovePrice2, 0);
            int belowPrice1Volume = volumeByPrice.getOrDefault(belowPrice1, 0);
            int belowPrice2Volume = volumeByPrice.getOrDefault(belowPrice2, 0);

            int aboveSum = abovePrice1Volume + abovePrice2Volume;
            int belowSum = belowPrice1Volume + belowPrice2Volume;

            if (aboveSum > belowSum) {
                incrementAbove = true;
                valueArea.put(abovePrice1, abovePrice1Volume);
                valueArea.put(abovePrice2, abovePrice2Volume);
                runningVolume += abovePrice1Volume + abovePrice2Volume;
            } else {
                incrementAbove = false;
                valueArea.put(belowPrice1, belowPrice1Volume);
                valueArea.put(belowPrice2, belowPrice2Volume);
                runningVolume += belowPrice1Volume + belowPrice2Volume;
            }

            if (((float) runningVolume / totalVolume) >= valueAreaPercent) break;
        }

        isValueAreaCalculated = true;
    }

    /**
     * @return The sum of all the volume within volume profile
     */
    public int getVolume() {
        return volumeByPrice.values().stream().mapToInt(i -> i).sum();
    }

    /**
     * @return The volume point of control, or VPOC; the price with the most volume
     */
    public float getPointOfControl() {
        return Collections.max(volumeByPrice.entrySet(), Map.Entry.comparingByValue()).getKey();
    }

    /**
     * @return A float representing the highest price of the value area
     */
    public float getValueAreaHigh() {
        if (!isValueAreaCalculated) calculateValueArea();
        return valueArea.lastKey();
    }

    /**
     * @return A float representing the lowest price of the value area
     */
    public float getValueAreaLow() {
        if (!isValueAreaCalculated) calculateValueArea();
        return valueArea.firstKey();
    }

    /**
     * @return A float representing the breadth or size of the value area
     */
    public float getValueAreaBreadth() {
        if (!isValueAreaCalculated) calculateValueArea();
        return getValueAreaHigh() - getValueAreaLow();
    }

    /**
     * @return A float representing the mid price of the value area
     */
    public float getValueAreaMid() {
        if (!isValueAreaCalculated) calculateValueArea();
        return getValueAreaHigh() - (getValueAreaBreadth() / 2);
    }

    public ValueArea getValueArea() {
        return new ValueArea(
                Collections.max(volumeByPrice.entrySet(), Map.Entry.comparingByValue()).getKey(),
                valueArea.lastKey(),
                valueArea.firstKey()
        );
    }

    public static class ValueArea {
        private final float pointOfControl;
        private final float valueAreaHigh;
        private final float valueAreaLow;
        private final float valueAreaMid;

        public ValueArea(float pointOfControl, float valueAreaHigh, float valueAreaLow) {
            this.pointOfControl = pointOfControl;
            this.valueAreaHigh = valueAreaHigh;
            this.valueAreaLow = valueAreaLow;
            valueAreaMid = valueAreaHigh - ((valueAreaHigh - valueAreaLow) / 2);
        }

        public float getPointOfControl() {
            return pointOfControl;
        }

        public float getValueAreaHigh() {
            return valueAreaHigh;
        }

        public float getValueAreaLow() {
            return valueAreaLow;
        }

        public float getValueAreaMid() {
            return valueAreaMid;
        }
    }
}
