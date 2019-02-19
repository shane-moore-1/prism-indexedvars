//==============================================================================
//	
//	Copyright (c) 2002-
//	Authors:
//	* Dave Parker <d.a.parker@cs.bham.ac.uk> (University of Birmingham/Oxford)
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

package prism;

import java.util.*;

import jdd.*;
import parser.*;
import parser.ast.*;

import parser.visitor.FindRelOpInvolvingVar;
import parser.visitor.ResolveRestrictedScopes;

// class to translate a modules description file into an MTBDD model

public class Modules2MTBDD
{
public static boolean DEBUG_ShowEXCL_INCL = false;		// Whether to show which DDs are being INCLUDED or EXCLUDED during construction of a DD (in TransUpdate)
public static boolean DEBUG_SHANE = false; //true && !DEBUG_SHANE_NOTHING;
public static boolean DEBUG_RecurseVars = true;
public static boolean DEBUG_SHANE_ShowVarList = true;
public static boolean DEBUG_SHANE_ShowDD_Tree = false;
public static boolean DEBUG_SHANE_ShowStepsInTM = false;
public static boolean DEBUG_SHANE_ShowStepsInCCN2 = false;
public static boolean DEBUG_ShowFinalTransDD = false;			// Show the ultimately final transition matrix DD ? (It could be huge!)
public static boolean DEBUG_TrSysDef = true; //true && !DEBUG_SHANE_NOTHING;
public static boolean DEBUG_TraSysMod = true; //true && !DEBUG_SHANE_NOTHING;
public static boolean DEBUG_TransMod = true; //true && !DEBUG_SHANE_NOTHING;		// The version with parameters
public static boolean DEBUG_tranModVoid = true; //true && !DEBUG_SHANE_NOTHING;		// The void parameters version
public static boolean DEBUG_TransUpd = false ; //true && !DEBUG_SHANE_NOTHING;
public static boolean DEBUG_TransUpd_ShowStack = false ; //false && !DEBUG_SHANE_NOTHING;
public static boolean DEBUG_AllocDDV = false ; //true && !DEBUG_SHANE_NOTHING;
public static boolean DEBUG_CCN = true; //true && !DEBUG_SHANE_NOTHING;			// Show detail of combineCommandsNondet()
public static boolean DEBUG_SortRanges = false ; //true && !DEBUG_SHANE_NOTHING;
public static boolean DEBUG_SUBSTITUTIONS = true;		// Show translation of a command for a specific substitution (or if only 1 possibility, then that possibility)
public static boolean DEBUG_ChkRstr = true;			// Show the basic steps of CheckRestrictionLowerBound and CheckRestrictionUpperBound
public static boolean DEBUG_ChkRstr_ExtraDetail = true && DEBUG_ChkRstr;	// Show more-detailed steps of the check restriction methods.
public static int DebugIndent = 0;
public static void PrintDebugIndent() { for (int i = 0; i < DebugIndent; i++) System.out.print(" "); }

private String DEBUG_CurSynch;	// The current Synch name, for help in DEBUG OUTPUTS

private ArrayList<Expression> cachedGuardExprs;		// SHANE ONLY - For debugging of large model with hundreds of enumerated substitutions.


	// Prism object
	private Prism prism;
	
	// StateModelChecker for expression -> MTBDD conversion
	private StateModelChecker expr2mtbdd;
	
	// logs
	private PrismLog mainLog;		// main log

	// ModulesFile object to store syntax tree from parser
	private ModulesFile modulesFile;
	
	// model info
	
	// type
	private ModelType modelType;				// model type (dtmc/mdp/ctmc)
	// modules
	private int numModules;			// number of modules
	private String[] moduleNames;	// module names
	// vars/constants
	private int numVars;			// total number of module variables
	private VarList varList;		// list of module variables
	private Values constantValues;	// values of constants
	// synch info
	private int numSynchs;			// number of synchronisations
	private Vector<String> synchs;			// synchronisations
	// rewards
	private int numRewardStructs;		// number of reward structs
	private String[] rewardStructNames;	// reward struct names
	
	// mtbdd stuff
	
	// dds/dd vars - whole system
	private JDDNode trans;				// transition matrix dd
	private JDDNode range;				// dd giving range for system
	private JDDNode start;				// dd for start state
	private JDDNode stateRewards[];		// dds for state rewards
	private JDDNode transRewards[];		// dds for transition rewards
	private JDDNode transActions;	// dd for transition action labels (MDPs)
	private JDDNode transPerAction[];	// dds for transitions for each action (D/CTMCs)
	private JDDNode transInd;	// dds for independent bits of trans
	private JDDNode transSynch[];	// dds for synch action parts of trans
	private JDDVars allDDRowVars;		// all dd vars (rows)
	private JDDVars allDDColVars;		// all dd vars (cols)
	private JDDVars allDDSynchVars;		// all dd vars (synchronising actions)
	private JDDVars allDDSchedVars;		// all dd vars (scheduling)
	private JDDVars allDDChoiceVars;	// all dd vars (internal non-det.)
	private JDDVars allDDNondetVars;	// all dd vars (all non-det.)
	// dds/dd vars - globals
	private JDDVars globalDDRowVars;	// dd vars for all globals (rows)
	private JDDVars globalDDColVars;	// dd vars for all globals (cols)
	// dds/dd vars - modules
	private JDDVars[] moduleDDRowVars;	// dd vars for each module (rows)
	private JDDVars[] moduleDDColVars;	// dd vars for each module (cols)
	private JDDNode[] moduleRangeDDs;	// dd giving range for each module
	private JDDNode[] moduleIdentities;	// identity matrix for each module
	// dds/dd vars - variables
	private JDDVars[] varDDRowVars;		// dd vars (row/col) for each module variable
	private JDDVars[] varDDColVars;
	private JDDNode[] varRangeDDs;		// dd giving range for each module variable
	private JDDNode[] varColRangeDDs;	// dd giving range for each module variable (in col vars)
	private JDDNode[] varIdentities;	// identity matrix for each module variable
	// dds/dd vars - nondeterminism
	private JDDNode[] ddSynchVars;		// individual dd vars for synchronising actions
	private JDDNode[] ddSchedVars;		// individual dd vars for scheduling non-det.
	private JDDNode[] ddChoiceVars;		// individual dd vars for local non-det.

	private ModelVariablesDD modelVariables;
	
	// flags for keeping track of which variables have been used
	private boolean[] varsUsed;
	
	// symmetry info
	private boolean doSymmetry;			// use symmetry reduction
	private JDDNode symm; 				// dd of symmetric states
	private JDDNode nonSymms[];			// dds of non-(i,i+1)-symmetric states (i=0...numSymmModules-1)
	private int numModulesBeforeSymm;	// number of modules in the PRISM file before the symmetric ones
	private int numModulesAfterSymm;	// number of modules in the PRISM file after the symmetric ones
	private int numSymmModules;			// number of symmetric components
	
	// hidden option - do we also store each part of the transition matrix separately? (now defunct)
	private boolean storeTransParts = false; 
	// hidden option - do we also store action info for the transition matrix? (supersedes the above)
	private boolean storeTransActions = true; 
	
	// data structure used to store mtbdds and related info
	// for some component of the whole model

	private class ComponentDDs
	{
		public JDDNode guards;		// bdd for guards
		public JDDNode trans;		// mtbdd for transitions
		public JDDNode rewards[];	// mtbdd for rewards
		public int min; 			// min index of dd vars used for local nondeterminism
		public int max; 			// max index of dd vars used for local nondeterminism
		public ComponentDDs()
		{
			rewards = new JDDNode[modulesFile.getNumRewardStructs()];
		}
	}

	// a data structure used to store DDs pertaining to a particular translation of a Command 
	// (and with particular enumeration of some variables if an IndexedSet access is involved)
// ADDED BY SHANE
	private class TranslatedCommandDDs {
		public JDDNode guardDD;		// The JDD representing the guard of the command
		public JDDNode upDD;		// The JDD representing the updates
		public int originalCommandNumber;	// The command number in the prism module from which this was derived.
		public Expression guardExpr;	// The guard (after any substitutions) which is represented by the DDs.
	}
	
	// data structure used to store mtbdds and related info
	// for some part of the system definition

	private class SystemDDs
	{
		public ComponentDDs ind;		// for independent bit (i.e. tau action)
		public ComponentDDs[] synchs;	// for each synchronising action
		public JDDNode id;	 			// mtbdd for identity matrix
		public HashSet<String> allSynchs;		// set of all synchs used (syntactic)
		public SystemDDs(int n)
		{
			synchs = new ComponentDDs[n];
			allSynchs = new HashSet<String>();
		}
	}
	
// TEMPORARY - a debugger helper for SHANE to use
public static void ShaneReportDD(JDDNode toReport, String announcement, boolean showChildrenTree)
{
	System.out.println("======");
	System.out.println(announcement);
	System.out.println("  GetNumNodes():      " + JDD.GetNumNodes(toReport));
	System.out.println("  GetNumTerminals():  " + JDD.GetNumTerminals(toReport));
        System.out.println("  GetNumPaths():      " + JDD.GetNumPaths(toReport));
	if (showChildrenTree) {
		if (JDD.GetNumPaths(toReport) > 1500)
		   System.out.println("Tree would be too large to report"); 
		else 
		   toReport.ShaneShowChildren();
	}
	System.out.println(announcement);
	System.out.println("======");
}

	// constructor
	
	public Modules2MTBDD(Prism p, ModulesFile mf)
	{
		prism = p;
		mainLog = p.getMainLog();
if (DEBUG_SHANE) {
  System.out.println("Constructor of Modules2MTBDD. ModulesFile is " + mf);
}
		modulesFile = mf;
		// get symmetry reduction info
		String s = prism.getSettings().getString(PrismSettings.PRISM_SYMM_RED_PARAMS);
		doSymmetry = !(s == null || s == "");
	}
	
	@SuppressWarnings("unchecked") // for clone of vector in translate()

	// main method - translate
	public Model translate() throws PrismException
	{
		Model model = null;
		JDDNode tmp, tmp2;
		JDDVars ddv;
		int i;
if (DEBUG_SHANE) mainLog.println("<m2m_translate>");
		
		// get variable info from ModulesFile
		varList = modulesFile.createVarList();
		if (modulesFile.containsUnboundedVariables())
			throw new PrismNotSupportedException("Cannot build a model that contains a variable with unbounded range (try the explicit engine instead)");
		numVars = varList.getNumVars();

if (DEBUG_SHANE_ShowVarList) {
	mainLog.println("numVars is " + numVars + " and these are the variables in varList:");
	for (i = 0; i < numVars; i++)
		mainLog.println("\t["+ i + "] is " + varList.getName(i));
}

		constantValues = modulesFile.getConstantValues();
if (DEBUG_SHANE) {
	mainLog.println("constantValues is: '" + constantValues + "' - there are " + constantValues.getNumValues() + " values:");
	for (i = 0; i < constantValues.getNumValues(); i++)
		mainLog.println("\t["+ i + "] is " + constantValues.getName(i));
}
		
if (DEBUG_SHANE) System.out.println("m2m_translate is about to call getModelType");
		// get basic system info
		modelType = modulesFile.getModelType();
if (DEBUG_SHANE) System.out.println("m2m_translate is about to call getModuleNames");
		moduleNames = modulesFile.getModuleNames();
if (DEBUG_SHANE) {
	mainLog.println("in m2m_translate Place 1 - These are the names of the modules...");
	for (i = 0; i < moduleNames.length; i++) System.out.println("\t" + moduleNames[i]);
}
mainLog.flush();
		numModules = modulesFile.getNumModules();
		synchs = modulesFile.getSynchs();
		numSynchs = synchs.size();
		
		// allocate dd variables
		allocateDDVars();
		sortDDVars();
		sortIdentities();
		sortRanges();
if (DEBUG_SHANE) {
	mainLog.println("in m2m_translate Place 2 - These are the names of the modules...");
	for (i = 0; i < moduleNames.length; i++) System.out.println("\t" + moduleNames[i]);
}
		
if (DEBUG_SHANE) System.out.println("in m2m_translate, about to instantiate the StateModelChecker [to store in 'expr2mtbdd']\n<Make_StateModelChecker>");
		// create stripped-down StateModelChecker for expression to MTBDD conversions
		expr2mtbdd = new StateModelChecker(prism, varList, allDDRowVars, varDDRowVars, constantValues);
if (DEBUG_SHANE) System.out.println("</Make_StateModelChecker>");
		
if (DEBUG_SHANE) {
	mainLog.println("in m2m_translate Place 3 - These are the names of the modules...");
	for (i = 0; i < moduleNames.length; i++) System.out.println("\t" + moduleNames[i]);
}

if (DEBUG_SHANE) System.out.println("in m2m_translate, about to call translateModules()");
		// translate modules file into dd
		translateModules();
if (DEBUG_SHANE) System.out.println("in m2m_translate, after returning from call of translateModules()");
		
if (DEBUG_SHANE) {
	mainLog.println("in m2m_translate Place 4 - These are the names of the modules...");
	for (i = 0; i < moduleNames.length; i++) System.out.println("\t" + moduleNames[i]);
}

		// get rid of any nondet dd variables not needed
		if (modelType == ModelType.MDP) {
			tmp = JDD.GetSupport(trans);
			tmp = JDD.ThereExists(tmp, allDDRowVars);
			tmp = JDD.ThereExists(tmp, allDDColVars);
			tmp2 = tmp;
			ddv = new JDDVars();
ddv.setPurpose("allDDNondetVars, set in m2mtbdd.translate()");
			while (!tmp2.equals(JDD.ONE)) {
				ddv.addVar(JDD.Var(tmp2.getIndex()));
				tmp2 = tmp2.getThen();
			}
			JDD.Deref(tmp);
			allDDNondetVars.derefAll();
			allDDNondetVars = ddv;
		}
		
// 		// print dd variables actually used (support of trans)
// 		mainLog.print("\nMTBDD variables used (" + allDDRowVars.n() + "r, " + allDDRowVars.n() + "c");
// 		if (type == ModulesFile.NONDETERMINISTIC) mainLog.print(", " + allDDNondetVars.n() + "nd");
// 		mainLog.print("):");
// 		tmp = JDD.GetSupport(trans);
// 		tmp2 = tmp;
// 		while (!tmp2.isConstant()) {
// 			//mainLog.print(" " + tmp2.getIndex() + ":" + ddVarNames.elementAt(tmp2.getIndex()));
// 			mainLog.print(" " + ddVarNames.elementAt(tmp2.getIndex()));
// 			tmp2 = tmp2.getThen();
// 		}
// 		mainLog.println();
// 		JDD.Deref(tmp);
		
		// Print some info (if extraddinfo flag on)
		if (prism.getExtraDDInfo()) {
			mainLog.print("Transition matrix (pre-reachability): ");
			mainLog.print(JDD.GetNumNodes(trans) + " nodes (");
			mainLog.print(JDD.GetNumTerminals(trans) + " terminal)\n");
		}
if (DEBUG_SHANE) {		// Replicates the thing immediately preceding
	mainLog.print("Transition matrix (pre-reachability): ");
	mainLog.print(JDD.GetNumNodes(trans) + " nodes (");
	mainLog.print(JDD.GetNumTerminals(trans) + " terminal)\n");

}
		
		// build bdd for initial state(s)
		buildInitialStates();
		
if (DEBUG_SHANE) {		// Replicates the above
	mainLog.print("Transition matrix (pre-reachability but after buildInitialStates): ");
	mainLog.print(JDD.GetNumNodes(trans) + " nodes (");
	mainLog.print(JDD.GetNumTerminals(trans) + " terminal)\n");

}

		// store reward struct names
		rewardStructNames = new String[numRewardStructs];
		for (i = 0; i < numRewardStructs; i++) {
			rewardStructNames[i] = modulesFile.getRewardStruct(i).getName();
		}
		
		// create new Model object to be returned
		if (modelType == ModelType.DTMC) {
if (DEBUG_SHANE) mainLog.println("will instantiate a ProbModel");
			model = new ProbModel(trans, start, stateRewards, transRewards, rewardStructNames, allDDRowVars, allDDColVars, modelVariables,
						   numModules, moduleNames, moduleDDRowVars, moduleDDColVars,
						   numVars, varList, varDDRowVars, varDDColVars, constantValues);
		}
		else if (modelType == ModelType.MDP) {
if (DEBUG_SHANE) mainLog.println("will instantiate a NondetModel");
			model = new NondetModel(trans, start, stateRewards, transRewards, rewardStructNames, allDDRowVars, allDDColVars,
						     allDDSynchVars, allDDSchedVars, allDDChoiceVars, allDDNondetVars, modelVariables,
						     numModules, moduleNames, moduleDDRowVars, moduleDDColVars,
						     numVars, varList, varDDRowVars, varDDColVars, constantValues);
		}
		else if (modelType == ModelType.CTMC) {
if (DEBUG_SHANE) mainLog.println("will instantiate a StochModel");
			model = new StochModel(trans, start, stateRewards, transRewards, rewardStructNames, allDDRowVars, allDDColVars, modelVariables,
						    numModules, moduleNames, moduleDDRowVars, moduleDDColVars,
						    numVars, varList, varDDRowVars, varDDColVars, constantValues);
		}
		
		// We also store a copy of the list of action label names
		model.setSynchs((Vector<String>)synchs.clone());
		
		// For MDPs, we also store the DDs used to construct the part
		// of the transition matrix that corresponds to each action
		if (modelType == ModelType.MDP && storeTransParts) {
			((NondetModel)model).setTransInd(transInd);
			((NondetModel)model).setTransSynch(transSynch);
		}
		
		// If required, we also store info about action labels
		if (storeTransActions) {
			// Note: one of these will be null, depending on model type
			// but this is fine: null = none stored.
			model.setTransActions(transActions);
			model.setTransPerAction(transPerAction);
		}
		
		// do reachability (or not)
		if (prism.getDoReach()) {
DebugIndent = 0;
PrintDebugIndent();
System.out.println("<CALL_doReach wherefrom='prism.Modules2MTBDD.translate'>\nCalling doReachability() for model of type " + model.getClass().getName() + "...");
			mainLog.print("\nComputing reachable states...\n");
			model.doReachability();
System.out.println("</CALL_doReach>");

System.out.println("<CALL_filterReachable>");
PrintDebugIndent();
System.out.println("[In Modules2MTBDD.translate()] finished doReachability(), calling filterReachableStates()...");

			model.filterReachableStates();
System.out.println("</CALL_filterReachable>");
		}
		else {
			mainLog.print("\nSkipping reachable state computation.\n");
			model.skipReachability();
			model.filterReachableStates();
		}
		
		// Print some info (if extraddinfo flag on)
		if (prism.getExtraDDInfo()) {
			mainLog.print("Reach: " + JDD.GetNumNodes(model.getReach()) + " nodes\n");
		}
if (DEBUG_SHANE) {		// Copied from earlier, and from the immediately prior 'ExtraDD' thing
	mainLog.print("Transition matrix (after reachability [if was desired]): ");
	mainLog.print(JDD.GetNumNodes(trans) + " nodes (");
	mainLog.print(JDD.GetNumTerminals(trans) + " terminal)\n");
	mainLog.print("Reach: " + JDD.GetNumNodes(model.getReach()) + " nodes\n");
}


if (DEBUG_SHANE & doSymmetry) {
	PrintDebugIndent();
	System.out.println("[In Modules2MTBDD.translate()] approx line 437, about to call doSymmetry()");
}
		// symmetrification
		if (doSymmetry) doSymmetry(model);

PrintDebugIndent();
System.out.println("[In Modules2MTBDD.translate()] approx line 443, about to call getFixDeadlocks() then possibly findDeadlocks()");

		// find/fix any deadlocks
		model.findDeadlocks(prism.getFixDeadlocks());
		
		// deref spare dds
		globalDDRowVars.derefAll();
		globalDDColVars.derefAll();
		JDD.DerefArray(moduleIdentities, numModules);
		JDD.DerefArray(moduleRangeDDs, numModules);
		JDD.DerefArray(varIdentities, numVars);
		JDD.DerefArray(varRangeDDs, numVars);
		JDD.DerefArray(varColRangeDDs, numVars);
		JDD.Deref(range);
		if (modelType == ModelType.MDP) {
			JDD.DerefArray(ddSynchVars, ddSynchVars.length);
			JDD.DerefArray(ddSchedVars, ddSchedVars.length);
			JDD.DerefArray(ddChoiceVars, ddChoiceVars.length);
		}
		if (doSymmetry) {
			JDD.Deref(symm);
			JDD.DerefArray(nonSymms, numSymmModules-1);
		}


		expr2mtbdd.clearDummyModel();
PrintDebugIndent();
System.out.println("[In Modules2MTBDD.translate()] Reached End of method");

if (DEBUG_SHANE_ShowStepsInTM)
  ShaneReportDD(model.getReach(),"The 'reach' DD after doReachability and filterReachableStates and fixDeadlocks is the following:",true);

if (DEBUG_SHANE) mainLog.println("</m2m_translate>");
		
		return model;
	}
	
	// allocate DD vars for system
	// i.e. decide on variable ordering and request variables from CUDD
			
	private void allocateDDVars()
	{
		int i, j, m, n, last;
		
System.out.println("<ALLOC_DD_VARS>");
		modelVariables = new ModelVariablesDD();
		
		switch (prism.getOrdering()) {
		
		case 1:
		// ordering: (a ... a) (s ... s) (l ... l) (r c ... r c)
System.out.println("in allocateDDVars - Case 1 is being done.");
		
System.out.println("Calling modelVariables.preallocateExtraActionVariables...");
			modelVariables.preallocateExtraActionVariables(prism.getSettings().getInteger(PrismSettings.PRISM_DD_EXTRA_ACTION_VARS));

			// create arrays/etc. first
			
			// nondeterministic variables
			if (modelType == ModelType.MDP) {
System.out.println("Doing the First IF block for MDP modeltypes - will create array ddSynchVars...");
				// synchronizing action variables
				ddSynchVars = new JDDNode[numSynchs];
System.out.println("Create array ddSchedVars...");
				// sched nondet vars
				ddSchedVars = new JDDNode[numModules];

				// local nondet vars
				// max num needed = total num of commands in all modules + num of modules
				// (actually, might need more for complex parallel compositions? hmmm...)
				m = numModules;
System.out.println("m, initially just the number of modules, is: " + m);
				for (i = 0; i < numModules; i++) {
					m += modulesFile.getModule(i).getNumCommands();
System.out.println("Module " + i + " has " + modulesFile.getModule(i).getNumCommands() + " commands - adding that to m, so m is now: " + m);
				}
System.out.println("Final value of m is " + m + " - creating an array ddChoiceVars with that many elements...");
				ddChoiceVars = new JDDNode[m];
			}
			// module variable (row/col) vars
System.out.println("\nCreating varDDRowVars (of size " + numVars + ") and varDDColVars (same size)");
			varDDRowVars = new JDDVars[numVars];
			varDDColVars = new JDDVars[numVars];
			for (i = 0; i < numVars; i++) {
System.out.println("\nCreating the JDDVars for varDDRowVars["+i+"]...");
				varDDRowVars[i] = new JDDVars();
varDDRowVars[i].setPurpose("varDDRowVars["+i+"], set-up in m2mtbdd.allocateDDVars()");
System.out.println("\nCreating the JDDVars for varDDColVars["+i+"]...");
				varDDColVars[i] = new JDDVars();
varDDColVars[i].setPurpose("varDDColVars["+i+"], set-up in m2mtbdd.allocateDDVars()");
			}
			
			// now allocate variables

			// allocate synchronizing action variables
			if (modelType == ModelType.MDP) {
System.out.println("\nIn the Second IF block for MDP modeltypes; about to create " + numSynchs + " variables for Synchronising Actions...");
				// allocate vars
				for (i = 0; i < numSynchs; i++) {
System.out.println("Creating ddSynchVars["+i+"]...");
					ddSynchVars[i] = modelVariables.allocateVariable(synchs.elementAt(i)+".a");
ddSynchVars[i].setPurpose("represent synchronisation action ddSynchVars[" + i + "] ("+synchs.get(i)+" : " + synchs.elementAt(i)+".a"+"), created in allocateDDVars");
				}
			}
		
			// allocate scheduling nondet dd variables
			if (modelType == ModelType.MDP) {
System.out.println("\nIn the Third IF block for MDP modeltypes; about to create " + numModules + " variables for Scheduling of Modules...");
				// allocate vars
				for (i = 0; i < numModules; i++) {
System.out.println("Creating ddSchedVars["+i+"], ("+moduleNames[i] + ".s)");
					ddSchedVars[i] = modelVariables.allocateVariable(moduleNames[i] + ".s");
ddSchedVars[i].setPurpose("represent a nondet scheduling variable ddSchedVars[" + i + "], named " + moduleNames[i]+".s, created in allocateDDVars");
				}
			}
			
			// allocate internal nondet choice dd variables
			if (modelType == ModelType.MDP) {
System.out.println("\nIn the Fourth IF block for MDP modeltypes; about to create variables for internal nondeterministic choices...");
				m = ddChoiceVars.length;
				for (i = 0; i < m; i++) {
System.out.println("Creating variable for ddChoiceVars["+i+"], named l"+i);
					ddChoiceVars[i] = modelVariables.allocateVariable("l" + i);
ddChoiceVars[i].setPurpose("represent a nondet choice variable: ddChoiceVars["+i+"] - created in allocateDDVars");
				}
			}
			

System.out.println("About to call modelVariables.preallocateExtraStateVariables()...");
			// create a gap in the dd variables
			// this allows to prepend additional row/col vars, e.g. for constructing
			// a product model when doing LTL model checking
			modelVariables.preallocateExtraStateVariables(prism.getSettings().getInteger(PrismSettings.PRISM_DD_EXTRA_STATE_VARS));

			
System.out.println("\nAbout to allocate the DD vars for all the modules' variables (numVars is " + numVars + ")...");
			// allocate dd variables for module variables (i.e. rows/cols)
			// go through all vars in order (incl. global variables)
			// so overall ordering can be specified by ordering in the input file
//SHANE MODIFICATION: SKIP the ones which are requested for Deferral - they will be created in the next loop instead.
			for (i = 0; i < numVars; i++) {
			    if (!varList.getDeferCreation(i)) {
				// get number of dd variables needed
				// (ceiling of log2 of range of variable)
				n = varList.getRangeLogTwo(i);
System.out.println("\nVariable " + i + " is \"" + varList.getName(i) + "\", and its range requires " + n + " DD variables. Creating " + (n*2) + " variables (pre and post ones)...");

// Set description of the RowVars[i] and ColVars[i]; each is a JDDVars to hold variable(s) pertaining to a specific prism variable.
varDDRowVars[i].setPurpose("varDDRowVars["+i+"], the Variables to represent pre-values for " + varList.getName(i) + ", set-up in m2mtbdd.allocateDDVars()");
varDDColVars[i].setPurpose("varDDColVars["+i+"], the Variables to represent updates for " + varList.getName(i) + "', set-up in m2mtbdd.allocateDDVars()");
				// add pairs of variables (row/col)
				for (j = 0; j < n; j++) {
System.out.println("Creating " + varList.getName(i) + "." + j);
					// new dd row variable
					varDDRowVars[i].addVar(modelVariables.allocateVariable(varList.getName(i) + "." + j));
System.out.println("Creating " + varList.getName(i) + "'." + j);
					// new dd col variable
					varDDColVars[i].addVar(modelVariables.allocateVariable(varList.getName(i) + "'." + j));
				}
			    }
else System.out.println("Skipping variable " + i + " (\""+varList.getName(i) + "\") because its DD creation is to be deferred.");
			}

// ADDED BY SHANE - Now we will created the Deferred variables...
			for (i = 0; i < numVars; i++) {
			    if (varList.getDeferCreation(i)) {
				// get number of dd variables needed
				// (ceiling of log2 of range of variable)
				n = varList.getRangeLogTwo(i);
System.out.println("\nVariable " + i + " is \"" + varList.getName(i) + "\", and its range requires " + n + " DD variables. Creating " + (n*2) + " variables (pre and post ones)...");

// Set description of the RowVars[i] and ColVars[i]; each is a JDDVars to hold variable(s) pertaining to a specific prism variable.
varDDRowVars[i].setPurpose("varDDRowVars["+i+"], the Variables to represent pre-values for " + varList.getName(i) + ", set-up in m2mtbdd.allocateDDVars()");
varDDColVars[i].setPurpose("varDDColVars["+i+"], the Variables to represent updates for " + varList.getName(i) + "', set-up in m2mtbdd.allocateDDVars()");
				// add pairs of variables (row/col)
				for (j = 0; j < n; j++) {
System.out.println("Creating " + varList.getName(i) + "." + j);
					// new dd row variable
					varDDRowVars[i].addVar(modelVariables.allocateVariable(varList.getName(i) + "." + j));
System.out.println("Creating " + varList.getName(i) + "'." + j);
					// new dd col variable
					varDDColVars[i].addVar(modelVariables.allocateVariable(varList.getName(i) + "'." + j));
				}
			    }
else System.out.println("Skipping variable " + i + " (\""+varList.getName(i) + "\") because we created the DD earlier (non-deferred var).");
			}


System.out.println("END of Case 1 block (within allocateDDVars())\n");
			break;
			
		case 2:
System.out.println("in allocateDDVars - Case 2 is being done.");
		// ordering: (a ... a) (l ... l) (s r c ... r c) (s r c ... r c) ...
	
			modelVariables.preallocateExtraActionVariables(prism.getSettings().getInteger(PrismSettings.PRISM_DD_EXTRA_ACTION_VARS));

			// create arrays/etc. first
			
			// nondeterministic variables
			if (modelType == ModelType.MDP) {
				// synchronizing action variables
				ddSynchVars = new JDDNode[numSynchs];
				// sched nondet vars
				ddSchedVars = new JDDNode[numModules];
				// local nondet vars: num = total num of commands in all modules + num of modules
				m = numModules;
				for (i = 0; i < numModules; i++) {
					m += modulesFile.getModule(i).getNumCommands();
				}
				ddChoiceVars = new JDDNode[m];
			}
			// module variable (row/col) vars
			varDDRowVars = new JDDVars[numVars];
			varDDColVars = new JDDVars[numVars];
			for (i = 0; i < numVars; i++) {
				varDDRowVars[i] = new JDDVars();
varDDRowVars[i].setPurpose("varDDRowVars["+i+"], set-up in m2mtbdd.allocateDDVars()");
				varDDColVars[i] = new JDDVars();
varDDColVars[i].setPurpose("varDDColVars["+i+"], set-up in m2mtbdd.allocateDDVars()");
			}
			
			// now allocate variables
			
			// allocate synchronizing action variables
			if (modelType == ModelType.MDP) {
				for (i = 0; i < numSynchs; i++) {
					ddSynchVars[i] = modelVariables.allocateVariable(synchs.elementAt(i)+".a");
ddSynchVars[i].setPurpose("represent synchronisation action ddSynchVars[" + i + "], created in allocateDDVars");
				}
			}

			// allocate internal nondet choice dd variables
			if (modelType == ModelType.MDP) {
				m = ddChoiceVars.length;
				for (i = 0; i < m; i++) {
					ddChoiceVars[i] = modelVariables.allocateVariable("l" + i);
ddChoiceVars[i].setPurpose("represent a nondet choice variable: ddChoiceVars["+i+"], created in allocateDDVars");
				}
			}

			// TODO: For the other variable order (-o1, used for sparse/hybrid by default,
			// see above), we preallocate a certain number of state variables.
			// For consistency, it would make sense to do the same here. However,
			// we should first do some testing to see if this negatively impacts
			// performance.

			// go through all vars in order (incl. global variables)
			// so overall ordering can be specified by ordering in the input file
			// use 'last' to detect when starting a new module
			last = -1; // globals are -1
			for (i = 0; i < numVars; i++) {
				// if at the start of a module's variables
				// and model is an mdp...
				if ((modelType == ModelType.MDP) && (last != varList.getModule(i))) {
					// add scheduling dd var(s) (may do multiple ones here if modules have no vars)
					for (j = last+1; j <= varList.getModule(i); j++) {
						ddSchedVars[j] = modelVariables.allocateVariable(moduleNames[j] + ".s");
ddSchedVars[j].setPurpose("represent a nondet scheduling variable ddSchedVars[" + j + "], for " + moduleNames[j] + ".s, created in allocateDDVars");
					}
					// change 'last'
					last = varList.getModule(i);
				}
				// now add row/col dd vars for the variable
				// get number of dd variables needed
				// (ceiling of log2 of range of variable)
				n = varList.getRangeLogTwo(i);
				// add pairs of variables (row/col)
				for (j = 0; j < n; j++) {
					varDDRowVars[i].addVar(modelVariables.allocateVariable(varList.getName(i) + "." + j));
//System.out.println("Created DD with index " + varDDRowVars[i].GetIndex() + " for normal variable " + varList(getName(i) + "." + j);
					varDDColVars[i].addVar(modelVariables.allocateVariable(varList.getName(i) + "'." + j));
//System.out.println("Created DD with index " + varDDRowVars[i].GetIndex() + " for normal update-variable " + varList(getName(i) + "'." + j);
				}
			}
			// add any remaining scheduling dd var(s) (happens if some modules have no vars)
			if (modelType == ModelType.MDP) for (j = last+1; j <numModules; j++) {
				ddSchedVars[j] = modelVariables.allocateVariable(moduleNames[j] + ".s");
			}
			break;
			
		default:
			mainLog.printWarning("Invalid MTBDD ordering selected - it's all going to go wrong.");
			break;
		}
		
if (DEBUG_AllocDDV) {
System.out.println("<MODEL_VARS>");
modelVariables.showVarNamesAndIDs();
System.out.println("</MODEL_VARS>");
}
		// print out all mtbdd variables allocated
//		mainLog.print("\nMTBDD variables:");
//		for (i = 0; i < ddVarNames.size(); i++) {
//			mainLog.print(" (" + i + ")" + ddVarNames.elementAt(i));
//		}
//		mainLog.println();
System.out.println("</ALLOC_DD_VARS>");
	}

