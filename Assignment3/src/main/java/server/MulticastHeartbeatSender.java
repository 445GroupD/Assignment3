package server;

import server.Packet.AppPacket;

import java.io.IOException;
import java.util.Map;

public class MulticastHeartbeatSender implements Runnable
{
    public static final int HEARBEAT_INTERVAL = 3000;
    private final MulticastServer server;
    private final Map<Integer, Integer> followerStatusMap;

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
        // getDataFromDBatIndex(smallest+1)
        return new AppPacket(server.getId(), AppPacket.PacketType.HEARTBEAT, server.getLeaderId(), server.getTerm(), -1, -1,smallest+1, "data for logIndex " + (smallest+1));

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