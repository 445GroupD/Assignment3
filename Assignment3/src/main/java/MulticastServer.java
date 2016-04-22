import javafx.util.Pair;

import java.io.IOException;
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
    private int majority;

    public MulticastServer(String serverId, String leaderId) throws IOException
    {
        System.out.println("constructor");
        multicastSocket = new MulticastSocket(4446);
        multicastSocket.setLoopbackMode(false);
        group = InetAddress.getByName("239.255.255.255");
        this.leaderId = Integer.valueOf(leaderId);
        this.termNum = 0;
        multicastSocket.joinGroup(group);
        if (serverId == null)
        {
            throw new NullPointerException("ServerId was null");
        }
        else
        {
            this.serverId = Integer.valueOf(serverId);
        }

        System.out.println("starting server");
        startServer();
    }

    public void startServer()
    {
        System.out.println("starting");
        Thread receive = new Thread(new MulticastServerReceiver(multicastSocket, group, PORT, serverId));
        receive.start();
        boolean sentinel = true;
        while (sentinel)
        {
            System.out.println("entered loop");
            if (serverId == leaderId)
            {
                System.out.println("leader");
                String j = "hello";
                AppPacket test = new AppPacket(serverId, AppPacket.PacketType.PICTURE, leaderId, 45, 3, 12, 55, "hello");
                try
                {
                    System.out.println("before send");
                    multicastSocket.send(test.getDatagram(group, PORT));
                    outgoinglocalStorage.put(test.getSeq(), new Pair(test, 0));
                    System.out.println("sending data: " + new String(test.getData(), "UTF-8"));
                    System.out.println("just sent");
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
            sentinel = false;
        }
    }

    public int getMajority()
    {
        majority = 2;
        return majority;
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

                                    }
                                    case COMMIT:
                                    {
                                        AppPacket ackPacket = new AppPacket(serverId, AppPacket.PacketType.ACK, leaderId, 0, 3, receivedPacket.getSeq(), 55, "");
                                        AppPacket local = incominglocalStorage.get(receivedPacket.getLogIndex() + " " + receivedPacket.getTerm());
                                        fakeDB.put(local.getLogIndex(), new String(local.getData()));
                                        multicastSocket.send(ackPacket.getDatagram(group, PORT));
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
                            System.out.println("is leader");
                            AppPacket ackPacket = new AppPacket(packet.getData());
                            switch (ackPacket.getType())
                            {
                                case ACK:
                                {
                                    Pair<AppPacket, Integer> ackedPacket = outgoinglocalStorage.get(ackPacket.getSeq());
                                    Integer count = ackedPacket.getValue();
                                    count++;
                                    System.out.println("count " + count);

                                    int majority = (getMajority() / 2) + 1;
                                    System.out.println("majority = " + majority);
                                    if (count >= majority)
                                    {
                                        System.out.println("committing");
                                        fakeDB.put(ackedPacket.getKey().getLogIndex(), new String(ackedPacket.getKey().getData()));
                                        AppPacket commitPacket = new AppPacket(serverId, AppPacket.PacketType.COMMIT, leaderId, termNum, 3, ackedPacket.getKey().getSeq(), ackedPacket.getKey().getLogIndex(), "");
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
}
