//==============================================================================
//	
//	Copyright (c) 2002-
//	Authors:
//	* Dave Parker <david.parker@comlab.ox.ac.uk> (University of Oxford)
//	* Joachim Klein <klein@tcs.inf.tu-dresden.de> (TU Dresden)
//	
//------------------------------------------------------------------------------
//	
//	This file is part of PRISM.
//	
//	PRISM is free software; you can redistribute it and/or modify
//	it under the terms of the GNU General Public License as published by
//	the Free Software Foundation; either version 2 of the License, or
//	(at your option) any later version.
//	
//	PRISM is distributed in the hope that it will be useful,
//	but WITHOUT ANY WARRANTY; without even the implied warranty of
//	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//	GNU General Public License for more details.
//	
//	You should have received a copy of the GNU General Public License
//	along with PRISM; if not, write to the Free Software Foundation,
//	Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//	
//==============================================================================

package explicit;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import parser.State;
import parser.ast.Expression;
import parser.ast.ExpressionBinaryOp;
import parser.ast.ExpressionLabel;
import parser.ast.ExpressionTemporal;
import parser.ast.ExpressionUnaryOp;
import parser.type.TypeBool;
import parser.type.TypePathBool;
import prism.ModelType;
import prism.PrismComponent;
import prism.PrismException;
import prism.PrismFileLog;
import prism.PrismLangException;
import prism.PrismLog;
import prism.PrismNotSupportedException;
import acceptance.AcceptanceGenRabin;
import acceptance.AcceptanceOmega;
import acceptance.AcceptanceRabin;
import acceptance.AcceptanceType;
import automata.DA;
import automata.LTL2DA;
import common.IterableStateSet;

/**
 * LTL model checking functionality
 */
public class LTLModelChecker extends PrismComponent
{
	/** Make LTL product accessible as a Product */
	public class LTLProduct<M extends Model> extends Product<M>
	{
		private int daSize;
		private int invMap[];
		private AcceptanceOmega acceptance;

		public LTLProduct(M productModel, M originalModel, AcceptanceOmega acceptance, int daSize, int[] invMap)
		{
			super(productModel, originalModel);
			this.daSize = daSize;
			this.invMap = invMap;
			this.acceptance = acceptance;
		}

		@Override
		public int getModelState(int productState)
		{
			return invMap[productState] / daSize;
		}

		@Override
		public int getAutomatonState(int productState)
		{
			return invMap[productState] % daSize;
		}

		public AcceptanceOmega getAcceptance() {
			return acceptance;
		}

		public void setAcceptance(AcceptanceOmega acceptance) {
			this.acceptance = acceptance;
		}
	}

	/**
	 * Create a new LTLModelChecker, inherit basic state from parent (unless null).
	 */
	public LTLModelChecker(PrismComponent parent) throws PrismException
	{
		super(parent);
	}

	/**
	 * Returns {@code true} if expression {@code expr} is a formula that can be handled by
	 * LTLModelChecker for the given ModelType.
	 */
	public static boolean isSupportedLTLFormula(ModelType modelType, Expression expr) throws PrismLangException
	{
		if (!expr.isPathFormula(true)) {
			return false;
		}
		if (Expression.containsTemporalTimeBounds(expr)) {
			if (modelType.continuousTime()) {
				// Only support temporal bounds for discrete time models
				return false;
			}
			
			if (!expr.isSimplePathFormula()) {
				// Only support temporal bounds for simple path formulas
				return false;
			}
		}
		return true;
	}

