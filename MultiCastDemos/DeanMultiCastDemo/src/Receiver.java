import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;

public class Receiver {
    public static void main(String[] args) {
        try {
            MulticastSocket socket = new MulticastSocket(4446);
            InetAddress group = InetAddress.getByName("239.255.255.255");
            socket.joinGroup(group);

            DatagramPacket packet;
            boolean sentinel = true;
            while(sentinel) {
                byte[] buf = new byte[512];
                packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                String received = new String(packet.getData());
                System.out.println("Repeating " + received);
                sentinel = !received.equals("End");
            }

            socket.leaveGroup(group);
            socket.close();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
