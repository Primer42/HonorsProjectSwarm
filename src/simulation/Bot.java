package simulation;

import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;

import util.Utilities;
import util.Vector;
import util.shapes.Circle2D;
import util.shapes.LineSegment;
import zones.BaseZone;
import zones.BoundingBox;
import zones.SafeZone;
import zones.Zone;

public class Bot extends Rectangle2D.Double {

	private static final long serialVersionUID = -3272426964314356266L;

	/***************************************************************************
	 * CONSTANTS
	 **************************************************************************/
	private final int DIMENSION = 2;
	private final double VISUAL_ID_SURVIVOR_PROB = .70;
	private final double HEAR_SURVIVOR_PROB = .75;
	private final double ASSES_SURVIVOR_CORRECTLY_PROB = .9;
	private final double CORRECT_ZONE_ASSESMENT_PROB = .8; // the probability that the bot will asses the zones correctly

	//1 px = 2 m
	public static final double DEFAULT_BROADCAST_RADIUS = 40; //40 px = 80 m
	public static final double DEFAULT_VISIBILITY_RADIUS = 12; //12 px = 24 m
	public static final double DEFAULT_AUDITORY_RADIUS = 24; //24 px = 48 m
	public static final double DEFAULT_FOUND_RANGE = DEFAULT_VISIBILITY_RADIUS;
	public static final double DEFAULT_MAX_VELOCITY = 4; //4 px = 8 m/s

	private static Random NUM_GEN = new Random();

	private final int ZONE_SAFE = 1;
	private final int ZONE_DANGEROUS = 2;
	private final int ZONE_BASE = 3;

	//TODO Scale force exerted on other bots based on how many neighobors each bot has - so bots with more neighbors will push more?
	private final double SEPERATION_FACTOR = 									50;
	private final double COHESION_FACTOR = 										.5; //cohesion factor should never me more than 1

	private final double SEPERATION_MIN_DIST = 0.1;
	private final double SEPERATION_MAX_DIST = DEFAULT_BROADCAST_RADIUS*2;
	private final double SEPERATION_CURVE_SHAPE = 2.5;

	private final int FACTOR_ADJUSTMENT_BOT_NUMBER = 5;
	private final double FACTOR_ADJUSTMENT_SEPERATION_VALUE = SEPERATION_FACTOR * 2;

	public static double timestepSeperationMagnitudeTotal;
	public static double timestepCohesionMagnitudeTotal;
	public static int timestepCountOfBotsAffectedBySepOrCohesion;

	public static double timestepZoneRepulsionMagnitudeTotal;
	public static double timestepBotsRepelledByZones;
	public static double timestepVisibleZoneSideTotal;
	public static int timestepNumVisibleZoneSides;

	private final static int SPREAD_OUT_PHASE = 				0;
	private final static int CREATE_PATHS_PHASE = 				1;
	private final static int AGGRIGATE_PHASE =					2;
	private final static int WAITING_TO_BE_TURNED_ON_PHASE = 	3;

	private final static double TURNED_ON_THIS_TIMESTEP_PROB = .02;


	public final static String BOT_LOCATION_MESSAGE = 						"bloc";
	public final static String CLAIM_SURVIVOR_MESSAGE = 					"cs";
	public final static String FOUND_SURVIVOR_MESSAGE = 					"fs";
	public final static String ADVANCE_TO_NEXT_PHASE_ELECTION_MESSAGE = 	"atnpe";
	public final static String NOT_READY_TO_ADVANCE_MESSAGE = 				"nrta";
	public final static String ADVANCE_TO_NEXT_PHASE_MESSAGE =				"atnp";
	public final static String CREATE_PATH_MESSAGE = 						"cp";

	private final int NUM_PREV_LOCATIONS_TO_CONSIDER;
	private final static double STOP_SPREADING_THRESHOLD = DEFAULT_MAX_VELOCITY / 2.0;
	private final static double DEFAULT_AVERAGE_LOCATION_VALUE = -1 * java.lang.Double.MAX_VALUE;

	private final int MOVE_TO_NEXT_PHASE_TIME_THRESHOLD;

	private boolean OVERALL_BOT_DEBUG = true;
	private boolean LISTEN_BOT_DEBUG = false;
	private boolean LOOK_BOT_DEBUG = false;
	private boolean MESSAGE_BOT_DEBUG = false;
	private boolean MOVE_BOT_DEBUG = false;
	private boolean SETTLE_DEBUG = false;

	/***************************************************************************
	 * VARIABLES
	 **************************************************************************/

	/**
	 * These variables the bot does not know about - we store them for our
	 * convience.
	 */
	private Zone currentZone; // what zones we actually are in can change some behavior
	private List<Shout> heardShouts; // the shouts that have been heard recently
	private Vector movementVector;
	private BoundingBox boundingBox;

	// private Bot previousBot;
	private List<BotInfo> otherBotInfo; // storage of what information we know
	// about all of the other Bots
	private List<Message> messageBuffer; // keep a buffer of messages from other robots we have recieved in the last timestep
	private List<Message> alreadyBroadcastedMessages;
	private int botID;
	private int zoneAssesment; // stores the bot's assessment of what sort of
	// zones it is in
	private Zone baseZone; // the home base zones.
	private List<Survivor> knownSurvivors; // keep a list of survivors that have
	// already been found, so can go
	// claim them
	// TODO handle reclaiming survivors in the case of robot death
	private List<Survivor> claimedSurvivors; // keep a list of survivors that
	// have been claimed, so that we
	// don't double up on one survivor
	private Survivor mySurvivor; // the survivor I have claimed - don't know if
	// this is useful, but it might be
	private int mySurvivorClaimTime;
	private World world;
	private int algorithmPhase;

	private int lastMoveToNextPhaseMessageRecievedTime = -1;
	private int numberOfElectionsStarted = 0;
	private boolean electionLooksSuccessful = true;
	private int myElectionStartTime = -1;
	private HashMap<Integer, Integer> electionLeaderRecord;

	private List<Point2D> previousLocations;
	private Point2D averagePreviousLocation;
	private boolean settledOnLocation;

	private boolean startedCreatingMyPath = false;
	private List<SurvivorPath> knownPaths;

