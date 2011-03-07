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

import java.util.Hashtable;
import java.util.Random;

import org.mmtk.plan.StopTheWorldMutator;
import org.mmtk.policy.ExplicitFreeListLocal;
import org.mmtk.policy.ExplicitFreeListSpace;
import org.mmtk.policy.MarkSweepLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-mutator thread</i> behavior and state for the
 * <i>MS</i> plan, which implements a full-heap mark-sweep collector.
 * <p>
 * 
 * Specifically, this class defines <i>MS</i> mutator-time allocation and
 * per-mutator thread collection semantics (flushing and restoring per-mutator
 * allocator state).
 * <p>
 * 
 * @see RefCount
 * @see RefCountCollector
 * @see StopTheWorldMutator
 * @see MutatorContext
 */
@Uninterruptible
public class RefCountMutator extends StopTheWorldMutator {

  /****************************************************************************
   * Instance fields
   */
  protected ExplicitFreeListLocal freelist = new ExplicitFreeListLocal(
		  	RefCount.rcSpace);
	
  /****************************************************************************
   * Mutator-time allocation
   */

  /**
   * Allocate memory for an object. This class handles the default allocator
   * from the mark sweep space, and delegates everything else to the superclass.
   * 
   * @param bytes The number of bytes required for the object.
   * @param align Required alignment for the object.
   * @param offset Offset associated with the alignment.
   * @param allocator The allocator associated with this request.
   * @return The low address of the allocated memory.
   */
  @Inline
  @Override
  public Address alloc(int bytes, int align, int offset, int allocator, int site) {
    if (allocator == RefCount.ALLOC_DEFAULT) {
      return freelist.alloc(bytes, align, offset);
    }
    return super.alloc(bytes, align, offset, allocator, site);
  }
	
  /**
   * Perform post-allocation actions. Initialize the object header for objects
   * in the mark-sweep space, and delegate to the superclass for other objects.
   * 
   * @param ref The newly allocated object
   * @param typeRef The type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   * @param allocator The allocator number to be used for this allocation
   */
  @Inline
  @Override
  public void postAlloc(ObjectReference ref, ObjectReference typeRef,
      int bytes, int allocator) {

    // System.err.println("-> postAlloc [ref: " + ref + ", typeRef: " + typeRef
    // + ", bytes: " + bytes + ", allocator: " + allocator);

    switch (allocator) {
    case RefCount.ALLOC_DEFAULT:
      RefCountHeader.initializeHeader(ref, false); // initial rc 0, since we
                                                   // dont count stack
                                                   // references
      ExplicitFreeListSpace.unsyncSetLiveBit(ref); // @demmeln why? what?
      return;

    case RefCount.ALLOC_NON_MOVING:
    case RefCount.ALLOC_CODE:
    case RefCount.ALLOC_IMMORTAL:
      RefCountHeader.initializeHeader(ref, false);
      ExplicitFreeListSpace.unsyncSetLiveBit(ref);
      break;

    case RefCount.ALLOC_LARGE_CODE:
    case RefCount.ALLOC_LOS:
    case RefCount.ALLOC_PRIMITIVE_LOS:
      RefCountHeader.initializeHeader(ref, false);
      break;

    default:
      VM.assertions.fail("Allocator not understood by RC");
      return;
    }

    super.postAlloc(ref, typeRef, bytes, allocator);
  }
	
  /**
   * Return the allocator instance associated with a space <code>space</code>,
   * for this plan instance.
   * 
   * @param space The space for which the allocator instance is desired.
   * @return The allocator instance associated with this plan instance which is
   *         allocating into <code>space</code>, or <code>null</code> if no
   *         appropriate allocator can be established.
   */
  @Override
  public Allocator getAllocatorFromSpace(Space space) {
    if (space == RefCount.rcSpace)
      return freelist;
    return super.getAllocatorFromSpace(space);
  }

	
  /****************************************************************************
   * Collection
   */

