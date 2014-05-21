DistributedProxyCache
=====================

A distributed proxy cache that emulates Apple's Bonjour service to coordinate
with peers.

RUN: java -jar dcache.jar {port}
Example: java -jar dcache.jar 9999

This will output: "Usage: Go to {ip}:{port}/{url} in your browser."
Going to http://192.168.1.7:9999/www.google.com in your browser will return
www.google.com as served from the distributed web cache.

Service Discovery is done with an MDNS query that serves to both announce
a cache presence and browse for other caches on the smmorneau-cache service.
This is an Exponential Back-off and Service Announcement because it occurs
after 1 second, 3 seconds, 9 seconds, 27 seconds, and so on, up to a maximum
interval of one hour. In order to achieve Suppression of Duplicate Responses,
each cache only answers for itself, replying to a cache's browse only if it has
never seen that cache before.

Page Retrieval is done by first checking your local cache, and serving the
content to the user on a cache hit. On a cache miss, use unicast queries to
each of the peer caches to see if one has a cached version of the desired url.
If a negative response is received, move on to the next peer until a positive
response is received or there are no more peer caches, then perform the GET
request to the web server yourself. If a positive response is received, we
query the peer as if we were a normal web client, and return that response to
our client.