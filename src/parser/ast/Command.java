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

package parser.ast;

import parser.visitor.*;
import prism.PrismLangException;
import java.util.*;

public class Command extends ASTElement
{
	// Action label
	private String synch;
	// Index of action label in model's list of all actions ("synchs")
	// This is 1-indexed, with 0 denoting an independent ("tau"-labelled) command.
	// -1 denotes not (yet) known.
	private int synchIndex;
	// Guard
	private Expression guard;
	// List of updates
	private Updates updates;
	// Parent module
	private Module parent;
	
	// Constructor
	
	public Command()
	{
		synch = "";
		synchIndex = -1;
		guard = null;
		updates = null;
	}
	
	// Set methods
	
	public void setSynch(String s)
	{
		synch = s;
	}
	
	public void setSynchIndex(int i)
	{
		synchIndex = i;
	}
	
	public void setGuard(Expression g)
	{
		guard = g;
	}
	
	public void setUpdates(Updates u)
	{
		updates = u;
		u.setParent(this);
	}
	
	public void setParent(Module m)
	{
		parent = m;
	}

	// Get methods

	/**
	 * Get the action label for this command. For independent ("tau"-labelled) commands,
	 * this is the empty string "" (it should never be null).
	 */
	public String getSynch()
	{
		return synch;
	}
	
	/**
	 * Get the index of the action label for this command (in the model's list of actions).
	 * This is 1-indexed, with 0 denoting an independent ("tau"-labelled) command.
	// -1 denotes not (yet) known.
	 */
	public int getSynchIndex()
	{
		return synchIndex;
	}
	
	public Expression getGuard()
	{
		return guard;
	}
	
	public Updates getUpdates()
	{
		return updates;
	}
	
	public Module getParent()
	{
		return parent;
	}

	/** Request a Set of all distinct expressions for indexed set accesses that involve variable position specifications,
	    so that we can ensure that each possible valuation can be catered-for in the translation into MTBDD.
	*/
	public Set<ExpressionIndexedSetAccess> getVariablePosEISAs()
	{
		Set<ExpressionIndexedSetAccess> varPosEISAs = new TreeSet<ExpressionIndexedSetAccess>();
		List<ExpressionIndexedSetAccess> guardEISAs = guard.getVariablePosEISAs();
		if (guardEISAs != null && guardEISAs.size() > 0)
			varPosEISAs.addAll(guardEISAs);

		// Note that some updates in a command may not even make reference to indexed sets; but we need to know any that are present...
		List<ExpressionIndexedSetAccess> updateEISAs = updates.getVariablePosEISAs();
		if (updateEISAs != null && updateEISAs.size() > 0)
			varPosEISAs.addAll(updateEISAs);

		return varPosEISAs;
	}
	
	// Methods required for ASTElement:
	
	/**
	 * Visitor method.
	 */
	public Object accept(ASTVisitor v) throws PrismLangException
	{
		return v.visit(this);
	}
	
	/**
	 * Convert to string.
	 */
	public String toString()
	{
		String s = "[" + synch;
		s += "] " + guard + " -> " + updates;
		return s;
	}
	
	/**
	 * Perform a deep copy.
	 */
	public ASTElement deepCopy()
	{
		Command ret = new Command();
		ret.setSynch(getSynch());
		ret.setSynchIndex(getSynchIndex());
		ret.setGuard(getGuard().deepCopy());
		ret.setUpdates((Updates)getUpdates().deepCopy());
		ret.setPosition(this);
		return ret;
	}
}

//------------------------------------------------------------------------------
