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

import org.mmtk.plan.StopTheWorldMutator;
import org.mmtk.policy.ExplicitFreeListLocal;
import org.mmtk.policy.ExplicitFreeListSpace;
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

  private static final CamlLightTrace clt = new CamlLightTrace();
  
  /************************************************************************
   * Instance fields
   */
//  private final MarkSweepLocal ms = new MarkSweepLocal(CamlLight.msSpace);
  private final ExplicitFreeListLocal cs = new ExplicitFreeListLocal(CamlLight.camlSpace);

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
      return cs.alloc(bytes, align, offset);
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

//      ExplicitFreeListSpace.unsyncSetLiveBit(ref);
//      CamlLightHeader.initializeHeader(ref, false);
      CamlLightHeader.initializeHeader(ref, false);

//      Log.prependThreadId();
      Log.write("postAlloc - ref: ");
      Log.writeln(ref);
//      Log.write(" typeRef: ");
//      Log.writeln(typeRef);
      
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
    if (space == CamlLight.camlSpace) return cs;
    return super.getAllocatorFromSpace(space);
  }


  /****************************************************************************
   * Collection
   */
  
  public static final void delete(ObjectReference obj) {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(CamlLight.isCamlLightObject(obj));
      //VM.assertions._assert(CamlLightHeader.isLiveRC(obj));
    }
    if(CamlLightHeader.getRC(obj) == CamlLightHeader.RC_ZERO) {
      Log.writeln("trying to delete object with 0 rc.");
    } else {
      if (CamlLightHeader.decRC(obj) == CamlLightHeader.RC_ZERO) {
        VM.scanning.scanObject(clt, obj);
        // CamlLight.camlSpace.free(obj);
      }
    }
  }

  /**
   * Perform a per-mutator collection phase.
   *
   * @param phaseId The collection phase to perform
   * @param primary perform any single-threaded local activities.
   */
  @Inline
  @Override
  public final void collectionPhase(short phaseId, boolean primary) {
    
//     if (phaseId == CamlLight.PREPARE) {
//       super.collectionPhase(phaseId, primary);
//       ms.prepare();
//       return;
//     }
//
//     if (phaseId == CamlLight.RELEASE) {
//       ms.release();
//       super.collectionPhase(phaseId, primary);
//       return;
//     }
     
     super.collectionPhase(phaseId, primary);

  }
  
  @Inline
  @Override  
  public void addressWrite(ObjectReference src, Address slot, Address value, Word metaDataA, Word metaDataB, int mode) {
    if (CamlLight.isCamlLightObject(slot.loadObjectReference()) || CamlLight.isCamlLightObject(value.toObjectReference())) {
      Address old = slot.loadAddress();
      Log.write("addressWrite - src: ");
      Log.write(src);
      Log.write(" slot: ");
      Log.write(slot);
      Log.write(" value: ");
      Log.write(value);
      Log.write(" old: ");
      Log.write(old);
      Log.write(" metaDataA: ");
      Log.write(metaDataA);
      Log.write(" metaDataB: ");
      Log.write(metaDataB);
      Log.write(" mode: ");
      Log.writeln(mode);
    }
    VM.barriers.addressWrite(src,value,metaDataA, metaDataB, mode);
  }
  
  public boolean addressTryCompareAndSwap(ObjectReference src, Address slot, Address old, Address value, Word metaDataA, Word metaDataB, int mode) {
    if (CamlLight.isCamlLightObject(old.toObjectReference()) || CamlLight.isCamlLightObject(value.toObjectReference())) {
      Log.write("addressTryCompareAndSwap - src: ");
      Log.write(src);
      Log.write(" slot: ");
      Log.write(slot);
      Log.write(" value: ");
      Log.write(value);
      Log.write(" old: ");
      Log.write(old);
      Log.write(" metaDataA: ");
      Log.write(metaDataA);
      Log.write(" metaDataB: ");
      Log.write(metaDataB);
      Log.write(" mode: ");
      Log.writeln(mode);
    }
    return VM.barriers.addressTryCompareAndSwap(src,old,value,metaDataA,metaDataB,mode);
  }

  @Inline
  @Override
  public void wordWrite(ObjectReference src, Address slot, Word value, Word metaDataA, Word metaDataB, int mode) {
    if (CamlLight.isCamlLightObject(slot.loadObjectReference()) || CamlLight.isCamlLightObject(value.toAddress().toObjectReference())) {
      Word old = slot.loadWord();
      Log.write("wordWrite - src: ");
      Log.write(src);
      Log.write(" slot: ");
      Log.write(slot);
      Log.write(" value: ");
      Log.write(value);
      Log.write(" old: ");
      Log.write(old);
      Log.write(" metaDataA: ");
      Log.write(metaDataA);
      Log.write(" metaDataB: ");
      Log.write(metaDataB);
      Log.write(" mode: ");
      Log.writeln(mode);
    }
    VM.barriers.wordWrite(src,value,metaDataA, metaDataB, mode);
  }
  
  public boolean wordTryCompareAndSwap(ObjectReference src, Address slot, Word old, Word value, Word metaDataA, Word metaDataB, int mode) {
    if (CamlLight.isCamlLightObject(old.toAddress().toObjectReference()) || CamlLight.isCamlLightObject(value.toAddress().toObjectReference())) {
      Log.write("wordTryCompareAndSwap - src: ");
      Log.write(src);
      Log.write(" slot: ");
      Log.write(slot);
      Log.write(" value: ");
      Log.write(value);
      Log.write(" old: ");
      Log.write(old);
      Log.write(" metaDataA: ");
      Log.write(metaDataA);
      Log.write(" metaDataB: ");
      Log.write(metaDataB);
      Log.write(" mode: ");
      Log.writeln(mode);
    }
    return VM.barriers.wordTryCompareAndSwap(src,old,value,metaDataA,metaDataB,mode);
  }
  
  @Inline
  @Override
  public void objectReferenceWrite(ObjectReference src, Address slot,
                           ObjectReference tgt, Word metaDataA,
                           Word metaDataB, int mode) {

    ObjectReference old = slot.loadObjectReference();

    writeBarrier(old, tgt, "objectReferenceWrite");

    VM.barriers.objectReferenceWrite(src,tgt,metaDataA, metaDataB, mode);
  }
  
  @Inline
  @Override
  public void objectReferenceNonHeapWrite(Address slot, ObjectReference tgt,
      Word metaDataA, Word metaDataB) {

    ObjectReference old = slot.loadObjectReference();

    writeBarrier(old, tgt, "objectReferenceNonHeapWrite");

    VM.barriers.objectReferenceNonHeapWrite(slot, tgt, metaDataA, metaDataB);
  }
  
  @Inline
  @Override
  public boolean objectReferenceTryCompareAndSwap(ObjectReference src, Address slot,
      ObjectReference old, ObjectReference tgt, Word metaDataA,
      Word metaDataB, int mode) {

//    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(old.equals(slot.loadObjectReference()));
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(old == slot.loadObjectReference());

    
    writeBarrier(old, tgt, "objectReferenceTryCompareAndSwap");
    // NOTE: This could lead to a race condition, where another thread changes the old value in between.
    return VM.barriers.objectReferenceTryCompareAndSwap(src,old,tgt,metaDataA,metaDataB,mode);
  }
  
  @Inline
  private void writeBarrier(ObjectReference old, ObjectReference tgt, String debug) {

    if (VM.VERIFY_ASSERTIONS) {
//      VM.assertions._assert(tgt != null, "tgt is null");
//      VM.assertions._assert(old != null, "old is null");
    }
    
    if (CamlLight.isCamlLightObject(tgt) || (old != null && CamlLight.isCamlLightObject(old))) {
      Log.write("writeBarrier - old: ");
      Log.write(old);
      Log.write(" tgt: ");
      Log.write(tgt);
      Log.write(" called from: ");
      Log.writeln(debug);
    }
    
    if (tgt == null) {
      Log.writeln("tgt is null");
    }
    
    if (tgt != null && CamlLight.isCamlLightObject(tgt)) {
      CamlLightHeader.incRC(tgt);
    }
    
    if (old == null) {
      Log.writeln("old is null");
    }

    if (old != null && CamlLight.isCamlLightObject(old)) {
      delete(old);
    }
  }

}
