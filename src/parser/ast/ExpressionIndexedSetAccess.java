package parser.ast;

import parser.EvaluateContext;
import parser.visitor.ASTVisitor;
import prism.PrismLangException;
import prism.PrismOutOfBoundsException;
import java.util.Vector;

/**
 * Represents an indexed identifier (i.e. an array, being accessed by an index) used as an expression, e.g. an element position of an Indexed Set is being given as the thing containing a value to be assigned during an Update (to another variable) 
 * It extends ExpressionIdent because it is meant to arise only in places where ExpressionIdent things would generally appear. 
 * It can occur both as a target of an update, or as an element in an expression (such as one specifying the way to calculate the value to be assigned to the target) 
 */
public class ExpressionIndexedSetAccess extends ExpressionIdent {	
																	

public static boolean DEBUG = false;
public static boolean DEBUG_VISITOR = false;

//	String name; <<-- inherited, no need to redeclare;
	Expression indexExpression;			// The expression which specifies (evaluates to) an index

	private Vector<String> varIdents;		// A reference to the one provided during the FindAllVars visitor, so that
							// the 'index' of the relevant variable can be found during evaluate()

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
		this.indexExpression = indexExpr;
	}

	public Expression getIndexExpression()
	{
		return indexExpression;
	}

	// Messy (high coupling to other code), but necessary for run-time resolution during evaluate()
	/**
	 * This should be called during the FindAllVars visitor, to enable run-time resolution of an index (where it may be dynamically determined).
	 */
	public void setVarIdentsVector(Vector<String> original)
	{
		varIdents = original;
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

// SHANE NOTE: This method will be invoked, at ****simulation time****, if we have a guard (for example) where the index to access 
// is given by a variable (thus not known at model-construction time).
	@Override
	public Object evaluate(EvaluateContext ec) throws PrismLangException
	{
		String nameToFind;
		PrismLangException ple;			// possible exception could be thrown.


		Object idx = indexExpression.evaluate(ec);
		if (!(idx instanceof Integer))
		{
			ple = new PrismLangException("Incompatible value given in Indexed-Set Access expression. Must be an integer",this);
			throw ple;
		}

		// Convert to int
		int idxAsInt = ((Integer)idx);

		// Now to check the index corresponds to a valid index
		Declaration origDecl = Helper.getIndexedSetDeclaration(this.getName());

		if (origDecl == null)
		{
			ple = new PrismLangException("Not an indexed set",this);
			throw ple;
		}

		DeclTypeIndexedSet dtInfo = (DeclTypeIndexedSet) origDecl.getDeclType();
		if (dtInfo != null) {
			Expression size = dtInfo.getSize();
			int count = size.evaluateInt();

			if ((idxAsInt < 0) || (idxAsInt >= count)) {
				ple = new PrismOutOfBoundsException("Attempt to access invalid index of an indexed set: " + idxAsInt);
				throw ple;
			}

			nameToFind = this.getName() + "[" + idx.toString() + "]";

			int i = -1;
			if (varIdents != null)
				// Copied from FindAllVars.visit(ExpressionIdent):
				i = varIdents.indexOf(nameToFind);		// Index within the ModulesFile collated list of all variables
			if (i == -1) {
				ple = new PrismLangException("Could not find variable in memory: " + nameToFind);
				throw ple;
			}
			return ec.getVarValue(nameToFind,i);		// the first parameter is actually ignored, hence why i was needed.
		} else
			throw new PrismLangException("Unexpected Error in System, evaluating: " + this);
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
if (DEBUG_VISITOR) System.out.println("The " + v.getClass().getName() + " visitor has reached ExpressionIndexedSetAccess.access() for: " + toString());
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
	 * Perform a deep copy. 
	 */
//SHANE Believes he has updated this correctly (from the parent version)
	@Override
	public Expression deepCopy()
	{
		ExpressionIndexedSetAccess expr = new ExpressionIndexedSetAccess(name,indexExpression.deepCopy());
		expr.setType(type);
		expr.setPosition(this);
		expr.varIdents = this.varIdents;
		return expr;
	}
}

//------------------------------------------------------------------------------
