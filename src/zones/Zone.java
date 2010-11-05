package zones;

import java.awt.Color;
import java.awt.Polygon;
import java.awt.geom.Point2D;

import simulation.Shout;
import simulation.Survivor;
import util.shapes.Circle2D;

public abstract class Zone extends Polygon {

	private static final long serialVersionUID = 1L;
	
	protected int zoneID;
	protected Color zoneColor;
	
	protected Zone(int[] xPoints, int[] yPoints, int numPoints, int _zoneID, Color _zoneColor) {
		super(xPoints, yPoints, numPoints);
		zoneID = _zoneID;
		zoneColor = _zoneColor;
	}
	
	protected Zone(int _zoneID, Color _zoneColor) {
		super();
		zoneID = _zoneID;
	}
		
	public Color getColor() {
		return zoneColor;
	}
	
	public int getID() {
		return zoneID;
	}
	
	public double getCenterX() {
		return this.getBounds2D().getCenterX();
	}
	
	public double getCenterY() {
		return this.getBounds2D().getCenterY();
	}
	
	public Point2D getCenterLocation() {
		return new Point2D.Double(getCenterX(), getCenterY());
	}
	
	public double getBotMaxVelocitySquared() {
		return getBotMaxVelocity() * getBotMaxVelocity();
	}

	protected void setColor(Color newColor) {
		zoneColor = newColor;
	}
	
	
	public abstract Circle2D getBroadcastRange(Point2D originator);
	public abstract Circle2D getVisibilityRange(Point2D originator);
	public abstract Circle2D getAudibleRange(Point2D originator);
	public abstract Shout getShout(Survivor shouter);
	public abstract Circle2D getFoundRange(Point2D originator);
	public abstract double getBotMaxVelocity();
	public abstract boolean isObstacle();
}
