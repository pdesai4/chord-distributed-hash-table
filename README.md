**Chord Distributed Hash Table**

Create a basic distributed hash table (DHT) with an architecture similar to the Chord

system.



**Compiling and Execution:**

	$ ./server *portNumber*
	This will run the server at the given port. (The executable is a bash script)

After running the servers, create a text file with the server ip and port written in the format <ip>:<port>. And run ./init *text file* to generate finger table for each server node.

**Implementation:**

Server.java -
	This class implements the server at the local IP address and command line specified port number. The `javaServer()` method in this class enables a server transport at the given host ip and port number.

ProcessRequest -
	This class contains all the methods from the chord.thrift interface. This class is responsible for processing all the client requests to the server.