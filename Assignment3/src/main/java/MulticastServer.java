import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.ByteBuffer;

public class MulticastServer
{
    private static final int PORT = 4446;
    private static final String GROUP = "239.255.255.255";
    private final MulticastSocket multicastSocket;
    private final InetAddress group;
    private final int serverId;
    private final int leaderId;

    public MulticastServer(String serverId, String leaderId) throws IOException
    {
        System.out.println("constructor");
        multicastSocket = new MulticastSocket(4446);
        group = InetAddress.getByName("239.255.255.255");
        this.leaderId = Integer.valueOf(leaderId);
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
                AppPacket test = new AppPacket(serverId, AppPacket.PacketType.PICTURE, leaderId, 45, 3, 12, "hello");
                try
                {
                    System.out.println("before send");
                    multicastSocket.send(test.getDatagram(group, PORT));
                    System.out.println("sending data: " + new String(test.getData(),"UTF-8"));
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
                    // NOT THE LEADER
                    if (serverId != leaderId)
                    {
                        packet = new DatagramPacket(buf, buf.length, group, port);
                        multicastSocket.receive(packet);
                        receivedPacket = new AppPacket(packet.getData());
                        System.out.println("leaderId = " + leaderId);
                        System.out.println("receivedPacket.getServerId() = " + receivedPacket.getServerId());
                        if (receivedPacket.getServerId() == leaderId)
                        {
                            System.out.println("size: "+receivedPacket.getData().length);
                            System.out.println("Received: " + new String(receivedPacket.getData(),"UTF-8"));
                            AppPacket ackPacket = new AppPacket(serverId, AppPacket.PacketType.ACK, leaderId, 0, 3, receivedPacket.getSeq(), "");

                            multicastSocket.send(ackPacket.getDatagram(group, PORT));
                        }
                    }
                    // IS THE LEADER
                    else
                    {
                        packet = new DatagramPacket(buf, buf.length, group, port);
                        multicastSocket.receive(packet);

                        AppPacket ackPacket = new AppPacket(packet.getData());

                        System.out.println(ackPacket.getSeq());
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