	/***************************************************************************
	 * CONSTRUCTORS
	 **************************************************************************/
	public Bot(World _world, double centerX, double centerY, int _numBots, int _botID, Zone homeBase, BoundingBox _bounds) {
		super();

		world = _world;

		// first, in order to store our location, we need to find our top left
		// corner
		double cornerX = centerX - DIMENSION / 2;
		double cornerY = centerY - DIMENSION / 2;

		// set our location and size
		setFrame(cornerX, cornerY, DIMENSION, DIMENSION);

		// now, set up the list of other bot information
		otherBotInfo = new ArrayList<BotInfo>();

		// set up other variables with default values
		messageBuffer = new ArrayList<Message>();
		alreadyBroadcastedMessages = new ArrayList<Message>();

		heardShouts = new CopyOnWriteArrayList<Shout>();

		botID = _botID;

		baseZone = homeBase;

		knownSurvivors = new ArrayList<Survivor>();
		claimedSurvivors = new ArrayList<Survivor>();

		previousLocations = new ArrayList<Point2D>();
		NUM_PREV_LOCATIONS_TO_CONSIDER = 100;

		boundingBox = _bounds;

		movementVector = new Vector(this.getCenterLocation(), this
				.getCenterLocation());

		// start in the spread out phase
		algorithmPhase = WAITING_TO_BE_TURNED_ON_PHASE;

		settledOnLocation = false;

		mySurvivor = null;

		MOVE_TO_NEXT_PHASE_TIME_THRESHOLD = _numBots * 2;

		electionLeaderRecord = new HashMap<Integer, Integer>();

		knownPaths = new ArrayList<SurvivorPath>();

		// for now, assume we're starting in a base zone
		zoneAssesment = ZONE_BASE;

		// find out what zones we start in, and try to determine how safe it is
		updateZoneInfo();		
	}

	/***************************************************************************
	 * GETTERS
	 **************************************************************************/
	public Point2D getCenterLocation() {
		return new Point2D.Double(getCenterX(), getCenterY());
	}

	public Circle2D getBroadcastArea() {
		// see how far the current zones thinks we can broadcast
		return currentZone.getBroadcastArea(getCenterLocation());
	}

	public Circle2D getVisibleArea() {
		// see how far the current zones thinks we can see
		return currentZone.getVisibilityArea(getCenterLocation());
	}

	public double getVisibityRange() {
		return currentZone.getVisiblityRange();
	}

	public Circle2D getAuditbleArea() {
		// see how far the current zones thinks we can hear
		return currentZone.getAudibleArea(getCenterLocation());
	}

	public ListIterator<Shout> getShoutIterator() {
		return heardShouts.listIterator();
	}

	public double getMaxVelocity() {
		return DEFAULT_MAX_VELOCITY;
	}

	public double getObstacleBufferRange() {
		return DEFAULT_VISIBILITY_RADIUS / 4.0;
	}

	public int getID() {
		return botID;
	}

	public BotInfo getThisBotInfo() {
		return new BotInfo(this.getID(), this.getCenterX(), this.getCenterY(), this.zoneAssesment);
	}


	public Vector getMovementVector() {
		return movementVector;
	}

	public void setCenterLocation(Point2D newCenterLoc) {
		// need find the new upper-left corner location
		double cornerX = newCenterLoc.getX() - (DIMENSION / 2.0);
		double cornerY = newCenterLoc.getY() - (DIMENSION / 2.0);
		this.setRect(cornerX, cornerY, DIMENSION, DIMENSION);
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
		//if we aren't on, we can't recieve
		//		if(algorithmPhase == WAITING_TO_BE_TURNED_ON_PHASE) {
		//			return;
		//		}

		messageBuffer.add(message);
	}

	private Message constructLocationMessage() {
		return new Message(this.getThisBotInfo(), 
				BOT_LOCATION_MESSAGE, this.getID() + " " + World.getCurrentTimestep() + " " + this.getCenterX() + " " + this.getCenterY() + "\n");
	}

	private Message constructFoundMessage(Survivor foundSurvivor,
			double surDamageAssessment) {
		return new Message(this.getThisBotInfo(), 
				FOUND_SURVIVOR_MESSAGE, this.getID() + " " + World.getCurrentTimestep() + " " + surDamageAssessment + " " + foundSurvivor.getCenterX() + " " + foundSurvivor.getCenterY() + "\n");
	}

	private Message constructClaimMessage() {
		if (mySurvivor == null) {
			// can't do it - no survivor to claim
			return null;
		}
		return new Message(this.getThisBotInfo(), 
				CLAIM_SURVIVOR_MESSAGE, this.getID() + " " + World.getCurrentTimestep() + " " + mySurvivor.getCenterX() + " " + mySurvivor.getCenterY() + " " + mySurvivorClaimTime + "\n");
	}

	private Message constructPhaseAdvancementElectionMessage() {
		numberOfElectionsStarted++;
		return new Message(this.getThisBotInfo(),
				ADVANCE_TO_NEXT_PHASE_ELECTION_MESSAGE, this.getID() + " " + World.getCurrentTimestep() + " " + numberOfElectionsStarted + "\n");
	}

	private Message constructNotReadyForPhaseAdvancementMessage(Message electionMessage) {
		Scanner messageScanner = new Scanner(electionMessage.getText());

		String messageType = electionMessage.getType();
		if(! messageType.equals(ADVANCE_TO_NEXT_PHASE_ELECTION_MESSAGE)) {
			throw new IllegalArgumentException("Did not pass an election message to counter");
		}
		int starterID = messageScanner.nextInt();
		int electionNum = messageScanner.nextInt();

		return new Message(this.getThisBotInfo(),
				NOT_READY_TO_ADVANCE_MESSAGE, starterID + " " + World.getCurrentTimestep() + " " + electionNum + "\n");		

	}

	private Message constructPhaseAdvancementMessage() {
		return new Message(this.getThisBotInfo(),
				ADVANCE_TO_NEXT_PHASE_MESSAGE, this.getID() + " " + World.getCurrentTimestep() + "\n");
	}

	private Message constructCreatePathsMessage(SurvivorPath pathToUse) {
		//make a string representing the path
		//include the damage of the survivor, and the points in the path
		//start with the survivor
		String messageBody = "";

		Survivor pathSurvivor = pathToUse.getSur();

		messageBody += pathSurvivor.getCenterX() + " " + pathSurvivor.getCenterY() + " " + pathSurvivor.getDamage() + "\t";

		//now add the points in the path
		PathIterator pathit = pathToUse.getPathIterator(null);

		double[] curCoord = new double[6];
		while(! pathit.isDone()){
			//get the coordinates of the current point
			int segType = pathit.currentSegment(curCoord);
			if(segType == PathIterator.SEG_CLOSE || segType == PathIterator.SEG_CUBICTO || segType == PathIterator.SEG_QUADTO) {
				throw new IllegalArgumentException("Got a path that has incorrect form");
			}
			//add the current point to the string
			messageBody += " " + curCoord[0] + " " + curCoord[1] + " ";
			pathit.next();
		}

		return new Message(this.getThisBotInfo(), CREATE_PATH_MESSAGE, messageBody);
	}

