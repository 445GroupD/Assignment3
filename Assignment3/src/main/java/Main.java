import java.io.IOException;
import java.util.Scanner;

public class Main
{
    public static void main(String[] args) throws IOException
    {
        Scanner scan = new Scanner(System.in);
        System.out.println("Server Id: ");
        int server = Integer.parseInt(scan.nextLine());
        System.out.println("Leader Id");
        int leader = Integer.parseInt(scan.nextLine());
        new MulticastServer(server+"",leader+"");
    }
}