	// sort out DD variables and the arrays they are stored in
	// (more than one copy of most variables is stored)
			
	private void sortDDVars()
	{
		int i, m;

System.out.println("\n<SORT_DD_VARS>");		
		// put refs for all globals and all vars in each module together
		// create arrays
		globalDDRowVars = new JDDVars();
globalDDRowVars.setPurpose("globalDDRowVars, set-up in m2mtbdd.sortDDVars()");
		globalDDColVars = new JDDVars();
globalDDColVars.setPurpose("globalDDColVars, set-up in m2mtbdd.sortDDVars()");
		moduleDDRowVars = new JDDVars[numModules];
		moduleDDColVars = new JDDVars[numModules];
System.out.println("\nAbout to go through each module, creating a JDDVars for its row and another for its column...");
		for (i = 0; i < numModules; i++) {
			moduleDDRowVars[i] = new JDDVars();
moduleDDRowVars[i].setPurpose("moduleDDRowVars["+i+"], The pre-update variables for module " + i+", set-up in m2mtbdd.sortDDVars()");
			moduleDDColVars[i] = new JDDVars();
moduleDDColVars[i].setPurpose("moduleDDColVars["+i+"], The post-update variables for module " + i+", set-up in m2mtbdd.sortDDVars()");
		}

System.out.println("\nAbout to go through all the variables of the model...");
		// go thru all variables
		for (i = 0; i < numVars; i++) {
			// check which module it belongs to
			m = varList.getModule(i);
System.out.println("\nConsidering variable: \"" + varList.getName(i) + "\"");
			// if global...
			if (m == -1) {
System.out.println("  It is a GLOBAL variable. So copying into globalDDRowVars...");
				globalDDRowVars.copyVarsFrom(varDDRowVars[i]);
System.out.println("  and copying into globalDDColVars...");
				globalDDColVars.copyVarsFrom(varDDColVars[i]);
			}
			// otherwise...
			else {
System.out.println("  It is from module " + m + " (" + moduleNames[m] + "), so copying into moduleDDRowVars["+m+"] from varDDRowVars["+i+"]");
				moduleDDRowVars[m].copyVarsFrom(varDDRowVars[i]);
System.out.println("  and copying into moduleDDColVars["+m+"] from varDDColVars["+i+"]");
				moduleDDColVars[m].copyVarsFrom(varDDColVars[i]);
			}
		}
		
System.out.println("\nCreating JDDVars for 'allDDRowVars'...");
		// put refs for all vars in whole system together
		// create arrays
		allDDRowVars = new JDDVars();
allDDRowVars.setPurpose("allDDRowVars, set-up in m2mtbdd.sortDDVars()");
System.out.println("\nCreating JDDVars for 'allDDColVars'...");
		allDDColVars = new JDDVars();
allDDColVars.setPurpose("allDDColVars, set-up in m2mtbdd.sortDDVars()");
		if (modelType == ModelType.MDP) {
System.out.println("\nCreating JDDVars for 'allDDSynchVars'...");
			allDDSynchVars = new JDDVars();
allDDSynchVars.setPurpose("allDDSynchVars, set-up in m2mtbdd.sortDDVars()");
System.out.println("\nCreating JDDVars for 'allDDSchedVars'...");
			allDDSchedVars = new JDDVars();
allDDSchedVars.setPurpose("allDDSchedVars, set-up in m2mtbdd.sortDDVars()");
System.out.println("\nCreating JDDVars for 'allDDChoiceVars'...");
			allDDChoiceVars = new JDDVars();
allDDChoiceVars.setPurpose("allDDChoiceVars, set-up in m2mtbdd.sortDDVars()");
System.out.println("\nCreating JDDVars for 'allDDNondetVars'...");
			allDDNondetVars = new JDDVars();
allDDNondetVars.setPurpose("allDDNondetVars, set-up in m2mtbdd.sortDDVars()");
		}
		// go thru all variables
System.out.println("\nCopying all variables from varDDRowVars into allDDRowVars, and all variables from varDDColVars to allDDColVars...");
		for (i = 0; i < numVars; i++) {
			// add to list
			allDDRowVars.copyVarsFrom(varDDRowVars[i]);
			allDDColVars.copyVarsFrom(varDDColVars[i]);
		}
		if (modelType == ModelType.MDP) {
System.out.println("\nCopying all variables from ddSynchVars into allDDSynchVars, and also into allDDNondetVars...");
			// go thru all syncronising action vars
			for (i = 0; i < ddSynchVars.length; i++) {
				// add to list
				allDDSynchVars.addVar(ddSynchVars[i].copy());
				allDDNondetVars.addVar(ddSynchVars[i].copy());
			}
			// go thru all scheduler nondet vars
System.out.println("\nCopying all variables from ddSchedVars into allDDSchedVars, and also into allDDNondetVars...");
			for (i = 0; i < ddSchedVars.length; i++) {
				// add to list
				allDDSchedVars.addVar(ddSchedVars[i].copy());
				allDDNondetVars.addVar(ddSchedVars[i].copy());
			}
System.out.println("\nCopying all variables from ddChoiceVars into allDDSchedVars, and also into allDDNondetVars...");
			// go thru all local nondet vars
			for (i = 0; i < ddChoiceVars.length; i++) {
				// add to list
				allDDChoiceVars.addVar(ddChoiceVars[i].copy());
				allDDNondetVars.addVar(ddChoiceVars[i].copy());
			}
		}
System.out.println("</SORT_DD_VARS>\n");		
	}
	
	// sort DDs for identities
	
	private void sortIdentities()
	{
		int i, j;
		JDDNode id;
		
		// variable identities
		varIdentities = new JDDNode[numVars];
		for (i = 0; i < numVars; i++) {
			// set each element of the identity matrix
			id = JDD.Constant(0);
			for (j = 0; j < varList.getRange(i); j++) {
				id = JDD.SetMatrixElement(id, varDDRowVars[i], varDDColVars[i], j, j, 1);
			}
			varIdentities[i] = id;
varIdentities[i].setPurpose("varIdentities["+i+"], created in sortIdentities");
ShaneReportDD(varIdentities[i],"varIdentities["+i+"]",true);
		}
		// module identities
		moduleIdentities = new JDDNode[numModules];
		for (i = 0; i < numModules; i++) {
			// product of identities for vars in module
			id = JDD.Constant(1);
			for (j = 0; j < numVars; j++) {
				if (varList.getModule(j) == i) {
					id = JDD.Apply(JDD.TIMES, id, varIdentities[j].copy());
				}
			}
			moduleIdentities[i] = id;
moduleIdentities[i].setPurpose("moduleIdentities["+i+"], created in sortIdentities");
		}
	}

	// Sort DDs for ranges
	
	private void sortRanges()
	{
		int i;
		
		// initialise range for whole system
		range = JDD.Constant(1);
if (DEBUG_SortRanges) System.out.println("<SortRanges>");		

		// variable ranges		
		varRangeDDs = new JDDNode[numVars];
		varColRangeDDs = new JDDNode[numVars];
		for (i = 0; i < numVars; i++) {
if(DEBUG_SortRanges) System.out.println("Considering i=" + i + " - will use varDDColVars["+i+"] which is " + varDDColVars[i]);
			// obtain range dd by abstracting from identity matrix
			varRangeDDs[i] = JDD.SumAbstract(varIdentities[i].copy(), varDDColVars[i]);
varRangeDDs[i].setPurpose("% varRangeDDs["+i+"], created during sortRanges() %");
if(DEBUG_SortRanges) System.out.println("Also, will use varDDRowVars["+i+"] which is " + varDDRowVars[i]);
			// obtain range dd by abstracting from identity matrix
			varColRangeDDs[i] = JDD.SumAbstract(varIdentities[i].copy(), varDDRowVars[i]);
varColRangeDDs[i].setPurpose("% varColRangeDDs["+i+"], created during sortRanges() %");
			// build up range for whole system as we go
			range = JDD.Apply(JDD.TIMES, range, varRangeDDs[i].copy());
		}
range.setPurpose("% range, created during sortRanges() %");

		// module ranges
if (DEBUG_SortRanges) System.out.println("Now the second loop of sortRanges()...");
		moduleRangeDDs = new JDDNode[numModules];
		for (i = 0; i < numModules; i++) {
if(DEBUG_SortRanges) System.out.println("Considering i=" + i + " - will use moduleDDColVars["+i+"] which is " + moduleDDColVars[i]);
			// obtain range dd by abstracting from identity matrix
			moduleRangeDDs[i] = JDD.SumAbstract(moduleIdentities[i].copy(), moduleDDColVars[i]);
moduleRangeDDs[i].setPurpose("% moduleRangeDDs[" + i + "], created in sortRanges() %");
		}
if (DEBUG_SortRanges) System.out.println("</SortRanges>");
	}

	// translate modules decription to dds
	
	private void translateModules() throws PrismException
	{
if (DEBUG_tranModVoid) System.out.println("Start of m2mtbdd.translateModules(void)\n<TranMods>");
		SystemFullParallel sys;
		JDDNode tmp;
		int i;
		
		varsUsed = new boolean[numVars];
		
		if (modulesFile.getSystemDefn() == null) {
if (DEBUG_tranModVoid) System.out.println("In Moduels2MTBDD.translateModules, inside the IF case - creating a SystemFullParallel");
			sys = new SystemFullParallel();
			for (i = 0; i < numModules; i++) {
if (DEBUG_SHANE) System.out.println("in translateModules(void), moduleNames["+i+"] is " + moduleNames[i]);
	// The following has been broken-down for debug interpreting...
	//			sys.addOperand(new SystemModule(moduleNames[i]));
if (DEBUG_tranModVoid) System.out.println("<Create_SystemModule where='m2mtbdd.translateModules(void)'>");
				SystemModule sm = new SystemModule(moduleNames[i]);
if (DEBUG_tranModVoid) System.out.println("</Create_SystemModule>\nCalling Sys.addOperand from m2mtbdd.translateModules(void)");
				sys.addOperand(sm);
if (DEBUG_tranModVoid) System.out.println("Completed call of Sys.addOperand from m2mtbdd.translateModules(void)");
			}
if (DEBUG_tranModVoid) System.out.println("<CALL what='translateSystemDefn(sys)' fromWhere='m2mtbdd.translateModules(void), the IF block'>");
			translateSystemDefn(sys);
if (DEBUG_tranModVoid) System.out.println("</CALL what='translateSystemDefn(sys)' fromWhere='m2mtbdd.translateModules(void)'>");
		}
		else {
if (DEBUG_tranModVoid) System.out.println("In Moduels2MTBDD.translateModules, inside the ELSE case.");
if (DEBUG_tranModVoid) System.out.println("<CALL what='translateSystemDefn(sys)' fromWhere='m2mtbdd.translateModules(void), the ELSE block'>");
			translateSystemDefn(modulesFile.getSystemDefn());
if (DEBUG_tranModVoid) System.out.println("</CALL what='translateSystemDefn(sys)' fromWhere='m2mtbdd.translateModules(void)'>");
		}
		
//		if (type == ModulesFile.PROBABILISTIC) {
//			// divide each row by number of modules
//			trans = JDD.Apply(JDD.DIVIDE, trans, JDD.Constant(numModules));
//		}
		
		// for dtmcs, need to normalise each row to remove local nondeterminism
		if (modelType == ModelType.DTMC) {
			// divide each row by row sum
			tmp = JDD.SumAbstract(trans.copy(), allDDColVars);
			trans = JDD.Apply(JDD.DIVIDE, trans, tmp);
		}
if (DEBUG_tranModVoid) System.out.println("</TranMods>\nending m2mtbdd.translateModules(void)");
	}

	// build system according to composition expression
	
	private void translateSystemDefn(SystemDefn sys) throws PrismException
	{
		SystemDDs sysDDs;
		JDDNode tmp, v;
		int i, j, n, max;
		int[] synchMin;
		
System.out.println("<TranslateSystemDefn>\nStarting to translate the system...\nThe number of synchs is " + numSynchs);
		// initialise some values for synchMin
		// (stores min indices of dd vars to use for local nondet)
		synchMin = new int[numSynchs];
		for (i = 0; i < numSynchs; i++) {
			synchMin[i] = 0;
		}
		
if (DEBUG_TrSysDef) {
  System.out.println("m2mtbdd::translateSystemDefn @ Place 1, just before call of translateSystemDefnRec.");
  System.out.println("<M2__TranSystemDefnRecursive_COMMENCE>");
}
		// build system recursively (descend parse tree)
		sysDDs = translateSystemDefnRec(sys, synchMin);
		
if (DEBUG_TrSysDef) {
  System.out.println("</M2__TranSystemDefnRecursive_COMMENCE>");
  System.out.println("Now Back in m2mtbdd::translateSystemDefn @ Place 2. That means we have considered each synch, for every module.");
  System.out.println("We have NOW FINISHED topmost recursive call of translateSystemDefnRec.\n");
  System.out.println("Continuing with m2mtbdd::translateSystemDefn ...");
}

		// for the nondeterministic case, add extra mtbdd variables to encode nondeterminism
		if (modelType == ModelType.MDP) {
if (DEBUG_TrSysDef) System.out.println("@ CASE A - MDP case of translateSystemDefnRec. More work to do...");
			// need to make sure all parts have the same number of dd variables for nondeterminism
			// so we don't generate lots of extra nondeterministic choices
			// first compute max number of variables used
			max = sysDDs.ind.max;
System.out.println("  Considering each sysDDs.synchs[_].max, to see if it is > current 'max' of "+max);
			for (i = 0; i < numSynchs; i++) {
				if (sysDDs.synchs[i].max > max) {
					max = sysDDs.synchs[i].max;
System.out.println("     It was for i="+i+". Updating max to " + max);
				}
			}
if (DEBUG_TrSysDef) System.out.println("m2mtbdd::translateSystemDefn @ PLACE A2:  After considering all " + numSynchs + " synchs, Max is : " + max + " and sysDDs.ind.max is " + sysDDs.ind.max);
			// check independent bit has this many variables
			if (max > sysDDs.ind.max) {
if (DEBUG_TrSysDef) System.out.println("m2mtbdd::translateSystemDefn @ PLACE A2-i. - because max IS GREATER than sysDDs.ind.max");
				tmp = JDD.Constant(1);
				for (i = sysDDs.ind.max; i < max; i++) {
					v = ddChoiceVars[ddChoiceVars.length-i-1];
					JDD.Ref(v);
					tmp = JDD.And(tmp, JDD.Not(v));
				}
				sysDDs.ind.trans = JDD.Apply(JDD.TIMES, sysDDs.ind.trans, tmp);
				//JDD.Ref(tmp);
				//sysDDs.ind.rewards = JDD.Apply(JDD.TIMES, sysDDs.ind.rewards, tmp);
				sysDDs.ind.max = max;
			}
else if (DEBUG_TrSysDef) System.out.println("m2mtbdd::translateSystemDefn @ PLACE A2-ii. - no, max was NOT greater than sysDDs.ind.max, so next task...");

if (DEBUG_TrSysDef) System.out.println("m2mtbdd::translateSystemDefn @ PLACE A3-START.\n   About to check each of " + numSynchs + " synchs, has 'this many variables' ...");
			// check each synchronous bit has this many variables
			for (i = 0; i < numSynchs; i++) {
if (DEBUG_TrSysDef) System.out.println("      @ PLACE A3-i:   i is " + i + " out of " + (numSynchs-1) );
				if (max > sysDDs.synchs[i].max) {
if (DEBUG_TrSysDef) System.out.println("          @ PLACE A3-ii (the IF was true).");
					tmp = JDD.Constant(1);
					for (j = sysDDs.synchs[i].max; j < max; j++) {
if (DEBUG_TrSysDef) System.out.println("             @ PLACE A3-iii:    j is " + j);
						v = ddChoiceVars[ddChoiceVars.length-j-1];
						JDD.Ref(v);
						tmp = JDD.And(tmp, JDD.Not(v));
if (DEBUG_TrSysDef && tmp.equals(JDD.ZERO)) System.out.println("tmp is ZERO.");
					}
					sysDDs.synchs[i].trans = JDD.Apply(JDD.TIMES, sysDDs.synchs[i].trans, tmp);
					//JDD.Ref(tmp);
					//sysDDs.synchs[i].rewards = JDD.Apply(JDD.TIMES, sysDDs.synchs[i].rewards, tmp);
					sysDDs.synchs[i].max = max;
				}
else if (DEBUG_TrSysDef) System.out.println("          @ PLACE A3-iii (the if was FALSE, nothing to do for this 'i' value).");
			}
if (DEBUG_TrSysDef) System.out.println("m2mtbdd::translateSystemDefn @ Place A4");
			// now add in new mtbdd variables to distinguish between actions
			// independent bit
			tmp = JDD.Constant(1);
			for (i = 0; i < numSynchs; i++) {
if (DEBUG_TrSysDef) System.out.println("      @ Place A4-i for i = " + i);
				tmp = JDD.And(tmp, JDD.Not(ddSynchVars[i].copy()));
if (DEBUG_TrSysDef && tmp.equals(JDD.ZERO)) System.out.println("tmp is ZERO.");

			}
			sysDDs.ind.trans = JDD.Apply(JDD.TIMES, tmp, sysDDs.ind.trans);
if (DEBUG_TrSysDef && sysDDs.ind.trans.equals(JDD.ZERO)) System.out.println("sysDDs.ind.trans is ZERO.");
			//JDD.Ref(tmp);
			//transRewards = JDD.Apply(JDD.TIMES, tmp, sysDDs.ind.rewards);

if (DEBUG_TrSysDef) System.out.println("m2mtbdd::translateSystemDefn @ Place A5");
			// synchronous bits
			for (i = 0; i < numSynchs; i++) {
				tmp = JDD.Constant(1);
				for (j = 0; j < numSynchs; j++) {
					if (j == i) {
						tmp = JDD.And(tmp, ddSynchVars[j].copy());
if (DEBUG_TrSysDef) System.out.println("        @ Place A5-i for synch #" + i + ", j = " + j);
					}
					else {
						tmp = JDD.And(tmp, JDD.Not(ddSynchVars[j].copy()));
					}
				}
				sysDDs.synchs[i].trans = JDD.Apply(JDD.TIMES, tmp, sysDDs.synchs[i].trans);
if (DEBUG_TrSysDef && sysDDs.synchs[i].trans.equals(JDD.ZERO)) System.out.println("sysDDs.synchs["+i+"].trans is ZERO.");
				//JDD.Ref(tmp);
				//transRewards = JDD.Apply(JDD.PLUS, transRewards, JDD.Apply(JDD.TIMES, tmp, sysDDs.synchs[i].rewards));
			}
if (DEBUG_TrSysDef) System.out.println("m2mtbdd::translateSystemDefn - End of CASE A.");
		}
else if (DEBUG_TrSysDef) System.out.println("CASE B - not mdp.");


if (DEBUG_TrSysDef) System.out.println("m2mtbdd::translateSystemDefn @ Place 3, about to call computeRewards()...");
		
		// build state and transition rewards
		computeRewards(sysDDs);
if (DEBUG_TrSysDef) System.out.println("m2mtbdd::translateSystemDefn @ Place 4, after computeRewards() has returned.");
		
		// now, for all model types, transition matrix can be built by summing over all actions
		// also build transition rewards at the same time
		n = modulesFile.getNumRewardStructs();
		trans = sysDDs.ind.trans.copy();
		for (j = 0; j < n; j++) {
			transRewards[j] = sysDDs.ind.rewards[j];
		}
		for (i = 0; i < numSynchs; i++) {
			trans = JDD.Apply(JDD.PLUS, trans, sysDDs.synchs[i].trans.copy());
			for (j = 0; j < n; j++) {
				transRewards[j] = JDD.Apply(JDD.PLUS, transRewards[j], sysDDs.synchs[i].rewards[j]);
			}
		}
if (DEBUG_ShowFinalTransDD) ShaneReportDD(trans,"~About trans (Possibly THE final transition matrix of the whole system)",true);
/*ALWAYS show the stats*/ ShaneReportDD(trans,"~About trans - stats alone",false);
if (DEBUG_TrSysDef) System.out.println("m2mtbdd::translateSystemDefn @ Place 5 - just a marker.");
		// For D/CTMCs, final rewards are scaled by dividing by total prob/rate for each transition
		// (when individual transition rewards are computed, they are multiplied by individual probs/rates).
		// Need to do this (for D/CTMCs) because transition prob/rate can be the sum of values from
		// several different actions; this gives us the "expected" reward for each transition.
		// (Note, for MDPs, nondeterministic choices are always kept separate so this never occurs.)
		if (modelType != ModelType.MDP) {
if (DEBUG_TrSysDef) System.out.println("m2mtbdd::translateSystemDefn @ Place 5-A");
			n = modulesFile.getNumRewardStructs();
			for (j = 0; j < n; j++) {
				transRewards[j] = JDD.Apply(JDD.DIVIDE, transRewards[j], trans.copy());
			}
		}
		
		// For MDPs, we take a copy of the DDs used to construct the part
		// of the transition matrix that corresponds to each action
		if (modelType == ModelType.MDP && storeTransParts) {
if (DEBUG_TrSysDef) System.out.println("m2mtbdd::translateSystemDefn @ Place 5-B");
			transInd = JDD.ThereExists(JDD.GreaterThan(sysDDs.ind.trans.copy(), 0), allDDColVars);
			transSynch = new JDDNode[numSynchs];
			for (i = 0; i < numSynchs; i++) {
				transSynch[i] = JDD.ThereExists(JDD.GreaterThan(sysDDs.synchs[i].trans.copy(), 0), allDDColVars);
			}
		}
		
		// If required, we also build MTBDD(s) to store the action labels for each transition.
		// The indexing of actions is as follows:
		// independent ("tau", non-action-labelled) transitions have index 0;
		// action-labelled transitions are 1-indexed using the ordering from the model file,
		// i.e. adding 1 to the list of actions from modulesFile.getSynchs().
		// What is actually stored differs for each model type.
		// For MDPs, we just store the action (index) for each state and nondet choice
		// (as an MTBDD 'transActions' over allDDRowVars and allDDNondetVars, with terminals giving index).  
		// For D/CTMCs, we have store to store a copy of the transition matrix for each action
		// (as numSynchs+1 MTBDDs 'transPerAction' over allDDRowVars/allDDColVars, with terminals giving prob/rate)  
		// because one global transition can come from several different actions.
		if (storeTransActions) {
if (DEBUG_TrSysDef) System.out.println("m2mtbdd::translateSystemDefn @ Place 6 - block for storeTransActions being done...");
			// Initialise storage to null so we know what we have used
			transActions = null;
			transPerAction = null;
			switch (modelType) {
			case MDP:
				transActions = JDD.Constant(0);
				// Don't need to store info for independent (action-less) transitions
				// as they are encoded as 0 anyway
				//JDD.Ref(sysDDs.ind.trans);
				//tmp = JDD.ThereExists(JDD.GreaterThan(sysDDs.ind.trans, 0), allDDColVars);
				//transActions = JDD.Apply(JDD.PLUS, transActions, JDD.Apply(JDD.TIMES, tmp, JDD.Constant(1)));
				for (i = 0; i < numSynchs; i++) {
					tmp = JDD.ThereExists(JDD.GreaterThan(sysDDs.synchs[i].trans.copy(), 0), allDDColVars);
if (DEBUG_TrSysDef) ShaneReportDD(tmp,"For synch " + i + " tmp is ",true);
					transActions = JDD.Apply(JDD.PLUS, transActions, JDD.Apply(JDD.TIMES, tmp, JDD.Constant(1+i)));
if (DEBUG_TrSysDef) ShaneReportDD(transActions,"After synch " + i + " transActions is ",true);
				}
				break;
			case DTMC:
			case CTMC:
				// Just reference DDs and copy them to new array
				transPerAction = new JDDNode[numSynchs + 1];
				transPerAction[0] = sysDDs.ind.trans.copy();
				for (i = 0; i < numSynchs; i++) {
					transPerAction[i + 1] = sysDDs.synchs[i].trans.copy();
				}
				break;
			}
		}
		
if (DEBUG_TrSysDef) System.out.println("m2mtbdd::translateSystemDefn @ Place 7 (Basically at the end, just dereferencing things before returning).");
		// deref bits of ComponentDD objects - we don't need them any more
		JDD.Deref(sysDDs.ind.guards);
		JDD.Deref(sysDDs.ind.trans);
		for (i = 0; i < numSynchs; i++) {
			JDD.Deref(sysDDs.synchs[i].guards);
			JDD.Deref(sysDDs.synchs[i].trans);
		}
		JDD.Deref(sysDDs.id);
System.out.println("In Mod2MTBDD.translateSystemDefn: Finished Translating the System.\n</TranslateSystemDefn>");
	}

