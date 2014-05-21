import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 *
 * @author smmorneau
 *
 * A thread that servers the proxy cache, handling client requests by fething
 * from a local cache, a peer cache, or the requested web server.
 *
 */
public class ProxyCacheThread extends Thread {
	public static final long ONE_SECOND = 1000;

	private ServiceDiscovery sd;
	private String ip;
	private int port;
	private String lastReferrer;
	private String lastAbsolute;

	public ProxyCacheThread(int port, ServiceDiscovery sd) {
		super("ProxyCacheThread");
		this.sd = sd;
		this.port = port;
		try {
			this.ip = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			System.err.println("Unable to get ip address.");
			System.exit(0);
		}
		System.out.println("Usage: Go to " + ip + ":" + port + "/<url> in your browser.");
	}

    public void run() {
    	ServerSocket serverSocket = null;
    	try {
			serverSocket = new ServerSocket(port);
		} catch (IOException e) {
			System.err.println("Unable to create socket on port " + port +
					". Please try another port.");
			System.exit(0);
		}

        while (true) {
        	Socket clientSocket = null;
        	while (clientSocket == null) {
        		try {
        			clientSocket = serverSocket.accept();
        		} catch (IOException e) {
        			e.printStackTrace();
        		}
        	}

            try {
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(
                    new InputStreamReader(
                        (clientSocket).getInputStream()));

                String request = "";
                String inputLine = "";

                while ((inputLine = in.readLine()) != null) {
                	if (inputLine.trim().isEmpty()) {
                		// \r\n
                		break;
                	}
                	request += inputLine + "\r\n";
                }

                request = request.trim();

                if (request.isEmpty()) {
                	continue;
                }

                if (request.startsWith("QUERY")) {
                	// incoming query: <QUERY url>
                	String url = request.split(" ")[1].trim();
                	String[] metadata = sd.cache.get(url);
                	if (metadata == null) {
                		out.println("NO");
                		System.out.println("<<< " + request + " -- NO");
                	} else {
                		out.println("YES " + metadata[1]);
                		System.out.println("<<< " + request + " -- YES");
                	}
                	out.flush();
                } else {
                	// incoming GET request
                	String url = formatURL(request);
                	System.out.println(">>> incoming GET: " + url);
                	String response = fetchResponseForClient(url, request);
                	out.println(response);
                }

                System.out.println("\n--------------------------------------------\n");
            } catch (IOException e) {
            } finally {
            	try {
					clientSocket.close();
				} catch (IOException e) {}
            }
        }
    }

    /*
     * Servers data from peers or makes GET request
     */
    public String localCacheMiss(String url, String request) {
    	String response = null;

		for (String cacheAddr: sd.cacheAddresses) {
			// skip your own address
			if (cacheAddr.compareTo(ip + ":" + port) == 0) {
				continue;
			}
			String remoteIp = cacheAddr.split(":")[0];
			int remotePort = Integer.parseInt(cacheAddr.split(":")[1]);

			String peerDataContentType = checkPeersCache(remoteIp, remotePort, url);
			if (peerDataContentType == null) {
				continue;
			}

			response = distributedCacheHit(remoteIp, remotePort, url, peerDataContentType);

		}

		// GET request if no peers have cached data
		if (response == null) {
			response = getRequest(url, request);
		}
		return response;
    }

    /* Returns content type or null if peer doesn't have it */
    public String checkPeersCache(String remoteIp, int remotePort, String url) {
    	String queryCacheAddr = remoteIp + ":" + remotePort;
        Socket socket = null;
        PrintWriter out = null;
        BufferedReader in = null;
        String contentType = null;
		try {
			socket = new Socket(remoteIp, remotePort);
	        out = new PrintWriter(socket.getOutputStream());
	        in = new BufferedReader( new InputStreamReader(socket.getInputStream()));

	        // ask if peer has cached data
	        out.println("QUERY " + url + "\r\n");
	        out.flush();
			String hasCachedData = in.readLine();
			if (hasCachedData == null) {
				System.out.println(">>> PEER TIMEOUT: " + remoteIp + ":" + remotePort);
				return contentType;
			} else if (hasCachedData.startsWith("NO")) {
				System.out.println(">>> PEER CACHE MISS: " + queryCacheAddr);
				return contentType;
			}
			contentType = hasCachedData.split(" ")[1];
		} catch (UnknownHostException e) {
			System.err.println("Don't know about host " + queryCacheAddr);
		} catch (IOException e) {
			System.err.println("Couldn't get I/O for the connection to " +
					queryCacheAddr);
		} finally {
			try {
        		if (in != null) in.close();
        		if (out != null) out.close();
				if (socket != null && !socket.isClosed()) socket.close();
			} catch (IOException e) {}
		}
		return contentType;
    }

