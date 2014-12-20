package spothunt;

import java.util.ArrayList;
import java.util.List;

/**
 * This class takes care of the <code>MovingSpot</code>.<br>
 * It controls a Player (e.g. <code>setLocation</code>, <code>getX</code>, <code>toString</code>) and 
 * it holds the value for <code>x</code> and <code>y</code>.<br>
 * <b>It implements Spot.</b>
 * @author Jeroen
 * @version 1.0
 *
 */
public class MovingSpot implements Spot {
	int x = 0;
	int y = 0;
	Playfield playfield;
	public static Factor[] factors = new Factor[]{Factor.TPD, Factor.SD, Factor.FDC, Factor.HD};
	
	/**
	 * The constructor of <code>MovingSpot</code>. This will link the spot to a <code>Playfield</code> and set it to its start location <code>[0,0]</code>.
	 * @param playfield	the Playfield it is linked to.
	 */
	public MovingSpot(Playfield playfield) {
		assert playfield != null : "Playfield not found!";
		this.playfield = playfield;
			// Set MovingSpot at its start position at [0,0]
		playfield.cells[0][0].putSpot();
	}
	
	
	public void setLocation(int newX, int newY) {
		assert newY >= 0 && newX >= 0 : "NewX and newY have to be larger than 0! (NewX,NewY = " + newX + "," + newY + ")";
		assert newY < playfield.width && newX < playfield.height : "NewX and newY are not within the height and width of the field! (NewX,NewY = " + newX + "," + newY + ")";
		
			// Update cells
		playfield.cells[x][y].removeSpot();
		x = newX;
		y = newY;
		playfield.cells[x][y].putSpot();
	}
	
	/**
	 * <code>pickTarget</code> picks the best <code>GoalSpot</code> based on:<br>
	 * <ul>
	 * 		<li> TPD (Total Player Distance): How far are all players away from the <code>GoalSpot</code>?</li>
	 * 		<li> SD (Spot Distance): How far away is the MovingSpot away from the <code>GoalSpot</code>?</li>
	 * 		<li> FDC (Fastest Danger Cost): How dangerous is the fastest path to the <code>GoalSpot</code>?</li>
	 * 		<li> ST (Surround Threat): How dangerous is the area around the <code>GoalSpot</code>?</li>
	 * 		<li> HD (Highest Danger): What is the highest danger level while moving over the FDC-path to the <code>GoalSpot</code>?</li>
	 * </ul>
	 * @param allGoals	the array of all the GoalSpots on the Playfield
	 * @return target	the GoalSpot that is the best choice to move to
	 */
	public GoalSpot pickTarget(GoalSpot[] allGoals) {
			// Create the empty array that will contain all the PossibleTargets.
		PossibleTarget[] possibleTargets = new PossibleTarget[allGoals.length];
		
			// Create all the PossibleTargets (out of GoalSpots) and set the correct values for HD, TPD, SD, FDC and ST.
		for(int i = 0; i < allGoals.length; i ++) {
			GoalSpot current = allGoals[i];
			possibleTargets[i] = new PossibleTarget(current);
			PossibleTarget possible = possibleTargets[i];
			int highestDanger = 0;
			int calculatedCost = 0;
			double penalty = 1;
			
				// Calculate the Fastest Danger Cost to this spot
			calculatedCost = current.calculateDangerCost(x, y);
			
			//TODO: Move to the calculateSurround() in GoalSpot
				// Check if the GoalSpot is located against the wall and add a penalty
			if(current.getX()==0 || current.getX()==playfield.width-1 
					|| current.getY()==playfield.height || current.getY()==0) {
				penalty = 1.6;
			}
			
				// Set all the values for the variables in PossibleTarget
			possible.setHighestDanger(highestDanger);
			possible.setTPD(current.getTPD());
			possible.setSD(current.getSD());
			possible.setPenalty(penalty);
			possible.setCalcCost(calculatedCost);
			int weightedSurThreat = (int) (current.calculateSurround() * penalty);
			possible.setSurThreat(weightedSurThreat);
		}
		
			// Loop through all factors and rate them for each of the PossibleTargets
		for(Factor current : factors) {
			possibleTargets = rateFactor(possibleTargets, current);	
		}
		
			// Create a new list BestOptions to compare the ratings
		List<PossibleTarget> bestOptions = new ArrayList<PossibleTarget>();
			// Compare ratings between all the PossibleTargets
		bestOptions = compareRatings(possibleTargets);
		
			// Check if bestOptions.size() is 1, as that means there is only one best Spot -> return this spot
		if(bestOptions.size()==1) {
			return bestOptions.get(0).toGoalSpot();
		} // Else: Continue with all the best options left
		
			// Make new list to be rated on independent factors (bestOptions = backup)
		List<PossibleTarget> compareOptions = bestOptions;
			// Loop through all the factors and compare them for all the remaining best options
		for(Factor current : factors) {
			compareOptions = compareFactor(current, compareOptions);
				// If the list has a size of 1 then, a result has been found and return this GoalSpot
			if(compareOptions.size()==1) {
				return compareOptions.get(0).toGoalSpot();
				// Else reset compareOptions with bestOptions and then try again for the next factor
			/*} else {
				compareOptions = bestOptions; */
			}
		}
			//If all else fails, pick a random target from the original PossibleTargets
		return pickRandomGoal(possibleTargets);
	}
	