	@SuppressWarnings("unchecked")
	private void broadcastMessage(Message mes) {

		//really firstly, make sure we haven't broadcasted this message before
		//if we have broadcastetd it before, don't do it again
		if(alreadyBroadcastedMessages.contains(mes)) {
			return;
		}

		//make sure we record that we are broadcasting this message
		alreadyBroadcastedMessages.add(mes);

		// first, get our broadcast range
		Shape broadcastRange = getBroadcastArea();

		// find any nearby bots
		List<Bot> nearbyBots = (List<Bot>) Utilities.findAreaIntersectionsInList(broadcastRange, World.allBots);

		//		// if I am the sender, send it out in all directions
		//		if (mes.getSender() == this) {
		// send out the message to all the nearby bots
		for (Bot b : nearbyBots) {
			if (b.getID() == this.getID()) {
				continue;
			}
			b.recieveMessage(mes);
		}

		// also, send it to any BaseZones
		List<Zone> nearbyZones = (List<Zone>) Utilities
		.findAreaIntersectionsInList(broadcastRange, World.allZones);

		for (Zone z : nearbyZones) {
			// skip non-BaseZones
			if (!(z instanceof BaseZone))
				continue;

			// send messages to those basezones
			BaseZone bz = (BaseZone) z;

			try {
				bz.recieveMessage(mes);
			} catch (InterruptedException e) {
			}

		}
	}

