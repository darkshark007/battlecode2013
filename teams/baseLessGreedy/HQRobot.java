package baseLessGreedy;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.Team;
import battlecode.common.Upgrade;

public class HQRobot extends BaseRobot {
	
	public ChannelType powerChannel = ChannelType.HQPOWERLEVEL;
	public ChannelType strategyChannel = ChannelType.STRATEGY;
	
	public boolean allInMode = false;
	public boolean ourNukeHalfDone = false;
	
	public int nukeProgress = 0;
	public int nukePowerThreshold = 150;
	
	public HQRobot(RobotController rc) throws GameActionException {
		super(rc);
		strategy = decideStrategy();
		
//		if (rc.getTeam() == Team.A) {
//			strategy = Strategy.ECON;
//		} else {
//			strategy = Strategy.NUKE;
//		}
		
		EncampmentJobSystem.initializeConstants();
	}
	
	public Strategy decideStrategy() throws GameActionException {
		int numPossibleArtilleryLocations = EncampmentJobSystem.getPossibleArtilleryLocations().length;
		
		rc.setIndicatorString(1, Integer.toString(numPossibleArtilleryLocations));
		MapLocation midPoint = findMidPoint();
		int rSquared = DataCache.rushDistSquared / 4;
		double mineDensity = rc.senseMineLocations(midPoint, rSquared, Team.NEUTRAL).length / (3.0 * rSquared);
		
//		System.out.println(numPossibleArtilleryLocations + ", " + DataCache.rushDistSquared + ", " + rc.senseMineLocations(midPoint, rSquared, Team.NEUTRAL).length + ", " + mineDensity);
//		String s = numPossibleArtilleryLocations + ", " + DataCache.rushDistSquared + ", " + rc.senseMineLocations(midPoint, rSquared, Team.NEUTRAL).length + ", " + mineDensity;
//		rc.setIndicatorString(1, s);
		
		if (numPossibleArtilleryLocations >= 3) {
//			System.out.println("nuke pls");
			return Strategy.NUKE;
		} else {
//			System.out.println("econ");
			return Strategy.ECON;
		}
	}
	
	private MapLocation findMidPoint() {
		MapLocation enemyLoc = DataCache.enemyHQLocation;
		MapLocation ourLoc = DataCache.ourHQLocation;
		int x, y;
		x = (enemyLoc.x + ourLoc.x) / 2;
		y = (enemyLoc.y + ourLoc.y) / 2;
		return new MapLocation(x,y);
	}
	
	@Override
	public void run() {
		try {
//			rc.setIndicatorString(0, Integer.toString(rc.checkResearchProgress(Upgrade.NUKE)));
			
			DataCache.updateRoundVariables();
			BroadcastSystem.write(powerChannel, (int) rc.getTeamPower()); // broadcast the team power
			BroadcastSystem.write(strategyChannel, strategy.ordinal()); // broadcast the strategy
			
			// Check if our nuke is half done
			nukeProgress = rc.checkResearchProgress(Upgrade.NUKE);
			if (!ourNukeHalfDone) {
				if (nukeProgress >= 200) {
					ourNukeHalfDone = true;
				}
			}
			// Check if enemy's nuke is half done
			if (!enemyNukeHalfDone) {
				enemyNukeHalfDone = rc.senseEnemyNukeHalfDone();
			}
			
			
			if (ourNukeHalfDone) {
				if (nukeProgress >= 205 && !enemyNukeHalfDone) {
					// make soldiers
					nukePowerThreshold = 100;
				} else {
					nukePowerThreshold = 150;
				}
				// Broadcast this				
				BroadcastSystem.write(ChannelType.OUR_NUKE_HALF_DONE, 1);
			}
			if (enemyNukeHalfDone) {
				// If it is half done, broadcast it
				if (!ourNukeHalfDone) {
					allInMode = true;
				}
				BroadcastSystem.write(ChannelType.ENEMY_NUKE_HALF_DONE, 1);
			}
			
			// to handle broadcasting between channel cycles
			if (Clock.getRoundNum() % Constants.CHANNEL_CYCLE == 0 && Clock.getRoundNum() > 0) {
                EncampmentJobSystem.updateJobsOnCycle();
	        } else {
	            EncampmentJobSystem.updateJobsAfterChecking();
	        }
			
			if (rc.isActive()) {
				if (strategy == Strategy.ECON || strategy == Strategy.RUSH) {
					boolean upgrade = false;
					if (enemyNukeHalfDone && !DataCache.hasDefusion && DataCache.numAlliedSoldiers > 5) {
						upgrade = true;
						rc.researchUpgrade(Upgrade.DEFUSION);
					} else if (rc.getTeamPower() < 100) {
						if (!DataCache.hasDefusion) {
							upgrade = true;
							rc.researchUpgrade(Upgrade.DEFUSION);
						} else if (!DataCache.hasFusion) {
							upgrade = true;
							rc.researchUpgrade(Upgrade.FUSION);
						} else {
							upgrade = true;
							rc.researchUpgrade(Upgrade.NUKE);
						}
					}
					if (!upgrade) {
						spawnSoldier();
					}
				} else if (strategy == Strategy.NUKE) {
					boolean upgrade = false;
					if (allInMode) {
						if (!DataCache.hasDefusion) {
							upgrade = true;
							rc.researchUpgrade(Upgrade.DEFUSION);
						}						
						if (!upgrade) {
							spawnSoldier();
						}
					} else {
						if (rc.getTeamPower() < nukePowerThreshold ||
								(DataCache.numNearbyEnemySoldiers == 0 && rc.checkResearchProgress(Upgrade.NUKE) > 385 && rc.getEnergon() > 475)) {
							upgrade = true;
							rc.researchUpgrade(Upgrade.NUKE);
						}
						if (!upgrade) {
							spawnSoldier();
						}
					}
				}
			}
		} catch (Exception e) {
			System.out.println("caught exception before it killed us:");
			System.out.println(rc.getRobot().getID());
			e.printStackTrace();
		}
	}
	
