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

import java.util.ArrayList;

import parser.*;
import parser.type.*;
import parser.visitor.*;
import prism.PrismLangException;

/**
 * Class to store a single update command, which consists one or more update-elements,
 * each of which specifies a variable identifier whose value is to be updated according
 * to some expressions.
 * For example:  (s'=1) &amp; (x'=x+1) consists of two update-elements, the first which would 
 * update the 's' variable to value 1, and the second which would cause the 'x' variable to be incremented.
 */
public class Update extends ASTElement
{
public static boolean DEBUG_MSG = false;
	public	// SHANE feels it should eventually be private
	class ElementOfUpdate {
		String var;						// The NAME only of a variable requiring update (its ExpressionIdent is below...) 
		// SHANE NOTES: The name of the variable which is to be updated, in the case of accessing an indexed
		// set, will not be known during FindAllValues, because it needs to be determined at Step-Time. (Run-Time, based on the state).
		Expression expr;			// The expression that specifies what run-time calculation to evaluate to find the new value for var.
		Type varType;					// The type that the var being updated is declared to be (and what updateExpr should evaluate to)
		ExpressionIdent varIdent;		// The ExpressionIdent object that this UpdateElement is regarding (its NAME was the above 'var').
										// This is to just to provide positional info.
		Integer index;					// The index in the model to which it belongs - set before any steps executed..
				// the above Integer is CURRENTLY set during FindAllVars.visitPost(Update e); it is position in the ModulesFile list of variables.

		public String toString()
		{
			return varIdent.toString() + " [type = " +varType + ", expr = " + expr + ", index = " + index + "]";
		}
	}
	
	
	// Lists of variable/expression pairs (and types)
	//private ArrayList<String> vars;				// vars[i] will be the NAME of a variable requiring update 
	//private ArrayList<Expression> exprs;		// exprs[i] will be the way to calculate the new value for vars[i] to be assigned when update() is called.
	//private ArrayList<Type> types;				// types[i] is the type that the variable at pos i expects.
	// We also store an ExpressionIdent to match each variable.
	// This is to just to provide positional info. (Originally; but SHANE may need to rely on it for further things)
	//private ArrayList<ExpressionIdent> varIdents;  //The ExpressionIdent objects, for which the vars[i] has the name only.
													// For ExpressionIndexedSetAccess This should contain the means of knowing which index to alter.   
	// The indices of each variable in the model to which it belongs - set before any steps executed.
	//private ArrayList<Integer> indices;			// CURRENTLY set during FindAllVars.visitPost(Update e); it is position in the ModulesFile list of variables.

	// These elements will all be effected during the update() method.
	private ArrayList<ElementOfUpdate> elements;	

	// Parent Updates object
	private Updates parent;

	/**
	 * Create an empty update.
	 */
	public Update()
	{
		elements = new ArrayList<ElementOfUpdate>();
/*		vars = new ArrayList<String>();
		exprs = new ArrayList<Expression>();
		types = new ArrayList<Type>();
		varIdents = new ArrayList<ExpressionIdent>();
		indices = new ArrayList<Integer>();
*/
	}

	// Set methods

	/**
	 * Add another element to this Update.
	 * @param v - The variable to be updated (either ordinary variable, or an element of an indexed-set)
	 * @param e - The expression describing how to calculate the value to be set as the new value for the v variable.
	 */
	public void addElement(ExpressionIdent v, Expression e)
	{
		ElementOfUpdate ue = new ElementOfUpdate();
		ue.varIdent = v;			// The variable to be updated.
		ue.var = v.getName();			// Just its name
		ue.expr = e;				// The expression saying how to calculate the new value for it.
		ue.varType = null;			// The type is currently unknown.
		ue.index = -1;				// Index is currently unknown. Set by the FindAllVars.visitPost(Update e)

		elements.add(ue);		// Store in memory for this Update.
System.out.println("Added update element for varIdent: " + v + " classtype: " + v.getClass().getName() +"\n  to be set to result of calculating: " + e);
	}

	/**
	 * Change the Identifier that this update will update, for the specified element of the update.
	 * @param i - update element's position within the update command
	 * @param v - the Identifier to change
	 */
	public void setVar(int i, ExpressionIdent v)
	{
		ElementOfUpdate ue = elements.get(i);
		ue.var = v.getName();
		ue.varIdent = v;
	}

	/**
	 * Change the expression specifying how to calculate the updated value for the specified element
	 * @param i - update element's position within the update command
	 * @param e - the new way of calculating the resultant value to assign
	 */
	public void setExpression(int i, Expression e)
	{
		ElementOfUpdate ue = elements.get(i);
		ue.expr = e;
	}

