package simulation;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;

import util.Utilities;
import util.Vector;
import util.shapes.Circle2D;
import zones.BaseZone;
import zones.BoundingBox;
import zones.SafeZone;
import zones.Zone;


public class Bot extends Rectangle {

	private static final long serialVersionUID = -3272426964314356266L;


	/***************************************************************************
	 * CONSTANTS
	 **************************************************************************/
	private final int DIMENSION = 6;
	private final double VISUAL_ID_SURVIVOR_PROB = .70;
	private final double HEAR_SURVIVOR_PROB = .75;
	private final double ASSES_SURVIVOR_CORRECTLY_PROB = .9;
	private final double CORRECT_ZONE_ASSESMENT_PROB = .8; //the probability that the bot will asses the zones correctly

	public static final double DEFAULT_BROADCAST_RADIUS = 95;
	public static final double DEFALUT_VISIBILITY_RADIUS = 15;
	public static final double DEFAULT_AUDITORY_RADIUS = 50;
	public static final double DEFAULT_FOUND_RANGE = DEFALUT_VISIBILITY_RADIUS;
	public static final double DEFAULT_MAX_VELOCITY = 8;

	private final int ZONE_SAFE = 1;
	private final int ZONE_DANGEROUS = 2;
	private final int ZONE_BASE = 3;

	private static double REPULSION_FACTOR_FROM_OTHER_BOTS = 1000;
	private static double REPULSION_FACTOR_FROM_HOME_BASES = 2000;

	private final static int SPREAD_OUT_PHASE = 0;
	private final static int CREATE_PATHS_PHASE = 1;
	private final static int AGGRIGATE_PHASE = 2;

	private final static String BOT_LOCATION_MESSAGE = "loc";
	private final static String CLAIM_SURVIVOR_MESSAGE = "cs";
	private final static String FOUND_SURVIVOR_MESSAGE = "fs";

	private final static int MESSAGE_TIMEOUT = Integer.MAX_VALUE; //after 5 timesteps, we should be able to say ignore it

	private boolean OVERALL_BOT_DEBUG = 	true;
	private boolean LISTEN_BOT_DEBUG = 		false;
	private boolean LOOK_BOT_DEBUG = 		false;
	private boolean MESSAGE_BOT_DEBUG = 	true;
	private boolean MOVE_BOT_DEBUG = 		false;

	/***************************************************************************
	 * VARIABLES
	 **************************************************************************/



	/** These variables the bot does not know about - we store them for our convience. */
	private Zone currentZone; //what zones we actually are in can change some behavior
	private List<Shout> heardShouts; //the shouts that have been heard recently
	private final Random numGen = new Random();
	private Vector movementVector;
	private BoundingBox boundingBox;

	//	private Bot previousBot;
	private List<BotInfo> otherBotInfo; //storage of what information we know about all of the other Bots
	private List<Message> messageBuffer; //keep a buffer of messages from other robots
	private int botID;
	private int zoneAssesment; //stores the bot's assessment of what sort of zones it is in
	private Zone baseZone; //the home base zones.
	private List<Survivor> knownSurvivors; //keep a list of survivors that have already been found, so can go claim them
	//TODO handle reclaiming survivors in the case of robot death
	private List<Survivor> claimedSurvivors; //keep a list of survivors that have been claimed, so that we don't double up on one survivor
	private Survivor mySurvivor; //the survivor I have claimed - don't know if this is useful, but it might be
	private int mySurvivorClaimTime;
	private World world;
	private int algorithmPhase;
	
	private List<Point2D> previousLocations;
	private Point2D averagePreviousLocation;
	private boolean settledOnLocation;


