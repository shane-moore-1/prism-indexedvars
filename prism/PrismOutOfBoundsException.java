//==============================================================================
//	
//	Author: Shane Moore
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

package prism;

/**
 * An exception indicating that an attempt to access an element of an indexed set
 * using an index that is not within the bounds as declared for it (for example, position -1)
 * If evaluation of a guard causes this exception, the command will not be available.
 */
public class PrismOutOfBoundsException extends PrismLangException
{
	public PrismOutOfBoundsException(String s)
	{
		super(s);
	}
}