	/**
	 * Change the type noted for an update element.
	 * @param i - update element's position within the update command
	 * @param t - the type of value needed for assigning to the relevant variable.
	 */
	public void setType(int i, Type t)
	{
//Exception e = new Exception(); e.printStackTrace(System.out);
System.out.println("in Update.setType(int,Type) for \'" + this.toString() + "\', with i=" + i + " and type: " + t);
		ElementOfUpdate ue = elements.get(i);
		ue.varType = t;
	}

	public void setVarIndex(int i, int index)
	{
		ElementOfUpdate ue = elements.get(i);
		ue.index = index;
	}

	public void setParent(Updates u)
	{
		parent = u;
	}

	// Get methods

	public int getNumElements()
	{
		return elements.size();
	}

	public ElementOfUpdate getElement(int i)		//SHANE added, should probably hide again
	{
		if (i >= 0 && i < elements.size())
			return elements.get(i);
		else
			return null;
	}

	/** Get the name (only) of an identifier (the variable) that is to be mutated by this update.
	 *  For non-indexed set variables, this is just the name of the variable.
	 *  If the identifier is actually an element within an indexed set, however, then there is 
	 *  CURRENTLY A PROBLEM BECAUSE IT NEEDS TO KNOW EXACTLY WHICH ONE!! 
	 * @param i The index of the update element for which to find out the name.
	 * @return
	 */
	public String getVar(int i)
	{
		if (getTypeForElement(i) instanceof TypeIndexedSet)
		{
System.out.println("in getVar() for " + elements.get(i).var + " / " + elements.get(i).varIdent + " - what to return for getType?");
System.out.println("The current getType is: " + getTypeForElement(i) + ", but returning null");
			return null;
		} 
		else
		return elements.get(i).var;
	}

	public Expression getExpression(int i)
	{
		return elements.get(i).expr;
	}

	public Type getTypeForElement(int i)
	{
		return elements.get(i).varType;
	}

	public ExpressionIdent getVarIdent(int i)
	{
		return elements.get(i).varIdent;
	}

	/**
	 * Returns the index in the owning module's list of variables (which contains globals and all modules' variables)
	 * @param i Specifies which element of this Update, whose target variable is desired to be found.
	 * @return
	 */
	public int getVarIndex(int i)
	{
		return elements.get(i).index;
	}

	public Updates getParent()
	{
		return parent;
	}

	/**
	 * Execute this update, based on variable values specified as a Values object,
	 * returning the result as a new Values object copied from the existing one.
	 * Values of any constants should also be provided.
	 * @param constantValues Values for constants
	 * @param oldValues Variable values in current state
	 */
	public Values update(Values constantValues, Values oldValues) throws PrismLangException
	{
		int i, n;
		Values res;
		res = new Values(oldValues);
		n = elements.size();
		for (i = 0; i < n; i++) {
			res.setValue(getVar(i), getExpression(i).evaluate(constantValues, oldValues));
		}
		return res;
	}

	/**
	 * Execute this update, based on variable values specified as a Values object,
	 * applying changes in variables to a second Values object. 
	 * Values of any constants should also be provided.
	 * @param constantValues Values for constants
	 * @param oldValues Variable values in current state
	 * @param newValues Values object to apply changes to
	 */
	public void update(Values constantValues, Values oldValues, Values newValues) throws PrismLangException
	{
		int i, n;
		n = elements.size();
		for (i = 0; i < n; i++) {
			newValues.setValue(getVar(i), getExpression(i).evaluate(constantValues, oldValues));
		}
	}

	/**
	 * Execute this update, based on variable values specified as a State object,
	 * returning the result as a new State object copied from the existing one.
	 * It is assumed that any constants have already been defined.
	 * @param oldState Variable values in current state
	 */
	public State update(State oldState) throws PrismLangException
	{
		int i, n;
		State res;
		res = new State(oldState);
		n = elements.size();
		for (i = 0; i < n; i++) {
			res.setValue(getVarIndex(i), getExpression(i).evaluate(oldState));
		}
		return res;
	}

