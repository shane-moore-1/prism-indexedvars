//==============================================================================
//	
//	Copyright (c) 2002-
//	Authors:
//	* Dave Parker <david.parker@comlab.ox.ac.uk> (University of Oxford, formerly University of Birmingham)
//	* Christian von Essen <christian.vonessen@imag.fr> (VERIMAG)
//	* Joachim Klein <klein@tcs.inf.tu-dresden.de> (TU Dresden)
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

package jdd;

public class JDDNode
{
private boolean SHANE_DEBUG = true;
private static String ShanePURPOSE_Unknown = "[Unknown Purpose]";
private String ShanePURPOSE = ShanePURPOSE_Unknown;
private int ShaneID;
private static int ShaneNextID = 0;
private Exception ShaneCreationTimeStack;

public void setPurpose(String purpose)
{
	ShanePURPOSE = purpose;
}

	private long ptr;
	
	// native methods (jni)
	protected static native boolean DDN_IsConstant(long dd);
	protected static native int DDN_GetIndex(long dd);
	protected static native double DDN_GetValue(long dd);
	protected static native long DDN_GetThen(long dd);
	protected static native long DDN_GetElse(long dd);

	static
	{
		try {
			System.loadLibrary("jdd");
		}
		catch (UnsatisfiedLinkError e) {
			System.out.println(e);
			System.exit(1);
		}
	}

	/**
	 * Protected constructor from a DdNode pointer.
	 * In general, to get a JDDNode from a pointer,
	 * use JDD.ptrToNode().
	 */
	protected JDDNode(long p)
	{
		ptr = p;
if (SHANE_DEBUG)
{
ShaneID = ++ShaneNextID;
// Guess the purpose, by saying what the most recent few method calls were (It can always be replaced with better description):
ShaneCreationTimeStack = new Exception();
StackTraceElement[] stackElts = ShaneCreationTimeStack.getStackTrace();
ShanePURPOSE = //ShanePURPOSE_Unknown;

"{Created in " + stackElts[1] + 
"\n\twhich was called by " + stackElts[2] + 
(stackElts.length >= 3 ? "\n\twhich was called by " + stackElts[3] : "") +
(stackElts.length >= 4 ? "\n\twhich was called by " + stackElts[4] : "") +
"\n}\n";
}
	}
	
	public long ptr()
	{
		return ptr;
	}

	public boolean isConstant()
	{
		return DDN_IsConstant(ptr);
	}

	public int getIndex()
	{	
		return DDN_GetIndex(ptr);
	}

	public double getValue()
	{
		if (DebugJDD.debugEnabled) {
			return DebugJDD.nodeGetValue(this);
		}
		return DDN_GetValue(ptr);
	}

	/**
	 * Returns the Then child of a (non-constant) JDDNode.
	 * <br>
	 * This method does NOT increase the reference count of the returned
	 * node, it is therefore illegal to call JDD.Deref on the result.
	 * Additionally, it is recommended to not use the returned node
	 * as the argument to the JDD methods or call JDD.Ref on it.
	 * Instead, if you need to obtain a "proper" node, call copy()
	 * on the returned node.
	 * <br>[ REFS: <i>none</i>, DEREFS: <i>none</i> ]
	 */
	public JDDNode getThen()
	{
		if (DebugJDD.debugEnabled) {
			return DebugJDD.nodeGetThen(this);
		}

		long thenPtr = DDN_GetThen(ptr);
		if (thenPtr == 0) {
			if (isConstant()) {
				throw new RuntimeException("Trying to access the 'then' child of a constant MTBDD node");
			} else {
				throw new RuntimeException("getThen: CUDD returned NULL, but node is not a constant node. Out of memory or corrupted MTBDD?");
			}
		}
		return new JDDNode(thenPtr);
	}

	/**
	 * Returns the Else child of a (non-constant) JDDNode.
	 * <br>
	 * This method does NOT increase the reference count of the returned
	 * node, it is therefore illegal to call JDD.Deref on the result.
	 * Additionally, it is recommended to not use the returned node
	 * as the argument to the JDD methods or call JDD.Ref on it.
	 * Instead, if you need to obtain a "proper" node, call copy()
	 * on the returned node.
	 * <br>[ REFS: <i>none</i>, DEREFS: <i>none</i> ]
	 */
	public JDDNode getElse()
	{
		if (DebugJDD.debugEnabled) {
			return DebugJDD.nodeGetElse(this);
		}

		long elsePtr = DDN_GetElse(ptr);
		if (elsePtr == 0) {
			if (isConstant()) {
				throw new RuntimeException("Trying to access the 'else' child of a constant MTBDD node");
			} else {
				throw new RuntimeException("getElse: CUDD returned NULL, but node is not a constant node. Out of memory or corrupted MTBDD?");
			}
		}
		return new JDDNode(elsePtr);
	}

	public boolean equals(Object o)
	{
		return (o instanceof JDDNode) && (((JDDNode) o).ptr == ptr);
	}
	
	public int hashCode()
	{
		return (int)ptr; 
	}
	
	public String toString()
	{
		String result = "" + ptr;
		if (ptr != 0) {
			if (this.isConstant()) result += " value=" + this.getValue();
			result += " references=" + DebugJDD.getRefCount(this);
		}
if (SHANE_DEBUG) {
  result += " [shane ID= " + ShaneID + ", purp = " + ShanePURPOSE;

  if (ShanePURPOSE == ShanePURPOSE_Unknown) {	// Show stack of creation time, partially:
StackTraceElement[] stackElts = ShaneCreationTimeStack.getStackTrace();
    result += "{Created in " + stackElts[1] + 
    ", which was called by " + stackElts[2] + 
    (stackElts.length >= 3 ? ", which was called by " + stackElts[3] : "") +
    (stackElts.length >= 4 ? ", which was called by " + stackElts[4] : "") ;
  }
  result += "] ";
}
		return result;
	}

	/**
	 * Returns a referenced copy of this node.
	 * This has the effect of increasing the reference count
	 * for the underlying MTBDD.
	 * <br>[ REFS: <i>result</i>, DEREFS: <i>none</i> ]
	 */
	public JDDNode copy()
	{
		if (DebugJDD.debugEnabled) {
			return DebugJDD.Copy(this);
		} else {
			JDDNode result = new JDDNode(ptr());
			JDD.Ref(result);
result.ShanePURPOSE = " Copy of JDDNode with ShaneID #"+ShaneID+", whose purpose was: {" + ShanePURPOSE + "}";
			return result;
		}
	}
}

//------------------------------------------------------------------------------
