import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.JFrame;


public class World extends JFrame {

	/***************************************************************************
	 * CONSTANTS
	 **************************************************************************/
	public static final Random RAMOM_GENERATOR = new Random();
	private static final int MENUBAR_MEIGHT = 21;
	private static final int FRAME_HEIGHT = 500 + MENUBAR_MEIGHT;
	private static final int FRAME_WIDTH = 500;
	public static final Rectangle BOUNDING_BOX = new Rectangle(0, MENUBAR_MEIGHT, FRAME_WIDTH, FRAME_HEIGHT - MENUBAR_MEIGHT);

	private static final Color BACKGROUND_COLOR = Color.white;
	private static final Color BOT_COLOR = Color.green;
	private static final Color VICTIM_COLOR = Color.red;
	private static final Color SHOUT_COLOR = new Color(30, 144, 255);
	private static final Color VISIBLE_RANGE_COLOR = new Color(255,106,106);
	private static final Color AUDIO_RANGE_COLOR = new Color(255,165,0);
	private static final Color BROADCAST_RANGE_COLOR = Color.yellow;
	private static final Color BOT_LABEL_COLOR = Color.black;
	private static final Color ZONE_LABEL_COLOR = Color.black;
	private static final Color ZONE_OUTLINE_COLOR = Color.black;
	
	private static final Font BOT_LABEL_FONT = new Font("Serif", Font.BOLD, 10);
	private static final Font ZONE_LABEL_FONT = new Font("Serif", Font.BOLD, 12);
	
	private static final long serialVersionUID = 1L;

	/** VARIABLES */
	public static CopyOnWriteArrayList<Zone> allZones; //The zones in the world - should be non-overlapping
	public static CopyOnWriteArrayList<Bot> allBots; //List of the Bots, so we can do stuff with them
	public static CopyOnWriteArrayList<Victim> allVictims; //The Victims
	
	public ListIterator<Bot> allBotSnapshot;
	public ListIterator<Victim> allVictimSnapshot;
	
	
	public World() {
		super("Swarm Simulation");
		//start with the frame.
		setupFrame();
		
		//this is with default values, mostly for debugging
		int numBots = 10;
		int numVic = 2;
		
		//initialize the zones
		allZones = new CopyOnWriteArrayList<Zone>();
		
		int[] xPointsBase = {200, 300, 300, 200};
		int[] yPointsBase = {200, 200, 300, 300};
		Zone homeBase = new BaseZone(xPointsBase, yPointsBase, 4, 0);
		allZones.add(homeBase);
		
		fillInZones();
		
		checkZoneSanity();
		
		//initialize the bots
		allBots = new CopyOnWriteArrayList<Bot>();

		for(int i = 0; i < numBots; i++) {
			allBots.add(new Bot(FRAME_WIDTH/2, FRAME_HEIGHT/2, numBots, i, homeBase));
		}
		
		//initialize the victims
		//only 2 for now, so we'll hard code them	
		allVictims = new CopyOnWriteArrayList<Victim>();
		
		allVictims.add(new Victim(FRAME_WIDTH/4.0, FRAME_HEIGHT/4.0, .5));
		allVictims.add(new Victim(FRAME_WIDTH/4.0, FRAME_HEIGHT*3.0/4.0, .5));
				
		setVisible(true);
	}
	
	private void setupFrame() {
		setSize(FRAME_WIDTH, FRAME_HEIGHT);
		setResizable(false);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setBackground(BACKGROUND_COLOR);
	}
	
	public void checkZoneSanity() {
		//check each zone's area with all the rest to make sure they don't overlap
		for(int i = 0; i < allZones.size(); i++) {
			//calculate if there are any intersections
			List<? extends Shape> intersections = findIntersections(allZones.get(i), allZones.subList(i+1, allZones.size()));
			//if there are, freak out
			if(intersections.size() > 0) {
				System.out.println("ZONES ARE NOT SANE!!!!");
				System.exit(0);
			}
		}
		
		//make sure the whole area is covered
		Area zoneArea = new Area();
		for(Zone z : allZones) {
			zoneArea.add(new Area(z));
		}
		
		if(! zoneArea.equals(new Area(BOUNDING_BOX))) {
			System.out.println("Zones don't cover all area");
			System.exit(0);
		}
		
		
	}
	
