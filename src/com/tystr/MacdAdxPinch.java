package com.tystr;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.draw.Marker;
import com.motivewave.platform.sdk.study.Plot;
import com.motivewave.platform.sdk.study.StudyHeader;
import com.sun.jdi.Value;

import java.awt.*;
import java.awt.geom.Point2D;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Combines a MACD, Moving Average and RSI into one study.
 */
@StudyHeader(
        namespace = "com.tystr",
        id = "TYSTR_MACD_ADX_PINCH",
        rb = "study_examples.nls.strings", // locale specific strings are loaded from here
        name = "MACD / ADX Pinch",
        desc = "This study provides a visual indicator when MACD and ADX values are at extremes and begin to reverse",
        overlay = true,
        signals = true)
public class MacdAdxPinch extends com.motivewave.platform.sdk.study.Study {
    enum Values {MA, MACD, SIGNAL, HIST, RSI, UP, DOWN, ADX, PDI, NDI, DX, PDM, NDM, TR, HIST_ADX}

    ;

    enum Signals {CROSS_ABOVE, CROSS_BELOW, RSI_TOP, RSI_BOTTOM}

    ;

    final static String MA_PERIOD = "maPeriod";
    final static String MA_INPUT = "maInput";
    final static String MA_METHOD = "maMethod";
    final static String MACD_INPUT = "macdInput";
    final static String MACD_METHOD = "macdMethod";
    final static String MACD_PERIOD1 = "macdPeriod1";
    final static String MACD_PERIOD2 = "macdPeriod2";
    final static String RSI_PERIOD = "rsiPeriod";
    final static String RSI_INPUT = "rsiInput";
    final static String RSI_METHOD = "rsiMethod";
    final static String ADX_PERIOD = "adxPeriod";
    final static String MACD_LINE = "macdLine";
    final static String MACD_IND = "macdInd";
    final static String HIST_IND = "histInd";

