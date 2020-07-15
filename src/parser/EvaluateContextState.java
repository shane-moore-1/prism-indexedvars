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

package parser;

/**
 * Information required to evaluate an expression: a State object.
 * This is basically an array of Objects, indexed according to a model file. 
 * Optionally values for constants can also be supplied.
 */
public class EvaluateContextState implements EvaluateContext
{
public static boolean DEBUG_Constructors = true;
public static boolean DEBUG_getCV = true;

public static int nextInstanceID = 0;

	private Values constantValues;
	private Object[] varValues;
private int InstanceID;

	public EvaluateContextState(State state)
	{
		this.constantValues = null;
		this.varValues = state.varValues;
this.InstanceID = ++nextInstanceID;
if (DEBUG_Constructors) {
  System.out.println("Constructor 1 of parser.EvaluateContextState - making ECS #"+InstanceID);
  Exception stackTr = new Exception("STACK TRACE ONLY");
  stackTr.printStackTrace(System.out);
}
	}

	public EvaluateContextState(Values constantValues, State state)
	{
		this.constantValues = constantValues;
		this.varValues = state.varValues;
this.InstanceID = ++nextInstanceID;
if (DEBUG_Constructors) System.out.println("Constructor 2 of parser.EvaluateContextState - making ECS #"+InstanceID);
	}

	public Object getConstantValue(String name)
	{
if (DEBUG_getCV) {
System.out.println("\n~~ in parser.EvaluateContextState (Instance #"+InstanceID+"): Looking for constant: " + name);
if (constantValues == null)
System.out.println(" -- but the constantValues is null!!");
}
		if (constantValues == null)
			return null;
if (DEBUG_getCV) {
System.out.println("The following constants are defined: ");
  System.out.println(constantValues);
}
		int i = constantValues.getIndexOf(name);
if (DEBUG_getCV && i == -1) {
	  System.out.println("It apparently doesn't exist!");
	}
		if (i == -1)
			return null;
		return constantValues.getValue(i);
	}

	public Object getVarValue(String name, int index)
	{
if (DEBUG_getCV) {
	System.out.println("\n~~ in parser.EvaluateContextState (Instance #"+InstanceID+"): Looking for variable: " + name + " using index " + index);
}
		// Use index to look up value
		return index == -1 ? null : varValues[index];
	}
}
