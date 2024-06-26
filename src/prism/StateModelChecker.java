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

package prism;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import mtbdd.PrismMTBDD;
import dv.DoubleVector;
import jdd.*;
import odd.*;
import parser.*;
import parser.ast.*;
import parser.ast.ExpressionFilter.FilterOperator;
import parser.type.*;
import parser.visitor.ReplaceLabels;

// Base class for model checkers - does state-based evaluations (no temporal/probabilistic)

public class StateModelChecker extends PrismComponent implements ModelChecker
{
public static boolean DEBUG_ShowStatsForNodes = false;
public static boolean DEBUG_HIDE_CHECK = true;
public static boolean DEBUG_constr = true;		// Whether to show information during the constructor
public static boolean DEBUG = true;			// Whether to show default/higher importance general debugging trace statements
public static boolean DEBUG2 = true;			// Whether to show lesser importance general debugging traces
public static boolean DEBUG3_chkExprDD = true && !DEBUG_HIDE_CHECK;		// Whether to debug (trace) the checkExpressionDD() method
public static boolean DEBUG_ChkExpr = true && !DEBUG_HIDE_CHECK;	// Whether to show the checkExpression()'s debug messages
public static boolean DEBUG_ChkExpr_EISA = true;
public static boolean DEBUG_CEF = true;			// Whether to debug (trace) the behaviours of checkExpressionFilter()
public static boolean DEBUG_CheckIndSetAcc = true && !DEBUG_HIDE_CHECK;	// Whether to debug the checkIndexSetAccess() method.
public static boolean DEBUG_CheckIndSetAcc_Highlight = true ;//&& !DEBUG_HIDE_CHECK;	// Whether to debug the checkIndexSetAccess() method.
public static boolean DEBUG_chkBinOp = true && !DEBUG_HIDE_CHECK;	// Whether to debug the checkIndexSetAccess() method.
public static boolean DEBUG_ChkVar = true && !DEBUG_HIDE_CHECK;		// Whether to show checkExpressionVar() stages.
public static boolean DEBUG_WHICH = true;		// Whether to show WHICH method we are doing.
public static int DebugIndent = 0;
public static void PrintDebugIndent() { if (DEBUG) { for (int i = 0; i < DebugIndent; i++) System.out.print(" "); } }
public static int ChkExpCallSeqID = 0;			// A code to uniquely identify each call to CheckExpression()

public static int MAX_TREE_TO_DISPLAY = 1200;

public static void ShaneReportDD(String message, JDDNode toReport)
{
/*	System.out.println("======");
	System.out.println(message);
	System.out.println("  GetNumNodes():      " + JDD.GetNumNodes(toReport));
	System.out.println("  GetNumTerminals():  " + JDD.GetNumTerminals(toReport));
        System.out.println("  GetNumPaths():      " + JDD.GetNumPaths(toReport));
	System.out.println(message);
		if (JDD.GetNumPaths(toReport) > MAX_TREE_TO_DISPLAY)
		   System.out.println("Tree would be too large to report"); 
		else 
		   toReport.ShaneShowChildren();
*/
}

public static void ShowCheckCalls()
{
System.out.println("SMC.ShowCheckCalls - NOT ENABLING DEBUGGING OF CALLS to some of the StateModelChecker class methods.");
/*DEBUG_HIDE_CHECK = false;		
	DEBUG_ChkExpr = true && !DEBUG_HIDE_CHECK;
	DEBUG3_chkExprDD = true && !DEBUG_HIDE_CHECK;
	DEBUG_ChkVar = true && !DEBUG_HIDE_CHECK;
*/	
}


	// PRISM stuff
	protected Prism prism;

	// Properties file
	protected PropertiesFile propertiesFile;

	// Constant values
	protected Values constantValues;

	// Model info
	protected Model model;
	protected VarList varList;
	protected JDDNode trans;
	protected JDDNode trans01;
	protected JDDNode transActions;
	protected JDDNode start;
	protected JDDNode reach;
	protected ODDNode odd;
	protected JDDVars allDDRowVars;
	protected JDDVars allDDColVars;
	protected JDDVars[] varDDRowVars;

	// The filter to be applied to the current property
	protected Filter currentFilter;

	// The result of model checking will be stored here
	protected Result result;

	// Options:

	// Which engine to use
	protected int engine;
	// Parameter for termination criterion
	protected double termCritParam;
	// Use interval iteration?
	protected boolean doIntervalIteration;
	// Verbose mode?
	protected boolean verbose;
	// Store the final results vector after model checking?
	protected boolean storeVector = false; 
	// Generate/store a strategy during model checking?
	protected boolean genStrat = false;

	// Constructor

	public StateModelChecker(Prism prism, Model m, PropertiesFile pf) throws PrismException
	{
		// Initialise PrismComponent
		super(prism);
if (DEBUG_constr) System.out.println("In the FIRST constructor of StateModelChecker");

		// Initialise
		this.prism = prism;
		model = m;
		propertiesFile = pf;
		constantValues = new Values();
		constantValues.addValues(model.getConstantValues());
		if (pf != null)
			constantValues.addValues(pf.getConstantValues());
		varList = model.getVarList();
		trans = model.getTrans();
		trans01 = model.getTrans01();
		transActions = model.getTransActions();
		start = model.getStart();
		reach = model.getReach();
		odd = model.getODD();
		allDDRowVars = model.getAllDDRowVars();
		allDDColVars = model.getAllDDColVars();
		varDDRowVars = model.getVarDDRowVars();

		// Inherit some options from parent Prism object
		// Store locally and/or pass onto engines
		engine = prism.getEngine();
		termCritParam = prism.getTermCritParam();
		doIntervalIteration = prism.getSettings().getBoolean(PrismSettings.PRISM_INTERVAL_ITER);
		verbose = prism.getVerbose();
		storeVector = prism.getStoreVector();
		genStrat = prism.getGenStrat();
	}

	/**
	 * Additional constructor for creating stripped down StateModelChecker for
	 * expression to MTBDD conversions (no colum variables, no transition function, ...).
	 * <br>
	 * The dummy model constructed for these purposes has to be cleared by calling
	 * {@code clearDummyModel()} later.
	 * <br>[ REFS: <i>none</i>, DEREFS: <i>none</i> ]
	 */
// SHANE-NOTE:   The elements of varList must correspond to the same element position of varDDRowVars (which are sets of DDs, being those DDs needed for each original prism variable) 
	public StateModelChecker(Prism prism, VarList varList, JDDVars allDDRowVars, JDDVars[] varDDRowVars, Values constantValues) throws PrismException
	{
		// Initialise PrismComponent
		super(prism);
if (DEBUG_constr) System.out.println("In the SECOND constructor of StateModelChecker");


		// Initialise
		this.prism = prism;
		this.varList = varList;
		this.varDDRowVars = varDDRowVars;
		this.constantValues = constantValues;
		// Create dummy model
		reach = null;
		model = new ProbModel(JDD.Constant(0),  // trans
		                      JDD.Constant(0),  // start
		                      new JDDNode[] {}, // state-rew
		                      new JDDNode[] {}, // trans-rew
		                      null,             // rewardStructNames
		                      allDDRowVars.copy(), // allDDRowVars
		                      new JDDVars(),    // allDDColVars
		                      null,             // ddVarNames
		                      0,                // numModules
		                      null,             // moduleNames
		                      null,             // moduleRowVars
		                      null,             // moduleColVars
		                      varDDRowVars.length, // numVars
		                      varList,          // varList
		                      JDDVars.copyArray(varDDRowVars), // varDDRowVars
		                      null,             // varDDColVars
		                      constantValues    // constantValues
		                     );
	}

	/**
	 * Create a model checker (a subclass of this one) for a given model type
	 */
	public static StateModelChecker createModelChecker(ModelType modelType, Prism prism, Model model, PropertiesFile propertiesFile) throws PrismException
	{
if (DEBUG_constr) System.out.println("in SMC.createModelChecker with 4 parameters");
		StateModelChecker mc = null;
		switch (modelType) {
		case DTMC:
if (DEBUG_constr) System.out.println(" modelType is DTMC, making a ProbModelChecker");
			mc = new ProbModelChecker(prism, model, propertiesFile);
			break;
		case MDP:
if (DEBUG_constr) System.out.println(" modelType is MDP, making a NondetModelChecker");
			mc = new NondetModelChecker(prism, model, propertiesFile);
			break;
		case CTMC:
if (DEBUG_constr) System.out.println(" modelType is CTMC, making a StochModelChecker");
			mc = new StochModelChecker(prism, model, propertiesFile);
			break;
		default:
			throw new PrismException("Cannot create model checker for model type " + modelType);
		}
		return mc;
	}

	/**
	 * Create a model checker (a subclass of this one) for a given model,
	 * deducing the model type and reusing the PropertiesFile and Prism objects
	 * of the current model checker.
	 */
	public ModelChecker createModelChecker(Model newModel) throws PrismException
	{
		return createModelChecker(newModel.getModelType(), prism, newModel, propertiesFile);
	}

	/**
	 * Clean up the dummy model created when using the abbreviated constructor
	 */
	public void clearDummyModel()
	{
		model.clear();
	}

private static int InvokeCount_check = 0;
	@Override
	public Result check(Expression expr) throws PrismException
	{
		long timer = 0;
		StateValues vals;
		String resultString;

		// Create storage for result
		result = new Result();

InvokeCount_check++;
		// Remove any existing filter info
		currentFilter = null;
System.out.println("\n'in prism.SMC::check() @ PLACE SMCChk-1 (the start of the method)");
System.out.println("<SMC_Check invocation='"+InvokeCount_check+"' expr='" + expr + "'>");

		// Wrap a filter round the property, if needed
		// (in order to extract the final result of model checking) 
		ExpressionFilter exprFilter = ExpressionFilter.addDefaultFilterIfNeeded(expr, model.getNumStartStates() == 1);
		// And if we need to store a copy of the results vector, make a note of this
		if (storeVector) {
			exprFilter.setStoreVector(true);
		}
		expr = exprFilter;
		
		// Do model checking and store result vector
		timer = System.currentTimeMillis();

		// check expression, statesOfInterest = all reachable states
System.out.println("in prism.SMC::check() @ PLACE SMCChk-2 : I am about to call checkExpression for " + expr);
System.out.println("passing as the 'statesOfInterest' a copy of model.getReach() [so, all reachable states]");

		vals = checkExpression(expr, model.getReach().copy());

System.out.println("in prism.SMC::check() @ PLACE SMCChk-3 : I have now returned from call of checkExpression for " + expr);

		timer = System.currentTimeMillis() - timer;
		System.out.println("\nTime for model checking: " + timer / 1000.0 + " seconds.");
PrismNative.ShaneMakeTopReportMsg("check "+InvokeCount_check+" has ended");

		// Print result to log
		resultString = "Result";
		if (!("Result".equals(expr.getResultName())))
			resultString += " (" + expr.getResultName().toLowerCase() + ")";
		resultString += ": " + result.getResultString();
		System.out.print("\n" + resultString + "\n");

		// Clean up
		vals.clear();
System.out.println("\nEnd of SMC.check()\n</SMC_Check invocation='"+InvokeCount_check+"' >\n");
		// Return result
		return result;
	}

