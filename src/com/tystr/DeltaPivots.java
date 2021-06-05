package com.tystr;

import java.awt.*;
import java.time.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.draw.*;
import com.motivewave.platform.sdk.draw.Label;
import com.motivewave.platform.sdk.study.Plot;
import com.motivewave.platform.sdk.study.StudyHeader;

/**
 * This study plots delta pivots and extensions
 * @see <a href="https://www.onlyticks.com/blog-orderflowleo/session-delta-pivots"></a>
 * @author Tyler Stroud <tyler@tylerstroud.com>
 */
@StudyHeader(
        namespace = "com.tystr",
        id = "DELTA_PIVOTS"
                + "",
        name = "Session Delta Pivots",
        desc = "This study plots delta pivots for a given session. See https://www.onlyticks.com/blog-orderflowleo/session-delta-pivots",
        overlay = true
)
public class DeltaPivots extends com.motivewave.platform.sdk.study.Study {
    final static String SESSION_RTH = "RTH";
    final static String SESSION_JPY = "JPY";
    final static String SESSION_LONDON = "EURO/London";
    final static DayOfWeek[] tradingDays = new DayOfWeek[]{
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    };

    private Map<Integer, Integer> deltas;
    private Map<Integer, Integer> rthDeltas;
    private Map<Integer, DeltaBar> deltaBars;
    private Map<Integer, Integer> rollingWindowDeltas; // index, delta

    private long rthOpen; // previous RTH Open Timestamp
    private long rthClose; // previous RTH Close Timestamp
    private long londonOpen;
    private long londonClose;

    // don't do this - used to reset bar colors for developing delta
    private Defaults defaults;

    @Override
    public void initialize(Defaults defaults) {
        this.defaults = defaults;
        clearFigures();
        clearState();
        var sd = createSD();
        var tab = sd.addTab("General");

        var grp = tab.addGroup("");
        grp.addRow(new PathDescriptor("PivotLine", "Pivot Line", Color.ORANGE, 1.0f, null, true, false, false));
        grp.addRow(new PathDescriptor("HighExtensionLine", "High Extensions", Color.BLUE, 1.0f, null, true, false, false));
        grp.addRow(new PathDescriptor("LowExtensionLine", "Low Extensions", Color.RED, 1.0f, null, true, false, false));
        grp.addRow(new InputDescriptor("SessionInput", "Session", new String[]{SESSION_RTH, SESSION_JPY, SESSION_LONDON}, SESSION_RTH));

        LocalTime rthOpenTime = LocalTime.of(9, 30);
        LocalDateTime rthOpenDateTime = LocalDateTime.of(LocalDate.now(), rthOpenTime);
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(rthOpenDateTime)) rthOpenDateTime = rthOpenDateTime.minusDays(1);

        // These timestamps will be used to determine which dataseries bars are used to calculate the delta pivots
        rthOpen = rthOpenDateTime.toEpochSecond(ZoneOffset.ofHours(-4)) * 1000; // -4 for EDT
        rthClose = rthOpenDateTime.plusHours(6).plusMinutes(30).toEpochSecond(ZoneOffset.ofHours(-4)) * 1000;
        londonOpen = getLondonOpen().toEpochSecond(ZoneOffset.ofHours(-4)) * 1000; // -4 for EDT;
        londonClose = getLondonOpen().plusHours(6).plusMinutes(30).toEpochSecond(ZoneOffset.ofHours(-4)) * 1000; // -4 for EDT
//        debug("rthOpen: " + rthOpen);
//        debug("rthClose: " + rthClose);
//        debug("londonOpen: " + londonOpen);
//        debug("londonClose: " + londonClose);

