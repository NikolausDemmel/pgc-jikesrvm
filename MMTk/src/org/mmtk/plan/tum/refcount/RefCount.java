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

import java.util.HashSet;

import org.mmtk.plan.Phase;
import org.mmtk.plan.StopTheWorld;
import org.mmtk.plan.Trace;
import org.mmtk.plan.TransitiveClosure;
import org.mmtk.policy.ExplicitFreeListSpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.heap.VMRequest;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.ObjectReference;


@Uninterruptible
public class RefCount extends StopTheWorld {

	/****************************************************************************
	 * Class variables
	 */
	public static final ExplicitFreeListSpace rcSpace = new ExplicitFreeListSpace("rc", VMRequest.create());

	public static HashSet<ObjectReference> ZCT = new HashSet<ObjectReference>();

	public static final int RC_DESC = rcSpace.getDescriptor();
	public static final int SCAN_MARK = 0;


	public final Trace refCountTrace = new Trace(metaDataSpace);

	public static final short RefCountCollect = Phase.createComplex("rc", null,
			Phase.scheduleComplex(initPhase),Phase.scheduleComplex(rootClosurePhase),Phase.scheduleComplex(completeClosurePhase),Phase.scheduleComplex(finishPhase));


	/****************************************************************************
	 * Instance variables
	 */
	public final Trace rcTrace = new Trace(metaDataSpace);


	/*****************************************************************************
	 * Collection
	 */
	public static final boolean isRefCountObject(ObjectReference object) {
		return !object.isNull();// && !Space.isInSpace(RC_DESC, object);
	}

	public Trace rootTrace;
	/**
	 * Perform a (global) collection phase.
	 *
	 * @param phaseId Collection phase to execute.
	 */
	@Inline
	@Override
	public void collectionPhase(short phaseId) {
		System.out.println("RefCount.collectionPhase("+phaseId+")");
		if (phaseId == PREPARE) {
			System.out.println("PREPARE");
			super.collectionPhase(phaseId);
			rcTrace.prepare();
			rcSpace.prepare();
			return;
		}

		if (phaseId == CLOSURE) {
			System.out.println("CLOSURE");
			rcTrace.prepare();
			return;
		}
		if (phaseId == RELEASE) {
			System.out.println("RELEASE");
			rcTrace.release();
			rcSpace.release();
			super.collectionPhase(phaseId);
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

	/**
	 * Register specialized methods.
	 */
	@Interruptible
	@Override
	protected void registerSpecializedMethods() {
		TransitiveClosure.registerSpecializedScan(SCAN_MARK, RefCountTraceLocal.class);
		super.registerSpecializedMethods();
	}
}
