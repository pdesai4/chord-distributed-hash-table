package chordDistributedHashTable;

import chordDistributedHashTable.gen_java.FileStore;
import chordDistributedHashTable.gen_java.NodeID;
import chordDistributedHashTable.gen_java.RFile;
import chordDistributedHashTable.gen_java.RFileMetadata;
import chordDistributedHashTable.gen_java.SystemException;
import com.sun.org.apache.xerces.internal.impl.dv.util.HexBin;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ProcessRequest implements FileStore.Iface {

    private ArrayList<NodeID> fingerTable;
    private int serverNodePort;
    private String serverNodeIp;
    private String serverNodeId;
    private Map<String, RFile> fileStore;

    ProcessRequest(int portNumber, String hostAddress) {
        fingerTable = new ArrayList<>();
        fileStore = new HashMap<>();
        serverNodeIp = hostAddress;
        serverNodePort = portNumber;
        String serverNode = hostAddress + ":" + portNumber;
        // Calculate server id
        try {
            MessageDigest nodeDigest = MessageDigest.getInstance("SHA-256");
            nodeDigest.update(serverNode.getBytes());
            byte[] nodeSHA = nodeDigest.digest();
            serverNodeId = HexBin.encode(nodeSHA).toLowerCase();

        } catch (NoSuchAlgorithmException e) {
            System.err.println("Error: Incorrect Hashing Algorithm stated");
        }

    }

    @Override
    public void writeFile(RFile rFile) throws SystemException, TException {
        // Check if the server is responsible for the file
        serverOwnsFile(rFile.meta.owner, rFile.meta.filename);
        if (fileStore.containsKey(rFile.meta.filename)) {
            // Requested file already exists
            RFile newRFile = fileStore.get(rFile.getMeta().getFilename());
            RFileMetadata newMetaData = newRFile.getMeta();
            newMetaData.setVersion(newMetaData.getVersion() + 1);
            newRFile.setMeta(newMetaData);
            newRFile.setContent(rFile.getContent());
            fileStore.put(rFile.getMeta().getFilename(), newRFile);

        } else {
            // Create new entry
            RFile newRFile = new RFile();
            RFileMetadata newMetaData = new RFileMetadata();
            newMetaData.setFilename(rFile.getMeta().getFilename());
            newMetaData.setVersion(0);
            newMetaData.setOwner(rFile.getMeta().getOwner());
            newMetaData.setContentHash(rFile.getMeta().getContentHash());
            newRFile.setMeta(newMetaData);
            newRFile.setContent(rFile.getContent());
            fileStore.put(rFile.getMeta().getFilename(), newRFile);
        }
    }

    @Override
    public RFile readFile(String filename, String owner) throws SystemException, TException {
        // Check if the server is responsible for the file
        if (serverOwnsFile(owner, filename)) {
            if (fileStore.containsKey(filename) && fileStore.get(filename).meta.owner.equals(owner)) {
                return fileStore.get(filename);
            } else {
                SystemException exception = new SystemException();
                exception.setMessage("Error: File trying to access is absent");
                throw exception;
            }
        }
        return null;
    }

    /**
     * Checks if the requested file will we available at the server node or not
     *
     * @param owner    Owner of the file
     * @param filename Name of the files
     * @return Returns true if the requested file should be at the server node, else false
     */
    private boolean serverOwnsFile(String owner, String filename) throws SystemException, TException {
        String fileSHA = owner + ":" + filename;
        try {
            // Calculate SHA-256 for the file
            MessageDigest fileDigest = MessageDigest.getInstance("SHA-256");
            fileDigest.update(fileSHA.getBytes());
            byte[] nodeSHA = fileDigest.digest();
            String fileId = HexBin.encode(nodeSHA).toLowerCase();
            // Check if server node is the successor for the file
            if (!findSucc(fileId).id.equals(serverNodeId)) {
                SystemException exception = new SystemException();
                exception.setMessage("Error: Requested server does not have access to the file");
                throw exception;
            }
            return true;

        } catch (NoSuchAlgorithmException e) {
            System.err.println("Error: Incorrect Hashing Algorithm stated");
        }
        return false;
    }

    @Override
    public void setFingertable(List<NodeID> node_list) throws TException {
        fingerTable = new ArrayList<>(node_list);
    }

    @Override
    public NodeID findSucc(String key) throws SystemException, TException {
        NodeID predNode = findPred(key);
        NodeID keyNode;
        // If the predecessor node is the server node itself
        if (predNode.id.equals(serverNodeId)) {
            keyNode = getNodeSucc();

        } else {
            // Create client at the predecessor node
            TSocket transport = new TSocket(predNode.ip, predNode.port);
            transport.open();
            TProtocol protocol = new TBinaryProtocol(transport);
            FileStore.Client client = new FileStore.Client(protocol);
            keyNode = client.getNodeSucc();
            transport.close();
        }
        NodeID succNode;
        // If the successor node is the server node itself
        if (keyNode.id.equals(serverNodeId)) {
            succNode = getNodeSucc();

        } else {
            TSocket transport_1 = new TSocket(keyNode.ip, keyNode.port);
            transport_1.open();
            TProtocol protocol_1 = new TBinaryProtocol(transport_1);
            FileStore.Client client_1 = new FileStore.Client(protocol_1);
            succNode = client_1.getNodeSucc();
            transport_1.close();
        }

        return succNode;
    }

    @Override
    public NodeID getNodeSucc() throws SystemException, TException {
        if (fingerTable.get(0) == null) {
            SystemException exception = new SystemException();
            exception.setMessage("Error: No finger table exists for the current node");
            throw exception;
        }
        return fingerTable.get(0);
    }

    /**
     * Takes three strings and determines if the key string is between the other two strings by value, inclusive of the
     * greater string value
     *
     * @param nodeA String to be compared
     * @param nodeB String to be compared
     * @param keyId Key string to check if lies between the other two strings, by value
     * @return Returns true if the key string lies between the other two given strings, else false
     */
    private boolean isKeyBetween(String nodeA, String nodeB, String keyId) {
        if (nodeA.compareTo(nodeB) == 0) {
            return true;
        } else if (nodeA.compareTo(nodeB) < 0) {
            return nodeA.compareTo(keyId) < 0 && keyId.compareTo(nodeB) <= 0;
        } else {
            return nodeB.compareTo(keyId) >= 0 || keyId.compareTo(nodeA) > 0;
        }
    }

    /**
     * Takes three strings and determines if the entry string is between the other two strings by value, exclusive of
     * both the other string values
     *
     * @param nodeA   String to be compared
     * @param nodeB   String to be compared
     * @param entryId Entry string to check if lies between the other two strings, by value
     * @return Returns true if the entry string lies between the other two given strings, else false
     */
    private boolean isEntryBetween(String nodeA, String nodeB, String entryId) {
        if (nodeA.compareTo(nodeB) == 0) {
            return true;
        } else if (nodeA.compareTo(nodeB) < 0) {
            return nodeA.compareTo(entryId) < 0 && entryId.compareTo(nodeB) < 0;
        } else {
            return nodeB.compareTo(entryId) > 0 || entryId.compareTo(nodeA) > 0;
        }
    }

    @Override
    public NodeID findPred(String key) throws SystemException, TException {
        String nodeSuccId = getNodeSucc().id;

        // Check if key is between the server node and its successor
        boolean isBetween = isKeyBetween(serverNodeId, nodeSuccId, key);
        if (isBetween) {
            // if yes, then return the server node
            NodeID predNode = new NodeID();
            predNode.port = serverNodePort;
            predNode.ip = serverNodeIp;
            predNode.id = serverNodeId;
            return predNode;

        } else {
            // Check for the node from the last entry of the server node finger table
            for (int i = fingerTable.size() - 1; i > -1; i--) {
                if (fingerTable.get(i).id.compareTo(serverNodeId) == 0) {
                    continue;
                }
                // Check if the entry node is between the server node and the key
                if (isEntryBetween(serverNodeId, key, fingerTable.get(i).id)) {
                    // Create a client at the selected entry node of the finger table
                    TSocket transport = new TSocket(fingerTable.get(i).ip, fingerTable.get(i).port);
                    transport.open();
                    TProtocol protocol = new TBinaryProtocol(transport);
                    FileStore.Client client = new FileStore.Client(protocol);
                    NodeID predNode = client.findPred(key);
                    transport.close();
                    return predNode;
                }
            }
        }
        System.err.println("Error: Key not found");
        throw new SystemException();
    }
}
