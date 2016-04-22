import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MulticastServer
{
    private static final int PORT = 4446;
    private static final String GROUP = "239.255.255.255";
    private final MulticastSocket multicastSocket;
    private final InetAddress group;
    private final int serverId;
    private int leaderId;
    private int termNum;
    private final Map<Integer, String> fakeDB = new HashMap<Integer, String>();
    private final Map<String, AppPacket> incominglocalStorage = new ConcurrentHashMap<String, AppPacket>();
    private final Map<Integer, LeaderPacket> outgoinglocalStorage = new ConcurrentHashMap<Integer, LeaderPacket>();
    private ServerState serverState = ServerState.FOLLOWER;
    private String dataToSend;
    private long groupCount = 5;

    public MulticastServer(String serverId, String leaderId) throws IOException
    {
        this.serverId = Integer.valueOf(serverId);
        this.leaderId = Integer.valueOf(leaderId);
        this.termNum = 0;
        System.out.println("serverId = " + serverId);
        System.out.println("leaderId = " + leaderId);
        if (this.serverId == this.leaderId)
        {
            System.out.println("IS LEADER");
            serverState = ServerState.LEADER;
        }

        // Create Socket
        multicastSocket = new MulticastSocket(PORT);
        group = InetAddress.getByName(GROUP);
        multicastSocket.joinGroup(group);

        startSendingThread();
        startReceivingThread();
        startDebugConsole();
    }


    private void startSendingThread()
    {

        Thread outgoing = new Thread(new MulticastServerSender(multicastSocket, group, PORT, serverId));
        outgoing.start();
        System.out.println("started outgoing thread");
    }

    private void startReceivingThread()
    {

        Thread incoming = new Thread(new MulticastServerReceiver(multicastSocket, group, PORT, serverId));
        incoming.start();
        System.out.println("started incoming thread");
    }

    private void startDebugConsole()
    {
        try
        {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            while (true)
            {
                System.out.println("What do you want to see");
                String[] other = in.readLine().split(" ");

                if (other[0].equals("status"))
                {
                    debugStatus();
                }
                else if (other[0].equals("send"))
                {
                    System.out.println("Data to send: ");
                    debugSend(in.readLine());
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
        dataToSend = debugData;
    }

    private void debugStatus()
    {
        System.out.println("\n----------------------------- START SERVER STATUS ---------------------------\n");
        System.out.println("\tid = " + serverId);
        System.out.println("\tleaderId = " + leaderId);
        System.out.println("\ttermNum = " + termNum);
        System.out.println("\tContents of FakeDB");

        System.out.println("\n\t------------------- Server DB Logs -------------------");
        for (Map.Entry current : fakeDB.entrySet())
        {
            System.out.printf("\n\tLog Index: %s | Log: %s", current.getKey(), current.getValue());
        }
        System.out.println("\n----------------------------- END SERVER STATUS ---------------------------\n");
    }

    public int getMajority()
    {
        return (int)Math.floor(groupCount / 2);
    }

    private class MulticastServerSender implements Runnable
    {
        private final MulticastSocket multicastSocket;
        private final InetAddress group;
        private final int port;
        private final int serverId;

        public MulticastServerSender(MulticastSocket multicastSocket, InetAddress group, int port, int serverId)
        {
            this.multicastSocket = multicastSocket;
            this.group = group;
            this.port = port;
            this.serverId = serverId;
        }

        @Override
        public void run()
        {
            while (true)
            {
                if (serverState.equals(ServerState.LEADER) && dataToSend != null)
                {
                    try
                    {
                        AppPacket outgoingPacket = new AppPacket(serverId, AppPacket.PacketType.COMMENT, leaderId, termNum, -1, LeaderPacket.getNextSequenceNumber(), -1, dataToSend);
                        outgoinglocalStorage.put(outgoingPacket.getSequenceNumber(), new LeaderPacket(outgoingPacket));

                        consoleMessage("\nSending " + outgoingPacket.toString());
                        multicastSocket.send(outgoingPacket.getDatagram(group, port));
                        dataToSend = null;
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
                try
                {
                    Thread.sleep(1000);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
        }

        private void consoleMessage(String s) {
            System.out.println(serverId + " m> " + s);
        }

        private void consoleError(String s) {
            System.out.println(serverId + " e> " + s);
        }
    }

    private class MulticastServerReceiver implements Runnable
    {
        private final MulticastSocket multicastSocket;
        private final InetAddress group;
        private final int port;
        private final int serverId;

        public MulticastServerReceiver(MulticastSocket multicastSocket, InetAddress group, int port, int serverId)
        {
            this.multicastSocket = multicastSocket;
            this.group = group;
            this.port = port;
            this.serverId = serverId;
        }

        @Override
        public void run()
        {
            try
            {
                DatagramPacket packet;
                AppPacket receivedPacket;
                byte[] buf;

                // Need to create some way to end the program
                boolean sentinel = true;
                while (sentinel)
                {
                    buf = new byte[1500];
                    packet = new DatagramPacket(buf, buf.length, group, port);
                    multicastSocket.receive(packet);
                    receivedPacket = new AppPacket(packet.getData());
                    if (serverState.equals(ServerState.LEADER))
                    {
                        leaderParse(receivedPacket);
                    }
                    else if (serverState.equals(ServerState.FOLLOWER))
                    {
                        followerParse(receivedPacket);
                    }
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    /**
     * Used for servers who are receiving packets from the leader.
     * This is a follower of the leader.
     *
     * It receives commit requests and commands from leading servers, in that order,
     * acks to confirm its agreement that the sender is the leader of the group,
     * and if so, it replicates the leader's incoming log to its own db when it receives the commit command from leader.
     */
    private void followerParse(AppPacket receivedPacket)
    {
        try
        {
            // make sure the packet is from the leader
            if (receivedPacket.getServerId() == leaderId)
            {
                switch (receivedPacket.getType())
                {
                    case ACK:
                        System.out.println("SHOULDNT SEE THIS");
                        break;
                    case COMMENT:
                        AppPacket ackPacket = new AppPacket(serverId, AppPacket.PacketType.ACK, leaderId,termNum ,groupCount, receivedPacket.getSequenceNumber(), receivedPacket.getLogIndex(), "");
                        incominglocalStorage.put(getIncomingStorageKey(receivedPacket), receivedPacket);
                        multicastSocket.send(ackPacket.getDatagram(group, PORT));
                        System.out.println("Acking commit request confirmation for " + receivedPacket.toString());
                        break;
                    case COMMIT:
                        AppPacket localPacketFromIncomingStorage = incominglocalStorage.get(getIncomingStorageKey(receivedPacket));
                        String receivedLogIndex = receivedPacket.getReadableData();
                        String actualDataFromIncomingStorage = localPacketFromIncomingStorage.getReadableData();

                        fakeDB.put(Integer.parseInt(receivedLogIndex), actualDataFromIncomingStorage);
                        System.out.println("Committed Packet: #%s" + localPacketFromIncomingStorage.toString());
                        break;
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private String getIncomingStorageKey(AppPacket receivedPacket) {
        return receivedPacket.getLeaderId() + " " + receivedPacket.getSequenceNumber() + " " + receivedPacket.getTerm();
    }


    /**
     *
     * @param receivedPacket
     */
    private void leaderParse(AppPacket receivedPacket)
    {
        try
        {
            switch (receivedPacket.getType())
            {
                case ACK:
                    LeaderPacket ackedLeaderPacket = outgoinglocalStorage.get(receivedPacket.getSequenceNumber());

                    int committedLogIndex = ackedLeaderPacket.confirm(getMajority(), fakeDB);
                    //make sure the log index returned from committing is valid
                    if (committedLogIndex > -1)
                    {
                        //all is well. The log was committed to this leader's persistent db at the committedLogIndex.
                        //send the commit command to all followers if necessary.

                        //we send the current term number of the leader because if it doesn't match what the followers have this packet stored as, they should not commit it to their db
                        AppPacket commitPacket = new AppPacket(serverId, AppPacket.PacketType.COMMIT, leaderId, termNum, groupCount, ackedLeaderPacket.getSequenceNumber(), committedLogIndex, committedLogIndex + "");
                        if (termNum == ackedLeaderPacket.getTerm())
                        {
                            //send the commit command to all followers of this leader
                            multicastSocket.send(commitPacket.getDatagram(group, PORT));
                        }
                        else
                        {
                            //this leader is on the wrong term and thus may or may not be the leader anymore
                            System.out.println("Leader is in the wrong term. Cant commit");
                        }
                    }
                    break;
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public enum ServerState
    {
        LEADER(),
        CANIDATE(),
        FOLLOWER();
    }
}
