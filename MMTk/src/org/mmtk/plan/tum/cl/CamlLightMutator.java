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

import org.mmtk.plan.StopTheWorldMutator;
import org.mmtk.policy.MarkSweepLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.Log;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-mutator thread</i> behavior and state
 * for the <i>NoGC</i> plan, which simply allocates (without ever collecting
 * until the available space is exhausted.<p>
 *
 * Specifically, this class defines <i>NoGC</i> mutator-time allocation
 * through a bump pointer (<code>def</code>) and includes stubs for
 * per-mutator thread collection semantics (since there is no collection
 * in this plan, these remain just stubs).
 *
 * @see CamlLight
 * @see CamlLightCollector
 * @see org.mmtk.plan.StopTheWorldMutator
 * @see org.mmtk.plan.MutatorContext
 */
@Uninterruptible
public class CamlLightMutator extends StopTheWorldMutator {

  /************************************************************************
   * Instance fields
   */
  private final MarkSweepLocal ms = new MarkSweepLocal(CamlLight.msSpace);


  /****************************************************************************
   * Mutator-time allocation
   */

  /**
   * Allocate memory for an object.
   *
   * @param bytes The number of bytes required for the object.
   * @param align Required alignment for the object.
   * @param offset Offset associated with the alignment.
   * @param allocator The allocator associated with this request.
   * @param site Allocation site
   * @return The address of the newly allocated memory.
   */
  @Inline
  @Override
  public Address alloc(int bytes, int align, int offset, int allocator, int site) {
    if (allocator == CamlLight.ALLOC_DEFAULT) {
      //if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false, "foo");
      return ms.alloc(bytes, align, offset);
    }
    return super.alloc(bytes, align, offset, allocator, site);
  }

  /**
   * Perform post-allocation actions.  For many allocators none are
   * required.
   *
   * @param ref The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   * @param allocator The allocator number to be used for this allocation
   */
  @Inline
  @Override
  public void postAlloc(ObjectReference ref, ObjectReference typeRef,
      int bytes, int allocator) {
    if (allocator == CamlLight.ALLOC_DEFAULT) {
      CamlLight.msSpace.postAlloc(ref);
    } else {
      super.postAlloc(ref, typeRef, bytes, allocator);
    }
  }

  /**
   * Return the allocator instance associated with a space
   * <code>space</code>, for this plan instance.
   *
   * @param space The space for which the allocator instance is desired.
   * @return The allocator instance associated with this plan instance
   * which is allocating into <code>space</code>, or <code>null</code>
   * if no appropriate allocator can be established.
   */
  @Override
  public Allocator getAllocatorFromSpace(Space space) {
    if (space == CamlLight.msSpace) return ms;
    return super.getAllocatorFromSpace(space);
  }


  /****************************************************************************
   * Collection
   */

  /**
   * Perform a per-mutator collection phase.
   *
   * @param phaseId The collection phase to perform
   * @param primary perform any single-threaded local activities.
   */
  @Inline
  @Override
  public final void collectionPhase(short phaseId, boolean primary) {
    
     if (phaseId == CamlLight.PREPARE) {
       super.collectionPhase(phaseId, primary);
       ms.prepare();
       return;
     }

     if (phaseId == CamlLight.RELEASE) {
       ms.release();
       super.collectionPhase(phaseId, primary);
       return;
     }
     
     super.collectionPhase(phaseId, primary);

  }
  
  @Inline
  @Override
  public void objectReferenceWrite(ObjectReference src, Address slot,
                           ObjectReference tgt, Word metaDataA,
                           Word metaDataB, int mode) {
    Log.writeln("objectReferenceWrite");
    Log.writeln(src);
    Log.writeln(slot);
    Log.writeln(tgt);
    Log.writeln(metaDataA);
    Log.writeln(metaDataB);
    Log.writeln(mode);
    
    //Log.writeln("-> objectReferenceWrite [src: " + src + ", slot: " + slot + ", tgt: " + tgt + ", metaDataA: " + metaDataA + ", metaDataB: " + metaDataB + ", mode: " + mode);
//    if(Space.isInSpace(CamlLight.MS, src))
//      Log.writeln("-> objectReferenceWrite");

    VM.barriers.objectReferenceWrite(src,tgt,metaDataA, metaDataB, mode);
  }
  
  @Inline
  @Override
  public void objectReferenceNonHeapWrite(Address slot, ObjectReference tgt,
      Word metaDataA, Word metaDataB) {
    //Log.writeln("-> objectReferenceNonHeapWrite [slot: " + slot + ", tgt: " + tgt + ", metaDataA: " + metaDataA + ", metaDataB: " + metaDataB);
    Log.writeln("-> objectReferenceNonHeapWrite");
    Log.writeln(slot);
    Log.writeln(tgt);
    Log.writeln(metaDataA);
    Log.writeln(metaDataB);

    VM.barriers.objectReferenceNonHeapWrite(slot, tgt, metaDataA, metaDataB);
  }
  
}