    public String distributedCacheHit(String remoteIp, int remotePort, String url, String contentType) {
    	String cacheAddr = remoteIp + ":" + remotePort;
		String newRequest = craftRequest(cacheAddr, url);
		System.out.println(">>> PEER CACHE HIT: " + cacheAddr);

		long peerFetchTime = System.currentTimeMillis();
		// get cached data from peer
		Socket socket = null;
		try {
			socket = new Socket(remoteIp, remotePort);
		}  catch (UnknownHostException e) {
			System.err.println("Don't know about host " + cacheAddr);
		} catch (IOException e) {
			System.err.println("Couldn't get I/O for the connection to " + cacheAddr);
		}

		String response = null;
		PrintWriter out = null;
		BufferedReader in = null;
		try {
			out = new PrintWriter(socket.getOutputStream());
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out.println(newRequest);
			out.flush();

			int c;
			StringBuilder sb = new StringBuilder();
			while ((c = in.read()) != -1) {
			    sb.append((char)c) ;
			}
//			String body = sb.toString();
			response = sb.toString();
			peerFetchTime = System.currentTimeMillis() - peerFetchTime;
			System.out.println("===== PEER FETCH TIME: " + peerFetchTime + " ms for " + url + " =====");
//			cacheData(url, contentType, body);
			cacheData(url, contentType, response);

			// needed for Firefox but not Chrome
//			StringBuffer output = new StringBuffer();
//			output.append("HTTP/1.1 200 Document Follows\r\n");
//			output.append("Content-Type: " + contentType + "\r\n");
//			output.append("Content-Length: " + body.length() + "\r\n");
//			output.append("\r\n");
//			output.append(body);
//			response = output.toString();

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (out != null) out.close();
			try {
				if (in != null) in.close();
			} catch (IOException e) {}
		}
		return response;
    }

    /* Serves data from local cache or calles localCacheMiss */
	public String fetchResponseForClient(String url, String request) {
    	// metadata = [fileName, contentType]
    	String response = "";
    	long localFetchTime = System.currentTimeMillis();
    	String[] metadata = sd.cache.get(url);
    	if (metadata == null) {
    		System.out.println(">>> LOCAL CACHE MISS --> distributed GET");
    		return localCacheMiss(url, request);
    	}

    	BufferedReader br = null;
    	try {
			br = new BufferedReader(new FileReader(metadata[0]));
		} catch (FileNotFoundException e) {
			System.out.println(">>> Cached data file not found --> distributed GET");
			return localCacheMiss(url, request);
		}

		int c;
		StringBuilder sb = new StringBuilder();
		try {
			while ((c = br.read()) != -1) {
			    sb.append((char)c) ;
			}
		} catch (IOException e) {
			System.out.println(">>> Error reading cached data --> distributed GET");
			e.printStackTrace();
			return localCacheMiss(url, request);
		} finally {
			try {
				br.close();
			} catch (IOException e) {}
		}
		response = sb.toString();
		localFetchTime = System.currentTimeMillis() - localFetchTime;
		System.out.println("===== LOCAL FETCH TIME: " + localFetchTime + " ms for " + url + " =====");
		System.out.println(">>> LOCAL CACHE HIT");
    	return response;
    }

	public void cacheData(String url, String contentType, String data) {
		// metadata = [fileName, contentType]

        String fileName = url.replaceAll("[ \t\n\r/]", "-");
        String[] metadata = new String[]{fileName, contentType};
        System.out.println(">>> SAVE: " + url + " -> [" + fileName + ", " + contentType + "]");

		PrintWriter writer;
		try {
			writer = new PrintWriter(metadata[0]);
		} catch (FileNotFoundException e) {
			System.err.println("File Not Found. Unable to cache data.");
			return;
		}

		writer.write(data);
		writer.close();

		sd.cache.put(url, metadata);
	}

