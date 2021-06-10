package study_examples;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.BarDescriptor;
import com.motivewave.platform.sdk.common.desc.BarSizeDescriptor;
import com.motivewave.platform.sdk.common.desc.BooleanDescriptor;
import com.motivewave.platform.sdk.common.desc.ColorDescriptor;
import com.motivewave.platform.sdk.common.desc.EnabledDependency;
import com.motivewave.platform.sdk.common.desc.GuideDescriptor;
import com.motivewave.platform.sdk.common.desc.IndicatorDescriptor;
import com.motivewave.platform.sdk.common.desc.InputDescriptor;
import com.motivewave.platform.sdk.common.desc.IntegerDescriptor;
import com.motivewave.platform.sdk.common.desc.MAMethodDescriptor;
import com.motivewave.platform.sdk.common.desc.MarkerDescriptor;
import com.motivewave.platform.sdk.common.desc.PathDescriptor;
import com.motivewave.platform.sdk.common.desc.ShadeDescriptor;
import com.motivewave.platform.sdk.common.desc.ValueDescriptor;
import com.motivewave.platform.sdk.draw.Marker;
import com.motivewave.platform.sdk.study.Plot;
import com.motivewave.platform.sdk.study.StudyHeader;
import com.sun.jdi.Value;

import java.awt.*;

/**
 * Combines a MACD, Moving Average and RSI into one study.
 */
@StudyHeader(
        namespace = "com.tystr",
        id = "TYSTX_MACD_ADX_PINCH",
        rb = "study_examples.nls.strings", // locale specific strings are loaded from here
        name = "MACD / ADX Pinch",
        desc = "MACD / ADX Pinch",
        overlay = true,
        signals = true)
public class CompositeSample extends com.motivewave.platform.sdk.study.Study {
    enum Values {MA, MACD, SIGNAL, HIST, RSI, UP, DOWN, ADX, PDI, NDI, DX, PDM, NDM, TR, HIST_ADX};

    enum Signals {CROSS_ABOVE, CROSS_BELOW, RSI_TOP, RSI_BOTTOM};

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

    final static String MACD_LINE = "macdLine";
    final static String MACD_IND = "macdInd";
    final static String HIST_IND = "histInd";

    final static String RSI_PLOT = "RSIPlot";

    @Override
    public void initialize(Defaults defaults) {
        var sd = createSD();
        var tab = sd.addTab(get("TAB_MA"));

        var inputs = tab.addGroup(get("LBL_INPUTS"));
        inputs.addRow(new BarSizeDescriptor(Inputs.BARSIZE, get("LBL_BAR_SIZE"), BarSize.getBarSize(5)));
        inputs.addRow(new InputDescriptor(MA_INPUT, get("LBL_INPUT"), Enums.BarInput.CLOSE));
        inputs.addRow(new IntegerDescriptor(MA_PERIOD, get("LBL_PERIOD"), 20, 1, 9999, 1),
                new IntegerDescriptor(Inputs.SHIFT, get("LBL_SHIFT"), 0, -999, 999, 1));
        inputs.addRow(new BooleanDescriptor(Inputs.FILL_FORWARD, get("LBL_FILL_FORWARD"), true));

        var colors = tab.addGroup(get("LBL_DISPLAY"));
        colors.addRow(new PathDescriptor(Inputs.PATH, get("LBL_LINE"), null, 1.0f, null, true, true, false));
        colors.addRow(new IndicatorDescriptor(Inputs.IND, get("LBL_INDICATOR"), null, null, false, true, true));

        var barColors = tab.addGroup(get("LBL_BAR_COLOR"));
        barColors.addRow(new BooleanDescriptor(Inputs.ENABLE_BAR_COLOR, get("LBL_ENABLE_BAR_COLOR"), false));
        barColors.addRow(new ColorDescriptor(Inputs.UP_COLOR, get("LBL_UP_COLOR"), defaults.getGreenLine()));
        barColors.addRow(new ColorDescriptor(Inputs.NEUTRAL_COLOR, get("LBL_NEUTRAL_COLOR"), defaults.getLineColor()));
        barColors.addRow(new ColorDescriptor(Inputs.DOWN_COLOR, get("LBL_DOWN_COLOR"), defaults.getRedLine()));

        sd.addDependency(new EnabledDependency(Inputs.ENABLE_BAR_COLOR, Inputs.UP_COLOR, Inputs.NEUTRAL_COLOR, Inputs.DOWN_COLOR));

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

        var desc = createRD();
        desc.exportValue(new ValueDescriptor(Values.MA, "MA", new String[]{MA_INPUT, MA_PERIOD, Inputs.SHIFT, Inputs.BARSIZE}));
        desc.exportValue(new ValueDescriptor(Values.RSI, get("LBL_RSI"), new String[]{RSI_INPUT, RSI_PERIOD}));
        desc.exportValue(new ValueDescriptor(Values.MACD, get("LBL_MACD"), new String[]{MACD_INPUT, MACD_METHOD, MACD_PERIOD1, MACD_PERIOD2}));
        desc.exportValue(new ValueDescriptor(Values.SIGNAL, get("LBL_MACD_SIGNAL"), new String[]{Inputs.SIGNAL_PERIOD}));
        desc.exportValue(new ValueDescriptor(Values.HIST, get("LBL_MACD_HIST"), new String[]{MACD_PERIOD1, MACD_PERIOD2, Inputs.SIGNAL_PERIOD}));

        desc.declareSignal(Signals.CROSS_ABOVE, get("LBL_CROSS_ABOVE_SIGNAL"));
        desc.declareSignal(Signals.CROSS_BELOW, get("LBL_CROSS_BELOW_SIGNAL"));
        desc.declareSignal(Signals.RSI_TOP, get("RSI_TOP"));
        desc.declareSignal(Signals.RSI_BOTTOM, get("RSI_BOTTOM"));

//        // Price plot (moving average)
//        desc.getPricePlot().setLabelSettings(MA_INPUT, MA_PERIOD, Inputs.SHIFT, Inputs.BARSIZE);
//        desc.getPricePlot().setLabelPrefix("MA");
//        desc.getPricePlot().declarePath(Values.MA, Inputs.PATH);
//        desc.getPricePlot().declareIndicator(Values.MA, Inputs.IND);
//        // This tells MotiveWave that the MA values come from the data series defined by "BARSIZE"
//        desc.setValueSeries(Values.MA, Inputs.BARSIZE);
//
//        // Default Plot (MACD)
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

        sd.addQuickSettings(RSI_INPUT);
    }

