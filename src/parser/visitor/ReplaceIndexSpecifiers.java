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
}
		ExpressionIndexedSetAccess copyToReturn = (ExpressionIndexedSetAccess) e.deepCopy();
		copyToReturn = (ExpressionIndexedSetAccess) copyToReturn.evaluatePartially(null,substitutions);

if (DEBUG) {
   System.out.println("</ReplaceIndSpec>\n");
}

		return copyToReturn;
	}
}