	/**
	 * Extract maximal state formula from an LTL path formula, model check them (with passed in model checker) and
	 * replace them with ExpressionLabel objects L0, L1, etc. Expression passed in is modified directly, but the result
	 * is also returned. As an optimisation, expressions that results in true/false for all states are converted to an
	 * actual true/false, and duplicate results (or their negations) reuse the same label. BitSets giving the states which
	 * satisfy each label are put into the vector labelDDs, which should be empty when this function is called.
	 */
	public Expression checkMaximalStateFormulas(ProbModelChecker mc, Model model, Expression expr, Vector<BitSet> labelBS) throws PrismException
	{
		// A state formula
		if (expr.getType() instanceof TypeBool) {
			// Model check state formula for all states
			StateValues sv = mc.checkExpression(model, expr, null);
			BitSet bs = sv.getBitSet();
			// Detect special cases (true, false) for optimisation
			if (bs.isEmpty()) {
				return Expression.False();
			}
			if (bs.cardinality() == model.getNumStates()) {
				return Expression.True();
			}
			// See if we already have an identical result
			// (in which case, reuse it)
			int i = labelBS.indexOf(bs);
			if (i != -1) {
				sv.clear();
				return new ExpressionLabel("L" + i);
			}
			// Also, see if we already have the negation of this result
			// (in which case, reuse it)
			BitSet bsNeg = new BitSet(model.getNumStates());
			bsNeg.set(0, model.getNumStates());
			bsNeg.andNot(bs);
			i = labelBS.indexOf(bsNeg);
			if (i != -1) {
				sv.clear();
				return Expression.Not(new ExpressionLabel("L" + i));
			}
			// Otherwise, add result to list, return new label
			labelBS.add(bs);
			return new ExpressionLabel("L" + (labelBS.size() - 1));
		}
		// A path formula (recurse, modify, return)
		else if (expr.getType() instanceof TypePathBool) {
			if (expr instanceof ExpressionBinaryOp) {
				ExpressionBinaryOp exprBinOp = (ExpressionBinaryOp) expr;
				exprBinOp.setOperand1(checkMaximalStateFormulas(mc, model, exprBinOp.getOperand1(), labelBS));
				exprBinOp.setOperand2(checkMaximalStateFormulas(mc, model, exprBinOp.getOperand2(), labelBS));
			} else if (expr instanceof ExpressionUnaryOp) {
				ExpressionUnaryOp exprUnOp = (ExpressionUnaryOp) expr;
				exprUnOp.setOperand(checkMaximalStateFormulas(mc, model, exprUnOp.getOperand(), labelBS));
			} else if (expr instanceof ExpressionTemporal) {
				ExpressionTemporal exprTemp = (ExpressionTemporal) expr;
				if (exprTemp.getOperand1() != null) {
					exprTemp.setOperand1(checkMaximalStateFormulas(mc, model, exprTemp.getOperand1(), labelBS));
				}
				if (exprTemp.getOperand2() != null) {
					exprTemp.setOperand2(checkMaximalStateFormulas(mc, model, exprTemp.getOperand2(), labelBS));
				}
			}
		}
		return expr;
	}

