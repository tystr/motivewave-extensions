package study_examples;

import java.awt.*;
import java.time.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.draw.*;
import com.motivewave.platform.sdk.study.StudyHeader;

/**
 * This study draws a trend line on the price graph and allows the user to move it using the resize points.
 * The purpose of this example is to demonstrate advanced features such as using resize points and context menus.
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
    private static enum SESSIONS {
        RTH,
        GLOBEX,
        EURO
    }

    private Map<Integer, Integer> deltas;
    private Map<Integer, Integer> rthDeltas;
    private long rthOpen; // previous RTH Open Timestamp
    private long rthClose; // previous RTH Close Timestamp
    private long londonOpen;
    private long londonClose;

    @Override
    public void initialize(Defaults defaults) {
        clearFigures();
        clearState();
        var sd = createSD();
        var tab = sd.addTab("General");

        var grp = tab.addGroup("");
        grp.addRow(new PathDescriptor("PivotLine", "Pivot Line", Color.ORANGE, 1.0f, null, true, false, false));
        grp.addRow(new PathDescriptor("HighExtensionLine", "High Extensions", Color.BLUE, 1.0f, null, true, false, false));
        grp.addRow(new PathDescriptor("LowExtensionLine", "Low Extensions", Color.RED, 1.0f, null, true, false, false));

        LocalTime rthOpenTime = LocalTime.of(9, 30);
        LocalDateTime rthOpenDateTime = LocalDateTime.of(LocalDate.now(), rthOpenTime);
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(rthOpenDateTime)) rthOpenDateTime = rthOpenDateTime.minusDays(1);

        rthOpen = rthOpenDateTime.toEpochSecond(ZoneOffset.ofHours(-4)) * 1000; // -4 for EDT
        rthClose = rthOpenDateTime.plusHours(6).plusMinutes(30).toEpochSecond(ZoneOffset.ofHours(-4)) * 1000;
        londonOpen = getLondonOpen().toEpochSecond(ZoneOffset.ofHours(-4)) * 1000; // -4 for EDT;
        londonClose = getLondonOpen().plusHours(6).plusMinutes(30).toEpochSecond(ZoneOffset.ofHours(-4)) * 1000; // -4 for EDT
        debug("rthOpen: " + rthOpen);
        debug("rthClose: " + rthClose);
        debug("londonOpen: " + londonOpen);
        debug("londonClose: " + londonClose);

        deltas = new HashMap<Integer, Integer>();
        rthDeltas = new HashMap<Integer, Integer>();
    }

    private LocalDateTime getLondonOpen() {
        LocalTime londonOpenTime = LocalTime.of(3, 00);
        LocalDateTime londonOpenDateTime = LocalDateTime.of(LocalDate.now(), londonOpenTime);
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(londonOpenDateTime)) londonOpenDateTime = londonOpenDateTime.minusDays(1);

        return londonOpenDateTime;
    }

    private LocalDateTime getRthOpenLocalDateTime() {
        LocalTime rthOpenTime = LocalTime.of(9, 30);
        LocalDateTime rthOpenDateTime = LocalDateTime.of(LocalDate.now(), rthOpenTime);
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(rthOpenDateTime)) rthOpenDateTime = rthOpenDateTime.minusDays(1);

        return rthOpenDateTime;
    }


    @Override
    protected void calculateValues(DataContext ctx) {
        SESSIONS session = SESSIONS.GLOBEX;

        SessionDeltaPivot sdp = calculateDeltasForSession(ctx, session);
        debug("SessionDeltaPivot: Session " + sdp.getSession());
        debug("SessionDeltaPivot: Pivot" + sdp.getPivot());
        debug("SessionDeltaPivot: High " + sdp.getHigh());
        debug("SessionDeltaPivot: Low " + sdp.getLow());
        debug("SessionDeltaPivot: Breadth " + sdp.getBreadth());
        debug("SessionDeltaPivot: Bar Index " + sdp.getBarIndex());
        addFiguresForSessionDeltaPivot(sdp, ctx.getDefaults());

        super.calculateValues(ctx);
    }

    private static class Session {
        SESSIONS name;
        long open;
        long close;

        public Session(SESSIONS name, long open, long close) {
            this.name = name;
            this.open = open;
            this.close = close;
        }
        public SESSIONS getName() {
            return this.name;
        }
        public long getOpen() {
            return this.open;
        }
        public long getClose() {
            return this.close;
        }
    }

    // calculate pivots for the given session
    private SessionDeltaPivot calculateDeltasForSession(DataContext ctx, SESSIONS session) {
        BarSize barSize;
        long sessionStart;
        long sessionEnd;
        LocalDateTime sessionStartDateTime;
        switch (session) {
            case RTH: // Calculate pivots during RTH session
                sessionStartDateTime = getRthOpenLocalDateTime();
                sessionStart = sessionStartDateTime.toEpochSecond(ZoneOffset.ofHours(-4)) * 1000; // -4 for EDT
                sessionEnd = sessionStartDateTime.plusHours(6).plusMinutes(30).toEpochSecond(ZoneOffset.ofHours(-4)) * 1000; // -4 for EDT
                barSize = BarSize.getBarSize(Enums.BarSizeType.CONSTANT_VOLUME, 10000);
                break;
            case GLOBEX: // Calculate pivots during London session
                sessionStartDateTime = getLondonOpen();
                sessionStart = sessionStartDateTime.toEpochSecond(ZoneOffset.ofHours(-4)) * 1000; // -4 for EDT
                sessionEnd = sessionStartDateTime.plusHours(6).plusMinutes(30).toEpochSecond(ZoneOffset.ofHours(-4)) * 1000; // -4 for EDT
                barSize = BarSize.getBarSize(Enums.BarSizeType.CONSTANT_VOLUME, 10000);
                break;
            case EURO:
            default:
                debug("USING DEFAULT SESSION RTH for session: " + session);
                sessionStartDateTime = getRthOpenLocalDateTime();
                barSize = BarSize.getBarSize(Enums.BarSizeType.CONSTANT_VOLUME, 10000);
                sessionStart = sessionStartDateTime.toEpochSecond(ZoneOffset.ofHours(-4)) * 1000; // -4 for EDT
                sessionEnd = sessionStartDateTime.plusHours(6).plusMinutes(30).toEpochSecond(ZoneOffset.ofHours(-4)) * 1000; // -4 for EDT
        }

        // Iterate over DataSeries and compute deltas. THe Max and Min are tracked also
        DataSeries series = ctx.getDataSeries(barSize);
        int minDelta = 0;
        int minDeltaIndex = 0;
        int maxDelta = 0;
        int maxDeltaIndex = 0;
        for (int i = 1; i < series.size(); i++) {
            if (series.getStartTime(i) < sessionStart)
                continue; // ignore if bar is before session open
            if (series.getEndTime(i) > sessionEnd)
                continue; // ignore if bar is after session close

            if (rthDeltas.get(i) == null) {
                int delta = getDeltaForTicks(ctx.getInstrument().getTicks(series.getStartTime(i), series.getEndTime(i)));
                float deltaPercent = delta / series.getVolumeAsFloat(i);
                series.setInt(i, "Delta", delta);
                series.setFloat(i, "DeltaPercent", deltaPercent);
                rthDeltas.put(i, delta);
                debug("Calculated delta for index " + i + ": " + delta + " " + deltaPercent);
            } else {
                debug("Found delta " + series.getInt(i, "Delta") + ", " + rthDeltas.get(i) + " for i " + i);
            }

            int delta = rthDeltas.get(i);
            if (delta < 0 && delta < minDelta) {
                minDelta = delta;
                minDeltaIndex = i;
            } else if (delta > 0 && delta > maxDelta) {
                maxDelta = delta;
                maxDeltaIndex = i;
            }
        }
        debug("MAX POSITIVE DELTA BAR INDEX: " + maxDeltaIndex);
        debug("MAX POSITIVE DELTA: " + maxDelta);
        debug("MAX NEGATIVE DELTA BAR INDEX: " + minDeltaIndex);
        debug("MAX NEGATIVE DELTA: " + minDelta);

        boolean isMax = Math.abs(maxDelta) > Math.abs(minDelta);
        int sdpIndex = isMax ? maxDeltaIndex : minDeltaIndex;
        if (isMax) {
            debug("Greatest Delta is POSITIVE");
        } else {
            debug("Greatest Delta is NEGATIVE");
        }

        SessionDeltaPivot sdp = new SessionDeltaPivot(
                sdpIndex,
                series.getHigh(sdpIndex),
                series.getLow(sdpIndex),
                session,
                series.getStartTime(sdpIndex),
                rthDeltas.get(sdpIndex)
        );

        return sdp;
    }

    private void addFiguresForSessionDeltaPivot(SessionDeltaPivot sdp, Defaults defaults) {

//        Marker rthStartArrow = new Marker(new Coordinate(ds.getStartTime(rthStartIndex), ds.getClose(rthStartIndex) - 8), Enums.MarkerType.ARROW);
//        Marker londonStartArrow = new Marker(new Coordinate(ds.getStartTime(londonOpenIndex), ds.getClose(londonOpenIndex) - 8), Enums.MarkerType.ARROW);
//        Marker londonEndArrow = new Marker(new Coordinate(ds.getEndTime(londonCloseIndex), ds.getClose(londonCloseIndex) - 8), Enums.MarkerType.ARROW);

        Marker sdpArrow = new Marker(new Coordinate(sdp.getStartTime(), sdp.getPivot() - 8), Enums.MarkerType.ARROW);
        sdpArrow.setSize(Enums.Size.LARGE);
        sdpArrow.setFillColor(Color.ORANGE);

        long sdpStartTime = sdp.getStartTime();
        double sdpHigh = sdp.getHigh();
        double sdpLow = sdp.getLow();
        double sdpBreadth = sdp.getBreadth();
        double sdpValue = sdp.getPivot();
        double sdpHighExtension1 = sdpHigh + sdpBreadth;
        double sdpHighExtension2 = sdpHigh + (sdpBreadth * 2);
        double sdpHighExtension3 = sdpHigh + (sdpBreadth * 3);
        double sdpLowExtension1 = sdpLow - sdpBreadth;
        double sdpLowExtension2 = sdpLow - (sdpBreadth * 2);
        double sdpLowExtension3 = sdpLow - (sdpBreadth * 3);

        debug("Using index " + sdp.getBarIndex() + " with delta " + sdp.getDelta() + " for SDP");
        debug("Using SDP Value: " + sdpValue);
        debug("Using SDP High: " + sdpHigh);
        debug("Using SDP Low: " + sdpLow);

        Settings settings = getSettings();
        PathInfo sdpPivotPathInfo = settings.getPath("PivotLine");
        PathInfo highExtensionPathInfo = settings.getPath("HighExtensionLine");
        PathInfo lowExtensionPathInfo = settings.getPath("LowExtensionLine");

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

        // extensions
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

    private static class SessionDeltaPivotBuilder {
        private final SessionDeltaPivot sdp;

        private SessionDeltaPivotBuilder(SessionDeltaPivot sdp) {
            this.sdp = sdp;
        }
        public static SessionDeltaPivotBuilder create(SessionDeltaPivot sdp) {
            return new SessionDeltaPivotBuilder(sdp);
        }


    }

    /**
     * Helper class for buidling lines to draw on the chart
     */
    private static class LineBuilder {
        private Defaults defaults;
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

        public LineBuilder setDefaults(Defaults defaults) {
            this.defaults = defaults;
            return this;
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
        private final SESSIONS session;
        private final long startTime;
        private final long delta;

        /**
         *
         * @param barIndex DataSeries index of the bar used to compute the pivot levels
         * @param high High of the bar used to compute the pivot levels
         * @param low Low of the bar used to compute the pivot levels
         * @param session The session during which the pivot was calculated
         * @param startTime Start time of the bar used to compute the pivot levels
         * @param delta Delta of the bar
         */
        public SessionDeltaPivot(int barIndex, float high, float low, SESSIONS session, long startTime, long delta) {
            this.barIndex = barIndex;
            this.high = high;
            this.low = low;
            this.breadth = high - low;
            this.pivot = high - (breadth / 2);
            this.session = session;
            this.startTime = startTime;
            this.delta = delta;
        }

        public int getBarIndex() {
            return this.barIndex;
        }

        public float getPivot() {
            return this.pivot;
        }

        public float getHigh() {
            return this.high;
        }

        public float getLow() {
            return this.low;
        }

        public float getBreadth() {
            return this.breadth;
        }

        public SESSIONS getSession() {
            return this.session;
        }

        public long getStartTime() {
            return this.startTime;
        }

        public long getDelta() {
            return this.delta;
        }
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