package zones;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.List;

import main.java.be.humphreys.voronoi.GraphEdge;

import simulation.Bot;
import simulation.Shout;
import simulation.Survivor;
import util.shapes.Circle2D;


public class SafeZone extends Zone {

	private static final long serialVersionUID = 1L;
	private static final Color SafeZoneColor = new Color(34, 139, 34);

	public SafeZone(List<GraphEdge> _sides, int _zoneID, Point2D center, BoundingBox bbox) {
		super(_sides, _zoneID, center, bbox);
	}	
	
	public SafeZone(Zone other) {
		super(other);
	}
	
	
	@Override
	public Shout getShout(Survivor shouter) {
		//for now, the shout is a circle of the default radius
		
		//return the circular shout
		return new Shout(new Circle2D(shouter.getCenterLocation(), Shout.DEFAULT_SHOUT_RADIUS), shouter);	
	}

	@Override
	public boolean isObstacle() {
		return false;
	}

	@Override
	public double getAudibleRange() {
		return Bot.DEFAULT_AUDITORY_RADIUS;
	}

	@Override
	public double getBroadcastRange() {
		return Bot.DEFAULT_BROADCAST_RADIUS;
	}

	@Override
	public double getFoundRange() {
		return Bot.DEFAULT_FOUND_RANGE;
	}

	@Override
	public double getVisiblityRange() {
		return Bot.DEFAULT_VISIBILITY_RADIUS;
	}

	@Override
	public boolean causesRepulsion() {
		return false;
	}

	@Override
	public double repulsionMinDist() {
		return 0;
	}

	@Override
	public double repulsionMaxDist() {
		return 0;
	}

	@Override
	public double repulsionCurveShape() {
		return 0;
	}

	@Override
	public double repulsionScalingFactor() {
		return 0;
	}

	@Override
	public Color getColor() {
		return SafeZoneColor;
	}

	@Override
	public double getPathWeightPerPixel() {
		return 1;
	}
}
