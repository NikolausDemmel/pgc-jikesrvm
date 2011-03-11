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

import org.mmtk.plan.StopTheWorldConstraints;
import org.mmtk.plan.tum.cl.CamlLightHeader;
import org.mmtk.policy.MarkSweepSpace;
import org.mmtk.policy.SegregatedFreeListSpace;

import org.vmmagic.pragma.*;

/**
 * This class and its subclasses communicate to the host VM/Runtime
 * any features of the selected plan that it needs to know.  This is
 * separate from the main Plan/PlanLocal class in order to bypass any
 * issues with ordering of static initialization.
 */
@Uninterruptible
public class CamlLightConstraints extends StopTheWorldConstraints {
  
  @Inline
  private int max(int a, int b) {
    if (a > b)
      return a;
    else
      return b;
  }
  
  @Override
  public int gcHeaderBits() { return max(MarkSweepSpace.LOCAL_GC_BITS_REQUIRED,
                                         CamlLightHeader.GLOBAL_GC_BITS_REQUIRED); }
  @Override
  public int gcHeaderWords() { return max(MarkSweepSpace.GC_HEADER_WORDS_REQUIRED,
                                          CamlLightHeader.GC_HEADER_WORDS_REQUIRED); }
  @Override
  public int maxNonLOSDefaultAllocBytes() { return SegregatedFreeListSpace.MAX_FREELIST_OBJECT_BYTES; }
  
  @Override
  public boolean needsObjectReferenceWriteBarrier() { return true; }
  
// non heap write barrier not needed / used, since it is not fully implemented.
//  @Override
//  public boolean needsObjectReferenceNonHeapWriteBarrier() { return true;}
  
}