	// recursive part of system composition (descend parse tree)
	
	private SystemDDs translateSystemDefnRec(SystemDefn sys, int[] synchMin) throws PrismException
	{
		SystemDDs sysDDs;
if (DEBUG_SHANE) {
mainLog.flush();
System.out.println("Invoked m2m_translateSystemDefnRec.");
Exception e = new Exception("Stack Trace ONLY");
e.printStackTrace(System.out);
System.out.flush();
}
		
		// determine type of current parse tree node
		// and pass to relevant method
		if (sys instanceof SystemModule) {
			sysDDs = translateSystemModule((SystemModule)sys, synchMin);
		}
		else if (sys instanceof SystemBrackets) {
			sysDDs = translateSystemDefnRec(((SystemBrackets)sys).getOperand(), synchMin);
		}
		else if (sys instanceof SystemFullParallel) {
			sysDDs = translateSystemFullParallel((SystemFullParallel)sys, synchMin);
		}
		else if (sys instanceof SystemInterleaved) {
			sysDDs = translateSystemInterleaved((SystemInterleaved)sys, synchMin);
		}
		else if (sys instanceof SystemParallel) {
			sysDDs = translateSystemParallel((SystemParallel)sys, synchMin);
		}
		else if (sys instanceof SystemHide) {
			sysDDs = translateSystemHide((SystemHide)sys, synchMin);
		}
		else if (sys instanceof SystemRename) {
			sysDDs = translateSystemRename((SystemRename)sys, synchMin);
		}
		else if (sys instanceof SystemReference) {
			String name = ((SystemReference) sys).getName();
			SystemDefn sysRef = modulesFile.getSystemDefnByName(name);
			if (sysRef == null)
				throw new PrismLangException("Reference to system " + sys + " which does not exist", sys);
			sysDDs = translateSystemDefnRec(sysRef, synchMin);
		}
		else {
			throw new PrismLangException("Unknown operator in model construction", sys);
		}
		
		return sysDDs;
	}

	// system composition (module)
	
	private SystemDDs translateSystemModule(SystemModule sys, int[] synchMin) throws PrismException
	{
		SystemDDs sysDDs;
		parser.ast.Module module;
		String synch;
		int i, m;

if (DEBUG_TraSysMod) {
	System.out.println("<TransSysMod module='" + sys.getName() + "'>\n");
	PrintDebugIndent();
	System.out.println("\nCommencing Modules2MTBDD.tranSysMod for module '" + sys.getName() + "'.");
	System.out.println("We will consider all " + numSynchs + " synchs, to see how they pertain to this module.");
	DebugIndent++;
}

		// create object to store result
		sysDDs = new SystemDDs(numSynchs);
		
if (DEBUG_TraSysMod) System.out.println("sys.getName() is '"+ sys.getName() + "'");
		// determine which module it is
		m = modulesFile.getModuleIndex(sys.getName());

		module = modulesFile.getModule(m);

if (DEBUG_TraSysMod || DEBUG_TransMod) {
	PrintDebugIndent();
	System.out.println("in Modules2MTBDD.tranSysMod Place 1, about to call translateModule() without synchs");
	PrintDebugIndent();
	System.out.println("<DealWithSynch synch=''>");
}

		// build mtbdd for independent bit
		sysDDs.ind = translateModule(m, module, "", 0);
if (DEBUG_TraSysMod || DEBUG_TransMod) {
	PrintDebugIndent();
	System.out.println("</DealWithSynch synch=''>\n");
}

if (DEBUG_TraSysMod) {		
	PrintDebugIndent();
	System.out.println("Back in Modules2MTBDD.tranSysMod, Place 2A for module " + sys.getName());
	PrintDebugIndent();
	System.out.println( "- will loop for this number of synchs: " + numSynchs); 
}
		// build mtbdd for each synchronising action
		for (i = 0; i < numSynchs; i++) {
			synch = synchs.elementAt(i);
if (DEBUG_TraSysMod || DEBUG_TransMod) {		
	System.out.println(); PrintDebugIndent(); System.out.println("-------------\n");
	PrintDebugIndent();
	System.out.println("in Modules2MTBDD.tranSysMod, Place 2B, Iteration " + (i+1) + "/" + numSynchs + " - will call translateModule for the module, for synch: " + synch); 
	PrintDebugIndent();
	System.out.println("<TrSysMod_DealWithSynch synch='"+synch+"' forModule='"+module.getName()+"'>");

DebugIndent++;
}
			sysDDs.synchs[i] = translateModule(m, module, synch, synchMin[i]);
if (DEBUG_TraSysMod || DEBUG_TransMod) {		
DebugIndent--;
	System.out.println();
	PrintDebugIndent();
	System.out.println("Back in Mod2MTBDD.tranSysMod:  Stored the result of translateModule("+m+","+module.getName()+","+synch+","+synchMin[i]+") into sysDDs.synchs["+i+"]") ;
	PrintDebugIndent();
	System.out.println("</TrSysMod_DealWithSynch synch='"+synch+"' forModule='"+module.getName()+"'>");
}

		}

if (DEBUG_TraSysMod) {		
	PrintDebugIndent();
	System.out.println("in Modules2MTBDD.tranSysMod, Place 4 - About to call copy() on moduleIdentities[m]"); 
}
		
		// store identity matrix
		sysDDs.id = moduleIdentities[m].copy();
		
if (DEBUG_TraSysMod) {		
	PrintDebugIndent();
	System.out.println("in Modules2MTBDD.tranSysMod, Place 5 - About to call sysDDs.allSynchs.addAll using the result of module.getAllSynchs()"); 
}
		// store synchs used
		sysDDs.allSynchs.addAll(module.getAllSynchs());

DebugIndent--;
PrintDebugIndent();
System.out.println("Concluding Modules2MTBDD.tranSysMod for  " + sys.getName());
System.out.println("That means, we have considered ALL synchs of the whole system, to see how they pertain to module '"+sys.getName()+"'");
System.out.println("We are returning sysDDs that was constructed by this call of tranSysMod.");
System.out.println("</TransSysMod>\n\n##########\n");

		return sysDDs;
	}

	// system composition (full parallel)
	
	private SystemDDs translateSystemFullParallel(SystemFullParallel sys, int[] synchMin) throws PrismException
	{
		SystemDDs sysDDs1, sysDDs2, sysDDs;
		int[] newSynchMin;
		int i, j;
if (DEBUG_SHANE) System.out.println("Invoked m2m_translateSystemFullParallel");		
		// construct mtbdds for first operand
		sysDDs = translateSystemDefnRec(sys.getOperand(0), synchMin);
		
		// loop through all other operands in the parallel operator
		for (i = 1; i < sys.getNumOperands(); i++) {
		
			// change min to max for potentially synchronising actions
			// store this in new array - old one may still be used elsewhere
			newSynchMin = new int[numSynchs];
			for (j = 0; j < numSynchs; j++) {
				if (sysDDs.allSynchs.contains(synchs.get(j))) {
					newSynchMin[j] = sysDDs.synchs[j].max;
				}
				else {
					newSynchMin[j] = synchMin[j];
				}
			}
			
			// construct mtbdds for next operand
			sysDDs2 = translateSystemDefnRec(sys.getOperand(i), newSynchMin);
			// move sysDDs (operands composed so far) into sysDDs1
			sysDDs1 = sysDDs;
			// we are going to combine sysDDs1 and sysDDs2 and put the result into sysDDs
			sysDDs = new SystemDDs(numSynchs);
			
			// combine mtbdds for independent bit
			sysDDs.ind = translateNonSynchronising(sysDDs1.ind, sysDDs2.ind, sysDDs1.id, sysDDs2.id);
			
			// combine mtbdds for each synchronising action
			for (j = 0; j < numSynchs; j++) {
				// if one operand does not use this action,
				// do asynchronous parallel composition
				if ((sysDDs1.allSynchs.contains(synchs.get(j))?1:0) + (sysDDs2.allSynchs.contains(synchs.get(j))?1:0) == 1) {
					sysDDs.synchs[j] = translateNonSynchronising(sysDDs1.synchs[j], sysDDs2.synchs[j], sysDDs1.id, sysDDs2.id);
				}
				else {
					sysDDs.synchs[j] = translateSynchronising(sysDDs1.synchs[j], sysDDs2.synchs[j]);
				}
			}
			
			// compute identity
			sysDDs.id = JDD.Apply(JDD.TIMES, sysDDs1.id, sysDDs2.id);
			
			// combine lists of synchs
			sysDDs.allSynchs.addAll(sysDDs1.allSynchs);
			sysDDs.allSynchs.addAll(sysDDs2.allSynchs);
		}
		
		return sysDDs;
	}

	// system composition (interleaved)
		
	private SystemDDs translateSystemInterleaved(SystemInterleaved sys, int[] synchMin) throws PrismException
	{
		SystemDDs sysDDs1, sysDDs2, sysDDs;
		int i, j;
	
		// construct mtbdds for first operand
		sysDDs = translateSystemDefnRec(sys.getOperand(0), synchMin);
		
		// loop through all other operands in the parallel operator
		for (i = 1; i < sys.getNumOperands(); i++) {
		
			// construct mtbdds for next operand
			sysDDs2 = translateSystemDefnRec(sys.getOperand(i), synchMin);
			// move sysDDs (operands composed so far) into sysDDs1
			sysDDs1 = sysDDs;
			// we are going to combine sysDDs1 and sysDDs2 and put the result into sysDDs
			sysDDs = new SystemDDs(numSynchs);
			
			// combine mtbdds for independent bit
			sysDDs.ind = translateNonSynchronising(sysDDs1.ind, sysDDs2.ind, sysDDs1.id, sysDDs2.id);
			
			// combine mtbdds for each synchronising action
			for (j = 0; j < numSynchs; j++) {
				sysDDs.synchs[j] = translateNonSynchronising(sysDDs1.synchs[j], sysDDs2.synchs[j], sysDDs1.id, sysDDs2.id);
			}
			
			// compute identity
			sysDDs.id = JDD.Apply(JDD.TIMES, sysDDs1.id, sysDDs2.id);
			
			// combine lists of synchs
			sysDDs.allSynchs.addAll(sysDDs1.allSynchs);
			sysDDs.allSynchs.addAll(sysDDs2.allSynchs);
		}
		
		return sysDDs;
	}

	// system composition (parallel over actions)
	
	private SystemDDs translateSystemParallel(SystemParallel sys, int[] synchMin) throws PrismException
	{
		SystemDDs sysDDs1, sysDDs2, sysDDs;
		boolean[] synchBool;
		int[] newSynchMin;
		int i;
		
		// go thru all synchronising actions and decide if we will synchronise on each one
		synchBool = new boolean[numSynchs];
		for (i = 0; i < numSynchs; i++) {
			synchBool[i] = sys.containsAction(synchs.elementAt(i));
		}
		
		// construct mtbdds for first operand
		sysDDs1 = translateSystemDefnRec(sys.getOperand1(), synchMin);
		
		// change min to max for synchronising actions
		// store this in new array - old one may still be used elsewhere
		newSynchMin = new int[numSynchs];
		for (i = 0; i < numSynchs; i++) {
			if (synchBool[i]) {
				newSynchMin[i] = sysDDs1.synchs[i].max;
			}
			else {
				newSynchMin[i] = synchMin[i];
			}
		}
		
		// construct mtbdds for second operand
		sysDDs2 = translateSystemDefnRec(sys.getOperand2(), newSynchMin);
		
		// create object to store mtbdds
		sysDDs = new SystemDDs(numSynchs);
		
		// combine mtbdds for independent bit
		sysDDs.ind = translateNonSynchronising(sysDDs1.ind, sysDDs2.ind, sysDDs1.id, sysDDs2.id);
		
		// combine mtbdds for each synchronising action
		for (i = 0; i < numSynchs; i++) {
			if (synchBool[i]) {
				sysDDs.synchs[i] = translateSynchronising(sysDDs1.synchs[i], sysDDs2.synchs[i]);
			}
			else {
				sysDDs.synchs[i] = translateNonSynchronising(sysDDs1.synchs[i], sysDDs2.synchs[i], sysDDs1.id, sysDDs2.id);
			}
		}
		
		// combine mtbdds for identity matrices
		sysDDs.id = JDD.Apply(JDD.TIMES, sysDDs1.id, sysDDs2.id);
		
		// combine lists of synchs
		sysDDs.allSynchs.addAll(sysDDs1.allSynchs);
		sysDDs.allSynchs.addAll(sysDDs2.allSynchs);
		
		return sysDDs;
	}
	
	// system composition (hide)
	
	private SystemDDs translateSystemHide(SystemHide sys, int[] synchMin) throws PrismException
	{
		SystemDDs sysDDs1, sysDDs;
		int[] newSynchMin;
		int i;
		
		// reset synchMin to 0 for actions to be hidden
		// store this in new array - old one may still be used elsewhere
		newSynchMin = new int[numSynchs];
		for (i = 0; i < numSynchs; i++) {
			if (sys.containsAction(synchs.elementAt(i))) {
				newSynchMin[i] = 0;
			}
			else {
				newSynchMin[i] = synchMin[i];
			}
		}
		
		// construct mtbdds for operand
		sysDDs1 = translateSystemDefnRec(sys.getOperand(), newSynchMin);
		
		// create object to store mtbdds for result
		sysDDs = new SystemDDs(numSynchs);
		
		// copy across independent bit
		sysDDs.ind = sysDDs1.ind;
		
		// go thru all synchronising actions
		for (i = 0; i < numSynchs; i++) {
			
			// if the action is in the set to be hidden, hide it...
			// note that it doesn't matter if an action is included more than once in the set
			// (although this would be picked up during the syntax check anyway)
			if (sys.containsAction(synchs.elementAt(i))) {
				
				// move these transitions into the independent bit
				sysDDs.ind = combineComponentDDs(sysDDs.ind, sysDDs1.synchs[i]);
				
				// create empty mtbdd for action
				sysDDs.synchs[i] = new ComponentDDs();
				sysDDs.synchs[i].guards = JDD.Constant(0);
				sysDDs.synchs[i].trans = JDD.Constant(0);
				//sysDDs.synchs[i].rewards = JDD.Constant(0);
				sysDDs.synchs[i].min = 0;
				sysDDs.synchs[i].max = 0;
			}
			// otherwise just copy it across
			else {
				sysDDs.synchs[i] = sysDDs1.synchs[i];
			}
		}
		
		// copy identity too
		sysDDs.id = sysDDs1.id;
		
		// modify list of synchs
		sysDDs.allSynchs.addAll(sysDDs1.allSynchs);
		for (i = 0; i < sys.getNumActions(); i++) {
			sysDDs.allSynchs.remove(sys.getAction(i));
		}
		
		return sysDDs;
	}

	// system composition (rename)
	
	private SystemDDs translateSystemRename(SystemRename sys, int[] synchMin) throws PrismException
	{
		SystemDDs sysDDs1, sysDDs;
		int[] newSynchMin;
		int i, j;
		String s;
		Iterator<String> iter;
		
		// swap some values in synchMin due to renaming
		// store this in new array - old one may still be used elsewhere
		newSynchMin = new int[numSynchs];
		for (i = 0; i < numSynchs; i++) {
			// find out what this action is renamed to
			// (this may be itself, i.e. it's not renamed)
			s = sys.getNewName(synchs.elementAt(i));
			j = synchs.indexOf(s);
			if (j == -1) {
				throw new PrismLangException("Invalid action name \"" + s + "\" in renaming", sys);
			}
			newSynchMin[i] = synchMin[j];
		}
		
		// construct mtbdds for operand
		sysDDs1 = translateSystemDefnRec(sys.getOperand(), newSynchMin);
		
		// create object to store mtbdds for result
		sysDDs = new SystemDDs(numSynchs);
		
		// copy across independent bit
		sysDDs.ind = sysDDs1.ind;
		
		// initially there are no mtbdds in result
		for (i = 0; i < numSynchs; i++) {
			sysDDs.synchs[i] = new ComponentDDs();
			sysDDs.synchs[i].guards = JDD.Constant(0);
			sysDDs.synchs[i].trans = JDD.Constant(0);
			//sysDDs.synchs[i].rewards = JDD.Constant(0);
			sysDDs.synchs[i].min = 0;
			sysDDs.synchs[i].max = 0;
		}
		
		// go thru all synchronising actions
		for (i = 0; i < numSynchs; i++) {
		
			// find out what this action is renamed to
			// (this may be itself, i.e. it's not renamed)
			// then add it to result
			s = sys.getNewName(synchs.elementAt(i));
			j = synchs.indexOf(s);
			if (j == -1) {
				throw new PrismLangException("Invalid action name \"" + s + "\" in renaming", sys);
			}
			sysDDs.synchs[j] = combineComponentDDs(sysDDs.synchs[j], sysDDs1.synchs[i]);
		}
		
		// copy identity too
		sysDDs.id = sysDDs1.id;
		
		// modify list of synchs
		iter = sysDDs1.allSynchs.iterator();
		while (iter.hasNext()) {
			sysDDs.allSynchs.add(sys.getNewName(iter.next()));
		}
		
		return sysDDs;
	}

	private ComponentDDs translateSynchronising(ComponentDDs compDDs1, ComponentDDs compDDs2) throws PrismException
	{
		ComponentDDs compDDs;
		
		// create object to store result
		compDDs = new ComponentDDs();
		
		// combine parts synchronously
		// first guards
		JDD.Ref(compDDs1.guards);
		JDD.Ref(compDDs2.guards);
		compDDs.guards = JDD.And(compDDs1.guards, compDDs2.guards);
		// then transitions
		JDD.Ref(compDDs1.trans);
		JDD.Ref(compDDs2.trans);
		compDDs.trans = JDD.Apply(JDD.TIMES, compDDs1.trans, compDDs2.trans);
		// then transition rewards
		//JDD.Ref(compDDs2.trans);
		//compDDs1.rewards = JDD.Apply(JDD.TIMES, compDDs1.rewards, JDD.GreaterThan(compDDs2.trans, 0));
		//JDD.Ref(compDDs1.trans);
		//compDDs2.rewards = JDD.Apply(JDD.TIMES, compDDs2.rewards, JDD.GreaterThan(compDDs1.trans, 0));
		//JDD.Ref(compDDs1.rewards);
		//JDD.Ref(compDDs2.rewards);
		//compDDs.rewards = JDD.Apply(JDD.PLUS, compDDs1.rewards, compDDs2.rewards);
		// compute new min/max
		compDDs.min = (compDDs1.min < compDDs2.min) ? compDDs1.min : compDDs2.min;
		compDDs.max = (compDDs1.max > compDDs2.max) ? compDDs1.max : compDDs2.max;
		
		// deref old stuff
		JDD.Deref(compDDs1.guards);
		JDD.Deref(compDDs2.guards);
		JDD.Deref(compDDs1.trans);
		JDD.Deref(compDDs2.trans);
		//JDD.Deref(compDDs1.rewards);
		//JDD.Deref(compDDs2.rewards);
		
		return compDDs;
	}

	private ComponentDDs translateNonSynchronising(ComponentDDs compDDs1, ComponentDDs compDDs2, JDDNode id1, JDDNode id2) throws PrismException
	{
		ComponentDDs compDDs;
		
		// add identities to mtbdds for transitions
		JDD.Ref(id2);
		compDDs1.trans = JDD.Apply(JDD.TIMES, compDDs1.trans, id2);
		JDD.Ref(id1);
		compDDs2.trans = JDD.Apply(JDD.TIMES, compDDs2.trans, id1);
		// add identities to mtbdds for transition rewards
		//JDD.Ref(id2);
		//compDDs1.rewards = JDD.Apply(JDD.TIMES, compDDs1.rewards, id2);
		//JDD.Ref(id1);
		//compDDs2.rewards = JDD.Apply(JDD.TIMES, compDDs2.rewards, id1);
		
		// then combine...
		compDDs = combineComponentDDs(compDDs1, compDDs2);
		
		return compDDs;
	}

