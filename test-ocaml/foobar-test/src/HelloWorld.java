//import java.util.LinkedList;

public class HelloWorld {

  /**
   * @param args
   */
  public static void main(String[] args) {

//    LinkedList<Object> list = new LinkedList<Object>();
//    
//    for (int i = 0; i < 10; ++i) {
//      Object foo = new Object();
//      list.add(foo);
//    }

    System.out.println("creating object...");
    Object foo = new Object();
    System.out.println("created object.");

    System.out.println("foo: " + foo);
    
    foo = null;

    System.out.println("creating object...");
    foo = new Object();
    System.out.println("created object.");

    System.out.println("bar: " + foo);
    
    foo = null;   
    
//    System.out.println("bar: " + list);

  }

}
