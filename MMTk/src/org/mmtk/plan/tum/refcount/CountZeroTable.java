package org.mmtk.plan.tum.refcount;
import org.mmtk.utility.deque.ObjectReferenceDeque;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.ObjectReferenceArray;

import sun.rmi.runtime.Log;
/**
 * Wrapper for ObjectReferenceDeque to access a FLUSHED!!!!! ObjectReferenceDeq
 * with functions contains and get
 * @author fre
 *
 */
@Uninterruptible
public class CountZeroTable  {
	ObjectReferenceArray objects;
	/**
	 * Constructor
	 * builds ObjectReferenceArray from ObjectReferenceDeque
	 * @param deq ReferenceDeque - meant to be the zero-count-table
	 * @param count number of elements in the Deque (is there an easier way?)
	 */
	public CountZeroTable(ObjectReferenceDeque deq, int count){
		this();
		init(deq,count);
	}
	public CountZeroTable() {
	}
	/**
	 * 
	 * @param ref
	 * 
	 * @return true if @param ref is in the array
	 */
	@Inline
	public boolean contains(ObjectReference ref){
		ObjectReference tobj=null;
		for(int i=0;i<objects.length();i++){
			if(objects.get(i).toAddress().EQ(ref.toAddress()))
				return true;
		}
		return false;
	}
	public ObjectReference get(int index){
		VM.assertions._assert(index < objects.length());
		return objects.get(index);
	}
	public int indexOf(ObjectReference ref){
		for(int i=0;i<objects.length();i++)
			if(objects.get(i).toAddress().EQ(ref.toAddress()))
				return i;
		return -1;
	}
	@Uninterruptible
	public void init(ObjectReferenceDeque deq, int count) {
		VM.assertions._assert(deq.isFlushed());
		objects = ObjectReferenceArray.create(count);
		org.mmtk.utility.Log.writeln("CZT_init()");
		for(int i=0;i<count;i++){
			objects.set(i, deq.pop());
			org.mmtk.utility.Log.writeln(objects.get(i));
		}
	}
}
