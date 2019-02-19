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
 * Find all RestrictedScopeExpressions during a visitor traversal, gathering them into a List that can be obtained afterwards.
 */

public class FindRestrictedScopeExprs extends ASTTraverse
{
public static boolean DEBUG = true;
	private ArrayList<RestrictedScopeExpression> rseList = new ArrayList<RestrictedScopeExpression>();

	public FindRestrictedScopeExprs ()
	{
	}

	public List<RestrictedScopeExpression> getExpressions()
	{
		return rseList;
	}

	@Override
	public Object visit(RestrictedScopeExpression e) throws PrismLangException
	{
if (DEBUG) System.out.println("FindRstrScpExpr.visit(RSE) has found this RestrictedScope Expression: \'"+ e );
		// Currently, we do not allow nested RestrictedScopeExpressions. So we won't delve into the compartments of this.
		// We will just note it.
		rseList.add(e);

		// Nothing to return.
		return null;
	}
}

