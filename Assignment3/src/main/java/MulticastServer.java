import javafx.util.Pair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.HashMap;
import java.util.Map;

public class MulticastServer {
    private static final int PORT = 4446;
    private static final String GROUP = "239.255.255.255";
    private final MulticastSocket multicastSocket;
    private final InetAddress group;
    private final int serverId;
    private int leaderId;
    private int termNum;
    private final Map<Integer, String> fakeDB = new HashMap<Integer, String>();
    private final Map<String, AppPacket> incominglocalStorage = new HashMap<String, AppPacket>();
    private final Map<Integer, Pair<AppPacket, Integer>> outgoinglocalStorage = new HashMap<Integer, Pair<AppPacket, Integer>>();
    private ServerState serverState = ServerState.FOLLOWER;
    private int majority = 2;
    private String dataToSend;
    private int seqNum = 0;
    private int logIndex = 0;
    private long groupCount = 5;
    private int timeout;
    private int voteCount = 0;
    private boolean votesRequested = false;

    public MulticastServer(String serverId, String leaderId) throws IOException {
        this.serverId = Integer.valueOf(serverId);
        this.leaderId = Integer.valueOf(leaderId);
        this.termNum = 0;
        System.out.println("serverId = " + serverId);
        System.out.println("leaderId = " + leaderId);
        if (this.serverId == this.leaderId) {
            System.out.println("IS LEADER");
            serverState = ServerState.LEADER;
        }

        // Create Socket
        multicastSocket = new MulticastSocket(PORT);
        group = InetAddress.getByName(GROUP);
        multicastSocket.joinGroup(group);

        startSendingThread();
        startReceivingThread();
        startDebugConsole();
    }


    private void startSendingThread() {

        Thread outgoing = new Thread(new MulticastServerSender(multicastSocket, group, PORT, serverId));
        outgoing.start();
        System.out.println("started outgoing thread");
    }

    private void startReceivingThread() {

        Thread incoming = new Thread(new MulticastServerReceiver(multicastSocket, group, PORT, serverId));
        incoming.start();
        System.out.println("started incoming thread");
    }

    private void startDebugConsole() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                System.out.println("What do you want to see");
                String[] other = in.readLine().split(" ");