	/**
	 * <code>rateFactor</code> will rate the given <code>factor</code> and will give the correct rating for each of the <code>PossibleTargets</code>.
	 * @param possibleTargets	an array of all the PossibleTargets on the Playfield
	 * @param factor			the name of the factor that will be rated
	 * @return possibleTargets	the updated array of all PossibleTargets with updated ratings
	 */
	private PossibleTarget[] rateFactor(PossibleTarget[] possibleTargets, Factor factor) {
			// Set the first possibleTarget as the current best target
		PossibleTarget best = possibleTargets[0];
			// Create a new Boolean[] that will contain the results of comparePossibleTargets
		Boolean[] compares = new Boolean[2];
			// Set the rating to the corresponding Factor rating
		double rating = factor.getRating();
			// Create new list that will contain all the equals
		List<PossibleTarget> equals = new ArrayList<PossibleTarget>();
			// Add the current best to the list of equals
		equals.add(best);
				// Loop through all the PossibleTargets and compare them with the current best
			for(int k=1; k<possibleTargets.length; k++) {
					// Set the current possibleTarget as 'current'
				PossibleTarget current = possibleTargets[k];
					// Compare the current with the best PossibleTarget, based on a factor
				compares = comparePossibleTargets(factor, current, best);
				if(compares[0]) { // if current >/< than the best, remove the best from the list and set this as best and add to list
					for(int j=0; j < equals.size(); j++) {
						equals.get(j).rating = equals.get(j).rating - rating;
					}
					equals.clear();
					best = current;
					best.rating = best.rating + rating;
					equals.add(best);
				} else if (compares[1]) { // else (current==best), name this one as best and add this one also to the list
					best = current;
					best.rating = best.rating + rating;
					equals.add(best);
				}
			}
		return possibleTargets;
	}
	
	/**
	 * <code>compareFactor</code> compares <code>Factor</code> values between two <code>PossibleSpot</code>s
	 * @param factor			the <code>Factor</code> that has to be compared
	 * @param compareOptions	the list of PossibleTargets that have to be compared
	 * @return equals			, the list of PossibleTargets (in this case the length is either 1 or 0)
	 */
	private List<PossibleTarget> compareFactor(Factor factor, List<PossibleTarget> compareOptions) {
			// Set the first possibleTarget as the current best target
		PossibleTarget best = compareOptions.get(0);
			// Create new list that will contain all the equals
		List<PossibleTarget> equals = new ArrayList<PossibleTarget>();
			// Add the current best to the list of equals
		equals.add(compareOptions.get(0));
			// Create a new Boolean[] that will contain the results of comparePossibleTargets
		Boolean[] compares = new Boolean[2];
			// Loop through all the PossibleTargets and compare the values of the factors
		for(int k=1; k<compareOptions.size(); k++) {
				// set the current PossibleTarget as 'current'
			PossibleTarget current = compareOptions.get(k);
				// Compare the current with the best PossibleTarget, based on a factor
			compares = comparePossibleTargets(factor, current, best);
			if(compares[0]) { // if current >/< than the best, remove the best from the list and set this as best and add to list
				equals.clear();
				best = current;
				equals.add(best);
			} else if (compares[1]){ // else (current==best), clear the list and break (as it turns out there are more than one with the same valuees)
				equals.clear();
				break;
			}
		}
		return equals;
	}
	