    // Since the Moving Average (MA) is plotted on a different data series, we need to override this method
    // and manually compute the MA for the secondary data series.
    @Override
    protected void calculateValues(DataContext ctx) {
//        int maPeriod = getSettings().getInteger(MA_PERIOD);
//        Object maInput = getSettings().getInput(MA_INPUT, Enums.BarInput.CLOSE);
//        var barSize = getSettings().getBarSize(Inputs.BARSIZE);
//        var series2 = ctx.getDataSeries(barSize);
//
//        StudyHeader header = getHeader();
//        boolean updates = getSettings().isBarUpdates() || (header != null && header.requiresBarUpdates());
//
//        // Calculate Moving Average for the Secondary Data Series
//        for (int i = 1; i < series2.size(); i++) {
//            if (series2.isComplete(i)) continue;
//            if (!updates && !series2.isBarComplete(i)) continue;
//            Double sma = series2.ma(Enums.MAMethod.SMA, i, maPeriod, maInput);
//            series2.setDouble(i, Values.MA, sma);
//        }

        // Invoke the parent method to run the "calculate" method below for the primary (chart) data series
        super.calculateValues(ctx);
    }


    private void calculateMACD(int index, DataContext ctx) {
        DataSeries series = ctx.getDataSeries();
        boolean complete = true;

        int macdPeriod1 = getSettings().getInteger(MACD_PERIOD1, 12);
        int macdPeriod2 = getSettings().getInteger(MACD_PERIOD2, 26);
        var macdMethod = getSettings().getMAMethod(MACD_METHOD, Enums.MAMethod.EMA);
        Object macdInput = getSettings().getInput(MACD_INPUT, Enums.BarInput.CLOSE);
        if (index >= Util.max(macdPeriod1, macdPeriod2)) {
            series.setBoolean("HasMACD", true);
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
            series.setBoolean("HasMACD", false);
        }

//        int rsiPeriod = getSettings().getInteger(RSI_PERIOD);
//        Object rsiInput = getSettings().getInput(RSI_INPUT);
//        if (index < 1) return; // not enough data
//
//        double diff = series.getDouble(index, rsiInput) - series.getDouble(index - 1, rsiInput);
//        double up = 0, down = 0;
//        if (diff > 0) up = diff;
//        else down = diff;
//
//        series.setDouble(index, Values.UP, up);
//        series.setDouble(index, Values.DOWN, Math.abs(down));
//
//        if (index <= rsiPeriod + 1) return;
//
//        var method = getSettings().getMAMethod(RSI_METHOD);
//        double avgUp = series.ma(method, index, rsiPeriod, Values.UP);
//        double avgDown = series.ma(method, index, rsiPeriod, Values.DOWN);
//        double RS = avgUp / avgDown;
//        double RSI = 100.0 - (100.0 / (1.0 + RS));

//        series.setDouble(index, Values.RSI, RSI);

//        // Do we need to generate a signal?
//        var topGuide = getSettings().getGuide(Inputs.TOP_GUIDE);
//        var bottomGuide = getSettings().getGuide(Inputs.BOTTOM_GUIDE);
//        if (crossedAbove(series, index, Values.RSI, topGuide.getValue())) {
//            series.setBoolean(index, Signals.RSI_TOP, true);
//            ctx.signal(index, Signals.RSI_TOP, get("SIGNAL_RSI_TOP", topGuide.getValue(), round(RSI)), round(RSI));
//        } else if (crossedBelow(series, index, Values.RSI, bottomGuide.getValue())) {
//            series.setBoolean(index, Signals.RSI_BOTTOM, true);
//            ctx.signal(index, Signals.RSI_BOTTOM, get("SIGNAL_RSI_BOTTOM", bottomGuide.getValue(), round(RSI)), round(RSI));
//        }

        series.setComplete(index, complete);
    }

