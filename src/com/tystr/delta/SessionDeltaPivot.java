package com.tystr.delta;

/**
 * @author Tyler Stroud <tyler@tylerstroud.com>
 */
public class SessionDeltaPivot {
    private final float pivot;
    private final float high;
    private final float low;
    private final float breadth;
    private final int barIndex;
    private final String session;
    private final long startTime;
    private final long delta;
    private final float deltaPoc;
    private final long endOfDay;

    /**
     *
     * @param barIndex DataSeries index of the bar used to compute the pivot levels
     * @param high High of the bar used to compute the pivot levels
     * @param low Low of the bar used to compute the pivot levels
     * @param session The session during which the pivot was calculated
     * @param startTime Start time of the bar used to compute the pivot levels
     */
    public SessionDeltaPivot(DeltaBar deltaBar, int barIndex, float high, float low, String session, long startTime, boolean usePocAsPivot, long endOfDay) {
        this.barIndex = barIndex;
        this.high = high;
        this.low = low;
        this.breadth = high - low;
        this.session = session;
        this.startTime = startTime;
        this.delta = deltaBar.getDelta();
        this.deltaPoc = deltaBar.getDeltaPOC();
        this.endOfDay = endOfDay;

        if (usePocAsPivot) {
            this.pivot = deltaPoc;
        } else {
            // using mid as pivot
            this.pivot = high - (breadth / 2);
        }
    }

    public int getBarIndex() {
        return this.barIndex;
    }

    /**
     * @todo how do we determine pivot? mid? POC? ????
     * @return
     */
    public float getPivot() {
//            return deltaPoc;
        return this.pivot;
    }

    public float getHigh() {
        return getPivot() + (breadth / 2); //this.high;
    }

    public float getLow() {
        return getPivot() - (breadth / 2); //this.low;
    }

    public float getBreadth() {
        return this.breadth;
    }

    /**
     *
     * @param percent whole value e.g. 100 for 100%, 250 for 250%
     * @return the value of the extension
     */
    public float getExtensionAbove(int percent) {
        return this.pivot + (breadth / 2) + (this.breadth * (percent / 100f));
    }

    /**
     *
     * @param percent whole value e.g. 100 for 100%, 250 for 250%
     * @return the value of the extension
     */
    public float getExtensionBelow(int percent) {
        return pivot - (breadth / 2) - (this.breadth * (percent / 100f));
    }

    /**
     * @return The session used to calculate the pivots
     */
    public String getSession() {
        return this.session;
    }

    public long getStartTime() {
        return this.startTime;
    }

    /**
     *
     * @return the delta of the bar used to calculate the pivots
     */
    public long getDelta() {
        return this.delta;
    }

    /**
     *
     * @return returns the value of the delta point of control
     */
    public float getDeltaPoc() {
        return this.deltaPoc;
    }

    public long getEndOfDay() {
        return endOfDay;
    }
}