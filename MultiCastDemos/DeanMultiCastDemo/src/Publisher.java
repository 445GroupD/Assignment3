import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.util.Scanner;

/**
 * Created by dean on 4/17/16.
 */
public class Publisher {
    public static void main(String[] args) {
        try {
            MulticastSocket socket = new MulticastSocket(4446);
            InetAddress group = InetAddress.getByName("239.255.255.255");
            socket.joinGroup(group);
            Scanner reader = new Scanner(System.in);  // Reading from System.in
            DatagramPacket packet;
            boolean sentinel = true;
            while(sentinel) {
                System.out.println("Enter a number: ");
                String input = reader.nextLine();
                byte[] buf = input.getBytes();
                packet = new DatagramPacket(buf, buf.length,group,4446);
                socket.send(packet);
                sentinel = !input.equals("End");
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
