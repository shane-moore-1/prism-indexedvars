package parser.type;

// ** This File by Shane Moore <shane.moore@student.rmit.edu.au> (RMIT University, Australia)

import prism.PrismLangException;

/**
 * This represents a pseudo-type, which is used for cases where an indexable set of a fixed number of values (alternatively described as an array)
 * would be desired.
 * As Shane has not yet worked out the intracacies of how the parser.type.Type hierarchy is used elsewhere in Prism (than the Parser),
 * he has only written this class cautiously. 
 * @author Shane Moore
 *
 */
public class TypeIndexedSet extends Type {
	
	/** What type of elements are used to make the values that are in this set? */
	private Type elementsType;

	// Should we also include 'size', as a distinguishing aspect for different types of indexed set.
	
	// The constructor is not currently being forced to some sort of singleton instance at this stage.
	/** Create a new type signature to represent an indexed-set of some specific type of elements (size not specified)
	 * @param elementsType What type of elements will be in the Set.
	 */
	public TypeIndexedSet(Type elementsType)
	{
		this.elementsType = elementsType;
	}
		

	@Override 
	public String getTypeString()
	{
		return "indexed set of " + elementsType.getTypeString() + " values";
	}
	
	/**
	 * NOTE: It makes no sense to give a default value; since the Indexed Set will be translated into individual items of the underlying type.
	 * Any Code that tries to call this method on an TypeIndexedSet will fail
	 */
	@Override
	public Object defaultValue()
	{
		throw new RuntimeException("Invalid call to defaultValue() of parser.type.TypeIndexedSet");
		//return null;
	}

	/**
	 * Always returns false, because this is not a true type, it is a mere syntactic convenience for declaring a group of variables. 
	 */
	@Override
	public boolean canAssign(Type type)
	{
		// No, because this is not a true type, it is a mere syntactic convenience for declaring a group of variables.
		return false;
	}

	/**
	 * Not valid for TypeIndexedSet, because this is not a true type, it is a mere syntactic convenience for declaring a group of variables.
	 */
	public Object castValueTo(Object value) throws PrismLangException
	{
		throw new PrismLangException("Cannot cast a value of type IndexedSet to type " + getTypeString());
	}

}
