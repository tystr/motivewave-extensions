package study_examples;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.Enums.ResizeType;
import com.motivewave.platform.sdk.common.desc.BooleanDescriptor;
import com.motivewave.platform.sdk.common.desc.DateTimeDescriptor;
import com.motivewave.platform.sdk.common.desc.GuideDescriptor;
import com.motivewave.platform.sdk.common.desc.PathDescriptor;
import com.motivewave.platform.sdk.common.menu.MenuDescriptor;
import com.motivewave.platform.sdk.common.menu.MenuItem;
import com.motivewave.platform.sdk.common.menu.MenuSeparator;
import com.motivewave.platform.sdk.draw.*;
import com.motivewave.platform.sdk.study.StudyHeader;

/** This study draws a trend line on the price graph and allows the user to move it using the resize points. 
    The purpose of this example is to demonstrate advanced features such as using resize points and context menus. */
@StudyHeader(
    namespace="com.motivewave", 
    id="TREND_LINE_EXAMPLE"
        + "", 
    name="Tystr's Trend Line",
    desc="This is an example study that draws a simple trend line and allows the user to resize it",
    overlay=true,
    requiresBarUpdates=true
    )
public class TrendLine extends com.motivewave.platform.sdk.study.Study 
{
  final static String START="start", END="end", EXT_RIGHT="extRight", EXT_LEFT="extLeft";
  
  @Override
  public void initialize(Defaults defaults)
  {
    var sd=createSD();
    var tab=sd.addTab("General");

    var grp=tab.addGroup("");
    grp.addRow(new PathDescriptor(Inputs.PATH, "Line", defaults.getLineColor(), 1.0f, null, true, false, true));
    grp.addRow(new BooleanDescriptor(EXT_RIGHT, "Extend Right", false));
    grp.addRow(new BooleanDescriptor(EXT_LEFT, "Extend Left", false));


    ZonedDateTime utcTime = ZonedDateTime.now(ZoneOffset.UTC).minusHours(3);
    rthOpen = utcTime.toLocalDate().atStartOfDay().plusHours(13).plusMinutes(30).toEpochSecond(ZoneOffset.UTC) * 1000;
  }
  
//  @Override
//  public void onLoad(Defaults defaults)
//  {
//    // Initialize the resize points and the trend line figure
//    startResize = new ResizePoint(ResizeType.ALL, true);
//    startResize.setSnapToLocation(true);
//    endResize = new ResizePoint(ResizeType.ALL, true);
//    endResize.setSnapToLocation(true);
//    trendLine = new Line();
//    box = new Box(new Coordinate(10, 10), new Coordinate(200, 200));
//  }


  // Adds custom menu items to the context menu when the user right clicks on this study.
  @Override
  public MenuDescriptor onMenu(String plotName, Point loc, DrawContext ctx)
  {
    List<MenuItem> items = new ArrayList<>();
    items.add(new MenuSeparator());
    // Add some menu items for the user to extend right and left without having to open the study dialog
    boolean extLeft = getSettings().getBoolean(EXT_LEFT);
    boolean extRight = getSettings().getBoolean(EXT_RIGHT);
    
    // Note: the study will be recalculated (ie call calculateValues(), see below) when either of these menu items is invoked by the user
    items.add(new MenuItem("Extend Left", extLeft, () -> getSettings().setBoolean(EXT_LEFT, !extLeft)));
    items.add(new MenuItem("Extend Right", extRight, () -> getSettings().setBoolean(EXT_RIGHT, !extRight)));
    return new MenuDescriptor(items, true);
  }

//  // This method is called when the user is moving a resize point but has not released the mouse button yet.
//  // This does not cause the study to be recalculated until the resize operation is completed.
//  @Override
//  public void onResize(ResizePoint rp, DrawContext ctx)
//  {
//    // In our case we want to adjust the trend line as the user moves the resize point
//    // This will provide visual feedback to the user
//    trendLine.layout(ctx);
//    box.layout(ctx);
//  }