    @Override
    public void initialize(Defaults defaults) {
        var sd = createSD();

        SettingTab tab;
        SettingGroup inputs;

        tab = sd.addTab("Thresholds");
        inputs = tab.addGroup("Inputs");

        inputs.addRow(new DoubleDescriptor("macdThreshold", "MACD Threshold", -1, -9999, 9999, 0.1d));
        inputs.addRow(new DoubleDescriptor("adxThreshold", "ADX Threshold", 12, -100, 100, 1));
        inputs.addRow(new DoubleDescriptor("rsiThreshold", "RSI Threshold", 25, -100, 100, 0.1d));

        inputs = tab.addGroup("Display");
        inputs.addRow(new IntegerDescriptor("pinchOffsetTicks", "Pinch Offset below low (ticks)", 4, -9999, 9999, 1));
        inputs.addRow(new IntegerDescriptor("pinchRsiOffsetTicks", "RSI Extreme Offset below low (ticks)", 6, -9999, 9999, 1));

        // MACD

        tab = sd.addTab(get("TAB_MACD"));
        inputs = tab.addGroup(get("LBL_INPUTS"));
        inputs.addRow(new InputDescriptor(MACD_INPUT, get("LBL_INPUT"), Enums.BarInput.CLOSE));
        inputs.addRow(new MAMethodDescriptor(MACD_METHOD, get("LBL_METHOD"), Enums.MAMethod.EMA));
        inputs.addRow(new MAMethodDescriptor(Inputs.SIGNAL_METHOD, get("LBL_SIGNAL_METHOD"), Enums.MAMethod.SMA));
        inputs.addRow(new IntegerDescriptor(MACD_PERIOD1, get("LBL_PERIOD1"), 12, 1, 9999, 1));
        inputs.addRow(new IntegerDescriptor(MACD_PERIOD2, get("LBL_PERIOD2"), 26, 1, 9999, 1));
        inputs.addRow(new IntegerDescriptor(Inputs.SIGNAL_PERIOD, get("LBL_SIGNAL_PERIOD"), 9, 1, 9999, 1));

        var lines = tab.addGroup(get("LBL_DISPLAY"));
        lines.addRow(new PathDescriptor(MACD_LINE, get("LBL_MACD_LINE"), defaults.getLineColor(), 1.5f, null, true, false, true));
        lines.addRow(new PathDescriptor(Inputs.SIGNAL_PATH, get("LBL_SIGNAL_LINE"), defaults.getRed(), 1.0f, null, true, false, true));
        lines.addRow(new BarDescriptor(Inputs.BAR, get("LBL_BAR_COLOR"), defaults.getBarColor(), true, true));
        lines.addRow(new MarkerDescriptor(Inputs.UP_MARKER, get("LBL_UP_MARKER"),
                Enums.MarkerType.TRIANGLE, Enums.Size.VERY_SMALL, defaults.getGreen(), defaults.getLineColor(), false, true));
        lines.addRow(new MarkerDescriptor(Inputs.DOWN_MARKER, get("LBL_DOWN_MARKER"),
                Enums.MarkerType.TRIANGLE, Enums.Size.VERY_SMALL, defaults.getRed(), defaults.getLineColor(), false, true));

        var indicators = tab.addGroup(get("LBL_INDICATORS"));
        indicators.addRow(new IndicatorDescriptor(MACD_IND, get("LBL_MACD_IND"), null, null, false, true, true));
        indicators.addRow(new IndicatorDescriptor(Inputs.SIGNAL_IND, get("LBL_SIGNAL_IND"), defaults.getRed(), null, false, false, true));
        indicators.addRow(new IndicatorDescriptor(HIST_IND, get("LBL_MACD_HIST_IND"), defaults.getBarColor(), null, false, false, true));

        var guides = tab.addGroup(get("LBL_GUIDES"));
        guides.addRow(new GuideDescriptor(Inputs.TOP_GUIDE, get("LBL_TOP_GUIDE"), 70, 1, 100, 1, true));
        var mg = new GuideDescriptor(Inputs.MIDDLE_GUIDE, get("LBL_MIDDLE_GUIDE"), 50, 1, 100, 1, true);
        mg.setDash(new float[]{3, 3});
        guides.addRow(mg);
        guides.addRow(new GuideDescriptor(Inputs.BOTTOM_GUIDE, get("LBL_BOTTOM_GUIDE"), 30, 1, 100, 1, true));

        // ADX
        tab = sd.addTab("ADX");
        inputs = tab.addGroup("Inputs");
        inputs.addRow(new IntegerDescriptor(ADX_PERIOD, "Period (bars)", 14, 0, Integer.MAX_VALUE, 1));

        // RSI

        tab = sd.addTab("RSI");
        inputs = tab.addGroup("Inputs");
        inputs.addRow(new InputDescriptor(RSI_INPUT, "Input", Enums.BarInput.CLOSE));
        inputs.addRow(new MAMethodDescriptor(RSI_METHOD, "Method", Enums.MAMethod.SMMA));
        inputs.addRow(new IntegerDescriptor(RSI_PERIOD, "Period (bars)", 14, 0, Integer.MAX_VALUE, 1));

        var desc = createRD();
        desc.exportValue(new ValueDescriptor(Values.MA, "MA", new String[]{MA_INPUT, MA_PERIOD, Inputs.SHIFT, Inputs.BARSIZE}));
        desc.exportValue(new ValueDescriptor(Values.RSI, get("LBL_RSI"), new String[]{RSI_INPUT, RSI_PERIOD}));
        desc.exportValue(new ValueDescriptor(Values.MACD, get("LBL_MACD"), new String[]{MACD_INPUT, MACD_METHOD, MACD_PERIOD1, MACD_PERIOD2}));
        desc.exportValue(new ValueDescriptor(Values.SIGNAL, get("LBL_MACD_SIGNAL"), new String[]{Inputs.SIGNAL_PERIOD}));
//        desc.exportValue(new ValueDescriptor(Values.HIST, get("LBL_MACD_HIST"), new String[]{MACD_PERIOD1, MACD_PERIOD2, Inputs.SIGNAL_PERIOD}));

        desc.declareSignal(Signals.CROSS_ABOVE, get("LBL_CROSS_ABOVE_SIGNAL"));
        desc.declareSignal(Signals.CROSS_BELOW, get("LBL_CROSS_BELOW_SIGNAL"));
        desc.declareSignal(Signals.RSI_TOP, get("RSI_TOP"));
        desc.declareSignal(Signals.RSI_BOTTOM, get("RSI_BOTTOM"));

        // Default Plot (MACD)
//        desc.setLabelSettings(MACD_INPUT, MACD_METHOD, MACD_PERIOD1, MACD_PERIOD2, Inputs.SIGNAL_PERIOD);
//        desc.setLabelPrefix("MACD");
//        desc.setTabName("MACD");
//        desc.declarePath(Values.MACD, MACD_LINE);
//        desc.declarePath(Values.SIGNAL, Inputs.SIGNAL_PATH);
//        desc.declareBars(Values.HIST, Inputs.BAR);
//        desc.declareIndicator(Values.MACD, MACD_IND);
//        desc.declareIndicator(Values.SIGNAL, Inputs.SIGNAL_IND);
//        desc.declareIndicator(Values.HIST, HIST_IND);
//        desc.setRangeKeys(Values.MACD, Values.SIGNAL, Values.HIST);
//        desc.addHorizontalLine(new LineInfo(0, null, 1.0f, new float[]{3, 3}));

        sd.addQuickSettings("macdThreshold", "adxThreshold", "rsiThreshold");
    }