	private void fillInZones() {
		//first, get all unfilled zones
		Area filledAreas = new Area();
		for(Zone z : allZones) {
			filledAreas.add(new Area(z));
		}
		
		Area unfilledArea = new Area(BOUNDING_BOX);
		unfilledArea.subtract(filledAreas);
		
		//get all the points on the edge of the unfilled area
		PathIterator unfilledIterator = unfilledArea.getPathIterator(null);
		
		ArrayList<Point2D> borderPoints = new ArrayList<Point2D>();
		
		double[] curPoint = new double[6];
		
		
		while(! unfilledIterator.isDone()) {			
			//get that point
			unfilledIterator.currentSegment(curPoint);
			
			//there shouldn't be any curves, so we just need to store the first 2 indicies
			//so, store them
			Point2D.Double newPoint = new Point2D.Double(curPoint[0], curPoint[1]);
						
			//don't want to add multiples
			if(borderPoints.indexOf(newPoint) >= 0) {
				unfilledIterator.next();
				continue;
			}
						
			borderPoints.add(newPoint);
			
			//go to the next point
			unfilledIterator.next();
		}
		
		//now, start making arbitrary triangles and see if they overlap with any existing zones
		//if they don't, add them to the zone list
		while(! unfilledArea.isEmpty()) {
			//choose 3 points randomly
			Point2D p1 = borderPoints.remove(RAMOM_GENERATOR.nextInt(borderPoints.size()));
			Point2D p2 = borderPoints.remove(RAMOM_GENERATOR.nextInt(borderPoints.size()));
			Point2D p3 = borderPoints.remove(RAMOM_GENERATOR.nextInt(borderPoints.size()));
			
			int[] xPoints = {(int) p1.getX(), (int) p2.getX(), (int) p3.getX()};
			int[] yPoints = {(int) p1.getY(), (int) p2.getY(), (int) p3.getY()};
			
			//make a zone out of them
			Zone newZone;
			
			switch(RAMOM_GENERATOR.nextInt(2)) {
				case 0: newZone = new SafeZone(xPoints, yPoints, 3, allZones.size()); break; 
				case 1: newZone = new DangerZone(xPoints, yPoints, 3, allZones.size()); break;
				default: newZone = new SafeZone(xPoints, yPoints, 3, allZones.size()); break;  
			}
			
			//make sure it doesn't intersect any existing zones
			if(findIntersections(newZone, allZones).size() > 0) {
				borderPoints.add(p1);
				borderPoints.add(p2);
				borderPoints.add(p3);
				continue;
			}
						
			//it checks out - add it
			allZones.add(newZone);
			//remove it's area from the unfilled area
			unfilledArea.subtract(new Area(newZone));
			
			borderPoints.add(p1);
			borderPoints.add(p2);
			borderPoints.add(p3);
		}
		
		//should be all filled up now - check the sanity to make sure
		checkZoneSanity();
	}
	
	
	public void go() {
		//start all the threads
		for(Bot b : allBots){
			(new Thread(b)).start();
		}
		
		for(Victim v : allVictims) {
			(new Thread(v)).start();
		}
		
		//start a timer to repaint
		Timer t = new Timer("Repaint timer");
		t.schedule(new TimerTask() {
			public void run() {
				repaint();
			}
		}, 0, 200);
	}
	
	public void paint(Graphics g) {		
		g = getGraphics();
		Graphics2D g2d = (Graphics2D) g;
		
//		System.out.println("REPAINTING");
		
		//clear everything
		g2d.setColor(BACKGROUND_COLOR);
		g2d.fill(BOUNDING_BOX);
		
		//get a snapshot of the bots and victims
		allBotSnapshot = allBots.listIterator();
		allVictimSnapshot = allVictims.listIterator();
		
		//draw the zones
		g2d.setFont(ZONE_LABEL_FONT);
		for(Zone z : allZones) {
			g2d.setColor(z.getColor());
			g2d.fill(z);
			g2d.setColor(ZONE_LABEL_COLOR);
			g2d.drawString("" + z.getID(), (int)z.getCenterX(), (int)z.getCenterY());
			g2d.setColor(ZONE_OUTLINE_COLOR);
			g2d.draw(z);
		}
		
		//all bots should know about all shouts, so draw them all based on what the first bot knows
		Bot firstBot = allBotSnapshot.next();
		//go previous one, so that when we start to draw the bots, we'll start at the beginning
		allBotSnapshot.previous();
		
		//now, drow all of the shouts
		g2d.setColor(SHOUT_COLOR);
		for(Shout s : firstBot.getShouts()) {
			g2d.draw(s);
		}
		
		//draw all the bots and their radii and their labels
		g2d.setFont(BOT_LABEL_FONT);
		while(allBotSnapshot.hasNext()) {
			Bot curBot = allBotSnapshot.next();
			
			g2d.setColor(BOT_COLOR);
			g2d.fill(curBot);
			
			g2d.setColor(AUDIO_RANGE_COLOR);
			g2d.draw(curBot.getAuditbleRadius());
			
			g2d.setColor(VISIBLE_RANGE_COLOR);
			g2d.draw(curBot.getVisibilityRadius());
			
			g2d.setColor(BROADCAST_RANGE_COLOR);
			g2d.draw(curBot.getBroadcastRadius());
			
			g2d.setColor(BOT_LABEL_COLOR);
			g2d.drawString("" + curBot.getID(), (float) (curBot.getX()), (float) (curBot.getY() + curBot.getHeight()));
			
		}
		
		//draw all the victims
		g2d.setColor(VICTIM_COLOR);
		while(allVictimSnapshot.hasNext()) {
			Victim curVic = allVictimSnapshot.next();
			
			g2d.fill(curVic);
		}
		
	}
	
	//finds all shapes in the shapeList that intersect the base shape
	public static List<? extends Shape> findIntersections(Shape base, List<? extends Shape> shapeList) {
		//we're going to take advantage of Area's intersect method
		// so we need to turn base into an area
		Area baseArea = new Area(base);
		
		//make the list of shapes that we'll end up returning
		List<Shape> intersectingShapes = new ArrayList<Shape>();
		
		//Then, we'll go through all the shapes in the list, and see if any of them intersect the base area
		for(Shape testShape : shapeList) {
			//make an area out of testShape
			Area testArea = new Area(testShape);
			//find the intersection
			testArea.intersect(baseArea);
			//now, test area is the area of intersection
			//see if that area is empty.
			//if it is not, we have an intersection and we should add it to the list
			if(! testArea.isEmpty()) {
				intersectingShapes.add(testShape);
			}
		}
		
		//we have found all the intersecting shape
		//return the list
		return intersectingShapes;
	}
	
	//figures out which zone the passed point is in, and returns it.
	//zones should not overlap, so there should only be one solution
	public static Zone findZone(Point2D point) {
		
		for(Zone z : allZones) {
			if(z.contains(point)) {
				return z;
			}
		}
		
		return null;
	}
	
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		//make a new World
		World w = new World();
		w.go();
		
	}

}