	/**
	 * Construct a deterministic automaton (DA) for an LTL formula, having first extracted maximal state formulas
	 * and model checked them with the passed in model checker. The maximal state formulas are assigned labels
	 * (L0, L1, etc.) which become the atomic propositions in the resulting DA. BitSets giving the states which
	 * satisfy each label are put into the vector {@code labelBS}, which should be empty when this function is called.
	 *
	 * @param mc a ProbModelChecker, used for checking maximal state formulas
	 * @param model the model
	 * @param expr a path expression, i.e. the LTL formula
 	 * @param labelBS empty vector to be filled with BitSets for subformulas 
 	 * @param allowedAcceptance the allowed acceptance types
	 * @return the DA
	 */
	public DA<BitSet,? extends AcceptanceOmega> constructDAForLTLFormula(ProbModelChecker mc, Model model, Expression expr, Vector<BitSet> labelBS, AcceptanceType... allowedAcceptance) throws PrismException
	{
		Expression ltl;
		DA<BitSet,? extends AcceptanceOmega> da;
		long time;

		if (Expression.containsTemporalTimeBounds(expr)) {
			if (model.getModelType().continuousTime()) {
				throw new PrismException("Automaton construction for time-bounded operators not supported for " + model.getModelType()+".");
			}

			if (expr.isSimplePathFormula()) {
				// Convert simple path formula to canonical form,
				// DA is then generated by LTL2RabinLibrary.
				// The conversion to canonical form has to happen here, because once
				// checkMaximalStateFormulas has been called, the formula should not be modified
				// anymore, as converters may expect that the generated labels for maximal state
				// formulas only appear positively
				expr = Expression.convertSimplePathFormulaToCanonicalForm(expr);
			} else {
				throw new PrismNotSupportedException("Time-bounded operators not supported in LTL: " + expr);
			}
		}

		// Model check maximal state formulas
		ltl = checkMaximalStateFormulas(mc, model, expr.deepCopy(), labelBS);

		// Convert LTL formula to deterministic automaton
		mainLog.println("\nBuilding deterministic automaton (for " + ltl + ")...");
		time = System.currentTimeMillis();
		LTL2DA ltl2da = new LTL2DA(this);
		da = ltl2da.convertLTLFormulaToDA(ltl, mc.getConstantValues(), allowedAcceptance);
		mainLog.println(da.getAutomataType()+" has " + da.size() + " states, " + da.getAcceptance().getSizeStatistics() + ".");
		da.checkForCanonicalAPs(labelBS.size());
		time = System.currentTimeMillis() - time;
		mainLog.println("Time for "+da.getAutomataType()+" translation: " + time / 1000.0 + " seconds.");
		// If required, export DA
		if (settings.getExportPropAut()) {
			mainLog.println("Exporting " + da.getAutomataType() + " to file \"" + settings.getExportPropAutFilename() + "\"...");
			PrismLog out = new PrismFileLog(settings.getExportPropAutFilename());
			da.print(out, settings.getExportPropAutType());
			out.close();
		}
		
		return da;
	}
	
	/**
	 * Generate a deterministic automaton for the given LTL formula
	 * and construct the product of this automaton with a Markov chain.
	 *
	 * @param mc a ProbModelChecker, used for checking maximal state formulas
	 * @param model the model
	 * @param expr a path expression
 	 * @param statesOfInterest the set of states for which values should be calculated (null = all states)
 	 * @param allowedAcceptance the allowed acceptance types
	 * @return the product with the DA
	 */
	public LTLProduct<DTMC> constructProductMC(ProbModelChecker mc, DTMC model, Expression expr, BitSet statesOfInterest, AcceptanceType... allowedAcceptance) throws PrismException
	{
		// Convert LTL formula to automaton
		Vector<BitSet> labelBS = new Vector<BitSet>();
		DA<BitSet,? extends AcceptanceOmega> da;
		da = constructDAForLTLFormula(mc, model, expr, labelBS, allowedAcceptance);

		// Build product of model and automaton
		mainLog.println("\nConstructing MC-"+da.getAutomataType()+" product...");
		LTLProduct<DTMC> product = constructProductMC(da, model, labelBS, statesOfInterest);
		mainLog.print("\n" + product.getProductModel().infoStringTable());

		return product;
	}

