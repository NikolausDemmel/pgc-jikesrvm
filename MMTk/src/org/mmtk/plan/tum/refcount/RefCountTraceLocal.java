///*
// *  This file is part of the Jikes RVM project (http://jikesrvm.org).
// *
// *  This file is licensed to You under the Eclipse Public License (EPL);
// *  You may not use this file except in compliance with the License. You
// *  may obtain a copy of the License at
// *
// *      http://www.opensource.org/licenses/eclipse-1.0.php
// *
// *  See the COPYRIGHT.txt file distributed with this work for information
// *  regarding copyright ownership.
// */
package org.mmtk.plan.tum.refcount;
//
import org.mmtk.plan.Trace;
import org.mmtk.plan.TraceLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.deque.ObjectReferenceDeque;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;
//
///**
// * This class implements the thread-local functionality for a transitive
// * closure over a mark-sweep space.
// */
@Uninterruptible
public final class RefCountTraceLocal extends TraceLocal {
	/****************************************************************************
	 * Instance fields
	 */
	private final ObjectReferenceDeque modBuffer;

	/**
	 * Constructor
	 */
	public RefCountTraceLocal(Trace trace, ObjectReferenceDeque modBuffer) {
		super(RefCount.SCAN_MARK, trace);
		this.modBuffer = modBuffer;
	}
	//
	//
	//  /****************************************************************************
	//   * Externally visible Object processing and tracing
	//   */
	//
	//  /**
	//   * Is the specified object live?
	//   *
	//   * @param object The object.
	//   * @return <code>true</code> if the object is live.
	//   */
	@Override
	public boolean isLive(ObjectReference object) {
		if (object.isNull()) return false;
		if (Space.isInSpace(RefCount.RC_DESC, object)) {
			return RefCount.rcSpace.isLive(object);
		}
		return super.isLive(object);
	}
	//
	//  /**
	//   * This method is the core method during the trace of the object graph.
	//   * The role of this method is to:
	//   *
	//   * 1. Ensure the traced object is not collected.
	//   * 2. If this is the first visit to the object enqueue it to be scanned.
	//   * 3. Return the forwarded reference to the object.
	//   *
	//   * In this instance, we refer objects in the mark-sweep space to the
	//   * msSpace for tracing, and defer to the superclass for all others.
	//   *
	//   * @param object The object to be traced.
	//   * @return The new reference to the same object instance.
	//   */
	@Inline
	@Override
	public ObjectReference traceObject(ObjectReference object) {
//		logMessage(1,"RefCountTraceLocal.traceObject("+object+")\n\tisInSpace:\t"+Space.isInSpace(RefCount.RC_DESC, object)+"\n\tisNull:\t"+object.isNull());
		
//		logMessage(3,"##################");
		if (object.isNull()) return object;
		if (Space.isInSpace(RefCount.RC_DESC, object)){
//			RefCount.rcSpace.free(object);
			return RefCount.rcSpace.traceObject(this, object);
		}
		return super.traceObject(object);
	}
//	@Override
//	public void processRoots() {
//		logMessage(3,"processRoots()");
//		Address ref = rootLocations.pop();
//		logMessage(3, (RefCount.refTable.containsKey(ref.toString()))?"true":"false");
//		super.processRoots();
//	}
	
	//
	//  /**
	//   * Process any remembered set entries.  This means enumerating the
	//   * mod buffer and for each entry, marking the object as unlogged
	//   * (we don't enqueue for scanning since we're doing a full heap GC).
	//   */
//	protected void processRememberedSets() {
//		logMessage(1,"RefCountTraceLocal.processRememberedSets()");
//		if (modBuffer != null) {
//			logMessage(5, "clearing modBuffer");
//			while (!modBuffer.isEmpty()) {
//				ObjectReference src = modBuffer.pop();
//				logMessage(1,src);
//				HeaderByte.markAsUnlogged(src);
//			}
//		}
//	}
	//
}