	@Override
	public StateValues checkExpression(Expression expr, JDDNode statesOfInterest) throws PrismException
	{
int myCallSeqID = ChkExpCallSeqID++;

if (DEBUG_ChkExpr) PrintDebugIndent();
if (DEBUG_ChkExpr && !DEBUG_HIDE_CHECK) {
  System.out.println("<CheckExpr callseq='" + myCallSeqID + "' comment=\'StateModelChecker.checkExpr() called for expression: " + expr + "\'>");
  DebugIndent++;
  System.out.println("In SMC.checkExpr() PLACE CE-1.\n");
//  System.out.println("\tstatesOfInterest is this JDDNode: " + statesOfInterest);
}
		StateValues res;

		// If-then-else
		if (expr instanceof ExpressionITE) {
if (DEBUG_ChkExpr) {PrintDebugIndent(); System.out.println("treating as ExpressionITE");}
			res = checkExpressionITE((ExpressionITE) expr, statesOfInterest);
if (DEBUG_ChkExpr) {PrintDebugIndent(); System.out.println("smc.chkExpr: finished treating "+expr+" as ExpressionITE");}
		}
		// Binary ops
		else if (expr instanceof ExpressionBinaryOp) {
if (DEBUG_ChkExpr) {PrintDebugIndent(); System.out.println("treating as ExpressionBinaryOp");}
			res = checkExpressionBinaryOp((ExpressionBinaryOp) expr, statesOfInterest);
if (DEBUG_ChkExpr) {PrintDebugIndent(); System.out.println("smc.chkExpr: finished treating "+expr+" as ExpressionBinaryOp");}
		}
		// Unary ops
		else if (expr instanceof ExpressionUnaryOp) {
if (DEBUG_ChkExpr) {PrintDebugIndent(); System.out.println("treating as ExpressionUnaryOp");}
			res = checkExpressionUnaryOp((ExpressionUnaryOp) expr, statesOfInterest);
if (DEBUG_ChkExpr) {PrintDebugIndent(); System.out.println("smc.chkExpr: finished treating "+expr+" as ExpressionUnaryOp");}
		}
		// Functions
		else if (expr instanceof ExpressionFunc) {
if (DEBUG_ChkExpr) {PrintDebugIndent(); System.out.println("treating as ExpressionFunc");}			
			res = checkExpressionFunc((ExpressionFunc) expr, statesOfInterest);
if (DEBUG_ChkExpr) {PrintDebugIndent(); System.out.println("smc.chkExpr: finished treating "+expr+" as ExpressionFunc");}			
		}
		// Identifier for accessing an indexed set
		else if (expr instanceof ExpressionIndexedSetAccess)		// ADDED BY SHANE
		{
if (DEBUG_ChkExpr || DEBUG_ChkExpr_EISA) {PrintDebugIndent(); System.out.println("smc.chkExpr: treating the expr "+expr+" as *****   ExpressionIndexedSetAccess ***** (by calling chkExprIndSetAcc)");}
			res = checkExpressionIndSetAcc((ExpressionIndexedSetAccess) expr,statesOfInterest);
if (DEBUG_ChkExpr || DEBUG_ChkExpr_EISA) {PrintDebugIndent(); System.out.println("smc.chkExpr: finished treating "+expr+" as *****   ExpressionIndexedSetAccess *****");}
		}
		// Identifiers (non-indexed)
		else if (expr instanceof ExpressionIdent) {
			// Should never happen
			throw new PrismException("Unknown identifier \"" + ((ExpressionIdent) expr).getName() + "\"");
		}
		// Literals
		else if (expr instanceof ExpressionLiteral) {
if (DEBUG_ChkExpr) {PrintDebugIndent(); System.out.println("treating as ExpressionLiteral");}
			res = checkExpressionLiteral((ExpressionLiteral) expr, statesOfInterest);
if (DEBUG_ChkExpr) {PrintDebugIndent(); System.out.println("smc.chkExpr: finished treating "+expr+" as ExpressionLiteral");}
		}
		// Constants
		else if (expr instanceof ExpressionConstant) {
if (DEBUG_ChkExpr) {PrintDebugIndent(); System.out.println("treating as ExpressionConstant");}
			res = checkExpressionConstant((ExpressionConstant) expr, statesOfInterest);
if (DEBUG_ChkExpr) {PrintDebugIndent(); System.out.println("smc.chkExpr: finished treating "+expr+" as ExpressionConstant");}
		}
		// Formulas
		else if (expr instanceof ExpressionFormula) {
if (DEBUG_ChkExpr) {PrintDebugIndent(); System.out.println("treating as ExpressionFormula");}
			// This should have been defined or expanded by now.
			if (((ExpressionFormula) expr).getDefinition() != null) {
if (DEBUG_ChkExpr) {PrintDebugIndent(); System.out.println("smc.chkExpr: finished treating "+expr+" as ExpressionFormula");}
				return checkExpression(((ExpressionFormula) expr).getDefinition(), statesOfInterest);
			} else
				throw new PrismException("Unexpanded formula \"" + ((ExpressionFormula) expr).getName() + "\"");
		}
		// Variables
		else if (expr instanceof ExpressionVar) {
if (DEBUG_ChkExpr) {PrintDebugIndent(); System.out.println("treating as ExpressionVar");}
			res = checkExpressionVar((ExpressionVar) expr, statesOfInterest);
if (DEBUG_ChkExpr) {PrintDebugIndent(); System.out.println("smc.chkExpr: finished treating "+expr+" as ExpressionVar");}
		}
		// Labels
		else if (expr instanceof ExpressionLabel) {
if (DEBUG_ChkExpr) {PrintDebugIndent(); System.out.println("treating as ExpressionLabel");}
			res = checkExpressionLabel((ExpressionLabel) expr, statesOfInterest);
if (DEBUG_ChkExpr) {PrintDebugIndent(); System.out.println("smc.chkExpr: finished treating "+expr+" as ExpressionLabel");}
		}
		// Property refs
		else if (expr instanceof ExpressionProp) {
if (DEBUG_ChkExpr) {PrintDebugIndent(); System.out.println("treating as ExpressionProp");}
			res = checkExpressionProp((ExpressionProp) expr, statesOfInterest);
if (DEBUG_ChkExpr) {PrintDebugIndent(); System.out.println("smc.chkExpr: finished treating "+expr+" as ExpressionProp");}
		}
		// Filter
		else if (expr instanceof ExpressionFilter) {
if (DEBUG_ChkExpr) {PrintDebugIndent(); System.out.println("treating as ExpressionFilter");}
			res = checkExpressionFilter((ExpressionFilter) expr, statesOfInterest);
if (DEBUG_ChkExpr) {PrintDebugIndent(); System.out.println("smc.chkExpr: finished treating "+expr+" as ExpressionFilter");}
		}
		// Anything else - error
		else {
if (DEBUG_ChkExpr) {PrintDebugIndent(); System.out.println("expr was not recognised - treating as an error - cannot check it.");}
			JDD.Deref(statesOfInterest);
			throw new PrismException("Couldn't check " + expr.getClass());
		}

if (DEBUG_ChkExpr) {System.out.println("\n\n"); PrintDebugIndent(); System.out.println("Back in SMC.checkExpr() PLACE CE-2 for callseq='" + myCallSeqID + "' for expression: " + expr + " we now have a value for 'res'.");}

if (DEBUG_ShowStatsForNodes)
{
	JDDNode rrr = res.convertToStateValuesMTBDD().getJDDNode().copy();
	ShaneReportDD("[in StateModelChecker] ~About the JDDNode created for this expression: " + expr, rrr);
	JDD.Deref(rrr);
}
		// Filter out non-reachable states from solution
		// (only necessary for symbolically stored vectors)
		// (skip if reach is null, e.g. if just being used to convert arbitrary expressions)
		if (res instanceof StateValuesMTBDD && reach != null)
		{
if (DEBUG_ChkExpr) { 
  PrintDebugIndent(); System.out.println("RES-CASE 1:\t 'res' IS an instance of StateValuesMTBDD (i.e. symbolic),\n\t\tAND the reach is not null,");
  System.out.println("\t\twhich means, we WILL FILTER OUT non-reachable states.");
}
// The next line is ORIGINAL from the github version (i.e. not part of debugging):
			res.filter(reach);
		}
else if (res instanceof StateValuesMTBDD) {
  if (DEBUG_ChkExpr) { PrintDebugIndent(); System.out.println("RES-CASE 2: 'res' IS an instance of StateValuesMTBDD (i.e. symbolic),  BUT reach is null - not filtering.");
  }
} else {
  if (DEBUG_ChkExpr) { PrintDebugIndent(); System.out.println("RES-CASE 3: 'res' is NOT an instance of StateValuesMTBDD, hence 'res' is explicit.");
  }
}

if (DEBUG_ChkExpr) System.out.println("In SMC.checkExpression() - PLACE CE-3 (Ending call)");
if (DEBUG_ChkExpr) DebugIndent--;
if (DEBUG_ChkExpr) PrintDebugIndent();
if (DEBUG_ChkExpr) System.out.println("</CheckExpr callseq='" + myCallSeqID + "' comment=\"StateModelChecker.checkExpr() finished for expression: " + expr + "\" >\n");

		return res;
	}

	@Override
	public JDDNode checkExpressionDD(Expression expr, JDDNode statesOfInterest) throws PrismException
	{
//ORIG:		StateValuesMTBDD sv = checkExpression(expr, statesOfInterest).convertToStateValuesMTBDD();
// SHANE has now broken down that into smaller steps, in order for debugging trace outputs:
		StateValuesMTBDD sv;
		
if (DEBUG3_chkExprDD) {
  StackTraceElement[] STACK = (new Exception()).getStackTrace();
  PrintDebugIndent(); System.out.println("\n<ChkDD comment='in SMC.ChkExprDD() for '" + expr + "'\ncalledFrom='"+STACK[1]+"'>\n");
  DebugIndent++;
}
if (DEBUG3_chkExprDD) {PrintDebugIndent(); System.out.println("1. [In SMC.ChkDD for '"+expr+"'] - Calling checkExpression for it (to get a StateValues)...");}

		StateValues interimStep = checkExpression(expr, statesOfInterest);

if (DEBUG3_chkExprDD) {
	PrintDebugIndent(); System.out.println("2. [In SMC.ChkExprDD for '"+expr+"'] - the StateValues returned from checkExpression is:");
	if (interimStep != null) interimStep.print(mainLog);
	else System.out.println(" NULL");
}

if (DEBUG3_chkExprDD) {PrintDebugIndent(); System.out.println("\n3. [In SMC.ChkExprDD for '"+expr+"'] - now calling convertToStateValuesMTBDD()");}
		sv = interimStep.convertToStateValuesMTBDD();

if (DEBUG3_chkExprDD) {
if (DEBUG3_chkExprDD) {PrintDebugIndent(); System.out.println("\n4. [In ChkExprDD for '"+expr+"'] - returned from calling convertToStateValuesMTBDD()\nResult: ");}
	if (sv != null) sv.print(mainLog);
	else System.out.println(" is null - nothing to print!");
}

		JDDNode result = sv.getJDDNode().copy();
if (DEBUG_ShowStatsForNodes)
{
	ShaneReportDD("[in StateModelChecker] ~About the JDDNode created for this expression: " + expr, result);
}
		sv.clear();

if (DEBUG3_chkExprDD) {
  DebugIndent--;
  PrintDebugIndent(); System.out.println("</ChkDD for='"+expr+"'>\n");
//Exception eee = new Exception("STACKTRACE ONLY (at very end of ChkDD)");
//eee.printStackTrace(System.out);
}

		return result;
	}

	// -----------------------------------------------------------------------------------
	// Check method for each operator
	// -----------------------------------------------------------------------------------

	/*
	 * These check methods (and similar ones in subclasses of this class) return
	 * a StateValues object which is a vector, over all states, of values. This can
	 * be represented either symbolically (as an (MT)BDD, encapsulated in a StateValuesMTBDD
	 * object) or explicitly (as an array of doubles, encapsulated in a StateValuesDV
	 * object, containing a DoubleVector object).
	 * 
	 * It is always possible to convert between these two forms but this will not always be
	 * efficient. In particular, we want to avoid creating: (a) explicit vectors for very large
	 * models where the vector can only be feasibly stored as an MTBDD; (b) and symbolic
	 * vectors for irregular vectors which are small enough to be stored explicitly but would
	 * blow up as an MTBDD.
	 * 
	 * Various schemes (and user preferences/configurations) are possible. Currently:
	 * 
	 * - simple, atomic expressions (constants, variable references, etc.) are
	 *   created symbolically (since this is easy and usually efficient)
	 * - for arithmetic operations, the result is stored explicitly if one or
	 *   more of its operands is explicit and symbolic otherwise
	 * - operators with Boolean results are always stored symbolically
	 * 
	 * So, currently the only time that explicit vectors are created anew is (in subclasses
	 * of this model checker) for e.g. the result of P=?, R=?, S=?, etc. operators. And for
	 * these operators, the result will be symbolic if the MTBDD engine is being used (or
	 * in some cases where the answer is trivial, e.g. 1 for all states). For P>p etc.
	 * properties, the vector will be stored symbolically since values are Booleans. 
	 */

