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

import org.mmtk.utility.Constants;
import org.mmtk.utility.Log;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

@Uninterruptible
public class CamlLightHeader implements Constants {
  /* Requirements */
  public static final int LOCAL_GC_BITS_REQUIRED = 0;
  public static final int GLOBAL_GC_BITS_REQUIRED = 2;
  public static final int GC_HEADER_WORDS_REQUIRED = 1;


  /************************************************************************
   * RC header word
   */

  /* Header offset */
  public static final Offset RC_HEADER_OFFSET = VM.objectModel.GC_HEADER_OFFSET();
  
  /* color bits */
  // intended to be used to cycle detection. unused at the moment
  public static final int COLOR_BIT_1 = 0;
  public static final int COLOR_BIT_2 = 1;
  public static final Word COLOR_BIT_MASK = Word.one().lsh(2).minus(Word.one()); // "4-1" : ...011

  /* Reference counting increments */
  public static final Word INCREMENT = Word.one();
  public static final Word INCREMENT_LIMIT = Word.one().lsh(BITS_IN_ADDRESS-1).not();
  public static final Word LIVE_THRESHOLD = INCREMENT;

  /* Return values from decRC */
  public static final int RC_ZERO = 0;
  public static final int RC_POSITIVE = 1;

  /**
   * Perform any required initialization of the GC portion of the header.
   *
   * @param object the object
   * @param initialInc start with a reference count of 1 (0 if false)
   */  
  @Inline
  public static void initializeHeader(ObjectReference object, boolean initialInc) {
    Word initialValue =  (initialInc) ? INCREMENT : Word.zero();
    object.toAddress().store(initialValue, RC_HEADER_OFFSET);
  }

  /**
   * Return true if given object is live
   *
   * @param object The object whose liveness is to be tested
   * @return True if the object is alive
   */
  @Inline
  @Uninterruptible
  public static boolean isLiveRC(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(CamlLight.isCamlLightObject(object));
    
    // dont use rc == 0 as a test for liveness, since freshly created object is live, but has
    // rc == 0 until the first reference from the heap is set.
    
    return CamlLight.camlSpace.isLive(object);
  }

  /**
   * Return the reference count for the object.
   *
   * @param object The object whose liveness is to be tested
   * @return True if the object is alive
   */
  @Inline
  @Uninterruptible
  public static int getRC(ObjectReference object) {
    return object.toAddress().loadWord(RC_HEADER_OFFSET).toInt();
  }

  /**
   * Increment the reference count of an object.
   *
   * @param object The object whose reference count is to be incremented.
   * @return True if the object has now RC 1
   */
  @Inline
  public static void incRC(ObjectReference object) {
    Word oldValue, newValue;
    
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(CamlLight.isCamlLightObject(object), "not caml");
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(isLiveRC(object), "not live");
    
    do {
      oldValue = object.toAddress().prepareWord(RC_HEADER_OFFSET);
      newValue = oldValue.plus(INCREMENT);
    } while (!object.toAddress().attempt(oldValue, newValue, RC_HEADER_OFFSET));

    // overflow of the rc field?
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(newValue.LE(INCREMENT_LIMIT));
    
//    Log.prependThreadId();
//    Log.write("incRC - object: ");
//    Log.write(object);
//    Log.write(" newValue: ");
//    Log.writeln(newValue);
    
  }

  /**
   * Decrement the reference count of an object.  Return either
   * <code>DEC_KILL</code> if the count went to zero,
   * <code>DEC_ALIVE</code> if the count did not go to zero.
   *
   * @param object The object whose RC is to be decremented.
   * @return <code>DEC_KILL</code> if the count went to zero,
   * <code>DEC_ALIVE</code> if the count did not go to zero.
   */
  @Inline
  @Uninterruptible
  public static int decRC(ObjectReference object) {
    Word oldValue, newValue;
    int rtn;
    
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(CamlLight.isCamlLightObject(object));
      VM.assertions._assert(isLiveRC(object));
    }
    
    do {
      oldValue = object.toAddress().prepareWord(RC_HEADER_OFFSET);
      newValue = oldValue.minus(INCREMENT);
      if (newValue.LT(LIVE_THRESHOLD)) {
        rtn = RC_ZERO;
      } else {
        rtn = RC_POSITIVE;
      }
    } while (!object.toAddress().attempt(oldValue, newValue, RC_HEADER_OFFSET));

//    Log.prependThreadId();
//    Log.write("decRC - object: ");
//    Log.write(object);
//    Log.write(" newValue: ");
//    Log.writeln(newValue);
    
    return rtn;
  }
  
}
