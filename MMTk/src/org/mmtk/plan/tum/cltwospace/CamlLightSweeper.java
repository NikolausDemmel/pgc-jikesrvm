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

import org.mmtk.policy.ExplicitFreeListSpace;
import org.mmtk.utility.Log;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

// Sweeper for lifting assumption 4)

/**
 * This class implements the thread-local core functionality for a transitive
 * closure over the heap graph.
 */
@Uninterruptible
public final class CamlLightSweeper extends ExplicitFreeListSpace.Sweeper {

  private final CamlLightTrace clt = new CamlLightTrace();
  
  /**
   * @return True if cell should be freed (only relevant if free was false).
   */
  public boolean sweepCell(ObjectReference object, boolean free) {
    Log.writeln("sweepCell");   
    if (!free) {
      return false;
    } else {
      // Cell is free. We need to scan for references. If they point to a
      // reference counted object, we must make sure the RC is decremented.
      if (!object.isNull()) {
        VM.scanning.scanObject(clt, object);
      }
      return false;
    }

  }
}