	public void spawnSoldier() throws GameActionException {
		Direction desiredDir = rc.getLocation().directionTo(DataCache.enemyHQLocation);
		Direction dir = getSpawnDirection(desiredDir);
		if (dir != null) {
			rc.spawn(dir);
		}
	}

	/**
	 * helper fcn to see what direction to actually go given a desired direction
	 * @param rc
	 * @param dir
	 * @return
	 */
	private Direction getSpawnDirection(Direction dir) {
		Direction canMoveDirection = null;
		int desiredDirOffset = dir.ordinal();
		int[] dirOffsets = new int[]{4, -3, 3, -2, 2, -1, 1, 0};
		for (int i = dirOffsets.length; --i >= 0; ) {
			int dirOffset = dirOffsets[i];
			Direction currentDirection = DataCache.directionArray[(desiredDirOffset + dirOffset + 8) % 8];
			if (rc.canMove(currentDirection)) {
				if (canMoveDirection == null) {
					canMoveDirection = currentDirection;
				}
				Team mineTeam = rc.senseMine(rc.getLocation().add(currentDirection));
				if (mineTeam == null || mineTeam == rc.getTeam()) {
					// If there's no mine here or the mine is an allied mine, we can spawn here
					return currentDirection;
				}
			}			
		}
		// Otherwise, let's just spawn in the desired direction, and make sure to clear out a path later
		return canMoveDirection;
	}

//	public static void main(String[] arg0) {
//		MapLocation origin = new MapLocation(0,0);
//
//		MapLocation test1 = new MapLocation(5,0);
//		MapLocation test2 = new MapLocation(4,1);
//		MapLocation test3 = new MapLocation(3,2);
//		MapLocation test4 = new MapLocation(1,3);
//		MapLocation test5 = new MapLocation(6,2);
//		MapLocation test6 = new MapLocation(0,5);
//		MapLocation test7 = new MapLocation(2,3);
//
//		MapLocation[] oldJobsList = {test1, test2, test3, test4, null};
//		MapLocation[] newJobsList = {test2, test4, test5, null, null};
//		ChannelType[] oldChannelsList = {ChannelType.ENC1, ChannelType.ENC2, ChannelType.ENC3, ChannelType.ENC4, ChannelType.ENC5};
//
//		ChannelType[] newChannelsList = EncampmentJobSystem.assignChannels(newJobsList, oldJobsList, oldChannelsList);
//
//		for (int i=0; i<newChannelsList.length; i++) {
//			System.out.println("result " + i + ": " + newChannelsList[i]);
//		}
//
//	}
}
