package server;

import org.apache.http.HttpException;
import server.Packet.AppPacket;
import utils.WebService.RestCaller;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;

public class MulticastHeartbeatSender implements Runnable
{
    public static final int HEARBEAT_INTERVAL = 6000;
    private final MulticastServer server;
    private final Map<Integer, Integer> followerStatusMap;
    long lt;
    public MulticastHeartbeatSender(MulticastServer server)
    {
        this.server = server;
        this.followerStatusMap = server.getFollowerStatusMap();
    }

    @Override
    public void run()
    {
        while (server.isLeader() && !server.getDebugKill())
        {
            System.out.println("heartbeat");
            try
            {
                server.getFollowerStatusMap();
                AppPacket heartbeatPacket = buildPacket();
                if (server.getHeartbeatDebug())
                {
                    server.consoleMessage("\nSending Heartbeat" + heartbeatPacket.toString(),2);
                }
                server.getMulticastSocket().send(heartbeatPacket.getDatagram(server.getGroup(), server.getPort()));
                rest();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    private AppPacket buildPacket()
    {
        int smallest = server.getLatestLogIndex() -1;
        for(Integer current : followerStatusMap.values())
        {
            if(current < smallest)
            {
                System.out.println("if case");
                smallest = current;
            }
        }
        // Something like this
        String data ="";
        try
        {
            System.out.println("smallest = " + smallest);
            data =RestCaller.getLogByIndex(server,++smallest+"");
        }
        catch (URISyntaxException e)
        {
            e.printStackTrace();
        }
        catch (HttpException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        System.out.println("data = " + data);
        server.clearFollowerStatusMap();
        return new AppPacket(server.getId(), AppPacket.PacketType.HEARTBEAT, server.getLeaderId(), server.getTerm(), server.getLatestLogIndex(), -1,smallest, data);

    }

    private void rest()
    {
        try
        {
            Thread.sleep(HEARBEAT_INTERVAL);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }
}