	/**
	 * Check an 'if-then-else' expression.
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 */
	protected StateValues checkExpressionITE(ExpressionITE expr, JDDNode statesOfInterest) throws PrismException
	{
		StateValues res1 = null, res2 = null, res3 = null;
		JDDNode dd, dd1, dd2, dd3;
		DoubleVector dv2, dv3;

		// Check operands recursively
		try {
			res1 = checkExpression(expr.getOperand1(), statesOfInterest.copy());
			res2 = checkExpression(expr.getOperand2(), statesOfInterest.copy());
			res3 = checkExpression(expr.getOperand3(), statesOfInterest.copy());
		} catch (PrismException e) {
			if (res1 != null)
				res1.clear();
			if (res2 != null)
				res2.clear();
			throw e;
		} finally {
			JDD.Deref(statesOfInterest);
		}

		// Operand 1 is boolean so should be symbolic
		dd1 = res1.convertToStateValuesMTBDD().getJDDNode();

		// If both operands 2/3 are symbolic, result will be symbolic
		if (res2 instanceof StateValuesMTBDD && res3 instanceof StateValuesMTBDD) {
			dd2 = ((StateValuesMTBDD) res2).getJDDNode();
			dd3 = ((StateValuesMTBDD) res3).getJDDNode();
			dd = JDD.ITE(dd1, dd2, dd3);
			return new StateValuesMTBDD(dd, model);
		}
		// Otherwise result will be explicit
		else {
			dv2 = res2.convertToStateValuesDV().getDoubleVector();
			dv2.filter(dd1, allDDRowVars, odd);
			dv3 = res3.convertToStateValuesDV().getDoubleVector();
			dd1 = JDD.Not(dd1);
			dv3.filter(dd1, allDDRowVars, odd);
			dv2.add(dv3);
			dv3.clear();
			JDD.Deref(dd1);
			return new StateValuesDV(dv2, model);
		}
	}

	/**
	 * Check a binary operator.
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 */
	protected StateValues checkExpressionBinaryOp(ExpressionBinaryOp expr, JDDNode statesOfInterest) throws PrismException
	{
		StateValues res1 = null, res2 = null;
		JDDNode dd, dd1, dd2;
		DoubleVector dv1, dv2;
		int i, n, op = expr.getOperator();
if (DEBUG_chkBinOp) System.out.println("in chkExprBinOp for " + expr + " - place 1.");

		// Optimisations are possible for relational operators
		// (note dubious use of knowledge that op IDs are consecutive)
		if (op >= ExpressionBinaryOp.EQ && op <= ExpressionBinaryOp.LE) {
if (DEBUG_chkBinOp) System.out.println("in chkExprBinOp for " + expr + " - place 1-B.");
			return checkExpressionRelOp(op, expr.getOperand1(), expr.getOperand2(), statesOfInterest);
		}

		// Check operands recursively
		try {
if (DEBUG_chkBinOp) System.out.println("in chkExprBinOp for " + expr + " - place 2-A - about to checkExpr the first operand: " + expr.getOperand1());
			res1 = checkExpression(expr.getOperand1(), statesOfInterest.copy());
if (DEBUG_chkBinOp) System.out.println("in chkExprBinOp for " + expr + " - place 2-B - completed checkExpr of the first operand: " + expr.getOperand1());
if (DEBUG_chkBinOp) System.out.println("in chkExprBinOp for " + expr + " - place 2-C - about to checkExpr for the second operand: " + expr.getOperand2());
			res2 = checkExpression(expr.getOperand2(), statesOfInterest.copy());
if (DEBUG_chkBinOp) System.out.println("in chkExprBinOp for " + expr + " - place 2-D - completed checkExpr for the second operand: " + expr.getOperand2());
		} catch (PrismException e) {
			if (res1 != null)
				res1.clear();
			throw e;
		} finally {
			JDD.Deref(statesOfInterest);
		}

		// If both operands are symbolic, result will be symbolic
		if (res1 instanceof StateValuesMTBDD && res2 instanceof StateValuesMTBDD) {
if (DEBUG_chkBinOp) System.out.println("in chkExprBinOp for " + expr + " - place 3-A - both operands are SYMBOLIC.");
			dd1 = ((StateValuesMTBDD) res1).getJDDNode();
			dd2 = ((StateValuesMTBDD) res2).getJDDNode();
			// Apply operation
			switch (op) {
			case ExpressionBinaryOp.IMPLIES:
if (DEBUG_chkBinOp) System.out.println("It is IMPLIES");
				dd = JDD.Or(JDD.Not(dd1), dd2);
if (DEBUG_chkBinOp) System.out.println("End of IMPLIES");
				break;
			case ExpressionBinaryOp.IFF:
if (DEBUG_chkBinOp) System.out.println("It is IFF");
				dd = JDD.Not(JDD.Xor(dd1, dd2));
if (DEBUG_chkBinOp) System.out.println("End of IFF");
				break;
			case ExpressionBinaryOp.OR:
if (DEBUG_chkBinOp) System.out.println("It is OR");
				dd = JDD.Or(dd1, dd2);
if (DEBUG_chkBinOp) System.out.println("End of OR");
				break;
			case ExpressionBinaryOp.AND:
if (DEBUG_chkBinOp) System.out.println("It is AND");
				dd = JDD.And(dd1, dd2);
if (DEBUG_chkBinOp) System.out.println("End of AND");
				break;
			case ExpressionBinaryOp.PLUS:
if (DEBUG_chkBinOp) System.out.println("It is PLUS");
				dd = JDD.Apply(JDD.PLUS, dd1, dd2);
dd.setPurpose("% The DD resulting from APPLY:PLUS of " + dd1.getPurpose() +" with " +dd2.getPurpose());
if (DEBUG_chkBinOp) System.out.println("End of PLUS");
				break;
			case ExpressionBinaryOp.MINUS:
if (DEBUG_chkBinOp) System.out.println("It is MINUS");
				dd = JDD.Apply(JDD.MINUS, dd1, dd2);
if (DEBUG_chkBinOp) System.out.println("End of MINUS");
				break;
			case ExpressionBinaryOp.TIMES:
if (DEBUG_chkBinOp) System.out.println("It is TIMES");
				dd = JDD.Apply(JDD.TIMES, dd1, dd2);
if (DEBUG_chkBinOp) System.out.println("End of TIMES");
				break;
			case ExpressionBinaryOp.DIVIDE:
if (DEBUG_chkBinOp) System.out.println("It is DIVIDE");
				dd = JDD.Apply(JDD.DIVIDE, dd1, dd2);
if (DEBUG_chkBinOp) System.out.println("End of DIVIDE");
				break;
			default:
				throw new PrismException("Unknown binary operator");
			}
			return new StateValuesMTBDD(dd, model);
		}
		// Otherwise result will be explicit
		else {
if (DEBUG_chkBinOp) System.out.println("in chkExprBinOp for " + expr + " - place 4-A - either or both operands are explicit, so result will be explicit.");
			dv1 = res1.convertToStateValuesDV().getDoubleVector();
			dv2 = res2.convertToStateValuesDV().getDoubleVector();
			n = dv1.getSize();
			// Apply operation
			switch (op) {
			case ExpressionBinaryOp.IMPLIES:
			case ExpressionBinaryOp.OR:
			case ExpressionBinaryOp.AND:
				throw new PrismException("Internal error: Explicit evaluation of Boolean");
				//for (i = 0; i < n; i++) dv1.setElement(i, (!(dv1.getElement(i)>0) || (dv2.getElement(i)>0)) ? 1.0 : 0.0);
				//for (i = 0; i < n; i++) dv1.setElement(i, ((dv1.getElement(i)>0) || (dv2.getElement(i)>0)) ? 1.0 : 0.0);
				//for (i = 0; i < n; i++) dv1.setElement(i, ((dv1.getElement(i)>0) && (dv2.getElement(i)>0)) ? 1.0 : 0.0);
			case ExpressionBinaryOp.PLUS:
if (DEBUG_chkBinOp) System.out.println("It is PLUS");
				for (i = 0; i < n; i++)
					dv1.setElement(i, dv1.getElement(i) + dv2.getElement(i));
if (DEBUG_chkBinOp) System.out.println("End of PLUS");
				break;
			case ExpressionBinaryOp.MINUS:
if (DEBUG_chkBinOp) System.out.println("It is MINUS");
				for (i = 0; i < n; i++)
					dv1.setElement(i, dv1.getElement(i) - dv2.getElement(i));
if (DEBUG_chkBinOp) System.out.println("End of MINUS");
				break;
			case ExpressionBinaryOp.TIMES:
if (DEBUG_chkBinOp) System.out.println("It is TIMES");
				for (i = 0; i < n; i++)
					dv1.setElement(i, dv1.getElement(i) * dv2.getElement(i));
if (DEBUG_chkBinOp) System.out.println("End of TIMES");
				break;
			case ExpressionBinaryOp.DIVIDE:
if (DEBUG_chkBinOp) System.out.println("It is DIVIDE");
				for (i = 0; i < n; i++)
					dv1.setElement(i, dv1.getElement(i) / dv2.getElement(i));
if (DEBUG_chkBinOp) System.out.println("End of DIVIDE");
				break;
			default:
				throw new PrismException("Unknown binary operator");
			}
			dv2.clear();
			return new StateValuesDV(dv1, model);
		}
	}

