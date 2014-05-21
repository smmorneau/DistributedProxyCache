import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.nio.charset.Charset;

/**
 *
 * @author smmorneau
 *
 * A thread that broadcasts a multicast DNS message announcing its service.
 *
 */
public class MulticastThread extends Thread {

	public static final String SERVICE_TYPE = "smm-cs621-cache";
	public static final String GROUP_IP = "224.0.0.251";
	public static final int GROUP_PORT = 5353;
	public static final int MAX_INTERVAL = 3600000;  // 1 hour

    private long interval = 1000; // 1 second
    private String strPrefix;
    private boolean once;

    public MulticastThread(int cachePort, boolean once) {
      super("MulticastThread");
      this.once = once;
      String ip = "";
		try {
			ip = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			System.err.println("Unable to get ip address.");
			System.exit(0);
		}
		this.strPrefix = SERVICE_TYPE + " " + ip + ":" + cachePort;
	}

	public void run() {
    	MulticastSocket socket = null;
    	while (socket == null) {
    		try {
    			socket = new MulticastSocket();
    		} catch (IOException e) {
    			System.err.println("Unable to create socket. Trying again.");
			}
    	}
        while (true) {
            try {
            	System.out.print("> ANNOUNCE: " + strPrefix);
            	if (once) {
            		System.out.println(" to new cache.");
            	} else {
            		System.out.println(" then wait " + (interval/1000) + " seconds.");
            	}

            	// Service Announcement
                byte[] buf = generatePing();

                InetAddress group = InetAddress.getByName(GROUP_IP);
                DatagramPacket packet = new DatagramPacket(buf, buf.length, group, GROUP_PORT);
                socket.send(packet);

                if (once) {
                	break;
                }

			    // Exponential Back-off
				try {
				    sleep((long)(interval));
				    interval = Math.min(interval * 3, MAX_INTERVAL);
				} catch (InterruptedException e) {
					break;
				}
	        } catch (IOException ioe) {
	            ioe.printStackTrace();
	            break;
	        }
        }
		socket.close();
    }

    /*
     * All six header fields equal zero (00 00) except the QDCOUNT,
     * which equals one (00 01).
     */
	private byte[] generatePing() {
		// 12 byte header + 1 byte for qname length
	    byte[] header = new byte[13];

		// QDCOUNT == x'0001 (1)
		header[5] = new Byte("1");

		// Length of qname label
		header[12] = new Byte("" + strPrefix.length());
		byte[] fqdn = strPrefix.getBytes(Charset.forName("UTF-8"));

		// 1 byte null for name termination + 4 byte flags
		byte[] flags = new byte[5];
		// QTYPE: A == x'0001 (1)
		flags[2] = new Byte("1");
		// QCLASS: IN = x'0001 (1)
		flags[4] = new Byte("1");

		byte[] query = new byte[header.length + fqdn.length + flags.length];
		System.arraycopy(header, 0, query, 0, header.length);
		System.arraycopy(fqdn, 0, query, header.length, fqdn.length);
		System.arraycopy(flags, 0, query, header.length + fqdn.length, flags.length);
		return query;
	}

}
