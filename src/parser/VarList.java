//==============================================================================
//	
//	Copyright (c) 2002-
//	Authors:
//	* Dave Parker <david.parker@comlab.ox.ac.uk> (University of Oxford, formerly University of Birmingham)
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

package parser;		// SHANE believes this should move into the simulator package, as it is a "runtime" / simulation-time thing (when the model is "instantiated")

import java.util.*;

import prism.*;
import parser.ast.*;
import parser.type.*;

/**
 * Class to store information about the set of variables in a PRISM model.
 * Assumes that any constants in the model have been given fixed values.
 * Thus, initial/min/max values for all variables are known.
 * VarList also takes care of how each variable will be encoded to an integer
 * (e.g. for (MT)BDD representation).
 */
public class VarList
{
private static boolean DEBUG_ADD_VAR = true;			// Whether to report the processing of variables (Mainly in the Constructor)

	// List of variables
	private List<Var> vars;
	// Mapping from names to indices
	private Map<String, Integer> nameMap;
	// Total number of bits needed  to encode
	private int totalNumBits;

// THIS IDEA WAS NOT WORKING: Because even if I alter it here, then all the AST elements would be inconsistent in terms of indexes of identifiers.
// IT IS NOT NEEDED NOW THAT I HAVE ALTERED THE Constructor of this class.
/*	public void reorderVariables(int[] newOrdering)
	{
		List<Var> newlyOrdered;
		Map<String, Integer> newNameMap;

		if ((newOrdering != null) && (newOrdering.length == vars.size()))
		{
System.out.println("[ShaneNote - REORDER_VARS will reorder the variables in the VarList...]");
			newlyOrdered = new ArrayList<Var>();
			Var nextVar;
			for (int i = 0; i < newOrdering.length; i++) {
				nextVar = vars.get(newOrdering[i]);
System.out.println(nextVar.decl.getName() + " being placed in position " + i + " for varList.vars");
				newlyOrdered.add(nextVar);
			}
			vars = newlyOrdered;		// Now we replace the old ordering with the newly constructed ordering

			// Recompute name map - COPIED FROM the version of addVar which has an extra parameter for position.
			int j, n;
			n = getNumVars();
			newNameMap = new HashMap<String, Integer>(n);
			for (j = 0; j < n; j++) {
				newNameMap.put(getName(j), j);
			}

			nameMap = newNameMap;		// Activate the new name map
System.out.println("[ShaneNote - REORDER_VARS completed]");
		}
else System.out.println("[ShaneError - REORDER_VARS given incompatible information]");
	}
*/

	/**
	 * Construct empty variable list.
	 */
	public VarList()
	{
		vars = new ArrayList<Var>();
		nameMap = new HashMap<String, Integer>();
		totalNumBits = 0;
	}

