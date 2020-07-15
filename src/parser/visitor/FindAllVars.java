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

package parser.visitor;

import java.util.List;

import parser.ast.*;
import parser.type.*;
import prism.PrismLangException;

/**
 * Find all references to variables, replace any identifier objects with variable objects,
 * check variables exist and store their index (position where they are located within 
 * the Vector of the containing ModuleFile).
 */
public class FindAllVars extends ASTTraverseModify
{
public static boolean DEBUG_Basic = false ; //true && DEBUG_SHOW_ENABLED;		// The most high-level (i.e. minimal) types of debug messages
public static boolean DEBUG = false ; //true && DEBUG_SHOW_ENABLED;			// The majority of debug messages
public static boolean DEBUG_OrdinVar = false ; //true && DEBUG_SHOW_ENABLED;		// Whether to show details for ordinary variables (as opposed to indexed ones).
public static boolean DEBUG_EISA = false;

	private List<String> varIdents;
	private List<Type> varTypes;
	
	public FindAllVars(List<String> varIdents, List<Type> varTypes)
	{
		this.varIdents = varIdents;
		this.varTypes = varTypes;
	}
	
	// Note that this is done with VisitPost, i.e. after recursively visiting children.
	// This is ok because we can modify rather than create a new object so don't need to return it.
	/**
	 * When considering an Update (that is a command of atomically-performed UpdateElements, each which alters one variable)
	 * this method determines which variables of the Model are targets to be updated, and pre-computes their 'position'
	 * (in the ModulesFile's list of all known variables).
	 */ 
	 // SHANE's comments:
	 // Prior to this visit being called:
	 //  1. each identifier specified as a target of an update-element, will have its 'index' set to -1. 
	 //  2. each identifier specified as a target will have its 'type' set as NULL.
	 //
	 // After conclusion of this method, this method will have attempted to:
	 //  1. change the index to correspond to the position in ModulesFile.vars where the target is located.
	 //     (guaranteed to have occurred only for non-indexed-set targets; but no change for indexed-set targets).
	 //  2. to set the type of the target (as stored within the ElementOfUpdate object) to have a reference 
	 //     to the corresponding type of that identifier. (We can do this for targets that are elements of an indexed set,
	 //     even without knowing yet exactly which element will be affected.)
	 //
	 // If the target of any ElementOfUpdate is an element of an indexed-set, the only time we could possibly
	 // determine the position (in ModulesFile.vars) is if the index is a literal or constant. (NOT YET ACTUALLY DONE).
	 // (The index will often need to be computed at update-run-time [i.e. check-time], if an expression is given instead).

