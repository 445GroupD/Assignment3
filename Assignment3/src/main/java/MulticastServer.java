import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import static java.lang.String.format;

public class MulticastServer
{
    private static final DateFormat DEFAULT_DATE_FORMAT = new SimpleDateFormat("HH:mm:ss");

    private static final int PORT = 4446;
    private static final String GROUP_IP = "239.255.255.255";

    private final int serverId;
    private final MulticastSocket multicastSocket;
    private final InetAddress group;
    private final long groupCount = 3;

    private final Map<Integer, String> fakeDB = new HashMap<Integer, String>();
    private final Map<Integer, LeaderPacket> outgoingLocalStorage = new ConcurrentHashMap<Integer, LeaderPacket>();
    private final LinkedBlockingQueue<String> linkedBlockingClientMessageQueue = new LinkedBlockingQueue<String>();

    private final Map<Integer, Integer> followerStatusMap = new ConcurrentHashMap<Integer, Integer>();

    private ServerState serverState = ServerState.FOLLOWER;
    private int leaderId;
    private int term;
    private int latestLogIndex = 0;

    private String outgoingData;

    private boolean heartbeatDebug = false;


    public MulticastServer(int serverId, int leaderId) throws IOException
    {
        this.serverId = serverId;
        this.leaderId = leaderId;
        this.term = 0;
        System.out.println("serverId = " + serverId);
        System.out.println("leaderId = " + leaderId);
        if (this.serverId == this.leaderId)
        {
            consoleMessage("IS LEADER");
            serverState = ServerState.LEADER;
        }

        // Create Socket
        multicastSocket = new MulticastSocket(PORT);
        group = InetAddress.getByName(GROUP_IP);
        multicastSocket.joinGroup(group);

        startSendingThread();
        startReceivingThread();
        startHeartbeatThread();
        startDebugConsole();
    }

    private void startHeartbeatThread()
    {
        Thread heartbeat = new Thread(new MulticastHeartbeatSender(this));
        heartbeat.start();
        consoleMessage("started heartbeat thread");
    }


    private void startSendingThread()
    {

        Thread outgoing = new Thread(new MulticastServerSender(this));
        outgoing.start();
        consoleMessage("started outgoing thread");
    }

    private void startReceivingThread()
    {

        Thread incoming = new Thread(new MulticastServerReceiver(this));
        incoming.start();
        consoleMessage("started incoming thread");
    }