	/**
	 * Execute this update, based on variable values specified as a State object.
	 * Apply changes in variables to a provided copy of the State object.
	 * (i.e. oldState and newState should be equal when passed in.) 
	 * It is assumed that any constants have already been defined.
	 * @param oldState Variable values in current state
	 * @param newState State object to apply changes to
	 */
	// MODIFIED BY SHANE to do run-time evaluation of index-access expressions for indexed sets.
	public void update(State oldState, State newState) throws PrismLangException
	{
		int i, n, indexOfVarToUpdate;
		
		n = elements.size();
		for (i = 0; i < n; i++) {
			if (getTypeForElement(i) instanceof TypeIndexedSet)
			{
				// we cannot rely on getVarIndex(i), because that would be the IndexedSet itself.
				// so perform runtime evaluation of the expression specified in the sourcecode modules file.
				ExpressionIndexedSetAccess eisa = (ExpressionIndexedSetAccess) getVarIdent(i); // HMM? Should it be getVarIndex instead? (Says me after 2 weeks away from the code, while it was unfinished before the break)
				Object evaluatedIndex = eisa.getIndexExpression().evaluate(oldState);
				if (evaluatedIndex instanceof Integer)
				{
					// Construct the hoped-for name of the specific variable to be updated.
					String varNameToUpdate = eisa.getName() + "[" + evaluatedIndex + "]";
					// Check it exists. If it doesn't, then it is either mis-use of IndexedSet notation
					// or else it is outside the bounds of the declared number of elements.
					indexOfVarToUpdate = parent.getParent().getParent().getParent().getVarIndex(varNameToUpdate);
					
					if (indexOfVarToUpdate == -1)		// It wasn't found as a valid variable, index was obviously wrong.
						throw new PrismLangException("Attempt to access undefined element of IndexedSet: " + varNameToUpdate, getExpression(i));
				}
				else
					throw new PrismLangException("Attempt to access IndexedSet using non-integer index value: " + evaluatedIndex, getExpression(i));
			}
			else {
				// Update of an non-indexed variable:  
				indexOfVarToUpdate = i;
			}
			
			// Evaluate the RH expression part, based on the 'old' state, and assign this to the target variable.
			newState.setValue(getVarIndex(indexOfVarToUpdate), getExpression(i).evaluate(oldState));
		}
	}

	/**
	 * Execute this update, based on variable values specified as a State object.
	 * Apply changes in variables to a provided copy of the State object.
	 * (i.e. oldState and newState should be equal when passed in.) 
	 * Both State objects represent only a subset of the total set of variables,
	 * with this subset being defined by the mapping varMap.
	 * Only variables in this subset are updated.
	 * But if doing so requires old values for variables outside the subset, this will cause an exception. 
	 * It is assumed that any constants have already been defined.
	 * @param oldState Variable values in current state
	 * @param newState State object to apply changes to
	 * @param varMap A mapping from indices (over all variables) to the subset (-1 if not in subset). 
	 */
	public void updatePartially(State oldState, State newState, int[] varMap) throws PrismLangException
	{
		int i, j, n;
		n = elements.size();
		for (i = 0; i < n; i++) {
			j = varMap[getVarIndex(i)];
			if (j != -1) {
				newState.setValue(j, getExpression(i).evaluate(new EvaluateContextSubstate(oldState, varMap)));
			}
		}
	}

	/**
	 * Check whether this update (from a particular state) would cause any errors, mainly variable overflows.
	 * Variable ranges are specified in the passed in VarList.
	 * Throws an exception if such an error occurs.
	 */
	public State checkUpdate(State oldState, VarList varList) throws PrismLangException
	{
		int i, n, valNew;
		State res;
		res = new State(oldState);
		n = elements.size();
		for (i = 0; i < n; i++) {
			valNew = varList.encodeToInt(i, getExpression(i).evaluate(oldState));
			if (valNew < varList.getLow(i) || valNew > varList.getHigh(i))
				throw new PrismLangException("Value of variable " + getVar(i) + " overflows", getExpression(i));
		}
		return res;
	}

	// Methods required for ASTElement:

	/**
	 * Visitor method.
	 */
	public Object accept(ASTVisitor v) throws PrismLangException
	{
if (DEBUG_MSG) System.out.println("\n\nin Update.accept(), for \'" + toString() + "\', about to call " + v.getClass().getName() +".visit(Update)...");
//Object o = v.visit(this);
		return v.visit(this);
//if (DEBUG_MSG) System.out.println("in Update.accept(), for \'" + toString() + "\', returned from call of " + v.getClass().getName() +".visit(Update)...");
//return o;
	}

	/**
	 * Convert to string.
	 */
	public String toString()
	{
		int i, n;
		String s = "";
		n = elements.size();
		// normal case
		if (n > 0) {
			for (i = 0; i < n - 1; i++) {
// ORIG:			s = s + "(" + getVar(i) + "'=" + getExpression(i) + ") & ";
/*SHANE*/				s = s + "(" + getVarIdent(i) + "'=" + getExpression(i) + ") & ";
			}
			s = s + "(" + getVar(n - 1) + "'=" + getExpression(n - 1) + ")";
		}
		// special (empty) case
		else {
			s = "true";
		}

		return s;
	}

	/**
	 * Perform a deep copy.
	 */
	public ASTElement deepCopy()
	{
		int i, n;
		Update ret = new Update();
		n = getNumElements();
		for (i = 0; i < n; i++) {
			ret.addElement((ExpressionIdent) getVarIdent(i).deepCopy(), getExpression(i).deepCopy());
			ret.setType(i, getTypeForElement(i));
			ret.setVarIndex(i, getVarIndex(i));
		}
		ret.setPosition(this);
		return ret;
	}
}

//------------------------------------------------------------------------------