    private void calculateMACD(int index, DataContext ctx) {
        DataSeries series = ctx.getDataSeries();
        boolean complete = true;

        int macdPeriod1 = getSettings().getInteger(MACD_PERIOD1, 12);
        int macdPeriod2 = getSettings().getInteger(MACD_PERIOD2, 26);
        var macdMethod = getSettings().getMAMethod(MACD_METHOD, Enums.MAMethod.EMA);
        Object macdInput = getSettings().getInput(MACD_INPUT, Enums.BarInput.CLOSE);
        if (index >= Util.max(macdPeriod1, macdPeriod2)) {
            series.setBoolean(index, "HasMACD", true);
            Double MA1 = null, MA2 = null;
            MA1 = series.ma(macdMethod, index, macdPeriod1, macdInput);
            MA2 = series.ma(macdMethod, index, macdPeriod2, macdInput);

            double MACD = MA1 - MA2;
            series.setDouble(index, Values.MACD, MACD);

            int signalPeriod = getSettings().getInteger(Inputs.SIGNAL_PERIOD, 9);

            // Calculate moving average of MACD (signal line)
            Double signal = series.ma(getSettings().getMAMethod(Inputs.SIGNAL_METHOD, Enums.MAMethod.SMA), index, signalPeriod, Values.MACD);
            series.setDouble(index, Values.SIGNAL, signal);

            if (signal != null) series.setDouble(index, Values.HIST, MACD - signal);

            if (series.isBarComplete(index) && signal != null) {
                // Check for signal events
                var c = new Coordinate(series.getStartTime(index), signal);
                if (crossedAbove(series, index, Values.MACD, Values.SIGNAL)) {
                    var marker = getSettings().getMarker(Inputs.UP_MARKER);
                    String msg = get("SIGNAL_MACD_CROSS_ABOVE", MACD, signal);
                    if (marker.isEnabled()) addFigure(new Marker(c, Enums.Position.BOTTOM, marker, msg));
                    ctx.signal(index, Signals.CROSS_ABOVE, msg, signal);
                } else if (crossedBelow(series, index, Values.MACD, Values.SIGNAL)) {
                    var marker = getSettings().getMarker(Inputs.DOWN_MARKER);
                    String msg = get("SIGNAL_MACD_CROSS_BELOW", MACD, signal);
                    if (marker.isEnabled()) addFigure(new Marker(c, Enums.Position.TOP, marker, msg));
                    ctx.signal(index, Signals.CROSS_BELOW, msg, signal);
                }
            }
        } else {
            complete = false;
            series.setBoolean(index, "HasMACD", false);
        }
    }

    private void calculateRSI(int index, DataContext ctx) {
        DataSeries series = ctx.getDataSeries();
        int rsiPeriod = getSettings().getInteger(RSI_PERIOD);
        Object rsiInput = getSettings().getInput(RSI_INPUT, Enums.BarInput.CLOSE);
        if (index < 1) return; // not enough data
//
        double diff = series.getDouble(index, rsiInput) - series.getDouble(index - 1, rsiInput);
        double up = 0, down = 0;
        if (diff > 0) up = diff;
        else down = diff;

        series.setDouble(index, Values.UP, up);
        series.setDouble(index, Values.DOWN, Math.abs(down));

        if (index <= rsiPeriod + 1) return;

        Enums.MAMethod method = getSettings().getMAMethod(RSI_METHOD);
        //Enums.MAMethod method = Enums.MAMethod.SMMA;

        double avgUp = series.ma(method, index, rsiPeriod, Values.UP);
        double avgDown = series.ma(method, index, rsiPeriod, Values.DOWN);
        double RS = avgUp / avgDown;
        double RSI = 100.0 - (100.0 / (1.0 + RS));

        series.setDouble(index, Values.RSI, RSI);
        series.setBoolean(index, "HasRSI", true);

        // Do we need to generate a signal?
//        var topGuide = getSettings().getGuide(Inputs.TOP_GUIDE);
//        var bottomGuide = getSettings().getGuide(Inputs.BOTTOM_GUIDE);
//        if (crossedAbove(series, index, Values.RSI, topGuide.getValue())) {
//            series.setBoolean(index, Signals.RSI_TOP, true);
//            ctx.signal(index, Signals.RSI_TOP, get("SIGNAL_RSI_TOP", topGuide.getValue(), round(RSI)), round(RSI));
//        } else if (crossedBelow(series, index, Values.RSI, bottomGuide.getValue())) {
//            series.setBoolean(index, Signals.RSI_BOTTOM, true);
//            ctx.signal(index, Signals.RSI_BOTTOM, get("SIGNAL_RSI_BOTTOM", bottomGuide.getValue(), round(RSI)), round(RSI));
//        }
    }

