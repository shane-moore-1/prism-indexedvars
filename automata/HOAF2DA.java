//==============================================================================
//	
//	Copyright (c) 2014-
//	Authors:
//	* Joachim Klein <klein@tcs.inf.tu-dresden.de> (TU Dresden)
//	* David Mueller <david.mueller@tcs.inf.tu-dresden.de> (TU Dresden)
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


package automata;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Set;

import prism.PrismException;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerException;
import jhoafparser.util.ImplicitEdgeHelper;
import jltl2ba.APElement;
import jltl2ba.APSet;
import jltl2dstar.APMonom;
import jltl2dstar.APMonom2APElements;
import acceptance.AcceptanceGenRabin;
import acceptance.AcceptanceGenRabin.GenRabinPair;
import acceptance.AcceptanceGeneric;
import acceptance.AcceptanceOmega;
import acceptance.AcceptanceRabin;
import acceptance.AcceptanceRabin.RabinPair;

/**
 * A HOAConsumer for jhoafparser that constructs a prism.DA from the parsed automaton.
 * <br>
 * The automaton has to be deterministic and complete, with state-based acceptance and
 * labels (explicit/implicit) on the edges.
 * <br>
 * If the automaton has transition-based acceptance, {@code TransitionBasedAcceptanceException}
 * is thrown.
 * <br>
 * There are (currently) more restrictions on the automaton:
 * <ul>
 * <li>The Start and States headers have to be present</li>
 * <li>At least one state in the automaton.
 * <li>All explicit edge labels have to be in disjunctive normal form (disjunction of conjunctive clauses)</li>
 * <li>At most 30 atomic propositions</li>
 * </ul>
 */
public class HOAF2DA implements HOAConsumer {


	/** An exception that is thrown to indicate that the automaton had transition based acceptance. */
	@SuppressWarnings("serial")
	public class TransitionBasedAcceptanceException extends HOAConsumerException {
		public TransitionBasedAcceptanceException(String e) {super(e);}
	}

	/** The resulting deterministic automaton */
	private DA<BitSet, ? extends AcceptanceOmega> da;
	/** The set of atomic propositions of the automaton (in APSet form) */
	private APSet aps = new APSet();

	/** Size, i.e. number of states */
	private int size;
	/** Do we know the number of states? Is provided by the optional HOA States-header */
	private boolean knowSize = false;

	/** Start state (index) */
	private int startState;
	/** Do we know the start state? Is provided by the HOA Start-header */
	private boolean knowStartState = false;

	/** The acceptance condition */
	private BooleanExpression<AtomAcceptance> accExpr = null;
	/** The condition name from the acc-name header (optional) */
	private String accName;
	/** The extra information from the acc-name header (optional) */
	private List<Object> extraInfo;

	/** For each acceptance set in the HOA automaton, the set of states that are included in that set */
	private List<BitSet> acceptanceSets = null;
	/** The set of acceptance set indizes where state membership has to be inverted */
	private Set<Integer> negateAcceptanceSetMembership = null;
	/** The list of atomic propositions (in List form) */
	private List<String> apList;

	/** The helper for handling implicit edges */
	private ImplicitEdgeHelper implicitEdgeHelper = null;

	/**
	 * The expected number of edges per state. As the automaton has to be complete,
	 * this is 2^|AP|.
	 */
	private long expectedNumberOfEdgesPerState;

	/** Clear the various state information */
	public void clear() {
		aps = new APSet();

		implicitEdgeHelper = null;

		size = 0;
		knowSize = false;

		startState = 0;
		knowStartState = false;

		accExpr = null;
		accName = null;
		extraInfo = null;

		acceptanceSets = null;
		negateAcceptanceSetMembership = null;
		apList = null;
	}

	/** Constructor */
	public HOAF2DA() {
	}

	@Override
	public boolean parserResolvesAliases() {
		return true;
	}

	@Override
	public void notifyHeaderStart(String version) throws HOAConsumerException {
		// NOP
	}