	/***************************************************************************
	 * CONSTRUCTORS
	 **************************************************************************/
	public Bot(World _world, double centerX, double centerY, int _numBots, int _botID, Zone homeBase, BoundingBox _bounds) {
		super();

		world = _world;

		//first, in order to store our location, we need to find our top left corner
		double cornerX = centerX - DIMENSION/2;
		double cornerY = centerY - DIMENSION/2;

		//set our location and size
		setFrame(cornerX, cornerY, DIMENSION, DIMENSION);

		//now, set up the list of other bot information
		otherBotInfo = new ArrayList<BotInfo>();

		//set up other variables with default values
		messageBuffer = new ArrayList<Message>();

		heardShouts = new CopyOnWriteArrayList<Shout>();

		botID = _botID;

		baseZone = homeBase;

		knownSurvivors = new ArrayList<Survivor>();
		claimedSurvivors = new ArrayList<Survivor>();

		previousLocations = new ArrayList<Point2D>();

		boundingBox = _bounds;

		movementVector = new Vector(this.getCenterLocation(), this.getCenterLocation());

		//start in the spread out phase
		algorithmPhase = SPREAD_OUT_PHASE;

		settledOnLocation = false;

		mySurvivor = null;

		//for now, assume we're starting in a base zone
		zoneAssesment = ZONE_BASE;

		//find out what zones we start in, and try to determine how safe it is
		updateZoneInfo();
	}

	/***************************************************************************
	 * GETTERS
	 **************************************************************************/
	public Point2D getCenterLocation() {
		return new Point2D.Double(getCenterX(), getCenterY());
	}

	public Circle2D getBroadcastArea() {
		//see how far the current zones thinks we can broadcast
		return currentZone.getBroadcastArea(getCenterLocation());
	}

	public Circle2D getVisibleArea() {
		//see how far the current zones thinks we can see
		return currentZone.getVisibilityArea(getCenterLocation());
	}

	public double getVisibityRange() {
		return currentZone.getVisiblityRange(getCenterLocation());
	}

	public Circle2D getAuditbleArea() {
		//see how far the current zones thinks we can hear
		return currentZone.getAudibleArea(getCenterLocation());
	}

	public ListIterator<Shout> getShoutIterator() {
		return heardShouts.listIterator();
	}

	public double getMaxVelocity() {
		return DEFAULT_MAX_VELOCITY;
	}

	public double getObstacleBufferRange() {
		return DEFALUT_VISIBILITY_RADIUS / 4.0;
	}


	public int getID() {
		return botID;
	}

	public Vector getMovementVector() {
		return movementVector;
	}


	public void setCenterLocation(Point2D newCenterLoc) {
		//need find the new upper-left corner location
		Point newCornerLoc = new Point((int) (newCenterLoc.getX()-(DIMENSION/2.0)), (int) (newCenterLoc.getY()-(DIMENSION/2.0)));
		this.setLocation(newCornerLoc);
	}