	/**
	 * Construct variable list for a ModulesFile.
	 */
	public VarList(ModulesFile modulesFile) throws PrismLangException
	{
		this();

		int i, j, n, n2;
		int round, moduleID;
		parser.ast.Module module;
		Declaration decl;

/* ORIGINAL CODE:
		// First add all globals to the list
		n = modulesFile.getNumGlobals();
		for (i = 0; i < n; i++) {
			decl = modulesFile.getGlobal(i);
			addVar(decl, -1, modulesFile.getConstantValues());
		}

		// Then add all module variables to the list
		n = modulesFile.getNumModules();
		for (i = 0; i < n; i++) {
			module = modulesFile.getModule(i);
			n2 = module.getNumDeclarations();
			for (j = 0; j < n2; j++) {
				decl = module.getDeclaration(j);
				addVar(decl, i, modulesFile.getConstantValues());
			}
		}
*/

// SHANE VERSION, which takes into consideration the deferral specifications.

		ArrayList<Declaration> decls = new ArrayList<Declaration>();
		ArrayList<Integer> moduleIDs = new ArrayList<Integer>();

		// First extract all globals, add to the interim list
		n = modulesFile.getNumGlobals();
		for (i = 0; i < n; i++) {
			decls.add(modulesFile.getGlobal(i));
			moduleIDs.add(new Integer(-1));
		}

		// Then extract all module variables to the interim list
		n = modulesFile.getNumModules();
		for (i = 0; i < n; i++) {
			module = modulesFile.getModule(i);
			n2 = module.getNumDeclarations();
			for (j = 0; j < n2; j++) {
				decls.add(module.getDeclaration(j));
				moduleIDs.add(new Integer(i));
			}
		}

		// Now to go through the interim list, for multiple rounds of deferral, and in each round, when considering a decl, if that
		// decl's round is the current round, we will call the addVar method, to put it into the VarList in the correct order 
		// according to deferral specification:

if (prism.Modules2MTBDD.DISABLE_VAR_DEFERRAL) {
		n = decls.size();		// Total number of variables in the interim list.
		for (i = 0; i < n; i++) {		// For each variable in the interim list
			decl = decls.get(i);			// Get next candidate declaration
			moduleID = moduleIDs.get(i);		// Find out the module number which that declaration belongs to
			addVar(decl, moduleID, modulesFile.getConstantValues());
		}
}
else {
		n = decls.size();		// Total number of variables in the interim list.
		for (round = 0; round <= Declaration.MAX_DEFERRAL_ROUND; round++) {
if (DEBUG_ADD_VAR) System.out.println("Doing ROUND " + round + " in VarList constructor");
			for (i = 0; i < n; i++) {		// For each variable in the interim list
				decl = decls.get(i);			// Get next candidate declaration
				if (decl.getDeferCreateDDRound() == round)	// The current round is the round for that declaration to be created.
				{
if (DEBUG_ADD_VAR) System.out.println(decl.getName() + " is for this round");
					moduleID = moduleIDs.get(i);		// Find out the module number which that declaration belongs to
					addVar(decl, moduleID, modulesFile.getConstantValues());
				}
			}
		}
}
	}

	/**
	 * Add a new variable to the end of the VarList.
	 * However, if the declaration's type is DeclTypeIndexedSet, it will do nothing.
	 * @param decl Declaration defining the variable
	 * @param module Index of module containing variable
	 * @param constantValues Values of constants needed to evaluate low/high/etc.
	 */
	public void addVar(Declaration decl, int module, Values constantValues) throws PrismLangException
	{
		Var var = createVar(decl, module, constantValues);
		if (var != null) {
			vars.add(var);
			totalNumBits += getRangeLogTwo(vars.size() - 1);
System.out.println("ADDVAR: Adding variable " +decl.getName() + " to VarList; totalNumBits is now " + totalNumBits + "; putting into Map: <"+decl.getName()+","+(vars.size()-1)+">");

			nameMap.put(decl.getName(), vars.size() - 1);
		}
else System.out.println("in parser.Values::addVar(3 Args): var is null *******"); 
		
	}

	/**
	 * Add a new variable at position i in the VarList.
	 * However, if the declaration's type is DeclTypeIndexedSet, it will do nothing.
	 * @param decl Declaration defining the variable
	 * @param module Index of module containing variable
	 * @param constantValues Values of constants needed to evaluate low/high/etc.
	 */
	private void addVar(int i, Declaration decl, int module, Values constantValues) throws PrismLangException
	{
		Var var = createVar(decl, module, constantValues);
		if (var != null) {
			vars.add(i, var);
			totalNumBits += getRangeLogTwo(i);
			// Recompute name map
			int j, n;
			n = getNumVars();
			nameMap = new HashMap<String, Integer>(n);
			for (j = 0; j < n; j++) {
				nameMap.put(getName(j), j);
			}
		}
else System.out.println("in parser.Values::addVar(4 Args): var is null *******"); 
	}

