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

import org.mmtk.plan.TransitiveClosure;
import org.mmtk.utility.Log;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class is the fundamental mechanism for performing a
 * transitive closure over an object graph.<p>
 *
 * @see org.mmtk.plan.TraceLocal
 */
@Uninterruptible
public final class CamlLightTrace extends TransitiveClosure {

  /**
   * Trace an edge during GC.
   *
   * @param source The source of the reference.
   * @param slot The location containing the object reference.
   */
  @Inline
  public void processEdge(ObjectReference source, Address slot) {
    ObjectReference obj = slot.loadObjectReference();
    
    Log.write("pE: ");
    Log.write(source);
    Log.write(" ");
    Log.writeln(slot);
    
    if (obj != null && !obj.isNull() && CamlLight.isRefCountObject(obj)) {
      CamlLightMutator.delete(obj);
    }
  }
}

