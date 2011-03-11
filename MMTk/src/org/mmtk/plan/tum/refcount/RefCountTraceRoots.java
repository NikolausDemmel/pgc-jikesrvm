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
import org.mmtk.policy.ExplicitFreeListSpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.Log;
import org.mmtk.utility.deque.ObjectReferenceDeque;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

import com.sun.xml.internal.bind.v2.schemagen.xmlschema.ExplicitGroup;
//
///**
// * This class implements the thread-local functionality for a transitive
// * closure over a mark-sweep space.
// */
@Uninterruptible
public final class RefCountTraceRoots extends TraceLocal {
	/****************************************************************************
	 * Instance fields
	 */
	/**
	 * Constructor
	 */
	public RefCountTraceRoots(Trace trace, ObjectReferenceDeque modBuffer) {
		super(trace);
		//		super(RefCount.SCAN_MARK, trace);
		//		super(-1,trace);
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
	

	@Inline
	@Override
	public ObjectReference traceObject(ObjectReference object, boolean root) {
		Log.writeln("TraceObject: "+object+" "+root);
		if (root)
			scanObject(object);
		else {
		int pos = RefCountCollector.getWrapper().indexOf(object);
		if (pos >= 0) {
			Log.writeln("unFree");
			ExplicitFreeListSpace.testAndSetLiveBit(object);
		}
		}
		
		return object;
		
		/*if(object.isNull()||object==null)
			return super.traceObject(object,root);
		int pos = RefCountCollector.getWrapper().indexOf(object);
		Log.write(object);
		Log.write(" ");
		Log.writeln(root);

		//		if(!root){
		//			Log.write("freeing object ");
		//			Log.writeln(object);
		//			RefCount.rcSpace.free(object);
		//		}
		if(pos>=0){
			//			RefCount.rcSpace.free(object);
			// revive root ZCT nodes
			ObjectReference ref = RefCountCollector.getWrapper().get(pos);
			ExplicitFreeListSpace.testAndSetLiveBit(ref);
			Log.writeln("found object in ZCT");
		}
		//if(root&&object!=null)
		//	VM.scanning.scanObject(this, object);
		return object;*/
	}

}
final class RefCountSweeperClosure extends org.mmtk.plan.TransitiveClosure{
	@Override
	public void processEdge(ObjectReference source, Address slot) {
		ObjectReference obj = slot.loadObjectReference();
		Log.write("-");
		RefCount.rcSpace.free(source);
		if(!obj.isNull()){
			Log.writeln(".");
			RefCount.rcSpace.free(obj);
//			VM.scanning.scanObject(this, obj);
		}
	}
}
@Uninterruptible
final class RefCountSweeper extends ExplicitFreeListSpace.Sweeper {

	private final RefCountSweeperClosure closure = new RefCountSweeperClosure();

	public boolean sweepCell(ObjectReference object) {
		if (Space.isInSpace(RefCount.RC_DESC, object)&&RefCountHeader.isLiveRC(object)){//||RefCountCollector.getWrapper().contains(object)){
			Log.write("sweep object ");
			Log.writeln(object);
			VM.scanning.scanObject(closure, object);
			return true;
		}
		Log.writeln(RefCount.freeMemory().toInt()/(1024f*1024f));

		return false;
	}
}
