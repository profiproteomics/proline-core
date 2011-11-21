package samples;

public class HelloJava {
	
	public static void helloScala() {
		System.out.println("Hello from Java");
		
		// call scala class
		System.out.println("Scala says: " + AppJava.helloJava()); 
	 }	
}