	@Override
	public void setNumberOfStates(int numberOfStates)
			throws HOAConsumerException {
		size = numberOfStates;
		knowSize = true;
		if (numberOfStates == 0) {
			throw new HOAConsumerException("Automaton with zero states, need at least one state");
		}
	}

	@Override
	public void addStartStates(List<Integer> stateConjunction)
			throws HOAConsumerException {
		if(stateConjunction.size() > 1 || knowStartState) {
			throw new HOAConsumerException("Not a deterministic automaton: More then one Start state");
		}
		startState = stateConjunction.get(0).intValue();
		knowStartState = true;
	}

	@Override
	public void addAlias(String name, BooleanExpression<AtomLabel> labelExpr)
			throws HOAConsumerException {
		// NOP, aliases are already resolved
	}

	@Override
	public void setAPs(List<String> aps) throws HOAConsumerException {
		if (aps.size() > 30) {
			throw new HOAConsumerException("Automaton has "+aps.size()+" atomic propositions, at most 30 are supported");
		}

		apList = aps;
		expectedNumberOfEdgesPerState = 1L << apList.size();

		for (String ap : aps) {
			this.aps.addAP(ap);
		}
	}

	@Override
	public void setAcceptanceCondition(int numberOfSets,
			BooleanExpression<AtomAcceptance> accExpr)
			throws HOAConsumerException {
		this.accExpr = accExpr;
	}

	@Override
	public void provideAcceptanceName(String name, List<Object> extraInfo)
			throws HOAConsumerException {
		accName = name;
		this.extraInfo = extraInfo;
	}

	@Override
	public void setName(String name) throws HOAConsumerException {
		// NOP
	}

	@Override
	public void setTool(String name, String version) throws HOAConsumerException {
		// NOP
	}

	@Override
	public void addProperties(List<String> properties)
			throws HOAConsumerException {
		if(!properties.contains("deterministic")) {
			// we don't know yet whether the automaton is actually deterministic...
		}
		if(properties.contains("univ-branch")) {
			throw new HOAConsumerException("A HOAF with universal branching is not deterministic");
		}
		
		if(properties.contains("state-labels")) {
			throw new HOAConsumerException("Can't handle state labelling");
		}
	}

	@Override
	public void addMiscHeader(String name, List<Object> content)
			throws HOAConsumerException {
		if (name.substring(0,1).toUpperCase().equals(name.substring(0,1))) {
			throw new HOAConsumerException("Unknown header "+name+" potentially containing semantic information, can not handle");
		}
	}

	@Override
	public void notifyBodyStart() throws HOAConsumerException {
		if (!knowSize) {
			throw new HOAConsumerException("Can currently only parse automata where the number of states is specified in the header");
		}
		if (!knowStartState) {
			throw new HOAConsumerException("Not a deterministic automaton: No initial state specified (Start header)");
		}
		if (startState >= size) {
			throw new HOAConsumerException("Initial state "+startState+" is out of range");
		}

		da = new DA<BitSet,AcceptanceGeneric>(size);
		da.setStartState(startState);

		if (apList == null) {
			// no call to setAPs
			apList = new ArrayList<String>(0);
		}
		da.setAPList(apList);
		implicitEdgeHelper = new ImplicitEdgeHelper(apList.size());

		DA.switchAcceptance(da, prepareAcceptance());
	}

	/**
	 * Prepare an acceptance condition for the parsed automaton.
	 * Called in notifyBodyStart()
	 **/
	private AcceptanceOmega prepareAcceptance() throws HOAConsumerException
	{
		if (accName != null) {
			if (accName.equals("Rabin")) {
				return prepareAcceptanceRabin();
			} else if (accName.equals("generalized-Rabin")) {
				return prepareAcceptanceGenRabin();
			}
		}

		acceptanceSets = new ArrayList<BitSet>();
		return prepareAcceptanceGeneric(accExpr);
	}

