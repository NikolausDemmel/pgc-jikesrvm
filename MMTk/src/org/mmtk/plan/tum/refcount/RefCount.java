/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.plan.tum.refcount;

import org.mmtk.plan.Phase;
import org.mmtk.plan.StopTheWorld;
import org.mmtk.plan.Trace;
import org.mmtk.plan.TransitiveClosure;
import org.mmtk.policy.ExplicitFreeListSpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.Log;
import org.mmtk.utility.deque.ObjectReferenceDeque;
import org.mmtk.utility.deque.SharedDeque;
import org.mmtk.utility.heap.VMRequest;
import org.mmtk.utility.options.Options;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.ObjectReference;


@Uninterruptible
public class RefCount extends StopTheWorld {
//
	private static int count = 0;
	  public final static SharedDeque zcts = new SharedDeque("zcts",metaDataSpace, 1);
	/****************************************************************************
	 * Class variables
	 */
	public static final ExplicitFreeListSpace rcSpace = new ExplicitFreeListSpace("rc", VMRequest.create());
	private static final RefCountSweeper sweeper = new RefCountSweeper();

	public static final int RC_DESC = rcSpace.getDescriptor();
	public static final int SCAN_MARK = 0;

	public final static Trace refCountTrace = new Trace(metaDataSpace);

	public static final short PREPARE_ZCT = Phase.createSimple("prepareZCT");
	public static final short PROCESS_ZCT = Phase.createSimple("processZCT");
	public static final short TRACE_ROOT_SET = Phase.createSimple("traceRootSet");

	protected static final short process_zct = Phase.createComplex("process-zct",
		      Phase.scheduleCollector  (PROCESS_ZCT),
		      Phase.scheduleGlobal     (PROCESS_ZCT));
	protected static final short prepare_zct = Phase.createComplex("prepare-zct",
		      Phase.scheduleMutator     (PREPARE_ZCT),
		      Phase.scheduleGlobal     (PREPARE_ZCT),
		      Phase.scheduleCollector   (PREPARE_ZCT));

	public  short collection = Phase.createComplex("collection", null,
			Phase.scheduleComplex(initPhase),
			Phase.scheduleComplex(prepare_zct),
			Phase.scheduleComplex(rootClosurePhase),
			Phase.scheduleComplex(process_zct),
			Phase.scheduleComplex(completeClosurePhase),
			Phase.scheduleComplex(finishPhase)
	);


	/****************************************************************************
	 * Instance variables
	 */
//	public final Trace rcTrace = new Trace(metaDataSpace);


	/*****************************************************************************
	 * Collection
	 */
	public static final boolean isRefCountObject(ObjectReference object) {
		return !object.isNull();// && !Space.isInSpace(RC_DESC, object);
	}
public RefCount(){
	Options.noFinalizer.setDefaultValue(true);
	Options.noReferenceTypes.setDefaultValue(true);
}
//	public Trace rootTrace;
	/**
	 * Perform a (global) collection phase.
	 *
	 * @param phaseId Collection phase to execute.
	 */
	@Inline
	@Override
	public void collectionPhase(short phaseId) {
//		Log.writeln("RefCount.collectionPhase("+phaseId+")");
		if (phaseId == PREPARE) {
			//			 try to kill all elements from ZCT
//			Log.writeln("PREPARE");
//			super.collectionPhase(phaseId);
			VM.finalizableProcessor.clear();
			VM.weakReferences.clear();
			VM.softReferences.clear();
			VM.phantomReferences.clear();
//			rcTrace.prepare();
			rcSpace.prepare();
			return;
		}
		
		if (phaseId == CLOSURE) {
//			Log.writeln("CLOSURE");
//			rcTrace.prepare();
			return;
		}
		if (phaseId == RELEASE) {
//			Log.writeln("RELEASE");
//			rcTrace.release();
			rcSpace.release();
			setCount(0);
			return;
		}
		if (phaseId == PREPARE_ZCT) {
			Log.writeln("GLOBAL_PREPARE_ZCT");
			return;
		}
		if(phaseId==PROCESS_ZCT){

			rcSpace.sweepCells(sweeper);
			return;
		}
		super.collectionPhase(phaseId);
	}

	/*****************************************************************************
	 * Accounting
	 */

	

	/**
	 * Return the number of pages reserved for use given the pending
	 * allocation.  The superclass accounts for its spaces, we just
	 * augment this with the mark-sweep space's contribution.
	 *
	 * @return The number of pages reserved given the pending
	 * allocation, excluding space reserved for copying.
	 */
	@Override
	public int getPagesUsed() {
		return (rcSpace.reservedPages() + super.getPagesUsed());
	}

	/*****************************************************************************
	 * Miscellaneous
	 */

	/**
	 * @see org.mmtk.plan.Plan#willNeverMove
	 *
	 * @param object Object in question
	 * @return True if the object will never move
	 */
	@Override
	public boolean willNeverMove(ObjectReference object) {
		if (Space.isInSpace(RC_DESC, object))
			return true;
		return super.willNeverMove(object);
	}
//	@Interruptible
//	@Override
//	protected void registerSpecializedMethods() {
//		// TODO Auto-generated method stub
//		super.registerSpecializedMethods();
//	}
	public synchronized static void setCount(int ncount) {
		RefCount.count = ncount;
	}
	public synchronized static int getCount() {
		Log.writeln("counter");
		Log.writeln(RefCount.count);
		return count;
	}
	public synchronized static void incCount(){
		count++;
	}
}
