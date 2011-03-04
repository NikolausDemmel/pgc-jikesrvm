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

import org.mmtk.plan.*;
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
	 * from the mark sweep space, and delegates everything else to the
	 * superclass.
	 * 
	 * @param bytes
	 *            The number of bytes required for the object.
	 * @param align
	 *            Required alignment for the object.
	 * @param offset
	 *            Offset associated with the alignment.
	 * @param allocator
	 *            The allocator associated with this request.
	 * @return The low address of the allocated memory.
	 */
	@Inline
	@Override
	public Address alloc(int bytes, int align, int offset, int allocator,
			int site) {
		if (allocator == RefCount.ALLOC_DEFAULT) {
			return freelist.alloc(bytes, align, offset);
		}
		return super.alloc(bytes, align, offset, allocator, site);
	}

	/**
	 * Perform post-allocation actions. Initialize the object header for objects
	 * in the mark-sweep space, and delegate to the superclass for other
	 * objects.
	 * 
	 * @param ref
	 *            The newly allocated object
	 * @param typeRef
	 *            the type reference for the instance being created
	 * @param bytes
	 *            The size of the space to be allocated (in bytes)
	 * @param allocator
	 *            The allocator number to be used for this allocation
	 */
	@Inline
	@Override
	public void postAlloc(ObjectReference ref, ObjectReference typeRef,
			int bytes, int allocator) {

		switch (allocator) {
		case RefCount.ALLOC_DEFAULT:
			RefCountHeader.initializeHeader(ref, true);
			return;

		case RefCount.ALLOC_NON_MOVING:
		case RefCount.ALLOC_CODE:
		case RefCount.ALLOC_IMMORTAL:
			RefCountHeader.initializeHeader(ref, true);
			break;
			
		case RefCount.ALLOC_LARGE_CODE:
		case RefCount.ALLOC_LOS:
		case RefCount.ALLOC_PRIMITIVE_LOS:
			RefCountHeader.initializeHeader(ref, true);
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
	 * @param space
	 *            The space for which the allocator instance is desired.
	 * @return The allocator instance associated with this plan instance which
	 *         is allocating into <code>space</code>, or <code>null</code> if no
	 *         appropriate allocator can be established.
	 */
	@Override
	public Allocator getAllocatorFromSpace(Space space) {
		if (space == RefCount.rcSpace)
			return freelist;
		return super.getAllocatorFromSpace(space);
	}

	/****************************************************************************
	 * 
	 * Write barriers.
	 */

	/**
	 * A new reference is about to be created. Take appropriate write barrier
	 * actions.
	 * <p>
	 * 
	 * <b>By default do nothing, override if appropriate.</b>
	 * 
	 * @param src
	 *            The object into which the new reference will be stored
	 * @param slot
	 *            The address into which the new reference will be stored.
	 * @param tgt
	 *            The target of the new reference
	 * @param metaDataA
	 *            A value that assists the host VM in creating a store
	 * @param metaDataB
	 *            A value that assists the host VM in creating a store
	 * @param mode
	 *            The context in which the store occurred
	 */
	@Inline
	public void objectReferenceWrite(ObjectReference src, Address slot,
			ObjectReference tgt, Word metaDataA, Word metaDataB, int mode) {
		

		// if Edgegets changed then dec the old target
		if (slottarget.get(slot.toString()) != null) {
			ObjectReference oldtgt = slottarget.get(slot.toString());
			// decOld because it loses a reference
			// counts.put(oldtgt, counts.get(oldtgt)-1);

			if (RefCountHeader.decRC(oldtgt) == RefCountHeader.DEC_KILL /*
																		 * &&
																		 * !oldtgt
																		 * .
																		 * toString
																		 * (
																		 * ).equals
																		 * (tgt.
																		 * toString
																		 * ())
																		 */) {
				karl.setVisible(true);
				lbl.setText(lbl.getText() + "\n " + oldtgt.toString());
				karl.add(lbl);
				karl.pack();
				System.err.println("Killed: " + oldtgt);
				// RefCount.rcSpace.free(slottarget.get(slot.toString()));
			}
			System.out.println("Dec of: " + oldtgt + ":to: "
					+ RefCountHeader.getRC(oldtgt));

			// save the new target
			slottarget.put(slot.toString(), tgt);
		} else {
			System.out.println("New Edge-Slot: " + slot.toString() + " for "
					+ tgt.toString());
			slottarget.put(slot.toString(), tgt);
		}

		RefCountHeader.incRC(tgt);

		// System.out.println("Change of: " + tgt.toString() + ":: " +
		// RefCountHeader.getRC(tgt));

		if (counts.get(tgt.toString()) != null) {
			RefCountHeader.incRC(tgt);
			// counts.put(tgt.toString(), counts.get(tgt.toString())+1);
			System.out.println("Change of: " + tgt.toString() + ":: "
					+ counts.get(tgt.toString()));
		} else {
			System.out.println("New Count for: " + tgt.toString());
			// RefCountHeader.incRC(tgt);
			counts.put(tgt.toString(), 1);
		}
		// src.toString()
		// System.out.println("New Edge from " + src.toString() + " to " +
		// tgt.toString() + " mode " + mode + " ::metaDataB " +
		// metaDataA.toString());
		VM.barriers.objectReferenceWrite(src, tgt, metaDataA, metaDataB, mode);
	}

	static java.awt.TextArea lbl = new java.awt.TextArea();
	static java.awt.Frame karl = new java.awt.Frame("deadFrame AHAHA");
	//
	Hashtable<String, Integer> counts = new Hashtable<String, Integer>();
	Hashtable<String, ObjectReference> slottarget = new Hashtable<String, ObjectReference>();

	

	/****************************************************************************
	 * Collection
	 */

	/**
	 * Perform a per-mutator collection phase.
	 * 
	 * @param phaseId
	 *            The collection phase to perform
	 * @param primary
	 *            Perform any single-threaded activities using this thread.
	 */
	@Inline
	@Override
	public void collectionPhase(short phaseId, boolean primary) {
		if (phaseId == RefCount.PREPARE) {
			super.collectionPhase(phaseId, primary);
			freelist.prepare();
			return;
		}

		if (phaseId == RefCount.RELEASE) {
			freelist.release();
			super.collectionPhase(phaseId, primary);
			return;
		}

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
}
