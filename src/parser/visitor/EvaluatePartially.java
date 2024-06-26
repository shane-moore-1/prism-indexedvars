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

import parser.*;
import parser.ast.*;
import prism.PrismLangException;

/**
 * Evaluate partially: replace some constants and variables with actual values. 
 */
public class EvaluatePartially extends ASTTraverseModify
{
public static boolean DEBUG = DEBUG_SHOW_ENABLED;
	private EvaluateContext ec;
	
	public EvaluatePartially(EvaluateContext ec)
	{
		this.ec = ec;
	}
	
	public Object visit(ExpressionConstant e) throws PrismLangException
	{
if (DEBUG) System.out.println("EvaluatePartially.visit(ExprConst) called for : " + e);
		Object val = ec.getConstantValue(e.getName());
		if (val == null) {
if (DEBUG) System.out.println(" Because val is null, returning the original: " + e);
			return e;
		} else {
if (DEBUG) System.out.println(" Going to replace (return) with a new ExpressionLiteral: ");
			return new ExpressionLiteral(e.getType(), val);
		}
	}
	
	public Object visit(ExpressionVar e) throws PrismLangException
	{
if (DEBUG) System.out.println("EvaluatePartially.visit(ExprVar) called for : " + e);
		Object val = ec.getVarValue(e.getName(), e.getIndex());
		if (val == null) {
if (DEBUG) System.out.println(" Because val is null, returning the original: " + e);
			return e;
		} else {
if (DEBUG) System.out.println(" Going to replace (return) with a new ExpressionLiteral: ");
			return new ExpressionLiteral(e.getType(), val);
		}
	}

/* The following IS NOT NEEDED. In fact, when it was added, it prevented the simulator from getting past the first step! The ASTTravModify is sufficient to replace constants that occur within the index-access expressions, and restriction expressions.
	public Object visit(ExpressionIndexedSetAccess eisa) throws PrismLangException
	{
System.out.println("EvaluatePartially.visit called on an ExpressionIndexedSetAccess object - WHAT WILL YOU DO HERE??? (Currently doing NOTHING!!)");
System.out.println("The object is: " + eisa);

		return eisa;
	}
*/

}
