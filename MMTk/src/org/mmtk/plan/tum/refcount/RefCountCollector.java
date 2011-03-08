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

import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.Phase;
import org.mmtk.plan.StopTheWorldCollector;
import org.mmtk.plan.TraceLocal;
import org.mmtk.utility.Log;
import org.mmtk.utility.deque.AddressPairDeque;
import org.mmtk.utility.deque.ObjectReferenceDeque;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.ObjectReference;

/**
 * This class implements <i>per-collector thread</i> behavior
 * and state for the <i>MS</i> plan, which implements a full-heap
 * mark-sweep collector.<p>
 *
 * Specifically, this class defines <i>MS</i> collection behavior
 * (through <code>trace</code> and the <code>collectionPhase</code>
 * method).<p>
 *
 * @see RefCount for an overview of the mark-sweep algorithm.<p>
 *
 * @see RefCount
 * @see RefCountMutator
 * @see StopTheWorldCollector
 * @see CollectorContext
 */
@Uninterruptible
public class RefCountCollector extends StopTheWorldCollector {
	protected final ObjectReferenceDeque ZCT = new ObjectReferenceDeque("zct", global().zcts);
	  protected final AddressPairDeque refTable = new AddressPairDeque(global().refs);


	//	/****************************************************************************
	//	 * Instance fields
	//	 */
	protected RefCountTraceLocal rctl = new RefCountTraceLocal(global().refCountTrace, null);;
	protected TraceLocal currentTrace = rctl;
	//
	//
	//	/****************************************************************************
	//	 * Collection
	//	 */
	//
	//	/**
	//	 * Perform a per-collector collection phase.
	//	 *
	//	 * @param phaseId The collection phase to perform
	//	 * @param primary Perform any single-threaded activities using this thread.
	//	 */
	@Inline
	@Override
	public void collectionPhase(short phaseId, boolean primary) {
//		Log.writeln("RefCountCollector.collectionPhase("+phaseId+", "+primary+")");
		if (phaseId == RefCount.PREPARE) {
//			RefCount.rcSpace.prepare();
//				for(ObjectReference obj : RefCount.ZCT){
//					RefCount.rcSpace.free(obj);
////					System.out.print(".");
//
//				}
//				RefCount.rcSpace.release();
//			Log.writeln("PREPARE");
			super.collectionPhase(phaseId, primary);
			rctl.prepare();
			return;
		}
		if(phaseId == RefCount.STACK_ROOTS){
//			Log.writeln("STACK_ROOTS\t"+RefCount.freeMemory());
		}
		if (phaseId == RefCount.CLOSURE) {
//			Log.writeln("CLOSURE\t"+RefCount.freeMemory());
			//						rctl.completeTrace();
			return;
		}

		if (phaseId == RefCount.RELEASE) {

//			Log.writeln("RELEASE\t"+RefCount.freeMemory());
			rctl.release();
			super.collectionPhase(phaseId, primary);
			return;
		}
		Log.writeln(RefCount.freeMemory().toInt()/(1024f*1024f));

		super.collectionPhase(phaseId, primary);
	}
	//
	//
	//	/****************************************************************************
	//	 * Miscellaneous
	//	 */
	public RefCountCollector(){
		//	rootTrace = new RCFindRootSetTraceLocal(global().rootTrace, newRootBuffer);
		//enwRootBuffer = new ObjectReferenceDeque("new-root",global().newRootPool());
	}

	@Override
	public void collect() {
		Phase.beginNewPhaseStack(Phase.scheduleComplex(global().collection));
		//		super.collect();
	}
	//
	//
	//	/** @return The active global plan as an <code>MS</code> instance. */
	@Inline
	private static RefCount global() {
		return (RefCount) VM.activePlan.global();
	}

	/** @return The current trace instance. */
	@Override
	public final TraceLocal getCurrentTrace() {
		return currentTrace;
	}
}