  /**
   * Perform a per-mutator collection phase.
   * 
   * @param phaseId The collection phase to perform
   * @param primary Perform any single-threaded activities using this thread.
   */
  @Inline
  @Override
  public void collectionPhase(short phaseId, boolean primary) {
    // if (phaseId == RefCount.PREPARE) {
    // super.collectionPhase(phaseId, primary);
    // freelist.prepare();
    // return;
    // }
    //
    // if (phaseId == RefCount.RELEASE) {
    // freelist.release();
    // super.collectionPhase(phaseId, primary);
    // return;
    // }

    super.collectionPhase(phaseId, primary);
  }
	
	
	/**
	 * Flush mutator context, in response to a requestMutatorFlush. Also called
	 * by the default implementation of deinitMutator.
	 */
	@Override
	public void flush() {
		super.flush();
		freelist.flush();
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
   * @param slot The address into which the new reference will be stored.
   * @param tgt The target of the new reference
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  @Inline
  @Override
  public void objectReferenceWrite(ObjectReference src, Address slot,
                           ObjectReference tgt, Word metaDataA,
                           Word metaDataB, int mode) {
	System.err.println("-> objectReferenceWrite [src: " + src + ", slot: " + slot + ", tgt: " + tgt + ", metaDataA: " + metaDataA + ", metaDataB: " + metaDataB + ", mode: " + mode);
//		if(RefCount.isRefCountObject(tgt)) {
//			RefCountHeader.incRC(tgt);
//		}
    //coalescingWriteBarrierSlow(src);
    VM.barriers.objectReferenceWrite(src,tgt,metaDataA, metaDataB, mode);
  }
	  
//  /**
//   * A new reference is about to be created in a location that is not
//   * a regular heap object.  Take appropriate write barrier actions.<p>
//   *
//   * <b>By default do nothing, override if appropriate.</b>
//   *
//   * @param slot The address into which the new reference will be
//   * stored.
//   * @param tgt The target of the new reference
//   * @param metaDataA A value that assists the host VM in creating a store
//   * @param metaDataB A value that assists the host VM in creating a store
//   */
//  @Inline
//  @Override
//  public void objectReferenceNonHeapWrite(Address slot, ObjectReference tgt, Word metaDataA, Word metaDataB) {
//	System.err.println("-> objectReferenceNonHeapWrite [slot: " + slot + ", tgt: " + tgt + ", metaDataA: " + metaDataA + ", metaDataB: " + metaDataB);
//
//    VM.barriers.objectReferenceNonHeapWrite(slot,tgt,metaDataA, metaDataB);
//  }
  
  
//  @Inline
//  @Override
//  public ObjectReference objectReferenceRead(ObjectReference src, Address slot, Word metaDataA, Word metaDataB, int mode) {
//    System.err.println("-> objectReferenceRead");
//    
//    return VM.barriers.objectReferenceRead(src,metaDataA, metaDataB, mode);
//  }

	  
//  /**
//   * Attempt to atomically exchange the value in the given slot
//   * with the passed replacement value. If a new reference is
//   * created, we must then take appropriate write barrier actions.<p>
//   *
//   * <b>By default do nothing, override if appropriate.</b>
//   *
//   * @param src The object into which the new reference will be stored
//   * @param slot The address into which the new reference will be
//   * stored.
//   * @param old The old reference to be swapped out
//   * @param tgt The target of the new reference
//   * @param metaDataA A value that assists the host VM in creating a store
//   * @param metaDataB A value that assists the host VM in creating a store
//   * @param mode The context in which the store occured
//   * @return True if the swap was successful.
//   */
//  @Inline
//  public boolean objectReferenceTryCompareAndSwap(ObjectReference src, Address slot,
//                                               ObjectReference old, ObjectReference tgt, Word metaDataA,
//                                               Word metaDataB, int mode) {
//	System.err.println("-> objectReferenceTryCompareAndSwap [src: " + src + ", slot: " + slot + "old: " + old + ", tgt: " + tgt + ", metaDataA: " + metaDataA + ", metaDataB: " + metaDataB + ", mode: " + mode);
////			if(RefCount.isRefCountObject(tgt)) {
////				if(RefCountHeader.incRC(tgt)) {
////					// Ref count is now 1, meaning it was zero before
////					// thus remove it from ZCT
////					// TODO
////				}
////			}
////			if(RefCount.isRefCountObject(old)) {
////				if(RefCountHeader.decRC(old) == RefCountHeader.RC_ZERO) {
////					// 0 RC, put in ZCT
////					// TODO
////					
////					
////					//System.err.println("!! KILL");
////			          decBuffer.processChildren(current);
////			          if (Space.isInSpace(RCBase.REF_COUNT, current)) {
////			            RCBase.rcSpace.free(current);
////			          } else if (Space.isInSpace(RCBase.REF_COUNT_LOS, current)) {
////			            RCBase.rcloSpace.free(current);
////			          } else if (Space.isInSpace(RCBase.IMMORTAL, current)) {
////			            VM.scanning.scanObject(zero, current);
////			          }
//				
////				}
////			}
//    //coalescingWriteBarrierSlow(src);
//    return VM.barriers.objectReferenceTryCompareAndSwap(src,old,tgt,metaDataA,metaDataB,mode);
//  }

//	  /**
//	   * A number of references are about to be copied from object
//	   * <code>src</code> to object <code>dst</code> (as in an array
//	   * copy).  Thus, <code>dst</code> is the mutated object.  Take
//	   * appropriate write barrier actions.<p>
//	   *
//	   * @param src The source of the values to be copied
//	   * @param srcOffset The offset of the first source address, in
//	   * bytes, relative to <code>src</code> (in principle, this could be
//	   * negative).
//	   * @param dst The mutated object, i.e. the destination of the copy.
//	   * @param dstOffset The offset of the first destination address, in
//	   * bytes relative to <code>tgt</code> (in principle, this could be
//	   * negative).
//	   * @param bytes The size of the region being copied, in bytes.
//	   * @return True if the update was performed by the barrier, false if
//	   * left to the caller (always false in this case).
//	   */
//	  @Inline
//	  public boolean objectReferenceBulkCopy(ObjectReference src, Offset srcOffset,
//	                              ObjectReference dst, Offset dstOffset, int bytes) {
//		//System.err.println("-> objectReferenceWrite [src: " + src + ", srcOffset: " + srcOffset + ", dst: " + dst + ", dstOffset: " + dstOffset + ", bytes: " + bytes);
//	    //coalescingWriteBarrierSlow(dst);
//	    return false;
//	  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /** @return The active global plan as an <code>RefCount</code> instance. */
  @Inline
  private static RefCount global() {
    return (RefCount) VM.activePlan.global();
  }
	
}



//
//
///****************************************************************************
// * 
// * Write barriers.
// */
//
///**
// * A new reference is about to be created. Take appropriate write barrier
// * actions.
// * <p>
// * 
// * <b>By default do nothing, override if appropriate.</b>
// * 
// * @param src
// *            The object into which the new reference will be stored
// * @param slot
// *            The address into which the new reference will be stored.
// * @param tgt
// *            The target of the new reference
// * @param metaDataA
// *            A value that assists the host VM in creating a store
// * @param metaDataB
// *            A value that assists the host VM in creating a store
// * @param mode
// *            The context in which the store occurred
// */
//@Inline
//public void objectReferenceWrite(ObjectReference src, Address slot,
//		ObjectReference tgt, Word metaDataA, Word metaDataB, int mode) {
//
//	
////
////	// if Edgegets changed then dec the old target
////	if (slottarget.get(slot.toString()) != null) {
////		ObjectReference oldtgt = slottarget.get(slot.toString());
////		// decOld because it loses a reference
////		// counts.put(oldtgt, counts.get(oldtgt)-1);
////
////		if (RefCountHeader.decRC(oldtgt) == RefCountHeader.DEC_KILL /*
////																	 * &&
////																	 * !oldtgt
////																	 * .
////																	 * toString
////																	 * (
////																	 * ).equals
////																	 * (tgt.
////																	 * toString
////																	 * ())
////																	 */) {
////			karl.setVisible(true);
////			lbl.setText(lbl.getText() + "\n " + oldtgt.toString());
////			karl.add(lbl);
////			karl.pack();
////			System.err.println("Killed: " + oldtgt);
////			// RefCount.rcSpace.free(slottarget.get(slot.toString()));
////		}
////		System.out.println("Dec of: " + oldtgt + ":to: "
////				+ RefCountHeader.getRC(oldtgt));
////
////		// save the new target
////		slottarget.put(slot.toString(), tgt);
////	} else {
////		System.out.println("New Edge-Slot: " + slot.toString() + " for "
////				+ tgt.toString());
////		slottarget.put(slot.toString(), tgt);
////	}
////
////	RefCountHeader.incRC(tgt);
////
////	// System.out.println("Change of: " + tgt.toString() + ":: " +
////	// RefCountHeader.getRC(tgt));
////
////	if (counts.get(tgt.toString()) != null) {
////		RefCountHeader.incRC(tgt);
////		// counts.put(tgt.toString(), counts.get(tgt.toString())+1);
////		System.out.println("Change of: " + tgt.toString() + ":: "
////				+ counts.get(tgt.toString()));
////	} else {
////		System.out.println("New Count for: " + tgt.toString());
////		// RefCountHeader.incRC(tgt);
////		counts.put(tgt.toString(), 1);
////	} 
//	// src.toString()
//	// System.out.println("New Edge from " + src.toString() + " to " +
//	// tgt.toString() + " mode " + mode + " ::metaDataB " +
//	// metaDataA.toString());
//	VM.barriers.objectReferenceWrite(src, tgt, metaDataA, metaDataB, mode);
//}
//
//static java.awt.TextArea lbl = new java.awt.TextArea();
//static java.awt.Frame karl = new java.awt.Frame("deadFrame AHAHA");
////
//static Hashtable<String, Integer> counts = new Hashtable<String, Integer>();
//static Hashtable<String, ObjectReference> slottarget = new Hashtable<String, ObjectReference>();
//
//
//