	/**
	 * Check a relational operator (=, !=, >, >=, < <=).
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 */
	protected StateValues checkExpressionRelOp(int op, Expression expr1, Expression expr2, JDDNode statesOfInterest) throws PrismException
	{
		StateValues res1 = null, res2 = null;
		JDDNode dd, dd1, dd2;
		String s;

		// Check for some easy (and common) special cases before resorting to
		// the general case

if (DEBUG_chkBinOp) System.out.println("<ChkExpRelOp exp1='"+ expr1+"' expr2='" + expr2 + "'>");
		// var relop int
		if (expr1 instanceof ExpressionVar && expr2.isConstant() && expr2.getType() instanceof TypeInt) {
if (DEBUG_chkBinOp) System.out.println("In chkExpRelOp - place 1 - CASE that expr1 is a Var, but expr2 is an int Constant");
			JDD.Deref(statesOfInterest);

			ExpressionVar e1;
			Expression e2;
			int i, j, l, h, v;
			e1 = (ExpressionVar) expr1;
			e2 = expr2;
			// get var's index
			s = e1.getName();
			v = varList.getIndex(s);
			if (v == -1) {
				throw new PrismException("Unknown variable \"" + s + "\"");
			}
			// get some info on the variable
			l = varList.getLow(v);
			h = varList.getHigh(v);
			// create dd
			dd = JDD.Constant(0);
			i = e2.evaluateInt(constantValues);
			switch (op) {
			case ExpressionBinaryOp.EQ:
if (DEBUG_chkBinOp) System.out.println("In chkExpRelOp - place 3 - case EQ START");
				if (i >= l && i <= h)
					dd = JDD.SetVectorElement(dd, varDDRowVars[v], i - l, 1);
if (DEBUG_chkBinOp) System.out.println("In chkExpRelOp - place 3 - case EQ END");
				break;
			case ExpressionBinaryOp.NE:
if (DEBUG_chkBinOp) System.out.println("In chkExpRelOp - place 3 - case NE START");
				if (i >= l && i <= h)
					dd = JDD.SetVectorElement(dd, varDDRowVars[v], i - l, 1);
if (DEBUG_chkBinOp) System.out.println("In chkExpRelOp - place 3 - case NE END");
				dd = JDD.Not(dd);
				break;
			case ExpressionBinaryOp.GT:
if (DEBUG_chkBinOp) System.out.println("In chkExpRelOp - place 3 - case GT START");
				for (j = i + 1; j <= h; j++)
					dd = JDD.SetVectorElement(dd, varDDRowVars[v], j - l, 1);
if (DEBUG_chkBinOp) System.out.println("In chkExpRelOp - place 3 - case GT END");
				break;
			case ExpressionBinaryOp.GE:
if (DEBUG_chkBinOp) System.out.println("In chkExpRelOp - place 3 - case GE START");
				for (j = i; j <= h; j++)
					dd = JDD.SetVectorElement(dd, varDDRowVars[v], j - l, 1);
if (DEBUG_chkBinOp) System.out.println("In chkExpRelOp - place 3 - case GE END");
				break;
			case ExpressionBinaryOp.LT:
if (DEBUG_chkBinOp) System.out.println("In chkExpRelOp - place 3 - case LT START");
				for (j = i - 1; j >= l; j--)
					dd = JDD.SetVectorElement(dd, varDDRowVars[v], j - l, 1);
if (DEBUG_chkBinOp) System.out.println("In chkExpRelOp - place 3 - case LT END");
				break;
			case ExpressionBinaryOp.LE:
if (DEBUG_chkBinOp) System.out.println("In chkExpRelOp - place 3 - case LE START");
				for (j = i; j >= l; j--)
					dd = JDD.SetVectorElement(dd, varDDRowVars[v], j - l, 1);
if (DEBUG_chkBinOp) System.out.println("In chkExpRelOp - place 3 - case LE END");
				break;
			default:
				throw new PrismException("Unknown relational operator");
			}
if (DEBUG_chkBinOp) System.out.println("</ChkExpRelOp exp1='"+ expr1+"' expr2='" + expr2 + "'>");
			return new StateValuesMTBDD(dd, model);
		}
		// int relop var
		else if (expr1.isConstant() && expr1.getType() instanceof TypeInt && expr2 instanceof ExpressionVar) {
if (DEBUG_chkBinOp) System.out.println("In chkExpRelOp - place 2 - CASE that expr2 is a Var, but expr1 is an int Constant.");
			JDD.Deref(statesOfInterest);

			Expression e1;
			ExpressionVar e2;
			int i, j, l, h, v;
			e1 = expr1;
			e2 = (ExpressionVar) expr2;
			// get var's index
			s = e2.getName();
			v = varList.getIndex(s);
			if (v == -1) {
				throw new PrismException("Unknown variable \"" + s + "\"");
			}
			// get some info on the variable
			l = varList.getLow(v);
			h = varList.getHigh(v);
			// create dd
			dd = JDD.Constant(0);
			i = e1.evaluateInt(constantValues);
			switch (op) {
			case ExpressionBinaryOp.EQ:
				if (i >= l && i <= h)
					dd = JDD.SetVectorElement(dd, varDDRowVars[v], i - l, 1);
				break;
			case ExpressionBinaryOp.NE:
				if (i >= l && i <= h)
					dd = JDD.SetVectorElement(dd, varDDRowVars[v], i - l, 1);
				dd = JDD.Not(dd);
				break;
			case ExpressionBinaryOp.GT:
				for (j = i - 1; j >= l; j--)
					dd = JDD.SetVectorElement(dd, varDDRowVars[v], j - l, 1);
				break;
			case ExpressionBinaryOp.GE:
				for (j = i; j >= l; j--)
					dd = JDD.SetVectorElement(dd, varDDRowVars[v], j - l, 1);
				break;
			case ExpressionBinaryOp.LT:
				for (j = i + 1; j <= h; j++)
					dd = JDD.SetVectorElement(dd, varDDRowVars[v], j - l, 1);
				break;
			case ExpressionBinaryOp.LE:
				for (j = i; j <= h; j++)
					dd = JDD.SetVectorElement(dd, varDDRowVars[v], j - l, 1);
				break;
			default:
				throw new PrismException("Unknown relational operator");
			}
if (DEBUG_chkBinOp) System.out.println("</ChkExpRelOp exp1='"+ expr1+"' expr2='" + expr2 + "'>");
			return new StateValuesMTBDD(dd, model);
		}

if (DEBUG_chkBinOp) System.out.println("In chkExprRelOp - CASE that we don't have a Var and an int Constant in either place [the 'General' case].");

		// General case.
		// Since the result is a Boolean and thus returned as an MTBDD, we
		// just convert both operands to MTBDDs first. Optimisations would be possible here.
		// Check operands recursively
		try {
if (DEBUG_chkBinOp) System.out.println("In chkExpRelOp - place 5A - about to checkExpression on expr1 using a copy of statesOfInterest");
			res1 = checkExpression(expr1, statesOfInterest.copy());
if (DEBUG_chkBinOp) System.out.println("In chkExpRelOp - place 5B, res1 is " + res1 + 
  "About to checkExpression on expr1 using a copy of statesOfInterest");
			res2 = checkExpression(expr2, statesOfInterest.copy());
if (DEBUG_chkBinOp) System.out.println("In chkExpRelOp - place 5C, res2 is " + res2);
		} catch (PrismException e) {
			if (res1 != null)
				res1.clear();
			throw e;
		} finally {
			JDD.Deref(statesOfInterest);
		}
if (DEBUG_chkBinOp) System.out.println("In chkExpRelOp - place 6A, about to convert res1 to StateValuesMTBDD, and then get its JDDNode.");
		dd1 = res1.convertToStateValuesMTBDD().getJDDNode();
if (DEBUG_chkBinOp) System.out.println("In chkExpRelOp - place 6B, about to convert res1 to StateValuesMTBDD, and then get its JDDNode.");
		dd2 = res2.convertToStateValuesMTBDD().getJDDNode();
if (DEBUG_chkBinOp) System.out.println("In chkExpRelOp - place 7:\ndd1 is " + dd1 + "\ndd2 is " + dd2);
		switch (op) {
		case ExpressionBinaryOp.EQ:
if (DEBUG_chkBinOp) System.out.println("Will apply JDD.EQUALS");
			dd = JDD.Apply(JDD.EQUALS, dd1, dd2);
			break;
		case ExpressionBinaryOp.NE:
if (DEBUG_chkBinOp) System.out.println("Will apply JDD.NOTEQUALS");
			dd = JDD.Apply(JDD.NOTEQUALS, dd1, dd2);
			break;
		case ExpressionBinaryOp.GT:
if (DEBUG_chkBinOp) System.out.println("Will apply JDD.GREATERTHAN");
			dd = JDD.Apply(JDD.GREATERTHAN, dd1, dd2);
			break;
		case ExpressionBinaryOp.GE:
if (DEBUG_chkBinOp) System.out.println("Will apply JDD.GREATERTHANEQUALS");
			dd = JDD.Apply(JDD.GREATERTHANEQUALS, dd1, dd2);
			break;
		case ExpressionBinaryOp.LT:
if (DEBUG_chkBinOp) System.out.println("Will apply JDD.LESSTHAN");
			dd = JDD.Apply(JDD.LESSTHAN, dd1, dd2);
			break;
		case ExpressionBinaryOp.LE:
if (DEBUG_chkBinOp) System.out.println("Will apply JDD.LESSTHANEQUALS");
			dd = JDD.Apply(JDD.LESSTHANEQUALS, dd1, dd2);
			break;
		default:
			throw new PrismException("Unknown relational operator");
		}
if (DEBUG_chkBinOp) System.out.println("The result of the apply is this dd: " + dd + " which will now be turned into a StateValuesMTBDD to be returned:");
	//ORIG:	return new StateValuesMTBDD(dd, model);
		StateValuesMTBDD returnVal = new StateValuesMTBDD(dd, model);
if (DEBUG_chkBinOp) System.out.println("</ChkExpRelOp exp1='"+ expr1+"' expr2='" + expr2 + "'>");
		return returnVal;
	}

	/**
	 * Check a unary operator.
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 */
	protected StateValues checkExpressionUnaryOp(ExpressionUnaryOp expr, JDDNode statesOfInterest) throws PrismException
	{
		StateValues res1 = null;
		JDDNode dd, dd1;
		DoubleVector dv1;
		int i, n, op = expr.getOperator();

		// Check operand recursively
		res1 = checkExpression(expr.getOperand(), statesOfInterest);

		// Parentheses are easy - nothing to do:
		if (op == ExpressionUnaryOp.PARENTH)
			return res1;

		// If operand is symbolic, result will be symbolic
		if (res1 instanceof StateValuesMTBDD) {
			dd1 = ((StateValuesMTBDD) res1).getJDDNode();
			// Apply operation
			switch (op) {
			case ExpressionUnaryOp.NOT:
				dd = JDD.Not(dd1);
				break;
			case ExpressionUnaryOp.MINUS:
				dd = JDD.Apply(JDD.MINUS, JDD.Constant(0), dd1);
				break;
			default:
				throw new PrismException("Unknown unary operator");
			}
			return new StateValuesMTBDD(dd, model);
		}
		// Otherwise result will be explicit
		else {
			dv1 = res1.convertToStateValuesDV().getDoubleVector();
			n = dv1.getSize();
			// Apply operation
			switch (op) {
			case ExpressionUnaryOp.NOT:
				throw new PrismException("Internal error: Explicit evaluation of Boolean");
				//for (i = 0; i < n; i++) dv1.setElement(i, (dv1.getElement(i)>0) ? 0.0 : 1.0);
			case ExpressionUnaryOp.MINUS:
				for (i = 0; i < n; i++)
					dv1.setElement(i, -dv1.getElement(i));
				break;
			default:
				throw new PrismException("Unknown unary operator");
			}
			return new StateValuesDV(dv1, model);
		}
	}

	/**
	 * Check a 'function'.
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 */
	protected StateValues checkExpressionFunc(ExpressionFunc expr, JDDNode statesOfInterest) throws PrismException
	{
		switch (expr.getNameCode()) {
		case ExpressionFunc.MIN:
		case ExpressionFunc.MAX:
			return checkExpressionFuncNary(expr, statesOfInterest);
		case ExpressionFunc.FLOOR:
		case ExpressionFunc.CEIL:
		case ExpressionFunc.ROUND:
			return checkExpressionFuncUnary(expr, statesOfInterest);
		case ExpressionFunc.POW:
		case ExpressionFunc.MOD:
		case ExpressionFunc.LOG:
			return checkExpressionFuncBinary(expr, statesOfInterest);
		case ExpressionFunc.MULTI:
			JDD.Deref(statesOfInterest);
			throw new PrismException("Multi-objective model checking is not supported for " + model.getModelType() + "s");
		default:
			JDD.Deref(statesOfInterest);
			throw new PrismException("Unrecognised function \"" + expr.getName() + "\"");
		}
	}

	/**
	 * Check a unary 'function'.
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 */
	protected StateValues checkExpressionFuncUnary(ExpressionFunc expr, JDDNode statesOfInterest) throws PrismException
	{
		StateValues res1 = null;
		JDDNode dd1;
		DoubleVector dv1;
		int i, n, op = expr.getNameCode();

		// Check operand recursively
		res1 = checkExpression(expr.getOperand(0), statesOfInterest);
		// Symbolic
		if (res1 instanceof StateValuesMTBDD) {
			dd1 = ((StateValuesMTBDD) res1).getJDDNode();
			switch (op) {
			case ExpressionFunc.FLOOR:
				// NB: Floor result kept as double, so don't need to check if operand is NaN
				dd1 = JDD.MonadicApply(JDD.FLOOR, dd1);
				break;
			case ExpressionFunc.CEIL:
				// NB: Ceil result kept as double, so don't need to check if operand is NaN
				dd1 = JDD.MonadicApply(JDD.CEIL, dd1);
				break;
			case ExpressionFunc.ROUND:
				// NB: Round result kept as double, so don't need to check if operand is NaN
				dd1 = JDD.MonadicApply(JDD.FLOOR, JDD.Plus(dd1, JDD.Constant(0.5)));
				break;
			}
			return new StateValuesMTBDD(dd1, model);
		}
		// Explicit
		else {
			dv1 = res1.convertToStateValuesDV().getDoubleVector();
			n = dv1.getSize();
			switch (op) {
			case ExpressionFunc.FLOOR:
				// NB: Floor result kept as double, so don't need to check if operand is NaN
				for (i = 0; i < n; i++)
					dv1.setElement(i, Math.floor(dv1.getElement(i)));
				break;
			case ExpressionFunc.CEIL:
				// NB: Ceil result kept as double, so don't need to check if operand is NaN
				for (i = 0; i < n; i++)
					dv1.setElement(i, Math.ceil(dv1.getElement(i)));
				break;
			case ExpressionFunc.ROUND:
				// NB: Round result kept as double, so don't need to check if operand is NaN
				for (i = 0; i < n; i++)
					dv1.setElement(i, Math.round(dv1.getElement(i)));
				break;
			}
			return new StateValuesDV(dv1, model);
		}
	}

