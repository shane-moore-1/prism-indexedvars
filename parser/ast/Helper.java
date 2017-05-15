//==============================================================================
//	
//	Authors: Shane Moore
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

package parser.ast;

import prism.PrismLangException;
import java.util.*;

/**
 * A class to provide easy-access to several pieces of information through static methods.
 */
public class Helper
{
	// A Map containing declarations of all known indexedSets. Useful at Evaluation time for bound checking.
	private static HashMap<String,Declaration> indexedSetDecls;

	// pseudo-constructor.
	static {
		indexedSetDecls = new HashMap<String,Declaration>();
	}

	public static void noteIndexedSetDeclaration(Declaration decl)
	{
		if (decl != null) {
System.out.println("Noting declaration of IndexedSet: " + decl.getName());
			indexedSetDecls.put(decl.getName(), decl);
		}
	}

	public static Declaration getIndexedSetDeclaration(String name)
	{
		return indexedSetDecls.get(name);
	}

}