                if (other[0].equals("status")) {
                    debugStatus();
                } else if (other[0].equals("send")) {
                    debugSend(other[1]);
                } else if (other[0].equals("timeout")) {
                    debugElection();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void debugSend(String debugData) {
        dataToSend = debugData;
    }

    private void debugStatus() {
        System.out.println("serverState = " + serverState);
        System.out.println("serverId = " + serverId);
        System.out.println("leaderId = " + leaderId);
        System.out.println("termNum = " + termNum);
        System.out.println("Contents of FakeDB");
        System.out.println("-------------------");
        for (Map.Entry current : fakeDB.entrySet()) {
            System.out.print("current.getKey() = " + current.getKey());
            System.out.println(" current.getValue() = " + current.getValue());
        }
        System.out.println("-------------------");
    }

    private void debugElection() {
        votesRequested = false;
        changeCandidateState(ServerState.CANIDATE);
        System.out.println("Server is now a candidate");
    }

    public int getMajority() {
        return (int) Math.floor(groupCount / 2);
    }

    private class MulticastServerSender implements Runnable {
        private final MulticastSocket multicastSocket;
        private final InetAddress group;
        private final int port;
        private final int serverId;

        public MulticastServerSender(MulticastSocket multicastSocket, InetAddress group, int port, int serverId) {
            this.multicastSocket = multicastSocket;
            this.group = group;
            this.port = port;
            this.serverId = serverId;
        }

        @Override
        public void run() {
            while (true) {
                if (serverState.equals(ServerState.LEADER) && dataToSend != null) {
                    try {

                        System.out.println("sending: " + dataToSend);
                        AppPacket test = new AppPacket(serverId, AppPacket.PacketType.COMMENT, leaderId, termNum, groupCount, seqNum, logIndex, dataToSend);

                        multicastSocket.send(test.getDatagram(group, port));
                        outgoinglocalStorage.put(test.getSeq(), new Pair(test, logIndex));
                        dataToSend = null;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else if (serverState.equals(ServerState.CANIDATE) && !votesRequested) {
                    leaderId = -1;
                    voteCount = 1;
                    termNum++;
                    //still need timeout implementation
                    System.out.println("sending Vote Requests");
                    AppPacket voteRequest = new AppPacket(serverId, AppPacket.PacketType.VOTE_REQUEST, leaderId, termNum, groupCount, seqNum, 0, "");

                    try {
                        votesRequested = true;
                        multicastSocket.send(voteRequest.getDatagram(group, port));
                       // votesRequested = true;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void changeCandidateState(ServerState nextState) {
        if(nextState==ServerState.LEADER){
            leaderId = serverId;
        }
        serverState = nextState;
        voteCount = 0;

    }

    private class MulticastServerReceiver implements Runnable {
        private final MulticastSocket multicastSocket;
        private final InetAddress group;
        private final int port;
        private final int serverId;

        public MulticastServerReceiver(MulticastSocket multicastSocket, InetAddress group, int port, int serverId) {
            this.multicastSocket = multicastSocket;
            this.group = group;
            this.port = port;
            this.serverId = serverId;
        }

        @Override
        public void run() {
            try {
                DatagramPacket packet;
                AppPacket receivedPacket;
                byte[] buf;

                // Need to create some way to end the program
                boolean sentinel = true;
                while (sentinel) {
                    buf = new byte[1500];
                    packet = new DatagramPacket(buf, buf.length, group, port);
                    multicastSocket.receive(packet);
                    receivedPacket = new AppPacket(packet.getData());
                    if(receivedPacket.getServerId()!= serverId && termNum <= receivedPacket.getTerm()&&receivedPacket.getType()!= AppPacket.PacketType.VOTE && receivedPacket.getType()!= AppPacket.PacketType.VOTE_REQUEST){
                        termNum = (int)receivedPacket.getTerm();
                        leaderId = (int)receivedPacket.getLeaderId();
                        System.out.println("This is not our concern");
                        changeCandidateState(ServerState.FOLLOWER);
                    }else if(receivedPacket.getType() == AppPacket.PacketType.VOTE_REQUEST){
                            System.out.println("Vote Request has been recieved from server " + receivedPacket.getServerId()+ " for term " + receivedPacket.getTerm());
                            if (termNum < receivedPacket.getTerm()) {
                                changeCandidateState(ServerState.FOLLOWER);
                                leaderId = -1;
                                termNum = (int) receivedPacket.getTerm();
                                AppPacket votePacket = new AppPacket(serverId, AppPacket.PacketType.VOTE, receivedPacket.getServerId(), termNum, groupCount, 0, 0, "");
                                multicastSocket.send(votePacket.getDatagram(group, PORT));
                                System.out.println("voting in term " + termNum + " for server " + receivedPacket.getServerId());
                            }
                    }else
                    if (serverState.equals(ServerState.LEADER)) {
                        leaderParse(receivedPacket);
                    } else if (serverState.equals(ServerState.CANIDATE)) {
                        candidateParse(receivedPacket);
                    } else if (serverState.equals(ServerState.FOLLOWER)) {
                        followerParse(receivedPacket);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void followerParse(AppPacket receivedPacket) {
        try {
            // make sure the packet is from the leader
            if (receivedPacket.getServerId() == leaderId) {
                switch (receivedPacket.getType()) {
                    case ACK: {
                        System.out.println("SHOULDNT SEE THIS");
                        break;
                    }
                    case COMMENT: {
                        AppPacket ackPacket = new AppPacket(serverId, AppPacket.PacketType.ACK, leaderId, termNum, groupCount, receivedPacket.getSeq(), receivedPacket.getLogIndex(), "");
                        incominglocalStorage.put(receivedPacket.getLogIndex() + " " + receivedPacket.getTerm(), receivedPacket);
                        multicastSocket.send(ackPacket.getDatagram(group, PORT));
                        System.out.println("acking");
                        break;

                    }
                    case COMMIT: {
                        AppPacket local = incominglocalStorage.get(receivedPacket.getLogIndex() + " " + receivedPacket.getTerm());
                        fakeDB.put(local.getLogIndex(), new String(local.getData()));
                        System.out.println("commit");
                        break;
                    }
//                    case VOTE_REQUEST: {
//                        System.out.println("Vote Request has been recieved from server " + receivedPacket.getServerId()+ " for term " + receivedPacket.getTerm());
//                        if (termNum < receivedPacket.getTerm()) {
//                            leaderId = -1;
//                            termNum = (int) receivedPacket.getTerm();
//                            AppPacket votePacket = new AppPacket(serverId, AppPacket.PacketType.VOTE, receivedPacket.getServerId(), termNum, groupCount, 0, 0, "");
//                            multicastSocket.send(votePacket.getDatagram(group, PORT));
//                            System.out.println("voting in term " + termNum + " for server " + receivedPacket.getServerId());
//                        }
//
//                    }
                }
            } else {


            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void candidateParse(AppPacket receivedPacket) {

        switch (receivedPacket.getType()) {
            case VOTE: {
                System.out.println("Vote recieved from server: " + receivedPacket.getServerId()+" for server: " + receivedPacket.getLeaderId());
                if (receivedPacket.getTerm() == termNum && serverId == receivedPacket.getLeaderId()) {
                    System.out.println("vote accepted for term: " + termNum);
                    voteCount++;
                    System.out.println("Current Vote count: "+ voteCount);
                    if (voteCount >= getMajority() + 1) {
                        System.out.println("Majority vote: "+ voteCount);
                        System.out.println("Election won");
                        changeCandidateState(ServerState.LEADER);
                    }
                }
                break;
            }
//            case VOTE_REQUEST:
//                // only accept vote requests from candidates with a higher term
//                System.out.println("Vote request recieved from "+ receivedPacket.getServerId());
//                if (receivedPacket.getTerm() > termNum) {
//                    // a more recent election is underway; end candidacy and revert to Follower state
//                    System.out.println("Higher term detected: switching to follower state");
//                    changeCandidateState(ServerState.FOLLOWER);
//                    followerParse(receivedPacket);
//                }
               // break;
            case HEARTBEAT:
            case COMMENT:
            case PICTURE:
            case GPS:
            case COMMIT: {
                if (receivedPacket.getTerm() >= termNum) {
                    // a new leader has been elected; defer to that leader
                    System.out.println("Higher or equal term detected: switching to follower state");
                    changeCandidateState(ServerState.FOLLOWER);
                    followerParse(receivedPacket);
                }
                break;
            }
        }
    }



    private void leaderParse(AppPacket receivedPacket)
    {
        try
        {
            switch (receivedPacket.getType())
            {
                case ACK:
                {
                    Pair<AppPacket, Integer> ackedPacket = outgoinglocalStorage.get(receivedPacket.getSeq());
                    outgoinglocalStorage.put(ackedPacket.getKey().getSeq(), new Pair<AppPacket, Integer>(ackedPacket.getKey(), ackedPacket.getValue() + 1));
                    ackedPacket = outgoinglocalStorage.get(receivedPacket.getSeq());
                    Integer count = ackedPacket.getValue();

                    if (count >= getMajority() && fakeDB.get(ackedPacket.getKey().getLogIndex()) == null)
                    {
                        fakeDB.put(ackedPacket.getKey().getLogIndex(), new String(ackedPacket.getKey().getData()));
                        AppPacket commitPacket = new AppPacket(serverId, AppPacket.PacketType.COMMIT, leaderId, termNum, groupCount, ackedPacket.getKey().getSeq(), ackedPacket.getKey().getLogIndex(), "sdfsdf");
                        if (commitPacket.getTerm() == ackedPacket.getKey().getTerm())
                        {
                            multicastSocket.send(commitPacket.getDatagram(group, PORT));
                        }
                        else
                        {
                            System.out.println("wrong term cant commit");
                        }
                        logIndex++;
                    }
                    break;
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public enum ServerState
    {
        LEADER(),
        CANIDATE(),
        FOLLOWER();
    }
}
