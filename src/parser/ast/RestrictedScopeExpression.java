package parser.ast;

import param.BigRational;
import parser.EvaluateContext;
import parser.visitor.ASTVisitor;
import prism.PrismLangException;
import prism.PrismOutOfBoundsException;
import java.util.*;

// ADDED BY SHANE
/**
 * The purpose of this class is to represent a scoping mechanism, particularly for expressions which will involve accessing indexed-sets by
 * variable positions, so that in formulas or other expression you can specify some 'default' value for evaluation in the cases where
 * the values of variables used in the access expression is deemed to be out of range. Typical examples of defaults are 'true' or 'false' 
 * or numeric literals. The key is the need to specify a restriction expression, which specifies the criteria for inclusion or exclusion of
 * a possible value for a variable used in the access expressions.   E.g. for a restriction expression of "x > 5" where x's domain is 0 to 10,
 * the valuations where the restriction expression is satisfied will result in substitutions (into access expressions, e.g. mySet[x])
 * would generate explicit accesses to set[6], set[7], etc., but for cases where the restriction expression are not satisfied or less, the 
 * 'default' value will replace the entire RestrictedScopedExpression's placement in some bigger expression.
 * It is therefore similar to an ITE, but is evaluated at model-building time, rather than model-checking time, because it is used to build
 * separate versions of the Command with particular assignments of the variables used in the restriction expression (and the particular 
 * assignments become part of the guard of the Command.)
 *
 */
public class RestrictedScopeExpression extends Expression implements Comparable<Expression>
{	
																	

public static boolean DEBUG = true;
public static boolean DEBUG_VISITOR = true;

	private Expression underlyingExpression;		// The expression which would be present if no restriction rule is violated
	private Expression defaultExpression;			// The expression which would be present if any restriction rule is violated

	private List<Expression> restrictionExpressions;	// Expressions that may restrict the scope of validity of the access expression

	// Constructors
	
	public RestrictedScopeExpression()
	{
	}
	
	/** The parameter should be the Expression which is the normal expression to be included at the point this RestrictedScopeExpression appears in a command or formula. */
	public RestrictedScopeExpression(Expression underlyingExpr)
	{
		underlyingExpression = underlyingExpr;
		restrictionExpressions = new ArrayList<Expression>();
		defaultExpression = null;		// The parser will set it shortly, or else will fail the whole parse due to wrong syntax.
	}

	
	public void setUnderlyingExpression(Expression underlyingExpr)
	{
		this.underlyingExpression = underlyingExpr;
	}

	public Expression getUnderlyingExpression()
	{
		return underlyingExpression;
	}


	public void setDefaultExpression(Expression newDefaultExpr)
	{
		this.defaultExpression = newDefaultExpr;
	}

	public Expression getDefaultExpression()
	{
		return defaultExpression;
	}


	public void addRestrictionExpression(Expression restrExpr)
	{
		restrictionExpressions.add(restrExpr);
	}

	public void replaceRestrictionExpression(Expression oldVersion, Expression newVersion)
	{
		if (restrictionExpressions.contains(oldVersion)) {
			restrictionExpressions.remove(oldVersion);
			restrictionExpressions.add(newVersion);
		}
	}

	public List<Expression> getRestrictionExpressions()
	{
		return (List<Expression>) new ArrayList<Expression>(restrictionExpressions);		// Give a copy, not our actual list.
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
	

	/**
	 * Evaluate this expression, return result 
	 * - which will mean the value of the specified index within the named indexed set (if correctly specified)
	 * Note: assumes that type checking has been done already.
	 */
	// Copied from ExpressionVar, which is what ExpressionIdent usually get converted to, by FindAllVars

// SHANE NOTE: This method will be invoked, at ****simulation time**** .
// but SHANE wonders if this will run during ModelChecking time?? (It probably shouldn't, because it should have been replaced by then with specific instantiations of the underlying expression.)
	@Override
	public Object evaluate(EvaluateContext ec) throws PrismLangException
	{
System.out.println("RestrictedScopeExpression.evaluate(EvaluateContext) has been called.");
throw new PrismLangException("Evaluation of a RestrictedScopeExpression is NOT YET IMPLEMENTED.");

//		return underlyingExpression.evaluate(ec);		// Possibly sufficient for now; but likely will need to code up the more complex semantics of checking all restrictions, otherwise, return the default's evaluation.
		// return defaultExpression.evaluate(ec);

	}

	@Override
	public BigRational evaluateExact(EvaluateContext ec) throws PrismLangException
        {
System.out.println("RestrictedScopeExpression.evaluateExact(EvaluateContext) has been called.");
throw new PrismLangException("Evaluation of a RestrictedScopeExpression is NOT YET IMPLEMENTED.");

//		return underlyingExpression.evaluateExact(ec);		// Possibly sufficient for now; but likely will need to code up the more complex semantics of checking all restrictions, otherwise, return the default's evaluation.
		// return defaultExpression.evaluateExact(ec);
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
if (DEBUG_VISITOR) System.out.println("The " + v.getClass().getName() + " visitor has invoked accept() in RestrictedScopeExpression on this instance: " + toString());
		return v.visit(this);
	}
	
	/**
	 * Convert to string.
	 */
	@Override
	public String toString()
	{
		boolean shownRestrAlready = false;
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		sb.append(underlyingExpression.toString());
		sb.append(" restrict ");
		for (Expression curRestr : restrictionExpressions)
		{
			if (shownRestrAlready) sb.append(", ");
			sb.append(curRestr);
			shownRestrAlready = true;
		}
		sb.append(" default ");
		sb.append(defaultExpression);
		return sb.toString();
	}

	/**
	 * Perform a deep copy. 
	 */
//SHANE Believes he has updated this correctly (from the parent version)
	@Override
	public Expression deepCopy()
	{
		RestrictedScopeExpression copiedExpr = new RestrictedScopeExpression(underlyingExpression.deepCopy());
		for (Expression curRestr : restrictionExpressions)
		{
			copiedExpr.addRestrictionExpression(curRestr.deepCopy());
		}
		copiedExpr.setDefaultExpression(defaultExpression.deepCopy());
		return copiedExpr;
	}

	// Method required for Comparable - Not sure if really needed.
	/** Simply uses the textual representation to form an order based on normal string ordering.
         */
	public int compareTo(Expression other)
	{
		if (other != null)
		  return this.toString().compareTo(other.toString());
		else
		  return -1;
	}
}

//------------------------------------------------------------------------------