	/**
	 * Construct the product of a DA and a DTMC.
	 * @param dra The DA
	 * @param dtmc The DTMC
	 * @param labelBS BitSets giving the set of states for each AP in the DA
	 * @param statesOfInterest the set of states for which values should be calculated (null = all states)
	 * @return The product DTMC
	 */
	public LTLProduct<DTMC> constructProductMC(DA<BitSet,? extends AcceptanceOmega> da, DTMC dtmc, Vector<BitSet> labelBS, BitSet statesOfInterest) throws PrismException
	{
		DTMCSimple prodModel = new DTMCSimple();

		int daSize = da.size();
		int numAPs = da.getAPList().size();
		int modelNumStates = dtmc.getNumStates();
		int prodNumStates = modelNumStates * daSize;
		int s_1, s_2, q_1, q_2;
		BitSet s_labels = new BitSet(numAPs);
		List<State> prodStatesList = null, daStatesList = null;

		// Encoding: 
		// each state s' = <s, q> = s * daSize + q
		// s(s') = s' / daSize
		// q(s') = s' % daSize

		LinkedList<Point> queue = new LinkedList<Point>();
		int map[] = new int[prodNumStates];
		Arrays.fill(map, -1);

		if (dtmc.getStatesList() != null) {
			prodStatesList = new ArrayList<State>();
			daStatesList = new ArrayList<State>(da.size());
			for (int i = 0; i < da.size(); i++) {
				daStatesList.add(new State(1).setValue(0, i));
			}
		}

		// We need results for all states of the original model in statesOfInterest
		// We thus explore states of the product starting from these states.
		// These are designated as initial states of the product model
		// (a) to ensure reachability is done for these states; and
		// (b) to later identify the corresponding product state for the original states
		//     of interest
		for (int s_0 : new IterableStateSet(statesOfInterest, dtmc.getNumStates())) {
			// Get BitSet representing APs (labels) satisfied by state s_0
			for (int k = 0; k < numAPs; k++) {
				s_labels.set(k, labelBS.get(Integer.parseInt(da.getAPList().get(k).substring(1))).get(s_0));
			}
			// Find corresponding initial state in DA
			int q_0 = da.getEdgeDestByLabel(da.getStartState(), s_labels);
			if (q_0 < 0) {
				throw new PrismException("The deterministic automaton is not complete (state " + da.getStartState() + ")");
			}
			// Add (initial) state to product
			queue.add(new Point(s_0, q_0));
			prodModel.addState();
			prodModel.addInitialState(prodModel.getNumStates() - 1);
			map[s_0 * daSize + q_0] = prodModel.getNumStates() - 1;
			if (prodStatesList != null) {
				// store DTMC state information for the product state
				prodStatesList.add(new State(daStatesList.get(q_0), dtmc.getStatesList().get(s_0)));
			}
		}

		// Product states
		BitSet visited = new BitSet(prodNumStates);
		while (!queue.isEmpty()) {
			Point p = queue.pop();
			s_1 = p.x;
			q_1 = p.y;
			visited.set(s_1 * daSize + q_1);

			// Go through transitions from state s_1 in original DTMC
			Iterator<Map.Entry<Integer, Double>> iter = dtmc.getTransitionsIterator(s_1);
			while (iter.hasNext()) {
				Map.Entry<Integer, Double> e = iter.next();
				s_2 = e.getKey();
				double prob = e.getValue();
				// Get BitSet representing APs (labels) satisfied by successor state s_2
				for (int k = 0; k < numAPs; k++) {
					s_labels.set(k, labelBS.get(Integer.parseInt(da.getAPList().get(k).substring(1))).get(s_2));
				}
				// Find corresponding successor in DRA
				q_2 = da.getEdgeDestByLabel(q_1, s_labels);
				if (q_2 < 0) {
					throw new PrismException("The deterministic automaton is not complete (state " + q_1 + ")");
				}
				// Add state/transition to model
				if (!visited.get(s_2 * daSize + q_2) && map[s_2 * daSize + q_2] == -1) {
					queue.add(new Point(s_2, q_2));
					prodModel.addState();
					map[s_2 * daSize + q_2] = prodModel.getNumStates() - 1;
					if (prodStatesList != null) {
						// store DTMC state information for the product state
						prodStatesList.add(new State(daStatesList.get(q_2), dtmc.getStatesList().get(s_2)));
					}
				}
				prodModel.setProbability(map[s_1 * daSize + q_1], map[s_2 * daSize + q_2], prob);
			}
		}

		// Build a mapping from state indices to states (s,q), encoded as (s * draSize + q) 
		int invMap[] = new int[prodModel.getNumStates()];
		for (int i = 0; i < map.length; i++) {
			if (map[i] != -1) {
				invMap[map[i]] = i;
			}
		}

		prodModel.findDeadlocks(false);

		if (prodStatesList != null) {
			prodModel.setStatesList(prodStatesList);
		}

		LTLProduct<DTMC> product = new LTLProduct<DTMC>(prodModel, dtmc, null, daSize, invMap);

		// generate acceptance for the product model by lifting
		product.setAcceptance(liftAcceptance(product, da.getAcceptance()));

		// lift the labels
		for (String label : dtmc.getLabels()) {
			BitSet liftedLabel = product.liftFromModel(dtmc.getLabelStates(label));
			prodModel.addLabel(label, liftedLabel);
		}

		return product;
	}
	/**
	 * Generate a deterministic automaton for the given LTL formula
	 * and construct the product of this automaton with an MDP.
	 *
	 * @param mc a ProbModelChecker, used for checking maximal state formulas
	 * @param model the model
	 * @param expr a path expression
	 * @param statesOfInterest the set of states for which values should be calculated (null = all states)
	 * @param allowedAcceptance the allowed acceptance conditions
	 * @return the product with the DA
	 * @throws PrismException
	 */
	public LTLProduct<MDP> constructProductMDP(ProbModelChecker mc, MDP model, Expression expr, BitSet statesOfInterest, AcceptanceType... allowedAcceptance) throws PrismException
	{
		// Convert LTL formula to automaton
		Vector<BitSet> labelBS = new Vector<BitSet>();
		DA<BitSet,? extends AcceptanceOmega> da;
		da = constructDAForLTLFormula(mc, model, expr, labelBS, allowedAcceptance);

		// Build product of model and automaton
		mainLog.println("\nConstructing MDP-"+da.getAutomataType()+" product...");
		LTLProduct<MDP> product = constructProductMDP(da, model, labelBS, statesOfInterest);
		mainLog.print("\n" + product.getProductModel().infoStringTable());

		return product;
	}

