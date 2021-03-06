/*
 * org.openmicroscopy.shoola.util.roi.figures.MeasureBezierFigure 
 *
  *------------------------------------------------------------------------------
 *  Copyright (C) 2006-2007 University of Dundee. All rights reserved.
 *
 *
 * 	This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *------------------------------------------------------------------------------
 */
package org.openmicroscopy.shoola.util.roi.figures;


//Java imports
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

//Third-party libraries
import org.jhotdraw.draw.AbstractAttributedFigure;
import org.jhotdraw.draw.FigureListener;
import org.jhotdraw.geom.BezierPath;

//Application-internal dependencies
import org.openmicroscopy.shoola.util.roi.model.annotation.AnnotationKeys;
import org.openmicroscopy.shoola.util.roi.model.annotation.MeasurementAttributes;
import org.openmicroscopy.shoola.util.roi.model.ROI;
import org.openmicroscopy.shoola.util.roi.model.ROIShape;
import org.openmicroscopy.shoola.util.roi.model.util.MeasurementUnits;
import org.openmicroscopy.shoola.util.ui.UIUtilities;
import org.openmicroscopy.shoola.util.ui.drawingtools.figures.BezierTextFigure;
import org.openmicroscopy.shoola.util.ui.drawingtools.figures.FigureUtil;

/** 
 * Bezier figure with measurement.
 *
 * @author  Jean-Marie Burel &nbsp;&nbsp;&nbsp;&nbsp;
 * 	<a href="mailto:j.burel@dundee.ac.uk">j.burel@dundee.ac.uk</a>
 * @author	Donald MacDonald &nbsp;&nbsp;&nbsp;&nbsp;
 * 	<a href="mailto:donald@lifesci.dundee.ac.uk">donald@lifesci.dundee.ac.uk</a>
 * @version 3.0
 * <small>
 * (<b>Internal version:</b> $Revision: $Date: $)
 * </small>
 * @since OME3.0
 */
