import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class MulticastServer
{
    private static final int PORT = 4446;
    private static final String GROUP = "239.255.255.255";
    private final MulticastSocket multicastSocket;
    private final InetAddress group;
    private final int serverId;

    public MulticastServer(String serverId) throws IOException
    {
        multicastSocket = new MulticastSocket(4446);
        group = InetAddress.getByName("239.255.255.255");
        multicastSocket.joinGroup(group);
        if (serverId == null)
        {
            throw new NullPointerException("ServerId was null");
        }
        else
        {
            this.serverId = Integer.valueOf(serverId);
        }
    }

    public void startServer()
    {
        boolean sentinel = true;
        while (sentinel)
        {

        }
    }

    private class MulticastServerReceiver implements Runnable
    {
        private final MulticastSocket multicastSocket;
        private final InetAddress group;
        private final int port;
        private final String serverId;

        public MulticastServerReceiver(MulticastSocket multicastSocket, InetAddress group, int port, String serverId)
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

                // needs to change
                byte[] buf = new byte[64000];

                // Need to create some way to end the program
                boolean sentinel = true;
                while (sentinel)
                {
                    packet = new DatagramPacket(buf, buf.length, group, port);
                    multicastSocket.receive(packet);
                    receivedPacket = new AppPacket(packet.getData());

                    // WE NEED TO WRITE THIS
                    receivedPacket.getData();
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }
}