	/**
	 * Construct the product of a DA and an MDP.
	 * @param da The DA
	 * @param mdp The MDP
	 * @param labelBS BitSets giving the set of states for each AP in the DA
	 * @param statesOfInterest the set of states for which values should be calculated (null = all states)
	 * @return The product MDP
	 */
	public LTLProduct<MDP> constructProductMDP(DA<BitSet,? extends AcceptanceOmega> da, MDP mdp, Vector<BitSet> labelBS, BitSet statesOfInterest) throws PrismException
	{
		MDPSimple prodModel = new MDPSimple();

		int daSize = da.size();
		int numAPs = da.getAPList().size();
		int modelNumStates = mdp.getNumStates();
		int prodNumStates = modelNumStates * daSize;
		int s_1, s_2, q_1, q_2;
		BitSet s_labels = new BitSet(numAPs);
		List<State> prodStatesList = null, daStatesList = null;


		// Encoding: 
		// each state s' = <s, q> = s * draSize + q
		// s(s') = s' / draSize
		// q(s') = s' % draSize

		LinkedList<Point> queue = new LinkedList<Point>();
		int map[] = new int[prodNumStates];
		Arrays.fill(map, -1);

		if (mdp.getStatesList() != null) {
			prodStatesList = new ArrayList<State>();
			daStatesList = new ArrayList<State>(da.size());
			for (int i = 0; i < da.size(); i++) {
				daStatesList.add(new State(1).setValue(0, i));
			}
		}

		// We need results for all states of the original model in statesOfInterest
		// We thus explore states of the product starting from these states.
		// These are designated as initial states of the product model
		// (a) to ensure reachability is done for these states; and
		// (b) to later identify the corresponding product state for the original states
		//     of interest
		for (int s_0 : new IterableStateSet(statesOfInterest, mdp.getNumStates())) {
			// Get BitSet representing APs (labels) satisfied by state s_0
			for (int k = 0; k < numAPs; k++) {
				s_labels.set(k, labelBS.get(Integer.parseInt(da.getAPList().get(k).substring(1))).get(s_0));
			}
			// Find corresponding initial state in DRA
			int q_0 = da.getEdgeDestByLabel(da.getStartState(), s_labels);
			if (q_0 < 0) {
				throw new PrismException("The deterministic automaton is not complete (state " + da.getStartState() + ")");
			}
			// Add (initial) state to product
			queue.add(new Point(s_0, q_0));
			prodModel.addState();
			prodModel.addInitialState(prodModel.getNumStates() - 1);
			map[s_0 * daSize + q_0] = prodModel.getNumStates() - 1;
			if (prodStatesList != null) {
				// store MDP state information for the product state
				prodStatesList.add(new State(daStatesList.get(q_0), mdp.getStatesList().get(s_0)));
			}
		}

		// Product states
		BitSet visited = new BitSet(prodNumStates);
		while (!queue.isEmpty()) {
			Point p = queue.pop();
			s_1 = p.x;
			q_1 = p.y;
			visited.set(s_1 * daSize + q_1);

			// Go through transitions from state s_1 in original MDP
			int numChoices = mdp.getNumChoices(s_1);
			for (int j = 0; j < numChoices; j++) {
				Distribution prodDistr = new Distribution();
				Iterator<Map.Entry<Integer, Double>> iter = mdp.getTransitionsIterator(s_1, j);
				while (iter.hasNext()) {
					Map.Entry<Integer, Double> e = iter.next();
					s_2 = e.getKey();
					double prob = e.getValue();
					// Get BitSet representing APs (labels) satisfied by successor state s_2
					for (int k = 0; k < numAPs; k++) {
						s_labels.set(k, labelBS.get(Integer.parseInt(da.getAPList().get(k).substring(1))).get(s_2));
					}
					// Find corresponding successor in DRA
					q_2 = da.getEdgeDestByLabel(q_1, s_labels);
					if (q_2 < 0) {
						throw new PrismException("The deterministic automaton is not complete (state " + q_1 + ")");
					}
					// Add state/transition to model
					if (!visited.get(s_2 * daSize + q_2) && map[s_2 * daSize + q_2] == -1) {
						queue.add(new Point(s_2, q_2));
						prodModel.addState();
						map[s_2 * daSize + q_2] = prodModel.getNumStates() - 1;
						if (prodStatesList != null) {
							// store MDP state information for the product state
							prodStatesList.add(new State(daStatesList.get(q_2), mdp.getStatesList().get(s_2)));
						}
					}
					prodDistr.set(map[s_2 * daSize + q_2], prob);
				}
				prodModel.addActionLabelledChoice(map[s_1 * daSize + q_1], prodDistr, mdp.getAction(s_1, j));
			}
		}

		// Build a mapping from state indices to states (s,q), encoded as (s * draSize + q) 
		int invMap[] = new int[prodModel.getNumStates()];
		for (int i = 0; i < map.length; i++) {
			if (map[i] != -1) {
				invMap[map[i]] = i;
			}
		}

		prodModel.findDeadlocks(false);

		if (prodStatesList != null) {
			prodModel.setStatesList(prodStatesList);
		}

		LTLProduct<MDP> product = new LTLProduct<MDP>(prodModel, mdp, null, daSize, invMap);

		// generate acceptance for the product model by lifting
		product.setAcceptance(liftAcceptance(product, da.getAcceptance()));

		// lift the labels
		for (String label : mdp.getLabels()) {
			BitSet liftedLabel = product.liftFromModel(mdp.getLabelStates(label));
			prodModel.addLabel(label, liftedLabel);
		}

		return product;
	}

