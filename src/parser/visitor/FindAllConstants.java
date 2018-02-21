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

import parser.ast.*;
import prism.PrismLangException;

/**
 * Find all idents which are constants, replace with ExpressionConstant, return result.
 */
public class FindAllConstants extends ASTTraverseModify
{
	private ConstantList constantList;
	
	public FindAllConstants(ConstantList constantList)
	{
		this.constantList = constantList;
	}
	
	public Object visit(ExpressionIdent e) throws PrismLangException
	{
System.out.println("FindAllConst.visit(ExprIdent) for "+ e + " [" + e.getClass().getName() + "]");

if (e instanceof ExpressionIndexedSetAccess)
System.out.println("Are you considering FindAllConstants for IndexedSet Access for this: " + e);
		int i;
		// See if identifier corresponds to a constant
		i = constantList.getConstantIndex(e.getName());
		if (i != -1) {
			// If so, replace it with an ExpressionConstant object
			ExpressionConstant expr = new ExpressionConstant(e.getName(),  constantList.getConstantType(i));
			expr.setPosition(e);
			return expr;
		}
		// Otherwise, leave it unchanged
		return e;
	}

	public Object visit(Update e) throws PrismLangException
	{
		int i, j, n;
		String s;
System.out.println("FindAllConstants.visit(Update) for : " + e);
		
		boolean sawAnIndexedSet = false;		// May not need, now. - shane
		
		// For each element of update, consider both the target, and the calculation, in case they involve constants
		n = e.getNumElements();
		for (i = 0; i < n; i++) {
			ExpressionIdent targetOfUpdate = e.getVarIdent(i);
if (DEBUG) System.out.println("Considering update element "+ (i+1) + "/"+n+"'s target: " + targetOfUpdate);			
			if (targetOfUpdate instanceof ExpressionIndexedSetAccess)	// A constant may occur during the index access expression
			{
				ExpressionIndexedSetAccess detail = (ExpressionIndexedSetAccess) targetOfUpdate;
if (DEBUG) System.out.println("\nDealing with indexed-set access for: " + e.getVarIdent(i));
				// Consider the Access part's validity - is it an int value.
				Expression indexExp = detail.getIndexExpression();

if (DEBUG) System.out.println("Going to call visit() on the access expression: " + indexExp);
				// Delve in so that the expression might be resolved.
				Expression revisedTarget = (Expression) indexExp.accept(this);
if (DEBUG) System.out.println("Completed call visit() on the access expression: " + indexExp + " which is now " + revisedTarget + " - (and its type is " + revisedTarget.getType() +")");
				detail.setIndexExpression(revisedTarget);
	
				//refresh it (in case it just got changed by above line)
				indexExp = detail.getIndexExpression();
			}

			Expression calcExpr = e.getExpression(i);
if (DEBUG) System.out.println("Considering update element "+ (i+1) + "/"+n+"'s calculation: " + calcExpr);
			Expression newCalcExpr = (Expression) calcExpr.accept(this);
if (DEBUG) System.out.println("After accept, the calculation is now: " + newCalcExpr);
			e.setExpression(i, newCalcExpr);
		}


		return e;
	}
}