	private void readMessages() {
		// go through all the messages

		// make a scanner to make going through the messages a bit easier
		Scanner s;
		// go through the messages and update the stored info about the other
		// bots

		for (Message mes : messageBuffer) {
			s = new Scanner(mes.getText());

			if (!s.hasNext())
				continue;

			String messageType = mes.getType();

			//TODO change this to a switch statement
			if (messageType.equals(BOT_LOCATION_MESSAGE)) {
				int botNum = s.nextInt();

				if (botNum == botID) {
					continue;
				}

				int sentTime = s.nextInt();

				double newX = s.nextDouble();
				double newY = s.nextDouble();

				BotInfo newBotInfo = new BotInfo(botNum, newX, newY);

				otherBotInfo.add(newBotInfo);
			} else if (messageType.equals(FOUND_SURVIVOR_MESSAGE)) {
				// get all the information off the message
				int finderID = s.nextInt();

				if (finderID == this.getID()) {
					// message from ourselves, we can safely ignore
					// TODO is this really safe to ignore?
					continue;
				}

				int sentTime = s.nextInt();

				if (MESSAGE_BOT_DEBUG) {
					print("Got a FOUND message from " + finderID);
				}

				double survivorDamage = s.nextDouble();
				double survivorX = s.nextDouble();
				double survivorY = s.nextDouble();

				// make that into a survivor entry
				Survivor foundSurvivor = new Survivor(survivorX, survivorY, survivorDamage);
				// figure out if we know about it already - update if we do, add
				// to our records if we don't
				if (knownSurvivors.contains(foundSurvivor)) {
					// update it
					knownSurvivors.set(knownSurvivors.indexOf(foundSurvivor),
							foundSurvivor);
				} else {
					// add it
					knownSurvivors.add(foundSurvivor);
				}

				// rebroadcast the message if we haven't already
				broadcastMessage(mes);
			} else if (messageType.equals(CLAIM_SURVIVOR_MESSAGE)) {
				// remember to give up and reset mySurvivor if someone else
				// finds them first
				// and maybe rebroadcast our claim message so that they get the
				// idea
				// otherwise, we want to rebroadcast their claim message
				// or any found messages we get

				// get the information out of the message
				int claimerID = s.nextInt();
				// if it is us, ignore it
				if (claimerID == getID()) {
					continue;
				}

				int sentTime = s.nextInt();

				if (MESSAGE_BOT_DEBUG) {
					print("Got a Claim message from " + claimerID);
				}

				double survivorX = s.nextDouble();
				double survivorY = s.nextDouble();
				int claimTime = s.nextInt();

				Survivor claimedSurvivor = new Survivor(survivorX, survivorY, 0);
				if (claimedSurvivor.equals(mySurvivor)) {
					// someone else is claiming my survivor
					// is this valid?
					if (claimTime < mySurvivorClaimTime) {
						// the other guy made the claim first
						// give it to them
						mySurvivor = null;
						// rebroadcast his message
						broadcastMessage(mes);
					} else if (claimTime == mySurvivorClaimTime) {
						// the guy with the lower ID gets it
						if (getID() < claimerID) {
							// I get it
							// rebroadcast my claim message
							Message myClaimMessage = constructClaimMessage();
							if(myClaimMessage != null) {
								broadcastMessage(myClaimMessage);
							}
						} else {
							// he gets it
							mySurvivor = null;
							// rebroadcast his message
							broadcastMessage(mes);
						}
					}
				} else {
					// store/update the survivor that they have found
					if (claimedSurvivors.contains(claimedSurvivor)) {
						claimedSurvivors.set(claimedSurvivors
								.indexOf(claimedSurvivor), claimedSurvivor);
					} else {
						claimedSurvivors.add(claimedSurvivor);
					}
					// rebroadcast their message
					broadcastMessage(mes);
				}
			} else if(messageType.equals(ADVANCE_TO_NEXT_PHASE_ELECTION_MESSAGE) 
					|| messageType.equals(NOT_READY_TO_ADVANCE_MESSAGE)) {

				//first, update or ignore messages based on our records of who has started what election
				Integer starterID = new Integer(s.nextInt());

				int sentTime = s.nextInt();

				Integer electionNumber = new Integer(s.nextInt());
				if(electionLeaderRecord.containsKey(starterID)) {
					//this sender has started an election before
					//see what election we last saw them start
					Integer lastKnownElectionStartedByThisSender = electionLeaderRecord.get(starterID);
					if(electionNumber < lastKnownElectionStartedByThisSender) {
						//this message has to do with an election that is already over
						//ignore it
						continue;
					} else if(electionNumber > lastKnownElectionStartedByThisSender){ 
						//we need to update our records - they have started a new election
						electionLeaderRecord.put(starterID, electionNumber);
					}
				} else {
					//this is the first election we have seen from this sender
					//record it and process the message
					electionLeaderRecord.put(starterID, electionNumber);
				}

				//now, process the message
				if (messageType.equals(ADVANCE_TO_NEXT_PHASE_ELECTION_MESSAGE)) { 
					//first, record that someone is trying to start an election
					lastMoveToNextPhaseMessageRecievedTime = World.getCurrentTimestep();

					//see if we are ready
					if(settledOnLocation) {
						//we're ready
						//rebroadcast the message
						broadcastMessage(mes);
					} else {
						//we're not ready
						//respond with a not-ready message
						Message notReady = constructNotReadyForPhaseAdvancementMessage(mes);
						broadcastMessage(notReady);
					}
				} else if(messageType.equals(NOT_READY_TO_ADVANCE_MESSAGE)) {
					//see if we started the elecetion

					if(starterID == this.getID()) {
						//we started the election
						//see if we are still waiting
						if(electionNumber == numberOfElectionsStarted) {
							//we are still waiting for the results
							//this means the election failed
							electionLooksSuccessful = false;
						} else {
							//we are not still waiting - ignore it
						}
					} else {
						//we did not start this election
						//pass on the message so the starter eventually gets it
						broadcastMessage(mes);
					}
				}

			} else if(messageType.equals(ADVANCE_TO_NEXT_PHASE_MESSAGE)) {
				//Someone has decided it is time to move onto the next phase
				//do so
				print("Moving onto next phase");
				algorithmPhase = CREATE_PATHS_PHASE;
				//tell everyone else to move on too
				broadcastMessage(mes);

				//also, if we have a claimed survivor, start making a path to them
				if(mySurvivor != null && !startedCreatingMyPath) {
					List<Point2D> pointsList = new ArrayList<Point2D>();
					pointsList.add(this.getCenterLocation());
					SurvivorPath pathStart = new SurvivorPath(mySurvivor, pointsList, baseZone.getCenterLocation());
					Message makeAPathMessage = constructCreatePathsMessage(pathStart);
					broadcastMessage(makeAPathMessage);
					print("Since we're starting create paths phase, broadcasting create paths message");
					print("Message reads: " + makeAPathMessage);
					startedCreatingMyPath = true;
				}
			} else if(messageType.equals(CREATE_PATH_MESSAGE)) {
				//read out the survivor
				Survivor pathSur = new Survivor(s.nextDouble(), s.nextDouble(), s.nextDouble());
				//read out each of the points
				List<Point2D> pathPoints = new ArrayList<Point2D>();
				while(s.hasNextDouble()) {
					Point2D nextPathPoint = new Point2D.Double(s.nextDouble(), s.nextDouble());
					pathPoints.add(nextPathPoint);
				}

				//add our point to the path if it isn't already there
				//not at the end, but one before the end
				if(! pathPoints.contains(this.getCenterLocation())) {
					pathPoints.add(pathPoints.size() - 2, this.getCenterLocation());
				}


				//make a path out of it
				SurvivorPath sp = new SurvivorPath(pathSur, pathPoints);

				//see how it compares to what path we know of that is best for this survivor
				Message passOnMessage;
				if(knownPaths.contains(sp)) {
					SurvivorPath pathWeKnow = knownPaths.get(knownPaths.indexOf(sp));
					//compare lengths
					if(pathWeKnow.getPathLength() < sp.getPathLength()) {
						//pass on the path we know
						passOnMessage = constructCreatePathsMessage(pathWeKnow);
					} else {
						//we want to pass on the new path
						//it is better
						knownPaths.remove(pathWeKnow);
						knownPaths.add(sp);
						passOnMessage = constructCreatePathsMessage(sp);
					}
				} else {
					//we haven't seen a path to this survivor before
					//store that we have seen it, and pass it on
					knownPaths.add(sp);
					passOnMessage = constructCreatePathsMessage(sp);
				}
				if(MESSAGE_BOT_DEBUG) {
					print("Passing on a path : " + passOnMessage);
				}
				broadcastMessage(passOnMessage);
			} else {
				continue; // this else matches up to figuring out what message type we have
			}

		}

		// once we are done reading, we should clear the buffer
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
		// //store our current state, so we can undo it if necessary.
		// this.previousBot = (Bot) this.clone();

		/*
		 * determine what actions we want to take there are levels to what we
		 * want to do 0) If we have claimed a survivor, head towards them 1) See
		 * if we can detect a survivor, first by sight and then by sound head
		 * towards them if we can 2) See if we are within broadcast range of any
		 * other robots by seeing what messages have come in since we last
		 * checked. a) If there are robots nearby, try to maximize distance from
		 * them and from the base b) If there are not, try to find robots by
		 * heading back towards base.
		 */

		// TODO the haveMoved thing seems bad form to me - try to do it better
		boolean haveMoved = false; // once we have made a movement, this will be
		// set to true

		// 0) If we have claimed a survivor, move towards them
		if (!haveMoved && mySurvivor != null) {
			// make a vector towards them
			Vector surVect = new Vector(this.getCenterLocation(), mySurvivor
					.getCenterLocation());
			actuallyMoveAlong(surVect);
			haveMoved = true;
		}

		if(settledOnLocation || algorithmPhase == CREATE_PATHS_PHASE) { 
			//don't move unless we aren't in communication range with anyone else
			if(otherBotInfo.size() <= 0) {
				//need to move back towards home base to get into communication range of more other bots
				Vector baseVect = new Vector(this.getCenterLocation(), baseZone.getCenterLocation());
				actuallyMoveAlong(baseVect);
			} else {
				//don't move
				actuallyMoveAlong(new Vector(this.getCenterLocation(), this.getCenterLocation()));
			}
			haveMoved = true;
		}

		// 1) See if we can detect a survivor, first by sight and then by sound
		// head towards them if we can
		if (!haveMoved) {
			List<Survivor> visibleSurs = lookForSurvivors();
			// if we find some, go towards one of them
			if (visibleSurs.size() > 0) {
				// want to go towards the nearest survivor
				Vector nearestVicVect = null;
				double nearestDistSquare = java.lang.Double.MAX_VALUE;

				// so, we need to figure out which one is the nearest one
				for (Survivor s : visibleSurs) {
					Vector surVect = new Vector(this.getCenterLocation(), s
							.getCenterLocation());
					if (surVect.getMagnitudeSquared() < nearestDistSquare) {
						nearestVicVect = surVect;
						nearestDistSquare = surVect.getMagnitudeSquared();
					}
				}

				// make a bee-line for that survivor!
				actuallyMoveAlong(nearestVicVect);

				haveMoved = true;
			}
		}

		if (!haveMoved) {
			List<Shout> audibleShouts = listenForSurvivors();
			// if we can hear anything, go towards one of them
			if (audibleShouts.size() > 0) {
				// want to go towards the nearest shout
				Vector nearestShoutVect = null;
				double nearestDistSquare = java.lang.Double.MAX_VALUE;

				// so, we need to figure out which one is the nearest one
				for (Shout s : audibleShouts) {
					Vector shoutVect = new Vector(this.getCenterLocation(), s
							.getCenterLocation());
					if (shoutVect.getMagnitudeSquared() < nearestDistSquare) {
						nearestShoutVect = shoutVect;
						nearestDistSquare = s.getCenterLocation().distanceSq(
								this.getCenterLocation());
					}
				}

				// make a bee-line for that survivor!
				actuallyMoveAlong(nearestShoutVect);

				haveMoved = true;
			}
		}

		/*
		 * 2) See if we are within broadcast range of any other robots by seeing
		 * what messages have come in since we last checked. a) If there are
		 * robots nearby, try to maximize distance from them and from base zones
		 */

		if (MOVE_BOT_DEBUG) {
			print("I know about " + otherBotInfo.size() + " other bots");
		}

		if ((!haveMoved) && (otherBotInfo.size() > 0)) {

			Vector botSeperationVector = new Vector(this.getCenterLocation(), this.getCenterLocation());

			for (int i = 0; i < otherBotInfo.size(); i++) {
				BotInfo bi = otherBotInfo.get(i);

				//get the location of the other bot
				Point2D curBotLoc = bi.getCenterLocation();
				double distToCurBot = this.getCenterLocation().distance(curBotLoc);

				//make a random vector if the other bot is right on top of us
				if(Utilities.shouldEqualsZero(distToCurBot)) {
					Vector randomDirVect = Vector.getHorizontalUnitVector(this.getCenterLocation());
					randomDirVect = randomDirVect.rotate(NUM_GEN.nextDouble() * 2.0 * Math.PI);
					curBotLoc = randomDirVect.getP2();
				}

				Vector curBotVect;
				if(distToCurBot < SEPERATION_MIN_DIST) {
					//too close
					//just make a vector pointing away of length 1, since the other vector calculation will be normalized to be between 0 and 1
					curBotVect = new Vector(this.getCenterLocation(), curBotLoc, -1.0);
				} else if (distToCurBot > SEPERATION_MAX_DIST) { 
					//too far
					//don't consider this bot
					continue;
				} else {
					curBotVect = calculateFractionalPotentialVector(curBotLoc, SEPERATION_MIN_DIST, SEPERATION_MAX_DIST, SEPERATION_CURVE_SHAPE, SEPERATION_FACTOR);
				}

				// now add it to the seperation vector
				botSeperationVector = botSeperationVector.add(curBotVect);
			}

			//since there are several other bots, need to divide by the number of bots to end up with an average location
			botSeperationVector = botSeperationVector.rescaleRatio(1.0/(otherBotInfo.size()));

			//			print("Final sep vect mag = " + seperationVector.getMagnitude());

			if(Utilities.shouldEqualsZero(botSeperationVector.getMagnitude())) {
				botSeperationVector = botSeperationVector.rescale(0.0);
			} else {
				World.debugSeperationVectors.add(botSeperationVector.rescaleRatio(10.0));
			}

			//now, scale it up
			//if there are too many nearby bots, make sure it scales up more than the danger zone repulsion
			if(otherBotInfo.size() > FACTOR_ADJUSTMENT_BOT_NUMBER) {
				botSeperationVector = botSeperationVector.rescaleRatio(FACTOR_ADJUSTMENT_SEPERATION_VALUE);
			} else {
				botSeperationVector = botSeperationVector.rescaleRatio(SEPERATION_FACTOR);
			}

			//also, make a cohesion vector, that points toward the average location of the neighboring bots
			//start by calculating the average location of all the bots
			double xSum = 0.0, ySum = 0.0;
			for(BotInfo curBotInfo : otherBotInfo) {
				xSum += curBotInfo.getCenterX();
				ySum += curBotInfo.getCenterY();
			}
			double avgX = xSum / otherBotInfo.size();
			double avgY = ySum / otherBotInfo.size();

			Point2D averageNeighborLocation = new Point2D.Double(avgX, avgY);

			Vector cohesionVector = new Vector(this.getCenterLocation(), averageNeighborLocation);

			//scale the cohesion vector based on it's scaling factor
			cohesionVector = cohesionVector.rescaleRatio(COHESION_FACTOR);

			//also, get a vector pushing us away from bad places
			Vector zoneRepulsionVector = getAllZonesRepulsionVector();

			if(Utilities.shouldEqualsZero(zoneRepulsionVector.getMagnitude())) {
				zoneRepulsionVector = zoneRepulsionVector.rescale(0.0);
			} else {
				World.debugRepulsionVectors.add(zoneRepulsionVector.rescaleRatio(10.0));
			}			
						
//			print("Num neighbors = " + otherBotInfo.size() + "\tsep = " + botSeperationVector.getMagnitude() + "\tzone = " + zoneRepulsionVector.getMagnitude());

			//add up the separation vectors
			//			Vector seperationVector = botSeperationVector.add(zoneRepulsionVector);


			//we want to move along the sum of these vectors
			Vector movementVector = botSeperationVector.add(cohesionVector).add(zoneRepulsionVector);

			timestepSeperationMagnitudeTotal += botSeperationVector.getMagnitude();
			timestepCohesionMagnitudeTotal += cohesionVector.getMagnitude();
			timestepCountOfBotsAffectedBySepOrCohesion++;

			if(! Utilities.shouldEqualsZero(zoneRepulsionVector.getMagnitude())) {
				timestepZoneRepulsionMagnitudeTotal += zoneRepulsionVector.getMagnitude();
				timestepBotsRepelledByZones++;
			}

			// move along the vector we made
			actuallyMoveAlong(movementVector);

			haveMoved = true;
		}

		if (!haveMoved) {
			// move toward the base, hopefully finding other robots and/or
			// getting messages about paths to follow
			//			if (MOVE_BOT_DEBUG) {
			print("No bots within broadcast distance - move back towards base");
			//			}

			Vector baseZoneVect = new Vector(this.getCenterLocation(), baseZone
					.getCenterLocation());

			actuallyMoveAlong(baseZoneVect);

			haveMoved = true;

			//			world.stopSimulation();
		}
	}

