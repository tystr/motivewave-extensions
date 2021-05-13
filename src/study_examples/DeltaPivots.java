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
        name = "Tystr's Delta Pivots",
        desc = "This study plots delta pivots. See https://www.onlyticks.com/blog-orderflowleo/session-delta-pivots",
        overlay = true
)
public class DeltaPivots extends com.motivewave.platform.sdk.study.Study {
    final static String START = "start", END = "end", EXT_RIGHT = "extRight", EXT_LEFT = "extLeft", EXTENSION_HIGH_COLOR = "extHighColor";

    private static enum SESSIONS {
        RTH,
        GLOBEX,
        EURO
    }

    private Map<Integer, Integer> deltas;
    private Map<Integer, Integer> rthDeltas;
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
        SESSIONS session = SESSIONS.RTH;

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

    //@Override
    protected void calculateASDFASDF(int index, DataContext ctx) {
        DataSeries series = ctx.getDataSeries();
        // RTH
        if (series.getStartTime(index) < rthOpen)
            return; // ignore if bar is before session open
        if (series.getEndTime(index) > rthClose)
            return; // ignore if bar is after session close

        if (!series.isBarComplete(index)) {
            return; //don't handle current bar
        }

//    // London
//    if (series.getStartTime(index) < londonOpen)
//      return; // ignore if bar is before session open
//    if (series.getEndTime(index) > londonClose)
//      return; // ignore if bar is after session close

        if (0 == series.getInt(index, "Delta") || deltas.get(index) == null) {
            int delta = getDeltaForTicks(ctx.getInstrument().getTicks(series.getStartTime(index), series.getEndTime(index)));
            float deltaPercent = delta / series.getVolumeAsFloat(index);
            series.setInt(index, "Delta", delta);
            series.setFloat(index, "DeltaPercent", deltaPercent);
            deltas.put(index, delta);
            debug("Calculated delta for index " + index + ": " + delta + " " + deltaPercent);
        } else {
            debug("Found delta " + series.getInt(index, "Delta") + ", " + deltas.get(index) + " for index " + index);
        }

        series.setComplete(index, series.isBarComplete(index));
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
        com.motivewave.platform.sdk.draw.Line sdpLine = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpValue), new Coordinate(sdpStartTime + 1, sdpValue));
        sdpLine.setColor(sdpPivotPathInfo.getColor());
        sdpLine.setStroke(sdpPivotPathInfo.getStroke());
        sdpLine.setExtendRightBounds(true);
        sdpLine.setText("SDP " + sdpValue, defaults.getFont());

        com.motivewave.platform.sdk.draw.Line sdpHighLine = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpHigh), new Coordinate(sdpStartTime + 1, sdpHigh));
        sdpHighLine.setColor(sdpPivotPathInfo.getColor());
        sdpHighLine.setStroke(sdpPivotPathInfo.getStroke());
        sdpHighLine.setExtendRightBounds(true);
        sdpHighLine.setText("SDP High " + sdpHigh, defaults.getFont());

        com.motivewave.platform.sdk.draw.Line sdpLowLine = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpLow), new Coordinate(sdpStartTime + 1, sdpLow));
        sdpLowLine.setColor(sdpPivotPathInfo.getColor());
        sdpLowLine.setStroke(sdpPivotPathInfo.getStroke());
        sdpLowLine.setExtendRightBounds(true);
        sdpLowLine.setText("SDP Low " + sdpLow, defaults.getFont());

        // extensions
        com.motivewave.platform.sdk.draw.Line sdpHighExtensionLine1 = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpHighExtension1), new Coordinate(sdpStartTime + 1, sdpHighExtension1));
        PathInfo highExtensionPathInfo = settings.getPath("HighExtensionLine");
        sdpHighExtensionLine1.setColor(highExtensionPathInfo.getColor());
        sdpHighExtensionLine1.setStroke(highExtensionPathInfo.getStroke());
        sdpHighExtensionLine1.setExtendRightBounds(true);
        sdpHighExtensionLine1.setText("SDP High Ext 1: " + sdpHighExtension1, defaults.getFont());

        com.motivewave.platform.sdk.draw.Line sdpHighExtensionLine2 = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpHighExtension2), new Coordinate(sdpStartTime + 1, sdpHighExtension2));
        sdpHighExtensionLine2.setColor(highExtensionPathInfo.getColor());
        sdpHighExtensionLine2.setStroke(highExtensionPathInfo.getStroke());
        sdpHighExtensionLine2.setExtendRightBounds(true);
        sdpHighExtensionLine2.setText("SDP High Ext 2: " + sdpHighExtension2, defaults.getFont());

        com.motivewave.platform.sdk.draw.Line sdpHighExtensionLine3 = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpHighExtension3), new Coordinate(sdpStartTime + 1, sdpHighExtension3));
        sdpHighExtensionLine3.setColor(highExtensionPathInfo.getColor());
        sdpHighExtensionLine3.setStroke(highExtensionPathInfo.getStroke());
        sdpHighExtensionLine3.setExtendRightBounds(true);
        sdpHighExtensionLine3.setText("SDP High Ext 3: " + sdpHighExtension3, defaults.getFont());


        PathInfo lowExtensionPathInfo = settings.getPath("LowExtensionLine");
        com.motivewave.platform.sdk.draw.Line sdpLowExtensionLine1 = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpLowExtension1), new Coordinate(sdpStartTime + 1, sdpLowExtension1));
        sdpLowExtensionLine1.setColor(lowExtensionPathInfo.getColor());
        sdpLowExtensionLine1.setStroke(lowExtensionPathInfo.getStroke());
        sdpLowExtensionLine1.setExtendRightBounds(true);
        sdpLowExtensionLine1.setText("SDP Low Ext 1: " + sdpLowExtension1, defaults.getFont());

        com.motivewave.platform.sdk.draw.Line sdpLowExtensionLine2 = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpLowExtension2), new Coordinate(sdpStartTime + 1, sdpLowExtension2));
        sdpLowExtensionLine2.setColor(lowExtensionPathInfo.getColor());
        sdpLowExtensionLine2.setStroke(lowExtensionPathInfo.getStroke());
        sdpLowExtensionLine2.setExtendRightBounds(true);
        sdpLowExtensionLine2.setText("SDP Low Ext 2: " + sdpLowExtension2, defaults.getFont());

        com.motivewave.platform.sdk.draw.Line sdpLowExtensionLine3 = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpLowExtension3), new Coordinate(sdpStartTime + 1, sdpLowExtension3));
        sdpLowExtensionLine3.setColor(lowExtensionPathInfo.getColor());
        sdpLowExtensionLine3.setStroke(lowExtensionPathInfo.getStroke());
        sdpLowExtensionLine3.setExtendRightBounds(true);
        sdpLowExtensionLine3.setText("SDP Low Ext 3: " + sdpLowExtension3, defaults.getFont());



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
        //addFigure(rthStartArrow);
        //londonStartArrow.setFillColor(Color.GREEN);
        //londonEndArrow.setFillColor(Color.RED);
        //addFigure(londonStartArrow);
        //addFigure(londonEndArrow);
        //addFigure(arrow2);

    }

    private Line getExtension(long startTime, float value, Color color, Font font) {
        Line extension = new Line(new Coordinate(startTime, value), new Coordinate(startTime + 1, value));
        extension.setColor(color);
        extension.setStroke(new BasicStroke(2));
        extension.setExtendRightBounds(true);
        extension.setText("SDP High Ext 3: " + value, font);

        return extension;
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


    //private       Map<Integer, Integer> deltas = new HashMap<Integer, Integer>();
    private long rthOpen; // previous RTH Open Timestamp
    private long rthClose; // previous RTH Close Timestamp
    private ResizePoint startResize, endResize;
    private Line trendLine;
    private Box box;

    @Override
    protected void postcalculate(DataContext ctx) {
        if (true) return;

        debug("Post Calculating...");


        DataSeries ds = ctx.getDataSeries();
        debug("Data Series Size: " + ds.size());
        debug("Start Time: " + ds.getStartTime(0));
        debug("BAR SIZE: " + ds.getBarSize());

        int minDelta = 0;
        int minIndex = 0;
        int maxDelta = 0;
        int maxIndex = 0;

        Instrument instrument = ctx.getInstrument();


        int rthStartIndex = -1;
        int londonOpenIndex = -1;
        int londonCloseIndex = -1;

        for (int i = 0; i < ds.size(); i++) {
            if (ds.getStartTime(i) < rthOpen) {
                continue;
            }

            if (ds.getEndTime(i) > rthClose) {
                if (-1 == londonCloseIndex) londonCloseIndex = i;
                continue; // Ignore bars before session open
            }
            //if (-1 == rthStartIndex) rthStartIndex = i;
            if (-1 == londonOpenIndex) londonOpenIndex = i;

            int d = ds.getInt(i, "Delta");

            if (d < 0 && d < minDelta) {
                minDelta = d;
                minIndex = i;
            } else if (d > 0 && d > maxDelta) {
                maxDelta = d;
                maxIndex = i;
            }
        }

        debug("MAX POSITIVE DELTA BAR INDEX: " + maxIndex);
        debug("MAX POSITIVE DELTA: " + ds.getInt(maxIndex, "Delta"));
        debug("MAX NEGATIVE DELTA BAR INDEX: " + minIndex);
        debug("MAX NEGATIVE DELTA: " + ds.getInt(minIndex, "Delta"));

        boolean isMax = Math.abs(maxDelta) > Math.abs(minDelta);
        int sdpIndex = isMax ? maxIndex : minIndex;
        if (isMax) {
            debug("Greatest Delta is POSITIVE");
        } else {
            debug("Greatest Delta is NEGATIVE");
        }

        Marker rthStartArrow = new Marker(new Coordinate(ds.getStartTime(rthStartIndex), ds.getClose(rthStartIndex) - 8), Enums.MarkerType.ARROW);
        Marker londonStartArrow = new Marker(new Coordinate(ds.getStartTime(londonOpenIndex), ds.getClose(londonOpenIndex) - 8), Enums.MarkerType.ARROW);
        Marker londonEndArrow = new Marker(new Coordinate(ds.getEndTime(londonCloseIndex), ds.getClose(londonCloseIndex) - 8), Enums.MarkerType.ARROW);
        Marker sdpArrow = new Marker(new Coordinate(ds.getStartTime(sdpIndex), ds.getClose(sdpIndex) - 8), Enums.MarkerType.ARROW);
        sdpArrow.setSize(Enums.Size.LARGE);
        sdpArrow.setFillColor(Color.ORANGE);
        Marker arrow2 = new Marker(new Coordinate(ds.getStartTime(minIndex), ds.getClose(minIndex) - 8), Enums.MarkerType.ARROW);
        arrow2.setSize(Enums.Size.MEDIUM);
        arrow2.setPosition(Enums.Position.BOTTOM);

        long sdpStartTime = ds.getStartTime(sdpIndex); // lines will begin here
        double sdpHigh = ds.getHigh(sdpIndex);
        double sdpLow = ds.getLow(sdpIndex);
        double sdpBreadth = ds.getHigh(sdpIndex) - ds.getLow(sdpIndex);
        double sdpValue = ds.getHigh(sdpIndex) - (sdpBreadth / 2);
        double sdpHighExtension1 = sdpHigh + sdpBreadth;
        double sdpHighExtension2 = sdpHigh + (sdpBreadth * 2);
        double sdpHighExtension3 = sdpHigh + (sdpBreadth * 3);
        double sdpLowExtension1 = sdpLow - sdpBreadth;
        double sdpLowExtension2 = sdpLow - (sdpBreadth * 2);
        double sdpLowExtension3 = sdpLow - (sdpBreadth * 3);

        debug("Using index " + sdpIndex + " with delta " + ds.getInt(sdpIndex, "Delta") + " for SDP");
        debug("Using SDP Value: " + sdpValue);
        debug("Using SDP High: " + sdpHigh);
        debug("Using SDP Low: " + sdpLow);
        com.motivewave.platform.sdk.draw.Line sdpLine = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpValue), new Coordinate(ds.getStartTime(sdpIndex + 1), sdpValue));
        sdpLine.setColor(Color.YELLOW);
        sdpLine.setStroke(new BasicStroke(4));
        sdpLine.setExtendRightBounds(true);
        sdpLine.setText("SDP " + sdpValue, ctx.getDefaults().getFont());

        com.motivewave.platform.sdk.draw.Line sdpHighLine = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpHigh), new Coordinate(ds.getStartTime(sdpIndex + 1), sdpHigh));
        sdpHighLine.setColor(Color.YELLOW);
        sdpHighLine.setStroke(new BasicStroke(2));
        sdpHighLine.setExtendRightBounds(true);
        sdpHighLine.setText("SDP High " + sdpHigh, ctx.getDefaults().getFont());

        com.motivewave.platform.sdk.draw.Line sdpLowLine = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpLow), new Coordinate(ds.getStartTime(sdpIndex + 1), sdpLow));
        sdpLowLine.setColor(Color.YELLOW);
        sdpLowLine.setStroke(new BasicStroke(2));
        sdpLowLine.setExtendRightBounds(true);
        sdpLowLine.setText("SDP Low " + sdpLow, ctx.getDefaults().getFont());

        // extensions
        com.motivewave.platform.sdk.draw.Line sdpHighExtensionLine1 = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpHighExtension1), new Coordinate(ds.getStartTime(sdpIndex + 1), sdpHighExtension1));
        sdpHighExtensionLine1.setColor(Color.CYAN);
        sdpHighExtensionLine1.setStroke(new BasicStroke(2));
        sdpHighExtensionLine1.setExtendRightBounds(true);
        sdpHighExtensionLine1.setText("SDP High Ext 1: " + sdpHighExtension1, ctx.getDefaults().getFont());

        com.motivewave.platform.sdk.draw.Line sdpHighExtensionLine2 = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpHighExtension2), new Coordinate(ds.getStartTime(sdpIndex + 1), sdpHighExtension2));
        sdpHighExtensionLine2.setColor(Color.CYAN);
        sdpHighExtensionLine2.setStroke(new BasicStroke(2));
        sdpHighExtensionLine2.setExtendRightBounds(true);
        sdpHighExtensionLine2.setText("SDP High Ext 2: " + sdpHighExtension2, ctx.getDefaults().getFont());

        com.motivewave.platform.sdk.draw.Line sdpHighExtensionLine3 = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpHighExtension3), new Coordinate(ds.getStartTime(sdpIndex + 1), sdpHighExtension3));
        sdpHighExtensionLine3.setColor(Color.CYAN);
        sdpHighExtensionLine3.setStroke(new BasicStroke(2));
        sdpHighExtensionLine3.setExtendRightBounds(true);
        sdpHighExtensionLine3.setText("SDP High Ext 3: " + sdpHighExtension3, ctx.getDefaults().getFont());


        com.motivewave.platform.sdk.draw.Line sdpLowExtensionLine1 = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpLowExtension1), new Coordinate(ds.getStartTime(sdpIndex + 1), sdpLowExtension1));
        sdpLowExtensionLine1.setColor(Color.RED);
        sdpLowExtensionLine1.setStroke(new BasicStroke(2));
        sdpLowExtensionLine1.setExtendRightBounds(true);
        sdpLowExtensionLine1.setText("SDP Low Ext 1: " + sdpLowExtension1, ctx.getDefaults().getFont());

        com.motivewave.platform.sdk.draw.Line sdpLowExtensionLine2 = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpLowExtension2), new Coordinate(ds.getStartTime(sdpIndex + 1), sdpLowExtension2));
        sdpLowExtensionLine2.setColor(Color.RED);
        sdpLowExtensionLine2.setStroke(new BasicStroke(2));
        sdpLowExtensionLine2.setExtendRightBounds(true);
        sdpLowExtensionLine2.setText("SDP Low Ext 2: " + sdpLowExtension2, ctx.getDefaults().getFont());
        com.motivewave.platform.sdk.draw.Line sdpLowExtensionLine3 = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpLowExtension3), new Coordinate(ds.getStartTime(sdpIndex + 1), sdpLowExtension3));
        sdpLowExtensionLine3.setColor(Color.RED);
        sdpLowExtensionLine3.setStroke(new BasicStroke(2));
        sdpLowExtensionLine3.setExtendRightBounds(true);
        sdpLowExtensionLine3.setText("SDP Low Ext 3: " + sdpLowExtension3, ctx.getDefaults().getFont());


        addFigure(sdpLine);
        addFigure(sdpHighLine);
        addFigure(sdpLowLine);
        addFigure(sdpHighExtensionLine1);
        addFigure(sdpHighExtensionLine2);
        addFigure(sdpHighExtensionLine3);
        addFigure(sdpLowExtensionLine1);
        addFigure(sdpLowExtensionLine2);
        addFigure(sdpLowExtensionLine3);
        //addFigure(rthStartArrow);
        londonStartArrow.setFillColor(Color.GREEN);
        londonEndArrow.setFillColor(Color.RED);
        addFigure(londonStartArrow);
        addFigure(londonEndArrow);
        addFigure(sdpArrow);
        //addFigure(arrow2);
    }

}