	/**
	 * Check a binary 'function'.
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 */
	protected StateValues checkExpressionFuncBinary(ExpressionFunc expr, JDDNode statesOfInterest) throws PrismException
	{
		StateValues res1 = null, res2 = null;
		JDDNode dd = null, dd1, dd2;
		DoubleVector dv1, dv2;
		int i, n, op = expr.getNameCode();
		double d = 0.0;

		// Check operands recursively
		try {
			res1 = checkExpression(expr.getOperand(0), statesOfInterest.copy());
			res2 = checkExpression(expr.getOperand(1), statesOfInterest.copy());
		} catch (PrismException e) {
			if (res1 != null)
				res1.clear();
			throw e;
		} finally {
			JDD.Deref(statesOfInterest);
		}
		// If both operands are symbolic, result will be symbolic
		if (res1 instanceof StateValuesMTBDD && res2 instanceof StateValuesMTBDD) {
			dd1 = ((StateValuesMTBDD) res1).getJDDNode();
			dd2 = ((StateValuesMTBDD) res2).getJDDNode();
			switch (op) {
			case ExpressionFunc.POW:
				// Deref dd1/dd2 because may still need below
				JDD.Ref(dd1);
				JDD.Ref(dd2);
				dd = JDD.Apply(JDD.POW, dd1, dd2);
				// Check for some possible problems in case of integer power
				// (denote error with NaN for states with problems)
				if (expr.getType() instanceof TypeInt) {
					// Negative exponent not allowed for integer power
					JDD.Ref(dd2);
					dd = JDD.ITE(JDD.LessThan(dd2, 0), JDD.Constant(0.0 / 0.0), dd);
					// Check for integer overflow 
					JDD.Ref(dd);
					dd = JDD.ITE(JDD.GreaterThan(dd, Integer.MAX_VALUE), JDD.Constant(0.0 / 0.0), dd);
				}
				// Late deref of dd1/dd2 because needed above
				JDD.Deref(dd1);
				JDD.Deref(dd2);
				break;
			case ExpressionFunc.MOD:
				dd = JDD.Apply(JDD.MOD, dd1, dd2);
				break;
			case ExpressionFunc.LOG:
				dd = JDD.Apply(JDD.LOGXY, dd1, dd2);
				break;
			}
			return new StateValuesMTBDD(dd, model);
		}
		// Otherwise result will be explicit
		else {
			dv1 = res1.convertToStateValuesDV().getDoubleVector();
			dv2 = res2.convertToStateValuesDV().getDoubleVector();
			n = dv1.getSize();
			switch (op) {
			case ExpressionFunc.POW:
				// For integer power, have to check for errors and flag as NaN
				if (expr.getType() instanceof TypeInt) {
					double base, exp, pow;
					for (i = 0; i < n; i++) {
						base = dv1.getElement(i);
						exp = dv2.getElement(i);
						pow = Math.pow(base, exp);
						dv1.setElement(i, (exp < 0 || pow > Integer.MAX_VALUE) ? 0.0 / 0.0 : pow);
					}
				} else {
					for (i = 0; i < n; i++)
						dv1.setElement(i, Math.pow(dv1.getElement(i), dv2.getElement(i)));
				}
				break;
			case ExpressionFunc.MOD:
				for (i = 0; i < n; i++) {
					double div = (int) dv2.getElement(i);
					// Non-positive divisor not allowed (flag as NaN)
					d = (div <= 0) ? Double.NaN : (int) dv1.getElement(i) % (int) div;
					// Take care of negative case (% is remainder, not modulo)
					dv1.setElement(i, d < 0 ? d + div : d);
				}
				break;
			case ExpressionFunc.LOG:
				for (i = 0; i < n; i++)
					dv1.setElement(i, PrismUtils.log(dv1.getElement(i), dv2.getElement(i)));
				break;
			}
			dv2.clear();
			return new StateValuesDV(dv1, model);
		}
	}

	/**
	 * Check an n-ary 'function'.
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 */
	protected StateValues checkExpressionFuncNary(ExpressionFunc expr, JDDNode statesOfInterest) throws PrismException
	{
		StateValues res1 = null, res2 = null;
		JDDNode dd1, dd2;
		DoubleVector dv1, dv2;
		int i, i2, n, n2, op = expr.getNameCode();
		boolean symbolic;

		// Check first operand recursively
		res1 = checkExpression(expr.getOperand(0), statesOfInterest.copy());
		// Go through remaining operands
		// Switch to explicit as soon as an operand is explicit
		n = expr.getNumOperands();
		symbolic = (res1 instanceof StateValuesMTBDD);
		for (i = 1; i < n; i++) {
			try {
				res2 = checkExpression(expr.getOperand(i), statesOfInterest.copy());
			} catch (PrismException e) {
				if (res2 != null)
					res2.clear();
				JDD.Deref(statesOfInterest);
				throw e;
			}
			// Explicit
			if (!symbolic || !(res2 instanceof StateValuesMTBDD)) {
				symbolic = false;
				dv1 = res1.convertToStateValuesDV().getDoubleVector();
				dv2 = res2.convertToStateValuesDV().getDoubleVector();
				n2 = dv1.getSize();
				switch (op) {
				case ExpressionFunc.MIN:
					for (i2 = 0; i2 < n2; i2++)
						dv1.setElement(i2, Math.min(dv1.getElement(i), dv2.getElement(i)));
					break;
				case ExpressionFunc.MAX:
					for (i2 = 0; i2 < n2; i2++)
						dv1.setElement(i2, Math.max(dv1.getElement(i), dv2.getElement(i)));
					break;
				}
				dv2.clear();
				res1 = new StateValuesDV(dv1, model);
			}
			// Symbolic
			else {
				dd1 = ((StateValuesMTBDD) res1).getJDDNode();
				dd2 = ((StateValuesMTBDD) res2).getJDDNode();
				switch (op) {
				case ExpressionFunc.MIN:
					dd1 = JDD.Apply(JDD.MIN, dd1, dd2);
					break;
				case ExpressionFunc.MAX:
					dd1 = JDD.Apply(JDD.MAX, dd1, dd2);
					break;
				}
				res1 = new StateValuesMTBDD(dd1, model);
			}
		}

		JDD.Deref(statesOfInterest);
		return res1;
	}

	/**
	 * Check a literal.
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 */
	protected StateValues checkExpressionLiteral(ExpressionLiteral expr, JDDNode statesOfInterest) throws PrismException
	{
		JDDNode dd;

		// it's more efficient to return the constant node
		// instead of a MTBDD function for ITE(statesOfInterest, value, 0),
		// so we ignore statesOfInterest
		JDD.Deref(statesOfInterest);

		try {
			dd = JDD.Constant(expr.evaluateDouble());
dd.setPurpose("% JDDNode to represent a literal: " + expr + ", created in SMC.checkExprLiteral %");
		} catch (PrismLangException e) {
			throw new PrismException("Unknown literal type");
		}
		return new StateValuesMTBDD(dd, model);
	}

	/**
	 * Check a constant.
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 */
	protected StateValues checkExpressionConstant(ExpressionConstant expr, JDDNode statesOfInterest) throws PrismException
	{
		int i;
		JDDNode dd;

		// it's more efficient to return the constant node
		// instead of a MTBDD function for ITE(statesOfInterest, value, 0),
		// so we ignore statesOfInterest
		JDD.Deref(statesOfInterest);

		i = constantValues.getIndexOf(expr.getName());
		if (i == -1)
			throw new PrismException("Couldn't evaluate constant \"" + expr.getName() + "\"");
		try {
			dd = JDD.Constant(constantValues.getDoubleValue(i));
dd.setPurpose("JDDNode to represent a constant: " + expr + ", created in SMC.checkExprConstant");
		} catch (PrismLangException e) {
			throw new PrismException("Unknown type for constant \"" + expr.getName() + "\"");
		}

		return new StateValuesMTBDD(dd, model);
	}

	/**
	 * Check a variable reference.
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 */
	protected StateValues checkExpressionVar(ExpressionVar expr, JDDNode statesOfInterest) throws PrismException
	{
		String s;
		int v, l, h, i;
		JDDNode dd;
if (DEBUG_ChkVar) System.out.println("<ChkVar expr='"+expr+"'>");
		// it's generally more efficient not to restrict to statesOfInterest here
		// so we ignore statesOfInterest
		JDD.Deref(statesOfInterest);

		s = expr.getName();
		// get the variable's index
		v = varList.getIndex(s);
		if (v == -1) {
			throw new PrismException("Unknown variable \"" + s + "\"");
		}
else if (DEBUG_ChkVar) {
    System.out.println("Var in expression: " + s + "\nand its index in the VarList is apparently: " + v);
}
		// get some info on the variable
		l = varList.getLow(v);
		h = varList.getHigh(v);
if (DEBUG_ChkVar) System.out.println("Variable's lowest val is: " + l + ", and its highest val is: " + h +"\nAbout to make a new DD, and manipulate it using SetVectorElement...");
		// create dd
		dd = JDD.Constant(0);
if (DEBUG_ChkVar) System.out.println("The purpose of the selected varDDRowVars is: " + varDDRowVars[v].getPurpose());
		for (i = l; i <= h; i++) {
			dd = JDD.SetVectorElement(dd, varDDRowVars[v], i - l, i);
		}
//dd.setPurpose("% JDD for an ExpressionVar for variable " + expr + ", created in SMC.checkExprVar %");

		StateValues result = new StateValuesMTBDD(dd, model);
if (DEBUG_ChkVar) System.out.println("The StateValues being returned for the expression: '"+expr+"' is:\n" + result);
if (DEBUG_ChkVar) System.out.println("</ChkVar expr='"+expr+"'>\n");

		return result;
	}

