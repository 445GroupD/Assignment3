package server;

import server.Packet.AppPacket;
import server.Packet.LeaderPacket;

import java.io.IOException;
import java.util.Random;

/**
 * Created by zsabin and adavis20 on 4/28/16.
 * This thread is used to control the timeout for FOLLOWERS and CANDIDATES
 * 
 */
public class TimeoutThread implements Runnable
{
    public static final int MIN_TIMEOUT = 4000;
    public static final int MAX_TIMEOUT = 4500;
    private final MulticastServer server;
    
    public TimeoutThread(MulticastServer server)
    {
        this.server = server;
    }

    @Override
    public void run()
    {
        //This while loop checks to see if the current server is not A LEADER (Leaders do not need timeout threads)
        // Also checks to see if the sever is currently "Alive"
        while (!server.isLeader() && !server.getDebugKill())
        {
            //This initializes the first timeOut for A server and resets it after an election starts
            server.resetTimeout();
            long timeLeft;
            do {
                //This gets the difference between the last time out timeout was reset and the current time
                timeLeft = System.currentTimeMillis() - server.getStartTime();
                if(server.isLeader() || server.getDebugKill()){
                    server.consoleMessage("Killing Timeout Thread", 2);
                    return;
                }
            }
            //This checks that the time that has passed since the last time out is not greater than the current timeout
            while (timeLeft < server.getTimeout());
            // If the previous check fails, a timeout occurs and an election starts
            server.consoleMessage("Server timed out", 2);
            if (server.isLeader()) {return; }
            //Changes the servers state to a canidate
            server.changeServerState(MulticastServer.ServerState.CANIDATE);
            
            //start election
            server.consoleMessage("Sending Vote Requests", 2);
            AppPacket voteRequest = new AppPacket(server.getId(), AppPacket.PacketType.VOTE_REQUEST, server.getLeaderId(),
                    server.getTerm(), -1, LeaderPacket.getNextSequenceNumber(), -1,AppPacket.PacketType.VOTE_REQUEST.ordinal(), "");

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