public class MeasureBezierFigure 
	extends BezierTextFigure
	implements ROIFigure
{
	
	/** Is this figure read only. */
	private boolean readOnly;

	/** Is this figure a client object. */
	private boolean clientObject;

	/** has the figure been modified. */
	private boolean dirty;
	
	/** The list of X coordinates of the nodes on the line. */
	private List<Double>			pointArrayX;

	/** The list of Y coordinates of the nodes on the line. */
	private List<Double>			pointArrayY;
	
	/** The list of lengths of sections on the line. */
	private List<Double>			lengthArray;
	
	/** The bounds of the bezier figure. */
	private	Rectangle2D bounds;
	
	/** The ROI containing the ROIFigure which in turn contains this Figure. */
	protected 	ROI					roi;

	/** The ROIFigure contains this Figure. */
	protected 	ROIShape 			shape;
	
	/** The Measurement units, and values of the image. */
	private MeasurementUnits 		units;
		
	/** 
	 * The status of the figure i.e. {@link ROIFigure#IDLE} or 
	 * {@link ROIFigure#MOVING}. 
	 */
	private int 					status;
	
	/**
	 * Returns the number of points(pixels) on the polyline.
	 * 
	 * @return See above.
	 */
	private int getLineSize()
	{
		int total = 0;
		Point2D pt1, pt2;
		for (int i = 0 ; i < getNodeCount()-1; i++)
		{
			pt1 = getPoint(i);
			pt2 = getPoint(i+1);
			iterateLine(new Line2D.Double(pt1, pt2), total);
		}
		return total;
	}
	
	/**
	 * Returns the points(pixels) on the polyline return this as an array.
	 * 
	 * @return See above.
	 */
	private List<Point> getLinePoints()
	{
		List<Point> vector = new ArrayList<Point>();
		Point2D pt1, pt2;
		for (int i = 0 ; i < getNodeCount()-1; i++)
		{
			pt1 = getPoint(i);
			pt2 = getPoint(i+1);
			iterateLine(new Line2D.Double(pt1, pt2), vector);
		}
		return vector;
	}
		
	/**
	 * Iterates the line to get the points under it.
	 * 
	 * @param line the line to iterate.
	 * @param vector the vector to add the point to.
	 */
	private void iterateLine(Line2D line, List<Point> vector)
	{
		Point2D start = line.getP1();
		Point2D end = line.getP2();
		Point2D m = new Point2D.Double(end.getX()-start.getX(),
				end.getY()-start.getY());
		double lengthM = (Math.sqrt(m.getX()*m.getX()+m.getY()*m.getY()));
		Point2D mNorm = new Point2D.Double(m.getX()/lengthM,m.getY()/lengthM);
		LinkedHashMap<Point2D, Boolean> 
		map = new LinkedHashMap<Point2D, Boolean>();
		Point2D pt;
		Point2D quantisedPoint;
		for (double i = 0 ; i < lengthM ; i+=0.1)
		{
			pt = new Point2D.Double(start.getX()+i*mNorm.getX(),
				start.getY()+i*mNorm.getY());
			quantisedPoint = new Point2D.Double(Math.floor(pt.getX()), 
				Math.floor(pt.getY()));
			if (!map.containsKey(quantisedPoint))
				map.put(quantisedPoint, Boolean.TRUE);
		}
		Iterator<Point2D> i = map.keySet().iterator();
		while (i.hasNext())
		{
			pt  = i.next();
			vector.add(new Point((int) pt.getX(), (int) pt.getY()));
		}
	}
	
	/**
	 * Iterates the line to get the points under it.
	 * 
	 * @param line The line to iterate.
	 * @param total The total number of points.
	 */
	private void iterateLine(Line2D line, int total)
	{
		Point2D start = line.getP1();
		Point2D end = line.getP2();
		Point2D m = new Point2D.Double(end.getX()-start.getX(),
				end.getY()-start.getY());
		double lengthM = (Math.sqrt(m.getX()*m.getX()+m.getY()*m.getY()));
		Point2D mNorm = new Point2D.Double(m.getX()/lengthM,m.getY()/lengthM);
		Map<Point2D, Boolean> map = new HashMap<Point2D, Boolean>();
		Point2D pt;
		Point2D quantisedPoint;
		for (double i = 0 ; i < lengthM ; i+=0.1)
		{
			pt = new Point2D.Double(start.getX()+i*mNorm.getX(),
				start.getY()+i*mNorm.getY());
			quantisedPoint = new Point2D.Double(Math.floor(pt.getX()), 
				Math.floor(pt.getY()));
			if (!map.containsKey(quantisedPoint))
				map.put(quantisedPoint, Boolean.TRUE);
		}
		total += map.size();
	}
	
	/** Creates an instance of the Bezier figure. */
	public MeasureBezierFigure()
	{
		this(false, false, true);
	}
	
	/** Creates an instance of the Bezier figure. */
	public MeasureBezierFigure(boolean closed)
	{
		this(closed, false, true);
	}

	/**
	 * Creates an instance of the Bezier figure.
	 * 
	 * @param closed Pass <code>true</code> if the figure is a polygon,
	 * 				 <code>false</code> if it is a polyline.
	 */
	public MeasureBezierFigure(boolean closed, boolean readOnly, 
			boolean clientObject)
	{
		this(ROIFigure.DEFAULT_TEXT, closed, readOnly, clientObject);
	}
	
	/**
	 * Creates an instance of the Bezier figure (closed).
	 * 
	 * @param text The string displayed in the figure. 
	 */
	public MeasureBezierFigure(String text)
	{
		this(text, false, false, true);
	}
	
	/**
	 * Creates an instance of the Bezier figure (closed).
	 * 
	 * @param text The string displayed in the figure. 
	 * @param closed Pass <code>true</code> if the figure is a polygon,
	 * 				 <code>false</code> if it is a polyline.
	 */
	public MeasureBezierFigure(String text, boolean closed)
	{
		this(text, closed, false, true);
	}
	
	/**
	 * Creates an instance of the bezier figure.
	 * 
	 * @param text 	 The string displayed in the figure. 
	 * @param closed Pass <code>true</code> if the figure is a polygon,
	 * 				 <code>false</code> if it is a polyline.
	 * @param readOnly The figure is read only.
	 * @param clientObject The figure is a client object.
	 */
	public MeasureBezierFigure(String text, boolean closed, boolean readOnly, 
			boolean clientObject)
	{
		super(text, closed);
		setAttribute(MeasurementAttributes.FONT_FACE, DEFAULT_FONT);
		setAttribute(MeasurementAttributes.FONT_SIZE, new Double(FONT_SIZE));
		this.readOnly = readOnly;
		pointArrayX = new ArrayList<Double>();
		pointArrayY = new ArrayList<Double>();
		lengthArray = new ArrayList<Double>();
		status = IDLE;
		setReadOnly(readOnly);
		setClientObject(clientObject);
	}

    /**
     * Draws the figure on the graphics context.
     * 
     * @param g The graphics context.
     */
	public void draw(Graphics2D g)
	{
		super.draw(g);
		if (MeasurementAttributes.SHOWMEASUREMENT.get(this) || 
				MeasurementAttributes.SHOWID.get(this))
		{
			if (isClosed())
			{
				NumberFormat formatter = new DecimalFormat(FORMAT_PATTERN);
				String polygonArea = formatter.format(getArea());
				polygonArea = addAreaUnits(polygonArea);
				double sz = ((Double) this.getAttribute(
							MeasurementAttributes.FONT_SIZE));
				g.setFont(new Font(FONT_FAMILY, FONT_STYLE, (int) sz));
				bounds = g.getFontMetrics().getStringBounds(polygonArea, g);
				bounds = new Rectangle2D.Double(
						this.getBounds().getCenterX()-bounds.getWidth()/2,
					this.getBounds().getCenterY()+bounds.getHeight()/2,
					bounds.getWidth(), bounds.getHeight());
				if (MeasurementAttributes.SHOWMEASUREMENT.get(this))
				{
					g.setColor(
							MeasurementAttributes.MEASUREMENTTEXT_COLOUR.get(
									this));
					g.drawString(polygonArea, (int) bounds.getX(), 
							(int) bounds.getY());
				}
				if (MeasurementAttributes.SHOWID.get(this))
				{
					g.setColor(this.getTextColor());
					g.drawString(this.getROI().getID()+"", (int) bounds.getX(), 
							(int) bounds.getY());
				}
			}
			else
			{
				NumberFormat formatter = new DecimalFormat(FORMAT_PATTERN);
				String polygonLength = formatter.format(getLength());
				polygonLength = addLineUnits(polygonLength);
				double sz = (Double) 
						getAttribute(MeasurementAttributes.FONT_SIZE);
				g.setFont(new Font(FONT_FAMILY, FONT_STYLE, (int) sz));
				bounds = g.getFontMetrics().getStringBounds(polygonLength, g);
				
				if (super.getNodeCount() > 1)
				{
					int midPoint = this.getNodeCount()/2-1;
					if(midPoint<0)
						midPoint = 0;
					Point2D p0 = getPoint(midPoint);
					Point2D p1 = getPoint(midPoint+1);
					double x, y;
					x = Math.min(p0.getX(),p1.getX())+Math.abs(p0.getX()
							-p1.getX());
					y = Math.min(p0.getY(),p1.getY())+Math.abs(p0.getY()
							-p1.getY());
					bounds = new Rectangle2D.Double(x-bounds.getWidth()/2,
							y+bounds.getHeight()/2,
							bounds.getWidth(), bounds.getHeight());
					if (MeasurementAttributes.SHOWMEASUREMENT.get(this))
					{
						g.setColor(
						MeasurementAttributes.MEASUREMENTTEXT_COLOUR.get(this));
						g.drawString(
								polygonLength+"", (int) path.getCenter().getX(), 
								(int) path.getCenter().getY());
					}
					if (MeasurementAttributes.SHOWID.get(this))
					{
						g.setColor(this.getTextColor());
						g.drawString(this.getROI().getID()+"", 
								(int) path.getCenter().getX(), 
								(int) path.getCenter().getY());
					}
				}
			}
		}
	}

	/**
	 * Calculates the bounds of the rendered figure, including the text 
	 * rendered.
	 *  
	 * @return See above.
	 */
	public Rectangle2D.Double getDrawingArea()
	{
		Rectangle2D.Double newBounds = super.getDrawingArea();
		if (bounds == null) return newBounds;
		double diff;
		if (newBounds.getX() > bounds.getX())
		{
			diff = newBounds.x-bounds.getX();
			newBounds.x = bounds.getX();
			newBounds.width = newBounds.width+diff;
		}
		if (newBounds.getY() > bounds.getY())
		{
			diff = newBounds.y-bounds.getY();
			newBounds.y = bounds.getY();
			newBounds.height = newBounds.height+diff;
		}
		if (bounds.getX()+bounds.getWidth() > 
			newBounds.getX()+newBounds.getWidth())
		{
			diff = bounds.getX()+bounds.getWidth()-newBounds.getX()
				+newBounds.getWidth();
			newBounds.width = newBounds.width+diff;
		}
		if (bounds.getY()+bounds.getHeight() > 
			newBounds.getY()+newBounds.getHeight())
		{
			diff = bounds.getY()+bounds.getHeight()-newBounds.getY()
				+newBounds.getHeight();
			newBounds.height = newBounds.height+diff;
		}
		return newBounds;
	}
	
	/**
	 * Add degrees to the measurements. 
	 * @param str the measurement.
	 * @return see above.
	 */
	public String addDegrees(String str)
	{
		return str + UIUtilities.DEGREES_SYMBOL;
	}
	
	/**
	 * Add length unit in pixels or microns to the measurements. 
	 * 
	 * @param str the measurement.
	 * @return see above.
	 */
	public String addLineUnits(String str)
	{
		if (shape == null) return str;
		
		if (units.isInMicrons()) return str+UIUtilities.MICRONS_SYMBOL;
		return str+UIUtilities.PIXELS_SYMBOL;
	}
	
	/**
	 * Adds area ^2 to the measurements. 
	 * 
	 * @param str the measurement.
	 * @return see above.
	 */
	public String addAreaUnits(String str)
	{
		if (shape == null) return str;
		if (units.isInMicrons())
			return str+UIUtilities.MICRONS_SYMBOL+UIUtilities.SQUARED_SYMBOL;
		return str+UIUtilities.PIXELS_SYMBOL+UIUtilities.SQUARED_SYMBOL;
	}
	
	/**
	 * Gets the point i in pixels or microns depending on the units used.
	 * 
	 * @param i node
	 * @return see above.
	 */
	private Point2D.Double getPt(int i)
	{
		Point2D.Double pt = getNode(i).getControlPoint(0); 
		if (units.isInMicrons())
			return new Point2D.Double(	pt.getX()*units.getMicronsPixelX(), 
										pt.getY()*units.getMicronsPixelY());
		return pt;
	}
	
	/**
	 * Calculates the length of the line.
	 * 
	 * @return see above.
	 */
	public double getLength()
	{
		double length = 0;
		Point2D p0, p1;
		for (int i = 0 ; i < path.size()-1 ; i++)
		{
			p0 = path.get(i).getControlPoint(0);
			p1 = path.get(i+1).getControlPoint(0);
			length += p0.distance(p1);
		}
		return length;
	}
	
	/** 
	 * Returns the number of points.
	 * 
	 * @return see above. 
	 */
	public int getPointCount() { return this.getPoints().size(); }
	
	/**
	 * Calculates the centre of the object (in pixels, or microns). 
	 * 
	 * @return see above.
	 */
	public Point2D getCentre()
	{
		if (units.isInMicrons())
		{
			Point2D.Double pt1 =  path.getCenter();
			pt1.setLocation(pt1.getX()*units.getMicronsPixelX(), 
					pt1.getY()*units.getMicronsPixelY());
			return pt1;
		}
		return path.getCenter();
	}
	
	/**
	 * Returns the Area of the object, in Pixels or microns.
	 * 
	 * @return see above.
	 */
	public double getArea()
	{
		double area = 0;
		Point2D centre = getCentre();
		Point2D p0, p1;
		for (int i = 0 ; i < path.size() ; i++)
		{
			p0 = getPt(i);
			if (i == path.size()-1) p1 = getPt(0);
			else p1 = getPt(i+1);
		
			p0.setLocation(p0.getX()-centre.getX(), p0.getY()-centre.getY());
			p1.setLocation(p1.getX()-centre.getX(), p1.getY()-centre.getY());
			area += (p0.getX()*p1.getY()-p1.getX()*p0.getY());
		}
		return Math.abs(area/2);
	}

	/** 
	 * Remove a node from the Bezier figure.
	 * @param index node to remove.
	 */
	public void measureBasicRemoveNode(int index)
	{
		this.removeNode(index);
	}
	
	/**
	 * Implemented as specified by the {@link ROIFigure} interface.
	 * @see ROIFigure#getROI()
	 */
	public ROI getROI() { return roi; }

	/**
	 * Implemented as specified by the {@link ROIFigure} interface.
	 * @see ROIFigure#getROIShape()
	 */
	public ROIShape getROIShape() { return shape; }
	
	/**
	 * Implemented as specified by the {@link ROIFigure} interface.
	 * @see ROIFigure#setROI(ROI)
	 */
	public void setROI(ROI roi) { this.roi = roi; }

	/**
	 * Implemented as specified by the {@link ROIFigure} interface.
	 * @see ROIFigure#setROIShape(ROIShape)
	 */
	public void setROIShape(ROIShape shape) { this.shape = shape; }

	/**
	 * Implemented as specified by the {@link ROIFigure} interface.
	 * @see ROIFigure#calculateMeasurements()
	 */
	public void calculateMeasurements() 
	{
		if (shape == null) return;
		if (getNodeCount() < 2) return;
		pointArrayX.clear();
		pointArrayY.clear();

		for (int i = 0 ; i < path.size(); i++)
		{
			pointArrayY.add(path.get(i).getControlPoint(0).getY());
		}
		AnnotationKeys.POINTARRAYX.set(shape, pointArrayX);
		AnnotationKeys.POINTARRAYY.set(shape, pointArrayY);
		if (super.isClosed())
		{
			AnnotationKeys.AREA.set(shape,getArea());
			AnnotationKeys.PERIMETER.set(shape, getLength());
			AnnotationKeys.CENTREX.set(shape, getCentre().getX());
			AnnotationKeys.CENTREY.set(shape, getCentre().getY());
		}
		else
		{
			lengthArray.clear();
			lengthArray.add(getLength());
			AnnotationKeys.LENGTH.set(shape, lengthArray);
			AnnotationKeys.CENTREX.set(shape, getCentre().getX());
			AnnotationKeys.CENTREY.set(shape, getCentre().getY());
			AnnotationKeys.STARTPOINTX.set(shape, getPt(0).getX());
			AnnotationKeys.STARTPOINTX.set(shape, getPt(0).getY());
			AnnotationKeys.ENDPOINTX.set(shape, getPt(path.size()-1).getX());
			AnnotationKeys.ENDPOINTY.set(shape, getPt(path.size()-1).getY());
		}
	}
	
	
	
	/**
	 * Returns the points(pixels) in the polygon return this as an array.
	 * 
	 * @return See above.
	 */
	private List<Point> getAreaPoints()
	{
		Rectangle r = path.getBounds();
		double iX = Math.floor(r.getX());
		double iY = Math.floor(r.getY());
		List<Point> vector = new ArrayList<Point>();
		path.toPolygonArray();
		Point2D point = new Point2D.Double(0,0);
		for (int x = 0 ; x < Math.ceil(r.getWidth()); x++)
		{
			for ( int y = 0 ; y < Math.ceil(r.getHeight()) ; y++)
			{
				point.setLocation(iX+x, iY+y);
				if (path.contains(point))
					vector.add(new Point((int) point.getX(), (int)  
							point.getY()));
			}
		}
		return vector;
	}

	/**
	 * Returns the number of points(pixels) in the polygon.
	 * 
	 * @return See above.
	 */
	private int getAreaSize()
	{
		Rectangle r = path.getBounds();
		double iX = Math.floor(r.getX());
		double iY = Math.floor(r.getY());
		int total = 0;
		path.toPolygonArray();
		Point2D point = new Point2D.Double(0,0);
		for (int x = 0 ; x < Math.ceil(r.getWidth()); x++)
		{
			for ( int y = 0 ; y < Math.ceil(r.getHeight()) ; y++)
			{
				point.setLocation(iX+x, iY+y);
				if (path.contains(point))
					total++;
			}
		}
		return total;
	}
	
	/**
	 * Implemented as specified by the {@link ROIFigure} interface.
	 * @see ROIFigure#getType()
	 */
	public String getType()
	{
		if (isClosed()) return FigureUtil.POLYGON_TYPE;
		return FigureUtil.SCRIBBLE_TYPE;
	}

	/**
	 * Implemented as specified by the {@link ROIFigure} interface.
	 * @see ROIFigure#setMeasurementUnits(MeasurementUnits)
	 */
	public void setMeasurementUnits(MeasurementUnits units)
	{
		this.units = units;
	}
	
	/**
	 * Implemented as specified by the {@link ROIFigure} interface.
	 * @see ROIFigure#getPoints()
	 */
	public List<Point> getPoints()
	{
		if (isClosed()) return getAreaPoints();
		return getLinePoints();
	}
	
	/**
	 * Implemented as specified by the {@link ROIFigure} interface.
	 * @see ROIFigure#getSize()
	 */
	public int getSize()
	{
		if (isClosed()) return getAreaSize();
		return getLineSize();
	}
		
	/**
	 * Implemented as specified by the {@link ROIFigure} interface.
	 * @see ROIFigure#setStatus(int)
	 */
	public void setStatus(int status) { this.status = status; }
	
	/**
	 * Implemented as specified by the {@link ROIFigure} interface.
	 * @see ROIFigure#getStatus()
	 */
	public int getStatus() { return status; }
	
	/**
	 * Implemented as specified by the {@link ROIFigure} interface.
	 * @see ROIFigure#isReadOnly()
	 */
	public boolean isReadOnly() { return readOnly;}
	
	/**
	 * Implemented as specified by the {@link ROIFigure} interface.
	 * @see ROIFigure#setReadOnly(boolean)
	 */
	public void setReadOnly(boolean readOnly) 
	{ 
		this.readOnly = readOnly; 
		setEditable(!readOnly);
	}

	/**
	 * Overridden to stop updating shape if read only
	 * @see AbstractAttributedFigure#transform(AffineTransform)
	 */
	public void transform(AffineTransform tx)
	{
		if (!readOnly)
		{
			super.transform(tx);
			this.setObjectDirty(true);
		}
	}
	
	/**
	 * Overridden to stop updating shape if read only.
	 * @see AbstractAttributedFigure#setBounds(Point2D, Point2D)
	 */
	public void setBounds(Point2D.Double anchor, Point2D.Double lead) 
	{
		if (!readOnly)
		{
			super.setBounds(anchor, lead);
			this.setObjectDirty(true);
		}
	}
	
	/**
	 * Overridden to return the correct handles.
	 * @see AbstractAttributedFigure#createHandles(int)
	 */
	/* cannot do that otherwise enter in an infinite loop
	public Collection<Handle> createHandles(int detailLevel) 
	{
		if (!readOnly)
			return super.createHandles(detailLevel);
		else
		{
			LinkedList<Handle> handles = new LinkedList<Handle>();
			handles.add(new FigureSelectionHandle(this));
			return handles;
		}
	}
	*/
	
	/**
	 * Implemented as specified by the {@link ROIFigure} interface
	 * @see ROIFigure#isClientObject()
	 */
	public boolean isClientObject() 
	{
		return clientObject;
	}

	/**
	 * Implemented as specified by the {@link ROIFigure} interface
	 * @see ROIFigure#setClientObject(boolean)
	 */
	public void setClientObject(boolean clientSide) 
	{
		clientObject = clientSide;
	}

	/**
	 * Implemented as specified by the {@link ROIFigure} interface
	 * @see ROIFigure#isDirty()
	 */
	public boolean isDirty() 
	{
		return dirty;
	}

	/**
	 * Implemented as specified by the {@link ROIFigure} interface
	 * @see ROIFigure#setObjectDirty(boolean)
	 */
	public void setObjectDirty(boolean dirty) 
	{
		this.dirty = dirty;
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.openmicroscopy.shoola.util.ui.drawingtools.figures.
	 * BezierFigure#clone()
	 */
	public MeasureBezierFigure clone()
	{
		MeasureBezierFigure that = (MeasureBezierFigure) super.clone();
		that.setReadOnly(this.isReadOnly());
		that.setClientObject(this.isClientObject());
		that.setObjectDirty(true);
		return that;
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.openmicroscopy.shoola.util.ui.drawingtools.figures.
	 * BezierFigure#setClosed(boolean)
	 */
	public void setClosed(boolean newValue) 
	{
		super.setClosed(newValue);
		this.setObjectDirty(true);
	}

	/*
	 * (non-Javadoc)
	 * @see org.openmicroscopy.shoola.util.ui.drawingtools.figures.
	 * BezierFigure#setBezierPath(BezierPath)
	 */
	public void setBezierPath(BezierPath newValue) 
	{
		super.setBezierPath(newValue);
		this.setObjectDirty(true);
	}
	   
	/*
	 * (non-Javadoc)
	 * @see org.openmicroscopy.shoola.util.ui.drawingtools.figures.
	 * BezierFigure#setEndPoint(Point2D)
	 */
	public void setEndPoint(Point2D.Double p) 
	{
		super.setEndPoint(p);
		this.setObjectDirty(true);
	}

	/*
	 * (non-Javadoc)
	 * @see org.openmicroscopy.shoola.util.ui.drawingtools.figures.
	 * BezierFigure#setNode(Point2D)
	 */
	public void setNode(int index, BezierPath.Node p) 
	{
		super.setNode(index, p);
		this.setObjectDirty(true);
	}

	/*
	 * (non-Javadoc)
	 * @see org.openmicroscopy.shoola.util.ui.drawingtools.figures.
	 * BezierFigure#setPoint(int, int, Point2D)
	 */
	public void setPoint(int index, int coord, Point2D.Double p) 
	{
		super.setPoint(index, coord, p);
		this.setObjectDirty(true);
	}

	/*
	 * (non-Javadoc)
	 * @see org.openmicroscopy.shoola.util.ui.drawingtools.figures.
	 * BezierFigure#setStartPoint(Point2D)
	 */
	public void setStartPoint(Point2D.Double p) 
	{
		super.setStartPoint(p);
		this.setObjectDirty(true);
	}

	/*
	 * (non-Javadoc)
	 * @see org.openmicroscopy.shoola.util.ui.drawingtools.figures.
	 * BezierFigure#splitSegment(Point2D)
	 */
	public int splitSegment(Point2D.Double split) 
	{
		this.setObjectDirty(true);
		return super.splitSegment(split);
	}

	/*
	 * (non-Javadoc)
	 * @see org.openmicroscopy.shoola.util.ui.drawingtools.figures.
	 * BezierFigure#splitSegment(Point2D, float)
	 */
	public int splitSegment(Point2D.Double split, float tolerance) 
	{
		this.setObjectDirty(true);
		return super.splitSegment(split, tolerance);
	}

	/*
	 * (non-Javadoc)
	 * @see org.openmicroscopy.shoola.util.ui.drawingtools.figures.
	 * BezierFigure#joinSegments(Point2D, float)
	 */
	public int joinSegments(Point2D.Double join, float tolerance) 
	{
		this.setObjectDirty(true);
		return super.joinSegments(join, tolerance);
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.openmicroscopy.shoola.util.ui.drawingtools.figures.
	 * BezierTextFigure#setText(String)
	 */
	public void setText(String text)
	{
		super.setText(text);
		this.setObjectDirty(true);
	}
	
	/**
	 * Implemented as specified by the {@link ROIFigure} interface
	 * @see ROIFigure#getFigureListeners()
	 */
	public List<FigureListener> getFigureListeners()
	{
		List<FigureListener> figListeners = new ArrayList<FigureListener>();
		Object[] listeners = listenerList.getListenerList();
		for(Object listener : listeners)
			if(listener instanceof FigureListener)
				figListeners.add((FigureListener)listener);
		return figListeners;
	}

	public BezierPath.Node removeNode(int node)
	{
		return super.removeNode(node);
	}
	
}