	/**
	 * Prepare a generic acceptance condition for the parsed automaton.
	 **/
	private AcceptanceGeneric prepareAcceptanceGeneric(BooleanExpression<AtomAcceptance> expr) throws HOAConsumerException
	{
		switch (expr.getType()) {
		case EXP_TRUE:
			return new AcceptanceGeneric(true);
		case EXP_FALSE:
			return new AcceptanceGeneric(false);
		case EXP_AND:
			return new AcceptanceGeneric(AcceptanceGeneric.ElementType.AND,
                    prepareAcceptanceGeneric(expr.getLeft()),
                    prepareAcceptanceGeneric(expr.getRight()));
		case EXP_OR:
			return new AcceptanceGeneric(AcceptanceGeneric.ElementType.OR,
                    prepareAcceptanceGeneric(expr.getLeft()),
                    prepareAcceptanceGeneric(expr.getRight()));
		case EXP_NOT:
			throw new HOAConsumerException("Boolean negation not allowed in acceptance expression");
		case EXP_ATOM: {
			int index = expr.getAtom().getAcceptanceSet();
			while (index >= acceptanceSets.size()) {
				// ensure that the acceptanceSets array is large enough
				acceptanceSets.add(null);
			}
			if (acceptanceSets.get(index) == null) {
				// this acceptance set index has not been seen yet, create BitSet
				acceptanceSets.set(index, new BitSet());
			}
			BitSet acceptanceSet = acceptanceSets.get(index);
			switch (expr.getAtom().getType()) {
			case TEMPORAL_FIN:
				if (expr.getAtom().isNegated()) {
					return new AcceptanceGeneric(AcceptanceGeneric.ElementType.FIN_NOT, acceptanceSet);
				} else {
					return new AcceptanceGeneric(AcceptanceGeneric.ElementType.FIN, acceptanceSet);
				}
			case TEMPORAL_INF:
				if (expr.getAtom().isNegated()) {
					return new AcceptanceGeneric(AcceptanceGeneric.ElementType.INF_NOT, acceptanceSet);
				} else {
					return new AcceptanceGeneric(AcceptanceGeneric.ElementType.INF, acceptanceSet);
				}
			}
		}
		}

		throw new UnsupportedOperationException("Unknown operator in acceptance condition: "+expr);
	}

	/**
	 * Prepare a Rabin acceptance condition from the acc-name header.
	 */
	private AcceptanceRabin prepareAcceptanceRabin() throws HOAConsumerException
	{
		if (extraInfo.size() != 1 ||
		    !(extraInfo.get(0) instanceof Integer)) {
			throw new HOAConsumerException("Invalid acc-name: Rabin header");
		}

		int numberOfPairs = (Integer)extraInfo.get(0);
		AcceptanceRabin acceptanceRabin = new AcceptanceRabin();
		acceptanceSets = new ArrayList<BitSet>(numberOfPairs*2);
		for (int i = 0; i< numberOfPairs; i++) {
			BitSet L = new BitSet();
			BitSet K = new BitSet();

			acceptanceSets.add(L);   // 2*i   = Fin(L) = F G !L
			acceptanceSets.add(K);   // 2*i+1 = Inf(K) = G F  K

			acceptanceRabin.add(new RabinPair(L,K));
		}

		return acceptanceRabin;
	}

