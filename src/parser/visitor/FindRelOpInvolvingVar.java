//==============================================================================
//	
//	Copyright (c) 2002-
//	Authors:
//	* THIS FILE - Shane Moore
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

import java.util.ArrayList;
import java.util.List;

/**
 * Find all relational operators involving a specific variable on the left-hand side, and collect in to a List for retrieval.
 * For example:  index < 5   would match, but    5 > index   would not match for the variable name 'index'.
 */

// SHANE NOTE:  Consider adding a new expression-type for Guards, a "Restrict[indexedSet,lower,higher]" clause, as a simpler
// way of determining rather than extracting out from other guards? (If I can't get this one to work in all cases...)
public class FindRelOpInvolvingVar extends ASTTraverse
{
public static boolean DEBUG = false;
	private String varNameToFind;
	private ArrayList<ExpressionBinaryOp> expressionsThatInvolve = new ArrayList<ExpressionBinaryOp>();

	public FindRelOpInvolvingVar (String varNameToFind)
	{
		this.varNameToFind = varNameToFind;
	}

	public List<ExpressionBinaryOp> getExpressionsThatInvolve()
	{
		return expressionsThatInvolve;
	}

	@Override
	public Object visit(ExpressionBinaryOp e) throws PrismLangException
	{
if (DEBUG) System.out.println("FindRelOp.visit(ExprBinOp) for '"+varNameToFind+"' called on this rel op expr: \'"+ e + "\' [" + e.getClass().getName() + "]");
		int whichOp = e.getOperator();
		// If it is an AND we need to delve into both operands, in case one of them involves a relational. (ORs don't have to be true, so can't be checked)
		if (whichOp == ExpressionBinaryOp.AND) {
if (DEBUG) System.out.println("FindRelOp.visit(ExprBinOp) will check first operand: " + e.getOperand1());
			e.getOperand1().accept(this);
if (DEBUG) System.out.println("FindRelOp.visit(ExprBinOp) has checked first operand: " + e.getOperand1());
if (DEBUG) System.out.println("FindRelOp.visit(ExprBinOp) will check second operand: " + e.getOperand2());
			e.getOperand2().accept(this);
if (DEBUG) System.out.println("FindRelOp.visit(ExprBinOp) has checked second operand: " + e.getOperand2());
		}
		// If it is a relational operator, rather than some other binary operator...
		else if (whichOp == ExpressionBinaryOp.EQ ||whichOp == ExpressionBinaryOp.NE ||	// Could have said e.isRelOp(whichOp)
		     whichOp == ExpressionBinaryOp.LT ||whichOp == ExpressionBinaryOp.LE ||
		     whichOp == ExpressionBinaryOp.GT ||whichOp == ExpressionBinaryOp.GE)
		{
			// We require the variable to be on the LEFT side of relational expressions.
		 	if (e.getOperand1() instanceof ExpressionVar) 
			{
				// If the var has the name we are looking for, then put it into our result list.
				if (((ExpressionVar) e.getOperand1()).getName().equals(varNameToFind))
{
if (DEBUG) System.out.println("  It is relational, and matches the desired variable, so was added to the results list.");
				  expressionsThatInvolve.add(e);		// The current RelOp will be put in results, and don't go deeper.
} else if (DEBUG) System.out.println("  Whilst relational, it doesn't involve varName " + varNameToFind+ " on left-side so WON'T be added to the results list.");
			}
			else if (e.getOperand1() instanceof ExpressionIdent)
			{
				// If the identifier is for the name we are looking for, then put it into our result list.
				if (((ExpressionIdent) e.getOperand1()).getName().equals(varNameToFind))
{
if (DEBUG) System.out.println("  It is relational, and matches the desired variable, so was added to the results list.");
				  expressionsThatInvolve.add(e);		// The current RelOp will be put in results, and don't go deeper.
} else if (DEBUG) System.out.println("  Whilst relational, it doesn't involve varName " + varNameToFind+ " on left-side so WON'T be added to the results list.");

				
			}
else System.out.println("The operand1 is an: " + e.getOperand1().getClass().getName());
		}
else System.out.println("The whichOp value is: " + whichOp);

		// We do not need to delve any deeper for other cases.

		// Nothing to return.
		return null;
	}
}

