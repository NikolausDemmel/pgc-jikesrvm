/////*
//// *  This file is part of the Jikes RVM project (http://jikesrvm.org).
//// *
//// *  This file is licensed to You under the Eclipse Public License (EPL);
//// *  You may not use this file except in compliance with the License. You
//// *  may obtain a copy of the License at
//// *
//// *      http://www.opensource.org/licenses/eclipse-1.0.php
//// *
//// *  See the COPYRIGHT.txt file distributed with this work for information
//// *  regarding copyright ownership.
//// */
//package org.mmtk.plan.tum.refcount;
//
////
//import org.mmtk.plan.Trace;
//import org.mmtk.plan.TraceLocal;
//import org.mmtk.policy.Space;
//import org.mmtk.utility.Log;
//import org.mmtk.utility.deque.ObjectReferenceDeque;
//import org.vmmagic.pragma.Inline;
//import org.vmmagic.pragma.Uninterruptible;
//import org.vmmagic.unboxed.Address;
//import org.vmmagic.unboxed.ObjectReference;
//import org.mmtk.policy.ExplicitFreeListSpace;
//import org.mmtk.vm.*;
//
//import com.sun.xml.internal.bind.v2.schemagen.xmlschema.ExplicitGroup;
//
////
/////**
//// * This class implements the thread-local functionality for a transitive
//// * closure over a mark-sweep space.
//// */
//@Uninterruptible
//public final class RefCountTraceLocal extends TraceLocal {
//	/****************************************************************************
//	 * Instance fields
//	 */
//	/**
//	 * Constructor
//	 */
//	public RefCountTraceLocal(Trace trace, ObjectReferenceDeque modBuffer) {
//		super(trace);
//		// super(RefCount.SCAN_MARK, trace);
//		// super(-1,trace);
//	}
//
//	//
//	//
//	// /****************************************************************************
//	// * Externally visible Object processing and tracing
//	// */
//	//
//	// /**
//	// * Is the specified object live?
//	// *
//	// * @param object The object.
//	// * @return <code>true</code> if the object is live.
//	// */
//	@Override
//	public boolean isLive(ObjectReference object) {
//		if (object.isNull())
//			return false;
//		if (Space.isInSpace(RefCount.RC_DESC, object)) {
//			return RefCount.rcSpace.isLive(object);
//		}
//		return super.isLive(object);
//	}
//
//	@Inline
//	@Override
//	public ObjectReference traceObject(ObjectReference object, boolean root) {
//
//		Log.write(object);
//		Log.write(" ");
//		Log.writeln(root);
//		if (root)
//			VM.scanning.scanObject(this, object);
//		else {
//			if(RefCountCollector.getWrapper().contains(object));
//			ExplicitFreeListSpace.unsyncSetLiveBit(object);
//		}
//		return super.traceObject(object, root);
//	}
//
//	@Inline
//	@Override
//	public ObjectReference traceObject(ObjectReference object) {
//		// logMessage(1,"RefCountTraceLocal.traceObject("+object+")\n\tisInSpace:\t"+Space.isInSpace(RefCount.RC_DESC,
//		// object)+"\n\tisNull:\t"+object.isNull());
//
//		// logMessage(3,"##################");
//		if (object.isNull())
//			return object;
//		if (Space.isInSpace(RefCount.RC_DESC, object)) {
//			// RefCount.rcSpace.free(object);
//			return RefCount.rcSpace.traceObject(this, object);
//		}
//		return super.traceObject(object);
//	}
//}