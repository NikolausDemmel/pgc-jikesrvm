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
package org.mmtk.plan.tum.cltwospace;

import org.mmtk.plan.*;
import org.mmtk.policy.MarkSweepSpace;
import org.mmtk.policy.ExplicitFreeListSpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.heap.VMRequest;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.ObjectReference;

/**
 * The CamlLight plan is intended to implement a garbage collector that is 
 * specifically designed for programs produced by the CamlLight to bytecode compiler.
 * 
 * To implement fast garbage collection, we have two heap spaces. The first space is
 * collected with a mark and sweep scanner while the sencond is collected using simple 
 * reference counting. We call these spaces ms-space and caml-space. 
 * 
 * Allocation looks at the type (class) of an object to be allocated. Only CamlHeap Objects
 * are allocated on the caml-space, everything else is allocated on the ms-space. The idea is
 * that due to the structure of purely functional progams we know for sure that there will be
 * no cycles on the caml-space. Thus rc works without fancy cycle detection. Also due to the locality
 * reference counting we hope to improve performance of parallel mutators.
 * 
 * The following assumptions are made:
 *   
 * 1) CamlHeap objects form no cycles
 * 2) CamlHeap objects have no (strong) pointers back into the ms-space
 * 3) As long as the Caml Heap object is live, it is pointed to from the ms-space, or it
 *    it reachable from a caml heap object which is pointed to from the ms-space
 * 4) All references from the ms-space to the caml-space are explicitly overwritten by another pointer
 *    or by null, before they become dead.
 *    
 * The idea is that the caml light compiler enforces these assumptions and there are no restrictions
 * on the programs it can successfully translate.
 * 
 * Some notes on the assumptions:
 * 
 * 1) cycles are not a theoretical problem, but cycle detection is expensive. Also functions programs
 *    make it easy to enforce this.
 * 2) A problem occurs when an object in the ms-space is only reachable from the caml-space. Since the 
 *    ms collection starts from the root node, we would need to include thoose objects in the root set.
 *    We could lift this restriction by implementing a "remembered" set like in generational gc. This set
 *    records all nodes in the ms-space that are reachable from the caml-space. A reference count in the
 *    ms-space objects can help to determine when to remove an object from the rem-set. The remembered
 *    set is then included in the root set of the ms-space.
 * 3) This restriciton makes it possible to only implement a write barrier for object references in the heap.
 *    We dont need to watch and handle any references from the stack. Firstly write barriers on the stack
 *    are very expensive. Also MMTk does not fully support / implement them.
 * 4) We need to notice when a reference to a caml-space object becomes dead, since we need to decrement the
 *    reference count. Since for the mark sweep the dead cells are simply those that are not reached during
 *    the mark phase, dead cells are not neccessarily touched. It would be possible to lift this restriction
 *    if we explicitly deleted those references in dead objects during the sweep phase. MMTk seems to support
 *    this kind of operation, but it is also very expensive to touch every cell in the ms-space (dead and alive)
 *    during the sweep phase.
 *    
 * For the caml-space we could release memory as soon as an object reaches rc 0. However MMTk does not seem to
 * support this well. Instead we for now only flag objects the reached rc 0 to be not live. During a stop-the-world
 * collection phase the free cells are returned to the allocator. The idea would be to run the sweeps of the
 * caml-space more often that the mark and sweep on the ms-space. This would be beneficial since most garbage would
 * be caml-heap-objects for a typical caml light program.
 * 
 * The algorithm is implemented and works in harness as well as the real rvm. However the caml compiler does not yet
 * support generating programs that enfore the assumptions. Thus we implemented simple test cases for harness 
 * (CamlLightTest.script) and foobar.Main for real rvm.
 * 
 */

/**
 * This class implements the global state of a a simple allocator without a
 * collector.
 */
@Uninterruptible
public class CamlLight extends StopTheWorld {

  /*****************************************************************************
   * Class variables
   */
  public static final MarkSweepSpace msSpace = new MarkSweepSpace("ms",
      VMRequest.create());
  public static final int MS = msSpace.getDescriptor();
  public static final ExplicitFreeListSpace camlSpace = new ExplicitFreeListSpace(
      "cs", VMRequest.create());
  public static final int CS = camlSpace.getDescriptor();

  /*****************************************************************************
   * Instance variables
   */
  public final Trace msTrace = new Trace(metaDataSpace);
// Sweeper for lifting assumption 4)
//  public final CamlLightSweeper msSweeper = new CamlLightSweeper();

  /*****************************************************************************
   * Collection
   */

  public static final boolean isCamlLightObject(ObjectReference object) {
    return !object.isNull() && Space.isInSpace(CS, object);
  }

  public static final boolean isMarkSweepObject(ObjectReference object) {
    return !object.isNull() && Space.isInSpace(MS, object);
  }

  public static final boolean isRCRelevantObject(ObjectReference object) {
    return isCamlLightObject(object) || isMarkSweepObject(object);
  }

  /**
   * Perform a (global) collection phase.
   * 
   * @param phaseId
   *          Collection phase
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
      // Sweeper for lifting assumption 4)
      // msSpace.sweepCells(msSweeper);
      camlSpace.release();
      super.collectionPhase(phaseId);
      return;
    }

    super.collectionPhase(phaseId);

  }

  /*****************************************************************************
   * Accounting
   */

  /**
   * Return the number of pages reserved for use given the pending allocation.
   * The superclass accounts for its spaces, we just augment this with the
   * default space's contribution.
   * 
   * @return The number of pages reserved given the pending allocation,
   *         excluding space reserved for copying.
   */
  @Override
  public int getPagesUsed() {
    return (camlSpace.reservedPages() + msSpace.reservedPages() + super
        .getPagesUsed());
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