	/**
	 * Create a new variable object to the store in the list.
	 * @param decl Declaration defining the variable
	 * @param module Index of module containing variable
	 * @param constantValues Values of constants needed to evaluate low/high/etc.
	 * @return a Var which represents the variable at "run-time", i.e. during simulation. The type is an inner class.
	 */
	private Var createVar(Declaration decl, int module, Values constantValues) throws PrismLangException
	{
		Var var;					// type is defined at bottom of this code file.
		int low, high, start;
		DeclarationType declType;

		// Create new Var object
		var = new Var();

		// Store name/type/module
		var.decl = decl;
		var.module = module;

		declType = decl.getDeclType();
		// Variable is a bounded integer
		if (declType instanceof DeclarationInt) {
			DeclarationInt intdecl = (DeclarationInt) declType;
			low = intdecl.getLow().evaluateInt(constantValues);
			high = intdecl.getHigh().evaluateInt(constantValues);
			start = decl.getStartOrDefault().evaluateInt(constantValues);
			// Check range is valid
			if (high - low <= 0) {
				String s = "Invalid range (" + low + "-" + high + ") for variable \"" + decl.getName() + "\"";
				throw new PrismLangException(s, decl);
			}
			if ((long) high - (long) low >= Integer.MAX_VALUE) {
				String s = "Range for variable \"" + decl.getName() + "\" (" + low + "-" + high + ") is too big";
				throw new PrismLangException(s, decl);
			}
			// Check start is valid
			if (start < low || start > high) {
				String s = "Invalid initial value (" + start + ") for variable \"" + decl.getName() + "\"";
				throw new PrismLangException(s, decl);
			}
		}
		// Variable is a Boolean
		else if (declType instanceof DeclarationBool) {
			low = 0;
			high = 1;
			start = (decl.getStartOrDefault().evaluateBoolean(constantValues)) ? 1 : 0;
		}
		// Variable is a clock
		else if (declType instanceof DeclarationClock) {
			// Create dummy info
			low = 0;
			high = 1;
			start = 0;
		}
		// Variable is an (unbounded) integer
		else if (declType instanceof DeclarationIntUnbounded) {
			// Create dummy range info
			low = 0;
			high = 1;
			start = decl.getStartOrDefault().evaluateInt(constantValues);
		}
		else if (declType instanceof DeclTypeIndexedSet) {
			// There is nothing to do, since the individual elements should by now have been added 
			// to the ModulesFile by the visitor class ConvertIndexedSetDeclarations
			return null;		// So, we return null.
		}
		else {
			throw new PrismLangException("Unknown variable type \"" + declType + "\" in declaration", decl);
		}

		// Store low/high/start and return
		var.low = low;
		var.high = high;
		var.start = start;

		return var;
	}

	/**
	 * Get the number of variables stored in this list.  
	 */
	public int getNumVars()
	{
		return vars.size();
	}

	/**
	 * Look up the index of a variable, as stored in this list, by name.
	 * Returns -1 if there is no such variable. 
	 */
	public int getIndex(String name)
	{
		Integer i = nameMap.get(name);
		return (i == null) ? -1 : i;
	}

	/**
	 * Check if there is a variable of a given name in this list.
	 */
	public boolean exists(String name)
	{
		return getIndex(name) != -1;
	}

	/**
	 * Get the declaration of the ith variable in this list.
	 */
	public Declaration getDeclaration(int i)
	{
		return vars.get(i).decl;
	}

