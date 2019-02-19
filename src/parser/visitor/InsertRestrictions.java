package parser.visitor;

import java.util.ArrayList;

import parser.ast.*;
import prism.PrismLangException;


/**
 * Class which is used by the ResolveRestrictedScopes visitor, to insert restriction expressions into and embedded IndexedSetAccess expressions;
 * this is so that any variables that might have restrictions, the restrictions will be preserved/applied to things inside the scope, but it allows
 * deferral of the determining whether or not they actually apply (to a later stage of the model build).
 * 
 * @author Shane Moore
 *
 */
public class InsertRestrictions extends ASTTraverseModify {

  public static boolean DEBUG = true;// && ASTTraverseModify.DEBUG_SHOW_ENABLED;

	// The restrictions to be applied.
	private ArrayList<Expression> restrictions;

	public InsertRestrictions()
	{
		this.restrictions = new ArrayList<Expression>();
	}
	
	public void addRestriction(Expression restrExpr)
	{
		if (restrExpr != null) restrictions.add(restrExpr);
	}
	
	@Override
	public Object visit(ExpressionIndexedSetAccess e) throws PrismLangException
	{
		ExpressionIndexedSetAccess returnVersion = (ExpressionIndexedSetAccess) e.deepCopy();
if (DEBUG) {
   System.out.println("<AppendRestriction>\nWill ensure restrictions are applied to the ExpressionIndexedSetAccess: " + e);
}

		for (Expression restrExpr : restrictions)
		{
if (DEBUG) System.out.println("\t"+restrExpr);
			returnVersion.addRestrictionExpression(restrExpr);
		}

if (DEBUG) {
   System.out.println("</AppendRestriction>\n");
}

		return returnVersion;
	}
}
