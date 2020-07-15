//==============================================================================
//	
//	Copyright (c) 2002-
//	Authors:
//	* Dave Parker <david.parker@comlab.ox.ac.uk> (University of Oxford, formerly University of Birmingham)
//	* Joachim Klein <klein@tcs.inf.tu-dresden.de> (TU Dresden)
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

#include "odd.h"

#include <time.h>	// Shane Inserted, for debugging assistance.

#define SHANE_DEBUG_DETAIL 0

// static variables
static int num_odd_nodes = 0;
static int reportedOverflow = false;

// local prototypes
static ODDNode *build_odd_rec(DdManager *ddman, DdNode *dd, int level, DdNode **vars, int num_vars, ODDNode **tables);
static int64_t add_offsets(DdManager *ddman, ODDNode *dd, int level, int num_vars, int fromOccurrenceShane);
static DdNode *single_index_to_bdd_rec(DdManager *ddman, int i, DdNode **vars, int num_vars, int level, ODDNode *odd, long o);

//------------------------------------------------------------------------------

ODDNode *build_odd(DdManager *ddman, DdNode *dd, DdNode **vars, int num_vars)
{
	int i;
	ODDNode **tables;
	ODDNode *res;

time_t StartTimer, ShaneTimer;
int shaneCount;

StartTimer = time(NULL);

printf("\n[ShaneNote] in odd/odd.cc: commencing build_odd - note that there are %d elements for 'tables' (that is the value of num_vars).\n",num_vars);
#if !SHANE
printf("\t   NOT displaying detailed construction and returning back (so log file will be smaller)");
printf("\n\t   You would need to re-compile src/odd/odd.cc with the SHANE_DEBUG_DETAIL #define set to 1.");
#endif
	// build tables to store odd nodes during construction
	tables = new ODDNode*[num_vars+1];
	for (i = 0; i < num_vars+1; i++) {
		tables[i] = NULL;
	}

	// reset node counter
	num_odd_nodes = 0;

printf("\n[ShaneNote] in odd/odd.cc: commencing recursive build_odd_rec\n");
	// call recursive bit
	res = build_odd_rec(ddman, dd, 0, vars, num_vars, tables);

printf("[ShaneNote] in odd/odd.cc in build_odd: completed the recursive top-level call of build_odd_rec.\n");
printf("\n\n[ShaneNote] The top-level ODDNode is %p, and the calculated number of odd nodes is: %d\n\n",res,num_odd_nodes);
	// At this point, all the allocated ODDNodes for this ODD are
	// chained by linked lists (ODD->next), one for each non-empty tables[i].
	// To facilitate deallocation later on, we chain all these
	// individual linked lists together.
	// By construction, the root node (res) is the only node in
	// the top-most, non-empty table and is thus at the
	// start of the resulting chain.

	// The current end of the linked list
	ODDNode *last = NULL;
printf("[ShaneNote] Commencing the large for loop in build_odd [will do %d times]...\n",num_vars+1);
	for (i = 0; i < num_vars+1; i++) {
ShaneTimer = time(NULL);
printf("iter %d\n",(i+1));
//if (tables[i] == NULL) printf("    tables[%d] is NULL\n",i); else printf("    tables[%d] not null\n",i);
		if (tables[i] != NULL) {
//if (last != NULL) printf("      last not null\n"); else printf("      last IS null\n");
			// non-empty tables slot
			if (last != NULL) {
				// chain the first node in this tables slot to
				// the last one above
				last->next = tables[i];
			}
			// search for last node in this tables slot
			last = tables[i];
shaneCount = 0;
			while (last->next != NULL) {
shaneCount++;
				last = last->next;
			}
printf("    the while loop had to go over %d items to find last.\n\n",shaneCount);
		}
	}

printf("\n[ShaneNote] About to call add_offsets...\n");
//printf("GraphViz: digraph add_offsets {\n");
//printf("GraphViz: node [shape=circle];\n");
	// add offsets to odd

reportedOverflow = false;
	if (add_offsets(ddman, res, 0, num_vars,0) < 0) {		/* Shane added 5th parameter */
printf(" overall result of add_offsets (at top level) was negative. This indicates that there was an arithmetic overflow.\n\n");
		// negative value indicates that there was an arithmetic overflow
		// cleanup and return 0
		clear_odd(res);
		res = 0;
	}
else printf(" overall result of add_offsets (at top level) was positive, which seems to mean \"all is fine\"\n");

printf("\n<SummaryODD>\n[ShaneNote] The all-important measures again:\n1. the number of states (i.e. sum of the eoff and toff at level 0) is: %ld\n2. the calculated number of unique odd nodes (of which some may be 'used' multiple times in the MTBDD) is: %d,\n and the top-level ODDNode is %p, \n</SummaryODD>\n\n",res->eoff + res->toff, num_odd_nodes,res);

//printf("GraphViz: }\n");

	// free memory
	delete tables;

	return res;
}