	private ComponentDDs combineComponentDDs(ComponentDDs compDDs1, ComponentDDs compDDs2) throws PrismException
	{
		ComponentDDs compDDs;
		JDDNode tmp, v;
		int i;
		
		// create object to store result
		compDDs = new ComponentDDs();
		
		// if no nondeterminism - just add
		if (modelType != ModelType.MDP) {
			compDDs.guards = JDD.Or(compDDs1.guards, compDDs2.guards);
			compDDs.trans = JDD.Apply(JDD.PLUS, compDDs1.trans, compDDs2.trans);
			//compDDs.rewards = JDD.Apply(JDD.PLUS, compDDs1.rewards, compDDs2.rewards);
			compDDs.min = 0;
			compDDs.max = 0;
		}
		// if there's nondeterminism, but one part is empty, it's also easy
		else if (compDDs1.trans.equals(JDD.ZERO)) {
			JDD.Deref(compDDs1.guards);
			compDDs.guards = compDDs2.guards;
			JDD.Deref(compDDs1.trans);
			compDDs.trans = compDDs2.trans;
			//JDD.Deref(compDDs1.rewards);
			//compDDs.rewards = compDDs2.rewards;
			compDDs.min = compDDs2.min;
			compDDs.max = compDDs2.max;
		}
		else if (compDDs2.trans.equals(JDD.ZERO)) {
			JDD.Deref(compDDs2.guards);
			compDDs.guards = compDDs1.guards;
			JDD.Deref(compDDs2.trans);
			compDDs.trans = compDDs1.trans;
			//JDD.Deref(compDDs2.rewards);
			//compDDs.rewards = compDDs1.rewards;
			compDDs.min = compDDs1.min;
			compDDs.max = compDDs1.max;
		}
		// otherwise, it's a bit more complicated...
		else {
			// make sure two bits have the same number of dd variables for nondeterminism
			// (so we don't generate lots of extra nondeterministic choices)
			if (compDDs1.max > compDDs2.max) {
				tmp = JDD.Constant(1);
				for (i = compDDs2.max; i < compDDs1.max; i++) {
					v = ddChoiceVars[ddChoiceVars.length-i-1];
					JDD.Ref(v);
					tmp = JDD.And(tmp, JDD.Not(v));
				}
				compDDs2.trans = JDD.Apply(JDD.TIMES, compDDs2.trans, tmp);
				//JDD.Ref(tmp);
				//compDDs2.rewards = JDD.Apply(JDD.TIMES, compDDs2.rewards, tmp);
				compDDs2.max = compDDs1.max;
			}
			else if (compDDs2.max > compDDs1.max) {
				tmp = JDD.Constant(1);
				for (i = compDDs1.max; i < compDDs2.max; i++) {
					v = ddChoiceVars[ddChoiceVars.length-i-1];
					JDD.Ref(v);
					tmp = JDD.And(tmp, JDD.Not(v));
				}
				compDDs1.trans = JDD.Apply(JDD.TIMES, compDDs1.trans, tmp);
				//JDD.Ref(tmp);
				//compDDs1.rewards = JDD.Apply(JDD.TIMES, compDDs1.rewards, tmp);
				compDDs1.max = compDDs2.max;
			}
			// and then combine
			if (ddChoiceVars.length-compDDs1.max-1 < 0)
				throw new PrismException("Insufficient BDD variables allocated for nondeterminism - please report this as a bug. Thank you");
			v = ddChoiceVars[ddChoiceVars.length-compDDs1.max-1];
			compDDs.guards = JDD.Or(compDDs1.guards, compDDs2.guards);
			JDD.Ref(v);
			compDDs.trans = JDD.ITE(v, compDDs2.trans, compDDs1.trans);
			//JDD.Ref(v);
			//compDDs.rewards = JDD.ITE(v, compDDs2.rewards, compDDs1.rewards);
			compDDs.min = compDDs1.min;
			compDDs.max = compDDs1.max+1;
		}
		
		return compDDs;
	}

// ADDED BY SHANE
/** This method will generate (by recursion if required), all the possible versions of the command with the restriction variables set to explicit values.
 *  For all RestrictedScopeExpressions which are deemed to be out of scope by the value of a restriction variable, the 'default' expression will be substituted,
 *  but for all RestrictedScopeExpressions which are deemed to be in scope by the value, the 'underlying' expression will be substituted with each possible
 *  value of the variable, leading to a set of commands which 'realize' the original command.
 * 
 *
 */
private List<Command> generateCommandVersions(Command theCommand, List<Values> restrictionValCombins)
{
	ArrayList<Command> generatedCommands = new ArrayList<Command>();
	ResolveRestrictedScopes rrs = new ResolveRestrictedScopes(constantValues,varList);
	Command resolvedVersion = null;

	for (Values substitutions : restrictionValCombins)
	{
System.out.println("Generating version of command: " + theCommand + "\n using these values affecting restriction scopes: " + substitutions);

		for (int i = 0; i < substitutions.getNumValues(); i++)
		{
			String varNameToUse = substitutions.getName(i);
			int valToUse = (int) ((Integer)substitutions.getValue(i));
			int indexInVarList = varList.getIndex(varNameToUse);

			ExpressionVar theVar = 	new ExpressionVar( varNameToUse, varList.getType(indexInVarList));
			theVar.setIndex(indexInVarList);		// SHANE HOPES THAT IS CORRECT - otherwise, have to find out appropriate value to use.
//if (DEBUG_TransMod) System.out.println("for variable " + varNameToUse + ", create ExprVar with type set to " + varList.getType(indexInVarList)");
			ExpressionLiteral theVal = new ExpressionLiteral(varList.getType(indexInVarList),valToUse);

		}

		rrs.setValuesForResolution(substitutions);		// What to use for evaluation of restriction expressions
		try {
			resolvedVersion = (Command) rrs.visit( (Command) theCommand.deepCopy() );	// Resolve for current values
			generatedCommands.add(resolvedVersion);					// Add to list we will return.


		} catch (PrismLangException ple) { 
System.out.println("An exception arose: " + ple);
ple.printStackTrace(System.out);
		}
	}

	return generatedCommands;
}

// ADDED BY SHANE
/** This method will generate, by recursion, all the possible combinations of values for the variables whose indexes
    are in the supplied first parameter. It is blind as to whether any of the combinations are fit-for-purpose - that is
    left to the checkValidity() method which should be given the result of the top-level call to THIS method.
    The 'template' parameter is used to lock other variables whilst the current front one is used to generate enumerations.
    It will return all the enumerations that are possible for the variables of the first parameter, but with the template
    values locked.
    In short, it does not seek to eliminate any combinations.
*/
private List<Values> getEnumeratedCombinations(
   ArrayList<Integer> varIdxsToEnumerate,
   Values template
)
{
	int low, high, curValForVar;
	String vName;
	List<Values> resultsFromRecurse, resultsToGiveBack = null;

	if (template == null)
	   template = new Values();		// Just make a new empty one.

	if (varIdxsToEnumerate != null) { 
		// Create clones, to remove the front item from and then pass the remainder to recursive call...
		ArrayList<Integer> remainingVarIdxs = (ArrayList<Integer>)varIdxsToEnumerate.clone();

		int idxOfCurVar = remainingVarIdxs.remove(0);

		// get some info on the variable
		vName = varList.getName(idxOfCurVar);
		low = varList.getLow(idxOfCurVar);
		high = varList.getHigh(idxOfCurVar);
if (DEBUG_RecurseVars) System.out.println("in getEnumeratedCombinations, front variable is: " + vName);
System.out.println(" the range of its values is from " + low + " to " + high);

		// Prepare the results from this call, which will be generated by enumeration of possible values for current variable...
		resultsToGiveBack = new ArrayList<Values>();

		// Enumerate the possible values, and translate the update for them...
		for (curValForVar = low; curValForVar <= high; curValForVar++) {
			Values valsToSubstitute = template.clone();		// Using the variables as defined by receive parameter.
			valsToSubstitute.addValue(vName,new Integer(curValForVar));	// we will now add this variable, with current value.
			if (remainingVarIdxs.size() > 0) {	// If there are more variables, then need to do a recursive call	
if (DEBUG_RecurseVars) System.out.println("Will set " + vName + " to be " + curValForVar + ", and now calculate combinations for other variables...");
				resultsFromRecurse = getEnumeratedCombinations(remainingVarIdxs, valsToSubstitute);
				if (resultsFromRecurse != null & resultsFromRecurse.size() > 0)
				  resultsToGiveBack.addAll(resultsFromRecurse);
if (DEBUG_RecurseVars) System.out.println("Back in getEnumeratedCombinations for front-variable of: " + vName + ", received " + resultsFromRecurse.size() + " values from recursive call.");
			} else {		// BASE CASE - no other variables to recurse into
				resultsToGiveBack.add(valsToSubstitute);
if (DEBUG_RecurseVars) System.out.println(" Will set " + vName + " to be " + curValForVar + ", and return it alone (Base Case).");
			//EvaluateContextValues evalContext = new EvaluateContextValues(valsToSubstitute.clone());
			}
		}
if (DEBUG_RecurseVars) System.out.println("Ending getEnumeratedCombinations for front-variable of: " + vName + "\n");
	}
	return resultsToGiveBack;

}


// ADDED BY SHANE
/** Takes a list of Values specifying an exhaustive range of combinations to be tried for validity against the Set of
    ExpressionIndexedSetAccess expressions. Any that fail to match to a valid variable name, will be discarded from the
    list that is returned.
 */
private List<Values> filterCombinationsForAccessExprs(List<Values> subsCombins, Set<ExpressionIndexedSetAccess> EISAs_toCheck)
{
	List<Values> keptCombinations = new ArrayList<Values>();
	boolean keepCombination;
	int indexToUse = 0;
	List<Expression> restrExprs;

	// If there are no EISAs to be checked, there is nothing to do, so just returned what we received.
	if (EISAs_toCheck == null || EISAs_toCheck.size() == 0)
	    return subsCombins;

	// Don't proceed if no substitutions were provided - just give back what we received
	if (subsCombins == null || subsCombins.size() == 0)
	    return subsCombins;

	for (Values curSubValues : subsCombins)
	{
		keepCombination = true;		// We assume the combination is worthy of inclusion, and seek to prove otherwise...
if (DEBUG_ChkRstr) {
  System.out.println("\nNow considering these proposed substitutions: " + curSubValues + "\nhave reset the keepCombination to true");
}
		// Consider each expression that accesses an indexed set, to see if it will be an invalid access. If so,
		// then disregard the current curSubValues; otherwise, preserve it for returning.
		for (ExpressionIndexedSetAccess accExpr: EISAs_toCheck)
		{
			// Substitute the curSubValues into the curAccessExpression, and see if it resolves to a known variable:
if (DEBUG_ChkRstr) {
  System.out.println("\nConsidering access expression: " + accExpr + "\n for these values: " + curSubValues);
}

			restrExprs = accExpr.getRestrictionExpressions();
			if (restrExprs != null && restrExprs.size() > 0) {
if (DEBUG_ChkRstr) {
  System.out.println("\tIt has restriction expressions to apply: "); 
}
				for (Expression restrExpr : restrExprs)
				{
if (DEBUG_ChkRstr) {
  System.out.println("\t" + restrExprs);
}
				}

				// Consider each of the variables in the current set of substitution variables, to see how it might be restricted
				int i;
				for (i = 0; i < curSubValues.getNumValues(); i++)	
				if (keepCombination)		// Provided it has not been rejected on the basis of one of the other variables
				{
					String varName = curSubValues.getName(i);
					int varVal = 0;
					try { 
					    varVal = curSubValues.getIntValue(i);
					} catch (PrismLangException ple1) {
System.out.println("Utterly unexpected exception has arisen: ");
ple1.printStackTrace(System.out);
System.exit(1);
					}
  System.out.println("\t Considering which restrictions impact on variable: " + varName);
					List<ExpressionBinaryOp> relationsInvolvingVar = null;
					FindRelOpInvolvingVar visitor = new FindRelOpInvolvingVar(varName);
					Expression op2;
					int relOp, maybeBound = 0;

					for (Expression restrExpr : restrExprs)
					{
					    try {
						restrExpr.accept(visitor);
					    } catch (PrismLangException ple2) {
System.out.println("Utterly unexpected exception has arisen: ");
ple2.printStackTrace(System.out);
System.exit(1);
					    }
					}
					// the visitor now know all the expressions involving the variable on the left side of a relational expression.



					// Extract out all the expressions within the guard, that involve the variable specified by varName
					relationsInvolvingVar = visitor.getExpressionsThatInvolve();

if (DEBUG_ChkRstr && relationsInvolvingVar != null)
  System.out.println("There are this many restrictions that involve that variable: " + relationsInvolvingVar.size());

					if (relationsInvolvingVar != null && relationsInvolvingVar.size() > 0)
					{
if (DEBUG_ChkRstr) System.out.println("The following expressions may have an impact on " + varName + ": ");
for (Expression output : relationsInvolvingVar) System.out.println("  " + output + " may restrict the range of " + varName);
System.out.println();
						// Consider each expression that involves the variable
						for (ExpressionBinaryOp curExpr : relationsInvolvingVar) {
if (DEBUG_ChkRstr) System.out.println("\nConsidering impact on variable " + varName + " caused by this expression: " + curExpr);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("oper1 is " + curExpr.getOperand1() + ", oper2 is " + curExpr.getOperand2() ) ;
							op2 = curExpr.getOperand2();
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("Going to call evaluateInt on " + op2 );//+ " substituting this for variables: " + alreadyConsideredOnes);
							try {
								maybeBound = op2.evaluateInt(constantValues,(Values)null);
//								maybeBound = op2.evaluateInt(constantValues,alreadyConsideredOnes);  // ,(Values)null);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("[A] value returned from evaluateInt (called 'maybeBound' in code) is: " + maybeBound );
								// Based on the relational operator in use, see if the proposed substitution conforms. If not, reject.
								relOp = curExpr.getOperator();
								if (relOp == ExpressionBinaryOp.EQ)
								{
									if (varVal != maybeBound)		// Doesn't match current substitution proposal
{ if (DEBUG_ChkRstr_ExtraDetail) System.out.println("Excluded due to case 1");
									  keepCombination = false;		// So don't keep the proposed substitution.
}
								}
								if (relOp == ExpressionBinaryOp.NE)
								{
									if (varVal == maybeBound)		// Doesn't match current substitution proposal
{ if (DEBUG_ChkRstr_ExtraDetail) System.out.println("Excluded due to case 2");
									  keepCombination = false;		// So don't keep the proposed substitution.
}
								}
								if (relOp == ExpressionBinaryOp.LT)
								{
									if (varVal >= maybeBound)		// Doesn't match current substitution proposal
{ if (DEBUG_ChkRstr_ExtraDetail) System.out.println("Excluded due to case 3");
									  keepCombination = false;		// So don't keep the proposed substitution.
								}
}
								if (relOp == ExpressionBinaryOp.LE)
								{
									if (varVal > maybeBound)		// Doesn't match current substitution proposal
{ if (DEBUG_ChkRstr_ExtraDetail) System.out.println("Excluded due to case 4");
									  keepCombination = false;		// So don't keep the proposed substitution.
}
								}
								if (relOp == ExpressionBinaryOp.GT)
								{
System.out.println("Checking whether " + varVal + " > " + maybeBound);

									if (varVal <= maybeBound)		// Doesn't match current substitution proposal
{ if (DEBUG_ChkRstr_ExtraDetail) System.out.println("Excluded due to case 5");
									  keepCombination = false;		// So don't keep the proposed substitution.
}
								}
								if (relOp == ExpressionBinaryOp.GE)
								{
									if (varVal < maybeBound)		// Doesn't match current substitution proposal
{ if (DEBUG_ChkRstr_ExtraDetail) System.out.println("Excluded due to case 6");
									  keepCombination = false;		// So don't keep the proposed substitution.
}
								}
if (DEBUG_ChkRstr) System.out.println("value " + varVal + ( keepCombination ? " still fits criteria" : " fails criteria") );
							} catch (Exception tooHard) {
if (DEBUG_ChkRstr) System.out.println("SHANE - PROBLEM TO DEAL WITH, in Mod2MTBDD.filterCombinations4AE in the TooHard case of evaluating int:");
tooHard.printStackTrace(System.out);
System.exit(1);
							}
/*				 catch (PrismEvaluationException pee1) {
								
					String problemVarName = ((ExpressionVar)pee1.getASTElement()).getName();
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("First need to determine lower bound of: " + problemVarName);
					// Make a copy of the values already 'set' by prior calls.
					copyOfAlready = new Values(alreadyConsideredOnes);
					// set a value for the CURRENT variable, so we don't enter an infinite cycle of calls.
					copyOfAlready.addValue(varName, new Integer(lowerBound) );

					newLower = checkRestrictLowerBound( problemVarName,		// The name of the variable we want to tie-down.
						varList.getLow(varList.getIndex(problemVarName)),	// Its default lower bound
						guard,
						copyOfAlready
					);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("Back in prior call, we are dealing with this expression: " + op2);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("The lower bound of " + problemVarName + " we received back, ends up being " + newLower);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("So we will copy the original set of 'alreadyConsideredVariables', and insert " + problemVarName + " with value " + newLower + " and re-evaluate " + op2);
					// Now that we have a value resolved for the problem variable name, lets try again with it added to the list of alreadyOnes.
					copyOfAlready = new Values(alreadyConsideredOnes);		// Reset, no longer have the CURRENT variable in it.
					copyOfAlready.addValue(problemVarName,newLower);		// Insert the variable we have just tried to resolve.

					try {
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("\nRe-Considering impact on variable " + varName + " caused by this expression: " + curExpr);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("by calling evaluateInt on this expr: " + op2 + " using the following partial value assignments for variables: " + copyOfAlready);
						maybeBound = op2.evaluateInt(constantValues,copyOfAlready);  // ,(Values)null);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("[B] value returned from evaluateInt is: " + maybeBound);
						  lowerBound = maybeBound;	// So make the subsequent integer be the new lower bound.
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("after all that, lowerBound is: " + lowerBound);

						// If we get to here, maybe we ought to replace the "alreadyConsideredOnes" with the newer copyOfAlready???
					} catch (Exception tooHard) {
if (DEBUG_ChkRstr) System.out.println("SHANE - PROBLEM TO DEAL WITH, in Mod2MTBDD.checkRestrictLowerBound in the TooHard case [3]):");
tooHard.printStackTrace(System.out);
System.exit(1);
					}
				} catch (Exception ex1) {
if (DEBUG_ChkRstr) System.out.println("SHANE - PROBLEM TO DEAL WITH, in Mod2MTBDD.checkRestrictLowerBound:");
ex1.printStackTrace(System.out);
System.exit(1);
				}
*/
						} // End of what to do for each of the relations involving the variable, that is in restrictions
					} // End of if for whether there were any restricitions for the variable.
				} // End of block attached to the considering each of the variables involved in the access expression.

				if (keepCombination)			// If it hasn't been rejected by restrictions, let's now try substituting this combination
				{				// in to see if the resultant value is a valid index position...
				  try {
				    // Evaluate the expression to find the definite index to retrieve
				    indexToUse = accExpr.getIndexExpression().evaluateInt(constantValues, curSubValues);

if (DEBUG_ChkRstr){
   PrintDebugIndent(); System.out.println("The accessExpression : " + accExpr + " with the current proposed substitutions of " + curSubValues + " evaluates as " + indexToUse );
}
				    if (indexToUse < 0)
				    {
if (DEBUG_ChkRstr){
  PrintDebugIndent(); System.out.println("However, as the index is less than 0, this combination of values is being REMOVED from the set to return.");
}
					keepCombination = false;
					//DONT throw new PrismLangException("Invalid index given in indexed-set access attempt",accExpr);
					//RATHER: just throw this combination away.
				    } else {
					// construct the supposed name of the definitive variable to be accessed
					String hopedName = accExpr.getName() + "[" + indexToUse + "]";
if (DEBUG_ChkRstr){
	PrintDebugIndent(); System.out.println("\t The resultant exact variable (if it exists) will be: " + hopedName);
}

					// Check whether a variable by this name actually exists...
					int v = varList.getIndex(hopedName);
if (DEBUG_ChkRstr){
	PrintDebugIndent(); System.out.println("The determined position of that variable in varList is " + v);
}

					if (v == -1) 
					 	  keepCombination = false;
				    }
				  } catch (PrismOutOfBoundsException oobe) {
if (DEBUG_ChkRstr){
System.out.println("That combination would be outside the valid range of accessing the indexed set, so excluding this combination.");
}
					keepCombination = false;
				  } catch (PrismLangException ple) {
System.out.println("Unanticipated Exception occurred...");
ple.printStackTrace(System.out);
					keepCombination = false;		// If there was a problem, exclude that combination
				  }
				}
			}
else System.out.println(" - no restrictions specified");
		}
		// Based on verdicts, either keep or discard this combination of values. Any include=false will cause discard.
		if (keepCombination)
		  keptCombinations.add(curSubValues);

		if (keepCombination)
		   System.out.println("KEEPING: " + curSubValues);
		else System.out.println("EXCLUDING: " + curSubValues);
	}
	return keptCombinations;	// Could be empty, but cannot be null.
}

// ADDED BY SHANE BUT MAY NOT BE NEEDED, HENCE NOT ACTUALLY WRITTEN MUCH YET.
/**
  Receives a list of potential substitution combinations, for further filtering-out by considering the supplied Guard Expression for any restrictions
  the guard imposes, for example by a 'greater-than' constraint on a variable used in an access expression.
*/
private List<Values> filterCombinationsForGuard(List<Values> substitutionCombins, Expression guardExpr)
{
	for (Values curValues : substitutionCombins) {
		System.out.println("Considering whether the following values: " + curValues + "\n  might be ruled out by this guard: " + guardExpr);
System.out.println("\n*** NOT ACTUALLY IMPLEMENTED THAT YET ***\n");

/*
		int i;
		for (i = 0; i < curValues.getNumValues(); i++)
		try {
			String varName = curValues.getName(i);
			int varVal = curValues.getIntValue(i);

			// First, look for any terms in guard that constrain variable to a specific value, involving the variable:
//			int relOp, maybeBound, newLower, lowerBound;
			List<ExpressionBinaryOp> relationsInvolvingVar = null;
			FindRelOpInvolvingVar visitor = new FindRelOpInvolvingVar(varName);
			Expression op2;
			Values copyOfAlready;

//	if (alreadyConsideredOnes == null) throw new NullPointerException("alreadyConsideredOnes must be an instantiated Values object, even if empty.");

//if (DEBUG_ChkRstr) System.out.println("\n<CHK_RSTR>\ncheckRestrictLowerBound called for: " + varName);

//if (DEBUG_ChkRstr) if (alreadyConsideredOnes.getNumValues() > 0)
//  System.out.println("The following values are provided as alreadyConsideredOnes:" + alreadyConsideredOnes);
//else
//  System.out.println("No values are in alreadyConsideredOnes.");

//if (DEBUG_ChkRstr) System.out.println("\nThe current lower bound of " + varName + " is: " + curLowerBound);

//	lowerBound = curLowerBound;

//if (DEBUG_ChkRstr) System.out.println("Going to search (using Visitor) for any place it appears in the following guard: " + guard);

			try {
				guard.deepCopy().accept(visitor);		// Start searching.
			} catch (PrismLangException peIgnore) { };

			// Extract out all the expressions within the guard, that involve the variable specified by varName
			relationsInvolvingVar = visitor.getExpressionsThatInvolve(ExpressionBinaryOp.EQ);


			if (relationsInvolvingVar != null && relationsInvolvingVar.size() > 0)
			{
if (DEBUG_ChkRstr) System.out.println("The following expressions may have an impact on " + varName + " by having an EQUALS: ");
for (Expression output : relationsInvolvingVar) System.out.println("  " + output + " may restrict the range of " + varName);
System.out.println();
				// Consider each expression that involves the variable
				for (ExpressionBinaryOp curExpr : relationsInvolvingVar) {
if (DEBUG_ChkRstr) System.out.println("\nConsidering impact on variable " + varName + " caused by this expression: " + curExpr);
					relOp = curExpr.getOperator();
					if (relOp == ExpressionBinaryOp.EQ)
{
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("oper1 is " + curExpr.getOperand1() + ", oper2 is " + curExpr.getOperand2() ) ;
				op2 = curExpr.getOperand2();
				try {
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("Going to call evaluateInt on " + op2 );//+ " substituting this for variables: " + alreadyConsideredOnes);
					maybeBound = op2.evaluateInt(constantValues,(Values)null);
//					maybeBound = op2.evaluateInt(constantValues,alreadyConsideredOnes);  // ,(Values)null);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("[A] value returned from evaluateInt (called 'maybeBound' in code) is: " + maybeBound + "\nDOES it make sense for THAT to be the binding of variable " + varName + "?");
					  lowerBound = maybeBound;	// So make the subsequent integer be the new lower bound.
					if (maybeBound != varVal)		// The explicit value differs to the proposed substitution value
					   keepCombination = false;		// So don't keep the proposed substitution.


				} catch (PrismEvaluationException pee1) {
					String problemVarName = ((ExpressionVar)pee1.getASTElement()).getName();
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("First need to determine lower bound of: " + problemVarName);
					// Make a copy of the values already 'set' by prior calls.
					copyOfAlready = new Values(alreadyConsideredOnes);
					// set a value for the CURRENT variable, so we don't enter an infinite cycle of calls.
					copyOfAlready.addValue(varName, new Integer(lowerBound) );

					newLower = checkRestrictLowerBound( problemVarName,		// The name of the variable we want to tie-down.
						varList.getLow(varList.getIndex(problemVarName)),	// Its default lower bound
						guard,
						copyOfAlready
					);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("Back in prior call, we are dealing with this expression: " + op2);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("The lower bound of " + problemVarName + " we received back, ends up being " + newLower);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("So we will copy the original set of 'alreadyConsideredVariables', and insert " + problemVarName + " with value " + newLower + " and re-evaluate " + op2);
					// Now that we have a value resolved for the problem variable name, lets try again with it added to the list of alreadyOnes.
					copyOfAlready = new Values(alreadyConsideredOnes);		// Reset, no longer have the CURRENT variable in it.
					copyOfAlready.addValue(problemVarName,newLower);		// Insert the variable we have just tried to resolve.

					try {
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("\nRe-Considering impact on variable " + varName + " caused by this expression: " + curExpr);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("by calling evaluateInt on this expr: " + op2 + " using the following partial value assignments for variables: " + copyOfAlready);
						maybeBound = op2.evaluateInt(constantValues,copyOfAlready);  // ,(Values)null);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("[B] value returned from evaluateInt is: " + maybeBound);
						  lowerBound = maybeBound;	// So make the subsequent integer be the new lower bound.
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("after all that, lowerBound is: " + lowerBound);

						// If we get to here, maybe we ought to replace the "alreadyConsideredOnes" with the newer copyOfAlready???
					} catch (Exception tooHard) {
if (DEBUG_ChkRstr) System.out.println("SHANE - PROBLEM TO DEAL WITH, in Mod2MTBDD.checkRestrictLowerBound in the TooHard case [3]):");
tooHard.printStackTrace(System.out);
System.exit(1);
}
				} catch (Exception ex1) {
if (DEBUG_ChkRstr) System.out.println("SHANE - PROBLEM TO DEAL WITH, in Mod2MTBDD.checkRestrictLowerBound:");
ex1.printStackTrace(System.out);
System.exit(1);
				}
if (DEBUG_ChkRstr) System.out.println("END OF THE >=BLOCK");
			}

		} catch (PrismLangException ple) {
			// What to do if the intVal of currentValue can't be obtained?? Probably nothing. Should probably never arise.
		}
*/
	}

	return substitutionCombins;
}

// ADDED BY SHANE - BUT REPLACING IT BY THE PRECEDING FEW METHODS.
/** This method expects to be given a list containing the index to variables of varList, 
    and will take the front one, iterate over all its possible values by recursively calling 
    with the remainder of the list, to generate a list (returned) of Values objects which 
    contain actual values for each variable. The 'template' parameter is used to lock other
    variables whilst the current front one is enumerated. The initial call to start the recursion
    should provide an empty but initialised template. The 'lowerBounds' and 'upperBounds' parameters
    specify the ranges to be used for each variable, as determined by an analysis of other conditions
    present in the guard which may have been there to restrict overflow cases of formulas.
*/
private List<Values> recurseOnVars(
   ArrayList<Integer> varIdxsToRecurse, 
   ArrayList<Integer> lowerBounds, 
   ArrayList<Integer> upperBounds, 
   Values template)
{
	int low, high, curValForVar;
	String vName;
	List<Values> resultsFromRecurse, resultsToGiveBack = null;

	if (template == null)
	   template = new Values();		// Just make a new empty one.
if (DEBUG_RecurseVars) {
//System.out.println("In recurseOnVars, varIdxsToRecurse != null is : " + (varIdxsToRecurse != null) );
//System.out.println("In recurseOnVars, varIdxsToRecurse.size() is: " + varIdxsToRecurse.size() );
//System.out.println("In recurseOnVars, lowerBounds != null is : " + (lowerBounds != null) );
//System.out.println("In recurseOnVars, lowerBounds.size() is: " + lowerBounds.size() );
//System.out.println("In recurseOnVars, upperBounds != null is : " + (upperBounds != null) );
//System.out.println("In recurseOnVars, upperBounds.size() is: " + upperBounds.size() );
}
	if (varIdxsToRecurse != null && lowerBounds != null && upperBounds != null &&
	  varIdxsToRecurse.size() > 0 && lowerBounds.size() > 0 && upperBounds.size() > 0) {
		// Create clones, to remove the front item from and then pass the remainder to recursive call...
		ArrayList<Integer> remainingVarIdxs = (ArrayList<Integer>)varIdxsToRecurse.clone();
		ArrayList<Integer> remLowerBounds = (ArrayList<Integer>) lowerBounds.clone();
		ArrayList<Integer> remUpperBounds = (ArrayList<Integer>) upperBounds.clone();

		int idxOfCurVar = remainingVarIdxs.remove(0);

		// get some info on the variable
		vName = varList.getName(idxOfCurVar);
if (DEBUG_RecurseVars) System.out.println("in recurseOnVars, front variable is: " + vName);
		low = remLowerBounds.remove(0);
		high = remUpperBounds.remove(0);

		// Check there is no contradiction:
		if (low > high || high < low)
			throw new RuntimeException("Low exceeds High, when dealing with variable " + vName);
if (DEBUG_RecurseVars) System.out.println("its values are to be taken over the range from: " + low + " to " + high);

		// Prepare the results from this call, which will be generated by enumeration of possible values for current variable...
		resultsToGiveBack = new ArrayList<Values>();

		// Enumerate the possible values, and translate the update for them...
		for (curValForVar = low; curValForVar <= high; curValForVar++) {
			Values valsToSubstitute = template.clone();		// Using the variables as defined by receive parameter.
			valsToSubstitute.addValue(vName,new Integer(curValForVar));	// we will now add this variable, with current value.
			if (remainingVarIdxs.size() > 0) {	// If there are more variables, then need to do a recursive call	
if (DEBUG_RecurseVars) System.out.println("Will set " + vName + " to be " + curValForVar + ", and now calculate combinations for other variables...");
				resultsFromRecurse = recurseOnVars(remainingVarIdxs, remLowerBounds, remUpperBounds, valsToSubstitute);
				if (resultsFromRecurse != null & resultsFromRecurse.size() > 0)
				  resultsToGiveBack.addAll(resultsFromRecurse);
if (DEBUG_RecurseVars) System.out.println("Back in recurseOnVars for front-variable of: " + vName + ", received " + resultsFromRecurse.size() + " values from recursive call.");
			} else {
				resultsToGiveBack.add(valsToSubstitute);
if (DEBUG_RecurseVars) System.out.println("Will set " + vName + " to be " + curValForVar + ", and return it alone.");
			//EvaluateContextValues evalContext = new EvaluateContextValues(valsToSubstitute.clone());
			}
		}
if (DEBUG_RecurseVars) System.out.println("Ending recurseOnVars for front-variable of: " + vName + "\n");
	}
	return resultsToGiveBack;

}

// ADDED BY SHANE	- BUT MAYBE SHOULD BE MERGED INTO THE recurseOnVars
/** Purpose is to return a list where the value in position X is the lower-allowable value for variable whose index (into varList) appears in the same position X of the provided varIdxsToCheck argument.
 */	
private ArrayList<Integer> checkLowerBounds(List<Integer> varIdxsToCheck, Expression guardMayRestrict)
{
// Caller line says:		List<Integer> lowerBounds = checkLowerBounds(list_varsForAccessingIndSet, command.getGuard());
	int low, curValForVar, idxOfCurVar;
	String vName;
	ArrayList<Integer> lowerBounds = new ArrayList<Integer>();

	if (varIdxsToCheck == null)
	   return null;

	for (Integer curVarIdx : varIdxsToCheck) {
		idxOfCurVar = curVarIdx;

		// get some info on the variable
		vName = varList.getName(idxOfCurVar);
//System.out.println("in checkLowerBounds, current variable being considered is: " + vName);

		low = varList.getLow(idxOfCurVar);
System.out.println(" for " + vName + " the lower boundary value is usually: " + low + " but we will consider the following guard for any restriction to impose: " + guardMayRestrict);

		low = checkRestrictLowerBound(vName,low,guardMayRestrict.deepCopy(), new Values());		// Starts a Visitor to find bounds modifications
System.out.println(" will use a lower boundary of: " + low + " for " + vName);

		lowerBounds.add(low);		// Store the decided lower bound of variable		

	}
	return lowerBounds;
}

// ADDED BY SHANE	- BUT MAYBE SHOULD BE MERGED INTO THE recurseOnVars
/** Purpose is to return a list where the value in position X is the uppermost-allowable value for variable whose index (into varList) appears in the same position X of the provided varIdxsToCheck argument.
 */	
private ArrayList<Integer> checkUpperBounds(List<Integer> varIdxsToCheck, Expression guardMayRestrict)

{
	int high, idxOfCurVar;
	String vName;
	ArrayList<Integer> upperBounds = new ArrayList<Integer>();

	if (varIdxsToCheck == null)
	   return null;

	for (Integer curVarIdx : varIdxsToCheck) {
		idxOfCurVar = curVarIdx;

		// get some info on the variable
		vName = varList.getName(idxOfCurVar);
//System.out.println("in checkUpperBounds, current variable being considered is: " + vName);

		high = varList.getHigh(idxOfCurVar);
//System.out.println(" usually its Upper boundary value is: " + high + " but we will consider the following guard for any restriction to impose: " + guardMayRestrict);

		high = checkRestrictUpperBound(vName,high,guardMayRestrict.deepCopy(), new Values());		// Starts a Visitor to find bounds modifications
System.out.println(" will use a Upper boundary of: " + high + " for " + vName);

		upperBounds.add(high);		// Store the decided lower bound of variable		
	}
	return upperBounds;
}

// ADDED BY SHANE

/** Considers the expressions in the guard to check if the lowerBound for a given varName could be increased. Recursive. */
private int checkRestrictLowerBound(String varName, int curLowerBound, Expression guard, Values alreadyConsideredOnes) 
{
	int relOp, maybeBound, newLower, lowerBound;
	List<ExpressionBinaryOp> relationsInvolvingVar = null;
	FindRelOpInvolvingVar visitor = new FindRelOpInvolvingVar(varName);
	Expression op2;
	Values copyOfAlready;

	if (alreadyConsideredOnes == null) throw new NullPointerException("alreadyConsideredOnes must be an instantiated Values object, even if empty.");

if (DEBUG_ChkRstr) System.out.println("\n<CHK_RSTR>\ncheckRestrictLowerBound called for: " + varName);

if (DEBUG_ChkRstr) if (alreadyConsideredOnes.getNumValues() > 0)
  System.out.println("The following values are provided as alreadyConsideredOnes:" + alreadyConsideredOnes);
else
  System.out.println("No values are in alreadyConsideredOnes.");

if (DEBUG_ChkRstr) System.out.println("\nThe current lower bound of " + varName + " is: " + curLowerBound);

	lowerBound = curLowerBound;

if (DEBUG_ChkRstr) System.out.println("Going to search (using Visitor) for any place it appears in the following guard: " + guard);

	try {
		guard.deepCopy().accept(visitor);		// Start searching.
	} catch (PrismLangException peIgnore) { };

	// Extract out all the expressions within the guard, that involve the variable specified by varName
	relationsInvolvingVar = visitor.getExpressionsThatInvolve();


	if (relationsInvolvingVar != null && relationsInvolvingVar.size() > 0)
	{
if (DEBUG_ChkRstr) System.out.println("The following expressions may have an impact on " + varName + ": ");
for (Expression output : relationsInvolvingVar) System.out.println("  " + output + " may restrict the range of " + varName);
System.out.println();
		// Consider each expression that involves the variable
		for (ExpressionBinaryOp curExpr : relationsInvolvingVar) {
if (DEBUG_ChkRstr) System.out.println("\nConsidering impact on variable " + varName + " caused by this expression: " + curExpr);
			relOp = curExpr.getOperator();
			if (relOp == ExpressionBinaryOp.GT)
			{
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("Operator is >");
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("oper1 is " + curExpr.getOperand1() + ", oper2 is " + curExpr.getOperand2() ) ;
				op2 = curExpr.getOperand2();
				try {
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("Going to call evaluateInt on " + op2 + " substituting this for variables: " + alreadyConsideredOnes);
					maybeBound = 1+ op2.evaluateInt(constantValues,alreadyConsideredOnes);  // ,(Values)null);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("[C] value returned from evaluateInt (called 'maybeBound' in code) is: " + maybeBound + "\nDOES it make sense for THAT to become lowerBound??");
					if (maybeBound > lowerBound)	// The current 'maybeBound' value is more restrictive.
					  lowerBound = maybeBound;	// So make the subsequent integer be the new lower bound.
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("after all that, lowerBound for " + varName + " is: " + lowerBound);
				} catch (PrismEvaluationException pee1) {
					String problemVarName = ((ExpressionVar)pee1.getASTElement()).getName();
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("First need to determine lower bound of: " + problemVarName);
					// Make a copy of the values already 'set' by prior calls.
					copyOfAlready = new Values(alreadyConsideredOnes);
					// set a value for the CURRENT variable, so we don't enter an infinite cycle of calls.
					copyOfAlready.addValue(varName, new Integer(lowerBound) );

					newLower = checkRestrictLowerBound( problemVarName,		// The name of the variable we want to tie-down.
						varList.getLow(varList.getIndex(problemVarName)),	// Its default lower bound
						guard,
						copyOfAlready
					);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("Back in prior call, we are dealing with this expression: " + op2);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("The lower bound of " + problemVarName + " we received back, ends up being " + newLower);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("So we will copy the original set of 'alreadyConsideredVariables', and insert " + problemVarName + " with value " + newLower + " and re-evaluate " + op2);
					// Now that we have a value resolved for the problem variable name, lets try again with it added to the list of alreadyOnes.
					copyOfAlready = new Values(alreadyConsideredOnes);		// Reset, no longer have the CURRENT variable in it.
					copyOfAlready.addValue(problemVarName,newLower);		// Insert the variable we have just tried to resolve.

					try {
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("\nRe-Considering impact on variable " + varName + " caused by this expression: " + curExpr);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("by calling evaluateInt on this expr: " + op2 + " using the following partial value assignments for variables: " + copyOfAlready);
						maybeBound = 1 + op2.evaluateInt(constantValues,copyOfAlready);  // ,(Values)null);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("[D] value returned from evaluateInt is: " + maybeBound);
						if (maybeBound > lowerBound)	// The current 'maybeBound' value is more restrictive.
						  lowerBound = maybeBound;	// So make the subsequent integer be the new lower bound.
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("after all that, lowerBound is: " + lowerBound);

						// If we get to here, maybe we ought to replace the "alreadyConsideredOnes" with the newer copyOfAlready???
					} catch (Exception tooHard) {
if (DEBUG_ChkRstr) System.out.println("SHANE - PROBLEM TO DEAL WITH, in Mod2MTBDD.checkRestrictLowerBound in the TooHard case):");
tooHard.printStackTrace(System.out);
System.exit(1);
}
				} catch (Exception ex1) {
if (DEBUG_ChkRstr) System.out.println("SHANE - PROBLEM TO DEAL WITH, in Mod2MTBDD.checkRestrictLowerBound:");
ex1.printStackTrace(System.out);
System.exit(1);
				}
if (DEBUG_ChkRstr) System.out.println("END OF THE > BLOCK");
			}
			else if (relOp == ExpressionBinaryOp.GE)
			{
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("Operator is >=");
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("oper1 is " + curExpr.getOperand1() + ", oper2 is " + curExpr.getOperand2() ) ;
				op2 = curExpr.getOperand2();
				try {
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("Going to call evaluateInt on " + op2 + " substituting this for variables: " + alreadyConsideredOnes);
					maybeBound = op2.evaluateInt(constantValues,alreadyConsideredOnes);  // ,(Values)null);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("[A] value returned from evaluateInt (called 'maybeBound' in code) is: " + maybeBound + "\nDOES it make sense for THAT to become lowerBound??");
					if (maybeBound >= lowerBound)	// The current 'maybeBound' value is more restrictive.
					  lowerBound = maybeBound;	// So make the subsequent integer be the new lower bound.
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("after all that, lowerBound for " + varName + " is: " + lowerBound);
				} catch (PrismEvaluationException pee1) {
					String problemVarName = ((ExpressionVar)pee1.getASTElement()).getName();
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("First need to determine lower bound of: " + problemVarName);
					// Make a copy of the values already 'set' by prior calls.
					copyOfAlready = new Values(alreadyConsideredOnes);
					// set a value for the CURRENT variable, so we don't enter an infinite cycle of calls.
					copyOfAlready.addValue(varName, new Integer(lowerBound) );

					newLower = checkRestrictLowerBound( problemVarName,		// The name of the variable we want to tie-down.
						varList.getLow(varList.getIndex(problemVarName)),	// Its default lower bound
						guard,
						copyOfAlready
					);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("Back in prior call, we are dealing with this expression: " + op2);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("The lower bound of " + problemVarName + " we received back, ends up being " + newLower);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("So we will copy the original set of 'alreadyConsideredVariables', and insert " + problemVarName + " with value " + newLower + " and re-evaluate " + op2);
					// Now that we have a value resolved for the problem variable name, lets try again with it added to the list of alreadyOnes.
					copyOfAlready = new Values(alreadyConsideredOnes);		// Reset, no longer have the CURRENT variable in it.
					copyOfAlready.addValue(problemVarName,newLower);		// Insert the variable we have just tried to resolve.

					try {
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("\nRe-Considering impact on variable " + varName + " caused by this expression: " + curExpr);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("by calling evaluateInt on this expr: " + op2 + " using the following partial value assignments for variables: " + copyOfAlready);
						maybeBound = op2.evaluateInt(constantValues,copyOfAlready);  // ,(Values)null);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("[B] value returned from evaluateInt is: " + maybeBound);
						if (maybeBound > lowerBound)	// The current 'maybeBound' value is more restrictive.
						  lowerBound = maybeBound;	// So make the subsequent integer be the new lower bound.
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("after all that, lowerBound is: " + lowerBound);

						// If we get to here, maybe we ought to replace the "alreadyConsideredOnes" with the newer copyOfAlready???
					} catch (Exception tooHard) {
if (DEBUG_ChkRstr) System.out.println("SHANE - PROBLEM TO DEAL WITH, in Mod2MTBDD.checkRestrictLowerBound in the TooHard case):");
tooHard.printStackTrace(System.out);
System.exit(1);
}
				} catch (Exception ex1) {
if (DEBUG_ChkRstr) System.out.println("SHANE - PROBLEM TO DEAL WITH, in Mod2MTBDD.checkRestrictLowerBound:");
ex1.printStackTrace(System.out);
System.exit(1);
				}
if (DEBUG_ChkRstr) System.out.println("END OF THE >=BLOCK");
			}
			else if (relOp == ExpressionBinaryOp.EQ)
			{
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("*[Case 3]*  Operator is =");
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("oper1 is " + curExpr.getOperand1() + ", oper2 is " + curExpr.getOperand2() ) ;
				op2 = curExpr.getOperand2();
				try {
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("Going to call evaluateInt on " + op2 + " substituting this for variables: " + alreadyConsideredOnes);
					maybeBound = op2.evaluateInt(constantValues,alreadyConsideredOnes);  // ,(Values)null);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("[A] value returned from evaluateInt (called 'maybeBound' in code) is: " + maybeBound + "\nDOES it make sense for THAT to become lowerBound??");
					  lowerBound = maybeBound;	// So make the subsequent integer be the new lower bound.
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("after all that, lowerBound for " + varName + " is: " + lowerBound);
				} catch (PrismEvaluationException pee1) {
					String problemVarName = ((ExpressionVar)pee1.getASTElement()).getName();
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("First need to determine lower bound of: " + problemVarName);
					// Make a copy of the values already 'set' by prior calls.
					copyOfAlready = new Values(alreadyConsideredOnes);
					// set a value for the CURRENT variable, so we don't enter an infinite cycle of calls.
					copyOfAlready.addValue(varName, new Integer(lowerBound) );

					newLower = checkRestrictLowerBound( problemVarName,		// The name of the variable we want to tie-down.
						varList.getLow(varList.getIndex(problemVarName)),	// Its default lower bound
						guard,
						copyOfAlready
					);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("Back in prior call, we are dealing with this expression: " + op2);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("The lower bound of " + problemVarName + " we received back, ends up being " + newLower);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("So we will copy the original set of 'alreadyConsideredVariables', and insert " + problemVarName + " with value " + newLower + " and re-evaluate " + op2);
					// Now that we have a value resolved for the problem variable name, lets try again with it added to the list of alreadyOnes.
					copyOfAlready = new Values(alreadyConsideredOnes);		// Reset, no longer have the CURRENT variable in it.
					copyOfAlready.addValue(problemVarName,newLower);		// Insert the variable we have just tried to resolve.

					try {
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("\nRe-Considering impact on variable " + varName + " caused by this expression: " + curExpr);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("by calling evaluateInt on this expr: " + op2 + " using the following partial value assignments for variables: " + copyOfAlready);
						maybeBound = op2.evaluateInt(constantValues,copyOfAlready);  // ,(Values)null);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("[B] value returned from evaluateInt is: " + maybeBound);
						  lowerBound = maybeBound;	// So make the subsequent integer be the new lower bound.
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("after all that, lowerBound is: " + lowerBound);

						// If we get to here, maybe we ought to replace the "alreadyConsideredOnes" with the newer copyOfAlready???
					} catch (Exception tooHard) {
if (DEBUG_ChkRstr) System.out.println("SHANE - PROBLEM TO DEAL WITH, in Mod2MTBDD.checkRestrictLowerBound in the TooHard case [3]):");
tooHard.printStackTrace(System.out);
System.exit(1);
}
				} catch (Exception ex1) {
if (DEBUG_ChkRstr) System.out.println("SHANE - PROBLEM TO DEAL WITH, in Mod2MTBDD.checkRestrictLowerBound:");
ex1.printStackTrace(System.out);
System.exit(1);
				}
if (DEBUG_ChkRstr) System.out.println("END OF THE >=BLOCK");
			}
else if (DEBUG_ChkRstr) System.out.println("It is neither > nor >=, so it has NO impact; just returning the original lowerBound value.");
		}
	}
if (DEBUG_ChkRstr) System.out.println("\ncheckRestrictLowerBound ending  for: " + varName + " with value: " + lowerBound + "\n</CHK_RSTR>\n");
	return lowerBound;
}


/** Considers the expressions in the guard to check if the upperBound for a given varName could be decreased. Recursive. */
private int checkRestrictUpperBound(String varName, int curUpperBound, Expression guard, Values alreadyConsideredOnes) 
{
	int relOp, maybeBound, newUpper, upperBound;
	List<ExpressionBinaryOp> relationsInvolvingVar = null;
	FindRelOpInvolvingVar visitor = new FindRelOpInvolvingVar(varName);
	Expression op2;
	Values copyOfAlready;

	if (alreadyConsideredOnes == null) throw new NullPointerException("alreadyConsideredOnes must be an instantiated Values object, even if empty.");

if (DEBUG_ChkRstr) System.out.println("\n<CHK_RSTR>\ncheckRestrictUpperBound called for: " + varName);

if (DEBUG_ChkRstr) if (alreadyConsideredOnes.getNumValues() > 0)
  System.out.println("The following values are provided as alreadyConsideredOnes:" + alreadyConsideredOnes);
else
  System.out.println("No values are in alreadyConsideredOnes.");

if (DEBUG_ChkRstr) System.out.println("\nThe current upper bound of " + varName + " is: " + curUpperBound);

	upperBound = curUpperBound;

if (DEBUG_ChkRstr) System.out.println("Going to search (using Visitor) for any place it appears in the following guard: " + guard);

	try {
		guard.deepCopy().accept(visitor);		// Start searching.
	} catch (PrismLangException peIgnore) { };

	// Extract out all the expressions within the guard, that involve the variable specified by varName
	relationsInvolvingVar = visitor.getExpressionsThatInvolve();


	if (relationsInvolvingVar != null && relationsInvolvingVar.size() > 0)
	{
if (DEBUG_ChkRstr) System.out.println("The following expressions may have an impact on " + varName + ": ");
for (Expression output : relationsInvolvingVar) System.out.println("  " + output + " may restrict the range of " + varName);
System.out.println();
		// Consider each expression that involves the variable
		for (ExpressionBinaryOp curExpr : relationsInvolvingVar) {
if (DEBUG_ChkRstr) System.out.println("\nConsidering impact on variable " + varName + " caused by this expression: " + curExpr);
			relOp = curExpr.getOperator();
			if (relOp == ExpressionBinaryOp.LT)
			{
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("Operator is <");
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("oper1 is " + curExpr.getOperand1() + ", oper2 is " + curExpr.getOperand2() ) ;
				op2 = curExpr.getOperand2();
				try {
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("Going to call evaluateInt on " + op2 + " substituting this for variables: " + alreadyConsideredOnes);
					maybeBound = op2.evaluateInt(constantValues,alreadyConsideredOnes) - 1;
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("value returned from evaluateInt (called 'maybeBound' in code) is: " + maybeBound + "\nDOES it make sense for THAT to become upperBound??");
					if (maybeBound < upperBound)	// The current 'maybeBound' value is more restrictive.
					  upperBound = maybeBound;	// So make the subsequent integer be the new upper bound.
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("after all that, upperBound for " + varName + " is: " + upperBound);
				} catch (PrismEvaluationException pee1) {
					String problemVarName = ((ExpressionVar)pee1.getASTElement()).getName();
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("First need to determine upper bound of: " + problemVarName);
					// Make a copy of the values already 'set' by prior calls.
					copyOfAlready = new Values(alreadyConsideredOnes);
					// set a value for the CURRENT variable, so we don't enter an infinite cycle of calls.
					copyOfAlready.addValue(varName, new Integer(upperBound) );

					newUpper = checkRestrictUpperBound( problemVarName,		// The name of the variable we want to tie-down.
						varList.getHigh(varList.getIndex(problemVarName)),	// Its default upper bound
						guard,
						copyOfAlready
					);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("Back in prior call, we are dealing with this expression: " + op2);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("The upper bound of " + problemVarName + " we received back, ends up being " + newUpper);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("So we will copy the original set of 'alreadyConsideredVariables', and insert " + problemVarName + " with value " + newUpper + " and re-evaluate " + op2);
					// Now that we have a value resolved for the problem variable name, lets try again with it added to the list of alreadyOnes.
					copyOfAlready = new Values(alreadyConsideredOnes);		// Reset, no longer have the CURRENT variable in it.
					copyOfAlready.addValue(problemVarName,newUpper);		// Insert the variable we have just tried to resolve.

					try {
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("\nRe-Considering impact on variable " + varName + " caused by this expression: " + curExpr);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("by calling evaluateInt on this expr: " + op2 + " using the following partial value assignments for variables: " + copyOfAlready);
						maybeBound = op2.evaluateInt(constantValues,copyOfAlready) -1;
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("value returned from evaluateInt is: " + maybeBound);
						if (maybeBound < upperBound)	// The current 'maybeBound' value is more restrictive.
						  upperBound = maybeBound;	// So make the subsequent integer be the new upper bound.
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("after all that, upperBound is: " + upperBound);

						// If we get to here, maybe we ought to replace the "alreadyConsideredOnes" with the newer copyOfAlready???
					} catch (Exception tooHard) {
if (DEBUG_ChkRstr) System.out.println("SHANE - PROBLEM TO DEAL WITH, in Mod2MTBDD.checkRestrictUpperBound in the TooHard case):");
tooHard.printStackTrace(System.out);
System.exit(1);
}
				} catch (Exception ex1) {
if (DEBUG_ChkRstr) System.out.println("SHANE - PROBLEM TO DEAL WITH, in Mod2MTBDD.checkRestrictUpperBound:");
ex1.printStackTrace(System.out);
System.exit(1);
				}
			}
			else if (relOp == ExpressionBinaryOp.LE)
			{
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("Operator is <=");
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("oper1 is " + curExpr.getOperand1() + ", oper2 is " + curExpr.getOperand2() ) ;
				op2 = curExpr.getOperand2();
				try {
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("Going to call evaluateInt on " + op2 + " substituting this for variables: " + alreadyConsideredOnes);
					maybeBound = op2.evaluateInt(constantValues,alreadyConsideredOnes);  // ,(Values)null);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("value returned from evaluateInt (called 'maybeBound' in code) is: " + maybeBound + "\nDOES it make sense for THAT to become upperBound??");
					if (maybeBound < upperBound)	// The current 'maybeBound' value is more restrictive.
					  upperBound = maybeBound;	// So make the subsequent integer be the new upper bound.
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("after all that, upperBound for " + varName + " is: " + upperBound);
				} catch (PrismEvaluationException pee1) {
					String problemVarName = ((ExpressionVar)pee1.getASTElement()).getName();
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("First need to determine upper bound of: " + problemVarName);
					// Make a copy of the values already 'set' by prior calls.
					copyOfAlready = new Values(alreadyConsideredOnes);
					// set a value for the CURRENT variable, so we don't enter an infinite cycle of calls.
					copyOfAlready.addValue(varName, new Integer(upperBound) );

					newUpper = checkRestrictUpperBound( problemVarName,		// The name of the variable we want to tie-down.
						varList.getLow(varList.getIndex(problemVarName)),	// Its default upper bound
						guard,
						copyOfAlready
					);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("Back in prior call, we are dealing with this expression: " + op2);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("The upper bound of " + problemVarName + " we received back, ends up being " + newUpper);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("So we will copy the original set of 'alreadyConsideredVariables', and insert " + problemVarName + " with value " + newUpper + " and re-evaluate " + op2);
					// Now that we have a value resolved for the problem variable name, lets try again with it added to the list of alreadyOnes.
					copyOfAlready = new Values(alreadyConsideredOnes);		// Reset, no longer have the CURRENT variable in it.
					copyOfAlready.addValue(problemVarName,newUpper);		// Insert the variable we have just tried to resolve.

					try {
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("\nRe-Considering impact on variable " + varName + " caused by this expression: " + curExpr);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("by calling evaluateInt on this expr: " + op2 + " using the following partial value assignments for variables: " + copyOfAlready);
						maybeBound = op2.evaluateInt(constantValues,copyOfAlready);  // ,(Values)null);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("value returned from evaluateInt is: " + maybeBound);
						if (maybeBound < upperBound)	// The current 'maybeBound' value is more restrictive.
						  upperBound = maybeBound;	// So make the subsequent integer be the new upper bound.
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("after all that, upperBound is: " + upperBound);

						// If we get to here, maybe we ought to replace the "alreadyConsideredOnes" with the newer copyOfAlready???
					} catch (Exception tooHard) {
if (DEBUG_ChkRstr) System.out.println("SHANE - PROBLEM TO DEAL WITH, in Mod2MTBDD.checkRestrictUpperBound in the TooHard case):");
tooHard.printStackTrace(System.out);
System.exit(1);
}
				} catch (Exception ex1) {
if (DEBUG_ChkRstr) System.out.println("SHANE - PROBLEM TO DEAL WITH, in Mod2MTBDD.checkRestrictUpperBound:");
ex1.printStackTrace(System.out);
System.exit(1);
				}
			}
			else if (relOp == ExpressionBinaryOp.EQ)
			{
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("Operator is =");
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("oper1 is " + curExpr.getOperand1() + ", oper2 is " + curExpr.getOperand2() ) ;
				op2 = curExpr.getOperand2();
				try {
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("Going to call evaluateInt on " + op2 + " substituting this for variables: " + alreadyConsideredOnes);
					maybeBound = op2.evaluateInt(constantValues,alreadyConsideredOnes);  // ,(Values)null);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("value returned from evaluateInt (called 'maybeBound' in code) is: " + maybeBound + "\nDOES it make sense for THAT to become upperBound??");
					  upperBound = maybeBound;	// So make the subsequent integer be the new upper bound.
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("after all that, upperBound for " + varName + " is: " + upperBound);
				} catch (PrismEvaluationException pee1) {
					String problemVarName = ((ExpressionVar)pee1.getASTElement()).getName();
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("First need to determine upper bound of: " + problemVarName);
					// Make a copy of the values already 'set' by prior calls.
					copyOfAlready = new Values(alreadyConsideredOnes);
					// set a value for the CURRENT variable, so we don't enter an infinite cycle of calls.
					copyOfAlready.addValue(varName, new Integer(upperBound) );

					newUpper = checkRestrictUpperBound( problemVarName,		// The name of the variable we want to tie-down.
						varList.getLow(varList.getIndex(problemVarName)),	// Its default upper bound
						guard,
						copyOfAlready
					);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("Back in prior call, we are dealing with this expression: " + op2);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("The upper bound of " + problemVarName + " we received back, ends up being " + newUpper);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("So we will copy the original set of 'alreadyConsideredVariables', and insert " + problemVarName + " with value " + newUpper + " and re-evaluate " + op2);
					// Now that we have a value resolved for the problem variable name, lets try again with it added to the list of alreadyOnes.
					copyOfAlready = new Values(alreadyConsideredOnes);		// Reset, no longer have the CURRENT variable in it.
					copyOfAlready.addValue(problemVarName,newUpper);		// Insert the variable we have just tried to resolve.

					try {
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("\nRe-Considering impact on variable " + varName + " caused by this expression: " + curExpr);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("by calling evaluateInt on this expr: " + op2 + " using the following partial value assignments for variables: " + copyOfAlready);
						maybeBound = op2.evaluateInt(constantValues,copyOfAlready);  // ,(Values)null);
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("value returned from evaluateInt is: " + maybeBound);
						  upperBound = maybeBound;	// So make the subsequent integer be the new upper bound.
if (DEBUG_ChkRstr_ExtraDetail) System.out.println("after all that, upperBound is: " + upperBound);

						// If we get to here, maybe we ought to replace the "alreadyConsideredOnes" with the newer copyOfAlready???
					} catch (Exception tooHard) {
if (DEBUG_ChkRstr) System.out.println("SHANE - PROBLEM TO DEAL WITH, in Mod2MTBDD.checkRestrictUpperBound in the TooHard case [3]):");
tooHard.printStackTrace(System.out);
System.exit(1);
}
				} catch (Exception ex1) {
if (DEBUG_ChkRstr) System.out.println("SHANE - PROBLEM TO DEAL WITH, in Mod2MTBDD.checkRestrictUpperBound:");
ex1.printStackTrace(System.out);
System.exit(1);
				}
			}
else if (DEBUG_ChkRstr) System.out.println("It is neither < nor <=, so it has NO impact; just returning the original upperBound value.");
		}
	}
if (DEBUG_ChkRstr) System.out.println("\ncheckRestrictUpperBound ending  for: " + varName + " with value: " + upperBound + "\n</CHK_RSTR>\n");
	return upperBound;
}

/*
OLD CODE:
{
	int relOp, maybeBound;
	List<ExpressionBinaryOp> relationsInvolvingVar;
	FindRelOpInvolvingVar visitor = new FindRelOpInvolvingVar(varName);

	int upperBound = curUpperBound;
	try {
		guard.deepCopy().accept(visitor);		// Start searching.
	} catch (PrismException peIgnore) { }
	relationsInvolvingVar = visitor.getExpressionsThatInvolve();

	if (relationsInvolvingVar != null && relationsInvolvingVar.size() > 0)
	{
//for (Expression output : relationsInvolvingVar) System.out.println(output + " may restrict the range of " + varName);
		for (ExpressionBinaryOp curExpr : relationsInvolvingVar) {
			relOp = curExpr.getOperator();
			if (relOp == ExpressionBinaryOp.LT)
			{
				try {
					maybeBound = (curExpr.getOperand2()).evaluateInt(constantValues,(Values)null);
					if (maybeBound < upperBound)	// The current 'maybeBound' value is more restrictive.
					  upperBound = maybeBound - 1;	// So make the preceding integer be the new lower bound.
				} catch (Exception ex1) {
System.out.println("<ExceptionBeingIgnored>");
ex1.printStackTrace(System.out);
System.out.println("</ExceptionBeingIgnored>");
//System.exit(1);
				}
			}
			else if (relOp == ExpressionBinaryOp.LE)
			{
				try {
					maybeBound = (curExpr.getOperand2()).evaluateInt(constantValues,(Values)null);
					if (maybeBound < upperBound)	// The current 'maybeBound' value is more restrictive.
					  upperBound = maybeBound;	// So make it the new lower bound.
				} catch (Exception ex2) {
ex2.printStackTrace(System.out);
System.exit(1);
				}
			}
		}
	}
//System.out.println("Final upperBound of " + varName + " will be " + upperBound);
	return upperBound;
}

*/


