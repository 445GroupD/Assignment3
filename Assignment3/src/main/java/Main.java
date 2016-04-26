import server.MulticastServer;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class Main
{
    public static void main(String[] args) throws IOException, InterruptedException {
        //create the new multicast serversin group

        /*we use a countdown latch for aesthetics only.
        We want the guis to show up in our desktop navigation tray
        in order from serverId 0 through the last.*/
        final CountDownLatch[] latches = {new CountDownLatch(1)};

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    new MulticastServer(0, 4, latches[0]);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        latches[0].await();
        latches[0] = new CountDownLatch(1);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    new MulticastServer(1,4, latches[0]);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        latches[0].await();
        latches[0] = new CountDownLatch(1);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    new MulticastServer(2,4, latches[0]);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        latches[0].await();

        latches[0] = new CountDownLatch(1);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    new MulticastServer(3,4, latches[0]);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        latches[0].await();

        latches[0] = new CountDownLatch(1);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    new MulticastServer(4,4, latches[0]);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        System.out.println("All servers' GUI's created and displaying.");
    }
}