    private void calculateADX(int index, DataContext ctx) {
        var series = ctx.getDataSeries();
        if (series == null) return;
        series.setBoolean("HasADX", false);
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

        if (PDMa == null ||NDMa == null || TRa == null) return;

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

        if (index < period*2) return; // not enough data to calculate the ADX

        // Calculate the Average DX
        Double ADX = series.smma(index, period, Values.DX);
        if (ADX == null) return;

        series.setDouble(index, Values.ADX, ADX);
        series.setBoolean("HasADX", true);
        series.setComplete(index);
    }
    // Computes the values for the MACD and RSI plots.  These plots use the primary (chart) data series.
    @Override
    protected void calculate(int index, DataContext ctx) {
        var series = ctx.getDataSeries();
        boolean complete = true;
        //debug("Calculating MACD for index: " + index);
//        debug("Calculating index: " + index + " of " + series.size());
        calculateMACD(index, ctx);
        if (series.getBoolean("HasMACD")) {
            //double macd  = series.getDouble(index, Values.MACD);
//            debug("MACD Value: " + series.getDouble(index, Values.MACD));
//            debug("MACD Value: " + series.getDouble(index, Values.MACD));
//            debug("MACD Signal: " + series.getDouble(index, Values.SIGNAL));
        }

//        //debug("Calculating ADX for index: " + index);
        calculateADX(index, ctx);
        if (series.getBoolean("HasADX")) {
            double adx = series.getDouble(index, Values.ADX);
//            debug("ADX Value: " + series.getDouble(index, Values.ADX));

        }

        // Are we consolidating?
//        series.setBoolean(index, "Consolidating", false);
//
//        float prevHigh;
//        float prevLow;
//        float currentHigh = series.getHigh(index);
//        float currentLow = series.getLow(index);
//        boolean consolidating = false;
//
//        if (index > 1) {
//            prevHigh = series.getHigh(index-1);
//            prevLow = series.getLow(index-1);
//
//            debug("Current High: " + currentHigh);
//            debug("Previous High: " + prevHigh);
//            debug("Difference in Highs: " + (Math.abs(currentHigh - prevHigh)));
//            if (currentHigh > prevHigh && Math.abs(currentHigh - prevHigh) >= 0.5) {
//                // high is too much above prev, no longer consolidatng
//                series.setBoolean(index, "Not Consolidating, High is out of bounds", false);
//            }
//            if (currentLow < prevLow && Math.abs(currentLow - prevLow) >= 0.5) {
//                // low is too much below prev, no longer consolidatng
//                series.setBoolean(index, "Not Consolidating, Low is out of bounds", false);
//            } else {
//
//                //if ( Math.abs(currentHigh) - Math.abs(prevHigh) < 1 && Math.abs(currentLow) - Math.abs(prevLow) < 1) {
//                debug("Consolidating");
//                series.setBoolean(index, "Consolidating", true);
//                Marker arrow = new Marker(new Coordinate(series.getStartTime(index), series.getLow(index) - 8), Enums.MarkerType.ARROW);
//                arrow.setFillColor(Color.BLUE);
//                arrow .setSize(Enums.Size.LARGE);
//                addFigure(Plot.PRICE, arrow);
//                series.setBoolean(index, "Consolidating", false);
//            }
//        }
//
//


//        if (series.isComplete(index)) {
//            debug("ATR for index " + index + ": " + series.atr(index));
//            if (series.atr(index) > 1.95) {
//                Marker arrow = new Marker(new Coordinate(series.getStartTime(index), series.getLow(index) - 8), Enums.MarkerType.ARROW);
//                arrow.setFillColor(Color.BLUE);
//                arrow .setSize(Enums.Size.LARGE);
//                addFigure(Plot.PRICE, arrow);
//            }
//
//        }


//        double barLength = series.getEndTime(index) - series.getStartTime(index);
//        double barBreadth = series.getOpen(index) - series.getClose(index);
////        debug("Bar Length at index " + index + ": " + barLength);
////        debug("Bar Breadth at index " + index + ": " + barBreadth);
//        if (barLength <= 30000 && barBreadth >= 2.5) {
//            // initiative bar? Highlight candle
//            Marker arrow = new Marker(new Coordinate(series.getStartTime(index), series.getClose(index) - 8), Enums.MarkerType.ARROW);
//            arrow.setFillColor(Color.RED);
//            arrow .setSize(Enums.Size.LARGE);
//            addFigure(Plot.PRICE, arrow);
//        }



        if (series.getBoolean( "HasMACD") && series.getBoolean("HasADX")) {
            double macdThreshold = -1;
            double adxThreshold = 12;
            double macdOptimalThreshold = -1;
            double adxOptimalThreshold = 30;

            double macd = series.getDouble(index, Values.MACD);
            double macdSignal = series.getDouble(index, Values.SIGNAL);
            double adx = series.getDouble(index, Values.ADX);
            boolean macdSlopeUp = false;
            boolean adxSlopeDown = false;
            if (macd < macdThreshold && adx > adxThreshold) {
                int prevIndex = index - 1;
                if (prevIndex > series.getStartIndex()) {
                    // Is the pinch "releasing" (MACD slope is up, ADX slope is down)
                    macdSlopeUp = series.getDouble(prevIndex, Values.MACD) < macd;
//                    adxSlopeDown = series.getDouble(prevIndex, Values.ADX) > adx;

                    // calculate slope
                    // (y2 - y1) / (x2 - x1);
                    double adxSlope = (adx - series.getDouble(index - 1, Values.ADX)) / (series.getEndTime(index - 1) - series.getEndTime(index));
                    double macdSlope = (macd - series.getDouble(index - 1, Values.MACD)) / (series.getEndTime(index - 1) - series.getEndTime(index));
                    debug("CALCULATED ADX SLOPE: (" + series.getDouble(index-1, Values.ADX) + ") (" + adx + "): "+adxSlope);
                    debug("CALCULATED MACD SLOPE: (" + series.getDouble(index-1, Values.MACD) + ") (" + macd+ "): "+macdSlope);

                    debug("SLOPE: " + adxSlope);
                    adxSlopeDown = adxSlope > 0.00003; // is this a good way to filter these
                    macdSlopeUp = macdSlope < -0.000005;
                    debug("ADX SLOPE DOWN ENOUGH?" + adxSlopeDown);
                    debug("MACD SLOPE DOWN ENOUGH?" + macdSlopeUp + " " + macdSlope);
                    //debug("ADX DIFFERENCE: " + (series.getDouble(prevIndex, Values.ADX) - adx));
                }



                // we are "releasing"
                    debug("SIGNAL AT INDEX: " + index);
                    debug("SIGNAL MACD: " + macd);
                    debug("SIGNAL MACD SIGNAL: " + macdSignal);
                    debug("SIGNAL ADX: " + adx);
                    debug(macdSlopeUp + " " +adxSlopeDown);

                    Marker signalArrow = new Marker(new Coordinate(series.getStartTime(index), series.getLow(index) - 1), Enums.MarkerType.ARROW);
                    //Marker signalArrow = new Marker(new Coordinate(series.getStartTime(index), 2), Enums.MarkerType.A1ROW);
                    if (macdSlopeUp && adxSlopeDown) { //&& crossedAbove(series, index, Values.MACD, Values.SIGNAL)) {
                        series.setPriceBarColor(index, Color.GREEN);
                        if (crossedAbove(series, index, Values.MACD, Values.SIGNAL)) {
                            signalArrow.setFillColor(Color.GREEN);
                            signalArrow.setSize(Enums.Size.VERY_LARGE);
                            addFigure(Plot.PRICE, signalArrow);
                        }
                    } else if (macdSignal > macd) {
                        signalArrow.setFillColor(Color.ORANGE);
                        //addFigure(Plot.PRICE, signalArrow);
                    }
            }
        }

        series.setComplete(index, complete);
    }
}