	protected void moveRandomly() {
		//calculate a random vector
		//start with a vector to the right
		Vector randomMove = Vector.getHorizontalUnitVector(this.getCenterLocation());
		//make it a random length between our min velocity and max velocity
		randomMove = randomMove.rescale(NUM_GEN.nextDouble() * this.getMaxVelocity());
		//rotate it to a random direction
		randomMove = randomMove.rotate(NUM_GEN.nextDouble() * 2.0 * Math.PI);
		//move along it
		actuallyMoveAlong(randomMove);
	}

	private Vector calculateFractionalPotentialVector(Point2D awayFrom, double minDist, double maxDist, double curveShape, double scalingFactor) {
		//check distances are OK
		if(minDist <= 0.0 || maxDist <= 0.0 || maxDist < minDist) {
			throw new IllegalArgumentException("Distances impossible");
		}

		//check that curve shape is OK
		if(curveShape < 0.0 || curveShape == 2.0) {
			throw new IllegalArgumentException("Illegal Curve Shape");
		}

		double distaceAway = this.getCenterLocation().distance(awayFrom);

		//if our current distance is outside the possible range, throw an error	
		if(distaceAway > maxDist) {
			throw new IllegalArgumentException("Current distance is too big - max = " + maxDist + "\t cur = " + distaceAway);
		}

		if(distaceAway < minDist) {
			throw new IllegalArgumentException("Current distance is too small");
		}

		//make a new vector pointing toward the point
		Vector awayVect = new Vector(this.getCenterLocation(), awayFrom, -1.0);

		//calculate the magnitude of the vector
		double exponent = curveShape - 2.0;
		double vectMag = (Math.pow(distaceAway, exponent) - Math.pow(maxDist, exponent)) / (Math.pow(minDist, exponent) - Math.pow(maxDist, exponent));

		//make sure the magnitude is positive, so we don't flip the vector's direction
		vectMag = Math.copySign(vectMag, 1.0);

		//		print("Partial vect mag = " + vectMag);

		return awayVect.rescale(vectMag * scalingFactor);
	}


