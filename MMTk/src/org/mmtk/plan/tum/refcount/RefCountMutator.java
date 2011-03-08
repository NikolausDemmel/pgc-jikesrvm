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

import org.mmtk.plan.MutatorContext;
import org.mmtk.plan.StopTheWorldMutator;
import org.mmtk.policy.ExplicitFreeListLocal;
import org.mmtk.policy.ExplicitFreeListSpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.DoublyLinkedList;
import org.mmtk.utility.Log;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.deque.AddressPairDeque;
import org.mmtk.utility.deque.ObjectReferenceDeque;
import org.mmtk.utility.sanitychecker.SanityDataTable;
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

	public static ObjectReferenceDeque ZCT = new ObjectReferenceDeque("zct", RefCount.zcts);
	protected final DoublyLinkedList refTable = new DoublyLinkedList(1,true);//RefCount.refs);
	/****************************************************************************
	 * Instance fields
	 */
	protected ExplicitFreeListLocal freelist = new ExplicitFreeListLocal(RefCount.rcSpace);

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
	@Override
	public Address alloc(int bytes, int align, int offset, int allocator,
			int site) {
		//		Log.writeln(RefCount.freeMemory().toInt()/(1024*1024));
		//		System.exit(42);
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
	 * @param bytes	Log.writeln(
	 *            The size of the space to be allocated (in bytes)
	 * @param allocator
	 *            The allocator number to be used for this allocation
	 */
	@Inline
	@Override
	public void postAlloc(ObjectReference ref, ObjectReference typeRef,
			int bytes, int allocator) {
		//		Log.writeln("postalloc");
		//		if(ref!=null)Log.writeln("ref:\t"+ref+" "+typeRef);
		//		if(typeRef!=null)Log.writeln("typeRef:\t"+typeRef.toAddress().toInt());
		switch (allocator) {
		case RefCount.ALLOC_DEFAULT:
			RefCountHeader.initializeHeader(ref, true);
			ExplicitFreeListSpace.unsyncSetLiveBit(ref);
			break;

		case RefCount.ALLOC_NON_MOVING:
		case RefCount.ALLOC_CODE:
		case RefCount.ALLOC_IMMORTAL:
			RefCountHeader.initializeHeader(ref, true);	
			ExplicitFreeListSpace.unsyncSetLiveBit(ref);
			break;

		case RefCount.ALLOC_LARGE_CODE:
		case RefCount.ALLOC_LOS:
		case RefCount.ALLOC_PRIMITIVE_LOS:
			RefCountHeader.initializeHeader(ref, true);
			ExplicitFreeListSpace.unsyncSetLiveBit(ref);
			break;

		default:
			VM.assertions.fail("Allocator not understood by RC");
			return;
		}
	}	


	public synchronized void out(boolean exit) {
		if(hcnt%1000==0){

			//			Log.writeln("refs:");
			//			Enumeration<String> addresses = RefCount.refTable.keys();
			//			while(addresses.hasMoreElements()){
			//				String addr = addresses.nextElement();
			//				ObjectReference obj = RefCount.refTable.get(addr);
			//				int count = RefCountHeader.getRC(obj);
			//				Log.writeln(addr+"\t->\t"+obj+" ("+count+")");
			//				RefCountHeader.getRC(obj);
			//			}	
			Log.write("ref_size: ");
//			Log.writeln(RefCount.refTable.size());
			Log.write("ZCT_size: ");
			//			Log.writeln(RefCount.ZCT.size());
			Log.write(iinc);
			Log.writeln(" times incremented");
			Log.write(idec);
			Log.writeln(" times decremented");
			Log.write(idel);
			Log.writeln(" times deleted");
			//			for(ObjectReference obj : RefCount.ZCT){
			//				Log.writeln(obj);
			//			}
			if(exit)System.exit(42);		//		Log.writeln("tablesize\t"+RefCount.refTable.size());
		}
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
	//	@Inline
	public void objectReferenceWrite(ObjectReference src, Address slot,
			ObjectReference tgt, Word metaDataA, Word metaDataB, int mode) {

		RefCountHeader.incRC(tgt);
		//		Log.writeln("RefCountMutator.objectReferenceWrite()");
		try{
			hcnt++;
			if(tgt!=null){
				// Zielobjekt ist bekannt
				Log.writeln("mooh");
				Log.writeln(slot.toInt());
//				refTable.isMember(slot);
//				Log.writeln(RefCount.refTable.contains(slot.toInt()));
//				Log.writeln(RefCount.refTable.indexOf(slot.toInt()));
				if(refTable.isMember(slot)){
					refTable.getNext(slot);
					
					ObjectReference old = RefCount.refTable.get(slot.toInt());
					/* Ziel_neu != Ziel_alt */
					if(!old.equals(tgt)){
						idec++;
						/* RC(Ziel_alt)-- */

						int retval = !RefCountHeader.isLiveRC(old) ? RefCountHeader.DEC_KILL : RefCountHeader.decRC(old);
						if(retval==RefCountHeader.DEC_KILL){
							idel=(retval==RefCountHeader.DEC_KILL)?idel+1:idel;
							/* töten!!!! */
							ZCT.push(old);
							//						RefCount.ZCT.addObject(old);
							RefCount.refTable.remove(slot.toInt());
						}
					}
				}else{
					if(RefCount.isRefCountObject(tgt)){
						//				Log.writeln("if if else");
						RefCountHeader.incRC(tgt);
						iinc++;
						RefCount.refTable.add(slot.toInt(), tgt);
					}
				}
			}
			out(false);
		}catch(Exception e){
			e.printStackTrace();
			System.exit(mode);
		}
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
		if(tgt!=null){
			// Zielobjekt ist bekannt
			if(RefCount.refTable.contains(slot.toInt())){
				ObjectReference old = RefCount.refTable.get(slot.toInt());
				/* Ziel_neu != Ziel_alt */
				if(!old.equals(tgt)){
					idec++;
					/* RC(Ziel_alt)-- */

					int retval = !RefCountHeader.isLiveRC(old) ? RefCountHeader.DEC_KILL : RefCountHeader.decRC(old);
					if(retval==RefCountHeader.DEC_KILL){
						idel=(retval==RefCountHeader.DEC_KILL)?idel+1:idel;
						/* töten!!!! */
						ZCT.push(old);
						//						RefCount.ZCT.addObject(old);
						RefCount.refTable.remove(slot.toInt());
					}
				}
			}else{
				if(RefCount.isRefCountObject(tgt)){
					//				Log.writeln("if if else");
					RefCountHeader.incRC(tgt);
					iinc++;
					RefCount.refTable.add(slot.toInt(), tgt);
				}
			}
		}
		out(false);

		super.objectReferenceNonHeapWrite(slot, tgt, metaDataA, metaDataB);
	}


	@Override
	public boolean objectReferenceTryCompareAndSwap(ObjectReference src,
			Address slot, ObjectReference old, ObjectReference tgt,
			Word metaDataA, Word metaDataB, int mode) {
		// TODO Auto-generated method stub
		//		Log.writeln("RefCountMutator.objectReferenceTryCompareAndSwap()");
		return super.objectReferenceTryCompareAndSwap(src, slot, old, tgt, metaDataA,
				metaDataB, mode);
	}


	@Override
	public void wordWrite(ObjectReference src, Address slot, Word value,
			Word metaDataA, Word metaDataB, int mode) {
		// TODO Auto-generated method stub
		//		Log.writeln("wordWrite");
		//		Log.writeln("src\t" +src.toAddress().toInt());
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
		//		Log.writeln("RefCountMutator.collectionPhase()");
		if (phaseId == RefCount.PREPARE) {

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
