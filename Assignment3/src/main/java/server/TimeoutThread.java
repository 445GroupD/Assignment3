package server;

import server.Packet.AppPacket;
import server.Packet.LeaderPacket;

import java.io.IOException;
import java.util.Random;

/**
 * Created by zsabin on 4/28/16.
 */
public class TimeoutThread implements Runnable
{
    private final MulticastServer server;

    public TimeoutThread(MulticastServer server)
    {
        this.server = server;
    }

    @Override
    public void run()
    {
        while (!server.isLeader() && !server.getDebugKill())
        {
            server.consoleMessage("Reseting Timeout", 2);
            server.resetTimeout();
            long timeLeft;
            do {

                timeLeft = System.currentTimeMillis() - server.getStartTime();
                //server.consoleMessage("Time Left: " + timeLeft, 2);
           //while(System.currentTimeMillis() - server.getStartTime() < server.getTimeout()){
                if(server.isLeader() || server.getDebugKill()){
                    server.consoleMessage("Killing Timeout Thread", 2);
                    return;
                }

            } while (timeLeft < server.getTimeout());

            server.consoleMessage("Server timed out", 2);
            if (server.isLeader()) {return; }
            server.changeServerState(MulticastServer.ServerState.CANIDATE);

            //start election
            server.consoleMessage("Inside the candidate run method",2);
            server.initializeCandidate();
            server.consoleMessage("Sending Vote Requests", 2);
            AppPacket voteRequest = new AppPacket(server.getId(), AppPacket.PacketType.VOTE_REQUEST, server.getLeaderId(),
                    server.getTerm(), -1, LeaderPacket.getNextSequenceNumber(), -1, "");

            try
            {
                server.getMulticastSocket().send(voteRequest.getDatagram(server.getGroup(), server.getPort()));
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        server.consoleMessage("Killing Timeout Thread", 2);
    }
}
