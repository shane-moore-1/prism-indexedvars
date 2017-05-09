package parser.ast;

import parser.EvaluateContext;
import parser.visitor.ASTVisitor;
import prism.PrismLangException;

/**
 * Represents an indexed identifier (i.e. an array, being accessed by an index) used as an expression, e.g. an element position of an Indexed Set is being given as the thing containing a value to be assigned during an Update (to another variable) 
 * It extends ExpressionIdent because it is meant to arise only in places where ExpressionIdent things would generally appear. 
 * It can occur both as a target of an update, or as an element in an expression (such as one specifying the way to calculate the value to be assigned to the target) 
 */
public class ExpressionIndexedSetAccess extends ExpressionIdent {	
																	
//	String name; <<-- inherited, no need to redeclare;
	Expression indexExpression;			// The expression which specifies (evaluates to) an index
	
	// Constructors
	
	public ExpressionIndexedSetAccess()
	{
	}
	
	/** The parameter should be the name (only) of the indexed-set being referenced. */
	public ExpressionIndexedSetAccess(String n)
	{
		name = n;
		indexExpression = null;
	}

	/** The parameters should be the name of the indexed-set being referenced, and an expression stating which index to access. */
	// Used by the deepCopy method only
	public ExpressionIndexedSetAccess(String n, Expression indexExpr)
	{
		name = n;
		indexExpression = indexExpr;
	}
	
	// Set methods
	
	public void setName(String n)
	{
Exception e = new Exception("CALLED ExpressionIndexedSetAccess.setName with parameter " + n + "(name was " + name + " when called)");
e.printStackTrace(System.out);
		name = n;
	}

	// Get methods
	
	/** Returns the name of the IndexedSet that this is going to access an element of. You can't know which element without
	    evaluating the indexExpression, which can only happen at run time. */
	public String getName()
	{
		return name;	// Don't include these: + "[" + indexExpression + "]";
	}

	public void setIndexExpression(Expression indexExpr)
	{
System.out.println("For object: " + hashCode() + " the index expression has been set to: " + indexExpr);
Exception e = new Exception(""); e.printStackTrace(System.out);
		this.indexExpression = indexExpr;
	}

	public Expression getIndexExpression()
	{
System.out.println("Retrieving IndexExpression for object " + hashCode());
		return indexExpression;
	}
	
	// Methods required for Expression ancestor class:
	
	/**
	 * Is this expression constant?
	 */
	@Override
	public boolean isConstant()
	{
		// Don't know - err on the side of caution
		return false;
	}

	@Override
	public boolean isProposition()
	{
		// Don't know - err on the side of caution
		return false;
	}
	
	/** Returns true, because this type of expression of an identifier, is for accessing an indexed variable. */
	@Override
	public boolean isIndexedVariable()
	{
		return true;
	}

	/**
	 * Evaluate this expression, return result 
	 * - which will mean the value of the specified index within the named indexed set (if correctly specified)
	 * Note: assumes that type checking has been done already.
	 */
	// Copied from ExpressionVar, which is what ExpressionIdent usually get converted to, by FindAllVars
	@Override
	public Object evaluate(EvaluateContext ec) throws PrismLangException
	{
		// This should never be called (that is, the version in ExpressionIdent, from where this was copied.)
		// The ExpressionIdent should have been converted to an ExpressionVar (by parser.visitor.FindAllVars.visit(ExprIdent) )
		// or ExpressionConstant (by parser.visitor.FindAllConstants)/...
		throw new PrismLangException("Could not evaluate indexed identifier - not implemented yet", this);
		
		// THE ABOVE should probably be replaced; unless we NEVER want to evaluate this type of object (because perhaps it is meant to be converted by a visitor)
	}

	@Override
	public boolean returnsSingleValue()
	{
		// Don't know - err on the side of caution
		return false;
	}

	// Methods required for ASTElement:
	
	/**
	 * Visitor method.
	 */
	@Override
	public Object accept(ASTVisitor v) throws PrismLangException
	{
System.out.println("In ExprIndSetAcc.accept(Visitor [" + v.getClass().getName()+"]) - about to call v.visit() for this visitor.");
		return v.visit(this);
	}
	
	/**
	 * Convert to string.
	 */
	@Override
	public String toString()
	{
		return name + "[" + indexExpression + "]";
	}

	/**
	 * Perform a deep copy. SHANE Believes he has updated this correctly (from the parent version)
	 */
	@Override
	public Expression deepCopy()
	{
		ExpressionIndexedSetAccess expr = new ExpressionIndexedSetAccess(name,indexExpression.deepCopy());
		expr.setType(type);
		expr.setPosition(this);
		return expr;
	}
}

//------------------------------------------------------------------------------