//------------------------------------------------------------------------------

static ODDNode *build_odd_rec(DdManager *ddman, DdNode *dd, int level, DdNode **vars, int num_vars, ODDNode **tables)
{
	ODDNode *ptr;
	
time_t StartTime, CurTime;
static long TotalOccurrences = 0L;		// PRESERVED across calls
long occurrence = ++TotalOccurrences;

#if SHANE_DEBUG_DETAIL
printf("\nbuild_odd_rec() #%ld (lvl=%d)\n",occurrence,level); //,dd);  // printed dd using %p, i.e. address 0x1234567890AB
#else
if ((occurrence % 10000) == 0)
 printf("\nbuild_odd_rec() #%ld (lvl=%d) commencing\n",occurrence,level); //,dd);  // printed dd using %p, i.e. address 0x1234567890AB
#endif
	// see if we already have odd in the tables AT THAT LEVEL
	ptr = tables[level];
	while (ptr != NULL) {
		if (ptr->dd == dd) break;
		ptr = ptr->next;
	}
	
	// if not, add it
	if (ptr == NULL) {
//printf(" dd new to level %d, adding \n",level);
		num_odd_nodes++;
		ptr = new ODDNode();
		ptr->dd = dd;		
		ptr->next = tables[level];
		tables[level] = ptr;
		// and recurse...
		
// should be able to add this because will never traverse a path to the
// zeros temrinal - always look at states that exist
// can we assume this?
//	if (dd == Cudd_ReadZero(ddman)) return;

		if (level == num_vars) {
			ptr->e = NULL;
			ptr->t = NULL;
		}
		else if (vars[level]->index < dd->index) {
//printf("   Case A recursing...\n");
			ptr->e = build_odd_rec(ddman, dd, level+1, vars, num_vars, tables);
			ptr->t = ptr->e;
//printf("   Case A concluded [both e+t same] - occur#%ld (at level %d).\n",occurrence,level);
		}
		else {
//printf("   Case B recursing for E...\n");
			ptr->e = build_odd_rec(ddman, Cudd_E(dd), level+1, vars, num_vars, tables);
//printf("   Case B Finished E - occur#%ld (at level %d). Recursing for T...\n",occurrence,level);
			ptr->t = build_odd_rec(ddman, Cudd_T(dd), level+1, vars, num_vars, tables);
//printf("   Case B Finished T - occur#%ld (at level %d).\n   Case B concluded\n",occurrence,level);
		}
		ptr->eoff = -1;
		ptr->toff = -1;
	}
//else printf(" dd already in level %d - returning it's odd.\n\n",level);
#if SHANE_DEBUG_DETAIL
else printf("already in\n");
#endif

#if !SHANE_DEBUG_DETAIL
if ((occurrence % 10000) == 0)
 printf("\nbuild_odd_rec() #%ld (lvl=%d) concluding\n",occurrence,level); //,dd);  // printed dd using %p, i.e. address 0x1234567890AB
#endif
	
	return ptr;
}

//------------------------------------------------------------------------------