  // This method is called when the user has completed moving a resize point with the mouse.
  // The underlying study framework will recalculate the study after this method is called.
  @Override
  public void onEndResize(ResizePoint rp, DrawContext ctx)
  {
    // Commit the resize to the study settings, so it can be used in calculateValues() (see below)
    // We will store this in the settings as a string: "<price>|<time in millis>"
   // getSettings().setString(rp == startResize ? START : END, rp.getValue() + "|" + rp.getTime());
  }

  @Override
  protected void calculate(int index, DataContext ctx) {
    DataSeries series = ctx.getDataSeries();
    if (series.getStartTime(index) < rthOpen)
      return; // ignore if bar is before session open

    debug("Calculating for index " + index);
    if (0 == series.getInt(index, "Delta")) {
      int delta = getDeltaForTicks(ctx.getInstrument().getTicks(series.getStartTime(index), series.getEndTime(index)));
      series.setInt(index, "Delta", delta);
    } else {
      debug("Found delta " + series.getInt(index, "Delta") + " For index " + index);
    }
  }

  @Override
  protected void postcalculate(DataContext ctx) {
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

    // Beginning of session timestamp
    ZonedDateTime utcTime = ZonedDateTime.now(ZoneOffset.UTC).minusHours(3);
    long rthOpen = utcTime.toLocalDate().atStartOfDay().plusHours(13).plusMinutes(30).toEpochSecond(ZoneOffset.UTC) * 1000;
    debug("RTH Open Timestamp: " + rthOpen);

    int rthStartIndex = -1;

    for (int i = 0; i < ds.size(); i++ ) {
      if (ds.getStartTime(i) < rthOpen) continue; // Ignore bars before session open
      if (-1 == rthStartIndex) rthStartIndex = i;
//      List<Tick> t = instrument.getTicks(ds.getStartTime(i), ds.getEndTime(i));
//
//      if (null == deltas.get(i)) {
//        debug("No Delta Found for index " + i + "...Calculating");
//        deltas.put(i, getDeltaForTicks(t));
//      } else {
//        int d = deltas.get(i);
//        debug("Found Delta " + d + " for Data Series index " + i);
//      }
//      int d = deltas.get(i);

      //debug("CALCULATED DELTA: " + d);
      //debug("Volume for candle " + i + ": " + ds.getVolume(i));
      int d = ds.getInt(i, "Delta");

      if (d < 0 && d < minDelta) {
        minDelta = d;
        minIndex = i;
      } else if (d > 0 && d > maxDelta) {
        maxDelta = d;
        maxIndex = i;
      }
    }

    debug ("MAX POSITIVE DELTA: " + maxDelta);
    debug ("MAX POSITIVE DELTA BAR INDEX: " + maxIndex);
    debug ("MAX NEGATIVE DELTA: " + minDelta);
    debug ("MAX NEGATIVE DELTA BAR INDEX: " + minIndex);

    boolean isMax = Math.abs(maxDelta) > Math.abs(minDelta);
    if (isMax) {
      debug("Greatest Delta is POSITIVE");
    } else {
      debug("Greatest Delta is NEGATIVE");
    }

    //int delta = getDeltaForTicks(ticks);
    //debug("CALCULATED DELTA: " + delta);

    Marker rthStartArrow = new Marker(new Coordinate(ds.getStartTime(rthStartIndex), ds.getClose(rthStartIndex) - 4), Enums.MarkerType.ARROW);
    Marker arrow1 = new Marker(new Coordinate(ds.getStartTime(maxIndex), ds.getClose(maxIndex) - 4), Enums.MarkerType.ARROW);
    arrow1.setSize(Enums.Size.MEDIUM);
    Marker arrow2 = new Marker(new Coordinate(ds.getStartTime(minIndex), ds.getClose(minIndex) - 4), Enums.MarkerType.ARROW);
    arrow2.setSize(Enums.Size.MEDIUM);
    arrow2.setPosition(Enums.Position.BOTTOM);

    long sdpStartTime = ds.getStartTime(minIndex);
    double sdpHigh = ds.getHigh(minIndex);
    double sdpLow = ds.getLow(minIndex);
    double sdpValue = ds.getHigh(minIndex) - ((ds.getHigh(minIndex) - ds.getLow(minIndex)) / 2);
    double sdpBreadth = ds.getHigh(minIndex) - ds.getLow(minIndex);
    double sdpHighExtension1 = sdpHigh + sdpBreadth;
    double sdpHighExtension2 = sdpHigh + (sdpBreadth * 2);
    double sdpHighExtension3 = sdpHigh + (sdpBreadth * 3);
    double sdpLowExtension1 = sdpLow - sdpBreadth;
    double sdpLowExtension2 = sdpLow - (sdpBreadth * 2);
    double sdpLowExtension3 = sdpLow - (sdpBreadth * 3);

    debug ("Using SDP Value: " + sdpValue);
    debug ("Using SDP High: " + sdpHigh);
    debug ("Using SDP Low: " + sdpLow);
    com.motivewave.platform.sdk.draw.Line sdpLine = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpValue), new Coordinate(ds.getStartTime(minIndex+1), sdpValue));
    sdpLine.setColor(Color.YELLOW);
    sdpLine.setStroke(new BasicStroke(4));
    sdpLine.setExtendRightBounds(true);
    sdpLine.setText("SDP " + sdpValue, ctx.getDefaults().getFont());

    com.motivewave.platform.sdk.draw.Line sdpHighLine = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpHigh), new Coordinate(ds.getStartTime(minIndex+1), sdpHigh));
    sdpHighLine.setColor(Color.YELLOW);
    sdpHighLine.setStroke(new BasicStroke(2));
    sdpHighLine.setExtendRightBounds(true);
    sdpHighLine.setText("SDP High " + sdpHigh, ctx.getDefaults().getFont());

    com.motivewave.platform.sdk.draw.Line sdpLowLine = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpLow), new Coordinate(ds.getStartTime(minIndex+1), sdpLow));
    sdpLowLine.setColor(Color.YELLOW);
    sdpLowLine.setStroke(new BasicStroke(2));
    sdpLowLine.setExtendRightBounds(true);
    sdpLowLine.setText("SDP Low " + sdpLow, ctx.getDefaults().getFont());

    // extensions
    com.motivewave.platform.sdk.draw.Line sdpHighExtensionLine1 = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpHighExtension1), new Coordinate(ds.getStartTime(minIndex+1), sdpHighExtension1));
    sdpHighExtensionLine1.setColor(Color.CYAN);
    sdpHighExtensionLine1.setStroke(new BasicStroke(2));
    sdpHighExtensionLine1.setExtendRightBounds(true);
    sdpHighExtensionLine1.setText("SDP High Ext 1: t" + sdpHigh, ctx.getDefaults().getFont());

    com.motivewave.platform.sdk.draw.Line sdpHighExtensionLine2 = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpHighExtension2), new Coordinate(ds.getStartTime(minIndex+1), sdpHighExtension2));
    sdpHighExtensionLine2.setColor(Color.CYAN);
    sdpHighExtensionLine2.setStroke(new BasicStroke(2));
    sdpHighExtensionLine2.setExtendRightBounds(true);
    sdpHighExtensionLine2.setText("SDP High Ext 2: " + sdpHigh, ctx.getDefaults().getFont());

    com.motivewave.platform.sdk.draw.Line sdpHighExtensionLine3 = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpHighExtension3), new Coordinate(ds.getStartTime(minIndex+1), sdpHighExtension3));
    sdpHighExtensionLine3.setColor(Color.CYAN);
    sdpHighExtensionLine3.setStroke(new BasicStroke(2));
    sdpHighExtensionLine3.setExtendRightBounds(true);
    sdpHighExtensionLine3.setText("SDP High Ext 3: " + sdpHigh, ctx.getDefaults().getFont());


    com.motivewave.platform.sdk.draw.Line sdpLowExtensionLine1 = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpLowExtension1), new Coordinate(ds.getStartTime(minIndex+1), sdpLowExtension1));
    sdpLowExtensionLine1.setColor(Color.RED);
    sdpLowExtensionLine1.setStroke(new BasicStroke(2));
    sdpLowExtensionLine1.setExtendRightBounds(true);
    sdpLowExtensionLine1.setText("SDP Low Ext 1: " + sdpLow, ctx.getDefaults().getFont());

    com.motivewave.platform.sdk.draw.Line sdpLowExtensionLine2 = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpLowExtension2), new Coordinate(ds.getStartTime(minIndex+1), sdpLowExtension2));
    sdpLowExtensionLine2.setColor(Color.RED);
    sdpLowExtensionLine2.setStroke(new BasicStroke(2));
    sdpLowExtensionLine2.setExtendRightBounds(true);
    sdpLowExtensionLine2.setText("SDP Low Ext 2: " + sdpLow, ctx.getDefaults().getFont());
    com.motivewave.platform.sdk.draw.Line sdpLowExtensionLine3 = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpLowExtension3), new Coordinate(ds.getStartTime(minIndex+1), sdpLowExtension3));
    sdpLowExtensionLine3.setColor(Color.RED);
    sdpLowExtensionLine3.setStroke(new BasicStroke(2));
    sdpLowExtensionLine3.setExtendRightBounds(true);
    sdpLowExtensionLine3.setText("SDP Low Ext 3: " + sdpLow, ctx.getDefaults().getFont());


    addFigure(sdpLine);
    addFigure(sdpHighLine);
    addFigure(sdpLowLine);
    addFigure(sdpHighExtensionLine1);
    addFigure(sdpHighExtensionLine2);
    addFigure(sdpHighExtensionLine3);
    addFigure(sdpLowExtensionLine1);
    addFigure(sdpLowExtensionLine2);
    addFigure(sdpLowExtensionLine3);
    addFigure(rthStartArrow);
    addFigure(arrow1);
    addFigure(arrow2);
  }

  // This method is called whenever the study needs to be (re)calculated.
  // In this example, this will be when the study loads, bar size changes as well as calling the onEndResize() method.
  // Additionally, the study will be recalculated every time a custom menu item is invoked.
  //@Override
  protected void calculateValuesASDFASDFASDF(DataContext ctx)
  {

    // If the points have not been defined yet, just choose two...
//    var series = ctx.getDataSeries();
//    long startTime = series.getStartTime(series.size()-41);
//    double startPrice = series.getDouble(series.size()-41, Enums.BarInput.MIDPOINT);
//    long endTime = series.getStartTime(series.size()-1);
//    double endPrice = series.getFloat(series.size()-1, Enums.BarInput.MIDPOINT);
//
//    // Storage format is price|time
//    String start = getSettings().getString(START);
//    String end = getSettings().getString(END);
//    if (!Util.isEmpty(start)) {
//      startPrice = Double.valueOf(start.substring(0, start.indexOf('|')));
//      startTime = Long.valueOf(start.substring(start.indexOf('|')+1));
//    }
//    if (!Util.isEmpty(end)) {
//      endPrice = Double.valueOf(end.substring(0, end.indexOf('|')));
//      endTime = Long.valueOf(end.substring(end.indexOf('|')+1));
//    }
//
//    box.setStart(startTime, startPrice);
//    box.setEnd(endTime, endPrice);
//    box.setFillColor(Color.BLUE);
//    startResize.setLocation(startTime, startPrice);
//    endResize.setLocation(endTime, endPrice);

    // Figures are cleared when the study is recalculated, so we need to add these figures every time

    //addFigure(box);

    //List<Tick> ticks = ctx.getInstrument().getTicks(startTime, endTime);
    DataSeries ds = ctx.getDataSeries();
    debug("Data Series Size: " + ds.size());
    debug("Start Time: " + ds.getStartTime(0));
    debug("BAR SIZE: " + ds.getBarSize());

    int minDelta = 0;
    int minIndex = 0;
    int maxDelta = 0;
    int maxIndex = 0;

    Instrument instrument = ctx.getInstrument();

    // Beginning of session timestamp
    ZonedDateTime utcTime = ZonedDateTime.now(ZoneOffset.UTC).minusHours(3);
    long rthOpen = utcTime.toLocalDate().atStartOfDay().plusHours(13).plusMinutes(30).toEpochSecond(ZoneOffset.UTC) * 1000;
     debug("RTH Open Timestamp: " + rthOpen);

    Timestamp timestamp = new Timestamp(ds.getStartTime(0));
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    // iterate over data series
      debug("Processing " + ds.size() + " Candles...");



      int rthStartIndex = -1;

    for (int i = 0; i < ds.size(); i++ ) {
      if (ds.getStartTime(i) < rthOpen) continue; // Ignore bars before session open
        if (-1 == rthStartIndex) rthStartIndex = i;
//      List<Tick> t = instrument.getTicks(ds.getStartTime(i), ds.getEndTime(i));
//
//      if (null == deltas.get(i)) {
//        debug("No Delta Found for index " + i + "...Calculating");
//        deltas.put(i, getDeltaForTicks(t));
//      } else {
//        int d = deltas.get(i);
//        debug("Found Delta " + d + " for Data Series index " + i);
//      }
//      int d = deltas.get(i);

      //debug("CALCULATED DELTA: " + d);
      //debug("Volume for candle " + i + ": " + ds.getVolume(i));
        int d = ds.getInt(i, "Delta");

      if (d < 0 && d < minDelta) {
          minDelta = d;
          minIndex = i;
      } else if (d > 0 && d > maxDelta) {
          maxDelta = d;
          maxIndex = i;
      }
    }

    debug ("MAX POSITIVE DELTA: " + maxDelta);
    debug ("MAX POSITIVE DELTA BAR INDEX: " + maxIndex);
    debug ("MAX NEGATIVE DELTA: " + minDelta);
    debug ("MAX NEGATIVE DELTA BAR INDEX: " + minIndex);

    boolean isMax = Math.abs(maxDelta) > Math.abs(minDelta);
    if (isMax) {
      debug("Greatest Delta is POSITIVE");
    } else {
      debug("Greatest Delta is NEGATIVE");
    }

    //int delta = getDeltaForTicks(ticks);
    //debug("CALCULATED DELTA: " + delta);

    Marker rthStartArrow = new Marker(new Coordinate(ds.getStartTime(rthStartIndex), ds.getClose(rthStartIndex) - 4), Enums.MarkerType.ARROW);
    Marker arrow1 = new Marker(new Coordinate(ds.getStartTime(maxIndex), ds.getClose(maxIndex) - 4), Enums.MarkerType.ARROW);
    arrow1.setSize(Enums.Size.MEDIUM);
    Marker arrow2 = new Marker(new Coordinate(ds.getStartTime(minIndex), ds.getClose(minIndex) - 4), Enums.MarkerType.ARROW);
    arrow2.setSize(Enums.Size.MEDIUM);
    arrow2.setPosition(Enums.Position.BOTTOM);

    long sdpStartTime = ds.getStartTime(minIndex);
    double sdpHigh = ds.getHigh(minIndex);
    double sdpLow = ds.getLow(minIndex);
    double sdpValue = ds.getHigh(minIndex) - ((ds.getHigh(minIndex) - ds.getLow(minIndex)) / 2);
    debug ("Using SDP Value: " + sdpValue);
    debug ("Using SDP High: " + sdpHigh);
    debug ("Using SDP Low: " + sdpLow);
    com.motivewave.platform.sdk.draw.Line sdpLine = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpValue), new Coordinate(ds.getStartTime(minIndex+1), sdpValue));
    sdpLine.setColor(Color.YELLOW);
    sdpLine.setStroke(new BasicStroke(4));
    sdpLine.setExtendRightBounds(true);
    sdpLine.setText("SDP " + sdpValue, ctx.getDefaults().getFont());

    com.motivewave.platform.sdk.draw.Line sdpHighLine = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpHigh), new Coordinate(ds.getStartTime(minIndex+1), sdpHigh));
    sdpHighLine.setColor(Color.YELLOW);
    sdpHighLine.setStroke(new BasicStroke(2));
    sdpHighLine.setExtendRightBounds(true);
    sdpHighLine.setText("SDP High " + sdpHigh, ctx.getDefaults().getFont());

    com.motivewave.platform.sdk.draw.Line sdpLowLine = new com.motivewave.platform.sdk.draw.Line(new Coordinate(sdpStartTime, sdpLow), new Coordinate(ds.getStartTime(minIndex+1), sdpLow));
    sdpLowLine.setColor(Color.YELLOW);
    sdpLowLine.setStroke(new BasicStroke(2));
    sdpLowLine.setExtendRightBounds(true);
    sdpLowLine.setText("SDP Low " + sdpLow, ctx.getDefaults().getFont());


    addFigure(sdpLine);
    addFigure(sdpHighLine);
    addFigure(sdpLowLine);
    addFigure(rthStartArrow);
    addFigure(arrow1);
    addFigure(arrow2);
  }

  public int getDeltaForTicks(List<Tick> ticks)
  {
      int delta = 0;
      for (Tick tick : ticks) {
        if (tick.isAskTick()) {
          delta += tick.getVolume();
        } else
          delta -= tick.getVolume();
      }

      return delta;
  }

  // This class is responsible for the rendering of the trend line
  // It also allows for user selection
  private class Line extends Figure
  {
    Line() {}
    
    // This method enables the user to select the line when the click on it.
    // We will check to see if the mouse is 6 pixels away from the trend line
    @Override
    public boolean contains(double x, double y, DrawContext ctx)
    {
      return line != null && Util.distanceFromLine(x, y, line) < 6;
    }

    // Create a line using the start and end resize points.
    // We also need to handle the cases where the user extends the line to the right and/or left
    @Override
    public void layout(DrawContext ctx)
    {
      var start = ctx.translate(startResize.getLocation());
      var end = ctx.translate(endResize.getLocation());
      // Its possible that the start resize point has been moved past the end resize
      // If this is the case, just reverse the points...
      if (start.getX() > end.getX()) {
        var tmp = end;
        end = start;
        start = tmp;
      }
      
      var gb = ctx.getBounds(); // this is the bounds of the graph
      double m = Util.slope(start, end); // calculate the slope, using a utility function for this
      
      if (getSettings().getBoolean(EXT_LEFT)) start = calcPoint(m, end, gb.getX(), gb);
      if (getSettings().getBoolean(EXT_RIGHT)) end = calcPoint(m, start, gb.getMaxX(), gb);

      line = new Line2D.Double(start, end);
    }

    // Extend the line to the given x coordinate using the simple slope formula: y = mx + b
    // This also handles the case where we have a vertical line (infinite slope)
    private Point2D calcPoint(double m, Point2D p, double x, Rectangle gb)
    {
      double y = 0;
      if (m == Double.POSITIVE_INFINITY) {
        y = gb.getMaxY();
        x = p.getX();
      }
      else if ( m == Double.NEGATIVE_INFINITY) {
        y = gb.getMinY();
        x = p.getX();
      }
      else {
        // y = mx + b
        double b = p.getY() - (m * p.getX());
        y = m*x + b;
      }
      return new Point2D.Double(x, y);
    }

    // Draw the line using the settings in the PATH variable
    @Override
    public void draw(Graphics2D gc, DrawContext ctx)
    {
      var path = getSettings().getPath(Inputs.PATH);
      // Provide feedback to the user by making the line bolder when it is selected by the user (ie use the selected stroke).
      gc.setStroke(ctx.isSelected() ? path.getSelectedStroke() : path.getStroke());
      gc.setColor(path.getColor());
      gc.draw(line);
    }
    
    private Line2D line;
  }

  private       Map<Integer, Integer> deltas = new HashMap<Integer, Integer>();
  private long rthOpen; // previous RTH Open Timestamp
  private ResizePoint startResize, endResize;
  private Line trendLine;
  private Box box;
}
