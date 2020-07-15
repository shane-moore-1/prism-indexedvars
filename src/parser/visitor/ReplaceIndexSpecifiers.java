package parser.visitor;

import java.util.Vector;

import parser.ast.*;
import prism.PrismLangException;

import parser.Values;
/**
 * Class which searches for IndexedSetAccess expressions, and will replace the variables that occur in the index specifiers
 * (the desired index to access), with the value as given in the constructor's set of substitutions.
 * 
 * @author Shane Moore
 *
 */
public class ReplaceIndexSpecifiers extends ASTTraverseModify {

  public static boolean DEBUG = true;// && ASTTraverseModify.DEBUG_SHOW_ENABLED;

	// Constants that have been defined, and can be used in specifying the size of the IndexedSet
	private Values substitutions;

	public ReplaceIndexSpecifiers(Values substitutions)
	{
		this.substitutions = substitutions;
	}
	
	
	@Override
	public Object visit(ExpressionIndexedSetAccess e) throws PrismLangException
	{
if (DEBUG) {
   System.out.println("<ReplaceIndSpec>\nThe ReplaceIndexSpecifiers.visit(EISA) method has been invoked for: " + e);
   System.out.println("with the following substitutions: [" + substitutions + "]");
}
		ExpressionIndexedSetAccess copyToReturn =  (ExpressionIndexedSetAccess) e.deepCopy();  //<-- Did not work, partic for RHS of any update.
//WORKED for RHS of Update: But does it break other things like LHS of update or Guards
//		ExpressionIndexedSetAccess copyToReturn = e;// (ExpressionIndexedSetAccess) e.deepCopy();  <-- Did not work, partic for RHS of any update.
System.out.println("The Index Access Expression is: " + copyToReturn.getIndexExpression() + "  and its type is " + copyToReturn.getIndexExpression().getClass().getName());
		copyToReturn.setIndexExpression( (Expression) copyToReturn.getIndexExpression().evaluatePartially(null,substitutions));		// Apply substitutions to the access expression
System.out.println("Returning: " + copyToReturn);
		// But that may not cater for indexed-set access expressions inside the access expression (i.e. recursive)
// OLD, won't work:	copyToReturn = (ExpressionIndexedSetAccess) copyToReturn.evaluatePartially(null,substitutions);
// BECAUSE the evaluatePartially is done by the visitor called EvaluatePartially, which doesn't know how to work for an ExpressionIndexedSetAccess.

if (DEBUG) {
   System.out.println("</ReplaceIndSpec>\n");
}

		return copyToReturn;
	}
}