    private void calculateADX(int index, DataContext ctx) {
        var series = ctx.getDataSeries();
        series.setBoolean(index, "HasADX", false);
        if (series == null) return;
        int period = 14; //getSettings().getInteger(Inputs.PERIOD);
        if (index < 1) return; // not enough data

        // Calculate the +DM, -DM and TR
        Float pDM = series.getPositiveDM(index);
        Float nDM = series.getNegativeDM(index);
        Float tr = series.getTrueRange(index);

        series.setFloat(index, Values.PDM, pDM);
        series.setFloat(index, Values.NDM, nDM);
        series.setFloat(index, Values.TR, tr);

        if (index <= period) return; // not enough data to calculate the first set of averages

        // Calculate the Average +DM, -DM and TR
        Double PDMa = series.smma(index, period, Values.PDM);
        Double NDMa = series.smma(index, period, Values.NDM);
        Double TRa = series.smma(index, period, Values.TR);

        if (PDMa == null || NDMa == null || TRa == null) return;

        // Determine the +DI, -DI and DX
        double pDI = PDMa / TRa * 100;
        double nDI = NDMa / TRa * 100;
        double DX = Math.abs((PDMa - NDMa)) / (PDMa + NDMa) * 100;

        series.setDouble(index, Values.DX, DX);
        series.setDouble(index, Values.PDI, pDI);
        series.setDouble(index, Values.NDI, nDI);
//        var hist = getSettings().getPath(HISTOGRAM);
//        if (hist != null && hist.isEnabled()) {
//            series.setDouble(index, Values.HIST, pDI - nDI);
//        }

        if (index < period * 2) return; // not enough data to calculate the ADX

        // Calculate the Average DX
        Double ADX = series.smma(index, period, Values.DX);
        if (ADX == null) return;

        series.setDouble(index, Values.ADX, ADX);
        series.setBoolean(index, "HasADX", true);
    }

