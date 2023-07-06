//==============================================================================
//	
//	Copyright (c) 2002-
//	Authors:
//  ** This File by Shane Moore <shane.moore@student.rmit.edu.au> (RMIT University, Australia)
//	* Dave Parker <david.parker@comlab.ox.ac.uk> (University of Oxford)
//	* Mark Kattenbelt <mark.kattenbelt@comlab.ox.ac.uk> (University of Oxford)
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

import parser.type.*;
import parser.visitor.ASTVisitor;
import prism.PrismLangException;

/**
 * This class is used by the Parser, to represent that it encountered a declaration (in the prism source code file) of an
 * indexed-set of variables (i.e. an array), for which individual variables will need to be instantiated at model-execution time,
 * such variables will all have as a common prefix the 'name' of the variable, and be distinguished by the index being postfixed to
 * the variable name, e.g.   counters[1] counters[2], counters[3], each would be individual variables.
 * 
 * @author Shane Moore
 *
 */
public class DeclTypeIndexedSet extends DeclarationType
{

private static boolean DEBUG_DTIS = false;

	// Size of array, in terms of the uppermost index position (assuming we always start at position 1).
	protected Expression size;
	// Type used for the elements of this Indexed Set (i.e. an Array, by a different name!)
	protected DeclarationType elementsType;

	public DeclTypeIndexedSet(DeclarationType elementsType, Expression size)
	{
		this.size = size;
		this.elementsType = elementsType;

		// The type stored for a Declaration/DeclarationType object
		// is static - it is not computed during type checking.
		// (But we re-use the existing "type" field for this purpose)
		// And we copy the info from DeclarationType across to Declaration for convenience.
		setType(new TypeIndexedSet(elementsType.getType()));
	}

	/** Returns the number of elements intended to comprise this IndexedSet.
	 * 
	 * @return the number of elements desired.
	 */
	public Expression getSize()
	{
		return size;
	}

	// Required by the ASTTraverseModify (in case the expression specifying the size needs to be modified by the visitor)
	public void setSize(Expression s)
	{
		size = s;
	}

	public DeclarationType getElementsType()
	{
		return elementsType;
	}

	// Required by the ASTTraverseModify (in case the DeclarationType needs to be modified by the visitor)
	public void setElementsType(DeclarationType type)
	{
		this.elementsType = type;
	}

	/**
	 * Always generates an exception, because this is a placeholder for what turns out to be separate individual variables at model execution time.
	 */
	public Expression getDefaultStart()
	{
		throw new RuntimeException("You should not be calling getDefaultStart for parser.ast.DeclarationIndexedSet");
	}
	
	
	/**
	 * Visitor method. In recursive traversal by visitors, this method will enable THIS DeclTypeIndexedSet to be processed by the visitor.
	 */
	public Object accept(ASTVisitor v) throws PrismLangException
	{
		return v.visit(this);
	}

	/**
	 * Perform a deep copy - Not sure why needed though, so the implementation could be inappropriate (since copied from other classes)
	 */
	@Override
	public ASTElement deepCopy()
	{
if (DEBUG_DTIS) {
  Exception e = new Exception();
  System.out.println("In DeclTypeIndexedSet.deepCopy, stack is: ");
  e.printStackTrace();
}
		Expression sizeCopied = (size == null) ? null : size.deepCopy();
		DeclTypeIndexedSet ret = new DeclTypeIndexedSet(elementsType,sizeCopied);
		ret.setPosition(this);
		return ret;
	}


	
	/**
	 * Convert to string.
	 */
	@Override
	public String toString()
	{
		return "indexed set of size: [" + size  + "] and of type: " + elementsType;
	}


}