	/**
	 * Prepare a Generalized Rabin acceptance condition from the acc-name header.
	 */
	private AcceptanceGenRabin prepareAcceptanceGenRabin() throws HOAConsumerException
	{
		if (extraInfo.size() < 1 ||
		    !(extraInfo.get(0) instanceof Integer)) {
			throw new HOAConsumerException("Invalid acc-name: generalized-Rabin header");
		}

		int numberOfPairs = (Integer)extraInfo.get(0);
		if (extraInfo.size() != numberOfPairs + 1) {
			throw new HOAConsumerException("Invalid acc-name: generalized-Rabin header");
		}
		int numberOfKs[] = new int[numberOfPairs];
		for (int i = 0; i < numberOfPairs; i++) {
			if (!(extraInfo.get(i + 1) instanceof Integer)) {
				throw new HOAConsumerException("Invalid acc-name: generalized-Rabin header");
			}
			numberOfKs[i] = (Integer) extraInfo.get(i + 1);
		}
		
		AcceptanceGenRabin acceptanceGenRabin = new AcceptanceGenRabin();
		acceptanceSets = new ArrayList<BitSet>(numberOfPairs*2);
		for (int i = 0; i< numberOfPairs; i++) {
			BitSet L = new BitSet();
			acceptanceSets.add(L);   // Fin(L) = F G !L
			ArrayList<BitSet> K_list = new ArrayList<BitSet>();
			for (int j = 0; j < numberOfKs[i]; j++) {
				BitSet K_j = new BitSet();
				K_list.add(K_j);
				acceptanceSets.add(K_j);   // Inf(K_j) = G F  K_j
			}
			acceptanceGenRabin.add(new GenRabinPair(L, K_list));
		}

		return acceptanceGenRabin;
	}

	@Override
	public void addState(int id, String info,
			BooleanExpression<AtomLabel> labelExpr, List<Integer> accSignature)
			throws HOAConsumerException {
		implicitEdgeHelper.startOfState(id);

		if(labelExpr != null) {
			throw new HOAConsumerException("State "+id+" has a state label, currently only supports labels on transitions");
		}
		
		if (id >= size) {
			throw new HOAConsumerException("Illegal state index "+id+", out of range");
		}

		if (accSignature != null) {
			for (int index : accSignature) {
				if (index >= acceptanceSets.size()) {
					// acceptance set index not used in acceptance condition, ignore
				}
				BitSet accSet = acceptanceSets.get(index);
				if (accSet == null) {
					// acceptance set index not used in acceptance condition, ignore
				} else {
					accSet.set(id);
				}
			}
		}
	}

	@Override
	public void addEdgeImplicit(int stateId, List<Integer> conjSuccessors,
			List<Integer> accSignature) throws HOAConsumerException {
		if (conjSuccessors.size() != 1) {
			throw new HOAConsumerException("Not a DA, state "+stateId+" has transition with conjunctive target");
		}

		if (accSignature != null) {
			throw new TransitionBasedAcceptanceException("DA has transition-based acceptance (state "+stateId+", currently only state-labeled acceptance is supported");
		}

		int to = conjSuccessors.get(0);

		BitSet edge = new BitSet();
		long tmp = implicitEdgeHelper.nextImplicitEdge();
		int index = 0;
		while (tmp != 0) {
			if (tmp % 2 == 1) {
				edge.set(index);
			}
			tmp = tmp >> 1L;
			index++;
		}
		da.addEdge(stateId, edge, to);
	}
	