	// ADDED BY SHANE - checks an expression for Accessing of an Indexed Set:
	/**
	 * Check an access of an indexed set.
	 * NEED TO DO: (copied from checkExprVar just prior): The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 */
	protected StateValues checkExpressionIndSetAcc(ExpressionIndexedSetAccess eisa, JDDNode statesOfInterest) throws PrismException
	{
DebugIndent++;
		String s;
		int v, l, h, i;
		JDDNode dd;
		StateValues res2 = null;

		// SHANE COMMENT:  Unlike the checkExpressionVar, I am not going to de-ref the statesOfInterest, 
		// because I don't know whether or not it is correct to do so. The comments in checkExpressionVar 
		// suggest that retaining it might lead to poorer efficiencies, as opposed to wrecking 
		// the veracity of the caclculation.
		// SHANE TO-DO: Consider if we can deref the statesOfInterest, at least after passing it to checkExpression.
		
		int evaluatedIndexPos = 0;
		
if (DEBUG_CheckIndSetAcc_Highlight)
{
	PrintDebugIndent();
	System.out.println("<ChkEISA> In SMC.checkExpressionIndSetAcc() - place 1, for: " + eisa);
}

		Expression accessExpr = eisa.getIndexExpression();
// OLD - wasn't sufficient		StateValues intermediate = checkExpression(accessExpr(),statesOfInterest);

		// Is it a constant int value - if so, find out exactly what that constant is...
if (DEBUG_CheckIndSetAcc) {
	PrintDebugIndent(); System.out.println("  isConstant gives:  " + accessExpr.isConstant() );
	PrintDebugIndent(); System.out.println("  type gives:        " + accessExpr.getType() );
	PrintDebugIndent(); System.out.println("  Was that type an int? " + ((accessExpr.getType() instanceof TypeInt) ? "Yes":"No"));
}
	// Note: Due to the substitution combinations having been done prior to reaching here, for model building at least, it will always
	// be THIS case which is done, and the access expression will always be a constant by the time this method is called.

		// A constant is provided, and its type is int  [maybe includes Literals??] 
		if (accessExpr.isConstant() && accessExpr.getType() instanceof TypeInt) {
if (DEBUG_CheckIndSetAcc_Highlight) System.out.println("ChkEISA - Considering as CASE 1: AccessExpression is constant, and integer");

//SHANE FEB 2020 - Should we de-ref this?? Or Not?
			JDD.Deref(statesOfInterest);	// It is constant, so we don't need to worry about any state!!

			evaluatedIndexPos = accessExpr.evaluateInt(constantValues);	// Find the value
if (DEBUG_CheckIndSetAcc) System.out.println("I believe you want to access specific index '" + evaluatedIndexPos+ "'");

			// Check the index is within the valid range?

			// Assemble the name of the variable in the model to retrieve.
			s = eisa.getName() + "[" + evaluatedIndexPos + "]";
if (DEBUG_CheckIndSetAcc_Highlight) {
	PrintDebugIndent();
	System.out.println("Resolved as meaning: " + s);
} else
if (DEBUG_CheckIndSetAcc)
{
	PrintDebugIndent();
	System.out.println("In SMC.checkExpressionIndSetAcc - place 3, determined name of variable as " + s);
	PrintDebugIndent();
	System.out.println("will look for the variable having that name.");
}
			// NOW that we know WHICH variable corresponds to the desired index, we do exactly as is done for a normal non-indexed variable (see checkExprVar)
			// get the indexed-variable's varList index

if (DEBUG_CheckIndSetAcc) System.out.println("<BOINK1>");
			v = varList.getIndex(s);
if (DEBUG_CheckIndSetAcc) System.out.println("Result of getIndex was: " + v + "\n</BOINK1>");
			if (v == -1) {
				throw new PrismException("Unknown variable \"" + s + "\"");
			}
			// get some info on the variable
			l = varList.getLow(v);
			h = varList.getHigh(v);
			// create dd (which will simply be the range of all possible values, for the nominated element position.
			dd = JDD.Constant(0);
			for (i = l; i <= h; i++) {
				dd = JDD.SetVectorElement(dd, varDDRowVars[v], i - l, i);
			}

DebugIndent--;
if (DEBUG_CheckIndSetAcc_Highlight)
{
	PrintDebugIndent();
	System.out.println("</ChkEISA considered='" + eisa + "'>\n");
}
			return new StateValuesMTBDD(dd, model);
		}
// THE REMAINDER IS INCOMPLETE - but may not be required since an earlier phase generates versions of command which have specific indexes before coming to this method.
		else if (accessExpr instanceof ExpressionVar) 		// THIS ONE CANNOT BE USED DURING MODEL-CONSTRUCTION; ONLY DURING ACTUAL MODEL-CHECKING
		{
if (DEBUG_CheckIndSetAcc_Highlight) System.out.println("ChkEISA - Considering as CASE 2: AccessExpression is a Var expression");
		    if (accessExpr.getType() instanceof TypeInt)
		    {
if (DEBUG_CheckIndSetAcc) System.out.println("SUBCASE A: the AccessExpression '"+accessExpr+"' is of type Int");
//			JDD.Deref(statesOfInterest);	// It is constant, so we don't need to worry about any state!!

//UP TO HERE
			// I think we need to check the access expression?   OR are we meant to just "evaluate" it to an int?
			try {
				res2 = checkExpression(accessExpr, statesOfInterest.copy());
			} catch (PrismException e) {
				if (res2 != null)
					res2.clear();
				JDD.Deref(statesOfInterest);
				throw e;
			}
if (DEBUG_CheckIndSetAcc) System.out.println("Is res2 NOT instanceof StateValuesMTBDD? " + (!(res2 instanceof StateValuesMTBDD)) );
			// If the outcome is explicit, extract the value.
			if (!(res2 instanceof StateValuesMTBDD)) {
				
System.out.println("Case A - res2 is of type: " + res2.getClass().getName());
				System.out.println("IMPLEMENTATION INCOMPLETE to deal with CASE 2A indexAccessExpression: " + accessExpr);
//res2.print(System.out);
//res2.convertToStateValuesDV().print(System.out);
/*
				evaluatedIndexPos = res2
accessExpr.evaluateInt(constantValues);	// Find the value
				dv1 = res1.convertToStateValuesDV().getDoubleVector();
				dv2 = res2.convertToStateValuesDV().getDoubleVector();
				n2 = dv1.getSize();
				switch (op) {
				case ExpressionFunc.MIN:
					for (i2 = 0; i2 < n2; i2++)
						dv1.setElement(i2, Math.min(dv1.getElement(i), dv2.getElement(i)));
					break;
				case ExpressionFunc.MAX:
					for (i2 = 0; i2 < n2; i2++)
						dv1.setElement(i2, Math.max(dv1.getElement(i), dv2.getElement(i)));
					break;
				}
				dv2.clear();
				res1 = new StateValuesDV(dv1, model);
*/			}
			// Symbolic
			else {
System.out.println("Case B - res2 is of type: " + res2.getClass().getName());

// START OF IDEA 17: I am just going to call setVectorElement on every possible 
/*			s = eisa.getName() + "[" + evaluatedIndexPos + "]";
if (DEBUG_CheckIndSetAcc)
{
	PrintDebugIndent();
	System.out.println("In SMC.checkExpressionIndSetAcc - place 3, determined name of variable as " + s);
	System.out.println("will look for the variable having that name.");
	PrintDebugIndent();
}
			// NOW that we know WHICH variable corresponds to the desired index, we do exactly as is done for a normal non-indexed variable (see checkExprVar)
			// get the indexed-variable's varList index
			v = varList.getIndex(s);
			if (v == -1) {
				throw new PrismException("Unknown variable \"" + s + "\"");
			}
			// get some info on the variable
			l = varList.getLow(v);
			h = varList.getHigh(v);
			// create dd
			dd = JDD.Constant(0);
			for (i = l; i <= h; i++) {
				dd = JDD.SetVectorElement(dd, varDDRowVars[v], i - l, i);
			}
*/
// END OF IDEA 17

//System.out.println("TRY THIS: " + (accessExpr.evaluateInt(constantValues,whereAreTheVAR_Values)));
//System.out.flush();
				StateValuesMTBDD d = (StateValuesMTBDD) res2;
	d.shaneShow();
//			evaluatedIndexPos = (int) d.getJDDNode().getValue();
//System.out.println(d);
	d.print(mainLog);
				System.out.println("IMPLEMENTATION INCOMPLETE to deal with CASE 2B indexAccessExpression: " + accessExpr);

/* some other stuff		dd1 = ((StateValuesMTBDD) res1).getJDDNode();
				dd2 = ((StateValuesMTBDD) res2).getJDDNode();
				switch (op) {
				case ExpressionFunc.MIN:
					dd1 = JDD.Apply(JDD.MIN, dd1, dd2);
					break;
				case ExpressionFunc.MAX:
					dd1 = JDD.Apply(JDD.MAX, dd1, dd2);
					break;
				}
				res1 = new StateValuesMTBDD(dd1, model);
*/			}



//			evaluatedIndexPos = accessExpr.evaluateInt(constantValues);	// Find the value
if (DEBUG_CheckIndSetAcc) System.out.println("I believe you want to access index " + evaluatedIndexPos);

			// Check the index is within the valid range?

			// Assemble the name of the variable in the model to retrieve.
			s = eisa.getName() + "[" + evaluatedIndexPos + "]";
if (DEBUG_CheckIndSetAcc)
{
	PrintDebugIndent();
	System.out.println("In SMC.checkExpressionIndSetAcc - place 3, determined name of variable as " + s);
	System.out.println("will look for the variable having that name.");
	PrintDebugIndent();
}
			// NOW that we know WHICH variable corresponds to the desired index, we do exactly as for a normal variable (see checkExprVar)

			// get the variable's index
if (DEBUG_CheckIndSetAcc) System.out.println("<BOINK2>");
			v = varList.getIndex(s);
if (DEBUG_CheckIndSetAcc) System.out.println("Result of getIndex was: " + v + "\n</BOINK2>");
			if (v == -1) {
				throw new PrismException("Unknown variable \"" + s + "\"");
			}
			// get some info on the variable
			l = varList.getLow(v);
			h = varList.getHigh(v);
			// create dd
			dd = JDD.Constant(0);
			for (i = l; i <= h; i++) {
				dd = JDD.SetVectorElement(dd, varDDRowVars[v], i - l, i);
			}

DebugIndent--;
//			return new StateValuesMTBDD(dd, model);
if (DEBUG_CheckIndSetAcc_Highlight)
{
	PrintDebugIndent();
	System.out.println("</ChkEISA considered='" + eisa + "'>\n");
}
			throw new PrismException("IMPLEMENTATION INCOMPLETE to deal with: " + accessExpr);

		    } else {	// The access expression has some type other than Int
if (DEBUG_CheckIndSetAcc) System.out.println("SUBCASE B: the AccessExpression is NOT of type Int - it is of type: " + accessExpr.getType().getClass().getName());
if (DEBUG_CheckIndSetAcc_Highlight)
{
	PrintDebugIndent();
	System.out.println("</ChkEISA considered='" + eisa + "'>\n");
}
			throw new PrismException("Unable to model-check that type of index access expression: " + accessExpr);
		    }
		} else if (accessExpr instanceof ExpressionBinaryOp || accessExpr instanceof ExpressionUnaryOp) { // Some kind of arithmetic, presumbly.
if (DEBUG_CheckIndSetAcc_Highlight) System.out.println("ChkEISA - Considering as CASE 3: AccessExpression is something which needs calculation, namely:" + accessExpr.getClass().getName() );
System.out.println("access expression: " + accessExpr);
System.out.println("object type of accessExpr: " + accessExpr.getClass().getName());
System.out.println("getType on the expression: " + accessExpr.getType());
if (DEBUG_CheckIndSetAcc_Highlight)
{
	PrintDebugIndent();
	System.out.println("</ChkEISA considered='" + eisa + "'>\n");
}
			throw new PrismException("IMPLEMENTATION INCOMPLETE - Unable to model-check that type of index access expression: " + accessExpr + "\nthe object type is: "+accessExpr.getClass().getName());
		}else { 	// For example, a
if (DEBUG_CheckIndSetAcc_Highlight) System.out.println("ChkEISA - Considering as CASE 4: AccessExpression is of an unhandled type, namely:" + accessExpr.getClass().getName() );
if (DEBUG_CheckIndSetAcc_Highlight)
{
	PrintDebugIndent();
	System.out.println("</ChkEISA considered='" + eisa + "'>\n");
}
			throw new PrismException(" *** IMPLEMENTATION INCOMPLETE *** - Unable to model-check that type of index access expression: " + accessExpr);
		}



/* Not Useful...
		// The following IF and ELSE blocks are based on the code found in the checkExpressionBinaryOp()  
		// If the result of the access expression was symbolic, result will be symbolic
		if (intermediate instanceof StateValuesMTBDD) {
if (DEBUG_CheckIndSetAcc) System.out.println("Intermediate is symbolic - Shane hasn't worked out what to do here yet.");
			JDDNode ddIndex = ((StateValuesMTBDD) intermediate).getJDDNode();
//				throw new PrismException("Unable to model check this index set access");
			//return new StateValuesMTBDD(dd, model);
		}
		// Otherwise result will be explicit
*/

	}
	
	/**
	 * Check a label.
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 */
	protected StateValues checkExpressionLabel(ExpressionLabel expr, JDDNode statesOfInterest) throws PrismException
	{
		LabelList ll;
		JDDNode dd;
		int i;

		// treat special cases
		if (expr.isDeadlockLabel()) {
			JDD.Deref(statesOfInterest);
			dd = model.getDeadlocks();
			JDD.Ref(dd);
			return new StateValuesMTBDD(dd, model);
		} else if (expr.isInitLabel()) {
			JDD.Deref(statesOfInterest);
			dd = start;
			JDD.Ref(dd);
			return new StateValuesMTBDD(dd, model);
		} else if (model.hasLabelDD(expr.getName())) {
			JDD.Deref(statesOfInterest);
			dd = model.getLabelDD(expr.getName());
			return new StateValuesMTBDD(dd.copy(), model);
		} else {
			// get expression associated with label
			ll = getLabelList();
			i = -1;
			if (ll != null)
				i = ll.getLabelIndex(expr.getName());
			if (i == -1)
				throw new PrismException("Unknown label \"" + expr.getName() + "\" in property");
			// check recursively
			return checkExpression(ll.getLabel(i), statesOfInterest);
		}
	}


	/**
	 * Check a property reference.
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 */
	protected StateValues checkExpressionProp(ExpressionProp expr, JDDNode statesOfInterest) throws PrismException
	{
		// Look up property and check recursively
		Property prop = propertiesFile.lookUpPropertyObjectByName(expr.getName());
		if (prop != null) {
			mainLog.println("\nModel checking : " + prop);
			return checkExpression(prop.getExpression(), statesOfInterest);
		} else {
			JDD.Deref(statesOfInterest);
			throw new PrismException("Unknown property reference " + expr);
		}
	}

