import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author smmorneau
 *
 * Uses an MDNS query that serves to both announce a cache
 * presence and browse for other caches on the smm-cs621-cache service.
 *
 */
public class ServiceDiscovery {

	public static final String SERVICE_TYPE = "smm-cs621-cache";
	public static final String GROUP_IP = "224.0.0.251";
	public static final int GROUP_PORT = 5353;

	public int cachePort;
	public HashMap<String, String[]> cache;  // url : [fileName, contentType]
	public String cacheAddress;
	public Set<String> cacheAddresses = new HashSet<String>();

	public ServiceDiscovery(int cachePort) throws UnknownHostException {
		String ip = InetAddress.getLocalHost().getHostAddress();
		this.cachePort = cachePort;
		this.cacheAddress = ip + ":" + cachePort;
		cacheAddresses.add(cacheAddress);
		this.cache = new HashMap<String, String[]>();
	}

	/*
	 * cs621-cache self_ip
	 */
	public void parsePacket(DatagramPacket packet) {
		String data = new String(packet.getData()).trim();
		// skip messages for different services
	    if (! data.contains(SERVICE_TYPE)) {
	    	return;
	    }
	    String[] splitData = data.split(" ");
	    if (splitData.length < 2) {
	    	System.err.println("UNABLE TO SPLIT DATA:\n" + data);
	    	return;
	    }
	    String sender = splitData[1];
	    System.out.println("> FROM: " + packet.getAddress().getHostAddress() +
	    		":" + packet.getPort() + ", DATA: " + data);
	    // ignore self and caches that have been seen already
	    if (!cacheAddresses.contains(sender)) {
	    	cacheAddresses.add(sender);
		    announce(true);
		    System.out.println("\t> CACHES: " + cacheAddresses);
    	}
	}

	public void listen() {
        MulticastSocket socket = null;
        InetAddress group = null;
        DatagramPacket packet;

		try {
			socket = new MulticastSocket(GROUP_PORT);
			group = InetAddress.getByName(GROUP_IP);
			socket.joinGroup(group);

			while (true) {
			    byte[] buf = new byte[256];
			    packet = new DatagramPacket(buf, buf.length);
			    try {
					socket.receive(packet);
				} catch (IOException e) {
					break;
				}
			    parsePacket(packet);
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
		} finally {
			if (socket != null) {
				if (group != null) {
					try {
						socket.leaveGroup(group);
					} catch (IOException e) { }
				}
				socket.close();
			}
		}
	}

	public void announce(boolean once) {
		new MulticastThread(cachePort, once).start();
	}

}