//
// Compute the actual eoff and toff values.
// Returns eoff + toff for this odd node or -1 if there is an arithmetic overflow
// (can not store eoff+toff in an int64_t)
int64_t add_offsets(DdManager *ddman, ODDNode *odd, int level, int num_vars,int fromOccurrenceShane)
{

static int TotalOccurrences = 0L;		// PRESERVED across calls
int occurrence = ++TotalOccurrences;

#if SHANE_DEBUG_DETAIL
printf("\nadd_off occ #%d,@lv %d\n",occurrence,level);
//printf(" ODDNode references dd: %p\n",odd->dd);
#endif
	if ((odd->eoff == -1) || (odd->toff == -1)) {
		// this node has not yet been seen (BY THIS RECURSIVE METHOD).
		if (level == num_vars) {
//printf(" REACHED the lowest level: ");
			if (odd->dd == Cudd_ReadZero(ddman)) {
//printf("  just a ZERO\n");
				odd->eoff = 0;
				odd->toff = 0;
			}
			else {
//printf("  non-zero: set eoff=0, toff=1\n");
				odd->eoff = 0;
				odd->toff = 1;
			}
		}
		else {
#if SHANE_DEBUG_DETAIL
printf("occ#d@lv %d (occur#%d), Do E\n",occurrence,level);
#endif
			odd->eoff = add_offsets(ddman, odd->e, level+1, num_vars,occurrence);
#if SHANE_DEBUG_DETAIL
printf("@lv %d (occ#%d), Dn E, eoff=%ld\n",level,occurrence,odd->eoff);
#endif
			if (odd->eoff < 0) return -1;
#if SHANE_DEBUG_DETAIL
printf("do T\n");
#endif
			odd->toff = add_offsets(ddman, odd->t, level+1, num_vars,occurrence);
#if SHANE_DEBUG_DETAIL
printf("@lv %d (occ#%d), Dn T, toff=%ld\n",level,occurrence,odd->toff);
#endif
			if (odd->toff < 0) return -1;
		}

		// overflow check for sum
		// do unsigned addition, guaranteed to not overflow
		// as eoff and toff are signed and positive, cast sum to signed and
		// check that it is larger than one of the summands
		int64_t tmp = (int64_t)((uint64_t)odd->eoff + (uint64_t)odd->toff);
		if (tmp < odd->eoff) {
if (!reportedOverflow) {
  printf("\n/add_off occ #%d,@lv %d OVERFLOW: - eoff:%ld, toff:%ld\n",occurrence,level,odd->eoff,odd->toff);
  reportedOverflow = true;
}

			// we have an overflow
			return -1;
		}
	}
#if SHANE_DEBUG_DETAIL
else printf("\talr seen");
#endif

#if SHANE_DEBUG_DETAIL
printf("\n/add_off occ #%d,@lv %d\n",occurrence,level);
#endif

if (level < 4) printf("\n/add_off @lv %d for occurrence #%d: - eoff:%ld, toff:%ld, sum: [%ld]\n",level,occurrence,odd->eoff,odd->toff, (odd->eoff + odd->toff));

//printf("\nGraphViz: occ_%d -> occ_%d [ label =\"%ld\" ];\n\n",fromOccurrenceShane,occurrence,odd->eoff + odd->toff);
	return odd->eoff + odd->toff;
}

//------------------------------------------------------------------------------

void clear_odd(ODDNode *odd) {
	// We assume that odd is the root node of an ODD, i.e.,
	// was returned previously from a build_odd call.
	// It is thus the first element of the linked list
	// (with ODDNode->next pointers) that references all the
	// allocated ODDNodes of this ODD and we can simply
	// delete each ODDNode in turn.
	while (odd != NULL) {
		ODDNode *next = odd->next;
		delete odd;
		odd = next;
	}
}

//------------------------------------------------------------------------------

// Get the index (according to an ODD) of the first non-zero enetry of a BDD

int get_index_of_first_from_bdd(DdManager *ddman, DdNode *dd, DdNode **vars, int num_vars, ODDNode *odd)
{
	DdNode *ptr, *ptr_next;;
	ODDNode *odd_ptr;
	int i, index;
	
	// Go down dd along first non-zero path
	index = 0;
	ptr = dd;
	odd_ptr = odd;
	for (i = 0; i < num_vars; i++) {
		ptr_next = (ptr->index > vars[i]->index) ? ptr : Cudd_E(ptr);
		if (ptr_next != Cudd_ReadZero(ddman)) {
			odd_ptr = odd_ptr->e;
		}
		else {
			ptr_next = (ptr->index > vars[i]->index) ? ptr : Cudd_T(ptr);
			index += odd_ptr->eoff;
			odd_ptr = odd_ptr->t;
		}
		ptr = ptr_next;
	}

	return index;
}

// Get a BDD for a single state given its index and the accompanying ODD.

EXPORT DdNode *single_index_to_bdd(DdManager *ddman, int i, DdNode **vars, int num_vars, ODDNode *odd)
{
	return single_index_to_bdd_rec(ddman, i, vars, num_vars, 0, odd, 0);
}

DdNode *single_index_to_bdd_rec(DdManager *ddman, int i, DdNode **vars, int num_vars, int level, ODDNode *odd, long o)
{
	DdNode *dd;

	if (level == num_vars) {
		return DD_Constant(ddman, 1);
	}
	else {
		if (odd->eoff > i - o) {
			dd = single_index_to_bdd_rec(ddman, i, vars, num_vars, level+1, odd->e, o);
			Cudd_Ref(vars[level]);
			return DD_And(ddman, DD_Not(ddman, vars[level]), dd);
		}
		else {
			dd = single_index_to_bdd_rec(ddman, i, vars, num_vars, level+1, odd->t, o+odd->eoff);
			Cudd_Ref(vars[level]);
			return DD_And(ddman, vars[level], dd);
		}
	}
}

//------------------------------------------------------------------------------

int get_num_odd_nodes()
{
	return num_odd_nodes;
}

//------------------------------------------------------------------------------