	public static void setRepulsionConstants(double botRepulsion, double homeBaseRepulsion) {
		REPULSION_FACTOR_FROM_OTHER_BOTS = botRepulsion;
		REPULSION_FACTOR_FROM_HOME_BASES = homeBaseRepulsion;
	}	

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (!(obj instanceof Bot))
			return false;
		Bot other = (Bot) obj;
		if (botID != other.botID)
			return false;
		return true;
	}


	/***************************************************************************
	 * METHODS
	 **************************************************************************/
	public void recieveMessage(Message message) {
		messageBuffer.add(message);
	}

	private Message constructLocationMessage() {
		return new Message(this,
				BOT_LOCATION_MESSAGE + " " + this.getID() + " " + World.getCurrentTimestep() + " " + this.getCenterX() + " " + this.getCenterY() + "\n");
	}

	private Message constructFoundMessage(Survivor foundSurvivor, double surDamageAssessment) {
		return new Message(this, 
				FOUND_SURVIVOR_MESSAGE + " " + this.getID() + " " + World.getCurrentTimestep() + " " + surDamageAssessment + " " + foundSurvivor.getCenterX() + " " + foundSurvivor.getCenterY() + " " + World.getCurrentTimestep() + "\n");
	}

	private Message constructClaimMessage() {
		if(mySurvivor == null) {
			//can't do it - no survivor to claim
			return null;
		}
		return new Message(this,
				CLAIM_SURVIVOR_MESSAGE + " " + this.getID() + " " + World.getCurrentTimestep() + " " + mySurvivor.getCenterX() + " " + mySurvivor.getCenterY() + " " + mySurvivorClaimTime + "\n");
	}
	
	@SuppressWarnings("unchecked")
	private void broadcastMessage(Message mes) {
		//TODO try to implement direction message passing? Probably will need a message wrapper class to do this
		
		//first, get our broadcast range
		Shape broadcastRange = getBroadcastArea();

		//find any nearby bots
		List<Bot> nearbyBots = (List<Bot>) Utilities.findAreaIntersectionsInList(broadcastRange, World.allBots);

		//if I am the sender, send it out in all directions
		if(mes.getSender() == this) {
			//send out the message to all the nearby bots
			for(Bot b : nearbyBots) {
				if(b.getID() == this.getID()) {
					continue;
				}
				b.recieveMessage(mes);
			}
		} else {
			if(MESSAGE_BOT_DEBUG) {
				print("Rebroadcasting message : " + mes);
			}
			//want to send it out directionally
			//figure out what direction it came from
			Vector senderVector = new Vector(this.getCenterLocation(), mes.getSender().getCenterLocation());
			//send it in the opposite direction, i.e. if they are more than pi/2 radians away, send it to them
			Vector recieverVector;
			for(Bot b : nearbyBots) {
				if(b.getID() == mes.getSender().getID() || b.getID() == this.getID()) {
					//skip it
					continue;
				}
				
				recieverVector = new Vector(this.getCenterLocation(), b.getCenterLocation());
				
				if(MESSAGE_BOT_DEBUG) {
					print("Angle between " + mes.getSender().getID() + " and " + b.getID() + " is " + Math.toDegrees(senderVector.getAngleBetween(recieverVector)));
				}
				
				if(Math.abs(senderVector.getAngleBetween(recieverVector)) > Math.PI/2.0) {
					if(MESSAGE_BOT_DEBUG) {
						print("Rebroadcasting to " + b.getID());
					}
					//send it
					b.recieveMessage(mes);
				}
			}	
		}

		//also, send it to any BaseZones
		List<Zone> nearbyZones = (List<Zone>) Utilities.findAreaIntersectionsInList(broadcastRange, World.allZones);

		for(Zone z : nearbyZones) {
			//skip non-BaseZones
			if(! (z instanceof BaseZone)) continue;

			//send messages to those basezones
			BaseZone bz = (BaseZone) z;

			try {
				bz.recieveMessage(mes);
			} catch (InterruptedException e) {
			}

		}
	}

	private void readMessages() {
		//go through all the messages

		//make a scanner to make going through the messages a bit easier
		Scanner s;
		//go through the messages and update the stored info about the other bots
		for(Message mes : messageBuffer) {
			s = new Scanner(mes.getText());

			if(! s.hasNext()) continue;

			String messageType = s.next();

			if(messageType.equals(BOT_LOCATION_MESSAGE)) {
				int botNum = s.nextInt();

				if(botNum == botID) {
					if(MESSAGE_BOT_DEBUG)
						print("got message from myself - skip it");
					continue;
				}

				int sentTime = s.nextInt();
				//see if the messasge has timed out
				if(World.getCurrentTimestep() - sentTime > MESSAGE_TIMEOUT) {
					//ignore it
					continue;
				}

				double newX = s.nextDouble();
				double newY = s.nextDouble();

				BotInfo newBotInfo = new BotInfo(botNum, newX, newY);

				otherBotInfo.add(newBotInfo);
			} else if (messageType.equals(FOUND_SURVIVOR_MESSAGE)) {
				//get all the information off the message
				int finderID = s.nextInt();

				if(finderID == this.getID()) {
					//message from ourselves, we can safely ignore
					//TODO is this really safe to ignore?
					continue;
				}

				int sentTime = s.nextInt();
				//see if the messasge has timed out
				if(World.getCurrentTimestep() - sentTime > MESSAGE_TIMEOUT) {
					//ignore it
					continue;
				}
				
				if(MESSAGE_BOT_DEBUG) {
					print("Got a FOUND message from " + finderID);
				}

				double survivorDamage = s.nextDouble();
				double survivorX = s.nextDouble();
				double survivorY = s.nextDouble();

				//make that into a survivor entry
				Survivor foundSurvivor = new Survivor(survivorX, survivorY, survivorDamage);
				//figure out if we know about it already - update if we do, add to our records if we don't
				if(knownSurvivors.contains(foundSurvivor)) {
					//update it
					knownSurvivors.set(knownSurvivors.indexOf(foundSurvivor), foundSurvivor);
				} else {
					//add it
					knownSurvivors.add(foundSurvivor);
				}

				//rebroadcast the message
				broadcastMessage(mes);
			} else if (messageType.equals(CLAIM_SURVIVOR_MESSAGE)) {
				//remember to give up and reset mySurvivor if someone else finds them first
				//and maybe rebroadcast our claim message so that they get the idea
				//otherwise, we want to rebroadcast their claim message
				//or any found messages we get

				//get the information out of the message
				int claimerID = s.nextInt();
				//if it is us, ignore it
				if(claimerID == getID()) {
					continue;
				}

				int sentTime = s.nextInt();
				//see if the messasge has timed out
				if(World.getCurrentTimestep() - sentTime > MESSAGE_TIMEOUT) {
					//ignore it
					continue;
				}

				if(MESSAGE_BOT_DEBUG) {
					print("Got a Claim message from " +  claimerID);
				}
				
				double survivorX = s.nextDouble();
				double survivorY = s.nextDouble();
				int claimTime = s.nextInt();

				Survivor claimedSurvivor = new Survivor(survivorX, survivorY, 0);
				if(claimedSurvivor.equals(mySurvivor)) {
					//someone else is claiming my survivor
					//is this valid?
					if(claimTime < mySurvivorClaimTime) {
						//the other guy made the claim first
						//give it to them
						mySurvivor = null;
						//rebroadcast his message
						broadcastMessage(mes);
					} else if(claimTime == mySurvivorClaimTime) {
						//the guy with the lower ID gets it
						if(getID() < claimerID) {
							//I get it
							//rebroadcast my claim message
							Message myClaimMessage = constructClaimMessage();
							broadcastMessage(myClaimMessage);
						} else {
							//he gets it
							mySurvivor = null;
							//rebroadcast his message
							broadcastMessage(mes);
						}
					}
				} else {
					//store/update the survivor that they have found
					if(claimedSurvivors.contains(claimedSurvivor)) {
						claimedSurvivors.set(claimedSurvivors.indexOf(claimedSurvivor), claimedSurvivor);
					} else {
						claimedSurvivors.add(claimedSurvivor);
					}
					//rebroadcast their message
					broadcastMessage(mes);
				}
			}
			else continue; //this else matches up to figuring out what message type we have

		}

		//once we are done reading, we should clear the buffer
		messageBuffer.clear();
	}

	public void hearShout(Shout s) throws InterruptedException {
		heardShouts.add(s);
	}

	private List<Survivor> getKnowButUnclaimedSurvivors() {
		List<Survivor> results = new ArrayList<Survivor>();
		results.addAll(knownSurvivors);
		results.removeAll(claimedSurvivors);
		return results;
	}


	private void move() {
		//		//store our current state, so we can undo it if necessary.
		//		this.previousBot = (Bot) this.clone();

		/*determine what actions we want to take
		 * there are levels to what we want to do
		 * 0) If we have claimed a survivor, head towards them
		 * 1) See if we can detect a survivor, first by sight and then by sound
		 * 		head towards them if we can
		 * 2) See if we are within broadcast range of any other robots by 
		 * 		seeing what messages have come in since we last checked.
		 * 	a) If there are robots nearby, try to maximize distance from them and from the base
		 * 	b) If there are not, try to find robots by heading back towards base.
		 */			

		//TODO the haveMoved thing seems bad form to me - try to do it better
		boolean haveMoved = false; //once we have made a movement, this will be set to true

		//0) If we have claimed a survivor, move towards them
		if(!haveMoved && mySurvivor != null) {
			//make a vector towards them
			Vector surVect = new Vector(this.getCenterLocation(), mySurvivor.getCenterLocation());
			actuallyMoveAlong(surVect);
			haveMoved = true;
		}

		//1) See if we can detect a survivor, first by sight and then by sound head towards them if we can 
		if(!haveMoved) {
			List<Survivor> visibleSurs = lookForSurvivors();
			//if we find some, go towards one of them
			if(visibleSurs.size() > 0) {
				//want to go towards the nearest survivor
				Vector nearestVicVect = null;
				double nearestDistSquare = java.lang.Double.MAX_VALUE;


				//so, we need to figure out which one is the nearest one
				for(Survivor s : visibleSurs) {
					Vector surVect = new Vector(this.getCenterLocation(), s.getCenterLocation());
					if(surVect.getMagSquare() < nearestDistSquare) {
						nearestVicVect = surVect;
						nearestDistSquare = surVect.getMagSquare();
					}
				}

				//make a bee-line for that survivor!
				actuallyMoveAlong(nearestVicVect);

				haveMoved = true;
			}
		}

		if(!haveMoved) {
			List<Shout> audibleShouts = listenForSurvivors();
			//if we can hear anything, go towards one of them
			if(audibleShouts.size() > 0) {
				//want to go towards the nearest shout
				Vector nearestShoutVect = null;
				double nearestDistSquare = java.lang.Double.MAX_VALUE;


				//so, we need to figure out which one is the nearest one
				for(Shout s : audibleShouts) {
					Vector shoutVect = new Vector(this.getCenterLocation(), s.getCenterLocation());
					if(shoutVect.getMagSquare() < nearestDistSquare) {
						nearestShoutVect = shoutVect;
						nearestDistSquare = s.getCenterLocation().distanceSq(this.getCenterLocation());
					}
				}

				//make a bee-line for that survivor!
				actuallyMoveAlong(nearestShoutVect);

				haveMoved = true;
			}
		}

		/* 2) See if we are within broadcast range of any other robots by 
		 * 		seeing what messages have come in since we last checked.
		 * 	a) If there are robots nearby, try to maximize distance from them and from base zones
		 */

		if(MOVE_BOT_DEBUG) {
			print("I know about " + otherBotInfo.size() + " other bots");
		}

		if( (!haveMoved) && (otherBotInfo.size() > 0)) {

			Vector movementVector = new Vector(this.getCenterLocation(), this.getCenterLocation());

			for(int i = 0; i < otherBotInfo.size(); i++) {
				BotInfo bi = otherBotInfo.get(i);

				//don't consider ourselves
				if(bi.getBotID() == botID) continue;

				//make a "force" vector from the other bot
				Vector curBotVect = new Vector(this.getCenterLocation(), bi.getCenterLocation());
				//scale it based on how far away we are from the bot and the repulsion factor
				//also, multiply by -1 so the vector points away from the thing we want to get away from
				curBotVect = curBotVect.rescaleRatio(-1.0 * REPULSION_FACTOR_FROM_OTHER_BOTS / curBotVect.getMagSquare());

				//now add it to our movement vector
				movementVector = movementVector.add(curBotVect);
			}

			//now, also try to maximize distance from base zones
			for(Zone z : World.allZones) {
				if(z instanceof BaseZone) {
					//find the point on the edge of the zone that is closest to us
					Point2D nearestBasePoint = Utilities.getNearestPoint(z, getCenterLocation());

					//try to move away from that point
					Vector curZoneVect = new Vector(this.getCenterLocation(), nearestBasePoint);
					//scale it based on how far away we are from the base, and the repulsion factor 
					//also, multiply by -1 so the vector points away from the thing we want to get away from
					curZoneVect = curZoneVect.rescale(-1.0 * REPULSION_FACTOR_FROM_HOME_BASES / curZoneVect.getMagSquare());

					//add it to our movement vector
					movementVector = movementVector.add(curZoneVect);
				}
			}

			//move along the vector we made
			actuallyMoveAlong(movementVector);

			haveMoved = true;
			//			}
		}

		if(!haveMoved) {
			//move toward the base, hopefully finding other robots and/or getting messages about paths to follow
			if(MOVE_BOT_DEBUG) {
				print("No bots within broadcast distance - move back towards base\nKnow location of " + otherBotInfo.size() + " other bots");
			}

			Vector baseZoneVect = new Vector(this.getCenterLocation(), baseZone.getCenterLocation());

			actuallyMoveAlong(baseZoneVect);

			haveMoved = true;
		}

		//we've now moved - broadcast location to nearby bots
		//construct the message we want to send them.
		Message outgoingMessage = constructLocationMessage(); 

		//broadcast it
		broadcastMessage(outgoingMessage);

	}

	protected void moveRandomly() {
		int xChange, yChange;
		//50-50 chance to go left or right
		if(numGen.nextDouble() < .5) {
			xChange = DIMENSION;
		} else {
			xChange = -1 * DIMENSION;
		}

		//50-50 chance to go up or down
		if(numGen.nextDouble() < .5) {
			yChange = DIMENSION;
		} else {
			yChange = -1 * DIMENSION;
		}

		this.translate(xChange, yChange);
	}


	private void actuallyMoveAlong(Vector v) {
		if(MOVE_BOT_DEBUG)
			print("Current location : " + this.getCenterLocation());

		//make sure the vector starts in the right place
		if(! v.getP1().equals(this.getCenterLocation())) {
			//move the vector to fix this
			print("HAD TO ADJUST MOVE VECTOR");
			v = v.moveTo(this.getCenterLocation());
		}

		//make sure the vector isn't too long i.e. assert our max velocity
		//this basically allows us to move to the end of the vector as 1 step
		v = verifyMovementVector(v);

		if(MOVE_BOT_DEBUG) 
			print("Moving along vector '" + v + "'");

		//don't hit the walls of the bounding box 
		if(Utilities.edgeIntersects(this.boundingBox, currentZone.getVisibilityArea(getCenterLocation()))) {
			//this means we can "see" the edge of the bounding box
			//try to move such that we don't hit it
			v = boundingBox.getPathThatStaysInside(v);
		}

		//again, make sure our movement vector is legal
		v = verifyMovementVector(v);

		if(MOVE_BOT_DEBUG)
			print("rescaled vector is " + v);

		//now that everything is all set with the vector, we can move to the other end of it
		this.setCenterLocation(v.getP2());

		movementVector = v;

	}

	//makes sure that the passed movement vector is OK i.e. it isn't too long
	private Vector verifyMovementVector(Vector v) {
		if(v.getMagSquare() > getMaxVelocity()) {
			v = v.rescale(getMaxVelocity());
		}
		return v;
	}

	private void updateAveragePreviousLocation() {
		//basically, we're going to take simple moving average of the previous moves
		
	}

	@SuppressWarnings("unchecked")
	private List<Survivor> lookForSurvivors() {
		//first, get our visibility radius
		Shape visibilityRange = getVisibleArea();

		//see if the location of any of our survivors intersects this range
		List<Survivor> visiblesurvivors = (List<Survivor>) Utilities.findAreaIntersectionsInList((Shape)visibilityRange, World.allSurvivors);

		if(LOOK_BOT_DEBUG)
			print("In perfect world, would have just seen " + visiblesurvivors.size() + " survivors");

		//ignore any vicitms we know have been claimed
		//use a list iterator for the reasons described below
		ListIterator<Survivor> vicIteratior = visiblesurvivors.listIterator();
		while(vicIteratior.hasNext()) {
			Survivor curSur = vicIteratior.next();
			if(claimedSurvivors.contains(curSur)) {
				vicIteratior.remove();
			}
		}

		//visibilesurvivors is now a list of all the survivors the robot could see and dosen't already know about 
		//if it was perfect but it's not, and there is some probability that it will miss some of the survivors
		//so, go through the list and remove some or all of the survivors with that probability.
		//need to use an iterator, because it won't let us remove as we go otherwise
		vicIteratior = visiblesurvivors.listIterator();
		while(vicIteratior.hasNext()) {
			vicIteratior.next();
			if(! (numGen.nextDouble() <= VISUAL_ID_SURVIVOR_PROB)) {
				vicIteratior.remove(); //removes from the iterator AND the list
			}
		}

		if(LOOK_BOT_DEBUG)
			print("Actually able to see " + visiblesurvivors.size() + " survivors");

		//we have our list survivors that the Bot saw - return it
		return visiblesurvivors;
	}

	@SuppressWarnings("unchecked")
	private List<Shout> listenForSurvivors() {
		//first, get our auditory radius
		Shape auditoryRange = getAuditbleArea();

		//see if any of the shouts we know about intersect this range
		List<Shout> audibleShouts = (List<Shout>) Utilities.findAreaIntersectionsInList((Shape) auditoryRange, heardShouts);

		if(LISTEN_BOT_DEBUG)
			print("In perfect world, would have just heard " + audibleShouts.size() + " vicitms");

		//ignore any shouts that sound like they're coming from vicitims have already been claimed
		//use a list iterator for the reasons described below
		ListIterator<Shout> shoutIterator = audibleShouts.listIterator();
		while(shoutIterator.hasNext()) {
			Shout curShout = shoutIterator.next();
			if(claimedSurvivors.contains(curShout.getShouter())) {
				shoutIterator.remove();
			}
		}

		//audible shouts is now all the shouts we could hear if the robot could hear perfectly
		//but it can't - we're using a probability to model this fact
		//so, go through the list and remove the ones that probability says we can't identify
		shoutIterator = audibleShouts.listIterator();
		while(shoutIterator.hasNext()) {
			shoutIterator.next();
			if(! (numGen.nextDouble() <= HEAR_SURVIVOR_PROB)) {
				shoutIterator.remove();  //remove from iterator AND list
			}
		}

		if(LOOK_BOT_DEBUG)
			print("Actually just heard " + audibleShouts.size() + " survivors");

		//we have our list of shouts - return it
		return audibleShouts;	
	}

	private void updateZoneInfo() {
		currentZone = World.findZone(getCenterLocation());

		if(currentZone == null) {
			print("AHH! WE DON'T KNOW WHAT ZONE WE'RE IN!! - " + this.getCenterX() + ", " + getCenterY());
			print("Just moved: " + movementVector);
		}

		if(currentZone.isObstacle()) {
			print("AHHHH!!!! I'M MELTING!!!!");
			world.stopSimulation();
		}

		//reasses the zones's status if we move to a new zones
		assessZone();
	}


	private void assessZone() {
		//with some probability, the bot will asses the zones correctly
		if(numGen.nextDouble() < CORRECT_ZONE_ASSESMENT_PROB) {
			if(currentZone instanceof SafeZone) {
				zoneAssesment = ZONE_SAFE;
			} else if(currentZone instanceof BaseZone) {
				zoneAssesment = ZONE_BASE;
			} else {
				zoneAssesment = ZONE_DANGEROUS;
			}
		} else {
			//if we don't get it right, assign the incorrect value
			if(currentZone instanceof SafeZone) {
				zoneAssesment = ZONE_DANGEROUS;
			} else {
				zoneAssesment = ZONE_SAFE;
			}
		}
	}

	private double assesSurvivor(Survivor s) {
		//with some probability we'll get it wrong
		if(numGen.nextDouble() < ASSES_SURVIVOR_CORRECTLY_PROB) {
			return s.getDamage();
		} else {
			return numGen.nextInt(101)/100.0;
		}
	}


	private void findAndAssesSurvivor() {
		//first, see if there are any survivors that we can see
		List<Survivor> visibleSurvivors = lookForSurvivors();

		//see if any of them are within the FOUND_RANGE
		List<Survivor> foundSurvivors = new ArrayList<Survivor>();

		for(Survivor v : visibleSurvivors) {
			if(v.getCenterLocation().distance(this.getCenterLocation()) < DEFAULT_FOUND_RANGE) {
				foundSurvivors.add(v);
			}
		}

		//we now know what survivors we have found
		//evaluate each of them in turn
		for(Survivor s : foundSurvivors) {
			double surDamage = assesSurvivor(s);

			//if someone has already claimed this survivor, don't bother letting everyone know about them
			if(claimedSurvivors.contains(s)) {
				continue;
			}
			
			//send out a message letting everyone know where the survivor is, what condition they are in, and how safe the zones is
			Message message = constructFoundMessage(s, surDamage);

			broadcastMessage(message);

			knownSurvivors.add(s);
		}

		//claim the one that is closest and that has not yet been claimed
		List<Survivor> claimableSurvivors = new ArrayList<Survivor>(foundSurvivors);
		claimableSurvivors.removeAll(claimedSurvivors);

		if(claimableSurvivors.size() > 0) {
			//figure out which one is closets
			Survivor closestSurvivor = claimableSurvivors.get(0);
			double closestSurvivorDist = getCenterLocation().distance(closestSurvivor.getCenterLocation());
			for(Survivor curSur : claimableSurvivors) {
				double curDist = getCenterLocation().distance(curSur.getCenterLocation());
				if(curDist < closestSurvivorDist) {
					closestSurvivor = curSur;
					closestSurvivorDist = curDist;
				}
			}

			//claim the closest one
			mySurvivor = closestSurvivor;
			Message message = constructClaimMessage();
			mySurvivorClaimTime = World.getCurrentTimestep();
			broadcastMessage(message);
		}
	}
	
	public void doOneTimestep() {
		//first, read any messages that have come in, and take care of them
		readMessages();

		switch (algorithmPhase) {
		case(SPREAD_OUT_PHASE):
			if(! settledOnLocation) {
				//now try to move, based on the move rules.
				move();

				//if we have not already claimed a survivor, find out if we can see any survivors
				//TODO make this better - if they have heard a survivor, check it out for a few steps
				if(mySurvivor == null) {
					findAndAssesSurvivor();
				}

				//TODO decide if we are settled on a location yet
				

			}
		break;
		case(CREATE_PATHS_PHASE):

			break;
		case(AGGRIGATE_PHASE):

			break;
		default:
			/*TODO fix this so that it asks neighbor what phase it is
			 * shouldn't happen regardless, so this doesn't really matter
			 */
			algorithmPhase = SPREAD_OUT_PHASE;
			break;
		}

		//now, just some housekeeping
		//we shouldn't hang onto shouts for too long
		heardShouts.clear();
		//also don't want to hang on to bot info for too long
		otherBotInfo.clear();


		//make sure we are still in the zones we think we are in
		if(currentZone == null || (! currentZone.contains(getCenterLocation()))) {
			updateZoneInfo();
		}
		if(MOVE_BOT_DEBUG) {
			print("");
		}
	}

	private void print(String message) {
		if(OVERALL_BOT_DEBUG) {
			System.out.println(botID + ":\t" + message);
			System.out.flush();
		}
	}
}