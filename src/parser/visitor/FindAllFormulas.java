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
 * Find all identifiers which are formulas, replace with ExpressionFormula, return result.
 */
public class FindAllFormulas extends ASTTraverseModify
{
public static boolean DEBUG = false && DEBUG_SHOW_ENABLED;
	private FormulaList formulaList;
	
	public FindAllFormulas(FormulaList formulaList)
	{
		this.formulaList = formulaList;
	}
	
	public Object visit(ExpressionIdent e) throws PrismLangException
	{
if (DEBUG) System.out.println("FindAllFormulas.visit(ExprIdent) (Overrides default visit() of ASTTM) called for ExprIdent: \'"+ e + "\' [" + e.getClass().getName() + "]");

		int i;
		// See if identifier corresponds to a formula
		i = formulaList.getFormulaIndex(e.getName());
		if (i != -1) {
			// If so, replace it with an ExpressionFormula object
			ExpressionFormula expr = new ExpressionFormula(e.getName());
			expr.setPosition(e);
			return expr;
		}
		// Otherwise, leave it unchanged
		return e;
	}
}