	private Vector getAllZonesRepulsionVector() {
		//see which zone-shapes we can see
		List<? extends Shape> visibleShapes = Utilities.findAreaIntersectionsInList(this.getVisibleArea(), World.allZones);

		//go through each of them and get the repulsion from each one
		Vector netRepulsionFromAllZones = new Vector(this.getCenterLocation(), this.getCenterLocation());
		int numContributingShapes = 0;

		for(Shape s : visibleShapes) {
			//should all be zones
			Vector curShapeContribution = getRepulsionContributionFromOneZone((Zone) s);

			if(! Utilities.shouldEqualsZero(curShapeContribution.getMagnitude())) {
				netRepulsionFromAllZones = netRepulsionFromAllZones.add(curShapeContribution);
				numContributingShapes++;
			}
		}

		if(numContributingShapes > 0) {
			//now, need to normalize it
			netRepulsionFromAllZones = netRepulsionFromAllZones.rescaleRatio(1.0/numContributingShapes);
		}

		return netRepulsionFromAllZones;
	}

	private Vector getRepulsionContributionFromOneZone(Zone z) {
		//make sure it is a zone that creates a repulsion
		if(! z.causesRepulsion()) {
			return new Vector(this.getCenterLocation(), this.getCenterLocation());
		}

		Vector netRepulsionFromZone = new Vector(this.getCenterLocation(), this.getCenterLocation());

		//get the repulsion vectors from each of the sides
		List<LineSegment> dzSides = Utilities.getSides(z);

		LineSegment visibleSegment;
		Point2D visSegMidpoint;
		Vector thisSideContribution;
		int numContributingSides = 0;

		//TODO I think this isn't normalized if we consider more than one side - do we maybe just want to consider the closest side or the closest midpoint?
		for(LineSegment s : dzSides) {
			//get the part of the segment that we can see, since that is the only part that should be exerting a force
			visibleSegment = this.getVisibleArea().getLineIntersectionSegment(s);

			if(visibleSegment == null) {
				//no contribution from this segment
				continue;
			}

			World.debugShapesToDraw.add(visibleSegment);

			timestepVisibleZoneSideTotal += visibleSegment.getLength();
			timestepNumVisibleZoneSides++;

			visSegMidpoint = visibleSegment.getMidpoint();

			//calculate the force from this side and add it to the net repulsion from the zone
			if(this.getCenterLocation().distance(visSegMidpoint) < z.repulsionMinDist()) {
				//distance too small
				//just have a vector of size 1 pointing away from the midpoint
				thisSideContribution = new Vector(this.getCenterLocation(), visSegMidpoint, -1.0);
			} else {
				thisSideContribution = calculateFractionalPotentialVector(visSegMidpoint, z.repulsionMinDist(), z.repulsionMaxDist(), z.repulsionCurveShape(), z.repulsionScalingFactor());
			}
			
			//reverse the vector if we are inside the zone
			if(z.contains(this.getCenterLocation())) {
				thisSideContribution = thisSideContribution.rescaleRatio(-1.0);
			}

			netRepulsionFromZone = netRepulsionFromZone.add(thisSideContribution);
			numContributingSides++;
		}

		if(numContributingSides > 0) {
			//need to divide by the number of sides to average it out
			netRepulsionFromZone = netRepulsionFromZone.rescaleRatio(1.0/numContributingSides);
		}

		//		print("Net repulsion vector = " + netRepulsionFromZone.getMagnitude());

		return netRepulsionFromZone;
	}





	private void actuallyMoveAlong(Vector v) {
		if (MOVE_BOT_DEBUG)
			print("Current location : " + this.getCenterLocation());

		// make sure the vector starts in the right place
		if (!v.getP1().equals(this.getCenterLocation())) {
			// move the vector to fix this
			print("HAD TO ADJUST MOVE VECTOR");
			v = v.moveTo(this.getCenterLocation());
		}

		// make sure the vector isn't too long i.e. assert our max velocity
		// this basically allows us to move to the end of the vector as 1 step
		v = verifyMovementVector(v);

		if (MOVE_BOT_DEBUG)
			print("Moving along vector '" + v + "'");

		// don't hit the walls of the bounding box
		if (Utilities.edgeIntersects(this.boundingBox, currentZone
				.getVisibilityArea(getCenterLocation()))) {
			// this means we can "see" the edge of the bounding box
			// try to move such that we don't hit it
			v = boundingBox.getPathThatStaysInside(v);
		}

		// again, make sure our movement vector is legal
		v = verifyMovementVector(v);

		if (MOVE_BOT_DEBUG)
			print("rescaled vector is " + v);

		// now that everything is all set with the vector, we can move to the
		// other end of it
		this.setCenterLocation(v.getP2());

		movementVector = v;

		//tell everyone where we are
		Message locationMessage = constructLocationMessage();
		broadcastMessage(locationMessage);
	}

	// makes sure that the passed movement vector is OK i.e. it isn't too long
	private Vector verifyMovementVector(Vector v) {
		if (v.getMagnitude() > getMaxVelocity()) {
			v = v.rescale(getMaxVelocity());
		}
		return v;
	}

