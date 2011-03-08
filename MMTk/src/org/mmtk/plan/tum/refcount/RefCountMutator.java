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

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;

import org.mmtk.plan.MutatorContext;
import org.mmtk.plan.StopTheWorldMutator;
import org.mmtk.policy.ExplicitFreeListLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Word;

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
	 * @param bytes	System.out.println(
	 *            The size of the space to be allocated (in bytes)
	 * @param allocator
	 *            The allocator number to be used for this allocation
	 */
	@Inline
	@Override
	public void postAlloc(ObjectReference ref, ObjectReference typeRef,
			int bytes, int allocator) {
//		System.out.println("postalloc");
//		if(ref!=null)System.out.println("ref:\t"+ref+" "+typeRef);
//		if(typeRef!=null)System.out.println("typeRef:\t"+typeRef.toAddress().toInt());
		switch (allocator) {
		case RefCount.ALLOC_DEFAULT:
			RefCountHeader.initializeHeader(ref, true);
			break;

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
	}	


	public void out(String ping) {
		//	System.out.println(ping);
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

	Address edgeAddress;
	ObjectReference edgeTarget;
	ObjectReference edgeOldTarget;
	ObjectReference edgeSource;
	boolean refc = false;


	static Hashtable<String, ObjectReference> slottarget = new Hashtable<String, ObjectReference>();
	List<ObjectReference> killed = new LinkedList<ObjectReference>();


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
	Hashtable<String,ObjectReference> refTable = new Hashtable<String,ObjectReference>();
	@Inline
	public void objectReferenceWrite(ObjectReference src, Address slot,
			ObjectReference tgt, Word metaDataA, Word metaDataB, int mode) {
		//		System.out.println("writeBarrier");
//		System.out.println(src+" "+slot+" "+tgt+" "+metaDataA+" "+metaDataB+" "+mode);
//		System.out.println((src==null)+" "+(slot==null)+" "+(tgt==null)+" "+(metaDataA==null)+" "+(metaDataB==null)+" "+mode);
		//		if(src!=null)System.out.println("src:\t"+src.toAddress().toInt()+" "+RefCountHeader.getRC(src));
		//		if(slot!=null)System.out.println("slot:\t"+slot.toInt());
		//		if(tgt!=null)System.out.println("tgt:\t"+tgt.toAddress().toInt()/*+" "+RefCountHeader.getRC(tgt)*/);

		//		System.out.println((src==null)+" "+(tgt==null));
		//RefCountHeader.initializeHeader(tgt, true);
		//System.out.println("hit:\t"+refTable.containsKey(slot));
		//if(refTable.containsKey(slot))
		hcnt++;
		//		System.out.println("hitcnt:\t"+hcnt);
		/* nur mit sinnvollen Targets arbeiten (sanity) */
		if(tgt!=null){
			// Zielobjekt ist bekannt
			if(refTable.containsKey(slot.toString())){
				ObjectReference old = refTable.get(slot.toString());
				/* Ziel_neu != Ziel_alt */
				if(!old.toString().equals(tgt.toString())){
					idec++;
					/* RC(Ziel_alt)-- */
					
					int retval = RefCountHeader.isLiveRC(old) ? RefCountHeader.DEC_KILL : RefCountHeader.decRC(old);
					if(retval==RefCountHeader.DEC_KILL){
						idel=retval==RefCountHeader.DEC_KILL?idel+1:idel;
						/* töten!!!! */
						RefCount.ZCT.add(old);
						refTable.remove(slot.toString());
					}
				}
			}else{
				if(RefCount.isRefCountObject(tgt)){
					//				System.out.println("if if else");
					RefCountHeader.incRC(tgt);
					iinc++;
					refTable.put(slot.toString(), tgt);
				}
			}
		}
		if(hcnt%10000==0){

			System.out.println("refs:");
//			Enumeration<String> addresses = refTable.keys();
//			while(addresses.hasMoreElements()){
//				String addr = addresses.nextElement();
//				ObjectReference obj = refTable.get(addr);
//				int count = RefCountHeader.getRC(obj);
//				System.out.println(addr+"\t->\t"+obj+" ("+count+")");
//				RefCountHeader.getRC(obj);
//			}	
			System.out.println("ref_size: "+refTable.size()+"\nZCT_size: "+RefCount.ZCT.size()+"\n"+iinc+" times incremented\n"+idec+" times decremented\n"+idel+" times deleted\nZCT:");
//			for(ObjectReference obj : RefCount.ZCT){
//				System.out.println(obj);
//			}
			//System.exit(42);		//		System.out.println("tablesize\t"+refTable.size());
		}
		// target of slot changed?
		// decrement old slot target
		VM.barriers.objectReferenceWrite(src, tgt, metaDataA, metaDataB, mode);
	}
	int hcnt=0;
	int iinc=0;
	int idec=0;
	int idel=0;
	/****************************************************************************
	 * Collection
	 */

	@Override
	public void deinitMutator() {
		// TODO Auto-generated method stub
		super.deinitMutator();
	}


	@Override
	public void objectReferenceNonHeapWrite(Address slot, ObjectReference tgt,
			Word metaDataA, Word metaDataB) {
		// TODO Auto-generated method stub
		System.out.println("RefCountMutator.objectReferenceNonHeapWrite()");
		super.objectReferenceNonHeapWrite(slot, tgt, metaDataA, metaDataB);
	}


	@Override
	public boolean objectReferenceTryCompareAndSwap(ObjectReference src,
			Address slot, ObjectReference old, ObjectReference tgt,
			Word metaDataA, Word metaDataB, int mode) {
		// TODO Auto-generated method stub
		System.out.println("RefCountMutator.objectReferenceTryCompareAndSwap()");
		return super.objectReferenceTryCompareAndSwap(src, slot, old, tgt, metaDataA,
				metaDataB, mode);
	}


	@Override
	public void wordWrite(ObjectReference src, Address slot, Word value,
			Word metaDataA, Word metaDataB, int mode) {
		// TODO Auto-generated method stub
		System.out.println("wordWrite");
		System.out.println("src\t" +src.toAddress().toInt());
		super.wordWrite(src, slot, value, metaDataA, metaDataB, mode);
	}


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
		if (phaseId == RefCount.CLOSURE) {
			for(ObjectReference k: killed){
				RefCount.rcSpace.free(k);
			}
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