	public void visitPost(Update e) throws PrismLangException
	{
if (DEBUG_Basic)
   System.out.println("<VisitPost_forUpdate vistor='FindAllVars'>");
		int i, j, n;
		String s;
if (DEBUG) System.out.println("Commencing FindAllVars.visitPost(Update) for this update: " + e);
		
		boolean sawAnIndexedSet = false;		// May no longer need this. - SHANE
		
		// For each element of update
		n = e.getNumElements();
if (DEBUG) System.out.println("The update has " + n + " update-elements to consider.");
		for (i = 0; i < n; i++) {
			ExpressionIdent targetOfUpdate = e.getVarIdent(i);
if (DEBUG) System.out.println("\nConsidering update-element " + (i+1) + "/"+n+", whose target is: \'" + targetOfUpdate + "\' [" + targetOfUpdate.getClass().getName() + "]" );	
			if (targetOfUpdate instanceof ExpressionIndexedSetAccess)
			{

				ExpressionIndexedSetAccess detail = (ExpressionIndexedSetAccess) targetOfUpdate;
if (DEBUG) System.out.println("The target of the update-element is an IndexedSet element. Calling accept on it.");
// THE FOLLOWING WAS TRIED (by commenting out the bulk of what comes below it); but it caused problems due to the element-of-update not having its type set.
//				detail.accept(this);		// 2019-2-14 hoping that it will proceed to the method below for EISAs.
if (DEBUG) System.out.println(" The target variable being updated IS AN ELEMENT within an indexed-set."); 

				// Consider the Access part's validity - is it an int value.
				Expression indexExp = detail.getIndexExpression();

if (DEBUG) System.out.println("  So I am going to call visit(FindAllVars) on the access expression: " + indexExp);
if (DEBUG) System.out.println("  <ResolveAccessExpression>");
				// Delve in so that the expression might be resolved.
				Expression res = (Expression) indexExp.accept(this);
if (DEBUG) System.out.println("  </ResolveAccessExpression>");
if (DEBUG) System.out.println("  FindAllVars.visitPost(Upd) has completed calling visit() on the access expression: " + indexExp);
if (DEBUG) System.out.println("  The result received from visit() " + ((res == indexExp) ? "is the same" : "has changed") + " [" + res.getClass().getName() + "]" );
if (DEBUG) { System.out.println("  That result, after visit(), is: " + res);  System.out.flush(); }
				detail.setIndexExpression(res);
	
				//refresh it (in case it just got changed by above line)
				indexExp = detail.getIndexExpression();

// SHANE Needs to deal with the case that the call to 'accept' a few lines above, may have transformed some ExpressionIdent into expressionVars.
// or rather, the case that when an access of an indexed set, is provided as the access expression of another set, the following sees a 'null' rather than an 'Int' type. Perhaps it is something that could be fixed by implementing getType in the ExprIndSetAcc class? 
				// Perform a type check of the indexExpression for accessing the set.
				if (!(indexExp.getType() instanceof TypeInt)) {
					s = "Invalid index expression '" + indexExp + "' given to access indexed set, expected int, saw: "
						+ indexExp.getType();
					PrismLangException ple = new PrismLangException(s, e.getVarIdent(i));	
if (DEBUG) ple.printStackTrace(System.out); else
					throw ple;
				}

				// Consider if it is out-of-bounds:
					// SHANE needs to do this - not done yet.

				// Consider the type that the result ought to be:
//if (DEBUG) System.out.println("Looking for "+ e.getVar(i) + "[0] (which is of the target kind)");
				j = varIdents.indexOf(e.getVar(i)+"[0]");		// the first element's type is all we need
				if (j == -1) {
					s = "Unknown indexed-set \"" + e.getVar(i) + "\"";
					PrismLangException ple = new PrismLangException(s, e.getVarIdent(i));	
if (DEBUG) ple.printStackTrace(System.out); else
					throw ple;
				}


				Type targetType = varTypes.get(j);
				e.setType(i, targetType);
if (DEBUG) System.out.println("  Also, determined its type: " + targetType);
if (DEBUG) System.out.println("  BUT UNLIKE normal variables, the j-position is being left for run-time determination [if that is even possible!].");

					
			

//System.out.println("but its type is reported to be: " + e.getTypeForElement(i));
//if (e.getTypeForElement(i) != null) System.out.println(  " (" + e.getTypeForElement(i).getClass().getName() +")");
//Exception eexx = new Exception(); eexx.printStackTrace(System.out);



				// We can know the name of the indexed set, and could derive the type for the element,
				// but there isn't much else we can really do at this point.
				sawAnIndexedSet = true;		// It means the setVarIndex will be wrong, staying at -1
			
				// **** And to make the run-time replacement will require we can access the varIdents Vector at run-time.
				// Maybe: replace Update with UpdateEnhanced, which has a reference to the varIdents.
// SHANE - TO-DO: Complete this!!! (maybe in other code files)	
			}
			else {		// Not an indexed-set, just an ordinary variable (seemingly)
if (DEBUG) System.out.println(" It is an ORDINARY variable: " + e.getVarIdent(i) );
				// Check variable exists - by finding its index within the vector of the ModulesFile's known variables.
				j = varIdents.indexOf(e.getVar(i));
				if (j == -1) {
					s = "Unknown variable \"" + e.getVar(i) + "\" in update";
					throw new PrismLangException(s, e.getVarIdent(i));
				}
				// Look up its type (in the ModulesFile), and store it inside the ElementOfUpdate
				Type tfe = e.getTypeForElement(i);

if (DEBUG_OrdinVar) System.out.println("\n  In FAV.visitPost(Upd) (normal var), before setType (for " + e.getVar(i) + "), the type is reported to be: " + tfe + " (" + ((tfe == null) ? "---" : tfe.getClass().getName()) +")");
if (DEBUG_OrdinVar) System.out.println("  and the VarIndex is " + e.getVarIndex(i) );
				e.setType(i, varTypes.get(j));
tfe = e.getTypeForElement(i);
if (DEBUG_OrdinVar) System.out.println("  After setType,  (for " + e.getVar(i) + "), the type is reported to be: " + tfe + " (" + ((tfe == null) ? "---" : tfe.getClass().getName()) +")\n" );

				// And store the variable index
				e.setVarIndex(i, j);
if (DEBUG_OrdinVar) System.out.println("  and its VarIndex is now " + e.getVarIndex(i) );
			}
			
if (DEBUG)
  System.out.println("Concluding call of FindAllVars.visitPost(Update) for this update: " + e);
if (DEBUG_Basic)
  System.out.println("</VisitPost_forUpdate visitor='FindAllVars'>");
		}
	}


// The comment in this javadoc may be outdated now...
	/** This would signify a node in the AST whereat is an expression attempting to access 
	 *  an indexed set, or rather, a specific position within the indexed set, for READING purposes.
         *  (e.g. if used in a calculation. Or maybe even as specification of a probability?)).
	 *  We cannot resolve exactly which variable is involved, unless the index expression is a constant or literal - 
	 *  normally the index needs to be determined at run-time.
	 */
// NOTE: SHANE has only observed this visit occur, if the Access of the indexed-set is within a GUARD; but not observed for when inside an Update Element.
	@Override
	public Object visit(ExpressionIndexedSetAccess e) throws PrismLangException
	{
		String s;
if (DEBUG_EISA) System.out.println("<Visit_EISA visitor='FAV'>");
		// COPIED FROM ABOVE METHOD (at an earlier point in time)
	// NOTE, perhaps this override is not needed (now that I think I have correctly modified the ASTTraverse and ASTTraverseModify)
if (DEBUG_EISA) System.out.println("\nReached FindAllVars.visit(ExprIndSetAcc) [overrides ASTTravMod] for this access expression: " + e );
				ExpressionIndexedSetAccess detail = e;
				// Consider the Access part's validity - is it an int value.
				Expression indexExp = detail.getIndexExpression();

if (DEBUG_EISA) System.out.println(" FAV.v(EISA): Going to call visit() on the access expression: " + indexExp);
				// Delve in so that the expression might be resolved.
				Expression resolve = (Expression) indexExp.accept(this);
if (DEBUG_EISA) System.out.println(" FAV.v(EISA): Completed call visit() on the access expression: " + indexExp + " - its type is " + resolve.getType());
				detail.setIndexExpression(resolve);
	
				//refresh it (in case it just got changed by above line)
				indexExp = detail.getIndexExpression();

				// Perform a type check of the indexExpression for accessing the set.
				if (!(indexExp.getType() instanceof TypeInt)) {
					s = "Invalid index expression '" + indexExp + "' given to access indexed set, expected int, saw: "
						+ indexExp.getType();
s += "[During FAV.visit(ExpIndSetAcc), and the object is actually a " + indexExp.getClass().getName() +"]";
					PrismLangException ple = new PrismLangException(s, e);	
if (DEBUG_EISA) ple.printStackTrace(System.out); else
					throw ple;
				}
else if (DEBUG_EISA) System.out.println(" FAV.v(EISA):Type of the argument used to access indexed-set is acceptable (int)");

				// Consider if it is out-of-bounds:
					// not done yet.

				// Consider the type that the result ought to be:
if (DEBUG_EISA) System.out.println(" FAV.v(EISA): Looking for "+ e.getName() + "[0]");
				int j = varIdents.indexOf(e.getName()+"[0]");		// the first element's type is all we need
				if (j == -1) {
					s = "Unknown indexed-set \"" + e.getName() + "\"";
					throw new PrismLangException(s, e);	
				}
if (DEBUG_EISA) System.out.println(" FAV.v(EISA) j for first element of indexed set is: " + j);
 
				Type targetType = varTypes.get(j);
if (DEBUG_EISA) System.out.println(" Determined its type: " + targetType + ", but LEAVING its j-position FOR RUN-TIME determination.");
				e.setType(targetType);

				// Tell it of the varIdents vector, to allow the specific element to be found at later time.
				e.setVarIdentsList(varIdents);	// enable run-time resolution of whichever index is to be accessed.


		// Consider the restriction expressions (if any) that apply to this accessing of the indexed set...
		List<Expression> restrExprs = e.getRestrictionExpressions();
		if (restrExprs != null && restrExprs.size() > 0) {
if (DEBUG_EISA) System.out.println(" FAV.v(EISA) - Delving into the restriction expressions...");
		    for (Expression curRestrExp : restrExprs) {		// Visit each of the restriction expressions.
if (DEBUG_ExpIndSetAcc) System.out.println(" The " + this.getClass().getName() + " visitor in ASTTraverse[ONLY].visit(ExprIndSetAcc) will call accept() on the restriction expression: " + curRestrExp);
			e.replaceRestrictionExpression(curRestrExp, (Expression) curRestrExp.accept(this));
		    }
		}
if (DEBUG_EISA) System.out.println(" FAV.v(EISA) - Finished delving into the restriction expressions.");


if (DEBUG_EISA) System.out.println("</Visit_EISA visitor='FAV'>");
		return detail;
	}		// End of method visit(ExprIndAccSet)