	/**
	 * Find the set of states that belong to accepting BSCCs in a model wrt an acceptance condition.
	 * @param model The model
	 * @param acceptance The acceptance condition
	 */
	public BitSet findAcceptingBSCCs(Model model, AcceptanceOmega acceptance) throws PrismException
	{
		// Compute bottom strongly connected components (BSCCs)
		SCCComputer sccComputer = SCCComputer.createSCCComputer(this, model);
		sccComputer.computeBSCCs();
		List<BitSet> bsccs = sccComputer.getBSCCs();

		BitSet result = new BitSet();

		for (BitSet bscc : bsccs) {
			if (acceptance.isBSCCAccepting(bscc)) {
				// this BSCC is accepting
				result.or(bscc);
			}
		}

		return result;
	}

	/**
	 * Compute the set of states in end components of the model that are accepting
	 * with regard to the acceptance condition.
	 * @param model the model
	 * @param acceptance the acceptance condition
	 * @return BitSet with the set of states that are accepting
	 */
	public BitSet findAcceptingECStates(NondetModel model, AcceptanceOmega acceptance) throws PrismException
	{
		if (acceptance instanceof AcceptanceRabin) {
			return findAcceptingECStatesForRabin(model, (AcceptanceRabin) acceptance);
		} else if (acceptance instanceof AcceptanceGenRabin) {
			return findAcceptingECStatesForGeneralizedRabin(model, (AcceptanceGenRabin) acceptance);
		}
		throw new PrismNotSupportedException("Computing end components for acceptance type '"+acceptance.getTypeName()+"' currently not supported (explicit engine).");
	}

