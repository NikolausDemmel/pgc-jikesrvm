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

import org.mmtk.policy.SegregatedFreeListSpace;

import org.mmtk.plan.StopTheWorldConstraints;

import org.vmmagic.pragma.*;

/**
 * This class and its subclasses communicate to the host VM/Runtime
 * any features of the selected plan that it needs to know.  This is
 * separate from the main Plan/PlanLocal class in order to bypass any
 * issues with ordering of static initialization.
 */
@Uninterruptible
public class RefCountConstraints extends StopTheWorldConstraints {
  
  @Override
  public int gcHeaderBits() { return RefCountHeader.GLOBAL_GC_BITS_REQUIRED; }
  @Override
  public int gcHeaderWords() { return RefCountHeader.GC_HEADER_WORDS_REQUIRED; }
  @Override
  public int maxNonLOSDefaultAllocBytes() { return SegregatedFreeListSpace.MAX_FREELIST_OBJECT_BYTES; }
  
//  @Override
//  public int numSpecializedScans() { return 1; }
  
  @Override
  public boolean needsObjectReferenceWriteBarrier() { return true; }
  @Override
  public boolean needsObjectReferenceNonHeapWriteBarrier() { return true;}

  // just to make sure they are not called:
  @Override
  public boolean needsAddressWriteBarrier() { return true; }
  @Override
  public boolean needsWordWriteBarrier() { return true; }
}
