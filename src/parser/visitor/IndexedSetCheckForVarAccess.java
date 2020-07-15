//==============================================================================
//	
//	Copyright (c) 2002-
//	Authors:
//	* THIS FILE - Shane Moore
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

package parser.visitor;

import java.util.Vector;

import parser.ast.*;
import prism.PrismLangException;

/**
 * This visitor will check all commands' guards, and expressions within the updates of the command, for IndexedSets, and
 * if the usage involves a literal (or constant) to access a specific one, will leave it alone, but for all others will
 * generate a group of inidividual new commands, one for each possible value that the variables used could actually be,
 * It IS messy (inefficient for complex commands), but should work.
 */

public class IndexedSetCheckForVarAccess extends ASTTraverseModify
{
private static boolean DEBUG_SHOW_PREPOST = false && DEBUG_Update;
	@SuppressWarnings("unused")
	private ModulesFile modulesFile;

	public IndexedSetCheckForVarAccess(ModulesFile modulesFile)
	{
		setModulesFile(modulesFile);
	}

	public void setModulesFile(ModulesFile modulesFile)
	{
		this.modulesFile = modulesFile;
	}

	public void visitPre(Command e) throws PrismLangException
	{
if (DEBUG_SHOW_PREPOST) {
System.out.println("\nNOTE\nvisitPre(Command) has occurred for this command:\n"+e+"\n(Exiting)\n");
}
	}

	public void visitPost(Command e) throws PrismLangException
	{
if (DEBUG_SHOW_PREPOST) {
System.out.println("\nNOTE\nvisitPost(Command) has occurred for this command:\n"+e+"\n(Exiting)\n");
}
	}

	// We will check to see if the update access-expression 
	public void visitPost(Update e) throws PrismLangException
	{
		int i, n;
		String var;
		Vector<String> varsUsed = new Vector<String>();
if (DEBUG_Update) System.out.println("<MFSCAC_VisitPost_Upd>");
		// Check that no variables are set twice in the same update
		// Currently, could do this *before* constants are defined,
		// but one day we might need to worry about e.g. array indices...
// SHANE SAYS: That day has come! Thus some alterations are made to this code...
		n = e.getNumElements();
		for (i = 0; i < n; i++) {
if (DEBUG_Update) {	// Declared in ASTTraverse
	System.out.println("in ModulesFileSemanticCheckAfterConstants.visitPost(Update) for Update: " + e);
	System.out.println("  Considering update-element #" + (i + 1) + "/" + n);
}
			// Ensure it is not altering an Indexed Set's element.
			if (!(e.getVarIdent(i) instanceof ExpressionIndexedSetAccess)) {
				var = e.getVar(i);
				if (varsUsed.contains(var)) {
					throw new PrismLangException("Variable \"" + var + "\" is set twice in the same update", e.getVarIdent(i));
				}
				varsUsed.add(var);
			} else {
				// There's not much we can do at this time, about actual attempted alterations to elements 
				// of an indexed set, as the access-expression may require run-time evaluation.
				// And there is potential that two update elements of the same update try to alter the one same item
				// in a particular indexed set.
				// We could maybe issue a 'warning' onto mainLog.
				System.out.println("ISCFVA - Update element " + (i+1) + " of the following update modifies an indexed-set element: " + e);
				System.out.println("If more than one element of the same Update alters the same index, there may be indeterminable behavior"); 
if (DEBUG_Update) {
   System.out.println("  Nothing was checked by this Visitor, because it was targeting an indexed-set access.");
}
			}
if (DEBUG_Update) {
	System.out.println("  Finished considering update-element #" + (i + 1) + "/" + n);
}
		}
		varsUsed.clear();
if (DEBUG_Update) System.out.println("</MFSCAC_VisitPost_Upd>\n");
	}
}
