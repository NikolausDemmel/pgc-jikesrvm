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
package org.mmtk.plan.tum.camlrc.main;

import org.mmtk.plan.StopTheWorldMutator;
import org.mmtk.plan.refcount.backuptrace.BTSweepImmortalScanner;
import org.mmtk.policy.ExplicitFreeListLocal;
import org.mmtk.policy.ExplicitFreeListSpace;
import org.mmtk.policy.LargeObjectLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.Log;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.deque.ObjectReferenceDeque;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements the mutator context for a simple reference counting collector.
 */
@Uninterruptible
public class RCBaseMutator extends StopTheWorldMutator {

  /************************************************************************
   * Instance fields
   */
  private final ExplicitFreeListLocal rc;
  //private final ObjectReferenceDeque modBuffer;
  //private final RCDecBuffer decBuffer;
  private final ObjectReferenceDeque decBuffer;
  //private final BTSweepImmortalScanner btSweepImmortal;

  /************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor. One instance is created per physical processor.
   */
  public RCBaseMutator() {
    rc = new ExplicitFreeListLocal(RCBase.rcSpace);
    //modBuffer = new ObjectReferenceDeque("mod", global().modPool);
    //decBuffer = new RCDecBuffer(global().decPool);
    decBuffer = new ObjectReferenceDeque("dec", global().decPool);
    //btSweepImmortal = new BTSweepImmortalScanner();
  }

