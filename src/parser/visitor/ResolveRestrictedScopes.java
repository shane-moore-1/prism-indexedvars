package parser.visitor;

import java.util.Vector;

import parser.ast.*;
import prism.PrismLangException;

import parser.Values;
import parser.VarList;

/**
 * Class which searches for RestrictedScopeExpressions, and will perform an on-the-spot evaluation of the restriction 
 * expressions using the suppled Values for substitution, to determine how to resolve the Expression - either the underlying
 * expression ANDed with the substitution values, or else the default by itself.
 * It will also ensure that the substitutions are added as restrictions to any IndexedSetAccessExpressions that occur WITHIN the Scope,
 * but those will be applied/evaluated in a later stage.
 * 
 * @author Shane Moore
 *
 */
public class ResolveRestrictedScopes extends ASTTraverseModify {

  public static boolean DEBUG = true;// && ASTTraverseModify.DEBUG_SHOW_ENABLED;

	// Values (including constants) that have been defined, and can be used in evaluating expressions
	private Values substitutions;

	private Values constants;

	private VarList varList;

	public ResolveRestrictedScopes(Values constants, VarList theVarList)
	{
		this.constants = constants;
		this.varList = theVarList;
	}

	/** Set the Values to be available during the visit method, to use for evaluating (with the constants given at Constructor time). */
	public void setValuesForResolution(Values substitutions)
	{
		this.substitutions = substitutions;
	}
	
	
	@Override
	public Object visit(RestrictedScopeExpression rse) throws PrismLangException
	{
if (DEBUG) {
   System.out.println("<ResolveRestrictedScope>\nThe ResolveRestrictedScopes.visit(RSE) method has been invoked for: " + rse + "\nusing the following values: " + substitutions +" and " + constants);
}
		ExpressionUnaryOp versionToReturn = null;
		Expression resultExpr = null;

		boolean nonCompliant = false;
		for (Expression restriction : rse.getRestrictionExpressions())
		{
if (DEBUG) {
   System.out.println("About to check if the substitution values cause the following restriction to fail or not: " + restriction);
} 
			boolean outcome = restriction.evaluateBoolean(constants,substitutions);
if (DEBUG) {
   System.out.println(" Outcome of evaluation: " + outcome);
}
			if (outcome == false)
			   nonCompliant = true;
		}

if (DEBUG) {
   if (nonCompliant) System.out.println("Because at least one restriction FAILED, the DEFAULT expression will be returned: " + rse.getDefaultExpression());
   else System.out.println("Because NO restrictions failed, the UNDERLYING expression will be returned, ANDed with the substitution values.");
}
		// We generate a newly parenthesized expression, so that the visit method below has something specific to commence with.
		if (nonCompliant) {
			versionToReturn = new ExpressionUnaryOp(ExpressionUnaryOp.PARENTH,rse.getDefaultExpression());
		} else {
// Not Doing,			// Construct the explicit value setting...
// May not be needed			Expression extraGuards

			// And combine that with the underlying expression...
//			versionToReturn = new ExpressionBinaryOp(
//				new ExpressionBinary
//			);
			versionToReturn = new ExpressionUnaryOp(ExpressionUnaryOp.PARENTH,rse.getUnderlyingExpression());
		}

		// However, before returning it, we must carry-through the restrictions of the current substitution into things within the restricted scope.
		InsertRestrictions ir = new InsertRestrictions();

		// Finally, insert the substitution values before the above bit of the final expression.
System.out.println(" <SetSubstitutionsPrefix within='ResolveRestrictedScopes.visit()'>");

		// Generate the prefix part which sets specific values for the variables that restrict the scope:
		Expression prefix = null;

System.out.println("There are " + substitutions.getNumValues() + " substitutions to prepend...");
		for (int i = 0; i < substitutions.getNumValues(); i++)
		{
			String varNameToUse = substitutions.getName(i);
			int valToUse = (int) ((Integer)substitutions.getValue(i));
			int indexInVarList = varList.getIndex(varNameToUse);

			ExpressionVar theVar = 	new ExpressionVar( varNameToUse, varList.getType(indexInVarList));
			theVar.setIndex(indexInVarList);		// SHANE HOPES THAT IS CORRECT - otherwise, have to find out appropriate value to use.
//System.out.println("  for variable " + varNameToUse + ", will create ExprVar with type set to " + varList.getType(indexInVarList));
			ExpressionLiteral theVal = new ExpressionLiteral(varList.getType(indexInVarList),valToUse);

			Expression nextPart;
			nextPart = new ExpressionBinaryOp(ExpressionBinaryOp.EQ, theVar, theVal);
			if (i == 0)
					prefix = nextPart;
			else
			  prefix = new ExpressionBinaryOp(ExpressionBinaryOp.AND, nextPart, prefix);	// Pre-catenate, as conjunction
System.out.println("  Prepending: " + nextPart);
			ir.addRestriction(nextPart);		// Add as a restriction to be applied to expressions contained inside.
		}
		if (prefix != null) {	// It should not be null if there were substitutions made.
System.out.println("The 'prefix' will be: " + prefix);
System.out.println("Before visit is done, versionToReturn is: " + versionToReturn);
			// Ensure the restrictions are carried through into the version that is to be returned...
			versionToReturn = (ExpressionUnaryOp) ir.visit(versionToReturn);
System.out.println("After visit is done, versionToReturn is: " + versionToReturn);

			// Include the constraints on this restriction's applicability by pre-pending as guards the substitutions; wrap the whole thing in parentheses

			resultExpr = new ExpressionUnaryOp(ExpressionUnaryOp.PARENTH,
			    new ExpressionBinaryOp(ExpressionBinaryOp.AND,prefix, versionToReturn)
			);
System.out.println("The resultant expression to return will be: " +resultExpr);
		}
else System.out.println("The 'prefix' expression is null. WON'T MODIFY the thing being returned.");


System.out.println(" </SetSubstitutionsPrefix>");



if (DEBUG) {
   System.out.println("The ResolveRestrictedScopes.visit(RSE) method is concluding for: " + rse + "\n by returning: " + resultExpr);
   System.out.println("</ResolveRestrictedScope>\n");
}

		return resultExpr;
	}
}