	/**
	 * Find the set of states in accepting end components (ECs) in a nondeterministic model wrt a Rabin acceptance condition.
	 * @param model The model
	 * @param acceptance The acceptance condition
	 */
	public BitSet findAcceptingECStatesForRabin(NondetModel model, AcceptanceRabin acceptance) throws PrismException
	{
		BitSet allAcceptingStates = new BitSet();
		int numStates = model.getNumStates();
		
		// Go through the DRA acceptance pairs (L_i, K_i) 
		for (int i = 0; i < acceptance.size(); i++) {
			// Find model states *not* satisfying L_i
			BitSet bitsetLi = acceptance.get(i).getL();
			BitSet statesLi_not = new BitSet();
			for (int s = 0; s < numStates; s++) {
				if (!bitsetLi.get(s)) {
					statesLi_not.set(s);
				}
			}
			// Skip pairs with empty !L_i
			if (statesLi_not.cardinality() == 0)
				continue;
			// Compute accepting maximum end components (MECs) in !L_i
			ECComputer ecComputer = ECComputer.createECComputer(this, model);
			ecComputer.computeMECStates(statesLi_not, acceptance.get(i).getK());
			List<BitSet> mecs = ecComputer.getMECStates();
			// Union MEC states
			for (BitSet mec : mecs) {
				allAcceptingStates.or(mec);
			}
		}

		return allAcceptingStates;
	}

	/**
	 * Find the set of states in accepting end components (ECs) in a nondeterministic model wrt a Generalized Rabin acceptance condition.
	 * @param model The model
	 * @param acceptance The acceptance condition
	 */
	public BitSet findAcceptingECStatesForGeneralizedRabin(NondetModel model, AcceptanceGenRabin acceptance) throws PrismException
	{
		BitSet allAcceptingStates = new BitSet();
		int numStates = model.getNumStates();
		
		// Go through the GR acceptance pairs (L_i, K_i_1, ..., K_i_n) 
		for (int i = 0; i < acceptance.size(); i++) {
			
			// Find model states *not* satisfying L_i
			BitSet bitsetLi = acceptance.get(i).getL();
			BitSet statesLi_not = new BitSet();
			for (int s = 0; s < numStates; s++) {
				if (!bitsetLi.get(s)) {
					statesLi_not.set(s);
				}
			}
			// Skip pairs with empty !L_i
			if (statesLi_not.cardinality() == 0)
				continue;
			// Compute maximum end components (MECs) in !L_i
			ECComputer ecComputer = ECComputer.createECComputer(this, model);
			ecComputer.computeMECStates(statesLi_not);
			List<BitSet> mecs = ecComputer.getMECStates();
			// Check which MECs contain a state from each K_i_j
			int n = acceptance.get(i).getNumK();
			for (BitSet mec : mecs) {
				boolean allj = true;
				for (int j = 0; j < n; j++) {
					if (!mec.intersects(acceptance.get(i).getK(j))) {
						allj = false;
						break;
					}
				}
				if (allj) {
					allAcceptingStates.or(mec);
				}
			}
		}

		return allAcceptingStates;
	}

	/** Lift the acceptance condition from the automaton to the product states. */
	private AcceptanceOmega liftAcceptance(final LTLProduct<?> product, AcceptanceOmega acceptance)
	{
		// make a copy of the acceptance condition
		AcceptanceOmega lifted = acceptance.clone();

		// lift state sets
		lifted.lift(new AcceptanceOmega.LiftBitSet() {
			@Override
			public BitSet lift(BitSet states)
			{
				return product.liftFromAutomaton(states);
			}
		});

		return lifted;
	}

}