	@SuppressWarnings("unchecked")
	private List<Survivor> lookForSurvivors() {
		// first, get our visibility radius
		Shape visibilityRange = getVisibleArea();

		// see if the location of any of our survivors intersects this range
		List<Survivor> visiblesurvivors = (List<Survivor>) Utilities
		.findAreaIntersectionsInList((Shape) visibilityRange,
				World.allSurvivors);

		if (LOOK_BOT_DEBUG)
			print("In perfect world, would have just seen "
					+ visiblesurvivors.size() + " survivors");

		// ignore any vicitms we know have been claimed
		// use a list iterator for the reasons described below
		ListIterator<Survivor> vicIteratior = visiblesurvivors.listIterator();
		while (vicIteratior.hasNext()) {
			Survivor curSur = vicIteratior.next();
			if (claimedSurvivors.contains(curSur)) {
				vicIteratior.remove();
			}
		}

		// visibilesurvivors is now a list of all the survivors the robot could
		// see and dosen't already know about
		// if it was perfect but it's not, and there is some probability that it
		// will miss some of the survivors
		// so, go through the list and remove some or all of the survivors with
		// that probability.
		// need to use an iterator, because it won't let us remove as we go
		// otherwise
		vicIteratior = visiblesurvivors.listIterator();
		while (vicIteratior.hasNext()) {
			vicIteratior.next();
			if (!(NUM_GEN.nextDouble() <= VISUAL_ID_SURVIVOR_PROB)) {
				vicIteratior.remove(); // removes from the iterator AND the list
			}
		}

		if (LOOK_BOT_DEBUG)
			print("Actually able to see " + visiblesurvivors.size()
					+ " survivors");

		// we have our list survivors that the Bot saw - return it
		return visiblesurvivors;
	}

	@SuppressWarnings("unchecked")
	private List<Shout> listenForSurvivors() {
		// first, get our auditory radius
		Shape auditoryRange = getAuditbleArea();

		// see if any of the shouts we know about intersect this range
		List<Shout> audibleShouts = (List<Shout>) Utilities
		.findAreaIntersectionsInList((Shape) auditoryRange, heardShouts);

		if (LISTEN_BOT_DEBUG)
			print("In perfect world, would have just heard "
					+ audibleShouts.size() + " vicitms");

		// ignore any shouts that sound like they're coming from vicitims have
		// already been claimed
		// use a list iterator for the reasons described below
		ListIterator<Shout> shoutIterator = audibleShouts.listIterator();
		while (shoutIterator.hasNext()) {
			Shout curShout = shoutIterator.next();
			if (claimedSurvivors.contains(curShout.getShouter())) {
				shoutIterator.remove();
			}
		}

		// audible shouts is now all the shouts we could hear if the robot could
		// hear perfectly
		// but it can't - we're using a probability to model this fact
		// so, go through the list and remove the ones that probability says we
		// can't identify
		shoutIterator = audibleShouts.listIterator();
		while (shoutIterator.hasNext()) {
			shoutIterator.next();
			if (!(NUM_GEN.nextDouble() <= HEAR_SURVIVOR_PROB)) {
				shoutIterator.remove(); // remove from iterator AND list
			}
		}

		if (LOOK_BOT_DEBUG)
			print("Actually just heard " + audibleShouts.size() + " survivors");

		// we have our list of shouts - return it
		return audibleShouts;
	}

	private void updateZoneInfo() {
		currentZone = World.findZone(getCenterLocation());

		if (currentZone == null) {
			print("AHH! WE DON'T KNOW WHAT ZONE WE'RE IN!! - "
					+ this.getCenterX() + ", " + getCenterY());
			print("Just moved: " + movementVector);
		}

		if (currentZone.isObstacle()) {
			print("AHHHH!!!! I'M MELTING!!!!");
			world.stopSimulation();
		}

		// reasses the zones's status if we move to a new zones
		assessZone();
	}

	private void assessZone() {
		// with some probability, the bot will asses the zones correctly
		if (NUM_GEN.nextDouble() < CORRECT_ZONE_ASSESMENT_PROB) {
			if (currentZone instanceof SafeZone) {
				zoneAssesment = ZONE_SAFE;
			} else if (currentZone instanceof BaseZone) {
				zoneAssesment = ZONE_BASE;
			} else {
				zoneAssesment = ZONE_DANGEROUS;
			}
		} else {
			// if we don't get it right, assign the incorrect value
			if (currentZone instanceof SafeZone) {
				zoneAssesment = ZONE_DANGEROUS;
			} else {
				zoneAssesment = ZONE_SAFE;
			}
		}
	}

	private double assesSurvivor(Survivor s) {
		// with some probability we'll get it wrong
		if (NUM_GEN.nextDouble() < ASSES_SURVIVOR_CORRECTLY_PROB) {
			return s.getDamage();
		} else {
			return NUM_GEN.nextInt(101) / 100.0;
		}
	}

	private void findAndAssesSurvivor() {
		// first, see if there are any survivors that we can see
		List<Survivor> visibleSurvivors = lookForSurvivors();

		// see if any of them are within the FOUND_RANGE
		List<Survivor> foundSurvivors = new ArrayList<Survivor>();

		for (Survivor v : visibleSurvivors) {
			if (v.getCenterLocation().distance(this.getCenterLocation()) < DEFAULT_FOUND_RANGE) {
				foundSurvivors.add(v);
			}
		}

		// we now know what survivors we have found
		// evaluate each of them in turn
		for (Survivor s : foundSurvivors) {
			double surDamage = assesSurvivor(s);

			// if someone has already claimed this survivor, don't bother
			// letting everyone know about them
			if (claimedSurvivors.contains(s)) {
				continue;
			}

			// send out a message letting everyone know where the survivor is,
			// what condition they are in, and how safe the zones is
			Message message = constructFoundMessage(s, surDamage);

			broadcastMessage(message);

			knownSurvivors.add(s);
		}

		// claim the one that is closest and that has not yet been claimed
		List<Survivor> claimableSurvivors = new ArrayList<Survivor>(
				foundSurvivors);
		claimableSurvivors.removeAll(claimedSurvivors);

		if (claimableSurvivors.size() > 0) {
			// figure out which one is closets
			Survivor closestSurvivor = claimableSurvivors.get(0);
			double closestSurvivorDist = getCenterLocation().distance(
					closestSurvivor.getCenterLocation());
			for (Survivor curSur : claimableSurvivors) {
				double curDist = getCenterLocation().distance(
						curSur.getCenterLocation());
				if (curDist < closestSurvivorDist) {
					closestSurvivor = curSur;
					closestSurvivorDist = curDist;
				}
			}

			// claim the closest one
			mySurvivor = closestSurvivor;
			Message message = constructClaimMessage();
			mySurvivorClaimTime = World.getCurrentTimestep();
			if(message != null) {
				broadcastMessage(message);
			}
		}
	}