	/**
	 * When we consider an expression that is an identifier, if the identifier corresponds to a name
	 * of a variable, we convert it to an ExpressionVar node.  
	 */
// SHANE NOTE: It will not process any ExpressionIndexedSetAccess objects; they get dealt with by above method.
	@Override	
	public Object visit(ExpressionIdent e) throws PrismLangException
	{
		int i;
		// See if identifier corresponds to a variable
if (DEBUG) System.out.println("\nReached FindAllVars.visit(ExprIdent) for " + e + " [" + e.getClass().getName() + "]");


		i = varIdents.indexOf(e.getName());
		if (i != -1) {			// An exact variable is being accessed, or a indexed variable with known index is being accessed.
			// If so, replace it with an ExpressionVar object
			ExpressionVar expr = new ExpressionVar(e.getName(), varTypes.get(i));
			expr.setPosition(e);
			// Store variable index
			expr.setIndex(i);
			return expr;
		}
		// Otherwise, leave it unchanged
		return e;
	}
	
	// Also re-compute info for ExpressionVar objects in case variable indices have changed
	/**
	 * If we consider an ExpressionVar, we don't need to convert it (it has already been converted),
	 * but we may need to re-compute some information about it.
	 */
	public Object visit(ExpressionVar e) throws PrismLangException
	{
		int i;
if (DEBUG) System.out.println("\nReached FindAllVars.visit(ExpressionVar) for: " + e + " [no further debug messages for it]");
		// See if identifier corresponds to a variable
		i = varIdents.indexOf(e.getName());
		if (i != -1) {
			// If so, set the index
			e.setIndex(i);
			return e;
		}
		// Otherwise, there is a problem
		throw new PrismLangException("Unknown variable " + e.getName() + " in ExpressionVar object", e);
	}
}