	/* Sends a GET request to website and returns the response */
    public String getRequest(String url, String originalRequest) {
    	String response = null;
    	URLParser urlParser = new URLParser(url);
    	if (!urlParser.valid) {
    		urlParser = new URLParser("http://" + url);
    		if (!urlParser.valid) {
    			System.err.println("Invalid url: " + url);
    			return response;
    		}
    	}
    	System.out.println(">>> GET " + url);
    	String request = craftRequest(urlParser.domain, urlParser.resource);
    	long sourceFetchTime = System.currentTimeMillis();
        Socket socket = null;
		try {
			socket = new Socket(urlParser.domain, 80);
	        PrintWriter out = new PrintWriter(socket.getOutputStream());
	        BufferedReader in = new BufferedReader(
	            new InputStreamReader(socket.getInputStream()));

			out.println(request);
			out.flush();

			int c;
			StringBuilder sb = new StringBuilder();
			while ((c = in.read()) != -1) {
			    sb.append((char)c) ;
			}
			response = sb.toString();
		} catch (UnknownHostException e) {
			System.err.println("Don't know about host " + urlParser.domain);
		} catch (IOException e) {
			System.err.println("Couldn't get I/O for the connection to " +
					urlParser.domain);
		} finally {
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e) {}
			}
		}
		sourceFetchTime = System.currentTimeMillis() - sourceFetchTime;
		System.out.println("===== SOURCE FETCH TIME: " + sourceFetchTime + " ms for " + url + " =====");
    	return craftResponse(url, response);
    }

	private String craftRequest(String domain, String resource) {
        if (!resource.startsWith("/")) {
            resource = "/" + resource;
        }

		StringBuffer output = new StringBuffer();
		output.append("GET " + resource + " HTTP/1.1\r\n");
		output.append("Host: " + domain + "\r\n");
		output.append("Connection: close\r\n");
		output.append("\r\n");
		return output.toString();
	}


	private String craftResponse(String url, String response) {
		String body = null;
//		String date = null;
		String contentType = null;

		// parse header
        String[] buf = response.split("\r\n");
        for (String line: buf) {
        	if (line.trim().isEmpty()) {
    			break;
    		} else if (line.startsWith("HTTP")) {
    			if (!line.contains("200 OK")) {
    				System.out.println(">>> BAD REQUEST: " + line);
    				return response;
    			}
    		} else if (line.startsWith("Content-Type: ")) {
    			contentType = line.substring(14).trim();
    		}
//    		else if (line.startsWith("Date: ")) {
//    			date = line.substring(6).trim();
//    		}
        }

        // get body
        int bodyPointer = response.indexOf("\r\n\r\n");
        body = response.substring(bodyPointer).trim();

        cacheData(url, contentType, body);

		StringBuffer output = new StringBuffer();
		output.append("HTTP/1.1 200 Document Follows\r\n");
		output.append("Content-Type: " + contentType + "\r\n");
		output.append("Content-Length: " + body.length() + "\r\n");
		output.append("\r\n");
		output.append(body);
		return output.toString();
	}

	public String formatURL(String request) {
		String url = null;
		String referrer = null;
		String[] lines = request.split("\n");
		for (String line: lines) {
			line = line.trim();
			if (line.isEmpty()) {
				break;
			}
			if (line.startsWith("GET ")) {
				String[] temp = line.split(" ");
				url = temp[1].substring(1);
			} else if (line.startsWith("Referer: ")) {
				String[] temp = line.split(ip + ":" + port + "/");
				referrer = temp[temp.length - 1];
				break;
			}
 		}
		if (referrer != null) {
			url = referrer + "/" + url;
			lastReferrer = referrer;
		} else {
			if (url.compareTo("favicon.ico") == 0) {
				if (lastAbsolute != null) {
					url = lastAbsolute + "/" + url;
				} else if (lastReferrer != null) {
					url = lastReferrer + "/" + url;
				} else {
					System.err.println("Unable to fetch favicon.");
				}
			} else {
				lastAbsolute = url;
			}
		}
		return url;
	}

}