        deltas = new HashMap<Integer, Integer>();
        rthDeltas = new HashMap<Integer, Integer>();
        deltaBars = new HashMap<>();
        rollingWindowDeltas = new HashMap<>();
    }

    private LocalDateTime getLondonOpen() {
        LocalTime londonOpenTime = LocalTime.of(3, 00);
        LocalDateTime londonOpenDateTime = LocalDateTime.of(LocalDate.now(), londonOpenTime);
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(londonOpenDateTime)) londonOpenDateTime = londonOpenDateTime.minusDays(1);

        if (londonOpenDateTime.getDayOfWeek() == DayOfWeek.SATURDAY) londonOpenDateTime = londonOpenDateTime.minusDays(1);
        if (londonOpenDateTime.getDayOfWeek() == DayOfWeek.SUNDAY) londonOpenDateTime = londonOpenDateTime.minusDays(2);

        return londonOpenDateTime;
    }

    private LocalDateTime getRthOpenLocalDateTime() {
        LocalTime rthOpenTime = LocalTime.of(9, 30);
        LocalDateTime rthOpenDateTime = LocalDateTime.of(LocalDate.now(), rthOpenTime);
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(rthOpenDateTime)) rthOpenDateTime = rthOpenDateTime.minusDays(1);

        if (rthOpenDateTime.getDayOfWeek() == DayOfWeek.SATURDAY) rthOpenDateTime = rthOpenDateTime.minusDays(1);
        if (rthOpenDateTime.getDayOfWeek() == DayOfWeek.SUNDAY) rthOpenDateTime = rthOpenDateTime.minusDays(2);

        return rthOpenDateTime;
    }

    @Override
    protected void calculateValues(DataContext ctx) {
        String session = getSettings().getInput("SessionInput").toString();
        DataSeries series = ctx.getDataSeries(BarSize.getBarSize(Enums.BarSizeType.CONSTANT_VOLUME, 10000));

        if (series.size() != 0) {
            SessionDeltaPivot sdp = calculateDeltasForSession(ctx, series, session);
            debug("SessionDeltaPivot: Session " + sdp.getSession());
            debug("SessionDeltaPivot: Pivot" + sdp.getPivot());
            debug("SessionDeltaPivot: High " + sdp.getHigh());
            debug("SessionDeltaPivot: Low " + sdp.getLow());
            debug("SessionDeltaPivot: Breadth " + sdp.getBreadth());
            debug("SessionDeltaPivot: Delta " + sdp.getDelta());
            debug("SessionDeltaPivot: Delta POC " + sdp.getDeltaPoc());
            debug("SessionDeltaPivot: DataSeries Bar Index " + sdp.getBarIndex());
            clearFigures();
            addFiguresForSessionDeltaPivot(sdp, ctx.getDefaults());
        } else {
            debug("DataSeries size is 0, skipping SDP calculation");
        }

        super.calculateValues(ctx);
    }

    /**
     * Calculate pivots for the given session
     * @param ctx
     * @param series
     * @param session One of SESSION_RTH, SESSION_JPY, or SESSION_EURO
     * @return
     */
    private SessionDeltaPivot calculateDeltasForSession(DataContext ctx, DataSeries series, String session) {
//        BarSize barSize;
        long sessionStart;
        long sessionEnd;
        LocalDateTime sessionStartDateTime;
        switch (session) {
            case SESSION_RTH: // Calculate pivots during RTH session
                sessionStartDateTime = getRthOpenLocalDateTime();
                sessionStart = sessionStartDateTime.toEpochSecond(ZoneOffset.ofHours(-4)) * 1000; // -4 for EDT
                sessionEnd = sessionStartDateTime.plusHours(6).plusMinutes(30).toEpochSecond(ZoneOffset.ofHours(-4)) * 1000; // -4 for EDT
//                barSize = BarSize.getBarSize(Enums.BarSizeType.CONSTANT_VOLUME, 10000);
                break;
            case SESSION_LONDON: // Calculate pivots during London session
                sessionStartDateTime = getLondonOpen();
                sessionStart = sessionStartDateTime.toEpochSecond(ZoneOffset.ofHours(-4)) * 1000; // -4 for EDT
                sessionEnd = sessionStartDateTime.plusHours(6).plusMinutes(30).toEpochSecond(ZoneOffset.ofHours(-4)) * 1000; // -4 for EDT
//                barSize = BarSize.getBarSize(Enums.BarSizeType.CONSTANT_VOLUME, 10000);
                break;
            case SESSION_JPY: //
            default:
                debug("USING DEFAULT SESSION RTH for session: " + session);
                sessionStartDateTime = getRthOpenLocalDateTime();
//                barSize = BarSize.getBarSize(Enums.BarSizeType.CONSTANT_VOLUME, 5000);
                sessionStart = sessionStartDateTime.toEpochSecond(ZoneOffset.ofHours(-4)) * 1000; // -4 for EDT
                sessionEnd = sessionStartDateTime.plusHours(6).plusMinutes(30).toEpochSecond(ZoneOffset.ofHours(-4)) * 1000; // -4 for EDT
        }

        // Iterate over DataSeries and compute deltas. THe Max and Min are tracked also
        //DataSeries series = ctx.getDataSeries(barSize);
        int minDelta = 0;
        int minDeltaIndex = 0;
        int maxDelta = 0;
        int maxDeltaIndex = 0;
        DeltaBar deltaBar;

        // track consecutive bar max delta
        int maxRollingWindowDeltaSum = 0;
        int maxRollingDeltaWindowDeltaStartIndex = 0;
        int numBars = 5; // @todo make this configurable - the number of bars over which to calculate the rolling window

        if (series.size() < 10) throw new RuntimeException();
        int startIndex = -1;
        for (int i = 1; i < series.size(); i++) {
            if (!series.isBarComplete(i)) continue;
            if (series.getStartTime(i) < sessionStart) {
                // todo if we're goin to calculate multiple sessions, need to instead see if series index is within
                // session's relative open/close time instead of using an absolute datetime for open/close
                continue; // ignore if bar is before session open
            } else {
                if (startIndex == -1) startIndex = i;
            }
            if (series.getEndTime(i) > sessionEnd)
                continue; // ignore if bar is after session close

//            if (rthDeltas.get(i) == null) {
//                int delta = getDeltaForTicks(ctx.getInstrument().getTicks(series.getStartTime(i), series.getEndTime(i)));
            if (deltaBars.get(i) == null) {
                deltaBar = getDeltaForTicksAsDeltaBar(ctx.getInstrument().getTicks(series.getStartTime(i), series.getEndTime(i)));
                deltaBars.put(i, deltaBar);
                int delta = deltaBar.getDelta();
                debug("DELTA POC: " + deltaBar.getDeltaPOC());
                float deltaPercent = delta / series.getVolumeAsFloat(i);
                series.setInt(i, "Delta", delta);
                series.setFloat(i, "DeltaPercent", deltaPercent);
                //rthDeltas.put(i, delta);
                debug("Calculated delta for index " + i + ": " + delta + " " + deltaPercent);
                debug("STREAM SUM DELTA: " + deltaBar.calcDelta());
            } else {
                debug("Found delta "  + deltaBars.get(i).getDelta() + " for index " + i);
            }

            // update min/max delta
            int delta = deltaBars.get(i).getDelta();
            if (delta < 0 && delta < minDelta) {
                minDelta = delta;
                minDeltaIndex = i;
            } else if (delta > 0 && delta > maxDelta) {
                maxDelta = delta;
                maxDeltaIndex = i;
            }

            debug("startIndex: " + startIndex);

            series.setPriceBarColor(i, defaults.getBarColor());
            if (i > startIndex + numBars) {
                debug("Calculating rolling window delta sum");
                int rollingWindowDeltaSum = 0;
                int rollingWindowStart = i - numBars;
                // calculate sum for rolling numBars window
                for (int j = i; j >= rollingWindowStart; j--) {
                    debug("Getting Delta for index " + j);
                    rollingWindowDeltaSum += Math.abs(deltaBars.get(j).getDelta());
                }
                if (rollingWindowDeltaSum > maxRollingWindowDeltaSum) {
                    maxRollingWindowDeltaSum = rollingWindowDeltaSum;
                    maxRollingDeltaWindowDeltaStartIndex = rollingWindowStart;
                }
                rollingWindowDeltas.put(rollingWindowStart, rollingWindowDeltaSum);
                debug("Calculated rollingWindowDeltaSum: " + rollingWindowDeltaSum);
            }
        }
        debug("Calculated MAX rollingWindowDeltaSum: " + maxRollingWindowDeltaSum  + " starting at index " + maxRollingDeltaWindowDeltaStartIndex);

        // highlight rolling window max delta bars
        for (int i = maxRollingDeltaWindowDeltaStartIndex; i < maxRollingDeltaWindowDeltaStartIndex + numBars; i++) {
            Marker arrow = new Marker(new Coordinate(series.getStartTime(i), series.getClose(i) - 6), Enums.MarkerType.ARROW);
            arrow.setSize(Enums.Size.MEDIUM);
            arrow.setFillColor(Color.ORANGE);
            addFigure(Plot.PRICE, arrow);
            series.setPriceBarColor(i, Color.GREEN);
        }
//        debug("MAX POSITIVE DELTA BAR INDEX: " + maxDeltaIndex);
//        debug("MAX POSITIVE DELTA: " + maxDelta);
//        debug("MAX NEGATIVE DELTA BAR INDEX: " + minDeltaIndex);
//        debug("MAX NEGATIVE DELTA: " + minDelta);

        int sdpIndex = Math.abs(maxDelta) > Math.abs(minDelta) ? maxDeltaIndex : minDeltaIndex;

        return new SessionDeltaPivot(
                deltaBars.get(sdpIndex),
                sdpIndex,
                series.getHigh(sdpIndex),
                series.getLow(sdpIndex),
                session,
                series.getStartTime(sdpIndex),
                true
        );
    }

    /**
     * Creates and adds the line drawings for the pivot and it's extensions
     *
     * @param sdp Session Delta Pivot
     * @param defaults Defaults
     */
    private void addFiguresForSessionDeltaPivot(SessionDeltaPivot sdp, Defaults defaults) {
        Marker sdpArrow = new Marker(new Coordinate(sdp.getStartTime(), sdp.getPivot() - 8), Enums.MarkerType.ARROW);
        sdpArrow.setSize(Enums.Size.MEDIUM);
        sdpArrow.setFillColor(Color.ORANGE);

        long sdpStartTime = sdp.getStartTime();
        double sdpHigh = sdp.getHigh();
        double sdpLow = sdp.getLow();
        double sdpValue = sdp.getPivot();
        double sdpHighExtension1 = sdp.getExtensionAbove(100);
        double sdpHighExtension2 = sdp.getExtensionAbove(200);
        double sdpHighExtension3 = sdp.getExtensionAbove(300);
        double sdpLowExtension1 = sdp.getExtensionBelow(100);
        double sdpLowExtension2 = sdp.getExtensionBelow(200);
        double sdpLowExtension3 = sdp.getExtensionBelow(300);

        Settings settings = getSettings();
        PathInfo sdpPivotPathInfo = settings.getPath("PivotLine");
        PathInfo highExtensionPathInfo = settings.getPath("HighExtensionLine");
        PathInfo lowExtensionPathInfo = settings.getPath("LowExtensionLine");

        // SDP pivot and high/low
        Line sdpLine = LineBuilder.create(sdpStartTime, sdpValue)
                .setColor(sdpPivotPathInfo.getColor())
                .setFont(defaults.getFont())
                .setStroke(sdpPivotPathInfo.getStroke())
                .setText("SDP: " + sdpValue)
                .build();
        Line sdpHighLine = LineBuilder.create(sdpStartTime, sdpHigh)
                .setColor(sdpPivotPathInfo.getColor())
                .setFont(defaults.getFont())
                .setStroke(sdpPivotPathInfo.getStroke())
                .setText("SDP High: " + sdpHigh)
                .build();
        Line sdpLowLine = LineBuilder.create(sdpStartTime, sdpLow)
                .setColor(sdpPivotPathInfo.getColor())
                .setFont(defaults.getFont())
                .setStroke(sdpPivotPathInfo.getStroke())
                .setText("SDP Low: " + sdpLow)
                .build();

        // extensions above/below pivot
        Line sdpHighExtensionLine1 = LineBuilder.create(sdpStartTime, sdpHighExtension1)
                .setColor(highExtensionPathInfo.getColor())
                .setFont(defaults.getFont())
                .setStroke(highExtensionPathInfo.getStroke())
                .setText("SDP High Ext 1: " + sdpHighExtension1)
                .build();
        Line sdpHighExtensionLine2 = LineBuilder.create(sdpStartTime, sdpHighExtension2)
                .setColor(highExtensionPathInfo.getColor())
                .setFont(defaults.getFont())
                .setStroke(highExtensionPathInfo.getStroke())
                .setText("SDP High Ext 2: " + sdpHighExtension2)
                .build();
        Line sdpHighExtensionLine3 = LineBuilder.create(sdpStartTime, sdpHighExtension3)
                .setColor(highExtensionPathInfo.getColor())
                .setFont(defaults.getFont())
                .setStroke(highExtensionPathInfo.getStroke())
                .setText("SDP High Ext 3: " + sdpHighExtension3)
                .build();

        Line sdpLowExtensionLine1 = LineBuilder.create(sdpStartTime, sdpLowExtension1)
                .setColor(lowExtensionPathInfo.getColor())
                .setFont(defaults.getFont())
                .setStroke(lowExtensionPathInfo.getStroke())
                .setText("SDP Low Ext 1: " + sdpLowExtension1)
                .build();
        Line sdpLowExtensionLine2 = LineBuilder.create(sdpStartTime, sdpLowExtension2)
                .setColor(lowExtensionPathInfo.getColor())
                .setFont(defaults.getFont())
                .setStroke(lowExtensionPathInfo.getStroke())
                .setText("SDP Low Ext 2: " + sdpLowExtension2)
                .build();
        Line sdpLowExtensionLine3 = LineBuilder.create(sdpStartTime, sdpLowExtension3)
                .setColor(lowExtensionPathInfo.getColor())
                .setFont(defaults.getFont())
                .setStroke(lowExtensionPathInfo.getStroke())
                .setText("SDP Low Ext 3: " + sdpLowExtension3)
                .build();

        addFigure(sdpLine);
        addFigure(sdpHighLine);
        addFigure(sdpLowLine);
        addFigure(sdpHighExtensionLine1);
        addFigure(sdpHighExtensionLine2);
        addFigure(sdpHighExtensionLine3);
        addFigure(sdpLowExtensionLine1);
        addFigure(sdpLowExtensionLine2);
        addFigure(sdpLowExtensionLine3);
        addFigure(sdpArrow);
    }

    /**
     * Helper class for buidling lines to draw on the chart
     */
    private static class LineBuilder {
        private final Coordinate coordinate1;
        private final Coordinate coordinate2;
        private Color color;
        private Stroke stroke;
        private String text;
        private Font font;

        private LineBuilder(long startTime, double value) {
            this.coordinate1 = new Coordinate(startTime, value);
            this.coordinate2 = new Coordinate(startTime+1, value);
        }

        public static LineBuilder create(long startTime, double value) {
            return new LineBuilder(startTime, value);
        }
        public LineBuilder setColor(Color color) {
            this.color = color;
            return this;
        }
        public LineBuilder setStroke(Stroke stroke) {
            this.stroke = stroke;
            return this;
        }
        public LineBuilder setText(String text) {
            this.text = text;
            return this;
        }
        public LineBuilder setFont(Font font) {
            this.font = font;
            return this;
        }

        public Line build() {
            Line line = new Line(coordinate1, coordinate2);
            line.setColor(color);
            line.setStroke(stroke);
            line.setExtendRightBounds(true);
            line.setText(text, font);

            return line;
        }
    }

    private static class SessionDeltaPivot {
        private final float pivot;
        private final float high;
        private final float low;
        private final float breadth;
        private final int barIndex;
        private final String session;
        private final long startTime;
        private final long delta;
        private final float deltaPoc;

        /**
         *
         * @param barIndex DataSeries index of the bar used to compute the pivot levels
         * @param high High of the bar used to compute the pivot levels
         * @param low Low of the bar used to compute the pivot levels
         * @param session The session during which the pivot was calculated
         * @param startTime Start time of the bar used to compute the pivot levels
         */
        public SessionDeltaPivot(DeltaBar deltaBar, int barIndex, float high, float low, String session, long startTime, boolean usePocAsPivot) {
            this.barIndex = barIndex;
            this.high = high;
            this.low = low;
            this.breadth = high - low;
            this.session = session;
            this.startTime = startTime;
            this.delta = deltaBar.getDelta();
            this.deltaPoc = deltaBar.getDeltaPOC();

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
            return deltaPoc;
//            return this.pivot;
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
    }


    private static class DeltaBar {
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
    }

    protected DeltaBar getDeltaForTicksAsDeltaBar(List<Tick> ticks) {
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

    /**
     * Calculates the delta between bid and ask volume for the given list of ticks
     *
     * @param ticks A List of ticks over which delta is to be calculated
     * @return The delta
     */
    protected int getDeltaForTicks(List<Tick> ticks) {
        int delta = 0;
        for (Tick tick : ticks) {
            if (tick.isAskTick()) {
                delta += tick.getVolume();
            } else
                delta -= tick.getVolume();
        }

        return delta;
    }
}