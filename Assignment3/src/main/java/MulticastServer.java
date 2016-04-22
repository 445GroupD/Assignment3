import javafx.util.Pair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

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
    private final Map<String, AppPacket> incominglocalStorage = new HashMap<String, AppPacket>();
    private final Map<Integer, Pair<AppPacket, Integer>> outgoinglocalStorage = new HashMap<Integer, Pair<AppPacket, Integer>>();
    private ServerState serverState = ServerState.FOLLOWER;
    private int majority;
    private String dataToSend;
    private int seqNum = 0;
    private int logIndex = 0;
    private long groupCount = 4;

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
                    debugSend(other[1]);
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
        System.out.println("serverId = " + serverId);
        System.out.println("leaderId = " + leaderId);
        System.out.println("termNum = " + termNum);
        System.out.println("Contents of FakeDB");
        System.out.println("-------------------");
        for (Map.Entry current : fakeDB.entrySet())
        {
            System.out.print("current.getKey() = " + current.getKey());
            System.out.println(" current.getValue() = " + current.getValue());
        }
        System.out.println("-------------------");
    }

    public int getMajority()
    {
        return majority;
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
                System.out.println("serverState = " + serverState);
                System.out.println("dataToSend = " + dataToSend);
                if (serverState.equals(ServerState.LEADER) && dataToSend != null)
                {
                    try
                    {
                        System.out.println("sending: " + dataToSend);
                        AppPacket test = new AppPacket(serverId, AppPacket.PacketType.COMMENT, leaderId, termNum, groupCount, seqNum, logIndex, dataToSend);

                        multicastSocket.send(test.getDatagram(group, port));
                        outgoinglocalStorage.put(test.getSeq(), new Pair(test, logIndex));
                        dataToSend = null;
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
                try
                {
                    Thread.sleep(3000);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
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
                System.out.println("started");
                DatagramPacket packet;
                AppPacket receivedPacket;

                // needs to change
                byte[] buf = new byte[1500];

                // Need to create some way to end the program
                boolean sentinel = true;
                while (sentinel)
                {
                    packet = new DatagramPacket(buf, buf.length, group, port);
                    multicastSocket.receive(packet);
                    receivedPacket = new AppPacket(packet.getData());
                    System.out.println("receivedPacket = " + receivedPacket.getServerId());
                    System.out.println("serverId " + serverId);
                    if (receivedPacket.getServerId() != serverId)
                    {
                        System.out.println("just received");
                        // NOT THE LEADER
                        if (serverId != leaderId)
                        {
                            System.out.println("Not LEADER");
                            if (receivedPacket.getServerId() == leaderId)
                            {
                                System.out.println("size: " + receivedPacket.getData().length);
                                System.out.println("Received: " + new String(receivedPacket.getData(), "UTF-8"));

                                switch (receivedPacket.getType())
                                {
                                    case ACK:
                                    {
                                        System.out.println("SHOULDNT SEE THIS");
                                    }
                                    case COMMENT:
                                    {
                                        AppPacket ackPacket = new AppPacket(serverId, AppPacket.PacketType.ACK, leaderId, 0, 3, receivedPacket.getSeq(), 55, "");
                                        incominglocalStorage.put(receivedPacket.getLogIndex() + " " + receivedPacket.getTerm(), receivedPacket);
                                        multicastSocket.send(ackPacket.getDatagram(group, PORT));
                                        System.out.println("acking");

                                    }
                                    case COMMIT:
                                    {
                                        AppPacket ackPacket = new AppPacket(serverId, AppPacket.PacketType.ACK, leaderId, 0, 3, receivedPacket.getSeq(), 55, "");
                                        AppPacket local = incominglocalStorage.get(receivedPacket.getLogIndex() + " " + receivedPacket.getTerm());
                                        fakeDB.put(local.getLogIndex(), new String(local.getData()));
                                        for (Map.Entry<Integer, String> entry : fakeDB.entrySet())
                                        {
                                            System.out.println("entry = " + entry);
                                        }
//                                        multicastSocket.send(ackPacket.getDatagram(group, PORT));
                                        System.out.println("acking");
                                    }
                                    case PICTURE:
                                    {
                                    }
                                    case GPS:
                                    {
                                    }
                                    case VOTE:
                                    {
                                    }
                                    case HEARTBEAT:
                                    {
                                    }
                                }
                            }
                        }
                        // IS THE LEADER
                        else
                        {
                            AppPacket ackPacket = new AppPacket(packet.getData());
                            switch (ackPacket.getType())
                            {
                                case ACK:
                                {
                                    Pair<AppPacket, Integer> ackedPacket = outgoinglocalStorage.get(ackPacket.getSeq());
                                    outgoinglocalStorage.put(ackedPacket.getKey().getSeq(), new Pair<AppPacket, Integer>(ackedPacket.getKey(), ackedPacket.getValue() + 1));
                                    ackedPacket = outgoinglocalStorage.get(ackPacket.getSeq());
                                    Integer count = ackedPacket.getValue();

                                    int majority = (getMajority() / 2) + 1;
                                    if (count >= majority)
                                    {
                                        fakeDB.put(ackedPacket.getKey().getLogIndex(), new String(ackedPacket.getKey().getData()));
                                        AppPacket commitPacket = new AppPacket(serverId, AppPacket.PacketType.COMMIT, leaderId, termNum, 3, ackedPacket.getKey().getSeq(), ackedPacket.getKey().getLogIndex(), "Committing");
                                        if (commitPacket.getTerm() == ackedPacket.getKey().getTerm())
                                        {
                                            multicastSocket.send(commitPacket.getDatagram(group, PORT));
                                        }
                                        else
                                        {
                                            System.out.println("wrong term cant commit");
                                        }
                                    }
                                }
                                case COMMENT:
                                {

                                }
                                case COMMIT:
                                {
                                }
                                case PICTURE:
                                {
                                }
                                case GPS:
                                {
                                }
                                case VOTE:
                                {
                                }
                                case HEARTBEAT:
                                {
                                }
                            }
                        }
                    }

                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    public enum ServerState
    {
        LEADER(),
        CANIDATE(),
        FOLLOWER();
    }
}