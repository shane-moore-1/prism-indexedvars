package parser.visitor;

import java.util.Vector;

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
	 */
	// May be wrong on how it does the following:
	// - Perhaps should delete the incoming node (because keeping it may cause issues in other things called by tidyUp, like checkVarNames).
	// - If we do delete the incoming node, should we be replacing it with a whole tree of other nodes
	//    because currently the newly made objects are NOT LINKED INTO the AST structure.
	// - In either case (making new subtree, or not), we may not be sensibly able to cope with re-starts of a parsed ModuleFile??
	// TO-DO: Resolve the above.
	@Override
	public Object visit(Declaration e) throws PrismLangException
	{
		// SHANE WAS WONDERING WHETHER I SHOULD SIMPLY ALTER THE Module.addDeclaration() method to do this straight out!
		// but then on later reflection, he thinks no - otherwise it may need to be done both in a global and in a local context.
		
		// See if the Type of this Declaration, is DeclTypeIndexedSet
		if (e.getDeclType() instanceof DeclTypeIndexedSet)
		{
		
			String indexedSetName = e.getName();		// Obtain the name used for this set.
			DeclTypeIndexedSet dtInfo = (DeclTypeIndexedSet) e.getDeclType();

			// Check that the name is not also being used for some other thing within the current module
			if (currentModuleFile.isIdentUsed(indexedSetName))
				throw new PrismLangException("name given for indexed set is already being used for another identifier: " + indexedSetName);
			
			Expression size = dtInfo.getSize();
			size.expandConstants(constants);	// Shane Hopes this will work! Otherwise, may need to insert:  size = (Expression) [the rest]
			int count = size.evaluateInt();
			
			DeclarationType elementsType =  dtInfo.getElementsType();
			
			if (currentModule != null)		// It was a local declaration within a specific module:
			{
				// Using that count, we will now create that many declarations in the module
				for (int i = 0; i < count; i++)
				{
					Declaration d = new Declaration(indexedSetName + "[" + i + "]", elementsType);
					d.setIsPartOfIndexedVar();
					currentModule.addDeclaration(d);
				}
				// delete e ?
			}
			else if (currentModuleFile != null) {						
				// Using that count, we will now create that many declarations at the global level:	
				for (int i = 0; i < count; i++)
				{
					Declaration d = new Declaration(indexedSetName + "[" + i + "]", elementsType);
					d.setIsPartOfIndexedVar();
					currentModuleFile.addGlobal(d);
				}
				// delete e ?
			}
			else
				throw new PrismLangException("Apparently found a declaration for an IndexedSet, but not whilst in an ModulesFile, nor a Module");
		}

		return e;
	}
}