	/**
	 * Check a filter.
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 */
	protected StateValues checkExpressionFilter(ExpressionFilter expr, JDDNode statesOfInterest) throws PrismException
	{
		
		// Translate filter
		Expression filter = expr.getFilter();
		// Create default filter (true) if none given
		if (filter == null)
			filter = Expression.True();

if (DEBUG_CEF) System.out.println("<ChkExprFilter comment=\"In StateModChkr.checkExpressionFilter() for expression: " + expr + "\">");

		// Remember whether filter is "true"
		boolean filterTrue = Expression.isTrue(filter);
if (DEBUG_CEF) System.out.println("   In ChkExprFilter, @ Place 1:  isTrue(filter) is " + filterTrue);

// Store some more info
		String filterStatesString = filterTrue ? "all states" : "states satisfying filter";
if (DEBUG_CEF) System.out.println("\n   In ChkExprFilter, @ Place 2:  about to call checkExprDD on the filter...");
		JDDNode ddFilter = checkExpressionDD(filter, statesOfInterest.copy());
if (DEBUG_CEF) System.out.println("\n   Back In SMC.ChkExprFilter, @ Place 3:  about to call constructor for StateListMTBDD, with ddFilter (just received) and model");
		StateListMTBDD statesFilter = new StateListMTBDD(ddFilter, model);
		// Check if filter state set is empty; we treat this as an error
		if (ddFilter.equals(JDD.ZERO)) {
if (DEBUG_CEF) System.out.println("   In ChkExprFilter, the ddFilter equals JDD.ZERO, so an Exception will be thrown:  Filter satisfies no states");
			throw new PrismException("Filter satisfies no states");
		}
else if (DEBUG_CEF) System.out.println("   In SMC.ChkExprFilter, @ Place 4:  the ddFilter does not equal JDD.ZERO, so NO Exception will be thrown ");

		// Remember whether filter is for the initial state and, if so, whether there's just one
		boolean filterInit = (filter instanceof ExpressionLabel && ((ExpressionLabel) filter).isInitLabel());
		boolean filterInitSingle = filterInit & model.getNumStartStates() == 1;
if (DEBUG_CEF) System.out.println("   In SMC.ChkExprFilter, @ Place 5:  filterInit is " + filterInit + ", filterInitSingle is " + filterInitSingle);

		// For some types of filter, store info that may be used to optimise model checking
		FilterOperator op = expr.getOperatorType();
if (DEBUG_CEF) System.out.print("   In SMC.ChkExprFilter, @ Place 6: Determining the type of FilterOperator... Then will call GetIndexOfFirstFromDD to pass to this Filter constructor: ");
		if (op == FilterOperator.STATE) {
if (DEBUG_CEF) System.out.println("Case 1 (FilterOperator.STATE)");
			// Check filter satisfied by exactly one state
			if (statesFilter.size() != 1) {
				String s = "Filter should be satisfied in exactly 1 state";
				s += " (but \"" + filter + "\" is true in " + statesFilter.size() + " states)";
				throw new PrismException(s);
			}
			currentFilter = new Filter(Filter.FilterOperator.STATE, ODDUtils.GetIndexOfFirstFromDD(ddFilter, odd, allDDRowVars));
		} else if (op == FilterOperator.FORALL && filterInit && filterInitSingle) {
if (DEBUG_CEF) System.out.println("Case 2 (FilterOperator.FORALL)");
			currentFilter = new Filter(Filter.FilterOperator.STATE, ODDUtils.GetIndexOfFirstFromDD(ddFilter, odd, allDDRowVars));
		} else if (op == FilterOperator.FIRST && filterInit && filterInitSingle) {
if (DEBUG_CEF) System.out.println("Case 3 (FilterOperator.FIRST)");
			currentFilter = new Filter(Filter.FilterOperator.STATE, ODDUtils.GetIndexOfFirstFromDD(ddFilter, odd, allDDRowVars));
		} else {
if (DEBUG_CEF) {
 System.out.print("Case 4 (Not calling), with: op being: ");
 if (op == FilterOperator.FORALL) System.out.println("FilterOperator.FORALL - but check filterInit and filterInitSingle");
 else if (op == FilterOperator.FIRST) System.out.println("FilterOperator.FIRST - but check filterInit and filterInitSingle");
 else System.out.println(" ** SOMETHING ELSE (" + op + ") **, so currentFilter being set to null");
}
			currentFilter = null;
		}

		StateValues vals = null;
		try {
			// Check operand recursively, using the filter as the states of interest
if (DEBUG_CEF) System.out.println("\n   In ChkExprFilter, @ Place 7: about to call checkExpression on operand: " + expr.getOperand() + "\nNote: the second parameter [statesOfInterest] is the DD representing the filter (the start point states for doing the check)\n");
			vals = checkExpression(expr.getOperand(), ddFilter.copy());
		} catch (PrismException e) {
			JDD.Deref(ddFilter);
			JDD.Deref(statesOfInterest);
if (DEBUG_CEF) e.printStackTrace(System.out);
			throw e;
		}
if (DEBUG_CEF) System.out.println("   In ChkExprFilter, @ Place 8: having just completed checkExpression on operand: " + expr.getOperand() + "\n");

		// Print out number of states satisfying filter
		if (!filterInit)
			mainLog.println("\nStates satisfying filter " + filter + ": " + statesFilter.sizeString());

		// Compute result according to filter type
		op = expr.getOperatorType();
		StateValues resVals = null;
		JDDNode ddMatch = null, dd = null;
		StateListMTBDD states;
		double d = 0.0, d2 = 0.0;
		boolean b = false;
		String resultExpl = null;
		Object resObj = null;
if (DEBUG_CEF) System.out.print("   In ChkExprFilter, @ Place 9: considering the operator type: ");

		switch (op) {
		case PRINT:
		case PRINTALL:
			// Format of print-out depends on type
			if (expr.getType() instanceof TypeBool) {
				// NB: 'usual' case for filter(print,...) on Booleans is to use no filter
				mainLog.print("\nSatisfying states");
				mainLog.println(filterTrue ? ":" : " that are also in filter " + filter + ":");
				dd = vals.deepCopy().convertToStateValuesMTBDD().getJDDNode();
				JDD.Ref(ddFilter);
				dd = JDD.And(dd, ddFilter);
				new StateListMTBDD(dd, model).print(mainLog);
				JDD.Deref(dd);
			} else {
				// TODO: integer-typed case: either add to print method or store in StateValues
				if (op == FilterOperator.PRINT) {
					mainLog.println("\nResults (non-zero only) for filter " + filter + ":");
					vals.printFiltered(mainLog, ddFilter);
				} else {
					mainLog.println("\nResults (including zeros) for filter " + filter + ":");
					vals.printFiltered(mainLog, ddFilter, false, false, true);
				}
			}
			// Result vector is unchanged; for PRINT/PRINTALL, don't store a single value (in resObj)
			// Also, don't bother with explanation string
			resVals = vals;
			// Set vals to null to stop it being cleared below
			vals = null;
			break;
		case MIN:
			// Compute min
			d = vals.minOverBDD(ddFilter);
			// Store as object/vector (note crazy Object cast to avoid Integer->int auto conversion)
			resObj = (expr.getType() instanceof TypeInt) ? ((Object) new Integer((int) d)) : (new Double(d));
			resVals = new StateValuesMTBDD(JDD.Constant(d), model);
			// Create explanation of result and print some details to log
			resultExpl = "Minimum value over " + filterStatesString;
			mainLog.println("\n" + resultExpl + ": " + resObj);
			// Also find states that (are close to) selected value for display to log
			ddMatch = vals.getBDDFromCloseValue(d, prism.getTermCritParam(), prism.getTermCrit() == Prism.ABSOLUTE);
			JDD.Ref(ddFilter);
			ddMatch = JDD.And(ddMatch, ddFilter);
			break;
		case MAX:
			// Compute max
			d = vals.maxOverBDD(ddFilter);
			// Store as object/vector (note crazy Object cast to avoid Integer->int auto conversion)
			resObj = (expr.getType() instanceof TypeInt) ? ((Object) new Integer((int) d)) : (new Double(d));
			resVals = new StateValuesMTBDD(JDD.Constant(d), model);
			// Create explanation of result and print some details to log
			resultExpl = "Maximum value over " + filterStatesString;
			mainLog.println("\n" + resultExpl + ": " + resObj);
			// Also find states that (are close to) selected value for display to log
			ddMatch = vals.getBDDFromCloseValue(d, prism.getTermCritParam(), prism.getTermCrit() == Prism.ABSOLUTE);
			JDD.Ref(ddFilter);
			ddMatch = JDD.And(ddMatch, ddFilter);
			break;
		case ARGMIN:
			// Compute/display min
			d = vals.minOverBDD(ddFilter);
			mainLog.print("\nMinimum value over " + filterStatesString + ": ");
			mainLog.println((expr.getType() instanceof TypeInt) ? ((Object) new Integer((int) d)) : (new Double(d)));
			// Find states that (are close to) selected value
			ddMatch = vals.getBDDFromCloseValue(d, prism.getTermCritParam(), prism.getTermCrit() == Prism.ABSOLUTE);
			JDD.Ref(ddFilter);
			ddMatch = JDD.And(ddMatch, ddFilter);
			// Store states in vector; for ARGMIN, don't store a single value (in resObj)
			// Also, don't bother with explanation string
			resVals = new StateValuesMTBDD(ddMatch, model);
			// Print out number of matching states, but not the actual states
			mainLog.println("\nNumber of states with minimum value: " + resVals.getNNZString());
			ddMatch = null;
			break;
		case ARGMAX:
			// Compute/display max
			d = vals.maxOverBDD(ddFilter);
			mainLog.print("\nMaximum value over " + filterStatesString + ": ");
			mainLog.println((expr.getType() instanceof TypeInt) ? ((Object) new Integer((int) d)) : (new Double(d)));
			// Find states that (are close to) selected value
			ddMatch = vals.getBDDFromCloseValue(d, prism.getTermCritParam(), prism.getTermCrit() == Prism.ABSOLUTE);
			JDD.Ref(ddFilter);
			ddMatch = JDD.And(ddMatch, ddFilter);
			// Store states in vector; for ARGMAX, don't store a single value (in resObj)
			// Also, don't bother with explanation string
			resVals = new StateValuesMTBDD(ddMatch, model);
			// Print out number of matching states, but not the actual states
			mainLog.println("\nNumber of states with maximum value: " + resVals.getNNZString());
			ddMatch = null;
			break;
		case COUNT:
			// Compute count
			vals.filter(ddFilter);
			d = vals.getNNZ();
			// Store as object/vector
			resObj = new Integer((int) d);
			resVals = new StateValuesMTBDD(JDD.Constant(d), model);
			// Create explanation of result and print some details to log
			resultExpl = filterTrue ? "Count of satisfying states" : "Count of satisfying states also in filter";
			mainLog.println("\n" + resultExpl + ": " + resObj);
			break;
		case SUM:
			// Compute sum
			d = vals.sumOverBDD(ddFilter);
			// Store as object/vector (note crazy Object cast to avoid Integer->int auto conversion)
			resObj = (expr.getType() instanceof TypeInt) ? ((Object) new Integer((int) d)) : (new Double(d));
			resVals = new StateValuesMTBDD(JDD.Constant(d), model);
			// Create explanation of result and print some details to log
			resultExpl = "Sum over " + filterStatesString;
			mainLog.println("\n" + resultExpl + ": " + resObj);
			break;
		case AVG:
			// Compute average
			d = vals.sumOverBDD(ddFilter) / JDD.GetNumMinterms(ddFilter, allDDRowVars.n());
			// Store as object/vector
			resObj = new Double(d);
			resVals = new StateValuesMTBDD(JDD.Constant(d), model);
			// Create explanation of result and print some details to log
			resultExpl = "Average over " + filterStatesString;
			mainLog.println("\n" + resultExpl + ": " + resObj);
			break;
		case FIRST:
			// Find first value
			d = vals.firstFromBDD(ddFilter);
			// Store as object/vector
			if (expr.getType() instanceof TypeInt) {
				resObj = new Integer((int) d);
			} else if (expr.getType() instanceof TypeDouble) {
				resObj = new Double(d);
			} else {
				resObj = new Boolean(d > 0);
			}
			resVals = new StateValuesMTBDD(JDD.Constant(d), model);
			// Create explanation of result and print some details to log
			resultExpl = "Value in ";
			if (filterInit) {
				resultExpl += filterInitSingle ? "the initial state" : "first initial state";
			} else {
				resultExpl += filterTrue ? "the first state" : "first state satisfying filter";
			}
			mainLog.println("\n" + resultExpl + ": " + resObj);
			break;
		case RANGE:
			// Find range of values
			d = vals.minOverBDD(ddFilter);
			d2 = vals.maxOverBDD(ddFilter);
			// Store as object
			if (expr.getOperand().getType() instanceof TypeInt) {
				resObj = new prism.Interval((int) d, (int) d2);
			} else {
				resObj = new prism.Interval(d, d2);
			}
			// Leave result vector unchanged: for a range, result is only available from Result object
			resVals = vals;
			// Set vals to null to stop it being cleared below
			vals = null;
			// Create explanation of result and print some details to log
			resultExpl = "Range of values over ";
			resultExpl += filterInit ? "initial states" : filterStatesString;
			mainLog.println("\n" + resultExpl + ": " + resObj);
			break;
		case FORALL:
			// Get access to BDD for this
			dd = vals.convertToStateValuesMTBDD().getJDDNode();
			// Check "for all" over filter, store result
			JDD.Ref(ddFilter);
			dd = JDD.And(dd, ddFilter);
			states = new StateListMTBDD(dd, model);
			b = dd.equals(ddFilter);
			// Store as object/vector
			resObj = new Boolean(b);
			resVals = new StateValuesMTBDD(JDD.Constant(b ? 1.0 : 0.0), model);
			// Set vals to null so that is not clear()-ed twice
			vals = null;
			// Create explanation of result and print some details to log
			resultExpl = "Property " + (b ? "" : "not ") + "satisfied in ";
			mainLog.print("\nProperty satisfied in " + states.sizeString());
			if (filterInit) {
				if (filterInitSingle) {
					resultExpl += "the initial state";
				} else {
					resultExpl += "all initial states";
				}
				mainLog.println(" of " + model.getNumStartStatesString() + " initial states.");
			} else {
				if (filterTrue) {
					resultExpl += "all states";
					mainLog.println(" of all " + model.getNumStatesString() + " states.");
				} else {
					resultExpl += "all filter states";
					mainLog.println(" of " + statesFilter.sizeString() + " filter states.");
				}
			}
			// Derefs
			JDD.Deref(dd);
			break;
		case EXISTS:
			// Get access to BDD for this
			dd = vals.convertToStateValuesMTBDD().getJDDNode();
			// Check "there exists" over filter
			JDD.Ref(ddFilter);
			dd = JDD.And(dd, ddFilter);
			b = !dd.equals(JDD.ZERO);
			// Store as object/vector
			resObj = new Boolean(b);
			resVals = new StateValuesMTBDD(JDD.Constant(b ? 1.0 : 0.0), model);
			// Set vals to null so that is not clear()-ed twice
			vals = null;
			// Create explanation of result and print some details to log
			resultExpl = "Property satisfied in ";
			if (filterTrue) {
				resultExpl += b ? "at least one state" : "no states";
			} else {
				resultExpl += b ? "at least one filter state" : "no filter states";
			}
			mainLog.println("\n" + resultExpl);
			// Derefs
			JDD.Deref(dd);
			break;
		case STATE:
			// Results of type void are handled differently
			if (expr.getType() instanceof TypeVoid) {
				// Extract result from StateValuesVoid object 
				resObj = ((StateValuesVoid) vals).getValue();
				// Leave result vector unchanged: for a range, result is only available from Result object
				resVals = vals;
				// Set vals to null to stop it being cleared below
				vals = null;
			} else {
				// Find first (only) value
				d = vals.firstFromBDD(ddFilter);
				// Store as object/vector
				if (expr.getType() instanceof TypeInt) {
					resObj = new Integer((int) d);
				} else if (expr.getType() instanceof TypeDouble) {
					resObj = new Double(d);
				} else if (expr.getType() instanceof TypeBool) {
					resObj = new Boolean(d > 0);
				} else {
					throw new PrismException("Don't know how to handle result of type " + expr.getType());
				}
				resVals = new StateValuesMTBDD(JDD.Constant(d), model);
			}
			// Create explanation of result and print some details to log
			resultExpl = "Value in ";
			if (filterInit) {
				resultExpl += "the initial state";
			} else {
				resultExpl += "the filter state";
			}
			mainLog.println("\n" + resultExpl + ": " + resObj);
			break;
		default:
			JDD.Deref(ddFilter);
			throw new PrismException("Unrecognised filter type \"" + expr.getOperatorName() + "\"");
		}

if (DEBUG_CEF) System.out.println("   In ChkExprFilter, @ Place 10: ddMatch != null is " + (ddMatch != null) );

		// For some operators, print out some matching states
		if (ddMatch != null) {
			states = new StateListMTBDD(ddMatch, model);
			mainLog.print("\nThere are " + states.sizeString() + " states with ");
			mainLog.print((expr.getType() instanceof TypeDouble ? "(approximately) " : "") + "this value");
			if (!verbose && (states.size() == -1 || states.size() > 10)) {
				mainLog.print(".\nThe first 10 states are displayed below. To view them all, enable verbose mode or use a print filter.\n");
				states.print(mainLog, 10);
			} else {
				mainLog.print(":\n");
				states.print(mainLog);
			}
			JDD.Deref(ddMatch);
		}

if (DEBUG_CEF) System.out.println("   In ChkExprFilter, @ Place 11" );
		// Store result
		result.setResult(resObj);
		// Set result explanation (if none or disabled, clear)
		if (expr.getExplanationEnabled() && resultExpl != null) {
			result.setExplanation(resultExpl.toLowerCase());
		} else {
			result.setExplanation(null);
		}
if (DEBUG_CEF) System.out.println("   In ChkExprFilter, @ Place 12" );
		// Store vector if requested (and if not, clear it)
		if (storeVector) {
			result.setVector(vals);
		} else if (vals != null) {
			vals.clear();
		}
if (DEBUG_CEF) System.out.println("   In ChkExprFilter, @ Place 13" );
		// Other derefs
		JDD.Deref(ddFilter);
if (DEBUG_CEF) System.out.println("   In ChkExprFilter, @ Place 14" );
		JDD.Deref(statesOfInterest);
if (DEBUG_CEF) System.out.println("   In ChkExprFilter, @ Place 15" );

if (DEBUG_CEF) System.out.println("</ChkExprFilter comment=\"ENDED. for expression: " + expr + "\">");
		return resVals;
	}

