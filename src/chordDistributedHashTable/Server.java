package chordDistributedHashTable;

import chordDistributedHashTable.gen_java.FileStore;
import org.apache.log4j.BasicConfigurator;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TSimpleServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;

import java.net.InetAddress;

public class Server {

    private static FileStore.Processor<ProcessRequest> asyncProcessor;

    public static void main(String[] args) {
        BasicConfigurator.configure();
        try {
            int portNumber = Integer.parseInt(args[0]);
            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            ProcessRequest processRequest = new ProcessRequest(portNumber, hostAddress);
            asyncProcessor = new FileStore.Processor<>(processRequest);
            Runnable serverRunnable = () -> javaServer(asyncProcessor, portNumber, hostAddress);
            new Thread(serverRunnable).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Enables a server transport at the requested host address and port number
     *
     * @param asyncProcessor {@link FileStore.Processor} object
     * @param portNumber     Server port number
     * @param hostAddress    Server host address
     */
    private static void javaServer(FileStore.Processor<ProcessRequest> asyncProcessor, Integer portNumber, String hostAddress) {
        try {
            TServerTransport serverTransport = new TServerSocket(portNumber);
            TServer server = new TSimpleServer(new TServer.Args(serverTransport).processor(asyncProcessor));
            System.out.println("Starting the simple server at port " + portNumber + " and with host " + hostAddress);
            server.serve();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
