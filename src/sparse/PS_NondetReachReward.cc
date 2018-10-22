//==============================================================================
//	
//	Copyright (c) 2002-
//	Authors:
//	* Dave Parker <david.parker@comlab.ox.ac.uk> (University of Oxford, formerly University of Birmingham)
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

// includes
#include "PrismSparse.h"
#include <cmath>
#include <util.h>
#include <cudd.h>
#include <dd.h>
#include <odd.h>
#include <dv.h>
#include "sparse.h"
#include "prism.h"
#include "PrismNativeGlob.h"
#include "PrismSparseGlob.h"
#include "jnipointer.h"
#include "ExportIterations.h"
#include <memory>
#include "Measures.h"
#include <new>

//------------------------------------------------------------------------------

JNIEXPORT jlong __jlongpointer JNICALL Java_sparse_PrismSparse_PS_1NondetReachReward
(
JNIEnv *env,
jclass cls,
jlong __jlongpointer t,		// trans matrix
jlong __jlongpointer ta,	// trans action labels
jobject synchs,
jlong __jlongpointer sr,	// state rewards
jlong __jlongpointer trr,	// transition rewards
jlong __jlongpointer od,	// odd
jlong __jlongpointer rv,	// row vars
jint num_rvars,
jlong __jlongpointer cv,	// col vars
jint num_cvars,
jlong __jlongpointer ndv,	// nondet vars
jint num_ndvars,
jlong __jlongpointer g,		// 'goal' states
jlong __jlongpointer in,	// 'inf' states
jlong __jlongpointer m,		// 'maybe' states
jboolean min				// min or max probabilities (true = min, false = max)
)
{
	// cast function parameters
	DdNode *trans = jlong_to_DdNode(t);				// trans matrix
	DdNode *trans_actions = jlong_to_DdNode(ta);	// trans action labels
	DdNode *state_rewards = jlong_to_DdNode(sr);	// state rewards
	DdNode *trans_rewards = jlong_to_DdNode(trr);	// transition rewards
	ODDNode *odd = jlong_to_ODDNode(od); 			// reachable states
	DdNode **rvars = jlong_to_DdNode_array(rv); 	// row vars
	DdNode **cvars = jlong_to_DdNode_array(cv); 	// col vars
	DdNode **ndvars = jlong_to_DdNode_array(ndv);	// nondet vars
	DdNode *goal = jlong_to_DdNode(g);				// 'goal' states
	DdNode *inf = jlong_to_DdNode(in); 				// 'inf' states
	DdNode *maybe = jlong_to_DdNode(m); 			// 'maybe' states

PS_PrintToMainLog(env,"<NondetReachReward>\n");
PS_PrintToMainLog(env,"PS_NondetReachReward.cc: Place 1\n");

	// mtbdds
	DdNode *a, *tmp = NULL;
	// model stats
	int n, nc, nc_r;
	long nnz, nnz_r;
	// sparse matrix
	NDSparseMatrix *ndsm = NULL, *ndsm_r = NULL;
	// vectors
	double *sr_vec = NULL, *soln = NULL, *soln2 = NULL, *tmpsoln = NULL, *inf_vec = NULL;
	// timing stuff
	long start1, start2, start3, stop;
	double time_taken, time_for_setup, time_for_iters;
	// adversary stuff
	int export_adv_enabled = export_adv;
	FILE *fp_adv = NULL;
	int adv_j;
	int *adv = NULL;
	// action info
	jstring *action_names_jstrings;
	const char** action_names = NULL;
	int num_actions;
	// misc
	int i, j, k, k_r, l1, h1, l2, h2, l2_r, h2_r, iters;
	double d1, d2, kb, kbt;
	bool done, first;
	// measure for convergence termination check
	MeasureSupNorm measure(term_crit == TERM_CRIT_RELATIVE);

	// exception handling around whole function
	try {
	
	// start clocks	
	start1 = start2 = util_cpu_time();
	
	// get number of states
	n = odd->eoff + odd->toff;

PS_PrintToMainLog(env,"PS_NondetReachReward.cc: Place 2\n");
	// filter out rows (goal states and infinity states) from matrix
	Cudd_Ref(trans);
	Cudd_Ref(maybe);
	a = DD_Apply(ddman, APPLY_TIMES, trans, maybe);
	
PS_PrintToMainLog(env,"PS_NondetReachReward.cc: Place 3\n");
	// also remove goal and infinity states from state rewards vector
	Cudd_Ref(state_rewards);
	Cudd_Ref(maybe);
	state_rewards = DD_Apply(ddman, APPLY_TIMES, state_rewards, maybe);
	
PS_PrintToMainLog(env,"PS_NondetReachReward.cc: Place 4\n");
	// and from transition rewards matrix
	Cudd_Ref(trans_rewards);
	Cudd_Ref(maybe);
	trans_rewards = DD_Apply(ddman, APPLY_TIMES, trans_rewards, maybe);
	
PS_PrintToMainLog(env,"PS_NondetReachReward.cc: Place 5\n");
	// build sparse matrix (probs)
	PS_PrintToMainLog(env, "\nBuilding sparse matrix (transitions)... ");
	ndsm = build_nd_sparse_matrix(ddman, a, rvars, cvars, num_rvars, ndvars, num_ndvars, odd);
	// get number of transitions/choices
	nnz = ndsm->nnz;
	nc = ndsm->nc;
	kb = (nnz*12.0+nc*4.0+n*4.0)/1024.0;
	kbt = kb;
	// print out info
	PS_PrintToMainLog(env, "[n=%d, nc=%d, nnz=%ld, k=%d] ", n, nc, nnz, ndsm->k);
	PS_PrintMemoryToMainLog(env, "[", kb, "]\n");

PS_PrintToMainLog(env,"PS_NondetReachReward.cc: Place 6\n");
	// if needed, and if info is available, build a vector of action indices for the MDP
	if (export_adv_enabled != EXPORT_ADV_NONE) {
		if (trans_actions != NULL) {
			PS_PrintToMainLog(env, "Building action information... ");
			// first need to filter out unwanted rows
			Cudd_Ref(trans_actions);
			Cudd_Ref(maybe);
			tmp = DD_Apply(ddman, APPLY_TIMES, trans_actions, maybe);
			// then convert to a vector of integer indices
			build_nd_action_vector(ddman, a, tmp, ndsm, rvars, cvars, num_rvars, ndvars, num_ndvars, odd);
			Cudd_RecursiveDeref(ddman, tmp);
			kb = n*4.0/1024.0;
			kbt += kb;
			PS_PrintMemoryToMainLog(env, "[", kb, "]\n");
			// also extract list of action names from 'synchs'
			get_string_array_from_java(env, synchs, action_names_jstrings, action_names, num_actions);
		} else {
			PS_PrintWarningToMainLog(env, "Action labels are not available for adversary generation.");
		}
	}
	
PS_PrintToMainLog(env,"PS_NondetReachReward.cc: Place 7\n");
	// build sparse matrix (rewards)
	PS_PrintToMainLog(env, "Building sparse matrix (transition rewards)... ");
	ndsm_r = build_sub_nd_sparse_matrix(ddman, a, trans_rewards, rvars, cvars, num_rvars, ndvars, num_ndvars, odd);
	// get number of transitions/choices
	nnz_r = ndsm_r->nnz;
	nc_r = ndsm_r->nc;
	// print out info
	PS_PrintToMainLog(env, "[n=%d, nc=%d, nnz=%ld, k=%d] ", n, nc_r, nnz_r, ndsm_r->k);
	kb = (nnz_r*12.0+nc_r*4.0+n*4.0)/1024.0;
	kbt += kb;
	PS_PrintMemoryToMainLog(env, "[", kb, "]\n");
	
PS_PrintToMainLog(env,"PS_NondetReachReward.cc: Place 8\n");
	// get vector for state rewards
	PS_PrintToMainLog(env, "Creating vector for state rewards... ");
	sr_vec = mtbdd_to_double_vector(ddman, state_rewards, rvars, num_rvars, odd);
	kb = n*8.0/1024.0;
	kbt += kb;
	PS_PrintMemoryToMainLog(env, "[", kb, "]\n");
	
PS_PrintToMainLog(env,"PS_NondetReachReward.cc: Place 9\n");
	// get vector for yes
	PS_PrintToMainLog(env, "Creating vector for inf... ");
	inf_vec = mtbdd_to_double_vector(ddman, inf, rvars, num_rvars, odd);
	kb = n*8.0/1024.0;
	kbt += kb;
	PS_PrintMemoryToMainLog(env, "[", kb, "]\n");
	
PS_PrintToMainLog(env,"PS_NondetReachReward.cc: Place 10\n");
	// create solution/iteration vectors
	PS_PrintToMainLog(env, "Allocating iteration vectors... ");
	soln = new double[n];
	soln2 = new double[n];
	kb = n*8.0/1024.0;
	kbt += 2*kb;
	PS_PrintMemoryToMainLog(env, "[2 x ", kb, "]\n");

	// if required, create storage for adversary and initialise
	if (export_adv_enabled != EXPORT_ADV_NONE) {
PS_PrintToMainLog(env,"PS_NondetReachReward.cc: Place 11\n");
		PS_PrintToMainLog(env, "Allocating adversary vector... ");
		adv = new int[n];
		kb = n*sizeof(int)/1024.0;
		kbt += kb;
		PS_PrintMemoryToMainLog(env, "[", kb, "]\n");
		// Initialise all entries to -1 ("don't know")
		for (i = 0; i < n; i++) {
			adv[i] = -1;
		}
	}
	
	// print total memory usage
	PS_PrintMemoryToMainLog(env, "TOTAL: [", kbt, "]\n");

PS_PrintToMainLog(env,"PS_NondetReachReward.cc: Place 12\n");
	// initial solution is infinity in 'inf' states, zero elsewhere
	for (i = 0; i < n; i++) {
///*SHANE*/ if (inf_vec[i] > 0 && inf_vec[i] < 40) 
		soln[i] = (inf_vec[i] > 0) ? HUGE_VAL : 0.0;
/*SHANE*/ PS_PrintToMainLog(env, "inf_vec[%d]=%f, so soln[%d]=%f\n ",i, inf_vec[i],i,soln[i]);
	}

	std::unique_ptr<ExportIterations> iterationExport;
	if (PS_GetFlagExportIterations()) {
		iterationExport.reset(new ExportIterations("PS_NondetReachReward"));
		PS_PrintToMainLog(env, "Exporting iterations to %s\n", iterationExport->getFileName().c_str());
		iterationExport->exportVector(soln, n, 0);
	}

	// get setup time
	stop = util_cpu_time();
	time_for_setup = (double)(stop - start2)/1000;
	start2 = stop;
	start3 = stop;

	// start iterations
	iters = 0;
	done = false;
PS_PrintToMainLog(env,"PS_NondetReachReward.cc: Place 13\n");
	PS_PrintToMainLog(env, "\nStarting iterations...\n");

	// open file to store adversary (if required)
	if (export_adv_enabled != EXPORT_ADV_NONE) {
		fp_adv = fopen(export_adv_filename, "w");
		if (!fp_adv) {
			PS_PrintWarningToMainLog(env, "Adversary generation cancelled (could not open file \"%s\").", export_adv_filename);
			export_adv_enabled = EXPORT_ADV_NONE;
		}
	}
	
	// store local copies of stuff
	// firstly for transition matrix
	double *non_zeros = ndsm->non_zeros;
	unsigned char *row_counts = ndsm->row_counts;
	int *row_starts = (int *)ndsm->row_counts;
	unsigned char *choice_counts = ndsm->choice_counts;
	int *choice_starts = (int *)ndsm->choice_counts;
	bool use_counts = ndsm->use_counts;
	unsigned int *cols = ndsm->cols;
	// and then for transition rewards matrix
	// (note: we don't need row_counts/row_starts for
	// this since choice structure mirrors transition matrix)
	double *non_zeros_r = ndsm_r->non_zeros;
	unsigned char *choice_counts_r = ndsm_r->choice_counts;
	int *choice_starts_r = (int *)ndsm_r->choice_counts;
	bool use_counts_r = ndsm_r->use_counts;
	unsigned int *cols_r = ndsm_r->cols;
		
// BELOW HERE IS TEMPORARY DEBUGGING

/*SHANE LOOP for OUTPUT ONLY*/

// In the following sets, I believe I have the correct upper limits.

PS_PrintToMainLog(env,"\nCSV-LONG,");
for (i = 0; i < nnz; i++) {
/*SHANE*/PS_PrintToMainLog(env,"cols[%d]=%u,",i,cols[i]);
}
PS_PrintToMainLog(env,"\nCSV-LONG,");
for (i = 0; i < nnz_r; i++) {
/*SHANE*/PS_PrintToMainLog(env,"cols_r[%d]=%u,",i,cols_r[i]);
}
PS_PrintToMainLog(env,"\nCSV-LONG,");
for (i = 0; i < nnz; i++) {
/*SHANE*/PS_PrintToMainLog(env,"non_z[%d]=%.1f,",i,non_zeros[i]);
}
PS_PrintToMainLog(env,"\nCSV-LONG,");
for (i = 0; i < nnz_r; i++) {
/*SHANE*/PS_PrintToMainLog(env,"non_z_r[%d]=%.1f,",i,non_zeros_r[i]);
}
PS_PrintToMainLog(env,"\nCSV-LONG,");
if (!use_counts)
for (i = 0; i < n; i++) {
/*SHANE*/PS_PrintToMainLog(env,"choice_s[%d]=%d,",i,choice_starts[i]);
}
else
for (i = 0; i < nc; i++) {
/*SHANE*/PS_PrintToMainLog(env,"choice_c[%d]=%d,",i,choice_counts[i]);
}
PS_PrintToMainLog(env,"\nCSV-LONG,");
if (!use_counts_r)
for (i = 0; i < nc_r; i++) {
/*SHANE*/PS_PrintToMainLog(env,"choice_s_r[%d]=%d,",i,choice_starts_r[i]);
}
else
for (i = 0; i < nc_r; i++) {
/*SHANE*/PS_PrintToMainLog(env,"choice_c_r[%d]=%d,",i,choice_counts_r[i]);
}
PS_PrintToMainLog(env,"\nCSV-LONG,");
if (!use_counts) 
for (i = 0; i < n; i++) {
/*SHANE*/PS_PrintToMainLog(env,"row_s[%d]=%d,",i,row_starts[i]);
}
else
for (i = 0; i < n; i++) {
/*SHANE*/PS_PrintToMainLog(env,"row_c[%d]=%d,",i,row_counts[i]);
}

PS_PrintToMainLog(env,"\n");


// ABOVE HERE IS TEMPORARY DEBUGGING

/// BELOW HERE SHOULD BE KEPT


/*SHANE - Make a CSV row that GREP can extract*/
/*SHANE*/ PS_PrintToMainLog(env,"CSV-LONG,");
/*SHANE*/for (i = 0; i < n; i++) 
///*SHANE*/  if (!(inf_vec[i] > 0)) 
/*SHANE*/   PS_PrintToMainLog(env,"soln[%d],",i);
/*SHANE*/ PS_PrintToMainLog(env,"\n");

/*SHANE - Make a CSV row that GREP can extract - this one hopefully skips (only) columns which are INF */
/*SHANE*/ PS_PrintToMainLog(env,"CSV-BRIEF,");
/*SHANE*/for (i = 0; i < n; i++) 
/*SHANE*/  if (!(inf_vec[i] > 0)) 
/*SHANE*/   PS_PrintToMainLog(env,"soln[%d],",i);
/*SHANE*/ PS_PrintToMainLog(env,"\n");

/*SHANE*/PS_PrintToMainLog(env,"About to start while loop...\n");
	while (!done && iters < max_iters) {
	
		iters++;
/*SHANE*/PS_PrintToMainLog(env,"<ITERATION iter_num=\"%d\">\n",iters);

		// do matrix multiplication and min/max
		h1 = h2 = h2_r = 0;
		// loop through states
		for (i = 0; i < n; i++) {
/*SHANE*/if (!(inf_vec[i] > 0)) PS_PrintToMainLog(env,"[%d beg, iter %d]\n",i,iters);
			d1 = 0.0; // initial value doesn't matter
			first = true; // (because we also remember 'first')
			// get pointers to nondeterministic choices for state i
			if (!use_counts) { l1 = row_starts[i]; h1 = row_starts[i+1]; }
			else { l1 = h1; h1 += row_counts[i]; }
/*SHANE*/PS_PrintToMainLog(env," @P: Reset d1 [for 'best' we encounter] to %f\n",d1);
/*SHANE*/PS_PrintToMainLog(env," @R: Number of choices at this state: %d\n",h1-l1);
			// loop through those choices
			for (j = l1; j < h1; j++) {
				// compute the reward value for state i for this iteration
				// start with state reward for this state
				d2 = sr_vec[i];
/*SHANE*/PS_PrintToMainLog(env,"   @S: Dealing with choice %d - reset d2 to state-reward value %f\n",j-l1+1,d2);
				// get pointers to transitions
				if (!use_counts) { l2 = choice_starts[j]; h2 = choice_starts[j+1]; }
				else { l2 = h2; h2 += choice_counts[j]; }
				// and get pointers to transition rewards
				if (!use_counts_r) { l2_r = choice_starts_r[j]; h2_r = choice_starts_r[j+1]; }
				else { l2_r = h2_r; h2_r += choice_counts_r[j]; }
/*SHANE*/PS_PrintToMainLog(env,"   @T: This choice has this many Transitions: %d\n",h2-l2);
/*SHANE*/PS_PrintToMainLog(env,"       This choice has this many Transition Rewards: %d, pointers %d to %d\n",h2_r-l2_r,l2_r,h2_r-1);
				// loop through transitions
				for (k = l2; k < h2; k++) {
   PS_PrintToMainLog(env,"    @U: Considering Transition %d  (this means k=%d;  and cols[k] is %u)\n",k-l2+1,k,cols[k]);
					// find corresponding transition reward if any
					k_r = l2_r;
///*SHANE*/PS_PrintToMainLog(env,"    @W: Transition %d has reward #%d (i.e. k_r).\n    Thus cols_r[k]=%u and cols[k]=%u ",k-l2+1,k_r,cols_r[k_r],cols[k]);
///*SHANE*/if (k_r < h2_r) {
///*SHANE*/PS_PrintToMainLog(env,"- And k_r < h2_r");
///*SHANE*/  if (cols_r[k_r] != cols[k]) {
///*SHANE*/PS_PrintToMainLog(env,"- And cols_r[k_r] != cols[k], so loop will run.");
///*SHANE*/  }
///*SHANE*/else PS_PrintToMainLog(env,"- BUT NOT cols_r[k_r] != cols[k], so loop won\'t run.");
///*SHANE*/}
///*SHANE*/else PS_PrintToMainLog(env,"- But NOT k_r < h2_r, so loop won\'t run.");
///*SHANE*/PS_PrintToMainLog(env,"\n");
/*SHANE*/if (k_r < h2_r) {
   PS_PrintToMainLog(env,"       @X: MIGHT need to search cols[?] to find the corresponding Transition Reward Pointer.\n");
   PS_PrintToMainLog(env,"           since cols[k=%d] is %u, and cols_r[k_r=%d] is %u\n",k,cols[k],k_r,cols_r[k_r]);
   if (cols_r[k_r] != cols[k])
   PS_PrintToMainLog(env,"           WILL search - since those two (cols and cols_r) don\'t match.\n");
   else PS_PrintToMainLog(env,"           WON\'T search - since those two (cols and cols_r) already match.\n");
/*SHANE*/} else PS_PrintToMainLog(env,"       @X: The last option for the corresponding Transition Reward Pointer.\n");
					 
					while (k_r < h2_r && cols_r[k_r] != cols[k]) {	// SHANE has expanded body, to enable debug output.
/*SHANE*/PS_PrintToMainLog(env,"      @E: cols_r[%d]=%u and cols[%d]=%u\n",k_r,cols_r[k_r],k,cols[k]);
						k_r++;
					}
					// if there is one, add reward * prob to reward value
					if (k_r < h2_r) { 		// SHANE has expanded body...
						d2 += non_zeros_r[k_r] * non_zeros[k]; 
/*SHANE*/PS_PrintToMainLog(env,"       @Y: Found as option #%d, non_zeros_r[k_r=%d] is %f and non_zeros[%d] is %f\n",k_r-l2_r+1,k_r,non_zeros_r[k_r],k_r,non_zeros[k]);
/*SHANE*/PS_PrintToMainLog(env,"           which multiply as %f, to be added to d2 to make d2 now: %f\n",non_zeros[k] * non_zeros_r[k_r],d2);

						k_r++; 
					}
/*SHANE*/else PS_PrintToMainLog(env,"       @Y: Not Found - thus d2 not increased by this Transition, and is still: %f\n",d2);

/*SHANE*/PS_PrintToMainLog(env,"       @V: k=%d, cols[k] (i.e. cols[%d]) is %d\n",k,k,cols[k]);
/*SHANE*/PS_PrintToMainLog(env,"           non_zeros[%d] is %f; soln[%d] is %f (apparently from previous iteration)\n",k,non_zeros[k],cols[k],soln[cols[k]]);
/*SHANE*/PS_PrintToMainLog(env,"           which multiplied together is %f\n",non_zeros[k] * soln[cols[k]]);
					// add prob * corresponding reward from previous iteration
					d2 += non_zeros[k] * soln[cols[k]];
/*SHANE*/PS_PrintToMainLog(env,"           which added to d2 to make d2 now: %f\n",d2);

//OLD /*SHANE*/PS_PrintToMainLog(env,"    @Z: After adding the previous iteration's value from soln[cols[k=%d]=%u], d2 is %f\n",k,cols[k],d2);
   PS_PrintToMainLog(env,"    @u: Finished Considering Transition %d\n",k-l2+1);
				}
				// see if this value is the min/max so far
				if (first || (min&&(d2<d1)) || (!min&&(d2>d1))) {
/*SHANE*/PS_PrintToMainLog(env,"   @s: End of considering Choice %d. Updating d1 [best this iteration, for this state] from %f to %f\n\n",j-l1+1,d1,d2);
					d1 = d2;
					// if adversary generation is enabled, remember optimal choice
					if (export_adv_enabled != EXPORT_ADV_NONE) {
						// for max, only remember strictly better choices
						// (this resolves problems with end components)
						if (!min) {
							if (adv[i] == -1 || (d1>soln[i])) {
								adv[i] = j;
							}
						}
						// for min, this is straightforward
						// (in fact, could do it at the end of value iteration, but we don't)
						else {
							adv[i] = j;
						}
					}
				}
/*SHANE*/else PS_PrintToMainLog(env,"   @s: End of considering Choice %d, but NOT Updating d1 from %f\n",j-l1+1,d1);
				first = false;
			}
if (h1 > l1)
/*SHANE*/PS_PrintToMainLog(env,"   Since there was at least 1 choice, we use value d1 for soln2[], which is %f\n",d1);
/*SHANE*/else {PS_PrintToMainLog(env,"   Since there was just 1 choice, we consider value inf_vec[state=i=%d] which is: %f\n",i,inf_vec[i]);
/*SHANE*/   if (inf_vec[i] > 0) PS_PrintToMainLog(env,"   and so sol2[] will be set to HUGE_VAL.\n");
/*SHANE*/   else PS_PrintToMainLog(env,"   and so sol2[] will be set to 0 also.\n");
/*SHANE*/}
			// set vector element
			// (if there were no choices from this state, reward is zero/infinity)
			soln2[i] = (h1 > l1) ? d1 : inf_vec[i] > 0 ? HUGE_VAL : 0;
/*SHANE*/if (!(inf_vec[i] > 0)) PS_PrintToMainLog(env,"[%d end] %c ",i, (soln[i] > 0) ? ((h1 <= l1 && inf_vec[i] > 0) ? '*':'-') : (h1 <= l1 && inf_vec[i] > 0) ? '+':' ' );  /* Asterisk the i values which are 'INFINITY' */
/*SHANE*/if (!(inf_vec[i] > 0) && soln[i] != soln2[i]) PS_PrintToMainLog(env,"soln[%d] was %f, soln2[%d] is now %f",i,soln[i],i,soln2[i]);
PS_PrintToMainLog(env,"\n");
		}

		if (iterationExport)
			iterationExport->exportVector(soln2, n, 0);

		// check convergence
		measure.reset();
		measure.measure(soln, soln2, n);
/*SHANE*/PS_PrintToMainLog(env,"Checked Convergence after iteration %d. measure.value()=%f, term_crit_param=%f\n",iters,measure.value(),term_crit_param);
		if (measure.value() < term_crit_param) {
/*SHANE*/PS_PrintToMainLog(env,"So COMPLETED now.\n");
			done = true;
		}

		// print occasional status update
		if ((util_cpu_time() - start3) > UPDATE_DELAY) {
			PS_PrintToMainLog(env, "Iteration %d: max %sdiff=%f", iters, measure.isRelative()?"relative ":"", measure.value());
			PS_PrintToMainLog(env, ", %.2f sec so far\n", ((double)(util_cpu_time() - start2)/1000));
			start3 = util_cpu_time();
		}
		
		// prepare for next iteration
		tmpsoln = soln;
		soln = soln2;
		soln2 = tmpsoln;
/*SHANE*/for (i = 0; i < n; i++) 
/*SHANE*/  if (!(inf_vec[i] > 0)) PS_PrintToMainLog(env,"soln[%d] is now %f (previously was %f)\n",i,soln[i],soln2[i]);

/*SHANE - Make a CSV row that GREP can extract*/
/*SHANE*/ PS_PrintToMainLog(env,"CSV-LONG,");
/*SHANE*/for (i = 0; i < n; i++) 
///*SHANE*/  if (!(inf_vec[i] > 0)) 
/*SHANE*/   PS_PrintToMainLog(env,"%f,",soln[i]);
/*SHANE*/ PS_PrintToMainLog(env,"\n");

/*SHANE - Make a CSV row that GREP can extract - This one SKIPS the INF columns, hopefully.*/
/*SHANE*/ PS_PrintToMainLog(env,"CSV-BRIEF,");
/*SHANE*/for (i = 0; i < n; i++) 
/*SHANE*/  if (!(inf_vec[i] > 0)) 
/*SHANE*/   PS_PrintToMainLog(env,"%f,",soln[i]);
/*SHANE*/ PS_PrintToMainLog(env,"\n");


/*SHANE*/PS_PrintToMainLog(env,"</ITERATION>\n\n");

	}

/*SHANE*/PS_PrintToMainLog(env,"PS_NondetReachReward.cc: Place 14 (conclusion of the while loop.)\n");
	
	// Traverse matrix to extract adversary
	if (export_adv_enabled != EXPORT_ADV_NONE) {
		// Do two passes: first to compute the number of transitions,
		// the second to actually do the export
		int num_trans = 0;
		for (int pass = 1; pass <= 2; pass++) {
			if (pass == 2) {
				fprintf(fp_adv, "%d %d\n", n, num_trans);
			}
			h1 = h2 = 0;
			for (i = 0; i < n; i++) {
				if (!use_counts) { l1 = row_starts[i]; h1 = row_starts[i+1]; }
				else { l1 = h1; h1 += row_counts[i]; }
				// Have to loop through all choices (to compute offsets)
				for (j = l1; j < h1; j++) {
					if (!use_counts) { l2 = choice_starts[j]; h2 = choice_starts[j+1]; }
					else { l2 = h2; h2 += choice_counts[j]; }
					// But only output a choice if it is in the adversary
					if (j == adv[i]) {
						switch (pass) {
						case 1:
							num_trans += (h2-l2);
							break;
						case 2:
							for (k = l2; k < h2; k++) {
								switch (export_adv_enabled) {
								case EXPORT_ADV_DTMC:
									fprintf(fp_adv, "%d %d %g", i, cols[k], non_zeros[k]); break;
								case EXPORT_ADV_MDP:
									fprintf(fp_adv, "%d 0 %d %g", i, cols[k], non_zeros[k]); break;
								}
								if (ndsm->actions != NULL) fprintf(fp_adv, " %s", ndsm->actions[j]>0?action_names[ndsm->actions[j]-1]:"");
								fprintf(fp_adv, "\n");
							}
						}
					}
				}
			}
		}
	}
	
	// stop clocks
	stop = util_cpu_time();
	time_for_iters = (double)(stop - start2)/1000;
	time_taken = (double)(stop - start1)/1000;
	
	// print iterations/timing info
PS_PrintToMainLog(env,"PS_NondetReachReward.cc: Place 15\n");
	PS_PrintToMainLog(env, "\nIterative method: %d iterations in %.2f seconds (average %.6f, setup %.2f)\n", iters, time_taken, time_for_iters/iters, time_for_setup);
	
	// if the iterative method didn't terminate, this is an error
	if (!done) { delete[] soln; soln = NULL; PS_SetErrorMessage("Iterative method did not converge within %d iterations.\nConsider using a different numerical method or increasing the maximum number of iterations", iters); }
	
	// close file to store adversary (if required)
	if (export_adv_enabled != EXPORT_ADV_NONE) {
		fclose(fp_adv);
		PS_PrintToMainLog(env, "\nAdversary written to file \"%s\".\n", export_adv_filename);
	}

/*SHANE*/PS_PrintToMainLog(env,"</NondetReachReward>\n");
	
	// catch exceptions: register error, free memory
	} catch (std::bad_alloc e) {
		PS_SetErrorMessage("Out of memory");
		if (soln) delete[] soln;
		soln = 0;
	}
	
	// free memory
	if (a) Cudd_RecursiveDeref(ddman, a);
	if (state_rewards) Cudd_RecursiveDeref(ddman, state_rewards);
	if (trans_rewards) Cudd_RecursiveDeref(ddman, trans_rewards);
	if (ndsm) delete ndsm;
	if (ndsm_r) delete ndsm_r;
	if (inf_vec) delete[] inf_vec;
	if (sr_vec) delete[] sr_vec;
	if (soln2) delete[] soln2;
	if (adv) delete[] adv;
	if (action_names != NULL) {
		release_string_array_from_java(env, action_names_jstrings, action_names, num_actions);
	}
	
	return ptr_to_jlong(soln);
}

//------------------------------------------------------------------------------