	// translate a single module to a dd
	// for a given synchronizing action ("" = none)

	private ComponentDDs translateModule(int m, parser.ast.Module module, String synch, int synchMin) throws PrismException
	{
		ComponentDDs compDDs;
		JDDNode tmp;
		ArrayList<JDDNode> guardDDs, upDDs;			// SHANE HAS CHANGED because we may have more generated rules than original commands.
		TranslatedCommandDDs translatedCmd;
		Command originalCommand;
		int l, numCommands;
		double dmin = 0, dmax = 0;
		boolean match;

DEBUG_CurSynch = synch;

//if (DEBUG_TraSysMod) 
{
	PrintDebugIndent();
	System.out.println("<TranslateModule mod='"+ module.getName() + "', usingSynchOf='" + synch + "'>");
}
//DebugIndent++;

		// get number of commands 
		numCommands = module.getNumCommands();
		// Prepare ArrayLists to store generated DDs
		guardDDs = new ArrayList<JDDNode>();
		upDDs = new ArrayList<JDDNode>();
		//rewDDs = new ArrayList<JDDNode>();

if (DEBUG_TransMod)
{
cachedGuardExprs = new ArrayList<Expression>();

	PrintDebugIndent();
	System.out.println("[in prism.Modules2MTBDD::translateModule()], Module: " + module.getName() + " has " + numCommands + " commands.\n");
	PrintDebugIndent(); System.out.println("<TransMod_Phase1>");
DebugIndent++;
	PrintDebugIndent();
	System.out.println("Looking for those commands with matching sync of: '" + synch +"'");
}
		// translate guard/updates for each command of the module
		for (l = 0; l < numCommands; l++) {
if (DEBUG_TransMod) {
	System.out.println();
//	PrintDebugIndent(); System.out.println("<TrMd_ConsidComForSync cmdNum='"+(l+1)+"/"+ numCommands+"' ofModule='"+module.getName()+"' hopeForSynch='"+synch+"'>");
//	PrintDebugIndent(); System.out.println("[in prism.Modules2MTBDD::translateModule()]: Considering command " + (l+1) + " against sync " + synch);
}
			originalCommand = module.getCommand(l);
			// check if command matches requested synch
			match = false;
			if (synch == "") {
				if (originalCommand.getSynch() == "") match = true;
			}
			else {
				if (originalCommand.getSynch().equals(synch)) match = true;
			}
			// if so translate
			if (match) {
if (DEBUG_TransMod) {
	PrintDebugIndent(); System.out.println("Command " + (l+1) + " of module " +module.getName() + " MATCHES synch '" + synch + "'.");
	PrintDebugIndent(); System.out.println("The command is:\n" + originalCommand);
	PrintDebugIndent(); System.out.println("<DealWithMatchedSynch synch='"+synch+"' module='"+module.getName()+"'"); DebugIndent++;
}

				// New section inserted by SHANE, in order to determine whether any RestrictedScope expressions 
				// are present, as these will potentially result in multiple versions of the command.

if (DEBUG_TransMod) {
	PrintDebugIndent(); System.out.println("<FindRestrictedScopeExpressions>");
}

				Set<RestrictedScopeExpression> RSEs = originalCommand.getRestrictedScopeExpressions();

				// Having determined the restricted Scope Expressions, we now need to work out what variables are involved...
				Set<ExpressionVar> varsForRestrictingScope = new TreeSet<ExpressionVar>();
				Set<ExpressionVar> tmpExprVars;
				List<Values> substitutionCombinsRS;	// Will contain the permutations that we need to generate DDs for.
				ArrayList<Integer> list_varsForRestrictingScope = new ArrayList<Integer>();
				List<Command> commandVersions = new ArrayList<Command>();

				substitutionCombinsRS = new ArrayList<Values>();
				if (RSEs.size() > 0) {
if (DEBUG_TransMod ) {
  System.out.println();
  PrintDebugIndent(); System.out.println("Back in m2MTBDD.transMod: The following are the restriction expressions in the current command:");
	for (Expression restrScopeExpr : RSEs) {
  PrintDebugIndent(); System.out.println("  " + restrScopeExpr);
	}
  System.out.println();
  PrintDebugIndent(); System.out.println("Will now extract out the variables used in the restriction expressions of the above");
}
					for (RestrictedScopeExpression rstrScopeExpr : RSEs) {
						List<Expression> curRestrictionExprs = rstrScopeExpr.getRestrictionExpressions();
						for (Expression restrExprSingleOne : curRestrictionExprs) {
if (DEBUG_TransMod) System.out.println("  Need to consider impact of this: " + restrExprSingleOne);
							tmpExprVars = restrExprSingleOne.extractVarExprs();
							if (tmpExprVars != null && tmpExprVars.size() > 0) {	// Should be true, but here for paranoid safety!
								varsForRestrictingScope.addAll(tmpExprVars);
							}
						}
					}

					// Expected to be true; will need to then consider the domain (range) of each variable.
					if (varsForRestrictingScope.size() > 0) {
						// Construct a List, of the index within varList of a Variable, to give to recursive method.
						for (ExpressionVar ev : varsForRestrictingScope) {
							String varName = ev.getName();
							int vIndex = varList.getIndex(varName);
							if (vIndex == -1) {
								throw new PrismException("Unknown variable \"" + varName + "\"");
							}
// Doesn't work:							else if (varList.getType(vIndex) != parser.ast.type.TypeInt) 
//								throw new PrismException("Variable \"" + varName + "\" not an integer type, cannot be used in a scope restriction expression");

if (DEBUG_TransMod) System.out.println("\nAdding variable " + varName + " to the list of vars used for accessing indexed set (given to checkLowerBounds, etc.)\n");

							list_varsForRestrictingScope.add(vIndex);

						}
					}
						// We need to see whether there are dependencies amongst variables that may occur in those index-expressions,
						// as these may determine an order in which to 'set' a value to determine the other substitutions.

System.out.println("Having worked out all the variables that arise in scope-restriction expressions, now to generate all possible combinations of their values ...\n<ENUMERATE_COMBINS>");
						substitutionCombinsRS = getEnumeratedCombinations(list_varsForRestrictingScope, new Values());
System.out.println("</ENUMERATE_COMBINS>");

System.out.println("There are a maximum of " + substitutionCombinsRS.size() + " combinations that might be applicable, depending on the actual restriction expressions.");

//Not for here; instead we apply the 'defaults'... System.out.println("About to perform elimination of combinations that lead to out-of-bounds index access attempts...\n<ELIMINATE_COMBINS>");

System.out.println(" <GenerateVersions>");
						commandVersions = generateCommandVersions(originalCommand,substitutionCombinsRS);

if (DEBUG_TransMod) {
  System.out.println("Generated " + commandVersions.size() + " versions of the command...");
  for (Command curCmdVer : commandVersions) 
    System.out.println(" One version is: " + curCmdVer);
}

System.out.println(" </GenerateVersions>");

				}		// End of RSEs.size > 0
				else {
					commandVersions.add(originalCommand);		// The original command will be the sole element in the commandVersions list.
if (DEBUG_TransMod ) {
  System.out.println();
  PrintDebugIndent(); System.out.println("Back in m2MTBDD.transMod: There were NO restricted scope expressions in this command.");
}
				}

if (DEBUG_TransMod) {
	PrintDebugIndent(); System.out.println("</FindRestrictedScopeExpressions>");
}

				// Now to cyle over all versions of the command, translating them...
			     for (Command command : commandVersions) {
if (DEBUG_TransMod) {
	PrintDebugIndent();
	System.out.println("\n<DealWithCommandVariant forSynch='"+synch+"'>");
DebugIndent--;
	PrintDebugIndent();
	System.out.println("Now dealing with this variant of the command:\n" + command);
}


				// New section inserted by SHANE, in order to determine if this command needs special treatment due to
				// any IndexedSet access expressions which are to indeterminate index positions...
if (DEBUG_TransMod || Expression.DEBUG_VPEISA) {
	PrintDebugIndent(); System.out.println("<FindInspecificAccessExpr>");
}

				Set<ExpressionIndexedSetAccess> EISAs = command.getVariablePosEISAs();
				Set<Expression> indexSpecifications = new TreeSet<Expression>();	// To find the unique specifications for accessing indexed positions

if (DEBUG_TransMod || Expression.DEBUG_VPEISA) {
	PrintDebugIndent(); System.out.println("</FindInspecificAccessExpr>\n");
	PrintDebugIndent(); System.out.println("in Mod2MTBDD.transMod: REMINDER - The command is: " + command);	// Repeating from earlier, so it (re-)appears in view immediately when I search on the XML tag
}
				// See if there are any found. If so, get the index-expressions and place unique ones into another Set
				if (EISAs.size() > 0) {
if (DEBUG_TransMod || Expression.DEBUG_VPEISA) {
  PrintDebugIndent(); System.out.println("We will need to consider the impact of the following index-specification Expressions:");
}
					for (ExpressionIndexedSetAccess curEISA : EISAs) {
if (DEBUG_TransMod || Expression.DEBUG_VPEISA) {
  PrintDebugIndent(); System.out.println("  " + curEISA.getIndexExpression() + " used to access the '"+curEISA.getName() +"' indexed set.");
}
					  indexSpecifications.add(curEISA.getIndexExpression());
					}
				}

				// Having determined the unique index-specification expressions, we now need to work out what variables are involved...
				Set<ExpressionVar> varsForAccessingIndSet = new TreeSet<ExpressionVar>();
// Now defined earlier:		Set<ExpressionVar> tmpExprVars;
				List<Values> substitutionCombins;	// Will contain the permutations that we need to generate DDs for.
				substitutionCombins = new ArrayList<Values>();
				if (indexSpecifications.size() > 0) {
if (DEBUG_TransMod || Expression.DEBUG_VPEISA) {
  System.out.println();
  PrintDebugIndent(); System.out.println("in m2MTBDD.transMod: The following are the unique set of index-specification expressions (i.e. duplicates removed):");
	for (Expression indSpecExpr : indexSpecifications) {
  PrintDebugIndent(); System.out.println("  " + indSpecExpr);
	}
  System.out.println();
  PrintDebugIndent(); System.out.println("Will now extract out the variables used in the above expressions");
}
					// Go through each identified index-specification expression, and extract out the variables.
					for (Expression indSpecExpr : indexSpecifications) {
						tmpExprVars = indSpecExpr.extractVarExprs();
						if (tmpExprVars != null && tmpExprVars.size() > 0)	// should be true, but done for safety!
						   varsForAccessingIndSet.addAll(tmpExprVars);
					}

					// Expected to be true; will need to then consider the domain (range) of each variable.
					if (varsForAccessingIndSet.size() > 0) {
						// Construct a List, of the index within varList of a Variable, to give to recursive method.
						ArrayList<Integer> list_varsForAccessingIndSet = new ArrayList<Integer>();
						for (ExpressionVar ev :varsForAccessingIndSet) {
/*							if (ev instanceof ExpressionIndexedSetAccess) {	// The index, is itself to come from another indexed set.
								throw new PrismException("Too hard to work with: trying to use an indexed set to access an indexed set.");
							} else 
*/							// Normal variable
							String varName = ev.getName();
							int vIndex = varList.getIndex(varName);
							if (vIndex == -1) {
								throw new PrismException("Unknown variable \"" + varName + "\"");
							}
//							else if (varList.getType(vIndex) != parser.ast.type.TypeInt) 
//								throw new PrismException("Variable \"" + varName + "\" not an integer type, cannot be used in index-specification expression");
	System.out.println("\nAdding variable " + varName + " to the list of vars used for accessing indexed set (given to checkLowerBounds, etc.)\n");

							list_varsForAccessingIndSet.add(vIndex);

						}
						// We need to see whether there are dependencies amongst variables that may occur in those index-expressions,
						// as these may determine an order in which to 'set' a value to determine the other substitutions.

System.out.println("Having worked out all the variables that arise in the index specification, now to enumerate all possible combinations of their values ...\n<ENUMERATE_COMBINS>");
						substitutionCombins = getEnumeratedCombinations(list_varsForAccessingIndSet, new Values());
System.out.println("</ENUMERATE_COMBINS>");

System.out.println("There are a maximum of " + substitutionCombins.size() + " combinations that might be applicable, depending on the actual guard of the command, and actual access expressions.");

System.out.println("About to perform elimination of combinations that lead to out-of-bounds index access attempts...\n<ELIMINATE_COMBINS>");

System.out.println(" <CheckAccessExpressions>");
						substitutionCombins = filterCombinationsForAccessExprs(substitutionCombins,EISAs);
System.out.println(" </CheckAccessExpressions>");

		// ********************** UP  TO   HERE *******************
		// Need to make some way of checking whether any guards are unable to be met by the already filtered combinations. And then, if no combinations remain, then the Command itself is never a possibility, which might be an error to the programmer.

System.out.println(" <CheckGuardLimitations>");
						substitutionCombins = filterCombinationsForGuard(substitutionCombins,command.getGuard());
System.out.println(" </CheckGuardLimitations>");

System.out.println("</ELIMINATE_COMBINS>");

	// **************** THE FOLLOWING 'checkLowerBounds' AND THE 'checkUpperBounds' TECHNIQUE WAS NOT FOOLPROOF ENOUGH ****
	// **************** It is replaced by the above deeper analysis of restrictions by the rest of a guard.  ************
/*
						// For each variable that appears, we need to work out its domain, which could be restricted by its use in other expressions of the same Command/Update.
System.out.println("<REFINE_BOUNDS_Broken>");
						ArrayList<Integer> lowerBounds = checkLowerBounds(list_varsForAccessingIndSet, command.getGuard());
						ArrayList<Integer> upperBounds = checkUpperBounds(list_varsForAccessingIndSet, command.getGuard());
System.out.println("</REFINE_BOUNDS_Broken>");
						// Knowing the domain for each variable used in the index-expression, now determine the individual substitutions.
						substitutionCombins = recurseOnVars(list_varsForAccessingIndSet, lowerBounds, upperBounds, new Values());
System.out.println("</RECURSE_ON_VARS>\n\nBack in translateModules(), received " + substitutionCombins.size() + " combinations for substitution to determine actual indexes of accessing the Indexed Sets"); 
						// The following set will be populated with the indexes to be used
						//Set<Integer> indexVals = enumeratIndexValues(
*/
					}
				}

				if (substitutionCombins.size() == 0)
					substitutionCombins.add(new Values());		// Ensure there is at least one Values -  in this case one with no substitutions.
if (DEBUG_TransMod && substitutionCombins.size() == 0)
{
	PrintDebugIndent();
	System.out.println("There is actually no substitutions, but we added a blank Values() to allow the following loop code to run.\n");
}
				for (Values substitutions : substitutionCombins) {
if (DEBUG_TransMod) {
	PrintDebugIndent();
	System.out.println("[In TranslateModule for mod='"+ module.getName() + "', usingSynchOf='" + synch + "']:");
	PrintDebugIndent();
	System.out.println("About to try the following combination of substitutions:\n" + substitutions + "\n");
	PrintDebugIndent();
	System.out.println("<TrMod_DealWithSubstitution subs='"+substitutions+"'>");
DebugIndent++;
}
					// Generate 1 or more DD which is for a command where the current value substitutions are made
					translatedCmd = translateCommandForValues(m, module, l, command, substitutions);
if (DEBUG_TransMod)
{
	PrintDebugIndent();
	System.out.println("[Back In TranslateModule for mod='"+ module.getName() + "', usingSynchOf='" + synch + "']");
	PrintDebugIndent();
	System.out.println("After translating command " + command + "\n having used the following set of substitutions: '" + substitutions + "'");
	PrintDebugIndent();
        System.out.println("<GuardDDs_Add for_cmd_with_synch='"+synch+"' for_substitutions='"+substitutions+"'>");
	
}
					if (!translatedCmd.guardDD.equals(JDD.ZERO))	// If the guard is never true, no point accumulating it
					{

if (DEBUG_TransMod)
{
	PrintDebugIndent();
	System.out.println("In TransMod: Since translatedCmd.guardDD is not zero, we WILL:\n 1. ADD the translatedCommand's guard to the guardDDs...");
}
						// Extract out the guardDD and the upDD, and append to this module's lists.
						guardDDs.add( translatedCmd.guardDD );
if (DEBUG_TransMod)
{
	PrintDebugIndent(); System.out.println("In TransMod: The accumulated GuardDDs array now has " + guardDDs.size() + " elements");
	PrintDebugIndent(); System.out.println("</GuardDDs_Add>");
	PrintDebugIndent(); System.out.println("In TransMod: 2. We will also add the translatedCommand's upDD to the upDDs...");
	PrintDebugIndent(); System.out.println("<UpDDs_Add>");
}

						upDDs.add( translatedCmd.upDD );

if (DEBUG_TransMod)
{
	System.out.println("</UpDDs_Add>\n");
}

cachedGuardExprs.add ( translatedCmd.guardExpr );


//guardDDs[l].setPurpose("guard for command "+l);	// Would need to say what substitutions are made, if uncommented.
//upDDs[l].setPurpose("upDD for command " + l);

					}
else if (DEBUG_TransMod)
{
	PrintDebugIndent();
	System.out.println("Since translatedCmd.guardDD equals JDD.ZERO (never able to be true), this translated JDD is being DISCARDED.\n  </GuardDDs_Add>");
}

// Always (if debug on):
if (DEBUG_TransMod) {
DebugIndent--;
	PrintDebugIndent();
	System.out.println("</TrMod_DealWithSubstitution sub='"+substitutions+"'>");
}
				}  // End of loop dealing with each possible substitution for variables used in index-set access expressions.

if (DEBUG_TransMod) {
	PrintDebugIndent();
	System.out.println("Completed dealing with this variant of the command:\n" + command);
DebugIndent--;
	PrintDebugIndent();
	System.out.println("</DealWithCommandVariant forSynch='"+synch+"'>");
}

			     } // End of loop which deals with each possible version of the command generated by resolving restriction scopes.


if (DEBUG_TransMod) {
   DebugIndent--; PrintDebugIndent(); System.out.println("</DealWithMatchedSynch synch='"+synch+"' module='"+module.getName()+"'");
}
			// otherwise use 0
			} else {
if (DEBUG_TransMod)
{
	PrintDebugIndent(); 
	System.out.println("Command " + (l+1) + " of module " +module.getName() + " with synch '" + originalCommand.getSynch() + "'");
	PrintDebugIndent(); 
	System.out.println(" - does not match synch '" + synch + "' so skip it for now.");
}
// SHANE BELIEVES THERE IS NO POINT NOW IN EVEN BOTHERING TO STORE ENTRIES FOR THIS ITERATION; it would just slow the CombineCommands phase

//				guardDDs.add(JDD.Constant(0));
//				upDDs.add(JDD.Constant(0));
				//rewDDs.add(JDD.Constant(0));
//guardDDs[l].setPurpose("guard for command "+l);
//upDDs[l].setPurpose("upDD for command " + l);
//cachedGuardExprs.add ( null );
			}

if (DEBUG_TransMod) {
//	PrintDebugIndent(); System.out.println("</TrMd_ConsidComForSync cmdNum='"+(l+1)+"/"+ numCommands+"' synch='"+synch+"'>");
}
		}

if (DEBUG_TransMod)
{
	System.out.println();
	PrintDebugIndent();
	System.out.println("in Modules2MTBDD.translateModule() - Phase 1 completed - finished considering ALL commands of module '" + module.getName() + "' against the synch '" + synch + "'");
DebugIndent--;
	PrintDebugIndent();
	System.out.println("</TransMod_Phase1 forSynch='"+synch+"'>\n");
	PrintDebugIndent();
	System.out.println("<TransMod_Phase2 forSynch='"+synch+"'>");
DebugIndent++;
	PrintDebugIndent();
	System.out.println("in Modules2MTBDD.translateModule() - Commencing Phase 2 - About to do some form of CombineCommands method.");
	PrintDebugIndent();
	System.out.println("for synch='"+synch+"', (within module '"+module.getName()+"' only)\n");
}

		// combine guard/updates dds for each command
		if (modelType == ModelType.DTMC) {
if (DEBUG_TransMod) {
	PrintDebugIndent();
	System.out.print("[in Modules2MTBDD.translateModule()]: About to call combineCommandsProb()");
}
			// OLD before ArrayLists: compDDs = combineCommandsProb(m, numCommands, guardDDs, upDDs);
			JDDNode[] guards= new JDDNode[guardDDs.size()];
			guards = guardDDs.toArray(guards);
			JDDNode[] updates= new JDDNode[guardDDs.size()];	// Sizes should be same anyway
			updates = upDDs.toArray(updates);
if (DEBUG_TransMod) {
	PrintDebugIndent();
	System.out.println("The size of guardDDs is " + guardDDs.size() + " and guards[] is " + guards.length);
	PrintDebugIndent();
	System.out.println("and the size of 'upDDs' is hopefully the same: " + upDDs.size());
}
			compDDs = combineCommandsProb(m, guardDDs.size(), guards, updates);
		}
		else if (modelType == ModelType.MDP) {

			// OLD before ArrayLists: compDDs = combineCommandsNondet(m, numCommands, guardDDs, upDDs, synchMin);
			JDDNode[] guards= new JDDNode[guardDDs.size()];
			guards = guardDDs.toArray(guards);
			JDDNode[] updates= new JDDNode[guardDDs.size()];	// Sizes should be same anyway
			updates = upDDs.toArray(updates);
if (DEBUG_TransMod) {
	PrintDebugIndent();
	System.out.println("The size of guardDDs is " + guardDDs.size() + " and guards[] is " + guards.length);
	PrintDebugIndent();
	System.out.println("and the size of 'upDDs' is hopefully the same: " + upDDs.size());
}
			
if (DEBUG_TransMod) {
	PrintDebugIndent();
	System.out.println("[in Modules2MTBDD.translateModule()]: ~3140: About to call combineCommandsNondet() ...");
}				

			compDDs = combineCommandsNondet(m, guardDDs.size(), guards, updates, synchMin);
			
if (DEBUG_TransMod) {
	PrintDebugIndent();
	System.out.println("[in Modules2MTBDD.translateModule()]: ~3150: After returning from call of combineCommandsNondet() for synch: " + synch);
}			
			// hopefully, the garbage collector will reclaim memory previously used by these arrays:
			guards = null;
			updates = null;

		}
		else if (modelType == ModelType.CTMC) {
if (DEBUG_TransMod) {
	PrintDebugIndent();
	System.out.println("[in Modules2MTBDD.translateModule()]: About to call combineCommandsStoch()");
}
			// OLD before ArrayLists: compDDs = combineCommandsStoch(m, numCommands, guardDDs, upDDs);
			compDDs = combineCommandsStoch(m, guardDDs.size(), (JDDNode[]) guardDDs.toArray(), (JDDNode[]) upDDs.toArray());
		}
		else {
			 throw new PrismException("Unknown model type");
		}

if (DEBUG_TransMod)
{
	PrintDebugIndent();
	System.out.println("[in Modules2MTBDD.translateModule()]: @ line 3172 (Prior to calling deref on the guardDDs and upDDs - guardDDs.size is " + guardDDs.size() + " and upDDs.get is " + upDDs.size() + ") ");
}

		// deref guards/updates
		for (l = 0; l < guardDDs.size(); l++) {
			JDD.Deref(guardDDs.get(l) );
			JDD.Deref(upDDs.get(l) );
			//JDD.Deref(rewDDs.get(l));
		}
	System.out.println("[in Modules2MTBDD.translateModule()]: @ line 3181 (After calling deref on the guardDDs and upDDs) ");
		
if (DEBUG_TraSysMod) 
{
	DebugIndent--;
	PrintDebugIndent();
	System.out.println("\n </TransMod_Phase2 forSynch='"+synch+"'>");
	PrintDebugIndent(); System.out.println("Finished processing synch " + synch + " within current module");
	System.out.println("</TranslateModule mod='"+ module.getName() + "', usingSynchOf='" + synch + "'>\n");
}		
		return compDDs;
	}
	

