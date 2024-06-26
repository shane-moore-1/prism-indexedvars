package parser.visitor;

import java.util.Vector;

import parser.ast.Module;
import parser.ast.*;
import prism.PrismLangException;

/**
 * Class which searches for declarations whose type is an indexed set, and converts them into individual declarations
 * of the underlying type but with names that indicate their index position.
 * 
 * CURRENTLY: only expressions without using constants can be used for specifying the size. 
 * @author Shane Moore
 *
 */
public class ConvertIndexedSetDeclarations extends ASTTraverseModify {

  public static boolean DEBUG = false && ASTTraverseModify.DEBUG_SHOW_ENABLED;

	// Constants that have been defined, and can be used in specifying the size of the IndexedSet
	private ConstantList constants;
	private ModulesFile currentModuleFile = null;
	private Module currentModule = null;

	public ConvertIndexedSetDeclarations(ConstantList knownConstants)
	{
		this.constants = knownConstants;
	}
	
	@Override
	public void visitPre(ModulesFile e) throws PrismLangException
	{
		// Register the fact we are entering the sub-tree that specifies a model
		currentModuleFile = e;
	}

	@Override
	public void visitPost(ModulesFile e) throws PrismLangException
	{
		// Register the fact we are leaving from the sub-tree containing a model
		currentModuleFile = null;
	}
	
	@Override
	public void visitPre(Module e) throws PrismLangException
	{
		// Note what module it is that we are now dealing with.
		currentModule = e;
	}
	
	@Override 
	public void visitPost(Module e) throws PrismLangException
	{
		// Note that we are no longer in a module sub-tree.
		currentModule = null;
	}
	

	/** If a Declaration is encountered, and it is for a variable whose type is an Indexed Set, then
	 * this method will insert into the ModulesFile, or the Module, in which the declaration occurs,
	 * a set of declarations for some number of individual variables of the element type.
	 * @return for Indexed Set declarations, it returns the Declaration of the first element; otherwise it returns the
	 * unaltered Declaration (of a non-indexed set type).
	 */
	// May be wrong on how it does the following (ACTUALLY, I THINK I HAVE RESOLVED THESE NOW, AT LEAST FOR NON-COPYABLE MODULES):
	// - Perhaps should delete the incoming node (because keeping it may cause issues in other things called by tidyUp, like checkVarNames).
	// - If we do delete the incoming node, should we be replacing it with a whole tree of other nodes
	//    because currently the newly made objects are NOT LINKED INTO the AST structure.
	// - In either case (making new subtree, or not), we may not be sensibly able to cope with re-starts of a parsed ModuleFile??
	@Override
	public Object visit(Declaration e) throws PrismLangException
	{
if (DEBUG) {
   System.out.println("<VISIT_DECL>\nConvertISDec_visit(Decl) called for this declaration: " + e);
   if (e.getDeclType() instanceof DeclTypeIndexedSet)
     System.out.println(" It  IS  a DeclTypeIndexedSet - so we will process it further...\n");
   else
     System.out.println(" It IS NOT a DeclTypeIndexedSet - so returning it unaltered.");
}
		// SHANE WAS WONDERING WHETHER I SHOULD SIMPLY ALTER THE Module.addDeclaration() method to do this straight out!
		// but then on later reflection, he thinks no - otherwise it may need to be done both in a global and in a local context.
		Declaration returnVal = e;		// We would return the same node, unless it was the Declaration of an indexed set.
		
		// See if the Type of this Declaration, is DeclTypeIndexedSet
		if (e.getDeclType() instanceof DeclTypeIndexedSet)
		{
		
			String indexedSetName = e.getName();		// Obtain the name used for this set.
			DeclTypeIndexedSet dtInfo = (DeclTypeIndexedSet) e.getDeclType();

			// Check that the name is not also being used for some other thing within the current module
			if (currentModuleFile.isIdentUsed(indexedSetName))
				throw new PrismLangException("name given for indexed set is already being used for another identifier: " + indexedSetName);
			
			Expression size = dtInfo.getSize();
			
			size = (Expression) size.expandConstants(constants);	// We store the returned value, as the visitor will replace any constants.
			int count = size.evaluateInt();
			DeclarationType elementsType =  dtInfo.getElementsType();
			
			if (currentModule != null)		// It was a local declaration within a specific module:
			{
				int posToInsert = currentModule.getDeclPosInArrayList(e);

				// Using that count, we will now create that many declarations in the module
				for (int i = 0; i < count; i++)
				{
					Declaration d = new Declaration(indexedSetName + "[" + i + "]", elementsType);
					// Preserve any deferral settings to the new variables.
					d.setDeferCreateDD(e.getDeferCreateDDRound());
					d.setIsPartOfIndexedVar();

					// If there was a specified value for initial (all vars in set get same initial), set it:
					d.setStart(e.getStart() );		// Later, may want to account for index-specific values

					if (i == 0) {
						returnVal = d;		// to replace original Declaration upon returning
						currentModule.addIndexedSetDecl(e);	// Needed for SemanticCheck visitor
					} else {
						currentModule.insertDeclarationAt(d,posToInsert+i);	// It will be additional to what was there beforehand
					}

				}

				// delete e so that the State class and the VarList class do not try to create/access it.
// Not needed because old e gets replaced by returnVal anyway
//				currentModule.removeDeclaration(e);

				// Note it instead in the helper, so it can be used for bounds checking when evaluating access expressions.
				Helper.noteIndexedSetDeclaration(e);
			}
			else if (currentModuleFile != null) {						
if (DEBUG) System.out.println(" It is from outside of any module. (No further debug outputs about it though)");
				// Using that count, we will now create that many declarations at the global level:	
				for (int i = 0; i < count; i++)
				{
					Declaration d = new Declaration(indexedSetName + "[" + i + "]", elementsType);
					if (i == 0) returnVal = d;		// to replace original Declaration
					d.setIsPartOfIndexedVar();		// to enable bounds checking at evaluationTime

					// If there was a specified value for initial (all vars in set get same initial), set it:
					d.setStart(e.getStart() );		// Later, may want to account for index-specific values

					currentModuleFile.addGlobal(d);
					currentModuleFile.addIndexedSetName(indexedSetName);	// MAYBE Needed for SemanticCheck visitor
						// But above line may not be needed any more for Semantic Check
				}

				// delete e so that the State class and the VarList class do not try to create/access it.
				currentModuleFile.removeGlobal(e);
				// Note it instead in the helper, so it can be used for bounds checking when evaluating access expressions.
				Helper.noteIndexedSetDeclaration(e);
			}
			else
				throw new PrismLangException("Apparently found a declaration for an IndexedSet, but not whilst in an ModulesFile, nor a Module");

		}
if (DEBUG) System.out.println("ConvISDec.visit(Decl) will return this value : " + returnVal+ "\n</VISIT_DECL>\n");

		return returnVal;
	}
}