    // Computes the values for the MACD and RSI plots.  These plots use the primary (chart) data series.
    @Override
    protected void calculate(int index, DataContext ctx) {
        var series = ctx.getDataSeries();
        boolean complete = true;

        calculateMACD(index, ctx);
        calculateADX(index, ctx);
        calculateRSI(index, ctx);

        if (series.getBoolean(index, "HasMACD") && series.getBoolean(index, "HasADX")) {
//            double macdThreshold = -1;
            double macdThreshold = getSettings().getDouble("macdThreshold");
            double adxThreshold = getSettings().getDouble("adxThreshold"); // 12;
            double rsiThreshold = getSettings().getDouble("rsiThreshold"); // 25;
            double macdOptimalThreshold = -1;
            double adxOptimalThreshold = 40;

            double macd = series.getDouble(index, Values.MACD);
            double macdSignal = series.getDouble(index, Values.SIGNAL);
            double adx = series.getDouble(index, Values.ADX);
            boolean macdSlopeUp = false;
            boolean adxSlopeDown = false;
            series.setBoolean(index, "Pinching", false);

            if (macd < macdThreshold && adx > adxThreshold) {
                // we are pinching

                if (!series.getBoolean(index, "Pinching")) {
                    System.err.println("BEGIN PINCHING at index " + index);
                }
                series.setBoolean(index, "Pinching", true);
                int lookbackStart = index - 5;
                if (lookbackStart < series.getStartIndex()) return; // not enough bars
                java.util.List<Point2D> points = new LinkedList<>();
                double[] leastSquares = null;
                for (int i = index; i >= lookbackStart; i--) {
                    debug("X: " + series.getEndTime(i));
                    debug("Y: " + series.getDouble(i, Values.ADX));
                    points.add(new Point2D.Double(series.getEndTime(i), series.getDouble(i, Values.ADX)));
                    leastSquares = Util.leastSquares(points);

                    if (!series.getBoolean(i, "Pinching")) return;
                }

                if (series.getBoolean(index, "Pinching")) {
                    debug("leastSquares: " + Arrays.toString(leastSquares));
                }

                int prevIndex = index - 1;
                if (prevIndex > series.getStartIndex()) {
                    // Is the pinch "releasing" (MACD slope is up, ADX slope is down)
                    macdSlopeUp = series.getDouble(prevIndex, Values.MACD) < macd;
                    adxSlopeDown = series.getDouble(prevIndex, Values.ADX) > adx;

                    // calculate slope
                    // (y2 - y1) / (x2 - x1);
                    double adxSlope = (adx - series.getDouble(index - 1, Values.ADX)) / (series.getEndTime(index - 1) - series.getEndTime(index));
                    double macdSlope = (macd - series.getDouble(index - 1, Values.MACD)) / (series.getEndTime(index - 1) - series.getEndTime(index));
                    adxSlope = adxSlope * 10000;
                    macdSlope = macdSlope * 10000;
                    System.err.println("CALCULATED ADX SLOPE: (" + series.getDouble(index-1, Values.ADX) + ") (" + adx + "): "+adxSlope);
                    System.err.println("CALCULATED MACD SLOPE: (" + series.getDouble(index-1, Values.MACD) + ") (" + macd+ "): "+macdSlope);


//                    java.util.List<Point2D> points = new LinkedList<>();
//                    points.add(new Point2D.Float(series.getEndTime(index - 1), (float) adx));
//                    points.add(new Point2D.Float(series.getEndTime(index), (float) adx));
//                    double[] leastSquares = Util.leastSquares(points);
//                    debug("leastSquares: " + Arrays.toString(leastSquares));

//                    debug("SLOPE: " + adxSlope);
//                    adxSlopeDown = adxSlope > 0.00003; // is this a good way to filter these
//                    macdSlopeUp = macdSlope < -0.000005;
//                    debug("ADX SLOPE DOWN ENOUGH?" + adxSlopeDown);
//                    debug("MACD SLOPE DOWN ENOUGH?" + macdSlopeUp + " " + macdSlope);
                    //debug("ADX DIFFERENCE: " + (series.getDouble(prevIndex, Values.ADX) - adx));
                }

                double rsi = series.getDouble(index, Values.RSI);
                int pinchOffset = getSettings().getInteger("pinchOffsetTicks");
                int pinchRsiOffset = getSettings().getInteger("pinchRsiOffsetTicks");

                if (rsi < rsiThreshold) {
                    Marker square = new Marker(new Coordinate(series.getStartTime(index), series.getLow(index) - pinchRsiOffset), Enums.MarkerType.SQUARE);
                    square.setFillColor(Color.YELLOW.darker());
                    square.setOutlineColor(Color.YELLOW.darker());
                    square.setSize(Enums.Size.MEDIUM);
                    addFigure(Plot.PRICE, square);
                }

                if (crossedAbove(series, index, Values.MACD, Values.SIGNAL)) {
                    // plot signal arrow
                    Marker signalArrow = new Marker(new Coordinate(series.getStartTime(index), series.getLow(index) - pinchOffset), Enums.MarkerType.ARROW);
                    series.setPriceBarColor(index, Color.GREEN.darker());
                    signalArrow.setFillColor(Color.GREEN.darker());
                    signalArrow.setOutlineColor(Color.GREEN.darker());
                    signalArrow.setSize(Enums.Size.SMALL);
                    addFigure(Plot.PRICE, signalArrow);
                } else if (macdSlopeUp && adxSlopeDown) {
                    System.err.println("PINCH RELEASING at index " + index);
                    // plot squares highlighting the pinch release
                    Marker square = new Marker(new Coordinate(series.getStartTime(index), series.getLow(index) - pinchOffset), Enums.MarkerType.SQUARE);
                    square.setFillColor(Color.GREEN.darker());
                    square.setOutlineColor(Color.GREEN.darker());
                    square.setSize(Enums.Size.MEDIUM);
                    addFigure(Plot.PRICE, square);
                } else {
                    // plot squares highlighting the pinching
                    Marker square = new Marker(new Coordinate(series.getStartTime(index), series.getLow(index) - pinchOffset), Enums.MarkerType.SQUARE);
                    square.setFillColor(Color.GRAY);
                    square.setOutlineColor(Color.GRAY);
                    square.setSize(Enums.Size.MEDIUM);
                    addFigure(Plot.PRICE, square);
                }
            }
        }

        series.setComplete(index, complete);
    }
}