	/**
	 * Get the index in this VarList for a given declaration.
	 */
	public int getIndexFromDeclaration(Declaration d)
throws PrismLangException		// Until SHANE has considered the code in more detail.
	{
		if (d.getDeclType() instanceof DeclTypeIndexedSet) {
			PrismLangException ple = new PrismLangException("Not Implemented for IndexedSets.");
			ple.printStackTrace(System.out);
			throw ple;
		}
		for (int i=0;i<vars.size();i++) {
			if (vars.get(i).decl == d) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Get the name of the ith variable in this list.
	 */
	public String getName(int i)
	{
		return vars.get(i).decl.getName();
	}

	/**
	 * Get the type of the ith variable in this list.
	 */
	public Type getType(int i)
	{
		return vars.get(i).decl.getDeclType().getType();
	}

	/**
	 * Get the index of the module of the ith variable in this list (-1 denotes global variable).
	 */
	public int getModule(int i)
	{
		return vars.get(i).module;
	}

	/**
	 * Get the low value of the ith variable in this list (when encoded as an integer).
	 */
	public int getLow(int i)
	{
		return vars.get(i).low;
	}

	/**
	 * Get the high value of the ith variable in this list (when encoded as an integer).
	 */
	public int getHigh(int i)
	{
		return vars.get(i).high;
	}

	/**
	 * Get the range of the ith variable in this list (when encoded as an integer).
	 */
	public int getRange(int i)
	{
		return vars.get(i).high - vars.get(i).low + 1;
	}

	/**
	 * Get the number of bits required to store the ith variable in this list (when encoded as an integer).
	 */
	public int getRangeLogTwo(int i)
	{
		return (int) Math.ceil(PrismUtils.log2(getRange(i)));
	}

	/**
	 * Get the total number of bits required to store all variables in this list (when encoded as integers).
	 */
	public int getTotalNumBits()
	{
		return totalNumBits;
	}

	/**
	 * Get the initial value of the ith variable in this list (when encoded as an integer).
	 */
	public int getStart(int i)
	{
		return vars.get(i).start;
	}

	/**
	 * See whether to defer the creation of the DD for the specified variable until second round.
	 * @param i the index within this VarList of the variable to query about.
	 * @returns The round in which the variable's JDD variables should be created (0-9)
	 */
	public int getDeferCreationRound(int i)
	{
		if (i < 0 || i >= vars.size())
			throw new RuntimeException("Invalid integer value given");
		return vars.get(i).decl.getDeferCreateDDRound();
		
	}

	/**
	 * Get the value (as an Object) of a variable, from the value encoded as an integer. 
	 */
	public Object decodeFromInt(int var, int val)
	{
		Type type = getType(var);
		// Integer type
		if (type instanceof TypeInt) {
			return new Integer(val + getLow(var));
		}
		// Boolean type
		else if (type instanceof TypeBool) {
			return new Boolean(val != 0);
		}
		// Anything else
		return null;
	}

	/**
	 * Get the integer encoding of a value for a variable, specified as an Object.
	 * The Object is assumed to be of correct type (e.g. Integer, Boolean).
	 * Throws an exception if Object is of the wrong type.
	 */
	public int encodeToInt(int var, Object val) throws PrismLangException
	{
		Type type = getType(var);
		try {
			// Integer type
			if (type instanceof TypeInt) {
				return ((Integer) val).intValue() - getLow(var);
			}
			// Boolean type
			else if (type instanceof TypeBool) {
				return ((Boolean) val).booleanValue() ? 1 : 0;
			}
			// Anything else
			else {
				throw new PrismLangException("Unknown type " + type + " for variable " + getName(var));
			}
		} catch (ClassCastException e) {
			throw new PrismLangException("Value " + val + " is wrong type for variable " + getName(var));
		}
	}

	/**
	 * Get the integer encoding of a value for a variable, specified as a string.
	 */
	public int encodeToIntFromString(int var, String s) throws PrismLangException
	{
		Type type = getType(var);
		// Integer type
		if (type instanceof TypeInt) {
			try {
				int i = Integer.parseInt(s);
				return i - getLow(var);
			} catch (NumberFormatException e) {
				throw new PrismLangException("\"" + s + "\" is not a valid integer value");
			}
		}
		// Boolean type
		else if (type instanceof TypeBool) {
			if (s.equals("true"))
				return 1;
			else if (s.equals("false"))
				return 0;
			else
				throw new PrismLangException("\"" + s + "\" is not a valid Boolean value");

		}
		// Anything else
		else {
			throw new PrismLangException("Unknown type " + type + " for variable " + getName(var));
		}
	}

	/**
	 * Get a list of all possible values for a subset of the variables in this list.
	 * @param vars The subset of variables
	 */
	public List<Values> getAllValues(List<String> vars) throws PrismLangException
	{
		int i, j, k, n, lo, hi;
		Vector<Values> allValues;
		Values vals, valsNew;

		allValues = new Vector<Values>();
		allValues.add(new Values());
		for (String var : vars) {
			i = getIndex(var);
			if (getType(i) instanceof TypeBool) {
				n = allValues.size();
				for (j = 0; j < n; j++) {
					vals = allValues.get(j);
					valsNew = new Values(vals);
					valsNew.setValue(var, true);
					allValues.add(valsNew);
					vals.addValue(var, false);
				}
			} else if (getType(i) instanceof TypeInt) {
				lo = getLow(i);
				hi = getHigh(i);
				n = allValues.size();
				for (j = 0; j < n; j++) {
					vals = allValues.get(j);
					for (k = lo + 1; k < hi + 1; k++) {
						valsNew = new Values(vals);
						valsNew.setValue(var, k);
						allValues.add(valsNew);
					}
					vals.addValue(var, lo);
				}
			} else {
				throw new PrismLangException("Cannot determine all values for a variable of type " + getType(i));
			}
		}

		return allValues;
	}

	/**
	 * Get a list of all possible states over the variables in this list. Use with care!
	 */
	public List<State> getAllStates() throws PrismLangException
	{
		List<State> allStates;
		State state, stateNew;

		int numVars = getNumVars();
		allStates = new ArrayList<State>();
		allStates.add(new State(numVars));
		for (int i = 0; i < numVars; i++) {
			if (getType(i) instanceof TypeBool) {
				int n = allStates.size();
				for (int j = 0; j < n; j++) {
					state = allStates.get(j);
					stateNew = new State(state);
					stateNew.setValue(i, true);
					state.setValue(i, false);
					allStates.add(stateNew);
				}
			} else if (getType(i) instanceof TypeInt) {
				int lo = getLow(i);
				int hi = getHigh(i);
				int n = allStates.size();
				for (int j = 0; j < n; j++) {
					state = allStates.get(j);
					for (int k = lo + 1; k < hi + 1; k++) {
						stateNew = new State(state);
						stateNew.setValue(i, k);
						allStates.add(stateNew);
					}
					state.setValue(i, lo);
				}
			} else {
				throw new PrismLangException("Cannot determine all values for a variable of type " + getType(i));
			}
		}

		return allStates;
	}

	/**
	 * Convert a bit vector representing a single state to a State object. 
	 */
	public State convertBitSetToState(BitSet bits)
	{
		int i, n, j, var, val;
		State state;
		state = new State(getNumVars());
		var = val = j = 0;
		n = totalNumBits;
		for (i = 0; i < n; i++) {
			if (bits.get(i))
				val += (1 << (getRangeLogTwo(var) - j - 1));
			if (j >= getRangeLogTwo(var) - 1) {
				state.setValue(var, decodeFromInt(var, val));
				var++;
				val = 0;
				j = 0;
			} else {
				j++;
			}
		}
		return state;
	}

	/**
	 * Clone this list.
	 */
	public Object clone()
	{
		int i, n;
		n = getNumVars();
		VarList rv = new VarList();
		rv.vars = new ArrayList<Var>(n);
		rv.nameMap = new HashMap<String, Integer>(n);
		for (i = 0; i < n; i++) {
			rv.vars.add(new Var(vars.get(i)));
			rv.nameMap.put(getName(i), i);
		}
		return rv;
	}

	/**
	 * Class to store information about a single variable.
	 */
	class Var
	{
		// Basic info (name/type/etc.) stored as Declaration
		public Declaration decl;
		// Index of containing module (-1 for a global)
		public int module;
		// Info about how variable is encoded as an integer
		public int low;
		public int high;
		public int start;

		// Default constructor
		public Var()
		{
		}

		// Copy constructor
		public Var(Var var)
		{
			decl = (Declaration) var.decl.deepCopy();
			module = var.module;
			low = var.low;
			high = var.high;
			start = var.start;
		}
	}

	/**
	 * A Class to store information about a single variable that is an indexed set of separate values (i.e. an array type)
	 */
// ADDED BY SHANE - but (in 2018) not sure if being used anywhere?
	class IndexedVar extends Var
	{
		public int size;
		
		public IndexedVar()
		{	
		}
		
		public IndexedVar(IndexedVar var)
		{
			decl = (Declaration) var.decl.deepCopy();
			module = var.module;
			low = var.low;
			high = var.high;
			start = var.start;
			size = var.size;
		}
	}

}
