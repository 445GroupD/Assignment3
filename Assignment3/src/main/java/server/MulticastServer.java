package server;

import org.apache.http.HttpException;
import server.Packet.AppPacket;
import server.Packet.LeaderPacket;
import utils.WebService.RestCaller;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static server.Packet.AppPacket.PacketType.ACK;
import static server.Packet.AppPacket.PacketType.COMMIT;

public class MulticastServer
{
    private static DateFormat DEFAULT_DATE_FORMAT = new SimpleDateFormat("HH:mm:ss");

    private static final int PORT = 4446;
    private static final String GROUP_IP = "239.255.255.255";
    private Thread outgoing;
    private Thread incoming;
    private Thread heartbeat;
    private Thread timeoutThread;
    private ServerState serverState = ServerState.FOLLOWER;
    private final MulticastSocket multicastSocket;
    private final InetAddress group;
    private final int serverId;
    private int leaderId;
    private int term;
    private int voteCount = 0;
    private Lock serverStateLock = new ReentrantLock();
    private Lock timeoutLock = new ReentrantLock();
    private String outgoingData;
    private long groupCount = 5;
    private int lastVotedElection = 0;
    private int timeout;
    private long startTime;
    private Random rand = new Random();
    private final Map<Integer, String> fakeDB = new HashMap<Integer, String>();

    private final Map<Integer, LeaderPacket> outgoingLocalStorage = new ConcurrentHashMap<Integer, LeaderPacket>();
    private final LinkedBlockingQueue<String> linkedBlockingClientMessageQueue = new LinkedBlockingQueue<String>();
    private final Map<Integer, Integer> followerStatusMap = new ConcurrentHashMap<Integer, Integer>();

    private JTextArea userConsole;
    private JScrollPane scrollpane;
    private JTextArea serverConsole;
    private JTextField userMessageInput;
    private JButton userMessageInputButton;
    private JButton serverStatusButton;
    private JButton serverKillButton;
    private JButton serverTimeoutButton;
    private boolean heartbeatDebug = false;
    private int latestLogIndex = 1;
    private boolean debugKill = false;


    public MulticastServer(int serverId, int leaderId, CountDownLatch latch, int x, int y) throws IOException
    {
        this.serverId = serverId;
        this.leaderId = leaderId;
        this.term = 0;


        if (serverId == leaderId) serverState = ServerState.LEADER;

        // Create Socket
        multicastSocket = new MulticastSocket(PORT);
        group = InetAddress.getByName(GROUP_IP);
        multicastSocket.joinGroup(group);

        launchGUI(latch,x,y);

        outgoing = startSendingThread();
        incoming = startReceivingThread();
        heartbeat = startHeartbeatThread();
        if(!this.isLeader()){ timeoutThread = startTimeOutThread();}

        try
        {
            latestLogIndex = RestCaller.getLatestIndexNumber(this);
        }
        catch (URISyntaxException e)
        {
            e.printStackTrace();
        }
        catch (HttpException e)
        {
            e.printStackTrace();
        }
    }

    private void launchGUI(CountDownLatch latch,int x, int y)
    {
        //1. Create the frame.
        String isLeaderDisplay = serverId == leaderId ? "*L* " : "";
        JFrame frame = new JFrame(isLeaderDisplay + "Server #" + serverId + " | " + leaderId);
        frame.setSize(925, 500);

        //2. Optional: What happens when the frame closes?
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //construct the user's UI
        JPanel userConsolePanel = constructUserConsolePanel();
        //construct the server's UI
        JPanel serverConsolePanel = constructServerConsolePanel();

        //creae the scroll panel
        JPanel scrollPanePanel = new JPanel();
        scrollPanePanel.setSize(500, 500);
        scrollPanePanel.setLayout(new BorderLayout());

        //add the user UI to the scroll panel
        scrollPanePanel.add(userConsolePanel, BorderLayout.WEST);
        //add the sever UI to the scroll panel
        scrollPanePanel.add(serverConsolePanel, BorderLayout.EAST);
        //create the scroll panel with the scroll panel
        scrollpane = new JScrollPane(scrollPanePanel);
        //add the scroll pane to the frame's content pane.
        frame.getContentPane().add(scrollpane, BorderLayout.CENTER);
        frame.setLocation(x,y);

        //show the frame
        frame.setVisible(true);
        //let the other servers start
        latch.countDown();
    }

