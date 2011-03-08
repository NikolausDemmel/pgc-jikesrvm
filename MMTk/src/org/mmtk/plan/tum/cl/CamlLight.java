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
package org.mmtk.plan.tum.cl;

import org.mmtk.plan.*;
import org.mmtk.policy.MarkSweepSpace;
import org.mmtk.policy.ExplicitFreeListSpace;
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
public class CamlLight extends StopTheWorld {

  /*****************************************************************************
   * Class variables
   */
  public static final MarkSweepSpace msSpace = new MarkSweepSpace("ms", VMRequest.create());
  public static final ExplicitFreeListSpace camlSpace = new ExplicitFreeListSpace ("cs", VMRequest.create());
  public static final int MS = msSpace.getDescriptor();
  public static final int CS = camlSpace.getDescriptor();


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

    if (phaseId == PREPARE) {
      super.collectionPhase(phaseId);
      msTrace.prepare();
      msSpace.prepare(true);
      return;
    }
    if (phaseId == CLOSURE) {
      msTrace.prepare();
      return;
    }
    if (phaseId == RELEASE) {
      msTrace.release();
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
    return (msSpace.reservedPages() + camlSpace.reservedPages() + super.getPagesUsed());
  }

  @Override
  public boolean willNeverMove(ObjectReference object) {
    if (Space.isInSpace(MS, object))
      return true;
    if (Space.isInSpace(CS, object))
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
  }
}