	/**
	 * Returns a list of APMonoms for the expression. The expression currently has to be in
	 * disjunctive normal form. Returns one APMonom for each clause of the DNF.
	 */
	private List<APMonom> labelExpressionToAPMonom(BooleanExpression<AtomLabel> expr) throws HOAConsumerException {
		List<APMonom> result = new ArrayList<APMonom>();
		
		switch (expr.getType()) {
		case EXP_AND:
		case EXP_ATOM:
		case EXP_NOT: {
			APMonom monom = new APMonom();
			labelExpressionToAPMonom(expr, monom);
			result.add(monom);
			return result;
		}
		case EXP_TRUE:
			result.add(new APMonom(true));
			return result;
		case EXP_FALSE:
			result.add(new APMonom(false));
		case EXP_OR:
			result.addAll(labelExpressionToAPMonom(expr.getLeft()));
			result.addAll(labelExpressionToAPMonom(expr.getRight()));
			return result;
		}
		throw new UnsupportedOperationException("Unsupported operator in label expression: "+expr);
	}

	
	/**
	 * Returns a single APMonom for a single clause of the overall DNF formula.
	 * Modifies APMonom result such that in the end it is correct.
	 */
	private void labelExpressionToAPMonom(BooleanExpression<AtomLabel> expr, APMonom result) throws HOAConsumerException {
		try {
			switch (expr.getType()) {
			case EXP_TRUE:
			case EXP_FALSE:
			case EXP_OR:
				throw new HOAConsumerException("Complex transition labels are not yet supported, only disjunctive normal form: "+expr);

			case EXP_AND:
				labelExpressionToAPMonom(expr.getLeft(), result);
				labelExpressionToAPMonom(expr.getRight(), result);
				return;
			case EXP_ATOM: {
				int apIndex = expr.getAtom().getAPIndex();
				if (result.isSet(apIndex) && result.getValue(apIndex)!=true) {
					throw new HOAConsumerException("Complex transition labels are not yet supported, transition label evaluates to false");
				}
				result.setValue(apIndex, true);
				return;
			}
			case EXP_NOT: {
				if (!expr.getLeft().isAtom()) {
					throw new HOAConsumerException("Complex transition labels are not yet supported, only conjunction of (negated) labels");
				}
				int apIndex = expr.getLeft().getAtom().getAPIndex();
				if (result.isSet(apIndex) && result.getValue(apIndex)!=false) {
					throw new HOAConsumerException("Complex transition labels are not yet supported, transition label evaluates to false");
				}
				result.setValue(apIndex, false);
				return;
			}
			}
		} catch (PrismException e) {
			throw new HOAConsumerException("While parsing, APMonom exception: "+e.getMessage());
		}
	}
	
	@Override
	public void addEdgeWithLabel(int stateId,
			BooleanExpression<AtomLabel> labelExpr,
			List<Integer> conjSuccessors, List<Integer> accSignature)
			throws HOAConsumerException {

		if (conjSuccessors.size() != 1) {
			throw new HOAConsumerException("Not a DA, state "+stateId+" has transition with conjunctive target");
		}

		if (accSignature != null) {
			throw new TransitionBasedAcceptanceException("DA has transition-based acceptance (state "+stateId+", currently only state-labeled acceptance is supported");
		}

		if (labelExpr == null) {
			throw new HOAConsumerException("Missing label on transition");
		}

		int to = conjSuccessors.get(0);

		for (APMonom monom : labelExpressionToAPMonom(labelExpr)) {
			APMonom2APElements it = new APMonom2APElements(aps, monom);
			while(it.hasNext()) {
				APElement el = it.next();
				if (da.hasEdge(stateId, el)) {
					throw new HOAConsumerException("Not a deterministic automaton, non-determinism detected (state "+stateId+")");
				}
				da.addEdge(stateId, el, to);
			}
		}
	}

	@Override
	public void notifyEndOfState(int stateId) throws HOAConsumerException
	{
		implicitEdgeHelper.endOfState();

		if (da.getNumEdges(stateId) != expectedNumberOfEdgesPerState) {
			throw new HOAConsumerException("State "+ stateId +" has " + da.getNumEdges(stateId)
			                               + " transitions, should have " + expectedNumberOfEdgesPerState
			                               + " (automaton is required to be complete and deterministic)");
		}
	}

	@Override
	public void notifyEnd() throws HOAConsumerException {
		// flip acceptance sets that need negating
		if (negateAcceptanceSetMembership != null) {
			for (int index : negateAcceptanceSetMembership) {
				acceptanceSets.get(index).flip(0, size);
			}
		}

		clear();
	}

	@Override
	public void notifyAbort() {
		clear();
		
	}
	
	public DA<BitSet,? extends AcceptanceOmega> getDA() {
		return da;
	}

	@Override
	public void notifyWarning(String warning) throws HOAConsumerException
	{
		// warnings are fatal
		throw new HOAConsumerException(warning);
	}

}
