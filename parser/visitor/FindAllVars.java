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

import java.util.Vector;

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
	public static boolean DEBUG = true;

	private Vector<String> varIdents;
	private Vector<Type> varTypes;
	
	public FindAllVars(Vector<String> varIdents, Vector<Type> varTypes)
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
	 // (The index will often need to be computed at update-run-time, if an expression is given instead).
	public void visitPost(Update e) throws PrismLangException
	{
		int i, j, n;
		String s;
		
		boolean sawAnIndexedSet = false;		// May not need, now. - shane
		
		// For each element of update
		n = e.getNumElements();
		for (i = 0; i < n; i++) {
			ExpressionIdent targetOfUpdate = e.getVarIdent(i);
			
			if (targetOfUpdate instanceof ExpressionIndexedSetAccess)
			{
				ExpressionIndexedSetAccess detail = (ExpressionIndexedSetAccess) targetOfUpdate;
System.out.println("\nDealing with indexed-set access for: " + e.getVarIdent(i));
				// Consider the Access part's validity - is it an int value.
				Expression indexExp = detail.getIndexExpression();

System.out.println("Going to call visit() on the access expression: " + indexExp);
				// Delve in so that the expression might be resolved.
				Expression res = (Expression) indexExp.accept(this);
System.out.println("Completed call visit() on the access expression: " + indexExp + " - its type is " + res.getType());
				detail.setIndexExpression(res);
	
				//refresh it (in case it just got changed by above line)
				indexExp = detail.getIndexExpression();

				// Perform a type check of the indexExpression for accessing the set.
				if (!(indexExp.getType() instanceof TypeInt)) {
					s = "Invalid index expression given to access indexed set, expected int, saw: "
						+ indexExp.getType();
					PrismLangException ple = new PrismLangException(s, e.getVarIdent(i));	
if (DEBUG) ple.printStackTrace(System.out); else
					throw ple;
				}
else System.out.println("Type of the argument used to access indexed-set is acceptable (int)");

				// Consider if it is out-of-bounds:
					// not done yet.

				// Consider the type that the result ought to be:
System.out.println("Looking for "+ e.getVar(i) + "[0] (which is of the target kind)");
				j = varIdents.indexOf(e.getVar(i)+"[0]");		// the first element's type is all we need
				if (j == -1) {
					s = "Unknown indexed-set \"" + e.getVar(i) + "\"";
					PrismLangException ple = new PrismLangException(s, e.getVarIdent(i));	
if (DEBUG) ple.printStackTrace(System.out); else
					throw ple;
				}


				Type targetType = varTypes.elementAt(j);
				e.setType(i, targetType);
System.out.println("Determined its type: " + targetType + ", but leaving its j-position for run-time determination.");

					
			

//System.out.println("but its type is reported to be: " + e.getTypeForElement(i));
//if (e.getTypeForElement(i) != null) System.out.println(  " (" + e.getTypeForElement(i).getClass().getName() +")");
//Exception eexx = new Exception(); eexx.printStackTrace(System.out);
				// We can know the name of the indexed set, and could derive the type for the element,
				// but there isn't much else we can really do at this point.
				sawAnIndexedSet = true;		// It means the setVarIndex will be wrong, staying at -1
				
				// **** And to make the run-time replacement will require we can access the varIdents Vector at run-time.
				// Maybe: replace Update with UpdateEnhanced, which has a reference to the varIdents.
			}
			else {		// Not an indexed-set, just an ordinary variable (seemingly)
System.out.println("\nDealing with: " + e.getVarIdent(i) + " (which is non-indexed)");
				// Check variable exists - by finding its index within the vector of the ModulesFile's known variables.
				j = varIdents.indexOf(e.getVar(i));
				if (j == -1) {
					s = "Unknown variable \"" + e.getVar(i) + "\" in update";
					throw new PrismLangException(s, e.getVarIdent(i));
				}
				// Look up its type (in the ModulesFile), and store it inside the ElementOfUpdate
				Type tfe = e.getTypeForElement(i);

System.out.println("\nBefore setType (for " + e.getVar(i) + "), the type is reported to be: " + tfe + " (" + ((tfe == null) ? "---" : tfe.getClass().getName()) +")");
				e.setType(i, varTypes.elementAt(j));
tfe = e.getTypeForElement(i);
System.out.println("After setType,  (for " + e.getVar(i) + "), the type is reported to be: " + tfe + " (" + ((tfe == null) ? "---" : tfe.getClass().getName()) +")\n" );
				// And store the variable index
				e.setVarIndex(i, j);
			}
			
		}
	}


	/** This would signify a node in the AST whereat is an expression attempting to access 
	 *  an indexed set, or rather, a specific position within the indexed set, for READING purposes.
         *  (e.g. if used in a calculation. Or maybe even as specification of a probability?)).
	 *  We cannot resolve exactly which variable is involved, unless the index expression is a constant or literal - 
	 *  normally the index needs to be determined at run-time.
	 */
	@Override
	public Object visit(ExpressionIndexedSetAccess e) throws PrismLangException
	{
		String s;
		// COPIED FROM ABOVE
				ExpressionIndexedSetAccess detail = e;
System.out.println("2:Interpreting access-expression: " + e);
				// Consider the Access part's validity - is it an int value.
				Expression indexExp = detail.getIndexExpression();

System.out.println("2:Going to call visit() on the access expression: " + indexExp);
				// Delve in so that the expression might be resolved.
				Expression resolve = (Expression) indexExp.accept(this);
System.out.println("2:Completed call visit() on the access expression: " + indexExp + " - its type is " + resolve.getType());
				detail.setIndexExpression(resolve);
	
				//refresh it (in case it just got changed by above line)
				indexExp = detail.getIndexExpression();

				// Perform a type check of the indexExpression for accessing the set.
				if (!(indexExp.getType() instanceof TypeInt)) {
					s = "Invalid index expression given to access indexed set, expected int, saw: "
						+ indexExp.getType();
					throw new PrismLangException(s, e);	
				}
else System.out.println("2:Type of the argument used to access indexed-set is acceptable (int)");

				// Consider if it is out-of-bounds:
					// not done yet.

				// Consider the type that the result ought to be:
System.out.println("2:Looking for "+ e.getName() + "[0]");
				int j = varIdents.indexOf(e.getName()+"[0]");		// the first element's type is all we need
				if (j == -1) {
					s = "Unknown indexed-set \"" + e.getName() + "\"";
					throw new PrismLangException(s, e);	
				}
System.out.println("j for first element of indexed set is: " + j);

				Type targetType = varTypes.elementAt(j);
System.out.println("2:Determined its type: " + targetType + ", but leaving its j-position for run-time determination.");
				e.setType(targetType);
		return detail;
	}		// End of method visit(ExprIndAccSet)

	/**
	 * When we consider an expression that is an identifier, if the identifier corresponds to a name
	 * of a variable, we convert it to an ExpressionVar node.  
	 */
	@Override
	public Object visit(ExpressionIdent e) throws PrismLangException
	{
		int i;
		// See if identifier corresponds to a variable
		i = varIdents.indexOf(e.getName());
		if (!(e instanceof ExpressionIndexedSetAccess)) {	// IndexedSet Access is to be treated differently/later.
		   if (i != -1) {
			// If so, replace it with an ExpressionVar object
			ExpressionVar expr = new ExpressionVar(e.getName(), varTypes.elementAt(i));
			expr.setPosition(e);
			// Store variable index
			expr.setIndex(i);
			return expr;
		   }
		} 
else System.out.println("visit(ExprIdent) was called, with an ExpressionIndexedSetAccess object as the parameter: " + e);
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