    private JPanel constructUserConsolePanel()
    {
        userConsole = new JTextArea();
        userConsole.setSize(500, 500);
        userConsole.setLineWrap(true);
        userConsole.setWrapStyleWord(true);
        userConsole.setEditable(false);

        userMessageInput = new JTextField(40);
        userMessageInput.setSize(400, 100);

        userMessageInputButton = new JButton("Send");
        userMessageInputButton.setSize(50, 100);

        userMessageInput.addKeyListener(new KeyAdapter()
        {
            public boolean waitingForClientMessageToSend;

            @Override
            public void keyTyped(KeyEvent keyEvent)
            {
                super.keyTyped(keyEvent);
                //the enter key returns a '\n' new line char
                if (keyEvent.getKeyChar() == '\n')
                {
                    userMessageInputButton.doClick();
                }
            }
        });

        userMessageInputButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent actionEvent)
            {
                userMessageInput.setEditable(false);
                String textFromGUI = userMessageInput.getText();
                addClientMessage(textFromGUI);
                consoleMessage(textFromGUI, 1);
                userMessageInput.setText("");
                userMessageInput.setEditable(true);
            }
        });

        userMessageInputButton.addMouseListener(new MouseListener()
        {
            @Override
            public void mouseClicked(MouseEvent mouseEvent)
            {
                userMessageInputButton.doClick();
            }

            @Override
            public void mousePressed(MouseEvent mouseEvent)
            {

            }

            @Override
            public void mouseReleased(MouseEvent mouseEvent)
            {

            }

            @Override
            public void mouseEntered(MouseEvent mouseEvent)
            {

            }

            @Override
            public void mouseExited(MouseEvent mouseEvent)
            {

            }
        });

        //add a listener to the userConsole's document to know when text has been added to it
        userConsole.getDocument().addDocumentListener(new DocumentListener()
        {

            @Override
            public void removeUpdate(DocumentEvent e)
            {
                //System.out.println("REMOVE UPDATE");
            }

            @Override
            public void insertUpdate(DocumentEvent e)
            {
                //System.out.println("INSERT UPDATE " + e);
               /* if(e.toString().contains("javax.swing.text.AbstractDocument")) {
                    consolePrompt("What do you want to do: ");
                }*/
                userConsole.setCaretPosition(userConsole.getDocument().getLength());
                //scrollToBottom(scrollpane);
            }

            @Override
            public void changedUpdate(DocumentEvent arg0)
            {
                //System.out.println("CHANGE UPDATE");
            }
        });

        JPanel userConsolePanel = new JPanel();
        userConsolePanel.setSize(500, 500);
        userConsolePanel.setLayout(new BorderLayout());
        userConsolePanel.add(new JLabel("User Console"), BorderLayout.NORTH);

        JPanel userControlsPanel = new JPanel();
        userControlsPanel.setSize(500, 500);
        userControlsPanel.setLayout(new BorderLayout());
        userControlsPanel.add(userMessageInput, BorderLayout.WEST);
        userControlsPanel.add(userMessageInputButton, BorderLayout.EAST);

        userConsolePanel.add(userConsole, BorderLayout.CENTER);
        userConsolePanel.add(userControlsPanel, BorderLayout.SOUTH);
        return userConsolePanel;
    }

    private JPanel constructServerConsolePanel()
    {
        serverConsole = new JTextArea();
        serverConsole.setSize(400, 500);
        serverConsole.setLineWrap(true);
        serverConsole.setWrapStyleWord(true);
        serverConsole.setEditable(false);

        serverStatusButton = new JButton("Status");
        serverStatusButton.setSize(50, 100);

        serverStatusButton.addMouseListener(new MouseListener()
        {
            @Override
            public void mouseClicked(MouseEvent mouseEvent)
            {
                consoleMessage("Show Status", 2);
                debugStatus();
            }

            @Override
            public void mousePressed(MouseEvent mouseEvent)
            {

            }

            @Override
            public void mouseReleased(MouseEvent mouseEvent)
            {

            }

            @Override
            public void mouseEntered(MouseEvent mouseEvent)
            {

            }

            @Override
            public void mouseExited(MouseEvent mouseEvent)
            {

            }
        });
        serverTimeoutButton = new JButton("Timeout");
        serverTimeoutButton.setSize(50, 100);

        serverTimeoutButton.addMouseListener(new MouseListener()
        {
            @Override
            public void mouseClicked(MouseEvent mouseEvent)
            {
                consoleMessage("Timeout Server", 2);
                debugTimeout();
            }

            @Override
            public void mousePressed(MouseEvent mouseEvent)
            {

            }

            @Override
            public void mouseReleased(MouseEvent mouseEvent)
            {

            }

            @Override
            public void mouseEntered(MouseEvent mouseEvent)
            {

            }

            @Override
            public void mouseExited(MouseEvent mouseEvent)
            {

            }
        });

        //add a listener to the userConsole's document to know when text has been added to it
        serverConsole.getDocument().addDocumentListener(new DocumentListener()
        {

            @Override
            public void removeUpdate(DocumentEvent e)
            {
                //System.out.println("REMOVE UPDATE");
            }

            @Override
            public void insertUpdate(DocumentEvent e)
            {
                //System.out.println("INSERT UPDATE " + e);
               /* if(e.toString().contains("javax.swing.text.AbstractDocument")) {
                    consolePrompt("What do you want to do: ");
                }*/
                serverConsole.setCaretPosition(userConsole.getDocument().getLength());
                scrollToBottom(scrollpane);
            }

            @Override
            public void changedUpdate(DocumentEvent arg0)
            {
                //System.out.println("CHANGE UPDATE");
            }
        });

        final JButton heartbeatButton = new JButton("heartBeat "+ heartbeatDebug);
        heartbeatButton.setSize(50, 100);
        heartbeatButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                heartbeatDebug = !heartbeatDebug;
                heartbeatButton.setText("heartbeat " +heartbeatDebug);
            }
        });

        serverKillButton = new JButton("Kill");
        serverKillButton.setSize(50, 100);
        serverKillButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                try
                {
                    if (serverKillButton.getText().equals("Kill"))
                    {
                        debugKill = true;
                        serverKillButton.setText("Restart");
                        multicastSocket.leaveGroup(group);

                    }
                    else
                    {
                        debugKill = false;
                        multicastSocket.joinGroup(group);
                        serverKillButton.setText("Kill");
                        outgoing = startSendingThread();
                        incoming = startReceivingThread();
                        heartbeat = startHeartbeatThread();
                    }
                }
                catch (IOException e1)
                {
                    e1.printStackTrace();
                }
            }
        });

        JButton deleteButton = new JButton("Delete");
        deleteButton.setSize(50, 100);
        deleteButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                deleteAllFromDB();
            }
        });

        JPanel serverControlsPanel = new JPanel();
        serverControlsPanel.setSize(500, 500);
        serverControlsPanel.setLayout(new BorderLayout());

        JPanel westControlsPanel = new JPanel();
        westControlsPanel.setSize(250, 500);
        westControlsPanel.setLayout(new BorderLayout());

        JPanel centralControlsPanel = new JPanel();
        centralControlsPanel.setSize(250, 500);
        centralControlsPanel.setLayout(new BorderLayout());

        westControlsPanel.add(serverStatusButton, BorderLayout.WEST);
        westControlsPanel.add(serverTimeoutButton, BorderLayout.CENTER);

        centralControlsPanel.add(heartbeatButton, BorderLayout.CENTER);
        centralControlsPanel.add(serverKillButton, BorderLayout.EAST);

        serverControlsPanel.add(westControlsPanel, BorderLayout.WEST);
        serverControlsPanel.add(centralControlsPanel, BorderLayout.CENTER);

        JPanel serverConsolePanel = new JPanel();
        serverConsolePanel.setSize(500, 500);
        serverConsolePanel.setLayout(new BorderLayout());
        serverConsolePanel.add(new JLabel("Server " + serverId + " Console"), BorderLayout.NORTH);

        serverConsolePanel.add(serverConsole, BorderLayout.CENTER);
        serverConsolePanel.add(serverControlsPanel, BorderLayout.SOUTH);
        return serverConsolePanel;
    }

    private void deleteAllFromDB()
    {
        try
        {
            RestCaller.deleteAll(this);
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
    }


    private void scrollToBottom(JScrollPane scrollPane)
    {
        final JScrollBar verticalBar = scrollPane.getVerticalScrollBar();
        AdjustmentListener downScroller = new AdjustmentListener()
        {
            @Override
            public void adjustmentValueChanged(AdjustmentEvent e)
            {
                Adjustable adjustable = e.getAdjustable();
                adjustable.setValue(adjustable.getMaximum());
                verticalBar.removeAdjustmentListener(this);
            }
        };
        verticalBar.addAdjustmentListener(downScroller);
    }

    private void addClientMessage(String textFromGUI)
    {
        //System.out.println("ADDING CM " + textFromGUI);
        try
        {
            linkedBlockingClientMessageQueue.put(textFromGUI);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        //System.out.println("ADDED CM " + textFromGUI);
    }

    private Thread startHeartbeatThread()
    {
        Thread heartbeat = new Thread(new MulticastHeartbeatSender(this));
        heartbeat.start();
        consoleMessage("started heartbeat thread", 2);
        return heartbeat;
    }

    private Thread startSendingThread()
    {

        Thread outgoing = new Thread(new MulticastServerSender(this));
        outgoing.start();
        consoleMessage("started outgoing thread", 2);
        return outgoing;
    }

    private Thread startReceivingThread()
    {

        Thread incoming = new Thread(new MulticastServerReceiver(this));
        incoming.start();
        consoleMessage("started incoming thread", 2);
        return incoming;
    }
    private Thread startTimeOutThread()
    {
        Thread timeOut = new Thread(new TimeoutThread(this));
        timeOut.start();
        consoleMessage("started timeout thread",2);
        return timeOut;
    }
    private void debugStatus()
    {
        try
        {
            consoleMessage("\n----------------------------- START SERVER STATUS ---------------------------\n", 2);
            consoleMessage("\tstate = " + serverState, 2);
            consoleMessage("\tid = " + serverId, 2);
            consoleMessage("\tleaderId = " + leaderId, 2);
            consoleMessage("\tterm = " + term, 2);
            consoleMessage("\tContents of FakeDB", 2);

            consoleMessage("\n\t------------------- Start Server DB Logs -------------------", 2);
            for (String current : RestCaller.getAllLogs(this))
            {
                consoleMessage(current, 2);
            }
            consoleMessage("\n\t------------------- END Server DB Logs -------------------", 2);
            consoleMessage("\n----------------------------- END SERVER STATUS ---------------------------\n", 2);
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
    }

    private void debugTimeout()
    {
        //changeServerState(ServerState.CANIDATE);
        resetTimeout(0);
    }

    public int getMajority()
    {
        return (int) Math.floor(groupCount / 2);
    }

    public ServerState getServerState()
    {
        serverStateLock.lock();
        ServerState state = serverState;
        serverStateLock.unlock();
        return state;
    }

    public boolean isFollower()
    {
        return getServerState().equals(ServerState.FOLLOWER);
    }

    public boolean isLeader()
    {
        return getServerState().equals(ServerState.LEADER);
    }

    public boolean isCandidate(){return getServerState().equals(ServerState.CANIDATE);}

    public Map<String, AppPacket> getIncomingLocalStorage()
    {
        return incomingLocalStorage;
    }

    private final Map<String, AppPacket> incomingLocalStorage = new ConcurrentHashMap<String, AppPacket>();

    public Map<Integer, LeaderPacket> getOutgoingLocalStorage()
    {
        return outgoingLocalStorage;
    }


    /**
     * Used for servers who are receiving packets from the leader.
     * This is a follower of the leader.
     * <p/>
     * It receives commit requests and commands from leading servers, in that order,
     * acks to confirm its agreement that the sender is the leader of the group,
     * and if so, it replicates the leader's incoming log to its own db when it receives the commit command from leader.
     */
    public void followerParse(AppPacket receivedPacket)
    {
        try
        {
            // make sure the packet is from the leader
            if (receivedPacket.getServerId() == leaderId)
            {
                //consoleMessage("Received Valid Packet", 2);
                resetTimeout();
                if(receivedPacket.getTerm() > term){
                    term = (int)receivedPacket.getTerm();
                }
                switch (receivedPacket.getType())
                {
                    case ACK:
                        consoleError("SHOULDNT SEE THIS", 2);
                        break;
                    case COMMENT:
                        AppPacket ackPacket = new AppPacket(serverId, ACK, leaderId, term, groupCount, receivedPacket.getSequenceNumber(), receivedPacket.getLogIndex(), "");
                        incomingLocalStorage.put(getIncomingStorageKey(receivedPacket), receivedPacket);
                        multicastSocket.send(ackPacket.getDatagram(group, PORT));
                        consoleMessage("Acking commit request confirmation for " + receivedPacket.toString(), 2);
                        break;
                    case COMMIT:
                        AppPacket localPacketFromIncomingStorage = incomingLocalStorage.get(getIncomingStorageKey(receivedPacket));
                        String receivedLogIndex = receivedPacket.getReadableData();
                        String actualDataFromIncomingStorage = localPacketFromIncomingStorage.getReadableData();

                        fakeDB.put(Integer.parseInt(receivedLogIndex), actualDataFromIncomingStorage);
                        RestCaller.postLog(this, receivedLogIndex, actualDataFromIncomingStorage);
                        consoleMessage("Committed Packet: #%s" + localPacketFromIncomingStorage.toString(), 2);
                        latestLogIndex = receivedPacket.getLogIndex();
                        break;
                    case HEARTBEAT:
                        parseHeartbeat(receivedPacket);
                        break;
                }
            } else{
                switch (receivedPacket.getType())
                {
                    case VOTE_REQUEST:
                    {
                        consoleMessage("Vote Request has been recieved from server " + receivedPacket
                                .getServerId() + " for term " + receivedPacket.getTerm(),2);
                        if (receivedPacket.getTerm() > term && lastVotedElection < receivedPacket.getTerm())
                        {
                            leaderId = -1;
                            lastVotedElection = (int) receivedPacket.getTerm();
                            AppPacket votePacket = new AppPacket(serverId, AppPacket.PacketType.VOTE,
                                    leaderId, lastVotedElection, groupCount, 0, 0, Integer.toString(receivedPacket.getServerId()));
                            multicastSocket.send(votePacket.getDatagram(group, PORT));
                            consoleMessage("voting in term " + term + " for server " + receivedPacket
                                    .getServerId(),2);
                        }
                        break;
                    }
                    case VOTE:
                    {
                        //IGNORE ALL VOTES
                        break;

                    }
                    default:
                    {

                        if (receivedPacket.getTerm() == term)
                        {
                            //consoleMessage("Packet Type: " + receivedPacket.getType(),2);
                            //consoleMessage("Received a Non-VoteRequest Packet w/ term = our current term. Term Recieved: " + receivedPacket.getTerm() + " || Recieved from server: " + receivedPacket.getServerId(),2);
                        }
                        term = (int) receivedPacket.getTerm();

                        break;
                    }
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public void candidateParse(AppPacket receivedPacket)
    {
        switch (receivedPacket.getType())
        {
            case VOTE:
            {
                /* Only accepts votes from the current term that are directed at this server*/
                String data = new String(receivedPacket.getData());
                data = data.trim();
                consoleMessage("Vote recieved from server: " + receivedPacket.getServerId() + " for server: " +
                        data,2);
                if (receivedPacket.getTerm() == term && serverId == Integer.parseInt(data))
                {
                    consoleMessage("vote accepted for term: " + term,2);
                    voteCount++;
                    consoleMessage("Current Vote count: " + voteCount,2);
                    if (voteCount >= getMajority() + 1)
                    {
                        consoleMessage("Majority vote: " + voteCount,2);
                        consoleMessage("Election won",2);
                        changeServerState(ServerState.LEADER);
                    }
                }
                break;
            }
            case HEARTBEAT:
            case COMMENT:
            case PICTURE:
            case GPS:
            case COMMIT:
            {
                if (receivedPacket.getTerm() >= term)
                {
                    // a new leader has been elected; defer to that leader
                    consoleMessage("Higher or equal term detected: switching to follower state", 2);
                    changeServerState(ServerState.FOLLOWER);
                    followerParse(receivedPacket);
                }
                break;
            }
        }
    }

    private void parseHeartbeat(AppPacket receivedPacket) throws IOException
    {
        try
        {
            if (receivedPacket.getLogIndex() == latestLogIndex + 1)
            {

                latestLogIndex = receivedPacket.getLogIndex();
                RestCaller.postLog(this, latestLogIndex + "", receivedPacket.getReadableData());
            }
            System.out.println(serverId + " receivedPacket.getReadableData() = " + receivedPacket.getReadableData());
            AppPacket heartbeatAckPacket = new AppPacket(serverId, AppPacket.PacketType.HEARTBEAT_ACK, leaderId, term, groupCount, -1, latestLogIndex, latestLogIndex + "");
            multicastSocket.send(heartbeatAckPacket.getDatagram(group, PORT));
            if (heartbeatDebug)
            {
                consoleMessage("Send HeartbeatAck: with latest index " + getLatestLogIndex(), 2);
            }
        }
        catch (URISyntaxException e)
        {
            e.printStackTrace();
        }
        catch (HttpException e)
        {
            e.printStackTrace();
        }
    }

    private String getIncomingStorageKey(AppPacket receivedPacket)
    {
        return receivedPacket.getLeaderId() + " " + receivedPacket.getSequenceNumber() + " " + receivedPacket.getTerm();
    }


    /**
     * @param receivedPacket
     */
    public void leaderParse(AppPacket receivedPacket)
    {
        try
        {
            switch (receivedPacket.getType())
            {
                case ACK:
                    LeaderPacket ackedLeaderPacket = outgoingLocalStorage.get(receivedPacket.getSequenceNumber());

                    int committedLogIndex = ackedLeaderPacket.confirm(getMajority(), this);
                    //make sure the log index returned from committing is valid
                    if (committedLogIndex > -1)
                    {
                        consoleMessage("\nLeader Committed " + ackedLeaderPacket.toString() + "\n", 2);
                        latestLogIndex++;
                        //all is well. The log was committed to this leader's persistent db at the committedLogIndex.
                        //send the commit command to all followers if necessary.

                        //we send the current term number of the leader because if it doesn't match what the followers have this packet stored as, they should not commit it to their db
                        AppPacket commitPacket = new AppPacket(serverId, COMMIT, leaderId, term, groupCount, ackedLeaderPacket.getSequenceNumber(), committedLogIndex, committedLogIndex + "");
                        if (term == ackedLeaderPacket.getTerm())
                        {
                            //send the commit command to all followers of this leader
                            multicastSocket.send(commitPacket.getDatagram(group, PORT));
                        }
                        else
                        {
                            //this leader is on the wrong term and thus may or may not be the leader anymore
                            consoleError("Leader is in the wrong term. Cant commit", 2);
                        }
                    }
                    break;

                case HEARTBEAT_ACK:
                    followerStatusMap.put(receivedPacket.getServerId(), receivedPacket.getLogIndex());
                    if (heartbeatDebug)
                    {
                        consoleMessage("received HeartbeatAck from " + receivedPacket.getServerId() + " with latest log index of " + receivedPacket.getLogIndex(), 2);
                    }
                    break;
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        catch (HttpException e)
        {
            e.printStackTrace();
        }
        catch (URISyntaxException e)
        {
            e.printStackTrace();
        }
    }

    public void changeServerState(ServerState nextState)
    {
        // A candidate changing to a candidate indicates their candidacy failed and they are starting a new election
        if (nextState != ServerState.CANIDATE && nextState == getServerState()) {
            return;
        }

        try
        {
            if(serverStateLock.tryLock(100, TimeUnit.MILLISECONDS))
            {
                consoleMessage("changing state to: " + nextState,2);
                if (nextState == ServerState.LEADER)
                {
                    leaderId = serverId;
                    timeoutThread = null;
                    heartbeat = startHeartbeatThread();
                } else
                {
                    if (timeoutThread == null)
                    {
                        timeoutThread = startTimeOutThread();
                    }
                }
                if (nextState == ServerState.CANIDATE) // start new election
                {
                    voteCount = 1;
                    term++;
                    leaderId = -1;
                } else
                {
                    voteCount = 0;
                }
                serverState = nextState;
                serverStateLock.unlock();
            }
            else
            {
                consoleMessage("Thread locked, cannot change state",2);
            }
        } catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    public String getOutgoingData()
    {
        return outgoingData;
    }

    public int getLeaderId()
    {
        return leaderId;
    }

    public int getTerm()
    {
        return term;
    }

    public InetAddress getGroup()
    {
        return group;
    }

    public int getPort()
    {
        return PORT;
    }

    public int getId()
    {
        return serverId;
    }

    public int getTimeout() { return timeout; }

    public long getStartTime() {
        timeoutLock.lock();
        long start = startTime;
        timeoutLock.unlock();
        return start;
    }

    public MulticastSocket getMulticastSocket()
    {
        return multicastSocket;
    }

    public void clearOutgoingData()
    {
        outgoingData = "";
    }

    public Map<Integer, Integer> getFollowerStatusMap()
    {
        return followerStatusMap;
    }

    public boolean getHeartbeatDebug()
    {
        return heartbeatDebug;
    }

    public int getLatestLogIndex()
    {
        return latestLogIndex;
    };

    public boolean filterPacket(AppPacket packet)
    {
        if (packet.getServerId() == serverId) /* Filter packets from itself */
        {
            return false;
        } else if (packet.getTerm() < term) /* Packet from obsolete term */
        {
            return false;
        } else if (packet.getTerm() == term)
        {
            return true;
        } else /* Packet.term > termNum: A new term has begun.*/
        {
            if (leaderId == -1) /* We don't know the leader of the current term so we accept all packets by
                default */
            {
                return true;
            }
            return packet.getServerId() == packet.getLeaderId(); /* Accept packet if it is from the new leader */
        }
    }

    public void updateStateAndLeader(AppPacket packet)
    {
        if (packet.getTerm() > term) /* A new term has begun. Update leader and term fields accordingly */
        {
            consoleMessage("Received packet of higher term. Term num: " + packet.getTerm() + " From Server: " + packet.getServerId(), 2);
            leaderId = (int) packet.getLeaderId();
            changeServerState(ServerState.FOLLOWER);
        }
    }

    public void resetTimeout()
    {
        int timeout = rand.nextInt(TimeoutThread.MIN_TIMEOUT) + TimeoutThread.MAX_TIMEOUT - TimeoutThread.MIN_TIMEOUT;
        resetTimeout(timeout);
    }

    private void resetTimeout(int timeout)
    {
        timeoutLock.lock();
        this.timeout = timeout;
        startTime = System.currentTimeMillis();
        //consoleMessage("Setting new Timeout:" + timeout + "ms", 2);
        timeoutLock.unlock();
    }


    public String getClientMessageToSend()
    {
        try
        {
            String take = linkedBlockingClientMessageQueue.take();
            //System.out.println("TAKE: " + take);
            return take;
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        return null;
    }

    public boolean getDebugKill()
    {
        return debugKill;
    }

    public enum ServerState
    {
        LEADER(),
        CANIDATE(),
        FOLLOWER();
    }

    public void consoleMessage(String s, int which)
    {
        if (s != null && !s.trim().isEmpty())
        {
            //m stands for message
            switch (which)
            {
                case 1:
                    userConsole.append("\n" + getCurrentDateTime(null) + " #" + serverId + "|m> " + s);
                    break;
                case 2:
                    serverConsole.append("\n" + getCurrentDateTime(null) + " #" + serverId + "|m> " + s);
                    break;
            }
        }
    }

    public void consoleError(String s, int which)
    {
        //e stands for error
        if (s != null && !s.trim().isEmpty())
        {
            switch (which)
            {
                case 1:
                    userConsole.append("\n" + getCurrentDateTime(null) + " #" + serverId + "|e> " + s);
                    break;
                case 2:
                    serverConsole.append("\n" + getCurrentDateTime(null) + " #" + serverId + "|e> " + s);
                    break;
            }
        }
    }

    protected void consolePrompt(String s, int which)
    {
        //p stands for prompt
        if (s != null && !s.trim().isEmpty())
        {
            switch (which)
            {
                case 1:
                    userConsole.append("\n" + getCurrentDateTime(null) + " #" + serverId + "|p> " + s);
                    break;
                case 2:
                    serverConsole.append("\n" + getCurrentDateTime(null) + " #" + serverId + "|p> " + s);
                    break;
            }
        }
    }

    public static String getCurrentDateTime(DateFormat dateFormat)
    {
        dateFormat = dateFormat != null ? dateFormat : DEFAULT_DATE_FORMAT;
        return dateFormat.format(new Date());
    }
}