  /****************************************************************************
   *
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
  public Address alloc(int bytes, int align, int offset, int allocator, int site) {
    switch (allocator) {
      case RCBase.ALLOC_DEFAULT:
      //case RCBase.ALLOC_NON_MOVING:
      //case RCBase.ALLOC_CODE:
        return rc.alloc(bytes, align, offset);
      //case RCBase.ALLOC_IMMORTAL:
        //return super.alloc(bytes, align, offset, allocator, site);
      default:
        //VM.assertions.fail("Allocator not understood by RC");
        //return Address.zero();
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
  public void postAlloc(ObjectReference ref, ObjectReference typeRef, int bytes, int allocator) {
    switch (allocator) {
    case RCBase.ALLOC_DEFAULT:
    //case RCBase.ALLOC_NON_MOVING:
      //modBuffer.push(ref);
    //case RCBase.ALLOC_CODE:
      //decBuffer.push(ref);
      RCHeader.initializeHeader(ref, false);
      ExplicitFreeListSpace.unsyncSetLiveBit(ref);
      return;
    /*case RCBase.ALLOC_IMMORTAL:
      modBuffer.push(ref);
      decBuffer.push(ref);
      RCHeader.initializeHeader(ref, true);
      return;
    default:
      VM.assertions.fail("Allocator not understood by RC");
      return;*/
    }
    
    super.postAlloc(ref, typeRef, bytes, allocator);
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
  public Allocator getAllocatorFromSpace(Space space) {
    if (space == RCBase.rcSpace) return rc;

    return super.getAllocatorFromSpace(space);
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a per-mutator collection phase.
   *
   * @param phaseId The collection phase to perform
   * @param primary perform any single-threaded local activities.
   */
  public void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == RCBase.PREPARE) {
      rc.prepare();
      return;
    }

    /*if (phaseId == RCBase.PROCESS_MODBUFFER) {
      modBuffer.flushLocal();
      return;
    }*/

    if (phaseId == RCBase.PROCESS_DECBUFFER) {
      decBuffer.flushLocal();
      return;
    }

    if (phaseId == RCBase.RELEASE) {
      rc.release();
      //if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(modBuffer.isEmpty());
      //if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(decBuffer.isEmpty());
      return;
    }

    super.collectionPhase(phaseId, primary);
  }

  /**
   * Flush per-mutator remembered sets into the global remset pool.
   */
  public final void flushRememberedSets() {
    decBuffer.flushLocal();
    //modBuffer.flushLocal();
    assertRemsetsFlushed();
  }

  /**
   * Assert that the remsets have been flushed.  This is critical to
   * correctness.  We need to maintain the invariant that remset entries
   * do not accrue during GC.  If the host JVM generates barrier entires
   * it is its own responsibility to ensure that they are flushed before
   * returning to MMTk.
   */
  public final void assertRemsetsFlushed() {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(decBuffer.isFlushed());
      //VM.assertions._assert(modBuffer.isFlushed());
    }
  }

  /**
   * Flush mutator context, in response to a requestMutatorFlush.
   * Also called by the default implementation of deinitMutator.
   */
  @Override
  public void flush() {
    super.flush();
    rc.flush();
  }

  /****************************************************************************
   *
   * Write barriers.
   */

  /**
   * A new reference is about to be created. Take appropriate write
   * barrier actions.<p>
   *
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param tgt The target of the new reference
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  @Inline
  public void objectReferenceWrite(ObjectReference src, Address slot,
                           ObjectReference tgt, Word metaDataA,
                           Word metaDataB, int mode) {
	Log.writeln("objectReferenceWrite");
    //if (RCHeader.logRequired(src)) {
    ObjectReference old = slot.loadObjectReference();
	deleteRef(old);
	addRef(tgt);
    VM.barriers.objectReferenceWrite(src,tgt,metaDataA, metaDataB, mode);
  }

  /**
   * Attempt to atomically exchange the value in the given slot
   * with the passed replacement value. If a new reference is
   * created, we must then take appropriate write barrier actions.<p>
   *
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param old The old reference to be swapped out
   * @param tgt The target of the new reference
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occured
   * @return True if the swap was successful.
   */
  @Inline
  public boolean objectReferenceTryCompareAndSwap(ObjectReference src, Address slot,
                                               ObjectReference old, ObjectReference tgt, Word metaDataA,
                                               Word metaDataB, int mode) {
	Log.writeln("objectReferenceTryCompareAndSwap");
    //if (RCHeader.logRequired(src)) {
      //coalescingWriteBarrierSlow(src);
    deleteRef(old);
    addRef(tgt);
    //}
    return VM.barriers.objectReferenceTryCompareAndSwap(src,old,tgt,metaDataA,metaDataB,mode);
  }

  /**
   * A number of references are about to be copied from object
   * <code>src</code> to object <code>dst</code> (as in an array
   * copy).  Thus, <code>dst</code> is the mutated object.  Take
   * appropriate write barrier actions.<p>
   *
   * @param src The source of the values to be copied
   * @param srcOffset The offset of the first source address, in
   * bytes, relative to <code>src</code> (in principle, this could be
   * negative).
   * @param dst The mutated object, i.e. the destination of the copy.
   * @param dstOffset The offset of the first destination address, in
   * bytes relative to <code>tgt</code> (in principle, this could be
   * negative).
   * @param bytes The size of the region being copied, in bytes.
   * @return True if the update was performed by the barrier, false if
   * left to the caller (always false in this case).
   */
  @Inline
  public boolean objectReferenceBulkCopy(ObjectReference src, Offset srcOffset,
                              ObjectReference dst, Offset dstOffset, int bytes) {
	Log.writeln("objectReferenceBulkCopy");
	Address srcCursor = src.toAddress().plus(srcOffset);
	Address dstCursor = dst.toAddress().plus(dstOffset);
    Address limit = srcCursor.plus(bytes);
    while (srcCursor.LT(limit)) {
      ObjectReference ref = srcCursor.loadObjectReference();
      addRef(ref);
      ref = dstCursor.loadObjectReference();
      deleteRef(ref);
      srcCursor = srcCursor.plus(BYTES_IN_ADDRESS);
    }
//    if (RCHeader.logRequired(dst)) {
//      coalescingWriteBarrierSlow(dst);
//    }
    return false;
  }
  
  @Inline
  public void objectReferenceNonHeapWrite(Address slot, ObjectReference tgt, Word metaDataA, Word metaDataB) {
	  Log.writeln("objectReferenceNonHeapWrite");
	  ObjectReference old = slot.loadObjectReference();
	  deleteRef(old);
	  addRef(tgt);
	  
	  VM.barriers.objectReferenceNonHeapWrite(slot, tgt, metaDataA, metaDataB);
  }
  
  @Inline
  private static void deleteRef(ObjectReference old) {
		if (old != null && RCBase.isRCObject(old)) {
		      //coalescingWriteBarrierSlow(src);
		      if (RCHeader.decRC(old) == RCHeader.DEC_KILL) {
		    	  Log.write(RCHeader.getRC(old));
		    	  Log.writeln(" -> delete object");
		    	  //RCBase.rcSpace.free(old);
		    	  //RCBase.rcSpace.release();
		    	  //decBuffer.push(old);
		      }
			}
  }
  
  @Inline
  private static void addRef(ObjectReference tgt) {
	  if (tgt != null && RCBase.isRCObject(tgt)) {
		  RCHeader.incRC(tgt);
	  }
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /** @return The active global plan as an <code>RC</code> instance. */
  @Inline
  private static RCBase global() {
    return (RCBase) VM.activePlan.global();
  }
}