	/**
	 * <code>comparePossibleTargets</code> is used in <code>rateFactor</code> to compare two <code>PossibleTargets</code>'s factor values.
	 * @param factor	the factor of which the value has to be compared
	 * @param possible	the first PossibleSpot (the one that will be placed before the evaluation symbol)
	 * @param best		the second PossibleSpot (the one that will be placed behind the evaluation symbol)
	 * @return result	an Boolean[2] array. Index 0 will contain true/false for the >/< evaluator and Index 1 will contain the true/false for the == evaluator.
	 */
	private Boolean[] comparePossibleTargets(Factor factor, PossibleTarget possible, PossibleTarget best) {
			// Create a new Boolean[] that will contain the results of comparePossibleTargets
		Boolean[] result = new Boolean[2];
		
			// Look at which factor has to be compared and compare them between two PossibleTargets
		switch(factor) {
			case TPD:
				result[0] = possible.getTPD() > best.getTPD();
				result[1] = possible.getTPD() == best.getTPD();
				break;
			case ST:
				result[0] = possible.getSurThreat() < best.getSurThreat();
				result[1] = possible.getSurThreat() == best.getSurThreat();
				break;
			case SD:
				result[0] = possible.getSD() < best.getSD();
				result[1] = possible.getSD() == best.getSD();
				break;
			case FDC:
				result[0] =	possible.getCalcCost() < best.getCalcCost();
				result[1] = possible.getCalcCost() == best.getCalcCost();
				break;
			case HD: 
				result[0] = possible.getHighestDanger() < best.getHighestDanger();
				result[1] = possible.getHighestDanger() == best.getHighestDanger();
				break;
		}
		
		return result;
	}
	
	/**
	 * <code>compareRatings</code> compares all the ratings of <code>PossibleTarget</code>s and retuns the best ones
	 * @param possibleTargets	an array of all the PossibleTargets
	 * @return bestOptions, the list of the best PossibleTargets
	 */
	private List<PossibleTarget> compareRatings(PossibleTarget[] possibleTargets) {
			// create list bestOptions, which will be all the PossibleTargets with the same and highest values
		List<PossibleTarget> bestOptions = new ArrayList<PossibleTarget>();
			// set the first possibleTarget as current best target
		PossibleTarget highestRating = possibleTargets[0];
			// Add this target to the list of bestOptions
		bestOptions.add(highestRating);
			// Loop through all the possibleTargets and compare their ratings
		for(int k = 0; k < possibleTargets.length; k++) {
			if(highestRating.rating < possibleTargets[k].rating) {	// if one with a higher rating is found, empty the list and add this as best
				bestOptions.clear();
				highestRating = possibleTargets[k];
				bestOptions.add(possibleTargets[k]);
			} else if (highestRating.rating == possibleTargets[k].rating) { // else (same ratings), add the current rating to bestOptions
				bestOptions.add(possibleTargets[k]);
			}
		}
		return bestOptions;
	}
	
	/**
	 * Picks a random <code>GoalSpot</code> from a given given <code>PossibleTarget</code>s aray
	 * @param possibleTargets the array containing the PossibleTargets
	 * @return target	, random GoalSpot
	 */
	private GoalSpot pickRandomGoal(PossibleTarget[] possibleTargets) {
		GoalSpot target = null;
			// Range is how many spots there are (-1, as the length is 1 higher than the amount of indexes used)
		int range = possibleTargets.length-1;
			// Math.random picks a number between 0 and 1, so balance that with the range and you should get a number between 0 and range (at least thats the idea..)
		int picked = (int) (Math.random()*range);
			// Pick the spot from possibleTargets that is the same as the random picked number
		target = possibleTargets[picked].toGoalSpot();
		return target;
	}
	
	public int getX() {
		return x;
	}
	
	public int getY() {
		return y;
	}
	
	public String toString() {
		return "[" + this.x + "," + this.y + "]";
	}
}
