package foobar;

public class Main {

  /**
   * @param args
   */
  public static void main(String[] args) {
//
    Junk foo = new Junk();
//    
//    Junk[] fooarray = new Junk[100];
//    
//    CamlLightHeap bar = new CamlLightHeap();
//    
//    CamlLightHeap[] bararray = new CamlLightHeap[100];
//    
//    System.out.println("foo: " + foo + " bar: " + bar);
//    System.out.println("fooarray: " + fooarray + " bararray: " + bararray);
    
    
    for(int i = 0; i < 100000; ++i) {
//      CamlLightHeap tmp = new CamlLightHeap();
      Junk tmp2 = new Junk();
      foo.car = new CamlLightHeap();
//      foo.car = null;
      foo = new Junk();
      tmp2 = null;
    }
   
    
  }
  
  

}
