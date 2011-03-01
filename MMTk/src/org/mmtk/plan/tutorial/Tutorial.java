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
package org.mmtk.plan.tutorial;

import org.mmtk.plan.*;
import org.mmtk.policy.CopySpace;
import org.mmtk.policy.MarkSweepSpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.heap.VMRequest;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.ObjectReference;


/**
 * This class implements the global state of a a simple allocator
 * without a collector.
 */
@Uninterruptible
public class Tutorial extends StopTheWorld {

  /*****************************************************************************
   * Class variables
   */
  public static final MarkSweepSpace msSpace = new MarkSweepSpace("mark-sweep", VMRequest.create());
  public static final int MARK_SWEEP = msSpace.getDescriptor();
  public static final CopySpace nurserySpace = new CopySpace("nursery", false, VMRequest.create(0.15f, true));
  public static final int NURSERY = nurserySpace.getDescriptor();
  
  public static final int SCAN_MARK = 0;


  /*****************************************************************************
   * Instance variables
   */
  public final Trace msTrace = new Trace(metaDataSpace);


  /*****************************************************************************
   * Collection
   */

  /**
   * Perform a (global) collection phase.
   *
   * @param phaseId Collection phase
   */
  @Inline
  @Override
  public final void collectionPhase(short phaseId) {
    //if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    
    if (phaseId == PREPARE) {
    	super.collectionPhase(phaseId);
    	msTrace.prepare();
    	nurserySpace.prepare(true);
    	msSpace.prepare(true);
    	return;
    }
    if (phaseId == CLOSURE) {
    	msTrace.prepare();
    	return;
    }
    if (phaseId == RELEASE) {
    	msTrace.release();
    	nurserySpace.release();
    	msSpace.release();
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
   * augment this with the default space's contribution.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */
  @Override
  public int getPagesUsed() {
    return (msSpace.reservedPages() + nurserySpace.reservedPages() + super.getPagesUsed());
  }
  
  
  @Override
  public int getCollectionReserve() {
	  return nurserySpace.reservedPages() + super.getCollectionReserve();
  }
  
  @Override
  public int getPagesAvail() {
	  return super.getPagesAvail() / 2;
  }
  
  
  @Override
  public boolean willNeverMove(ObjectReference object) {
	  if (Space.isInSpace(MARK_SWEEP, object))
		  return true;
	  return super.willNeverMove(object);
  }


  /*****************************************************************************
   * Miscellaneous
   */

  /**
   * Register specialized methods.
   */
  @Interruptible
  @Override
  protected void registerSpecializedMethods() {
    super.registerSpecializedMethods();
    TransitiveClosure.registerSpecializedScan(SCAN_MARK, TutorialTraceLocal.class);
  }
}
