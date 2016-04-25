import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

/**
 * Created by Lincoln W Daniel on 4/22/2016.
 */
public class MulticastServerReceiver implements Runnable
{
    private final MulticastServer server;

    public MulticastServerReceiver(MulticastServer server)
    {
        this.server = server;
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
                packet = new DatagramPacket(buf, buf.length, server.getGroup(), server.getPort());
                server.getMulticastSocket().receive(packet);
                receivedPacket = new AppPacket(packet.getData());
                if (server.isLeader())
                {
                    server.leaderParse(receivedPacket);
                }
                else if (server.isFollower())
                {
                    server.followerParse(receivedPacket);
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void rest() {
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