	/**
	 * Method for handling the recursive part of PCTL* checking, i.e.,
	 * recursively checking maximal state subformulas and replacing them
	 * with labels and the corresponding satisfaction sets.
	 * <br>
	 * Extracts maximal state formula from an LTL path formula,
	 * model checks them (with the current model checker and the current model)
	 * and replaces them with ExpressionLabel objects that correspond
	 * to freshly generated labels attached to the model.
	 * <br>
	 * Returns the modified Expression.
	 *
	 * @param expr the expression (a path formula)
	 */
	public Expression handleMaximalStateFormulas(Expression expr) throws PrismException
	{
		Vector<JDDNode> labelDD = new Vector<JDDNode>();

		// construct LTL model checker, using this model checker instance
		// (which is specialised for the model type) for the recursive
		// model checking computation
		LTLModelChecker ltlMC = new LTLModelChecker(this);

		// check the maximal state subformulas and gather
		// the satisfaction sets in labelBS, with index i
		// in the vector corresponding to label Li in the
		// returned formula
		Expression exprNew = ltlMC.checkMaximalStateFormulas(this, model, expr.deepCopy(), labelDD);

		HashMap<String, String> labelReplacements = new HashMap<String, String>();
		for (int i=0; i < labelDD.size(); i++) {
			String currentLabel = "L"+i;
			// Attach satisfaction set for Li to the model, record necessary
			// label renaming
			String newLabel = model.addUniqueLabelDD("phi", labelDD.get(i), getDefinedLabelNames());
			labelReplacements.put(currentLabel, newLabel);
		}
		// rename the labels
		return (Expression) exprNew.accept(new ReplaceLabels(labelReplacements));
	}

	// Utility functions for symbolic model checkers 
	
	/**
	 * Get the state rewards (from a model) corresponding to the index of this R operator.
	 * Throws an exception (with explanatory message) if it cannot be found.
	 */
	public JDDNode getStateRewardsByIndexObject(Object rs, Model model, Values constantValues) throws PrismException
	{
		JDDNode stateRewards = null;
		if (model.getNumRewardStructs() == 0)
			throw new PrismException("Model has no rewards specified");
		if (rs == null) {
			stateRewards = model.getStateRewards(0);
		} else if (rs instanceof Expression) {
			int i = ((Expression) rs).evaluateInt(constantValues);
			rs = new Integer(i); // for better error reporting below
			stateRewards = model.getStateRewards(i - 1);
		} else if (rs instanceof String) {
			stateRewards = model.getStateRewards((String) rs);
		}
		if (stateRewards == null)
			throw new PrismException("Invalid reward structure index \"" + rs + "\"");
		return stateRewards; 
	}
	
	/**
	 * Get the transition rewards (from a model) corresponding to the index of this R operator.
	 * Throws an exception (with explanatory message) if it cannot be found.
	 */
	public JDDNode getTransitionRewardsByIndexObject(Object rs, Model model, Values constantValues) throws PrismException
	{
		JDDNode transRewards = null;
		if (model.getNumRewardStructs() == 0)
			throw new PrismException("Model has no rewards specified");
		if (rs == null) {
			transRewards = model.getTransRewards(0);
		} else if (rs instanceof Expression) {
			int i = ((Expression) rs).evaluateInt(constantValues);
			rs = new Integer(i); // for better error reporting below
			transRewards = model.getTransRewards(i - 1);
		} else if (rs instanceof String) {
			transRewards = model.getTransRewards((String) rs);
		}
		if (transRewards == null)
			throw new PrismException("Invalid reward structure index \"" + rs + "\"");
		return transRewards; 
	}

	@Override
	public Values getConstantValues()
	{
		return constantValues;
	}

	/**
	 * Get the label list (combined list from properties file, if attached).
	 * @return the label list for the properties/modules file, or {@code null} if not available.
	 */
	public LabelList getLabelList()
	{
		if (propertiesFile != null) {
			return propertiesFile.getCombinedLabelList(); // combined list from properties and modules file
		} else {
			return null;
		}
	}

	/**
	 * Return the set of label names that are defined
	 * either by the model (from the modules file)
	 * or properties file (if attached to the model checker).
	 */
	public Set<String> getDefinedLabelNames()
	{
		TreeSet<String> definedLabelNames = new TreeSet<String>();

		// labels from the label list
		LabelList labelList = getLabelList();
		if (labelList != null) {
			definedLabelNames.addAll(labelList.getLabelNames());
		}

		return definedLabelNames;
	}

	/**
	 * Export a set of labels and the states that satisfy them.
	 * @param labelNames The name of each label
	 * @param exportType The format in which to export
	 * @param file Where to export
	 */
	public void exportLabels(List<String> labelNames, int exportType, File file) throws PrismException, FileNotFoundException
	{
		// Convert labels to BDDs
		int numLabels = labelNames.size();
		JDDNode labels[] = new JDDNode[numLabels];
		for (int i = 0; i < numLabels; i++) {
			labels[i] = checkExpressionDD(new ExpressionLabel(labelNames.get(i)), model.getReach().copy());
		}

		// Export them using the MTBDD engine
		String matlabVarName = "l";
		String labelNamesArr[] = labelNames.toArray(new String[labelNames.size()]);
		PrismMTBDD.ExportLabels(labels, labelNamesArr, matlabVarName, allDDRowVars, odd, exportType, (file != null) ? file.getPath() : null);
		
		// Derefs
		for (int i = 0; i < numLabels; i++) {
			JDD.Deref(labels[i]);
		}
	}
}

// ------------------------------------------------------------------------------