    private void startDebugConsole()
    {
        try
        {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            while (true)
            {
                consolePrompt("What do you want to do: ");
                String[] other = in.readLine().split(" ");

                if (other[0].equals("status"))
                {
                    debugStatus();
                }
                else if (other[0].equals("send"))
                {
                    consolePrompt("Data to send: ");
                    debugSend(in.readLine());
                }
                else if (other[0].equals("heartbeat"))
                {
                    heartbeatDebug = !heartbeatDebug;
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void debugSend(String debugData)
    {
        try
        {
            linkedBlockingClientMessageQueue.put(debugData);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    private void debugStatus()
    {
        consoleMessage("\n----------------------------- START SERVER STATUS ---------------------------\n");
        consoleMessage("\tid = " + serverId);
        consoleMessage("\tleaderId = " + leaderId);
        consoleMessage("\tterm = " + term);
        consoleMessage("\tContents of FakeDB");

        consoleMessage("\n\t------------------- Server DB Logs -------------------");
        for (Map.Entry current : fakeDB.entrySet())
        {
            consoleMessage(format("\n\tLog Index: %s | Log: %s", current.getKey(), current.getValue()));
        }
        consoleMessage("\n----------------------------- END SERVER STATUS ---------------------------\n");
    }

    public int getMajority()
    {
        return (int) Math.floor(groupCount / 2);
    }

    public ServerState getServerState()
    {
        return serverState;
    }

    public boolean isFollower()
    {
        return serverState.equals(ServerState.FOLLOWER);
    }

    public boolean isLeader()
    {
        return serverState.equals(ServerState.LEADER);
    }

    public Map<String, AppPacket> getIncomingLocalStorage()
    {
        return incomingLocalStorage;
    }

    private final Map<String, AppPacket> incomingLocalStorage = new ConcurrentHashMap<String, AppPacket>();

    public Map<Integer, LeaderPacket> getOutgoingLocalStorage()
    {
        return outgoingLocalStorage;
    }


    /**
     * Used for servers who are receiving packets from the leader.
     * This is a follower of the leader.
     * <p/>
     * It receives commit requests and commands from leading servers, in that order,
     * acks to confirm its agreement that the sender is the leader of the group,
     * and if so, it replicates the leader's incoming log to its own db when it receives the commit command from leader.
     */
    public void followerParse(AppPacket receivedPacket)
    {
        try
        {
            // make sure the packet is from the leader
            if (receivedPacket.getServerId() == leaderId)
            {
                switch (receivedPacket.getType())
                {
                    case ACK:
                        consoleError("SHOULDNT SEE THIS");
                        break;
                    case COMMENT:
                        AppPacket ackPacket = new AppPacket(serverId, AppPacket.PacketType.ACK, leaderId, term, groupCount, receivedPacket.getSequenceNumber(), receivedPacket.getLogIndex(), "");
                        incomingLocalStorage.put(getIncomingStorageKey(receivedPacket), receivedPacket);
                        multicastSocket.send(ackPacket.getDatagram(group, PORT));
                        consoleMessage("Acking commit request confirmation for " + receivedPacket.toString());
                        break;
                    case COMMIT:
                        AppPacket localPacketFromIncomingStorage = incomingLocalStorage.get(getIncomingStorageKey(receivedPacket));
                        String receivedLogIndex = receivedPacket.getReadableData();
                        String actualDataFromIncomingStorage = localPacketFromIncomingStorage.getReadableData();

                        fakeDB.put(Integer.parseInt(receivedLogIndex), actualDataFromIncomingStorage);
                        consoleMessage("Committed Packet: #%s" + localPacketFromIncomingStorage.toString());
                        latestLogIndex = receivedPacket.getLogIndex();
                        break;
                    case HEARTBEAT:
                        parseHeartbeat(receivedPacket);
                        break;
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void parseHeartbeat(AppPacket receivedPacket) throws IOException
    {
        if(receivedPacket.getLogIndex() == latestLogIndex +1)
        {
            //commitDataToDB()
            latestLogIndex = receivedPacket.getLogIndex();
        }
        AppPacket heartbeatAckPacket = new AppPacket(serverId, AppPacket.PacketType.HEARTBEAT_ACK, leaderId, term, groupCount, -1, latestLogIndex, latestLogIndex+"");
        multicastSocket.send(heartbeatAckPacket.getDatagram(group, PORT));
        if (heartbeatDebug)
        {
            consoleMessage("Send HeartbeatAck: with latest index " + getLatestLogIndex());
        }
    }

    private String getIncomingStorageKey(AppPacket receivedPacket)
    {
        return receivedPacket.getLeaderId() + " " + receivedPacket.getSequenceNumber() + " " + receivedPacket.getTerm();
    }


    /**
     * @param receivedPacket
     */
    public void leaderParse(AppPacket receivedPacket)
    {
        try
        {
            switch (receivedPacket.getType())
            {
                case ACK:
                    LeaderPacket ackedLeaderPacket = outgoingLocalStorage.get(receivedPacket.getSequenceNumber());

                    int committedLogIndex = ackedLeaderPacket.confirm(getMajority(), fakeDB);
                    //make sure the log index returned from committing is valid
                    if (committedLogIndex > -1)
                    {
                        consoleMessage("\nLeader Committed " + ackedLeaderPacket.toString());
                        latestLogIndex++;

                        //all is well. The log was committed to this leader's persistent db at the committedLogIndex.
                        //send the commit command to all followers if necessary.

                        //we send the current term number of the leader because if it doesn't match what the followers have this packet stored as, they should not commit it to their db
                        AppPacket commitPacket = new AppPacket(serverId, AppPacket.PacketType.COMMIT, leaderId, term, groupCount, ackedLeaderPacket.getSequenceNumber(), committedLogIndex, committedLogIndex + "");
                        if (term == ackedLeaderPacket.getTerm())
                        {
                            //send the commit command to all followers of this leader
                            multicastSocket.send(commitPacket.getDatagram(group, PORT));
                        }
                        else
                        {
                            //this leader is on the wrong term and thus may or may not be the leader anymore
                            consoleError("Leader is in the wrong term. Cant commit");
                        }
                    }
                    break;
                case HEARTBEAT_ACK:
                    followerStatusMap.put(receivedPacket.getServerId(),receivedPacket.getLogIndex());
                    if(heartbeatDebug)
                    {
                        consoleMessage("received HeartbeatAck from " + receivedPacket.getServerId() + " with latest log index of " + receivedPacket.getLogIndex());
                    }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public String getOutgoingData()
    {
        return outgoingData;
    }

    public int getLeaderId()
    {
        return leaderId;
    }

    public int getTerm()
    {
        return term;
    }

    public InetAddress getGroup()
    {
        return group;
    }

    public int getPort()
    {
        return PORT;
    }

    public int getId()
    {
        return serverId;
    }

    public MulticastSocket getMulticastSocket()
    {
        return multicastSocket;
    }

    public void clearOutgoingData()
    {
        outgoingData = "";
    }

    public String getClientMessageToSend()
    {
        try
        {
            return linkedBlockingClientMessageQueue.take();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        return null;
    }

    public boolean getHeartbeatDebug()
    {
        return heartbeatDebug;
    }

    public int getLatestLogIndex()
    {
        return latestLogIndex;
    }

    public Map<Integer, Integer> getFollowerStatusMap()
    {
        return followerStatusMap;
    }


    public enum ServerState
    {
        LEADER(),
        CANIDATE(),
        FOLLOWER();
    }

    protected void consoleMessage(String s)
    {
        //m stands for message
        System.out.println(getCurrentDateTime(null) + " #" + serverId + "|m> " + s);
    }

    protected void consoleError(String s)
    {
        //e stands for error
        System.out.println(getCurrentDateTime(null) + " #" + serverId + "|e> " + s);
    }

    protected void consolePrompt(String s)
    {
        //p stands for prompt
        System.out.println(getCurrentDateTime(null) + " #" + serverId + "|p> " + s);
    }

    public static String getCurrentDateTime(DateFormat dateFormat)
    {
        dateFormat = dateFormat != null ? dateFormat : DEFAULT_DATE_FORMAT;
        return dateFormat.format(new Date());
    }
}
