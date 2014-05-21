import java.net.UnknownHostException;

/**
 * 
 * @author smmorneau
 *
 * Starts a proxy cache on a specified port.
 *
 */
public class DistributedProxyCache {

	public static void main(String[] args) {
    	if (args.length == 1) {
    		int port = -1;
        	try {    		
        		port = Integer.parseInt(args[0]);
        		ServiceDiscovery sd = new ServiceDiscovery(port);
        		sd.announce(false);
        		new ProxyCacheThread(port, sd).start();
        		sd.listen();
        	} catch (NumberFormatException e) {
        		System.err.println("Port number must be an int.");
        	} catch (UnknownHostException e) {
				System.err.println("Unable to get host ip address.");
			} 
    	} else {
    		System.err.println("Usage: java DistributedProxyCache <cache_port>");
    	}
	}

}