	private void updateAveragePreviousLocation() {
		// basically, we're going to take simple moving average of the previous
		// moves
		// start by storing the current location
		double avgX, avgY;

		// if(SETTLE_DEBUG) {
		// print("Considering " + previousLocations.size() +
		// " previous locations");
		// print(previousLocations.toString());
		// }

		if (previousLocations.size() < NUM_PREV_LOCATIONS_TO_CONSIDER) {
			previousLocations.add(0, this.getCenterLocation());
			// since we don't have a full dataset yet, don't take an average

			avgX = DEFAULT_AVERAGE_LOCATION_VALUE;
			avgY = DEFAULT_AVERAGE_LOCATION_VALUE;
		} else {
			// add the new value
			previousLocations.add(0, this.getCenterLocation());

			// if this is the first time we are calculating the average, need to
			// calculate it from scratch
			if (averagePreviousLocation.getX() == DEFAULT_AVERAGE_LOCATION_VALUE
					&& averagePreviousLocation.getY() == DEFAULT_AVERAGE_LOCATION_VALUE) {
				// remove the oldest value
				previousLocations.remove(NUM_PREV_LOCATIONS_TO_CONSIDER);

				// calculate the average value of the values we have
				double xSum = 0, ySum = 0;
				for (Point2D curPoint : previousLocations) {
					xSum += curPoint.getX();
					ySum += curPoint.getY();
				}

				avgX = (xSum / NUM_PREV_LOCATIONS_TO_CONSIDER);
				avgY = (ySum / NUM_PREV_LOCATIONS_TO_CONSIDER);
			} else {
				// already have an average value
				// do the whole moving average thing
				double oldAvgX = averagePreviousLocation.getX();
				double oldAvgY = averagePreviousLocation.getY();

				Point2D oldestPoint = previousLocations
				.remove(NUM_PREV_LOCATIONS_TO_CONSIDER);
				Point2D newestPoint = previousLocations.get(0);
				// subtract the oldest point's contribution to the average
				// and add the newest point's contribution to the average
				avgX = oldAvgX
				- (oldestPoint.getX() / NUM_PREV_LOCATIONS_TO_CONSIDER)
				+ (newestPoint.getX() / NUM_PREV_LOCATIONS_TO_CONSIDER);
				avgY = oldAvgY
				- (oldestPoint.getY() / NUM_PREV_LOCATIONS_TO_CONSIDER)
				+ (newestPoint.getY() / NUM_PREV_LOCATIONS_TO_CONSIDER);
			}
		}

		// make the point out of the average values
		averagePreviousLocation = new Point2D.Double(avgX, avgY);
	}

	private void determineIfSettledOnLocation() {		
		// first, update our average location
		updateAveragePreviousLocation();

		// now, see if the average location is within the threshold of where we
		// are
		// that determines if we have settled on a location
		if (SETTLE_DEBUG) {
			print("I am at '"
					+ this.getCenterLocation()
					+ "' average location is '"
					+ averagePreviousLocation
					+ "' distance between them is '"
					+ averagePreviousLocation
					.distance(this.getCenterLocation()) + "'");
		}

		settledOnLocation = averagePreviousLocation.distance(this
				.getCenterLocation()) < STOP_SPREADING_THRESHOLD;
	}

	private void handlePhaseElectionIfNeeded() {
		//see if we are waiting on results
		if(myElectionStartTime > 0) {
			//see if the election is over
			//first, check if we have gotten a no back
			if(! electionLooksSuccessful) {
				//the election failed
				//start another one in a while
				//basically, to do this, set the lastMoveToNextPhaseMessageRecievedTime such that it will start when we want it to
				//i.e. set it at MOVE_TO_NEXT_PHASE_TIME_THRESHOLD ago
				//so that we start before anyone else does (hopefully)
				lastMoveToNextPhaseMessageRecievedTime = World.getCurrentTimestep() - MOVE_TO_NEXT_PHASE_TIME_THRESHOLD;
				myElectionStartTime = -1;
			} else {
				//see if the election is over
				int timeToEndElection = myElectionStartTime + MOVE_TO_NEXT_PHASE_TIME_THRESHOLD;
				if(World.getCurrentTimestep() >= timeToEndElection) {
					//the election is over, and no one has objected
					//broadcast that we should move on
					Message moveOnMessage = constructPhaseAdvancementMessage();
					broadcastMessage(moveOnMessage);
				}
			}
		}
		else if(lastMoveToNextPhaseMessageRecievedTime > 0) {
			//see if we need to start an election
			int timeToStartElection = lastMoveToNextPhaseMessageRecievedTime + MOVE_TO_NEXT_PHASE_TIME_THRESHOLD*2;
			if(World.getCurrentTimestep() >= timeToStartElection) {
				startElection();
			}
		} else {
			//no election has been started
			//start it myself
			startElection();
		}
	}

	private void startElection() {
		Message electionStartMesasge = constructPhaseAdvancementElectionMessage();
		broadcastMessage(electionStartMesasge);
		myElectionStartTime = World.getCurrentTimestep();
		electionLooksSuccessful = true;
		print("Starting move on to next phase election");
	}

	private void print(String message) {
		if (OVERALL_BOT_DEBUG) {
			System.out.println(botID + ":\t" + message);
			System.out.flush();
		}
	}

	private void print(int message) {
		this.print("" + message);
	}

	public void doOneTimestep() {
		// first, read any messages that have come in, and take care of them
		readMessages();

		switch (algorithmPhase) {
			case (WAITING_TO_BE_TURNED_ON_PHASE):
				//with some probability, we'll be turned on this timestep
				//if that probability is right, turn on and move to the next phase
				if(NUM_GEN.nextDouble() < TURNED_ON_THIS_TIMESTEP_PROB) {
					algorithmPhase = SPREAD_OUT_PHASE;
				}

			break;
			case (SPREAD_OUT_PHASE) :
				// now try to move, based on the move rules.
				move();
			// if we have not already claimed a survivor, find out if we can see any survivors
			// TODO if they have heard a survivor, check it out for a few steps
			if (mySurvivor == null) {
				findAndAssesSurvivor();
			}

			// decide if we should stop moving
			determineIfSettledOnLocation();

			if(settledOnLocation) {
				//handle starting an election to move onto the next phase if needbe
				handlePhaseElectionIfNeeded();
			}

			break;
			case (CREATE_PATHS_PHASE) :

				break;
			case (AGGRIGATE_PHASE) :

				break;
			default :
				/*
				 * TODO fix this so that it asks neighbor what phase it is
				 * shouldn't happen regardless, so this doesn't really matter
				 */
				algorithmPhase = WAITING_TO_BE_TURNED_ON_PHASE;
				break;
		}

		// now, just some housekeeping
		// we shouldn't hang onto shouts for too long
		heardShouts.clear();
		// also don't want to hang on to bot info for too long
		otherBotInfo.clear();

		// make sure we are still in the zones we think we are in
		if (currentZone == null || (!currentZone.contains(getCenterLocation()))) {
			updateZoneInfo();
		}
		if (MOVE_BOT_DEBUG) {
			print("");
		}
	}
}