	// SHANE extracted from translateModules, and has modified
	/**
	  Translate a command into a DD. If substitutions' is non-empty (the case when an indexed-set access using 
	  variables in expressions) then various evaluations of expressions (possibly in guards, possibly in updates) 
	  will be done BEFORE the translation into DD.
	  @param m The Module number that contained this command (known by the caller translateModules)
	  @param l The command-number within the module of the original command (from the caller)
	  @param command The actual command to be translated
	  @param substitutions A partial set of variables, with specific values to be used on this particular translation.
	 */
	public TranslatedCommandDDs translateCommandForValues(int m, parser.ast.Module module, int l, Command command, Values substitutions)
	throws PrismException
	{
		TranslatedCommandDDs translatedCommandDD;		// For the return value
		ComponentDDs compDDs;
		JDDNode guardDD, upDD, tmp;
		double dmin = 0, dmax = 0;

if (DEBUG_SUBSTITUTIONS) { 
  PrintDebugIndent(); System.out.println(" <TranslateCommand>\nTranslating the following command: " + command); 
  if (substitutions.getNumValues() > 0) 
    System.out.println("\nUsing the following substitutions: " + substitutions);
}


// MOVED - this occurs before THIS method is called.
//		// First, we must resolve any RestrictedScopeExpressions to determine whether to include their underlying
//		// expression, or their default instead...
//		ResolveRestrictedScopes rrs = new ResolveRestrictedScopes(constantValues,substitutions);
//		command = (Command) rrs.visit((Command) command.deepCopy());		// I hope that is the appropriate thing to do, so as to not break the original module.


if (DEBUG_TransMod) { PrintDebugIndent(); System.out.println(" <DealWithGuard>"); }

		// Find the current command's guard
		Expression curGuard = command.getGuard();

		// Generate the extra part of the guard that substitutes specific values for the variables in the supplied cases:
		Expression extraGuard = null;

		for (int i = 0; i < substitutions.getNumValues(); i++)
		{
			String varNameToUse = substitutions.getName(i);
			int valToUse = (int) ((Integer)substitutions.getValue(i));
			int indexInVarList = varList.getIndex(varNameToUse);

			ExpressionVar theVar = 	new ExpressionVar( varNameToUse, varList.getType(indexInVarList));
			theVar.setIndex(indexInVarList);		// SHANE HOPES THAT IS CORRECT - otherwise, have to find out appropriate value to use.
//if (DEBUG_TransMod) System.out.println("for variable " + varNameToUse + ", create ExprVar with type set to " + varList.getType(indexInVarList)");
			ExpressionLiteral theVal = new ExpressionLiteral(varList.getType(indexInVarList),valToUse);

			Expression nextPart;
			nextPart = new ExpressionBinaryOp(ExpressionBinaryOp.EQ, theVar, theVal);
			if (i == 0)
				extraGuard = nextPart;
			else
			  extraGuard = new ExpressionBinaryOp(ExpressionBinaryOp.AND, nextPart, extraGuard);	// Pre-catenate, as conjunction
		}
		if (extraGuard != null) {
if (DEBUG_SUBSTITUTIONS) System.out.println("\nThe command with synch "+ DEBUG_CurSynch + "'s original guard was: " + curGuard + "\n");
			// Exchange the known values of the current substitution into the original guard BUT ONLY where appearing inside Index-Specification expressions. 
			curGuard = (Expression) curGuard.deepCopy();	// Use a copy, so the original can be used for next iteration.
			curGuard.replaceIndexSpecifiers(substitutions);
if (DEBUG_SUBSTITUTIONS) System.out.println("\nThe command's interim guard (after substitutions into original guard, before the additional guards) is: " + curGuard);
			// Include the constraints on this rule's applicability by appending as guards the substitutions
			curGuard = new ExpressionBinaryOp(ExpressionBinaryOp.AND,
				curGuard,			// and the new part, with the current/old part.
				// wrap new part in parentheses
				//new ExpressionUnaryOp(ExpressionUnaryOp.PARENTH,
				extraGuard
			);
if (DEBUG_SUBSTITUTIONS) System.out.println("\nThe command's final guard is: " + curGuard + "\n");
		}


if (DEBUG_TransMod) {
	PrintDebugIndent(); System.out.println(" <TranslateGuardExpr guard=\"" + curGuard + "\">");
	DebugIndent += 2;
}
		// translate guard
		guardDD = translateExpression(curGuard); 

if (DEBUG_TransMod) {
	DebugIndent -= 1;
	PrintDebugIndent(); System.out.println("</TranslateGuardExpr guard=\"" + curGuard + "\">");
	PrintDebugIndent();
System.out.println("[in prism.Modules2MTBDD::translateCommand()], concluded calling translateExpr for guard of command " + (l+1) + " for substitutions: '" + substitutions +"'");
}

if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(range,"~About the JDDNode describing 'range' ",true);

		JDD.Ref(range);
		guardDD = JDD.Apply(JDD.TIMES, guardDD, range);

if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(guardDD,"~About the JDDNode describing the final guard after TIMES by range: ",true);

		// check for false guard
		if (guardDD.equals(JDD.ZERO)) {
if (DEBUG_TransMod) {
	PrintDebugIndent();
	System.out.println("[in prism.Modules2MTBDD::translateCommand()]: The result is that guardDD actually IS equal to JDD.ZERO...");
}
			// display a warning (unless guard is "false", in which case was probably intentional)
			// Also, if extraGuard is not null, then it could be due to incoherent contradictory value for current iteration so don't warn then.
			if (!Expression.isFalse(curGuard) && extraGuard == null) {
				String s = "Guard for command " + (l+1) + " of module \"" + module.getName() + "\" is never satisfied.";
				mainLog.printWarning(s);
/*SHANE*/	mainLog.println("I think the guard that is never satisfied, was: " + curGuard);
			}
			// no point bothering to compute the mtbdds for the update
			// if the guard is never satisfied
			upDD = JDD.Constant(0);
			//rewDD = JDD.Constant(0);
if (DEBUG_TransMod) {
	PrintDebugIndent(); System.out.println(" </DealWithGuard>"); DebugIndent-=1;
}
		}
		else {
if (DEBUG_TransMod) {
	PrintDebugIndent(); System.out.println(" </DealWithGuard>"); DebugIndent-=1;
	System.out.println();		// Blank row.
	PrintDebugIndent();
	System.out.println("[in Modules2MTBDD.translateCommand()]: the result was that guardDD was not JDD.ZERO, so calling translateUpdates()...");
}
			// translate updates and do some checks on probs/rates
			upDD = translateUpdates(m, l, command.getUpdates(), (command.getSynch()=="")?false:true, guardDD, substitutions);
	System.out.println();		// Blank row.
	PrintDebugIndent();
if (DEBUG_TransMod) {
	System.out.println("[in Modules2MTBDD.translateCommand()]: Finished call of translateUpdates(), doing other things...\n");
}

if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(upDD,"~About the JDDNode describing the update: " + command.getUpdates() + " for substitutions " + substitutions,true);

			JDD.Ref(guardDD);
			upDD = JDD.Apply(JDD.TIMES, upDD, guardDD);
if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(upDD,"~About the JDDNode describing the update after TIMES with the guardDD: ",true);

			// are all probs/rates non-negative?
			dmin = JDD.FindMin(upDD);
			if (dmin < 0) {
				String s = (modelType == ModelType.CTMC) ? "Rates" : "Probabilities";
				s += " in command " + (l+1) + " of module \"" + module.getName() + "\" are negative";
				s += " (" + dmin + ") for some states.\n";
				s += "Perhaps the guard needs to be strengthened";
				throw new PrismLangException(s, command);
			}
			// only do remaining checks if 'doprobchecks' flag is set
if (DEBUG_TransMod)
{
	PrintDebugIndent();
	System.out.print("[in Modules2MTBDD.translateCommand()]: - calling prism.getDoProbChecks()... ");
}
			if (prism.getDoProbChecks()) {
if (DEBUG_TransMod)
{
	System.out.println("result was true, doing the IF branch's code, which will determine if probabilities add up to 1.0");
}
				// sum probs/rates in updates
				JDD.Ref(upDD);
				tmp = JDD.SumAbstract(upDD, moduleDDColVars[m]);
				tmp = JDD.SumAbstract(tmp, globalDDColVars);
				// put 1s in for sums which are not covered by this guard
				JDD.Ref(guardDD);
				tmp = JDD.ITE(guardDD, tmp, JDD.Constant(1));
				// compute min/max sums
				dmin = JDD.FindMin(tmp);
				dmax = JDD.FindMax(tmp);
				// check sums for NaNs (note how to check if x=NaN i.e. x!=x)
if (DEBUG_TransMod) {
	PrintDebugIndent();
	System.out.println("dmin is: " + dmin + ", dmax is: " + dmax + ", prism.getSumRoundOff() is " +prism.getSumRoundOff());
}

	// SHANE NOTES that we may need to say in the errors, whether therewere particular substitutions being used...
				if (dmin != dmin || dmax != dmax) {
					JDD.Deref(tmp);
					String s = (modelType == ModelType.CTMC) ? "Rates" : "Probabilities";
					s += " in command " + (l+1) + " of module \"" + module.getName() + "\" have errors (NaN) for some states. ";
					s += "Check for zeros in divide or modulo operations. ";
					s += "Perhaps the guard needs to be strengthened";
					throw new PrismLangException(s, command);
				}
				// check min sums - 1 (ish) for dtmcs/mdps, 0 for ctmcs
				if (modelType != ModelType.CTMC && dmin < 1-prism.getSumRoundOff()) {
					JDD.Deref(tmp);
					String s = "Probabilities in command " + (l+1) + " of module \"" + module.getName() + "\" sum to less than one";
					s += " (e.g. " + dmin + ") for some states. ";
					s += "Perhaps some of the updates give out-of-range values. ";
					s += "One possible solution is to strengthen the guard";
					throw new PrismLangException(s, command);
				}
				if (modelType == ModelType.CTMC && dmin <= 0) {
					JDD.Deref(tmp);
					// note can't sum to less than zero - already checked for negative rates above
					String s = "Rates in command " + (l+1) + " of module \"" + module.getName() + "\" sum to zero for some states. ";
					s += "Perhaps some of the updates give out-of-range values. ";
					s += "One possible solution is to strengthen the guard";
					throw new PrismLangException(s, command);
				}
				// check max sums - 1 (ish) for dtmcs/mdps, infinity for ctmcs
				if (modelType != ModelType.CTMC && dmax > 1+prism.getSumRoundOff()) {
					JDD.Deref(tmp);
					String s = "Probabilities in command " + (l+1) + " of module \"" + module.getName() + "\" sum to more than one";
					s += " (e.g. " + dmax + ") for some states. ";
					s += "Perhaps the guard needs to be strengthened";
					throw new PrismLangException(s, command);
				}
				if (modelType == ModelType.CTMC && dmax == Double.POSITIVE_INFINITY) {
					JDD.Deref(tmp);
					String s = "Rates in command " + (l+1) + " of module \"" + module.getName() + "\" sum to infinity for some states. ";
					s += "Perhaps the guard needs to be strengthened";
					throw new PrismLangException(s, command);
				}
				JDD.Deref(tmp);
			}
else	// Else to getDoProbChecks; not in original code, just here for debug.
if (DEBUG_TransMod)
{
	System.out.println("was false, nothing to do. (In other words, not checking that probabilities sum to 1.0)");
}

			// translate reward, if present
			// if (command.getReward() != null) {
			// 	tmp = translateExpression(command.getReward());
			// 	JDD.Ref(upDDs[l]);
			// 	rewDDs[l] = JDD.Apply(JDD.TIMES, tmp, JDD.GreaterThan(upDDs[l], 0));
			// 	// are all rewards non-negative?
			// if ((d = JDD.FindMin(rewDDs[l])) < 0) {
			// 	String s = "Rewards in command " + (l+1) + " of module \"" + module.getName() + "\" are negative";
			// 	s += " (" + d + ") for some states. ";
			// 	s += "Perhaps the guard needs to be strengthened";
			// 	throw new PrismException(s);
			// }
			// } else {
			// 	rewDDs[l] = JDD.Constant(0);
			// }
		}
if (DEBUG_SUBSTITUTIONS) { 
	PrintDebugIndent(); System.out.println(" </TranslateCommand>\n"); 
}

		// Construct the return value...
		translatedCommandDD = new TranslatedCommandDDs();
		translatedCommandDD.guardDD = guardDD;
		translatedCommandDD.upDD = upDD;
		translatedCommandDD.originalCommandNumber = l;
		translatedCommandDD.guardExpr = curGuard;

		return translatedCommandDD;
	}

