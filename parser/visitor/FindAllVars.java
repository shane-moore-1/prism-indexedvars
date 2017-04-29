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
	 * this method determines which variables of the Model are to be updated, and pre-computes their
	 * position.
	 */ 
	 // SHANE's comments:
	 // Before this is called:
	 //  1. each identifier specified as a target to be updated will have its 'index' set to -1. 
     //  2. each identifier specified as a target will have its 'type' set as NULL.
	 //
	 //  This visitor method will aim to change the index to correspond to the position in 
	 //  ModulesFile.vars where the recipient is located, and to set the type (within the ElementOfUpdate
	 //  object) to have a reference to the corresponding type of that identifier.
	 //
	 // But if the recipient variable of any ElementOfUpdate is an indexed-set, we cannot determine the index,
	 // but we can at least determine the type. (The index will need to be computed at update-time).
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
				// We can know the name of the indexed set, and could derive the type for the element,
				// but there isn't much else we can really do at this point.
				sawAnIndexedSet = true;		// It means the setVarIndex will be wrong, staying at -1
				
				// **** And to make the run-time replacement will require we can access the varIdents Vector at run-time.
				// Maybe: replace Update with UpdateEnhanced, which has a reference to the varIdents.
			}
			else {		// Not an indexed-set, just an ordinary variable (seemingly)
				// Check variable exists - by finding its index within the vector of the ModulesFile's known variables.
				j = varIdents.indexOf(e.getVar(i));
				if (j == -1) {
					s = "Unknown variable \"" + e.getVar(i) + "\" in update";
					throw new PrismLangException(s, e.getVarIdent(i));
				}
				// Look up its type (in the ModulesFile), and store it inside the ElementOfUpdate
				e.setType(i, varTypes.elementAt(j));
				// And store the variable index
				e.setVarIndex(i, j);
			}
		}
	}

// SHANE thinks there is no need for dealing-with any ExpressionIndexedSetAccess occurrences; or it
// could simply do a check on the name	
//	Leave it all to the 'evaluate' call.
	/** This would signify a node in the AST whereat is an expression attempting to access 
	 * an indexed set, or rather, a specific position within the indexed set.
	 * Perhaps this should move into a separate visitor class?
	 */
	public Object visit(ExpressionIndexedSetAccess e) throws PrismLangException
	{
		//probably needs to become an ExpressionVar, linking to the relevant item of the set.
		throw new PrismLangException("FindAllVars.visit(ExpressionIndexedSetAccess) not yet implemented.");
		
		// Maybe this has to be moved to run-time, since it needs to evaluate the access-expression to
		// know exactly which variable to access.
	}

	/**
	 * When we consider an expression that is an identifier, if the identifier corresponds to a name
	 * of a variable, we convert it to an ExpressionVar node.  
	 */
	public Object visit(ExpressionIdent e) throws PrismLangException
	{
		int i;
		// See if identifier corresponds to a variable
		i = varIdents.indexOf(e.getName());
		if (!(e instanceof ExpressionIndexedSetAccess))		// IndexedSet Access is to be treated differently/later.
		if (i != -1) {
			// If so, replace it with an ExpressionVar object
			ExpressionVar expr = new ExpressionVar(e.getName(), varTypes.elementAt(i));
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