	// go thru guard/updates dds for all commands of a prob. module and combine
	// also check for any guard overlaps, etc...
	
	private ComponentDDs combineCommandsProb(int m, int numCommands, JDDNode guardDDs[], JDDNode upDDs[])
	{
		ComponentDDs compDDs;
		int i;
		JDDNode covered, transDD, tmp;
		//JDDNode rewardsDD;
		
		// create object to return result
		compDDs = new ComponentDDs();
		
		// use 'transDD' to build up MTBDD for transitions
		transDD = JDD.Constant(0);
		// use 'transDD' to build up MTBDD for rewards
		//rewardsDD = JDD.Constant(0);
		// use 'covered' to track states covered by guards
		covered = JDD.Constant(0);
		// loop thru commands...
		for (i = 0; i < numCommands; i++) {
			// do nothing if guard is empty
			if (guardDDs[i].equals(JDD.ZERO)) {
				continue;
			}
			// check if command overlaps with previous ones
			JDD.Ref(guardDDs[i]);
			JDD.Ref(covered);
			tmp = JDD.And(guardDDs[i], covered);
			if (!(tmp.equals(JDD.ZERO))) {
				// if so, output a warning (but carry on regardless)
				mainLog.printWarning("Guard for command " + (i+1) + " of module \""
					+ moduleNames[m] + "\" overlaps with previous commands.");
			}
			JDD.Deref(tmp);
			// add this command's guard to 'covered'
			JDD.Ref(guardDDs[i]);
			covered = JDD.Or(covered, guardDDs[i]);
			// add transitions
			JDD.Ref(guardDDs[i]);
			JDD.Ref(upDDs[i]);
			transDD = JDD.Apply(JDD.PLUS, transDD, JDD.Apply(JDD.TIMES, guardDDs[i], upDDs[i]));
			// add rewards
			//JDD.Ref(guardDDs[i]);
			//JDD.Ref(rewDDs[i]);
			//rewardsDD = JDD.Apply(JDD.PLUS, rewardsDD, JDD.Apply(JDD.TIMES, guardDDs[i], rewDDs[i]));
		}
		
		// store result
		compDDs.guards = covered;
		compDDs.trans = transDD;
		//compDDs.rewards = rewardsDD;
		compDDs.min = 0;
		compDDs.max = 0;
		
		return compDDs;
	}

	// go thru guard/updates dds for all commands of a stoch. module and combine
	
	private ComponentDDs combineCommandsStoch(int m, int numCommands, JDDNode guardDDs[], JDDNode upDDs[])
	{
		ComponentDDs compDDs;
		int i;
		JDDNode covered, transDD;
		//JDDNode rewardsDD;
		
		// create object to return result
		compDDs = new ComponentDDs();
		
		// use 'transDD 'to build up MTBDD for transitions
		transDD = JDD.Constant(0);
		// use 'transDD 'to build up MTBDD for rewards
		//rewardsDD = JDD.Constant(0);
		// use 'covered' to track states covered by guards
		covered = JDD.Constant(0);
		
		// loop thru commands...
		for (i = 0; i < numCommands; i++) {
			// do nothing if guard is empty
			if (guardDDs[i].equals(JDD.ZERO)) {
				continue;
			}
			// add this command's guard to 'covered'
			JDD.Ref(guardDDs[i]);
			covered = JDD.Or(covered, guardDDs[i]);
			// add transitions
			JDD.Ref(guardDDs[i]);
			JDD.Ref(upDDs[i]);
			transDD = JDD.Apply(JDD.PLUS, transDD, JDD.Apply(JDD.TIMES, guardDDs[i], upDDs[i]));
			// add rewards
			//JDD.Ref(guardDDs[i]);
			//JDD.Ref(rewDDs[i]);
			//rewardsDD = JDD.Apply(JDD.PLUS, rewardsDD, JDD.Apply(JDD.TIMES, guardDDs[i], rewDDs[i]));
		}
		
		// store result
		compDDs.guards = covered;
		compDDs.trans = transDD;
		//compDDs.rewards = rewardsDD;
		compDDs.min = 0;
		compDDs.max = 0;
		
		return compDDs;
	}

	// go thru guard/updates dds for all commands of a non-det. module,
	// work out guard overlaps and sort out non determinism accordingly
	// (non recursive version)
	
	private ComponentDDs combineCommandsNondet(int m, int numCommands, JDDNode guardDDs[], JDDNode upDDs[], int synchMin) throws PrismException
	{
		ComponentDDs compDDs;
		int i, j, k, maxChoices, numDDChoiceVarsUsed;
		JDDNode covered, transDD, overlaps, equalsi, tmp, tmp2, tmp3;
		//JDDNode rewardsDD;
		JDDNode[] transDDbits, frees;
		//JDDNode[] rewardsDDbits;
		JDDVars ddChoiceVarsUsed;
if (DEBUG_CCN) {
	PrintDebugIndent();
	System.out.println("\n<CombineCommandsNondet numCommands='" + numCommands + "' sizeof_guardDDs='"+guardDDs.length+"'>");
	DebugIndent++;
}
long startTime = 0;
long nowTime;
		
		// create object to return result
		compDDs = new ComponentDDs();
		
		// use 'transDD' to build up MTBDD for transitions
		transDD = JDD.Constant(0);
		// use 'transDD' to build up MTBDD for rewards
		//rewardsDD = JDD.Constant(0);
		// use 'covered' to track states covered by guards
		covered = JDD.Constant(0);
		
		// find overlaps in guards by adding them all up
		overlaps = JDD.Constant(0);
if (DEBUG_CCN) {
	PrintDebugIndent();
	System.out.println("Before any CCN Iterations we have these facts (I expect 0 for each):");
if (DEBUG_SHANE_ShowStepsInTM)	ShaneReportDD(overlaps,"The 'overlaps' DD before any CCN itereations:",true);
if (DEBUG_SHANE_ShowStepsInTM)	ShaneReportDD(covered,"The 'covered' DD before any CCN itereations: ",true);
	System.out.println("In CCN: About to commence first loop (over " + numCommands + " commands)");
	System.out.println("\n<DetermineGuardOverlaps>");
}
		for (i = 0; i < numCommands; i++) {


if (DEBUG_CCN) {
	PrintDebugIndent();
	System.out.println("<CCN_ITER1_ConsidGuard commandNum='"+(i+1)+"/"+numCommands+"'>");
//	PrintDebugIndent();
//	System.out.println("Will reference this JDD, to 'PLUS' it to the 'overlaps' JDD and OR it with the 'covered' JDD:\n" + guardDDs[i]);
System.out.println("Considering command which has the following guard: \n" +cachedGuardExprs.get(i));

if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(guardDDs[i],"~About DD for guardDDs["+i+"]",true);
}
			if (guardDDs[i] != null && !guardDDs[i].equals(JDD.ZERO)) {	// Nothing to do if it is null/zero

				JDD.Ref(guardDDs[i]);
if (DEBUG_CCN) { 
startTime = System.currentTimeMillis();
PrintDebugIndent(); System.out.println(" CCN at point A - about to do JDD Plus (to grow 'overlaps')");}
				overlaps = JDD.Apply(JDD.PLUS, overlaps, guardDDs[i]);
if (DEBUG_CCN) { 
nowTime = System.currentTimeMillis();
PrintDebugIndent(); System.out.println(" That took " + ((nowTime-startTime)/1000) + " seconds to do.");
if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(overlaps,"~About DD for 'overlaps' after PLUS-ing guardDDs["+i+"] into it:",DEBUG_SHANE_ShowDD_Tree);

PrintDebugIndent(); System.out.println(" CCN at point B - about to do JDD.Or (to grow 'covered')");}
startTime = System.currentTimeMillis();
				// compute bdd of all guards at same time
				JDD.Ref(guardDDs[i]);
				covered = JDD.Or(covered, guardDDs[i]);
if (DEBUG_CCN) {
	PrintDebugIndent();
	nowTime = System.currentTimeMillis();
PrintDebugIndent(); System.out.println(" That took " + ((nowTime-startTime)/1000) + " seconds to do.");
if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(covered,"~About DD for 'covered' after OR-ing guardDDs["+i+"] into it:",DEBUG_SHANE_ShowDD_Tree);
	System.out.println("</CCN_ITER1_ConsidGuard commandNum='"+(i+1)+"/"+numCommands+"'>");
}
			}
else System.out.println("Shane is skipping commandNum " + (i+1) +" due to its guardDDs[] being " + (guardDDs[i] == null ? "null" : "zero"));
		}
		
if (DEBUG_CCN) {
	System.out.println("\n</DetermineGuardOverlaps>\n");
	PrintDebugIndent();
	System.out.println("In CCN: Completed First Loop which considered guards for all commands.");
	PrintDebugIndent();
	System.out.println("In CCN: About to call 'FindMax' on the Overlaps JDD...");
}
		// find the max number of overlaps
		// (i.e. max number of nondet. choices)
		maxChoices = (int)Math.round(JDD.FindMax(overlaps));
if (DEBUG_CCN) {
	PrintDebugIndent();
	System.out.println("  IN CCN: The result of FindMax (stored in maxChoices) is " + maxChoices);
}
		
		// if all the guards were false, we're done already
		if (maxChoices == 0) {
if (DEBUG_CCN) {
	PrintDebugIndent();
	System.out.println("    Since it is 0, that means \"THE GUARDS WERE ALL FALSE\", and there's nothing more to process.");
}
			compDDs.guards = covered;
			compDDs.trans = transDD;
			//compDDs.rewards = rewardsDD;
			compDDs.min = synchMin;
			compDDs.max = synchMin;
			JDD.Deref(overlaps);
if (DEBUG_CCN) {
	DebugIndent--;
	PrintDebugIndent();
	System.out.println("</CombineCommandsNondet place='EARLY_MID_METHOD'>");
}
			return compDDs;
		}
		
if (DEBUG_CCN) {
  System.out.println("\n<BuildTransMatrixDD>");
}
		// likewise, if there are no overlaps, it's also pretty easy
		if (maxChoices == 1) {
if (DEBUG_CCN) {
	PrintDebugIndent();
	System.out.println("    Since it is 1, that means \"NO OVERLAPS\", - so will just Add-Up the DDs for all commands.");
	PrintDebugIndent();
	System.out.println("In CCN: Commencing 2nd Loop, to build the Transition Matrix DD, by combining guards and updates.");
}
			// add up dds for all commands
			for (i = 0; i < numCommands; i++) {
				// add up transitions
				JDD.Ref(guardDDs[i]);
				JDD.Ref(upDDs[i]);
if (DEBUG_CCN) {
	PrintDebugIndent();
	System.out.println("<CCN_ITER2 for_cmdNum='" + (i+1) + "'>");
	PrintDebugIndent();
	System.out.println("In CCN_ITER2: Will apply TIMES to these two DDs\nguardDDs["+i+"]: " + guardDDs[i] + "\nupDDs["+i+"]: " + upDDs[i]);
	PrintDebugIndent();
	System.out.println("And will PLUS that, to transDD.");
}

// ORIGINAL, KEEP:		transDD = JDD.Apply(JDD.PLUS, transDD, JDD.Apply(JDD.TIMES, guardDDs[i], upDDs[i]));
// TEMPORARY BREAK-UP OF THE ABOVE:
JDDNode curItem = JDD.Apply(JDD.TIMES, guardDDs[i],upDDs[i]);
if (DEBUG_CCN && DEBUG_SHANE_ShowStepsInCCN2) {
  ShaneReportDD(guardDDs[i],"~About: guardDD["+i+"] is:",true);
  ShaneReportDD(upDDs[i],"~About: updateDD["+i+"] is:",true);
  ShaneReportDD(curItem,"~About: The GuardDD["+i+"] TIMES the UpdateDD["+i+"] is the following DD:",true);
}

// SHANE THOUGHT:		if (!curItem.equals(JDD.ZERO)) { }		// No point processing it if it is zero, but maybe it is slow to do the 'equals', or negligbly quick to do the Apply of the original code...

transDD = JDD.Apply(JDD.PLUS, transDD, curItem);

				// add up rewards
				//JDD.Ref(guardDDs[i]);
				//JDD.Ref(rewDDs[i]);
				//rewardsDD = JDD.Apply(JDD.PLUS, rewardsDD, JDD.Apply(JDD.TIMES, guardDDs[i], rewDDs[i]));
if (DEBUG_CCN) {
	if (DEBUG_SHANE_ShowStepsInCCN2) ShaneReportDD(transDD,"~About: After including the above by PLUS it into the transition matrix DD, the transDD (for the current synch) is now:",true);

//	PrintDebugIndent();
//	System.out.println("In CCN2 transDD is now: " + transDD);
	PrintDebugIndent();
	System.out.println("</CCN_ITER2>");
}
			}
			compDDs.guards = covered;
			compDDs.trans = transDD;
			//compDDs.rewards = rewardsDD;
			compDDs.min = synchMin;
			compDDs.max = synchMin;
			JDD.Deref(overlaps);	
if (DEBUG_CCN) {
	DebugIndent--;
	PrintDebugIndent();
	System.out.println("In CCN: Finished Second Loop");
  System.out.println("\n</BuildTransMatrixDD>");
	System.out.println("</CombineCommandsNondet MID_METHOD_RETURN>");
}
			return compDDs;
		}
		
		// otherwise, it's a bit more complicated...
if (DEBUG_CCN) {
	PrintDebugIndent();
	System.out.println("Since maxChoices is neither 0 nor 1, that means \"THERE WERE OVERLAPS\" - so it's got more work to do...\"");
}
		
		// first, calculate how many dd vars will be needed
		numDDChoiceVarsUsed = (int)Math.ceil(PrismUtils.log2(maxChoices));
if (DEBUG_CCN) {
	PrintDebugIndent();
	System.out.println("We will require " + numDDChoiceVarsUsed + " choice variables (DDs), because there are " + maxChoices + " possibilities (log2 this)");
}
		
		// select the variables we will use and put them in a JDDVars
		ddChoiceVarsUsed = new JDDVars();
ddChoiceVarsUsed.setPurpose("ddChoiceVarsUsed, set-up in m2mtbdd.combineCommandsNondet()");
		for (i = 0; i < numDDChoiceVarsUsed; i++) {
			if (ddChoiceVars.length-synchMin-numDDChoiceVarsUsed+i < 0)
				throw new PrismException("Insufficient BDD variables allocated for nondeterminism - please report this as a bug. Thank you.");
			ddChoiceVarsUsed.addVar(ddChoiceVars[ddChoiceVars.length-synchMin-numDDChoiceVarsUsed+i]);
		}
		
		// for each i (i = 1 ... max number of nondet. choices)
		for (i = 1; i <= maxChoices; i++) {
if (DEBUG_CCN) {
	PrintDebugIndent();
	System.out.println("Considering cases that have " + i + " non-deterministic choices (by finding when 'overlaps' equals this number)");
}
			
			// find sections of state space
			// which have exactly i nondet. choices in this module
			JDD.Ref(overlaps);
			equalsi = JDD.Equals(overlaps, (double)i);
			// if there aren't any for this i, skip the iteration
			if (equalsi.equals(JDD.ZERO)) {
if (DEBUG_CCN) { PrintDebugIndent(); System.out.println("There are no cases that have exactly " + i + " non-deterministic choices."); }
				JDD.Deref(equalsi);
				continue;
			}
if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(equalsi,"~About equalsi: ",true);
			
			// create arrays of size i to store dds
if (DEBUG_CCN) { PrintDebugIndent(); System.out.println("There are some cases that have exactly " + i + " non-deterministic choices.\nTherefore, making transDDbits be a array of " + i + " JDDNode objects"); }
			transDDbits = new JDDNode[i];
			//rewardsDDbits = new JDDNode[i];
			frees = new JDDNode[i];
			for (j = 0; j < i; j++) {
				transDDbits[j] = JDD.Constant(0);
				//rewardsDDbits[j] = JDD.Constant(0);
				frees[j] = equalsi;
				JDD.Ref(equalsi);
			}
			
			// go thru each command of the module...
			for (j = 0; j < numCommands; j++) {
if (DEBUG_CCN) { PrintDebugIndent(); System.out.println("Check if command " + (j+1) + " (of the current batch) matches criteria (of having " + i + " choices)."); }
				
				// see if this command's guard overlaps with 'equalsi'
				JDD.Ref(guardDDs[j]);
				JDD.Ref(equalsi);
if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(guardDDs[j],"~About guardDDs["+j+"]",true);
				tmp = JDD.And(guardDDs[j], equalsi);
				// if it does...
				if (!tmp.equals(JDD.ZERO)) {
if (DEBUG_CCN) { PrintDebugIndent(); System.out.println(" It does (apparently). So will try to split into that many choices..."); }
					
					// split it up into nondet. choices as necessary
					
					JDD.Ref(tmp);
					tmp2 = tmp;
if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(tmp2,"~About tmp2: ",true);
					
					// for each possible nondet. choice (1...i) involved...
					for (k = 0; k < i; k ++) {
if (DEBUG_CCN) { PrintDebugIndent(); System.out.println("   Examining choice #" + (k+1) + "/" + i + " of command " + (j+1)); }
						// see how much of the command can go in nondet. choice k
						JDD.Ref(tmp2);
						JDD.Ref(frees[k]);
if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(frees[k],"~About frees["+k+"]",true);
						tmp3 = JDD.And(tmp2, frees[k]);
if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(tmp3,"~About the AND of tmp2 and frees[k]",true);
						// if some will fit in...
						if (!tmp3.equals(JDD.ZERO)) {
if (DEBUG_CCN) { PrintDebugIndent(); System.out.println("     Some will probably fit."); }
							JDD.Ref(tmp3);
							frees[k] = JDD.And(frees[k], JDD.Not(tmp3));
frees[k].setPurpose("% frees["+k+"] set in CCN%");
							JDD.Ref(tmp3);
							JDD.Ref(upDDs[j]);

// ORIGINAL, KEEP:					transDDbits[k] = JDD.Apply(JDD.PLUS, transDDbits[k], JDD.Apply(JDD.TIMES, tmp3, upDDs[j]));
// TEMPORARY BREAK-UP OF THE ABOVE:
JDDNode curItem = JDD.Apply(JDD.TIMES, tmp3,upDDs[j]);
transDDbits[k] = JDD.Apply(JDD.PLUS, transDDbits[k], curItem);

if (DEBUG_CCN && DEBUG_SHANE_ShowStepsInTM) {
  ShaneReportDD(curItem,"~About: The result of TIMES between tmp3 and upDDs["+j+"]",true);
}
transDDbits[k].setPurpose("% transDDbits["+k+"] set during CCN %");
							//JDD.Ref(tmp3);
							//JDD.Ref(rewDDs[j]);
							//rewardsDDbits[k] = JDD.Apply(JDD.PLUS, rewardsDDbits[k], JDD.Apply(JDD.TIMES, tmp3, rewDDs[j]));
						}
						// take out the bit just put in this choice
						tmp2 = JDD.And(tmp2, JDD.Not(tmp3));
if (DEBUG_CCN && DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(tmp2,"tmp2 after 'taking out the bit just put in this choice' is:",true);
						if (tmp2.equals(JDD.ZERO)) {
							break;
						}
					}
					JDD.Deref(tmp2);
if (DEBUG_CCN) { PrintDebugIndent(); System.out.println("   Finished examining choice #" + (k+1) + " of command " + (j+1) ); }
				}
				JDD.Deref(tmp);
			}
if (DEBUG_CCN) { PrintDebugIndent(); System.out.println("Finished checking all commands. Now 'add' the choices for i value " + i); }
			
			// now add the nondet. choices for this value of i
			for (j = 0; j < i; j++) {

				tmp = JDD.SetVectorElement(JDD.Constant(0), ddChoiceVarsUsed, j, 1);
if (DEBUG_CCN && DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(tmp,"After SetVectorElement for j="+j,true);
				transDD = JDD.Apply(JDD.PLUS, transDD, JDD.Apply(JDD.TIMES, tmp, transDDbits[j]));
if (DEBUG_CCN && DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(transDD,"~About transDD after TIMES and PLUS for iteration j="+j,true);
				//JDD.Ref(tmp);
				//rewardsDD = JDD.Apply(JDD.PLUS, rewardsDD, JDD.Apply(JDD.TIMES, tmp, rewardsDDbits[j]));
				JDD.Deref(frees[j]);
			}
transDD.setPurpose("% transDD when i=" + i + " after all j iterations, set in CCN %");

			
			// take the i bits out of 'overlaps'
			overlaps = JDD.Apply(JDD.TIMES, overlaps, JDD.Not(equalsi));
if (DEBUG_CCN) {
	PrintDebugIndent();
	System.out.println("Finished considering cases that have " + i + " non-deterministic choices");
}
		}
		JDD.Deref(overlaps);
		
		// store result
		compDDs.guards = covered;
compDDs.guards.setPurpose("% compDDs.guards as modified during CCN %");
		compDDs.trans = transDD;
compDDs.trans.setPurpose("% compDDs.trans as modified during CCN %");
		//compDDs.rewards = rewardsDD;
		compDDs.min = synchMin;
		compDDs.max = synchMin + numDDChoiceVarsUsed;
		
if (DEBUG_CCN) {
  System.out.println("\n</BuildTransMatrixDD>");
}

if (DEBUG_CCN) {
	DebugIndent--;
	PrintDebugIndent();
	System.out.println("</CombineCommandsNondet place='END of method'>");
}
		return compDDs;
	}

	// translate the updates part of a command
// MODIFIED by SHANE - to accept a Values containing substitutions to be made (for indexed-set index-specification processing)
	private JDDNode translateUpdates(int m, int l, Updates u, boolean synch, JDDNode guard, Values substitutions) throws PrismException
	{
		int i, n;
		Expression p;
		JDDNode dd, udd, pdd;
		boolean warned;
		String msg;

JDDNode rrr;	// Shane Debugging only

if (DEBUG_TransUpd) System.out.println("<Mod2MTBDD_transUp_Pt1>");
		
		// sum up over possible updates
		dd = JDD.Constant(0);
		n = u.getNumUpdates();
if (DEBUG_TransUpd) System.out.println("In Mod2MTBDD.transUpdates (First one - whole Command), there are " + n + " update elements to deal with for this command #" + (l+1) + ".");
		for (i = 0; i < n; i++) {
if (DEBUG_TransUpd) System.out.println("In Mod2MTBDD.transUpdates (First version), Place A iteration i=" + (i+1) + " of " + n + " - about to call translateUpdate" );
			// translate a single update
			udd = translateUpdate(m, u.getUpdate(i), synch, guard, substitutions);
if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(udd,"~About the JDDNode which is the translation of this update: " + u.getUpdate(i) + " with substitutions: " + substitutions,true );

if (DEBUG_TransUpd) System.out.println("In Mod2MTBDD.transUpdates (First version), Place B for iteration i=" + (i+1) + " of " + n + " - returned from call of translateUpdate" );
if (DEBUG_TransUpd) System.out.println("Remember that the whole set of updates is: " + u + "\nWe just did update #" + (i + 1) + " which was " + u.getUpdate(i) );
			// check for zero update
			warned = false;
			if (udd.equals(JDD.ZERO)) {
if (DEBUG_TransUpd) System.out.println("\n\t\t<FATAL>\n ************** ");
				warned = true;
				// Use a PrismLangException to get line numbers displayed
				msg = "Update " + (i+1) + " of command " + (l+1);
				msg += " of module \"" + moduleNames[m] + "\" doesn't do anything";
				mainLog.printWarning(new PrismLangException(msg, u.getUpdate(i)).getMessage());
if (DEBUG_TransUpd) System.out.println("During Mod2MTBDD_transUp_Pt1 ~ Line 2685 **************\n\t\t</FATAL>\n");
			}
			// multiply by probability/rate
			p = u.getProbability(i);
			if (p == null) p = Expression.Double(1.0);
if (DEBUG_TransUpd) System.out.println("In Mod2MTBDD.transUpdates (First one), The probability of the update is " + p + " - about to translateExpression on it...");

			pdd = translateExpression(p);
pdd.setPurpose("% DD of probability of update occurring. %");

//if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(pdd,"~About the JDDNode which represents probability of the update (" + p + "):",true );


if (DEBUG_TransUpd) System.out.println("Back in Mod2MTBDD.transUpdates (First one), The DD for the probability is: " + pdd + "\nAbout to apply TIMES to the update's DD with that DD");
			udd = JDD.Apply(JDD.TIMES, udd, pdd);

if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(udd,"~About the JDDNode which is the translation of this update after the probability is TIMESed: " + u.getUpdate(i),true );

			// check (again) for zero update
			if (!warned && udd.equals(JDD.ZERO)) {
				// Use a PrismLangException to get line numbers displayed
				msg = "Update " + (i+1) + " of command " + (l+1);
				msg += " of module \"" + moduleNames[m] + "\" doesn't do anything";
				mainLog.printWarning(new PrismLangException(msg, u.getUpdate(i)).getMessage());
			}
if (DEBUG_TransUpd) System.out.println("In Mod2MTBDD.transUpdates (First one), The resultant DD will be used to apply PLUS to the DD representing this command.");
			dd = JDD.Apply(JDD.PLUS, dd, udd);
dd.setPurpose("% DD representing command #" + (l+1) + " %");

if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(dd,"~About the JDDNode which is the interim translation of the command: " + u + " with substitutions: " + substitutions,true);
		}
		
if (DEBUG_TransUpd) System.out.println("</Mod2MTBDD_transUp_Pt1>");
		return dd;
	}

	// translate an update
	
// MODIFIED BY SHANE - to receive a Values specifying substitutions to be made for indexed-set access expressions
	private JDDNode translateUpdate(int m, Update c, boolean synch, JDDNode guard, Values substitutions) throws PrismException
	{
		int i, j, n, v, l, h;
		String s;
		JDDNode dd, tmp1, tmp2, indAccTmp, cl;
JDDNode rrr;	// Shane Debugging only
		
		// clear varsUsed flag array to indicate no vars used yet
		for (i = 0; i < numVars; i++) {	
			varsUsed[i] = false;
		}
		// take product of clauses
		dd = JDD.Constant(1);
		n = c.getNumElements();
if (DEBUG_TransUpd) {
	PrintDebugIndent();
	System.out.println("<TranslateUpdate_GROUP numUpdates='"+n+"'>");
	PrintDebugIndent();
	System.out.println("   transUpGrp  - PLACE 1: The Full Update is: " + c);
	if (DEBUG_TransUpd_ShowStack) {
//		(new Exception("Stack Trace ONLY - no actual exception")).printStackTrace(System.out);
	}
}
		for (i = 0; i < n; i++) {
if (DEBUG_TransUpd) {
	PrintDebugIndent();
	System.out.println(" <IterationForUpdateElement which='"+ (i+1) +" of " + n + "' expr=\"" + c.getElement(i) +"\">");
	System.out.println("    transUpGrp - PLACE 2 (iter "+i+"): Considering which variable is being updated.");
}
			
// SHANE INSERTED CONDITIONAL BRANCH:  to deal with indexed-set variable accesses.
			if (c.getVarIdent(i).isIndexedVariable() )
			{
				// get variable
				s = c.getVar(i);		// This will be the name of indexed set (without index expression)

				Expression accExpr = ((ExpressionIndexedSetAccess) c.getVarIdent(i)).getIndexExpression();
if (DEBUG_TransUpd) {
	PrintDebugIndent();
	System.out.println("\tCase 1 - it is an update to change an indexed variable's value: " + s);
	PrintDebugIndent();
	System.out.println("\tThe access expression is: " + accExpr);
}


				// Evaluate the expression to find the definite index to retrieve
				int indexToUse = 0;

				indexToUse = accExpr.evaluateInt(constantValues,substitutions);

if (DEBUG_TransUpd) {
	PrintDebugIndent();
System.out.println("place ZIRK: back in Modules2MTBDD.translateUpdate(): Apparently, the accessExpression : " + accExpr + " evaluates as " + indexToUse + " - BUT is that sensible ??");

//  System.out.println("The indAccTmp node is " + indAccTmp); 

}

				if (indexToUse < 0)	// Should not arise, because of filterCombinations.
					throw new PrismLangException("Invalid index given in indexed-set access attempt",accExpr);


				// construct the name of the definitive variable to be accessed
				s = c.getVar(i) + "[" + indexToUse + "]";
if (DEBUG_TransUpd) {
	PrintDebugIndent();
	System.out.println("\t The resultant exact variable for " + c.getElement(i) + " will be: " + s + " - DOES THAT MAKE SENSE???");
}
			} else {
				// get variable's name
				s = c.getVar(i);
if (DEBUG_TransUpd) {
	PrintDebugIndent();
	System.out.println("\tCase 2 - it is updating an Ordinary variable: " + s);
}
			}

// SHANE NOTE: THE FOLLOWING IS COMMON FOR BOTH Indexed AND Non-Indexed Variables:

			v = varList.getIndex(s);
if (DEBUG_TransUpd) {
	PrintDebugIndent();
	System.out.println("The variable is " + varList.getName(v) + ". Its position in varList is " + v);
}
			if (v == -1) {
				throw new PrismLangException("Unknown variable \"" + s + "\" in update", c.getVarIdent(i));
			}
			varsUsed[v] = true;
			// check if the variable to be modified is valid
			// (i.e. belongs to this module or is global)
			if (varList.getModule(v) != -1 && varList.getModule(v) != m) {
				throw new PrismLangException("Cannot modify variable \""+s+"\" from module \""+moduleNames[m]+"\"", c.getVarIdent(i));
			}
			// print out a warning if this update is in a command with a synchronising
			// action AND it modifies a global variable
			if (varList.getModule(v) == -1 && synch) {
				throw new PrismLangException("Synchronous command cannot modify global variable", c.getVarIdent(i));
			}
			// get some info on the variable
			l = varList.getLow(v);
			h = varList.getHigh(v);
			// create dd
if (DEBUG_TransUpd) {
	PrintDebugIndent();
	System.out.println("   transUp (Second one) - PLACE 3A: Having determined the variable of update element " + (i+1) + ", now we will prepare it by setting its vector elements.");
}
// SHANE NOTE: This basically creates a JDD where all the leafs are corresponding to the range of values of the variable. The j-l simply designates the pathway.
// and the varDDColVars tells it which JDD variables to utilise in constructing a pathway.
			tmp1 = JDD.Constant(0);
			for (j = l; j <= h; j++) {
				tmp1 = JDD.SetVectorElement(tmp1, varDDColVars[v], j-l, j);
if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(tmp1,"~About DD after calling SetVector for j of " + j + " for variable '" + varList.getName(v) + "'",true);
			}

if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(tmp1,"~After setting the vector elements for variable '"+ varList.getName(v) + "', the JDD (named 'tmp1') looks like this:",true);

			Expression calcExpr = c.getExpression(i).deepCopy();		//make a copy, so we can preserve orig, but do substitutions for current.
			
if (DEBUG_TransUpd) {
	PrintDebugIndent();
	System.out.println("   transUpGrp - PLACE 3B: After setting the vector elements of update element " + (i+1) +":" + c.getElement(i) + ")");
}

if (DEBUG_TransUpd) {
	System.out.println("<CalcExpr>\nBefore any substitutions, the calcExpression is: " + calcExpr );
}
			// Work out the effect of substituting any values for provided variable substitutions (and constants too).
			calcExpr = (Expression) calcExpr.evaluatePartially(constantValues,substitutions);

if (DEBUG_TransUpd) {
	System.out.println("</CalcExpr during='translateUpdate()'>\nAfter the substitutions, the calcExpression is: " + calcExpr);
	PrintDebugIndent();
	System.out.println("   Will call translateExpression using that calculation expression.");
	System.out.println("\n<TransCalcExpr>");
}


			tmp2 = translateExpression(calcExpr);
if (DEBUG_TransUpd) System.out.println("</TransCalcExpr during='translateUpdate()'>\n");

tmp2.setPurpose("% Apparently the translation of the calcExpr: " + calcExpr);

if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(tmp2,"~About the resultant DD (called 'tmp2') from translating the calcExpression: " + calcExpr,true);

if (DEBUG_TransUpd) {
	PrintDebugIndent();
	System.out.println("   Returned to transUp (Second one) - PLACE 4 (still dealing with this update element " + (i+1) + ": " + c.getElement(i) + " )");
	PrintDebugIndent();
	System.out.println("   after having translated the calculation expression (which was " + calcExpr + ")"  );
	PrintDebugIndent();
	System.out.println("   which is in tmp2.  Now to finish up tranlsating it (by integrating it with other updates of this command choice");
}

// SHANE - Needs to deeply consider what the following do:

			JDD.Ref(guard);
if (DEBUG_TransUpd) {
	PrintDebugIndent();
	System.out.println("<ThinkDeepAbout during='transUp (Second one)' >");
}
if (DEBUG_TransUpd) {
	PrintDebugIndent();
	System.out.println("  will apply TIMES to tmp2: (the current update-element, translated) and guard (received from transCommand), to store as new 'tmp2'.");
}
			tmp2 = JDD.Apply(JDD.TIMES, tmp2, guard);
if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(tmp2,"~About the result of TIMES of the calculation expression and the guard (called 'tmp2'):",true);

if (DEBUG_TransUpd) {
	PrintDebugIndent();
	System.out.println("  cl will be the result of applying EQUALS to tmp1 (the JDD representing the variable to be updated) and tmp2 (calc expr).");
}
			cl = JDD.Apply(JDD.EQUALS, tmp1, tmp2);
if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(cl,"~About the result of EQUALS between the tmp1 and tmp2 (called 'cl'):",true);

			JDD.Ref(guard);
if (DEBUG_TransUpd) {
	PrintDebugIndent();
	System.out.println("  cl will now be set to the result of applying TIMES to cl with guard.");
}
			cl = JDD.Apply(JDD.TIMES, cl, guard);
if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(cl,"~About the result of TIMES between the prior cl and the guard  (new 'cl' value):",true);

			// filter out bits not in range
			JDD.Ref(varColRangeDDs[v]);
if (DEBUG_TransUpd) {
	PrintDebugIndent();
	System.out.println("  cl will now be set to the result of applying TIMES to cl with varColRangeDDs[v="+v+"], which is currently: " + varColRangeDDs[v]);
}
			cl = JDD.Apply(JDD.TIMES, cl, varColRangeDDs[v]);
if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(cl,"~About the result of TIMES between the prior cl and varColRangeDDs for the variable '" + varList.getName(v) + "' (new 'cl'):",true);

			JDD.Ref(range);
if (DEBUG_TransUpd) {
	PrintDebugIndent();
	System.out.println("  cl will now be set to the result of applying TIMES to cl with range, which is currently: " + range);
}
			cl = JDD.Apply(JDD.TIMES, cl, range);
if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(cl,"~About the result of TIMES between the prior cl and 'range' (new 'cl'):",true);

if (DEBUG_TransUpd) {
	PrintDebugIndent();
	System.out.println("  now the resultant cl is used to apply TIMES of dd with cl, to be new/final value of dd. dd is currently: " + dd);
}

if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(dd,"~Re-display the DD representing the accumulation for each update element, before adding the above cl (old 'dd'):",true);

			dd = JDD.Apply(JDD.TIMES, dd, cl);

if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(dd,"~About the DD representing the accumulation for each update element, after incorporating element " + (i+1) + " (new 'dd')",true);

dd.setPurpose("Partially Translated version of " + c);
if (DEBUG_TransUpd) {
	PrintDebugIndent();
	System.out.println("</ThinkDeepAbout>");
}

if (DEBUG_TransUpd) {
	PrintDebugIndent();
	System.out.println("    transUp (Second one) - PLACE 5, end of iteration for that update element.");
	System.out.println("    dd at end of iteration "+i+" is: " + dd);
	PrintDebugIndent();
	System.out.println("</IterationForUpdateElement which='"+ (i+1) + " of " + n + "' was=\"" + c.getElement(i) + "\">");
}
		}
if (DEBUG_TransUpd) System.out.println("<AllUpdateElementsNowConsidered/>\n");

		// if a variable from this module or a global variable
		// does not appear in this update assume it does not change value
		// so multiply by its identity matrix
	// SHANE NOTE:  The Identity Matrix of a variable, is basically a JDD where each possible value of the variable is indicated by
	// the presence of a 1 at the leaves for paths that are possible outcomes;
if (DEBUG_TransUpd) {
	PrintDebugIndent();
	System.out.println("    transUp (Second one) - PLACE 6A: Remember, the Full Update is: " + c);
	PrintDebugIndent();
	System.out.println("    transUp (Second one) - PLACE 6B, about to loop i from 0 to numVars=" + numVars + ", to determine which identies to apply...");
}
		for (i = numVars-1; i >= 0; i--) {	// SHANE IS EXPERIMENTING WITH REVERSING THE ORDER.
//		for (i = 0; i < numVars; i++) {	
			if ((varList.getModule(i) == m || varList.getModule(i) == -1) && !varsUsed[i]) {
if (DEBUG_TransUpd || DEBUG_ShowEXCL_INCL) 
{
	PrintDebugIndent();
	System.out.print("    INCLUDING identity for i = " + i);
System.out.print(", name of var is \"" + varList.getName(i) +"\"");
System.out.println(", (varsUsed[i] is " + varsUsed[i] + ") by Applying TIMES with existing dd value");
if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(varIdentities[i],"~About JDD which is called 'varIdentities["+i+"]'",true);

}

				JDD.Ref(varIdentities[i]);
				dd = JDD.Apply(JDD.TIMES, dd, varIdentities[i]);
if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(dd,"~About JDD for the DD after incorporating varIdentities["+i+"]",true);
			}
else 
if (DEBUG_TransUpd || DEBUG_ShowEXCL_INCL) 
{
	PrintDebugIndent();
	System.out.print("    EXCLUDING identity for i = " + i);
System.out.print(", name of var is \"" + varList.getName(i) +"\"");
System.out.println(", varsUsed[i] is " + varsUsed[i] + " by NOT applying TIMES - doing nothing");
}
		}
dd.setPurpose("% Fully Translated version of " + c + " %");

if (DEBUG_SHANE_ShowStepsInTM) ShaneReportDD(dd,"~About the DD representing the final set of updates (after identities added for ALL other variables) for substitutions: " + substitutions,true);


if (DEBUG_TransUpd) {
	PrintDebugIndent();
	System.out.println("</TranslateUpdate_GROUP>");
}
		
		return dd;
	}

	// translate an arbitrary expression
	
	private JDDNode translateExpression(Expression e) throws PrismException
	{
if (DEBUG_TransUpd) {
	DebugIndent++;
	PrintDebugIndent(); System.out.println("<transExpr for='"+e+"' note='Simply calls expr2mtbdd [=SMC].checkExpDD()'");
}
/*SHANE*/expr2mtbdd.DebugIndent = DebugIndent;

		// pass this work onto the Expression2MTBDD object
		// states of interest = JDD.ONE = true = all possible states
//ORIG:		return expr2mtbdd.checkExpressionDD(e, JDD.ONE.copy());
//SHANE has broken it up for debugging output:
		JDDNode result;
		result = expr2mtbdd.checkExpressionDD(e, JDD.ONE.copy());
if (DEBUG_TransUpd) {
	PrintDebugIndent();
	System.out.println("</transExpr>");
	DebugIndent--;
}

//result.setPurpose("Result of checkExpressionDD for " + e);
		return result;

	}

	// build state and transition rewards
	
	private void computeRewards(SystemDDs sysDDs) throws PrismException
	{
		RewardStruct rs;
		int i, j, k, n;
		double d;
		String synch, s;
		JDDNode rewards, states, item;
		ComponentDDs compDDs;
		
		// how many reward structures?
		numRewardStructs = modulesFile.getNumRewardStructs();
		
		// initially rewards zero
		stateRewards = new JDDNode[numRewardStructs];
		transRewards = new JDDNode[numRewardStructs];
		for (j = 0; j < numRewardStructs; j++) {
			stateRewards[j] = JDD.Constant(0);
			sysDDs.ind.rewards[j] = JDD.Constant(0);
			for (i = 0; i < numSynchs; i++) sysDDs.synchs[i].rewards[j] = JDD.Constant(0);
		}
		
		// for each reward structure...
		for (j = 0; j < numRewardStructs; j++) {
			
			// get reward struct
			rs = modulesFile.getRewardStruct(j);
			
			// work through list of items in reward struct
			n = rs.getNumItems();
			for (i = 0; i < n; i++) {
				
				// translate states predicate and reward expression
				states = translateExpression(rs.getStates(i));
				rewards = translateExpression(rs.getReward(i));
				
				// first case: item corresponds to state rewards
				synch = rs.getSynch(i);
				if (synch == null) {
					// restrict rewards to relevant states
					item = JDD.Apply(JDD.TIMES, states, rewards);
					// check for negative rewards
					if ((d = JDD.FindMin(item)) < 0) {
						s = "Reward structure item contains negative rewards (" + d + ").";
						s += "\nNote that these may correspond to states which are unreachable.";
						s += "\nIf this is the case, try strengthening the predicate.";
						throw new PrismLangException(s, rs.getRewardStructItem(i));
					}
					// add to state rewards
					stateRewards[j] = JDD.Apply(JDD.PLUS, stateRewards[j], item);
				}
				
				// second case: item corresponds to transition rewards
				else {
					// work out which (if any) action this is for
					if ("".equals(synch)) {
						compDDs = sysDDs.ind;
					} else if ((k = synchs.indexOf(synch)) != -1) {
						compDDs = sysDDs.synchs[k];
					} else {
						throw new PrismLangException("Invalid action name \"" + synch + "\" in reward structure item", rs.getRewardStructItem(i));
					}
					// identify corresponding transitions
					// (for dtmcs/ctmcs, keep actual values - need to weight rewards; for mdps just store 0/1)
					JDD.Ref(compDDs.trans);
					if (modelType == ModelType.MDP) {
						item = JDD.GreaterThan(compDDs.trans, 0);
					} else {
						item = compDDs.trans;
					}
					// restrict to relevant states
					item = JDD.Apply(JDD.TIMES, item, states);
					// multiply by reward values
					item = JDD.Apply(JDD.TIMES, item, rewards);
					// check for negative rewards
					if ((d = JDD.FindMin(item)) < 0) {
						s = "Reward structure item contains negative rewards (" + d + ").";
						s += "\nNote that these may correspond to states which are unreachable.";
						s += "\nIf this is the case, try strengthening the predicate.";
						throw new PrismLangException(s, rs.getRewardStructItem(i));
					}
					// add result to rewards
					compDDs.rewards[j] = JDD.Apply(JDD.PLUS, compDDs.rewards[j], item);
				}
			}
		}
	}
	
	// calculate dd for initial state(s)
	
	private void buildInitialStates() throws PrismException
	{
		int i;
		JDDNode tmp;
		
if (DEBUG_SHANE) {
	PrintDebugIndent();
	System.out.println("<call method='buildInitialStates()'>");
}
		
		// first, handle case where multiple initial states specified with init...endinit
		if (modulesFile.getInitialStates() != null) {
			start = translateExpression(modulesFile.getInitialStates());
			JDD.Ref(range);
			start = JDD.And(start, range);
			if (start.equals(JDD.ZERO)) throw new PrismLangException("No initial states: \"init\" construct evaluates to false", modulesFile.getInitialStates());
		}
		// second, handle case where initial state determined by init values for variables
		else {
			start = JDD.Constant(1);
			for (i = 0; i < numVars; i++) {
if (DEBUG_SHANE)
	System.out.println("Setting initial-state value for this varDDRowVars: " + varDDRowVars[i] + " (which has all the JDD Nodes for variable: " + varList.getName(i)+")");
				tmp = JDD.SetVectorElement(JDD.Constant(0), varDDRowVars[i], varList.getStart(i)-varList.getLow(i), 1);
				start = JDD.And(start, tmp);
			}
		}
if (DEBUG_SHANE) {
	PrintDebugIndent();
	System.out.println("</call method='buildInitialStates()'>");
}
	}
	
	// symmetrification
	
	private void doSymmetry(Model model) throws PrismException
	{
		JDDNode tmp, transNew, reach, trans, transRewards[];
		int i, j, k, numSwaps;
		boolean done;
		long clock;
		String ss[];
		
		// parse symmetry reduction parameters
		ss = prism.getSettings().getString(PrismSettings.PRISM_SYMM_RED_PARAMS).split(" ");
		if (ss.length != 2) throw new PrismException ("Invalid parameters for symmetry reduction");
		try {
			numModulesBeforeSymm = Integer.parseInt(ss[0].trim());
			numModulesAfterSymm = Integer.parseInt(ss[1].trim());
		}
		catch (NumberFormatException e) {
			throw new PrismException("Invalid parameters for symmetry reduction");
		}

		clock = System.currentTimeMillis();
		
		// get a copies of model (MT)BDDs
		reach = model.getReach();
		JDD.Ref(reach);
		trans =  model.getTrans();
		JDD.Ref(trans);
		transRewards = new JDDNode[numRewardStructs];
		for (i = 0; i < numRewardStructs; i++) {
			transRewards[i] =  model.getTransRewards(i);
			JDD.Ref(transRewards[i]);
		}
		
		mainLog.print("\nApplying symmetry reduction...\n");
		
		//identifySymmetricModules();
		numSymmModules = numModules - (numModulesBeforeSymm + numModulesAfterSymm);
		computeSymmetryFilters(reach);
		
		// compute number of local states
// 		JDD.Ref(reach);
// 		tmp = reach;
// 		for (i = 0; i < numModules; i++) {
// 			if (i != numModulesBeforeSymm) tmp = JDD.ThereExists(tmp, moduleDDRowVars[i]);
// 		}
// 		tmp = JDD.ThereExists(tmp, globalDDRowVars);
// 		mainLog.println("Local states: " + (int)JDD.GetNumMinterms(tmp, moduleDDRowVars[numModulesBeforeSymm].n()));
// 		JDD.Deref(tmp);
		
		//ODDNode odd = ODDUtils.BuildODD(reach, allDDRowVars);
		//try {sparse.PrismSparse.NondetExport(trans, allDDRowVars, allDDColVars, allDDNondetVars, odd, Prism.EXPORT_PLAIN, "trans-full.tra"); } catch (FileNotFoundException e) {}
		
		mainLog.println("\nNumber of states before before symmetry reduction: " + model.getNumStatesString());
		mainLog.println("DD sizes before symmetry reduction:");
		
		// trans - rows
		mainLog.print("trans: ");
		mainLog.println(JDD.GetInfoString(trans, (modelType==ModelType.MDP)?(allDDRowVars.n()*2+allDDNondetVars.n()):(allDDRowVars.n()*2)));
		JDD.Ref(symm);
		trans = JDD.Apply(JDD.TIMES, trans, symm);
		//mainLog.print("trans (symm): ");
		//mainLog.println(JDD.GetInfoString(trans, (type==ModulesFile.NONDETERMINISTIC)?(allDDRowVars.n()*2+allDDNondetVars.n()):(allDDRowVars.n()*2)));
		
		// trans rewards - rows
		for (k = 0; k < numRewardStructs; k++) {
			mainLog.print("transrew["+k+"]: ");
			mainLog.println(JDD.GetInfoString(transRewards[k], (modelType==ModelType.MDP)?(allDDRowVars.n()*2+allDDNondetVars.n()):(allDDRowVars.n()*2)));
			JDD.Ref(symm);
			transRewards[k] = JDD.Apply(JDD.TIMES, transRewards[k], symm);
			//mainLog.print("transrew["+k+"] (symm): ");
			//mainLog.println(JDD.GetInfoString(transRewards[k], (type==ModulesFile.NONDETERMINISTIC)?(allDDRowVars.n()*2+allDDNondetVars.n()):(allDDRowVars.n()*2)));
		}
		
		mainLog.println("Starting quicksort...");
		done = false;
		numSwaps = 0;
		for (i = numSymmModules; i > 1 && !done; i--) {
			// store trans from previous iter
			JDD.Ref(trans);
			transNew = trans;
			for (j = 0; j < i-1; j++) {
				
				// are there any states where j+1>j+2?
				if (nonSymms[j].equals(JDD.ZERO)) continue;
				
				// identify offending block in trans
				JDD.Ref(transNew);
				JDD.Ref(nonSymms[j]);
				tmp = JDD.Apply(JDD.TIMES, transNew, JDD.PermuteVariables(nonSymms[j], allDDRowVars, allDDColVars));
				//mainLog.print("bad block: ");
				//mainLog.println(JDD.GetInfoString(tmp, (type==ModulesFile.NONDETERMINISTIC)?(allDDRowVars.n()*2+allDDNondetVars.n()):(allDDRowVars.n()*2)));
				
				if (tmp.equals(JDD.ZERO)) { JDD.Deref(tmp); continue; }
				numSwaps++;
				mainLog.println("Iteration "+(numSymmModules-i+1)+"."+(j+1));
				
				// swap
				tmp = JDD.SwapVariables(tmp, moduleDDColVars[numModulesBeforeSymm+j], moduleDDColVars[numModulesBeforeSymm+j+1]);
				//mainLog.print("bad block (swapped): ");
				//mainLog.println(JDD.GetInfoString(tmp, (type==ModulesFile.NONDETERMINISTIC)?(allDDRowVars.n()*2+allDDNondetVars.n()):(allDDRowVars.n()*2)));
				
				// insert swapped block
				JDD.Ref(nonSymms[j]);
				JDD.Ref(tmp);
				transNew = JDD.ITE(JDD.PermuteVariables(nonSymms[j], allDDRowVars, allDDColVars), JDD.Constant(0), JDD.Apply(JDD.PLUS, transNew, tmp));
				//mainLog.print("trans (symm): ");
				//mainLog.println(JDD.GetInfoString(transNew, (type==ModulesFile.NONDETERMINISTIC)?(allDDRowVars.n()*2+allDDNondetVars.n()):(allDDRowVars.n()*2)));
				JDD.Deref(tmp);
				
				for (k = 0; k < numRewardStructs; k++) {
					// identify offending block in trans rewards
					JDD.Ref(transRewards[k]);
					JDD.Ref(nonSymms[j]);
					tmp = JDD.Apply(JDD.TIMES, transRewards[k], JDD.PermuteVariables(nonSymms[j], allDDRowVars, allDDColVars));
					//mainLog.print("bad block: ");
					//mainLog.println(JDD.GetInfoString(tmp, (type==ModulesFile.NONDETERMINISTIC)?(allDDRowVars.n()*2+allDDNondetVars.n()):(allDDRowVars.n()*2)));
					
					// swap
					tmp = JDD.SwapVariables(tmp, moduleDDColVars[numModulesBeforeSymm+j], moduleDDColVars[numModulesBeforeSymm+j+1]);
					//mainLog.print("bad block (swapped): ");
					//mainLog.println(JDD.GetInfoString(tmp, (type==ModulesFile.NONDETERMINISTIC)?(allDDRowVars.n()*2+allDDNondetVars.n()):(allDDRowVars.n()*2)));
					
					// insert swapped block
					JDD.Ref(nonSymms[j]);
					JDD.Ref(tmp);
					transRewards[k] = JDD.ITE(JDD.PermuteVariables(nonSymms[j], allDDRowVars, allDDColVars), JDD.Constant(0), JDD.Apply(JDD.PLUS, transRewards[k], tmp));
					//mainLog.print("transrew["+k+"] (symm): ");
					//mainLog.println(JDD.GetInfoString(transRewards[k], (type==ModulesFile.NONDETERMINISTIC)?(allDDRowVars.n()*2+allDDNondetVars.n()):(allDDRowVars.n()*2)));
					JDD.Deref(tmp);
				}
			}
			
			if (transNew.equals(trans)) {
				done = true;
			}
			JDD.Deref(trans);
			trans = transNew;
		}
		
		// reset (MT)BDDs in model
		model.resetTrans(trans);
		for (i = 0; i < numRewardStructs; i++) {
			model.resetTransRewards(i, transRewards[i]);
		}
		
		// reset reach bdd, etc.
		JDD.Ref(symm);
		reach = JDD.And(reach, symm);
		
		model.setReach(reach);
		model.filterReachableStates();
		
		clock = System.currentTimeMillis() - clock;
		mainLog.println("Symmetry complete: " + (numSymmModules-i) + " iterations, " + numSwaps + " swaps, " + clock/1000.0 + " seconds");
	}

	private void computeSymmetryFilters(JDDNode reach) throws PrismException
	{
		int i;
		JDDNode tmp;
		
		// array for non-symmetric parts
		nonSymms = new JDDNode[numSymmModules-1];
		// dd for all symmetric states
		JDD.Ref(reach);
		symm = reach;
		// loop through symmetric module pairs
		for (i = 0; i < numSymmModules-1; i++) {
			// (locally) symmetric states, i.e. where i+1 <= i+2
			tmp = JDD.VariablesLessThanEquals(moduleDDRowVars[numModulesBeforeSymm+i], moduleDDRowVars[numModulesBeforeSymm+i+1]);
			// non-(locally)-symmetric states
			JDD.Ref(tmp);
			JDD.Ref(reach);
			nonSymms[i] = JDD.And(JDD.Not(tmp), reach);
			// all symmetric states
			symm = JDD.And(symm, tmp);
		}
	}

	// old version of computeSymmetryFilters()
	/*private void computeSymmetryFilters() throws PrismException
	{
		int i, j, k, n;
		String varNames[][] = null;
		JDDNode tmp;
		Expression expr, exprTmp;
		
		// get var names for each symm module
		n = modulesFile.getModule(numModulesBeforeSymm).getNumDeclarations();
		varNames = new String[numModules][];
		for (i = numModulesBeforeSymm; i < numModulesBeforeSymm+numSymmModules; i++) {
			varNames[i-numModulesBeforeSymm] = new String[n];
			j = 0;
			while (j < numVars && varList.getModule(j) != i) j++;
			for (k = 0; k < n; k++) {
				varNames[i-numModulesBeforeSymm][k] = varList.getName(j+k);
			}
		}
		
		// array for non-symmetric parts
		nonSymms = new JDDNode[numSymmModules-1];
		// dd for all symmetric states
		JDD.Ref(reach);
		symm = reach;
		// loop through symmetric module pairs
		for (i = 0; i < numSymmModules-1; i++) {
			// expression for (locally) symmetric states, i.e. where i+1 <= i+2
			expr = new ExpressionTrue();
			for (j = varNames[0].length-1; j >= 0 ; j--) {
				exprTmp = new ExpressionAnd();
				((ExpressionAnd)exprTmp).addOperand(new ExpressionBrackets(new ExpressionRelOp(new ExpressionVar(varNames[i][j], 0), "=", new ExpressionVar(varNames[i+1][j], 0))));
				((ExpressionAnd)exprTmp).addOperand(new ExpressionBrackets(expr));
				expr = exprTmp;
				exprTmp = new ExpressionOr();
				((ExpressionOr)exprTmp).addOperand(new ExpressionBrackets(new ExpressionRelOp(new ExpressionVar(varNames[i][j], 0), "<", new ExpressionVar(varNames[i+1][j], 0))));
				((ExpressionOr)exprTmp).addOperand(expr);
				expr = exprTmp;
			}
			mainLog.println(expr);
			// bdd for (locally) symmetric states, i.e. where i+1 <= i+2
			tmp = expr2mtbdd.translateExpression(expr);
			// non-(locally)-symmetric states
			JDD.Ref(tmp);
			JDD.Ref(reach);
			nonSymms[i] = JDD.And(JDD.Not(tmp), reach);
			// all symmetric states
			symm = JDD.And(symm, tmp);
		}
	}*/
}

//------------------------------------------------------------------------------
